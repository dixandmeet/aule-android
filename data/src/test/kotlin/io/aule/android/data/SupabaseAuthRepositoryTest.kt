package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthException
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.AuthPkceFlow
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.ProRegistrationDraft
import io.aule.android.core.model.ProfessionalProfile
import io.aule.android.core.model.ProfessionalTransportMode
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.EMAIL_CONFIRMATION_REDIRECT
import io.aule.android.data.aule.MemoryAuthPkceStore
import io.aule.android.data.aule.MemoryAuthSessionStore
import io.aule.android.data.aule.SupabaseAuthRepository
import io.aule.android.data.auth.Pkce
import io.aule.android.data.dto.GoTrueErrorDto
import io.aule.android.data.dto.authFailureKindOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SupabaseAuthRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SupabaseAuthRepository
    private val store = MemoryAuthSessionStore()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = SupabaseAuthRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            store = store,
            supabaseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "sb_publishable_test",
            logger = NoopLogger,
            nowEpochSeconds = { 1_700_000_000L },
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse.Builder().code(status).body(body).build())
    }

    @Test
    fun `une connexion valide ouvre et persiste la session`() = runTest {
        respond(TOKEN_BODY)

        val session = repository.signIn("Agent@Aule.fr", "secret")

        assertEquals("agent@aule.fr", session.user.email)
        assertEquals("user-1", session.user.id)
        assertEquals("access-1", session.accessToken)
        assertEquals(1_700_000_000L + 3_600, session.expiresAtEpochSeconds)
        assertEquals(session, store.read())
        assertEquals(session, repository.currentSession())

        val recorded = server.takeRequest()
        assertTrue(recorded.url.toString().contains("grant_type=password"))
        assertEquals("sb_publishable_test", recorded.headers["apikey"])
        assertTrue(recorded.body?.utf8()?.contains("agent@aule.fr") == true)
    }

    @Test
    fun `des identifiants faux deviennent invalid_credentials`() = runTest {
        respond(
            """{"error_code":"invalid_credentials","msg":"Invalid login credentials"}""",
            status = 400,
        )

        val failure = assertThrows<AuthException> {
            repository.signIn("a@b.fr", "nope")
        }
        assertEquals(AuthFailureKind.INVALID_CREDENTIALS, failure.kind)
        assertNull(repository.currentSession())
    }

    @Test
    fun `sans configuration on ne parle pas au reseau`() = runTest {
        val bare = SupabaseAuthRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            store = MemoryAuthSessionStore(),
            supabaseUrl = "",
            publishableKey = "",
            logger = NoopLogger,
        )
        val failure = assertThrows<AuthException> {
            bare.signIn("a@b.fr", "x")
        }
        assertEquals(AuthFailureKind.NOT_CONFIGURED, failure.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `restore rattache une session encore valide`() = runTest {
        respond(TOKEN_BODY)
        repository.signIn("a@b.fr", "secret")
        val restarted = SupabaseAuthRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            store = store,
            supabaseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "sb_publishable_test",
            logger = NoopLogger,
            nowEpochSeconds = { 1_700_000_000L },
        )
        val restored = assertNotNull(restarted.restore())
        assertEquals("agent@aule.fr", restored.user.email)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `restore rafraichit un jeton expire`() = runTest {
        store.write(
            AuthSession(
                user = AuthUser("user-1", "a@b.fr"),
                accessToken = "old",
                refreshToken = "refresh-1",
                expiresAtEpochSeconds = 1_000L,
            ),
        )
        respond(TOKEN_BODY)

        val restored = assertNotNull(repository.restore())
        assertEquals("access-1", restored.accessToken)
        val recorded = server.takeRequest()
        assertTrue(recorded.url.toString().contains("grant_type=refresh_token"))
    }

    @Test
    fun `un role staff se lit dans user_profiles`() = runTest {
        respond("""[{"role":"driver"}]""")
        val role = repository.fetchStaffRole(
            AuthSession(
                user = AuthUser("user-1", "agent@aule.fr"),
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresAtEpochSeconds = 9_999_999_999L,
            ),
        )
        assertEquals("driver", role)
        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.endsWith("/rest/v1/user_profiles"))
        assertEquals("eq.user-1", recorded.url.queryParameter("id"))
        assertEquals("role", recorded.url.queryParameter("select"))
        assertEquals("Bearer access-1", recorded.headers["Authorization"])
    }

    @Test
    fun `une ligne user_profiles vide n est pas une panne`() = runTest {
        respond("[]")
        assertNull(
            repository.fetchStaffRole(
                AuthSession(
                    user = AuthUser("user-1", "agent@aule.fr"),
                    accessToken = "access-1",
                    refreshToken = "refresh-1",
                    expiresAtEpochSeconds = 9_999_999_999L,
                ),
            ),
        )
    }

    @Test
    fun `la table des codes GoTrue est complete`() {
        assertEquals(
            AuthFailureKind.EMAIL_NOT_CONFIRMED,
            authFailureKindOf(400, GoTrueErrorDto(errorCode = "email_not_confirmed")),
        )
        assertEquals(
            AuthFailureKind.RATE_LIMITED,
            authFailureKindOf(429, null),
        )
        assertEquals(
            AuthFailureKind.INVALID_CREDENTIALS,
            authFailureKindOf(400, GoTrueErrorDto(error = "invalid_grant")),
        )
        assertEquals(
            AuthFailureKind.USER_ALREADY_EXISTS,
            authFailureKindOf(422, GoTrueErrorDto(errorCode = "user_already_exists")),
        )
    }

    @Test
    fun `l inscription professionnelle envoie les metadonnees et le defi PKCE`() = runTest {
        val pkce = MemoryAuthPkceStore()
        val verifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ012"
        val signing = SupabaseAuthRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            store = store,
            supabaseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "sb_publishable_test",
            logger = NoopLogger,
            pkce = pkce,
            createVerifier = { verifier },
            nowEpochSeconds = { 1_700_000_000L },
        )
        respond("""{"id":"user-1","email":"camille@example.com"}""")

        signing.signUpProfessional(
            draft = ProRegistrationDraft(
                profiles = setOf(ProfessionalProfile.CONDUCTEUR),
                networkKey = "naolib",
                fullName = "Camille Martin",
                employeeId = "48271",
                transportMode = ProfessionalTransportMode.BUS,
                email = "Camille@Example.com",
                termsAccepted = true,
            ),
            password = "secretsecret",
        )

        assertNull(signing.currentSession())
        assertNull(store.read())
        assertEquals(verifier, pkce.readVerifier())

        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.endsWith("/auth/v1/signup"))
        assertEquals(EMAIL_CONFIRMATION_REDIRECT, recorded.url.queryParameter("redirect_to"))
        val body = recorded.body?.utf8().orEmpty()
        assertTrue("camille@example.com" in body)
        assertTrue("\"role\":\"driver\"" in body)
        assertTrue("\"onboarding_profile\":\"conducteur\"" in body)
        assertTrue("\"code_challenge_method\":\"s256\"" in body)
        assertTrue(Pkce.challenge(verifier) in body)
        assertTrue("secretsecret" in body)
    }

    @Test
    fun `un compte deja existant devient user_already_exists`() = runTest {
        respond(
            """{"error_code":"user_already_exists","msg":"User already registered"}""",
            status = 422,
        )
        val failure = assertThrows<AuthException> {
            repository.signUpProfessional(
                draft = ProRegistrationDraft(
                    profiles = setOf(ProfessionalProfile.CONTROLEUR),
                    networkKey = "naolib",
                    fullName = "Sam Dupont",
                    employeeId = "MSR21",
                    email = "sam@example.com",
                    termsAccepted = true,
                ),
                password = "secretsecret",
            )
        }
        assertEquals(AuthFailureKind.USER_ALREADY_EXISTS, failure.kind)
        assertNull(repository.currentSession())
    }

    @Test
    fun `le renvoi de confirmation reprend le redirect PKCE`() = runTest {
        val pkce = MemoryAuthPkceStore()
        val verifier = "resend-verifier-abcdefghijklmnopqrstuvwxyz012345"
        val signing = SupabaseAuthRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            store = store,
            supabaseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "sb_publishable_test",
            logger = NoopLogger,
            pkce = pkce,
            createVerifier = { verifier },
        )
        respond("{}")

        signing.resendSignupConfirmation("Sam@Example.com")

        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.endsWith("/auth/v1/resend"))
        assertEquals(EMAIL_CONFIRMATION_REDIRECT, recorded.url.queryParameter("redirect_to"))
        val body = recorded.body?.utf8().orEmpty()
        assertTrue("sam@example.com" in body)
        assertTrue("\"type\":\"signup\"" in body)
        assertEquals(verifier, pkce.readVerifier())
    }

    @Test
    fun `le code du deep link ouvre la session via PKCE`() = runTest {
        val pkce = MemoryAuthPkceStore()
        pkce.writeVerifier("stored-verifier")
        val signing = SupabaseAuthRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            store = store,
            supabaseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "sb_publishable_test",
            logger = NoopLogger,
            pkce = pkce,
            nowEpochSeconds = { 1_700_000_000L },
        )
        respond(TOKEN_BODY)

        val session = signing.exchangeAuthCode("auth-code-1")

        assertEquals("agent@aule.fr", session.user.email)
        assertEquals(session, store.read())
        assertEquals(session, signing.currentSession())
        assertNull(pkce.readVerifier())
        val recorded = server.takeRequest()
        assertTrue(recorded.url.toString().contains("grant_type=pkce"))
        val body = recorded.body?.utf8().orEmpty()
        assertTrue("auth-code-1" in body)
        assertTrue("stored-verifier" in body)
    }

    @Test
    fun `le lien de recuperation reprend le redirect PKCE et marque le genre`() = runTest {
        val pkce = MemoryAuthPkceStore()
        val verifier = "recover-verifier-abcdefghijklmnopqrstuvwxyz0123"
        val recovering = SupabaseAuthRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            store = store,
            supabaseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "sb_publishable_test",
            logger = NoopLogger,
            pkce = pkce,
            createVerifier = { verifier },
        )
        respond("{}")

        recovering.sendPasswordRecovery("Sam@Example.com")

        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.endsWith("/auth/v1/recover"))
        assertEquals(EMAIL_CONFIRMATION_REDIRECT, recorded.url.queryParameter("redirect_to"))
        val body = recorded.body?.utf8().orEmpty()
        assertTrue("sam@example.com" in body)
        assertTrue("\"code_challenge_method\":\"s256\"" in body)
        assertTrue(Pkce.challenge(verifier) in body)
        assertEquals(verifier, pkce.readVerifier())
        // Le genre est ce qui, au retour, interdira d'entrer dans l'application
        // par la boîte e-mail. Il s'écrit ici et nulle part ailleurs.
        assertEquals(AuthPkceFlow.RECOVERY, recovering.pendingAuthFlow())
    }

    @Test
    fun `une inscription laisse le genre a SIGN_UP`() = runTest {
        val pkce = MemoryAuthPkceStore()
        val signing = SupabaseAuthRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            store = store,
            supabaseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "sb_publishable_test",
            logger = NoopLogger,
            pkce = pkce,
        )
        respond("{}")

        signing.resendSignupConfirmation("sam@example.com")

        assertEquals(AuthPkceFlow.SIGN_UP, signing.pendingAuthFlow())
    }

    @Test
    fun `une adresse inconnue ne se distingue pas d une adresse connue`() = runTest {
        // GoTrue répond 200 dans les deux cas, exprès : c'est ce qui empêche de
        // découvrir qui est inscrit. Le dépôt ne rattrape rien.
        respond("{}")
        repository.sendPasswordRecovery("inconnu@example.com")
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `le nouveau mot de passe part en PUT sous le jeton de session`() = runTest {
        respond(TOKEN_BODY)
        val session = repository.signIn("agent@aule.fr", "secret")
        server.takeRequest()
        respond("""{"id":"user-1","email":"agent@aule.fr"}""")

        repository.updatePassword("nouveau-secret")

        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertTrue(recorded.url.encodedPath.endsWith("/auth/v1/user"))
        assertEquals("Bearer access-1", recorded.headers["Authorization"])
        assertTrue("nouveau-secret" in recorded.body?.utf8().orEmpty())
        // La réponse ne rend pas de jetons : la session en cours reste la même.
        assertEquals(session, repository.currentSession())
    }

    @Test
    fun `un mot de passe trop court ne part pas sur le reseau`() = runTest {
        respond(TOKEN_BODY)
        repository.signIn("agent@aule.fr", "secret")
        server.takeRequest()

        val failure = assertThrows<AuthException> { repository.updatePassword("court") }

        assertEquals(AuthFailureKind.WEAK_PASSWORD, failure.kind)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `sans session ouverte il n y a rien a changer`() = runTest {
        val failure = assertThrows<AuthException> {
            repository.updatePassword("un-mot-de-passe-valable")
        }
        assertEquals(AuthFailureKind.UNKNOWN, failure.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `un refus GoTrue sur le mot de passe se traduit`() = runTest {
        respond(TOKEN_BODY)
        repository.signIn("agent@aule.fr", "secret")
        server.takeRequest()
        respond(
            """{"error_code":"weak_password","msg":"Password is too weak"}""",
            status = 422,
        )

        val failure = assertThrows<AuthException> {
            repository.updatePassword("motdepasse")
        }
        assertEquals(AuthFailureKind.WEAK_PASSWORD, failure.kind)
    }

    @Test
    fun `la suppression reussie appelle le RPC puis ferme la session`() = runTest {
        respond(TOKEN_BODY)
        repository.signIn("agent@aule.fr", "secret")
        server.takeRequest()
        respond("", 204)
        respond("", 204)

        repository.deleteAccount()

        val rpc = server.takeRequest()
        assertTrue(rpc.url.toString().contains("rpc/delete_my_account"))
        assertEquals("{}", rpc.body?.utf8())
        assertNull(repository.currentSession())
        assertNull(store.read())
    }

    @Test
    fun `un RPC en echec ne ferme pas la session`() = runTest {
        respond(TOKEN_BODY)
        val session = repository.signIn("agent@aule.fr", "secret")
        server.takeRequest()
        respond("""{"message":"boom"}""", 500)

        val thrown = assertThrows<AuthException> { repository.deleteAccount() }
        assertEquals(AuthFailureKind.NETWORK, thrown.kind)
        assertEquals(session, repository.currentSession())
        assertEquals(session, store.read())
    }

    private companion object {
        const val TOKEN_BODY = """
            {
              "access_token": "access-1",
              "refresh_token": "refresh-1",
              "expires_in": 3600,
              "token_type": "bearer",
              "user": { "id": "user-1", "email": "agent@aule.fr" }
            }
        """
    }
}
