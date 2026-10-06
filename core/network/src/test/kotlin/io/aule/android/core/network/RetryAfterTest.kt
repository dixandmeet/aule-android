package io.aule.android.core.network

import io.aule.android.core.common.log.NoopLogger
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.fail

class RetryAfterTest {
    @Test
    fun `les erreurs HTTP typees conservent Retry After sans changer leur classification`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = AuleHttpClient(OkHttpClient(), NoopLogger)
            for (status in listOf(429, 503, 500)) {
                server.enqueue(MockResponse.Builder().code(status)
                    .addHeader("retry-after", "600").body("{}").build())
                val failure = try {
                    client.getText(server.url("/").toString())
                    fail("Une réponse HTTP $status doit échouer")
                } catch (error: ApiException) { error }
                assertEquals("600", failure.retryAfter)
                when (status) {
                    429 -> assertIs<ApiException.BadRequest>(failure)
                    503 -> assertIs<ApiException.UpstreamUnavailable>(failure)
                    else -> assertIs<ApiException.Server>(failure)
                }
            }
        }
    }

    @Test
    fun `date HTTP conservee exactement et absence d en tete reste explicite`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = AuleHttpClient(OkHttpClient(), NoopLogger)
            for (header in listOf("Sun, 04 Oct 2026 12:03:00 GMT", null)) {
                val response = MockResponse.Builder().code(503).body("{}")
                if (header != null) response.addHeader("Retry-After", header)
                server.enqueue(response.build())
                val failure = try {
                    client.getText(server.url("/").toString())
                    fail("Le fournisseur doit échouer")
                } catch (error: ApiException.UpstreamUnavailable) { error }
                if (header != null) assertEquals(header, failure.retryAfter) else assertNull(failure.retryAfter)
            }
        }
    }
}
