package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.HandoverException
import io.aule.android.core.model.HandoverFailureKind
import io.aule.android.core.model.HandoverStatus
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.SupabaseHandoverRepository
import io.aule.android.data.dto.cleanTerminus
import kotlin.test.assertEquals
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

class SupabaseHandoverRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SupabaseHandoverRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = SupabaseHandoverRepository(
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
    fun `le lookup decode les candidats sans coordonnees`() = runTest {
        respond(LOOKUP_BODY)
        val found = repository.lookup(SESSION, "C6", "324")
        assertEquals(1, found.size)
        assertEquals("svc-out", found[0].serviceId)
        assertEquals("Hermeland", found[0].terminus)
        assertEquals("324", found[0].vehicleId)
        assertEquals(12, found[0].positionAgeSeconds)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.url.encodedPath.endsWith("/rest/v1/rpc/handover_lookup"))
        assertTrue(recorded.body?.utf8().orEmpty().contains("\"p_query\":\"324\""))
    }

    @Test
    fun `un headsign fleche se reduit au terminus`() {
        assertEquals("Hermeland", cleanTerminus("\u2192 Hermeland"))
        assertEquals("Hermeland", cleanTerminus("Hermeland"))
        assertNull(cleanTerminus("  "))
    }

    @Test
    fun `engager passe par handover_request`() = runTest {
        respond(SUMMARY_BODY)
        val summary = repository.request(SESSION, "svc-out")
        assertEquals("hov-1", summary.id)
        assertEquals(HandoverStatus.ENGAGED, summary.status)
        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.endsWith("/rest/v1/rpc/handover_request"))
        assertTrue(recorded.body?.utf8().orEmpty().contains("\"p_outgoing_service_id\":\"svc-out\""))
    }

    @Test
    fun `poser l arret passe par handover_set_stop`() = runTest {
        respond(SUMMARY_BODY_WITH_STOP)
        val summary = repository.setStop(
            session = SESSION,
            handoverId = "hov-1",
            stopId = "2",
            stopName = "Commerce",
            latitude = 47.2134,
            longitude = -1.558,
            plannedAt = null,
        )
        assertEquals("2", summary.reliefStopId)
        assertEquals("Commerce", summary.reliefStopName)
        assertEquals(47.2134, summary.reliefStopCoordinate?.latitude)
        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.endsWith("/rest/v1/rpc/handover_set_stop"))
        val body = recorded.body?.utf8().orEmpty()
        assertTrue(body.contains("\"p_stop_id\":\"2\""))
        assertTrue(body.contains("\"p_stop_name\":\"Commerce\""))
        assertTrue(body.contains("\"p_planned_at\":null"))
    }

    @Test
    fun `confirmer rend le service de l arrivant`() = runTest {
        respond(CONFIRMED_BODY)
        val summary = repository.confirm(SESSION, "hov-1")
        assertEquals("svc-in", summary.incomingServiceId)
        assertEquals(HandoverStatus.COMPLETED, summary.status)
    }

    @Test
    fun `le suivi decode la position du collegue`() = runTest {
        respond(TRACK_BODY)
        val track = repository.track(SESSION, "hov-1")
        assertEquals("hov-1", track.handover.id)
        assertEquals(HandoverStatus.ENGAGED, track.handover.status)
        assertEquals(47.2184, track.fix?.coordinate?.latitude)
        assertEquals(12, track.fix?.ageSeconds)
        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.endsWith("/rest/v1/rpc/handover_track"))
        assertTrue(recorded.body?.utf8().orEmpty().contains("\"p_handover_id\":\"hov-1\""))
    }

    @Test
    fun `sans releve a reprendre on rend null`() = runTest {
        respond("null")
        assertNull(repository.activeForMe(SESSION))
    }

    @Test
    fun `deja en service se lit depuis le message RPC`() = runTest {
        respond("""{"message":"incoming_already_on_service"}""", status = 400)
        val failure = assertThrows<HandoverException> {
            repository.confirm(SESSION, "hov-1")
        }
        assertEquals(HandoverFailureKind.ALREADY_ON_SERVICE, failure.kind)
    }

    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse.Builder().code(status).body(body).build())
    }

    private companion object {
        val SESSION = AuthSession(
            user = AuthUser("user-1", "agent@aule.fr"),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 9_999_999_999L,
        )

        const val LOOKUP_BODY = """
            [{
              "outgoing_service_id": "svc-out",
              "driver_display": "Martin",
              "line_id": "C6",
              "direction_id": 0,
              "headsign": "Hermeland",
              "vehicle_id": "324",
              "train_number": "1-12",
              "started_at": "2026-08-16T15:00:00Z",
              "last_position_at": "2026-08-16T15:58:00Z",
              "position_age_seconds": 12
            }]
        """

        const val SUMMARY_BODY = """
            {
              "id": "hov-1",
              "status": "engaged",
              "line_id": "C6",
              "vehicle_id": "324",
              "outgoing_service_id": "svc-out",
              "incoming_service_id": null,
              "outgoing_display": "Martin"
            }
        """

        const val SUMMARY_BODY_WITH_STOP = """
            {
              "id": "hov-1",
              "status": "engaged",
              "line_id": "C6",
              "vehicle_id": "324",
              "outgoing_service_id": "svc-out",
              "incoming_service_id": null,
              "outgoing_display": "Martin",
              "relief_stop_id": "2",
              "relief_stop_name": "Commerce",
              "relief_stop_lat": 47.2134,
              "relief_stop_lon": -1.558
            }
        """

        const val CONFIRMED_BODY = """
            {
              "id": "hov-1",
              "status": "completed",
              "line_id": "C6",
              "vehicle_id": "324",
              "outgoing_service_id": "svc-out",
              "incoming_service_id": "svc-in"
            }
        """

        const val TRACK_BODY = """
            {
              "service_status": "active",
              "server_time": "2026-08-16T16:00:00Z",
              "handover": {
                "id": "hov-1",
                "status": "engaged",
                "line_id": "C6",
                "outgoing_service_id": "svc-out",
                "outgoing_display": "Martin"
              },
              "position": {
                "latitude": 47.2184,
                "longitude": -1.5536,
                "speed": 8.0,
                "heading": 90.0,
                "accuracy": 6.0,
                "recorded_at": "2026-08-16T15:59:48Z",
                "age_seconds": 12
              }
            }
        """
    }
}
