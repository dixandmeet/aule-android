package io.aule.android.data.aule

import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.AuthException
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.AuthPkceFlow
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.ProRegistrationDraft
import io.aule.android.core.model.repository.AuthPkceStore
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.AuthSessionStore
import io.aule.android.core.model.repository.RegistrationDraftStore
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.core.network.RawHttpResponse
import io.aule.android.data.auth.Pkce
import io.aule.android.data.dto.GoTrueErrorDto
import io.aule.android.data.dto.GoTrueTokenResponseDto
import io.aule.android.data.dto.UserProfileDto
import io.aule.android.data.dto.authFailureKindOf
import io.aule.android.data.dto.serverMessage
import io.aule.android.data.dto.toSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Client GoTrue (Supabase Auth) sur OkHttp.
 *
 * Pas de SDK supabase-kt : le projet a déjà OkHttp pour le BFF et MapLibre
 * (ADR-004), et un second client HTTP n'apporterait que de la divergence.
 *
 * L'URL et la clé publiable viennent de la config. Absentes, [signIn] lève
 * [AuthFailureKind.NOT_CONFIGURED] plutôt que d'appeler le vide.
 */
class SupabaseAuthRepository(
    private val client: AuleHttpClient,
    private val store: AuthSessionStore,
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val logger: AuleLogger,
    private val pkce: AuthPkceStore = MemoryAuthPkceStore(),
    private val createVerifier: () -> String = { Pkce.generateVerifier() },
    private val json: Json = AuleHttpClient.defaultJson,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) : AuthRepository {

    private val mutex = Mutex()
    @Volatile private var session: AuthSession? = null

    private val configured: Boolean
        get() = supabaseUrl.isNotBlank() && publishableKey.isNotBlank()

    private val authBase: String
        get() = supabaseUrl.trimEnd('/') + "/auth/v1"

    override fun currentSession(): AuthSession? = session

    override suspend fun restore(): AuthSession? = mutex.withLock {
        val stored = store.read() ?: run {
            session = null
            return null
        }
        if (!stored.isExpired(nowEpochSeconds())) {
            session = stored
            return stored
        }
        return try {
            refresh(stored.refreshToken).also { session = it }
        } catch (failure: AuthException) {
            logger.info(LogDomain.AUTH, "Session expirée, reconnexion requise (${failure.kind}).")
            store.clear()
            session = null
            null
        } catch (failure: Throwable) {
            logger.warn(LogDomain.AUTH, "Rafraîchissement impossible.", failure)
            store.clear()
            session = null
            null
        }
    }

    override suspend fun signIn(email: String, password: String): AuthSession = mutex.withLock {
        if (!configured) throw AuthException(AuthFailureKind.NOT_CONFIGURED)
        val trimmed = email.trim().lowercase()
        if (trimmed.isEmpty() || '@' !in trimmed) {
            throw AuthException(AuthFailureKind.INVALID_EMAIL)
        }
        if (password.isEmpty()) {
            throw AuthException(AuthFailureKind.INVALID_CREDENTIALS)
        }
        val body = buildJsonObject {
            put("email", trimmed)
            put("password", password)
        }.toString()
        val opened = requestToken(
            query = mapOf("grant_type" to "password"),
            jsonBody = body,
        )
        store.write(opened)
        session = opened
        logger.info(LogDomain.AUTH, "Session ouverte.")
        opened
    }

    override suspend fun signOut() {
        mutex.withLock {
            val current = session
            session = null
            store.clear()
            if (current == null || !configured) return@withLock
            try {
                client.postRaw(
                    url = "$authBase/logout",
                    jsonBody = "{}",
                    headers = authHeaders(current.accessToken),
                )
            } catch (_: Throwable) {
                // La session locale est déjà effacée : un logout réseau en échec
                // ne doit pas bloquer la sortie.
            }
            logger.info(LogDomain.AUTH, "Session fermée.")
        }
    }

    override suspend fun fetchStaffRole(session: AuthSession): String? {
        if (!configured) return null
        val restBase = supabaseUrl.trimEnd('/') + "/rest/v1"
        val response = client.getRaw(
            url = "$restBase/user_profiles",
            headers = mapOf(
                "apikey" to publishableKey,
                "Authorization" to "Bearer ${session.accessToken}",
            ),
            query = mapOf(
                "select" to "role",
                "id" to "eq.${session.user.id}",
                "limit" to "1",
            ),
        )
        when (response.code) {
            in 200..299 -> Unit
            404 -> return null
            502, 503, 504 -> throw ApiException.UpstreamUnavailable(response.code)
            in 400..499 -> throw ApiException.BadRequest(response.code)
            else -> throw ApiException.Server(response.code)
        }
        val rows = try {
            json.decodeFromString(ListSerializer(UserProfileDto.serializer()), response.body)
        } catch (failure: Throwable) {
            throw ApiException.Decoding(failure)
        }
        return rows.firstOrNull()?.role?.trim()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun signUpProfessional(draft: ProRegistrationDraft, password: String) {
        if (!configured) throw AuthException(AuthFailureKind.NOT_CONFIGURED)
        val trimmed = draft.email.trim().lowercase()
        if (trimmed.isEmpty() || '@' !in trimmed) {
            throw AuthException(AuthFailureKind.INVALID_EMAIL)
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw AuthException(AuthFailureKind.WEAK_PASSWORD)
        }
        val verifier = createVerifier()
        val challenge = Pkce.challenge(verifier)
        pkce.writeVerifier(verifier, AuthPkceFlow.SIGN_UP)
        val body = JsonObject(
            buildJsonObject {
                put("email", trimmed)
                put("password", password)
                put("code_challenge", challenge)
                put("code_challenge_method", "s256")
            } + ("data" to draft.toAuthMetadata()),
        ).toString()
        postAuth(
            path = "/signup",
            jsonBody = body,
            query = mapOf("redirect_to" to EMAIL_CONFIRMATION_REDIRECT),
        )
        logger.info(LogDomain.AUTH, "Inscription professionnelle envoyée.")
    }

    override suspend fun resendSignupConfirmation(email: String) {
        if (!configured) throw AuthException(AuthFailureKind.NOT_CONFIGURED)
        val trimmed = email.trim().lowercase()
        if (trimmed.isEmpty() || '@' !in trimmed) {
            throw AuthException(AuthFailureKind.INVALID_EMAIL)
        }
        val verifier = createVerifier()
        val challenge = Pkce.challenge(verifier)
        pkce.writeVerifier(verifier, AuthPkceFlow.SIGN_UP)
        val body = buildJsonObject {
            put("type", "signup")
            put("email", trimmed)
            put("code_challenge", challenge)
            put("code_challenge_method", "s256")
        }.toString()
        postAuth(
            path = "/resend",
            jsonBody = body,
            query = mapOf("redirect_to" to EMAIL_CONFIRMATION_REDIRECT),
        )
        logger.info(LogDomain.AUTH, "E-mail de confirmation renvoyé.")
    }

    /**
     * `POST /recover` — le lien « mot de passe oublié ».
     *
     * Même adresse de retour que l'inscription : c'est celle qui est déclarée
     * dans les *Redirect URLs* du projet, et en ajouter une seconde n'apporterait
     * qu'une valeur de plus à tenir à jour côté serveur. Le vérifieur PKCE est
     * donc marqué [AuthPkceFlow.RECOVERY] : c'est lui, et non l'URL de retour,
     * qui dira au retour que ce lien n'ouvre que le nouveau mot de passe.
     *
     * **Un compte inconnu répond 200.** GoTrue ne distingue pas, exprès, et on
     * ne rattrape pas : dire « cette adresse n'existe pas » livrerait la liste
     * des inscrits à qui prend le temps de la deviner.
     */
    override suspend fun sendPasswordRecovery(email: String) {
        if (!configured) throw AuthException(AuthFailureKind.NOT_CONFIGURED)
        val trimmed = email.trim().lowercase()
        if (trimmed.isEmpty() || '@' !in trimmed) {
            throw AuthException(AuthFailureKind.INVALID_EMAIL)
        }
        val verifier = createVerifier()
        val challenge = Pkce.challenge(verifier)
        pkce.writeVerifier(verifier, AuthPkceFlow.RECOVERY)
        val body = buildJsonObject {
            put("email", trimmed)
            put("code_challenge", challenge)
            put("code_challenge_method", "s256")
        }.toString()
        postAuth(
            path = "/recover",
            jsonBody = body,
            query = mapOf("redirect_to" to EMAIL_CONFIRMATION_REDIRECT),
        )
        logger.info(LogDomain.AUTH, "Lien de récupération envoyé.")
    }

    /**
     * `PUT /user` — le nouveau mot de passe, sous le jeton de la session.
     *
     * GoTrue ne redemande pas l'ancien : c'est le jeton qui autorise, qu'il
     * vienne d'un lien de récupération ou d'une connexion ordinaire. Sans
     * session ouverte, il n'y a rien à modifier — [AuthFailureKind.UNKNOWN],
     * comme la suppression de compte.
     *
     * La réponse rend l'utilisateur, **pas** de jetons : la session en cours
     * reste valable et n'est pas réécrite.
     */
    override suspend fun updatePassword(newPassword: String) {
        val current = mutex.withLock { session }
            ?: throw AuthException(AuthFailureKind.UNKNOWN)
        if (!configured) throw AuthException(AuthFailureKind.NOT_CONFIGURED)
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            throw AuthException(AuthFailureKind.WEAK_PASSWORD)
        }
        val body = buildJsonObject {
            put("password", newPassword)
        }.toString()
        val response = try {
            client.putRaw(
                url = "$authBase/user",
                jsonBody = body,
                headers = authHeaders(current.accessToken),
            )
        } catch (cancelled: ApiException.Cancelled) {
            throw cancelled
        } catch (transport: ApiException.Transport) {
            throw AuthException(AuthFailureKind.NETWORK, transport.message)
        } catch (failure: ApiException) {
            throw AuthException(AuthFailureKind.NETWORK, failure.message)
        }
        if (response.code !in 200..299) {
            val error = runCatching {
                json.decodeFromString(GoTrueErrorDto.serializer(), response.body)
            }.getOrNull()
            throw AuthException(
                kind = authFailureKindOf(response.code, error),
                serverMessage = error.serverMessage(),
            )
        }
        logger.info(LogDomain.AUTH, "Mot de passe changé.")
    }

    override suspend fun pendingAuthFlow(): AuthPkceFlow? = pkce.readFlow()

    override suspend fun exchangeAuthCode(code: String): AuthSession = mutex.withLock {
        if (!configured) throw AuthException(AuthFailureKind.NOT_CONFIGURED)
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            throw AuthException(AuthFailureKind.UNKNOWN)
        }
        val verifier = pkce.readVerifier()
            ?: throw AuthException(AuthFailureKind.UNKNOWN, "PKCE verifier missing.")
        val body = buildJsonObject {
            put("auth_code", trimmed)
            put("code_verifier", verifier)
        }.toString()
        val opened = requestToken(
            query = mapOf("grant_type" to "pkce"),
            jsonBody = body,
        )
        pkce.clearVerifier()
        store.write(opened)
        session = opened
        logger.info(LogDomain.AUTH, "Session ouverte par confirmation d'e-mail.")
        opened
    }

    override suspend fun deleteAccount() {
        val current = mutex.withLock { session }
            ?: throw AuthException(AuthFailureKind.UNKNOWN)
        if (!configured) throw AuthException(AuthFailureKind.NOT_CONFIGURED)
        val restBase = supabaseUrl.trimEnd('/') + "/rest/v1"
        val response = try {
            client.postRaw(
                url = "$restBase/rpc/delete_my_account",
                jsonBody = "{}",
                headers = mapOf(
                    "apikey" to publishableKey,
                    "Authorization" to "Bearer ${current.accessToken}",
                    "Prefer" to "return=minimal",
                ),
            )
        } catch (cancelled: ApiException.Cancelled) {
            throw cancelled
        } catch (transport: ApiException.Transport) {
            throw AuthException(AuthFailureKind.NETWORK, transport.message)
        } catch (failure: ApiException) {
            throw AuthException(AuthFailureKind.NETWORK, failure.message)
        }
        if (response.code !in 200..299) {
            val error = runCatching {
                json.decodeFromString(GoTrueErrorDto.serializer(), response.body)
            }.getOrNull()
            throw AuthException(
                kind = if (response.code in 500..599 || response.code == 429) {
                    AuthFailureKind.NETWORK
                } else {
                    AuthFailureKind.UNKNOWN
                },
                serverMessage = error.serverMessage(),
            )
        }
        signOut()
        logger.info(LogDomain.AUTH, "Compte supprimé.")
    }

    private suspend fun refresh(refreshToken: String): AuthSession {
        if (!configured) throw AuthException(AuthFailureKind.NOT_CONFIGURED)
        val body = buildJsonObject {
            put("refresh_token", refreshToken)
        }.toString()
        val opened = requestToken(
            query = mapOf("grant_type" to "refresh_token"),
            jsonBody = body,
        )
        store.write(opened)
        return opened
    }

    private suspend fun requestToken(
        query: Map<String, String?>,
        jsonBody: String,
    ): AuthSession {
        val response = try {
            client.postRaw(
                url = "$authBase/token",
                jsonBody = jsonBody,
                headers = mapOf(
                    "apikey" to publishableKey,
                    "Authorization" to "Bearer $publishableKey",
                ),
                query = query,
            )
        } catch (cancelled: ApiException.Cancelled) {
            throw cancelled
        } catch (transport: ApiException.Transport) {
            throw AuthException(AuthFailureKind.NETWORK, transport.message)
        } catch (failure: ApiException) {
            throw AuthException(AuthFailureKind.NETWORK, failure.message)
        }

        if (response.code in 200..299) {
            val dto = try {
                json.decodeFromString(GoTrueTokenResponseDto.serializer(), response.body)
            } catch (failure: Throwable) {
                throw AuthException(AuthFailureKind.UNKNOWN, failure.message)
            }
            return dto.toSession(nowEpochSeconds())
                ?: throw AuthException(AuthFailureKind.UNKNOWN, "Session sans utilisateur.")
        }

        val error = runCatching {
            json.decodeFromString(GoTrueErrorDto.serializer(), response.body)
        }.getOrNull()
        throw AuthException(
            kind = authFailureKindOf(response.code, error),
            serverMessage = error.serverMessage(),
        )
    }

    /**
     * POST GoTrue qui n'ouvre pas de session — inscription, renvoi d'e-mail.
     *
     * Un 2xx suffit : GoTrue peut renvoyer l'utilisateur sans jetons tant que
     * l'e-mail n'est pas confirmé, et Flutter n'enregistre pas non plus une
     * session à ce stade.
     */
    private suspend fun postAuth(
        path: String,
        jsonBody: String,
        query: Map<String, String?>,
    ): RawHttpResponse {
        val response = try {
            client.postRaw(
                url = "$authBase$path",
                jsonBody = jsonBody,
                headers = mapOf(
                    "apikey" to publishableKey,
                    "Authorization" to "Bearer $publishableKey",
                ),
                query = query,
            )
        } catch (cancelled: ApiException.Cancelled) {
            throw cancelled
        } catch (transport: ApiException.Transport) {
            throw AuthException(AuthFailureKind.NETWORK, transport.message)
        } catch (failure: ApiException) {
            throw AuthException(AuthFailureKind.NETWORK, failure.message)
        }
        if (response.code in 200..299) return response
        val error = runCatching {
            json.decodeFromString(GoTrueErrorDto.serializer(), response.body)
        }.getOrNull()
        throw AuthException(
            kind = authFailureKindOf(response.code, error),
            serverMessage = error.serverMessage(),
        )
    }

    private fun authHeaders(accessToken: String): Map<String, String> = mapOf(
        "apikey" to publishableKey,
        "Authorization" to "Bearer $accessToken",
    )
}

