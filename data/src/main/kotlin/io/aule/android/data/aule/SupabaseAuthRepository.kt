package io.aule.android.data.aule

import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.AuthException
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.AuthPkceFlow
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.OAuthProvider
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Les seuls refus qui condamnent un jeton de rafraîchissement.
 *
 * La liste est **positive**, et c'est le point : un code que GoTrue n'a pas
 * encore inventé, un 500, un 429, une coupure de transport ne doivent pas
 * déconnecter quelqu'un. Le doute profite à la session — la sanction d'une
 * erreur de jugement est asymétrique. Se tromper en gardant coûte un appel qui
 * répondra 401 ; se tromper en effaçant coûte un conducteur qui ne peut plus
 * entrer, précisément là où il n'a pas de réseau pour se reconnecter.
 *
 * `invalid_grant` — le code de GoTrue pour un jeton révoqué ou déjà consommé —
 * arrive ici en [AuthFailureKind.INVALID_CREDENTIALS] (`authFailureKindOf`).
 */
private val REVOKING_FAILURES = setOf(
    AuthFailureKind.INVALID_CREDENTIALS,
    AuthFailureKind.EMAIL_NOT_CONFIRMED,
)

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
    /**
     * Le brouillon d'inscription, relu au retour d'un fournisseur externe.
     *
     * Il est ici et pas dans l'écran parce que l'écran peut ne plus exister :
     * l'aller-retour vers Google passe par le navigateur, et le système a tout
     * loisir de tuer le processus pendant ce temps. Au retour, le seul endroit
     * qui sache encore ce que l'inscription avait collecté est le disque.
     */
    private val drafts: RegistrationDraftStore = MemoryRegistrationDraftStore(),
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AuthException) {
            if (failure.kind in REVOKING_FAILURES) {
                logger.info(LogDomain.AUTH, "Session révoquée (${failure.kind}).")
                store.clear()
                session = null
                null
            } else {
                keepUnverified(stored, "refus non définitif (${failure.kind})")
            }
        } catch (failure: Throwable) {
            keepUnverified(stored, "rafraîchissement injoignable : ${failure.message}")
        }
    }

    /**
     * Le serveur n'a pas répondu — on garde la session, on ne la juge pas.
     *
     * ## Ce que faisait le code d'avant, et pourquoi c'était grave
     *
     * Toute panne était traitée comme un refus : `store.clear()`, et l'écran de
     * connexion. Or un jeton d'accès GoTrue vit **une heure**. Passé ce délai,
     * relancer l'application sans réseau — dépôt en sous-sol, parking, tunnel —
     * jetait un jeton de rafraîchissement parfaitement valide et renvoyait à un
     * écran de connexion qui, sans réseau, ne peut pas aboutir. Le conducteur
     * était enfermé dehors par la seule absence de couverture.
     *
     * On ne condamne donc que ce que le serveur a **explicitement** condamné
     * ([REVOKING_FAILURES]). Le reste — panne de transport, 5xx, 429, code
     * inconnu — laisse la session sur le disque et en mémoire : son jeton
     * d'accès est périmé, le prochain appel qui en a besoin le verra, et le
     * prochain [restore] retentera. Une session périmée qu'on garde est
     * réparable ; une session effacée hors ligne ne l'est pas.
     */
    private fun keepUnverified(stored: AuthSession, why: String): AuthSession {
        logger.warn(LogDomain.AUTH, "Session conservée sans vérification — $why.")
        session = stored
        return stored
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

    /**
     * ## Injoignable et refusé ne se ressemblent pas, et le contrat le dit
     *
     * Cette lecture décide qui entre dans Aule Pro. Tant qu'elle jetait une
     * `ApiException` brute, l'écran — qui ne voit pas `:core:network` — ne
     * pouvait que rattraper `Throwable` et fermer la session : un dépôt en
     * sous-sol suffisait à faire perdre son habilitation à un conducteur qui
     * l'avait.
     *
     * Une panne de transport, un 5xx, un 429 lèvent donc désormais
     * [AuthFailureKind.NETWORK] — « je n'ai pas pu demander ». Un 4xx, lui,
     * reste une réponse : le serveur a demandé et refusé.
     */
    override suspend fun fetchStaffRole(session: AuthSession): String? {
        if (!configured) return null
        val restBase = supabaseUrl.trimEnd('/') + "/rest/v1"
        val response = try {
            client.getRaw(
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
        } catch (cancelled: ApiException.Cancelled) {
            throw cancelled
        } catch (transport: ApiException.Transport) {
            throw AuthException(AuthFailureKind.NETWORK, transport.message)
        }
        when (response.code) {
            in 200..299 -> Unit
            404 -> return null
            429 -> throw AuthException(AuthFailureKind.NETWORK, "PostgREST a limité le débit.")
            502, 503, 504 -> throw AuthException(
                AuthFailureKind.NETWORK,
                ApiException.UpstreamUnavailable(response.code).message,
            )
            in 400..499 -> throw ApiException.BadRequest(response.code)
            else -> throw AuthException(
                AuthFailureKind.NETWORK,
                ApiException.Server(response.code).message,
            )
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
     * `GET /authorize` — l'URL que le navigateur ira chercher.
     *
     * Aucun appel réseau : GoTrue répond à cette adresse par une redirection
     * vers Google, et c'est le navigateur qui la suit. La construire ici plutôt
     * que dans l'écran garde au même endroit ce qui doit rester d'accord — le
     * défi PKCE écrit dans le dépôt et celui posé dans l'URL.
     *
     * `redirect_to` est la même adresse que les liens e-mail : elle est déjà
     * déclarée dans les *Redirect URLs* du projet, et Google n'en voit jamais
     * la couleur — il redirige vers Supabase, qui redirige vers nous.
     */
    override suspend fun beginOAuthSignUp(provider: OAuthProvider): String {
        if (!configured) throw AuthException(AuthFailureKind.NOT_CONFIGURED)
        val verifier = createVerifier()
        pkce.writeVerifier(verifier, AuthPkceFlow.OAUTH_SIGN_UP)
        val url = ("$authBase/authorize").toHttpUrl().newBuilder()
            .addQueryParameter("provider", provider.key)
            .addQueryParameter("redirect_to", EMAIL_CONFIRMATION_REDIRECT)
            .addQueryParameter("code_challenge", Pkce.challenge(verifier))
            .addQueryParameter("code_challenge_method", "s256")
            .build()
            .toString()
        logger.info(LogDomain.AUTH, "Inscription déléguée à ${provider.key}.")
        return url
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
                serverMessage = error.messageOr(response.code),
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
        val flow = pkce.readFlow()
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
        if (flow == AuthPkceFlow.OAUTH_SIGN_UP) {
            logger.info(LogDomain.AUTH, "Session ouverte par fournisseur externe.")
            attachOnboarding(opened)
        } else {
            logger.info(LogDomain.AUTH, "Session ouverte par confirmation d'e-mail.")
        }
        opened
    }

    /**
     * Pose sur le compte fraîchement ouvert ce que l'OAuth n'a pas pu porter.
     *
     * `/authorize` n'accepte pas de `data` : le métier, le réseau, le matricule
     * arrivent donc en second, par `PUT /user`, une fois la session ouverte.
     *
     * ## Un échec ici ne referme pas la session
     *
     * Elle est ouverte, elle est écrite, le compte existe : la refuser
     * laisserait un compte Google lié à Aule que son propriétaire ne pourrait
     * plus ni utiliser ni recréer. On journalise donc, en **gardant le
     * brouillon** — c'est ce qui permet de reprendre l'inscription là où elle
     * en était. Le compte reste sans habilitation demandée, et l'écran
     * d'habilitation dit déjà cette attente-là.
     *
     * Le brouillon n'est effacé qu'en cas de succès, et c'est le seul endroit
     * du parcours OAuth qui puisse le faire : l'écran d'inscription, lui, a
     * disparu au moment où le navigateur s'est ouvert.
     */
    private suspend fun attachOnboarding(opened: AuthSession) {
        val encoded = drafts.readDraft() ?: run {
            logger.warn(LogDomain.AUTH, "Retour de fournisseur sans brouillon d'inscription.")
            return
        }
        val metadata = try {
            ProRegistrationDraft.decode(encoded).toAuthMetadata()
        } catch (failure: Throwable) {
            logger.warn(LogDomain.AUTH, "Brouillon d'inscription illisible.", failure)
            return
        }
        val body = JsonObject(mapOf("data" to metadata)).toString()
        val response = try {
            client.putRaw(
                url = "$authBase/user",
                jsonBody = body,
                headers = authHeaders(opened.accessToken),
            )
        } catch (cancelled: ApiException.Cancelled) {
            throw cancelled
        } catch (failure: Throwable) {
            logger.warn(LogDomain.AUTH, "Métadonnées d'onboarding non posées.", failure)
            return
        }
        if (response.code !in 200..299) {
            logger.warn(
                LogDomain.AUTH,
                "Métadonnées d'onboarding refusées (${response.code}).",
            )
            return
        }
        drafts.clear()
        logger.info(LogDomain.AUTH, "Métadonnées d'onboarding posées.")
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
                serverMessage = error.messageOr(response.code),
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

    /**
     * Ce qu'on garde d'un refus dont le corps ne dit rien.
     *
     * GoTrue répond parfois un corps vide, ou un JSON dont aucun des trois
     * champs de message n'est rempli — c'est ce que rend une clé publiable
     * refusée. Le journal affichait alors « Connexion refusée (UNKNOWN) » tout
     * court : la **même** trace pour un mot de passe faux, une clé erronée et un
     * projet éteint, qui n'appellent pas le même geste. Le statut les sépare, et
     * il ne coûte rien à garder.
     *
     * Rien de cela n'atteint l'écran, qui traduit la catégorie et non ce champ :
     * c'est une trace de diagnostic, pas une phrase à lire.
     */
    private fun GoTrueErrorDto?.messageOr(status: Int): String =
        serverMessage() ?: "HTTP $status"

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
            serverMessage = error.messageOr(response.code),
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
            serverMessage = error.messageOr(response.code),
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
