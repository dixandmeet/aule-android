package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.DriverReport
import io.aule.android.core.model.DriverReportException
import io.aule.android.core.model.DriverReportFailureKind
import io.aule.android.core.model.DriverReportType
import io.aule.android.core.model.DriverReportUrgency
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.SupabaseDriverReportRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SupabaseDriverReportRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SupabaseDriverReportRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = SupabaseDriverReportRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            supabaseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "sb_publishable_test",
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `un signalement resout le conducteur puis insert`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""[{"id":"drv-1"}]""").build())
        server.enqueue(MockResponse.Builder().code(201).body("").build())

        repository.submit(
            SESSION,
            DriverReport(
                type = DriverReportType.ACCIDENT,
                urgency = DriverReportUrgency.HIGH,
                message = "  Carrefour bloqué  ",
                latitude = 47.21,
                longitude = -1.55,
            ),
        )

        val lookup = server.takeRequest()
        assertTrue(lookup.url.encodedPath.endsWith("/rest/v1/drivers"))
        assertEquals("id", lookup.url.queryParameter("select"))
        assertEquals("Bearer access-1", lookup.headers["Authorization"])

        val insert = server.takeRequest()
        assertEquals("POST", insert.method)
        assertTrue(insert.url.encodedPath.endsWith("/rest/v1/driver_reports"))
        assertEquals("return=minimal", insert.headers["Prefer"])
        val body = insert.body?.utf8().orEmpty()
        assertTrue(body.contains("\"driver_id\":\"drv-1\""))
        assertTrue(body.contains("\"type\":\"accident\""))
        assertTrue(body.contains("\"urgency\":\"high\""))
        assertTrue(body.contains("\"message\":\"Carrefour bloqué\""))
        assertTrue(body.contains("\"latitude\":47.21"))
        assertTrue(body.contains("\"longitude\":-1.55"))
        assertFalse(body.contains("driver_service_id"))
        assertFalse(body.contains("vehicle_id"))
    }

    @Test
    fun `sans fiche conducteur on n insert pas`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("[]").build())
        val failure = assertThrows<DriverReportException> {
            repository.submit(SESSION, DriverReport(type = DriverReportType.TRAFFIC))
        }
        assertEquals(DriverReportFailureKind.NO_DRIVER, failure.kind)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `un refus PostgREST se lit comme un rejet`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""[{"id":"drv-1"}]""").build())
        server.enqueue(MockResponse.Builder().code(400).body("""{"message":"invalid"}""").build())
        val failure = assertThrows<DriverReportException> {
            repository.submit(SESSION, DriverReport(type = DriverReportType.OTHER))
        }
        assertEquals(DriverReportFailureKind.REJECTED, failure.kind)
    }

    @Test
    fun `sans configuration on ne parle pas au reseau`() = runTest {
        val bare = SupabaseDriverReportRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            supabaseUrl = "",
            publishableKey = "",
        )
        val failure = assertThrows<DriverReportException> {
            bare.submit(SESSION, DriverReport(type = DriverReportType.DELAY))
        }
        assertEquals(DriverReportFailureKind.NOT_CONFIGURED, failure.kind)
        assertEquals(0, server.requestCount)
    }

    private companion object {
        val SESSION = AuthSession(
            user = AuthUser(id = "user-1", email = "agent@aule.fr"),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 2_000_000_000,
        )
    }
}