/** Dépôt de session en mémoire — tests, et rien d'autre. */
class MemoryAuthSessionStore : AuthSessionStore {
    @Volatile private var session: AuthSession? = null
    override suspend fun read(): AuthSession? = session
    override suspend fun write(session: AuthSession) {
        this.session = session
    }
    override suspend fun clear() {
        session = null
    }
}

/** Vérifieur PKCE en mémoire — tests, et rien d'autre. */
class MemoryAuthPkceStore : AuthPkceStore {
    @Volatile private var verifier: String? = null
    @Volatile private var flow: AuthPkceFlow? = null
    override suspend fun writeVerifier(verifier: String, flow: AuthPkceFlow) {
        this.verifier = verifier
        this.flow = flow
    }
    override suspend fun readVerifier(): String? = verifier
    override suspend fun readFlow(): AuthPkceFlow? = flow
    override suspend fun clearVerifier() {
        verifier = null
        flow = null
    }
}

/** Brouillon d'inscription en mémoire — tests, et rien d'autre. */
class MemoryRegistrationDraftStore : RegistrationDraftStore {
    @Volatile private var draft: String? = null
    @Volatile private var step: String? = null
    override suspend fun readDraft(): String? = draft
    override suspend fun readStep(): String? = step
    override suspend fun write(draftJson: String, step: String) {
        this.draft = draftJson
        this.step = step
    }
    override suspend fun clear() {
        draft = null
        step = null
    }
}

internal const val EMAIL_CONFIRMATION_REDIRECT = "io.aule.pro://login-callback/"
private const val MIN_PASSWORD_LENGTH = 8
