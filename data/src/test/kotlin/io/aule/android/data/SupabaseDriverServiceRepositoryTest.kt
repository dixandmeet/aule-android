package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.DriverServiceException
import io.aule.android.core.model.DriverServiceFailureKind
import io.aule.android.core.model.PositionPublishRequest
import io.aule.android.core.model.ServiceStartRequest
import io.aule.android.core.model.TransportMode
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.SupabaseDriverServiceRepository
import java.time.Instant
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

class SupabaseDriverServiceRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SupabaseDriverServiceRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = SupabaseDriverServiceRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            supabaseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "sb_publishable_test",
            now = { Instant.parse("2026-08-16T16:00:00Z") },
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `les lignes se decodent depuis gtfs_routes`() = runTest {
        respond(ROUTES_BODY)
        val lines = repository.fetchLines(SESSION)
        assertEquals(2, lines.size)
        assertEquals("C6", lines[0].label)
        assertEquals(TransportMode.BUS, lines[0].mode)
        assertEquals("Hermeland", lines[0].directions[0].terminus)
        assertEquals("Chantrerie", lines[0].directions[1].terminus)
        assertEquals("1", lines[1].label)
        assertEquals(TransportMode.TRAM, lines[1].mode)
        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.endsWith("/rest/v1/gtfs_routes"))
    }

    @Test
    fun `la desserte prend le circuit le plus long`() = runTest {
        respond(TRIPS_BODY)
        respond(STOP_TIMES_BODY)
        val journey = repository.fetchJourney(SESSION, "C6", 0)
        assertEquals("trip-long", journey.tripId)
        assertEquals(listOf("Hermeland", "Commerce", "Chantrerie"), journey.stops.map { it.name })
        assertEquals(47.2134, journey.stops[1].coordinate?.latitude)
        val trips = server.takeRequest()
        assertTrue(trips.url.encodedPath.endsWith("/rest/v1/gtfs_trips"))
        assertEquals("eq.C6", trips.url.queryParameter("route_id"))
        val times = server.takeRequest()
        assertTrue(times.url.encodedPath.endsWith("/rest/v1/gtfs_stop_times"))
        assertTrue(times.url.queryParameter("trip_id").orEmpty().contains("trip-long"))
    }

    @Test
    fun `un catalogue vide est une panne`() = runTest {
        respond("[]")
        val failure = assertThrows<DriverServiceException> { repository.fetchLines(SESSION) }
        assertEquals(DriverServiceFailureKind.LINES_EMPTY, failure.kind)
    }

    @Test
    fun `un service ouvert se relit`() = runTest {
        respond("""[{"id":"drv-1"}]""")
        respond(ACTIVE_BODY)
        val active = repository.fetchActiveService(SESSION)
        assertEquals("svc-1", active?.id)
        assertEquals("C6", active?.lineId)
        assertEquals(0, active?.directionId)
    }

    @Test
    fun `sans fiche on n invente pas de service`() = runTest {
        respond("[]")
        assertNull(repository.fetchActiveService(SESSION))
    }

    @Test
    fun `demarrer passe par le RPC et rend l identifiant`() = runTest {
        respond("[]")
        respond("\"svc-9\"")
        val started = repository.startService(
            SESSION,
            ServiceStartRequest(
                lineId = "C6",
                lineLabel = "C6",
                directionId = 0,
                terminus = "Hermeland",
                vehicleId = "1234",
            ),
        )
        assertEquals("svc-9", started.id)
        assertEquals("C6", started.lineLabel)
        assertEquals("1234", started.vehicleId)
        server.takeRequest()
        val rpc = server.takeRequest()
        assertEquals("POST", rpc.method)
        assertTrue(rpc.url.encodedPath.endsWith("/rest/v1/rpc/driver_service_start"))
        val body = rpc.body?.utf8().orEmpty()
        assertTrue(body.contains("\"p_line_id\":\"C6\""))
        assertTrue(body.contains("\"p_direction_id\":0"))
        assertTrue(body.contains("\"p_headsign\":\"Hermeland\""))
        assertTrue(body.contains("\"p_vehicle_id\":\"1234\""))
    }

    @Test
    fun `publier la position rend l etat serveur`() = runTest {
        respond(HEARTBEAT_BODY)
        val beat = repository.publishPosition(
            SESSION,
            PositionPublishRequest(
                driverServiceId = "svc-1",
                latitude = 47.21,
                longitude = -1.55,
                vehicleId = "324",
                speed = 8.0,
                heading = 90.0,
                accuracy = 6.0,
            ),
        )
        assertEquals("active", beat.serviceStatus)
        assertTrue(beat.published)
        assertEquals("hov-1", beat.handover?.id)
        assertEquals("Camille", beat.handover?.incomingDisplay)
        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.endsWith("/rest/v1/rpc/publish_position_with_state"))
        val body = recorded.body?.utf8().orEmpty()
        assertTrue(body.contains("\"p_driver_service_id\":\"svc-1\""))
        assertTrue(body.contains("\"p_latitude\":47.21"))
        assertTrue(body.contains("\"p_vehicle_id\":\"324\""))
    }

    @Test
    fun `un service deja ouvert refuse un second demarrage`() = runTest {
        respond("""[{"id":"drv-1"}]""")
        respond(ACTIVE_BODY)
        val failure = assertThrows<DriverServiceException> {
            repository.startService(
                SESSION,
                ServiceStartRequest("C6", "C6", 0, "Hermeland"),
            )
        }
        assertEquals(DriverServiceFailureKind.ALREADY_ON_SERVICE, failure.kind)
    }

    @Test
    fun `cloturer passe en completed`() = runTest {
        respond(ACTIVE_BODY)
        repository.endService(SESSION, "svc-1")
        val recorded = server.takeRequest()
        assertEquals("PATCH", recorded.method)
        assertEquals("eq.svc-1", recorded.url.queryParameter("id"))
        assertTrue(recorded.body?.utf8().orEmpty().contains("\"status\":\"completed\""))
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

        const val ROUTES_BODY = """
            [
              {
                "route_id": "C6",
                "route_short_name": "C6",
                "route_long_name": "Hermeland - Chantrerie",
                "route_type": 3,
                "route_color": "00A3E0",
                "network_id": "net-nan"
              },
              {
                "route_id": "1",
                "route_short_name": "1",
                "route_long_name": "François Mitterrand - Ranzay",
                "route_type": 0,
                "route_color": "E40046",
                "network_id": "net-nan"
              }
            ]
        """

        const val ACTIVE_BODY = """
            [{
              "id": "svc-1",
              "line_id": "C6",
              "direction_id": 0,
              "headsign": "Hermeland",
              "vehicle_id": null,
              "train_number": null,
              "start_time_real": "2026-08-16T15:00:00Z",
              "created_at": "2026-08-16T15:00:00Z"
            }]
        """

        const val TRIPS_BODY = """
            [
              {"trip_id": "trip-short", "direction_id": 0},
              {"trip_id": "trip-long", "direction_id": 0}
            ]
        """

        const val STOP_TIMES_BODY = """
            [
              {
                "trip_id": "trip-short",
                "stop_sequence": 1,
                "gtfs_stops": {
                  "stop_id": "h1",
                  "stop_name": "Hermeland",
                  "geom": {"type": "Point", "coordinates": [-1.52, 47.29]}
                }
              },
              {
                "trip_id": "trip-short",
                "stop_sequence": 2,
                "gtfs_stops": {
                  "stop_id": "c1",
                  "stop_name": "Chantrerie",
                  "geom": {"type": "Point", "coordinates": [-1.52, 47.28]}
                }
              },
              {
                "trip_id": "trip-long",
                "stop_sequence": 1,
                "gtfs_stops": {
                  "stop_id": "h1",
                  "stop_name": "Hermeland",
                  "geom": {"type": "Point", "coordinates": [-1.52, 47.29]}
                }
              },
              {
                "trip_id": "trip-long",
                "stop_sequence": 2,
                "gtfs_stops": {
                  "stop_id": "co1",
                  "stop_name": "Commerce",
                  "geom": {"type": "Point", "coordinates": [-1.558, 47.2134]}
                }
              },
              {
                "trip_id": "trip-long",
                "stop_sequence": 3,
                "gtfs_stops": {
                  "stop_id": "c1",
                  "stop_name": "Chantrerie",
                  "geom": {"type": "Point", "coordinates": [-1.52, 47.28]}
                }
              }
            ]
        """

        const val HEARTBEAT_BODY = """
            {
              "service_status": "active",
              "published": true,
              "server_time": "2026-08-16T16:00:00Z",
              "handover": {
                "id": "hov-1",
                "status": "engaged",
                "line_id": "C6",
                "outgoing_service_id": "svc-1",
                "incoming_display": "Camille",
                "relief_stop_name": "Commerce",
                "relief_planned_at": "2026-08-16T15:42:00Z"
              }
            }
        """
    }
}
