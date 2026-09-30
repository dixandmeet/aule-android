package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthException
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.NetworkFailureReason
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.MemoryAuthSessionStore
import io.aule.android.data.aule.SupabaseAuthRepository
import io.aule.android.data.aule.networkReasonOf
import io.aule.android.data.aule.unavailableIf
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Ce qu'une panne de réseau dit d'elle-même.
 *
 * `AuthFailureKind.NETWORK` reste le genre — Aule Pro décide dessus qu'il peut rouvrir un
 * conducteur sur ses dernières habilitations connues —, et [AuthException.networkReason] en est
 * le détail : le Voyageur s'en sert pour dire la bonne chose. Hors connexion, on vérifie son
 * réseau ; un délai dépassé, on réessaie ; un serveur en panne, on attend.
 */
class AuthNetworkReasonTest {

    @Test
    fun `sans reseau, la requete n'est jamais partie`() {
        assertEquals(NetworkFailureReason.OFFLINE, networkReasonOf(ApiException.Transport(UnknownHostException("aule"))))
        assertEquals(NetworkFailureReason.OFFLINE, networkReasonOf(ApiException.Transport(ConnectException("refusée"))))
    }

    @Test
    fun `un delai depasse se dit comme tel, qu'il vienne de la socket ou de l'appel entier`() {
        assertEquals(NetworkFailureReason.TIMEOUT, networkReasonOf(ApiException.Transport(SocketTimeoutException())))
        // Le délai d'appel d'OkHttp lève une `InterruptedIOException` nue.
        assertEquals(NetworkFailureReason.TIMEOUT, networkReasonOf(ApiException.Transport(InterruptedIOException("timeout"))))
    }

    @Test
    fun `une panne qu'on ne sait pas nommer reste sans detail`() {
        assertNull(networkReasonOf(ApiException.Transport(IllegalStateException("?"))))
    }

    @Test
    fun `seule une panne du serveur est une indisponibilite`() {
        assertEquals(NetworkFailureReason.UNAVAILABLE, unavailableIf(503))
        assertEquals(NetworkFailureReason.UNAVAILABLE, unavailableIf(500))
        assertNull(unavailableIf(400))
        assertNull(unavailableIf(429))
    }

    @Test
    fun `une connexion qui tombe sur un 503 est une indisponibilite, et garde son genre`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse.Builder().code(503).body("""{"msg":"Service Unavailable"}""").build())
            val repository = SupabaseAuthRepository(
                client = AuleHttpClient(OkHttpClient(), NoopLogger),
                store = MemoryAuthSessionStore(),
                supabaseUrl = server.url("/").toString().trimEnd('/'),
                publishableKey = "sb_publishable_test",
                logger = NoopLogger,
                nowEpochSeconds = { 1_700_000_000L },
            )
            val refus = assertThrows<AuthException> { repository.signIn("camille@exemple.fr", "motdepasse") }
            assertEquals(NetworkFailureReason.UNAVAILABLE, refus.networkReason)
            // Le genre, lui, ne change pas : un 5xx reste `NETWORK`, celui qu'Aule Pro connaît.
            assertEquals(AuthFailureKind.NETWORK, refus.kind)
        } finally {
            server.close()
        }
    }
}
