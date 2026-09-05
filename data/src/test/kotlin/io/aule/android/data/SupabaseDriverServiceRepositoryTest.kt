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
    /**
     * ⚠️ **Le plus court, et non le plus fourni** — la règle a été renversée.
     *
     * Les deux courses relient les mêmes bouts ; la longue insère un arrêt entre
     * les deux. Entre deux mêmes terminus, un arrêt de plus est un **détour**,
     * pas le trajet ordinaire. Sur la C1, cette préférence pour le plus fourni
     * peignait neuf arrêts hors du tracé de la ligne — un crochet par Procé que
     * le service courant ne fait pas.
     */
    fun `la desserte prend le parcours de reference et non le detour`() = runTest {
        respond(TRIPS_BODY)
        respond(STOP_TIMES_BODY)
        val journey = repository.fetchJourney(SESSION, "C6", 0)
        assertEquals("trip-short", journey.tripId)
        assertEquals(listOf("Hermeland", "Chantrerie"), journey.stops.map { it.name })
        val trips = server.takeRequest()
        assertTrue(trips.url.encodedPath.endsWith("/rest/v1/gtfs_trips"))
        assertEquals("eq.C6", trips.url.queryParameter("route_id"))
        val times = server.takeRequest()
        assertTrue(times.url.encodedPath.endsWith("/rest/v1/gtfs_stop_times"))
        assertTrue(times.url.queryParameter("trip_id").orEmpty().contains("trip-long"))
    }

    /**
     * Les branches sont **rendues**, la référence en tête.
     *
     * C'est ce qui alimente le menu de la fiche : deux trajets complets entre
     * des bouts différents — la ligne 1 dessert Beaujoire **ou** Babinière dans
     * le même sens. Aucun des deux n'est un détour de l'autre, et en jeter un
     * revenait à ne jamais montrer Babinière.
     */
    @Test
    fun `les branches d un meme sens sont toutes rendues`() = runTest {
        respond(
            """
            [
              {"trip_id": "t-babiniere", "shape_id": "1-2", "direction_id": 0},
              {"trip_id": "t-beaujoire", "shape_id": "1-0", "direction_id": 0}
            ]
            """.trimIndent(),
        )
        respond(
            """
            [
              {"trip_id": "t-babiniere", "stop_sequence": 1, "gtfs_stops": {"stop_id": "BAB", "stop_name": "Babinière", "geom": {"type": "Point", "coordinates": [-1.53, 47.28]}}},
              {"trip_id": "t-babiniere", "stop_sequence": 2, "gtfs_stops": {"stop_id": "COMM", "stop_name": "Commerce", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}},
              {"trip_id": "t-beaujoire", "stop_sequence": 1, "gtfs_stops": {"stop_id": "BJOI", "stop_name": "Beaujoire", "geom": {"type": "Point", "coordinates": [-1.53, 47.25]}}},
              {"trip_id": "t-beaujoire", "stop_sequence": 2, "gtfs_stops": {"stop_id": "COMM", "stop_name": "Commerce", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}}
            ]
            """.trimIndent(),
        )

        val profiles = repository.fetchJourneyProfiles(SESSION, "C6", 0)
        assertEquals(2, profiles.size)
        assertEquals(listOf("1-0", "1-2"), profiles.map { it.profileId })
        assertEquals("Beaujoire → Commerce", profiles[0].label)
        assertEquals("Babinière → Commerce", profiles[1].label)
        // À longueur égale, l'identifiant tranche : la fiche s'ouvre toujours sur
        // la même branche, quel que soit l'ordre où le serveur a servi les courses.
        assertEquals("1-0", profiles.first().profileId)
    }

    /**
     * ⚠️ **Le prolongement au-delà du terminus, et pourquoi le « plus long » ne
     * peut pas servir de repère.**
     *
     * La règle enchaîne deux pas : les bouts viennent du parcours **le plus
     * long**, puis on retient le plus court qui les relie. Le second pas écarte
     * les détours ; le premier suppose que le plus long relie les vrais
     * terminus. Une course qui pousse jusqu'au dépôt renverse cette supposition
     * — et rien dans sa forme ne la distingue d'un trajet ordinaire : elle
     * contient l'autre, comme un parcours complet contient une course partielle.
     *
     * Le repère ne peut donc pas sortir des courses elles-mêmes. Il vient du
     * terminus que le référentiel **annonce** pour ce sens
     * (`route_long_name`, via `ServiceLine.directions`) : quand un parcours
     * finit là, c'est lui qui donne les bouts.
     */
    @Test
    fun `une course prolongee au depot ne donne pas les bouts de la ligne`() = runTest {
        respond(
            """
            [
              {"trip_id": "t-depot", "shape_id": "C3-9", "direction_id": 0},
              {"trip_id": "t-normal", "shape_id": "C3-3", "direction_id": 0}
            ]
            """.trimIndent(),
        )
        respond(
            """
            [
              {"trip_id": "t-depot", "stop_sequence": 1, "gtfs_stops": {"stop_id": "DOU", "stop_name": "Bd de Doulon", "geom": {"type": "Point", "coordinates": [-1.51, 47.23]}}},
              {"trip_id": "t-depot", "stop_sequence": 2, "gtfs_stops": {"stop_id": "COM", "stop_name": "Commerce", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}},
              {"trip_id": "t-depot", "stop_sequence": 3, "gtfs_stops": {"stop_id": "ARM", "stop_name": "Armor", "geom": {"type": "Point", "coordinates": [-1.62, 47.23]}}},
              {"trip_id": "t-depot", "stop_sequence": 4, "gtfs_stops": {"stop_id": "DEP", "stop_name": "Dépôt de Saint-Herblain", "geom": {"type": "Point", "coordinates": [-1.66, 47.24]}}},
              {"trip_id": "t-normal", "stop_sequence": 1, "gtfs_stops": {"stop_id": "DOU", "stop_name": "Bd de Doulon", "geom": {"type": "Point", "coordinates": [-1.51, 47.23]}}},
              {"trip_id": "t-normal", "stop_sequence": 2, "gtfs_stops": {"stop_id": "COM", "stop_name": "Commerce", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}},
              {"trip_id": "t-normal", "stop_sequence": 3, "gtfs_stops": {"stop_id": "ARM", "stop_name": "Armor", "geom": {"type": "Point", "coordinates": [-1.62, 47.23]}}}
            ]
            """.trimIndent(),
        )

        val journey = repository.fetchJourney(SESSION, "C3", 0, expectedTerminus = "Armor")

        assertEquals("t-normal", journey.tripId)
        assertEquals(listOf("Bd de Doulon", "Commerce", "Armor"), journey.stops.map { it.name })
    }

    /**
     * Le garde-fou ne s'applique **que quand il reconnaît le terminus**.
     *
     * La ligne 1 annonce « Beaujoire / Babinière », qui ne nomme aucun arrêt :
     * la comparaison échoue, et la règle d'origine reprend la main plutôt que de
     * rendre une liste vide. Un repère qu'on ne sait pas lire ne doit rien
     * décider.
     */
    @Test
    fun `un terminus annonce qui ne nomme aucun arret ne change rien`() = runTest {
        respond(TRIPS_BODY)
        respond(STOP_TIMES_BODY)

        val journey = repository.fetchJourney(
            SESSION,
            "C6",
            0,
            expectedTerminus = "Beaujoire / Babinière",
        )

        assertEquals("trip-short", journey.tripId)
    }

    /**
     * Les deux graphies du référentiel désignent le même lieu.
     *
     * Comparés bruts, « Hôtel Dieu » et « HOTEL-DIEU » feraient deux jeux de
     * bouts distincts, et le filtre écarterait le parcours ordinaire au profit
     * du détour — le défaut même que ce filtre est censé corriger.
     */
    @Test
    fun `deux graphies du meme terminus ne font pas deux parcours`() = runTest {
        respond(
            """
            [
              {"trip_id": "t-detour", "shape_id": "s-detour", "direction_id": 0},
              {"trip_id": "t-normal", "shape_id": "s-normal", "direction_id": 0}
            ]
            """.trimIndent(),
        )
        respond(
            """
            [
              {"trip_id": "t-detour", "stop_sequence": 1, "gtfs_stops": {"stop_id": "A", "stop_name": "Hôtel Dieu", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}},
              {"trip_id": "t-detour", "stop_sequence": 2, "gtfs_stops": {"stop_id": "X", "stop_name": "Crochet", "geom": {"type": "Point", "coordinates": [-1.58, 47.22]}}},
              {"trip_id": "t-detour", "stop_sequence": 3, "gtfs_stops": {"stop_id": "B", "stop_name": "Armor", "geom": {"type": "Point", "coordinates": [-1.62, 47.23]}}},
              {"trip_id": "t-normal", "stop_sequence": 1, "gtfs_stops": {"stop_id": "A2", "stop_name": "HOTEL-DIEU", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}},
              {"trip_id": "t-normal", "stop_sequence": 2, "gtfs_stops": {"stop_id": "B", "stop_name": "Armor", "geom": {"type": "Point", "coordinates": [-1.62, 47.23]}}}
            ]
            """.trimIndent(),
        )

        val journey = repository.fetchJourney(SESSION, "C3", 0)

        assertEquals("t-normal", journey.tripId)
    }

    /**
     * L'ordre des courses est **demandé**, il n'est pas laissé au planificateur.
     *
     * PostgREST n'en garantit aucun sans `order` : tout le soin pris à départager
     * les égalités « pour que deux lancements peignent la même carte » ne sert à
     * rien si l'échantillon d'entrée, lui, varie.
     */
    @Test
    fun `l echantillon de courses est demande dans un ordre stable`() = runTest {
        respond(TRIPS_BODY)
        respond(STOP_TIMES_BODY)

        repository.fetchJourney(SESSION, "C6", 0)

        val trips = server.takeRequest()
        assertEquals("trip_id", trips.url.queryParameter("order"))
    }

    /**
     * Une course qui s'arrête en chemin n'est pas un parcours de référence.
     *
     * C'est ce qui rend le « plus court » sûr : sans le filtre sur les
     * extrémités, la desserte d'une ligne se réduirait à son service partiel du
     * matin, et la carte s'arrêterait au milieu du tracé.
     */
    @Test
    fun `une course partielle ne remplace pas le parcours complet`() = runTest {
        respond(
            """
            [
              {"trip_id": "trip-partial", "direction_id": 0},
              {"trip_id": "trip-full", "direction_id": 0}
            ]
            """.trimIndent(),
        )
        respond(
            """
            [
              {"trip_id": "trip-partial", "stop_sequence": 1, "gtfs_stops": {"stop_id": "h1", "stop_name": "Hermeland", "geom": {"type": "Point", "coordinates": [-1.52, 47.29]}}},
              {"trip_id": "trip-partial", "stop_sequence": 2, "gtfs_stops": {"stop_id": "co1", "stop_name": "Commerce", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}},
              {"trip_id": "trip-full", "stop_sequence": 1, "gtfs_stops": {"stop_id": "h1", "stop_name": "Hermeland", "geom": {"type": "Point", "coordinates": [-1.52, 47.29]}}},
              {"trip_id": "trip-full", "stop_sequence": 2, "gtfs_stops": {"stop_id": "co1", "stop_name": "Commerce", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}},
              {"trip_id": "trip-full", "stop_sequence": 3, "gtfs_stops": {"stop_id": "c1", "stop_name": "Chantrerie", "geom": {"type": "Point", "coordinates": [-1.52, 47.28]}}}
            ]
            """.trimIndent(),
        )

        val journey = repository.fetchJourney(SESSION, "C6", 0)
        assertEquals("trip-full", journey.tripId)
        assertEquals(listOf("Hermeland", "Commerce", "Chantrerie"), journey.stops.map { it.name })
    }

    @Test
    fun `la ligne 1 assemble le troncon principal et le troncon relais 1B en sens 0`() = runTest {
        respond("""[{"trip_id": "T-1-0", "direction_id": 0}]""")
        respond("""
            [
              {"trip_id": "T-1-0", "stop_sequence": 1, "gtfs_stops": {"stop_id": "BJOI", "stop_name": "Beaujoire", "geom": {"type": "Point", "coordinates": [-1.53, 47.25]}}},
              {"trip_id": "T-1-0", "stop_sequence": 2, "gtfs_stops": {"stop_id": "COMM", "stop_name": "Commerce", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}}
            ]
        """.trimIndent())
        respond("""[{"trip_id": "T-1B-0", "direction_id": 0}]""")
        respond("""
            [
              {"trip_id": "T-1B-0", "stop_sequence": 1, "gtfs_stops": {"stop_id": "HD", "stop_name": "Hôtel Dieu", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}},
              {"trip_id": "T-1B-0", "stop_sequence": 2, "gtfs_stops": {"stop_id": "MED", "stop_name": "Médiathèque", "geom": {"type": "Point", "coordinates": [-1.56, 47.21]}}},
              {"trip_id": "T-1B-0", "stop_sequence": 3, "gtfs_stops": {"stop_id": "FMIT", "stop_name": "François Mitterrand", "geom": {"type": "Point", "coordinates": [-1.63, 47.22]}}}
            ]
        """.trimIndent())

        val journey = repository.fetchJourney(SESSION, "1", 0)
        assertEquals(listOf("Beaujoire", "Commerce", "Médiathèque", "François Mitterrand"), journey.stops.map { it.name })
    }

    @Test
    fun `la ligne 1 assemble le troncon relais 1B et le troncon principal en sens 1`() = runTest {
        respond("""[{"trip_id": "T-1-14", "direction_id": 1}]""")
        respond("""
            [
              {"trip_id": "T-1-14", "stop_sequence": 1, "gtfs_stops": {"stop_id": "COMM", "stop_name": "Commerce", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}},
              {"trip_id": "T-1-14", "stop_sequence": 2, "gtfs_stops": {"stop_id": "BJOI", "stop_name": "Beaujoire", "geom": {"type": "Point", "coordinates": [-1.53, 47.25]}}}
            ]
        """.trimIndent())
        respond("""[{"trip_id": "T-1B-3", "direction_id": 1}]""")
        respond("""
            [
              {"trip_id": "T-1B-3", "stop_sequence": 1, "gtfs_stops": {"stop_id": "FMIT", "stop_name": "François Mitterrand", "geom": {"type": "Point", "coordinates": [-1.63, 47.22]}}},
              {"trip_id": "T-1B-3", "stop_sequence": 2, "gtfs_stops": {"stop_id": "MED", "stop_name": "Médiathèque", "geom": {"type": "Point", "coordinates": [-1.56, 47.21]}}},
              {"trip_id": "T-1B-3", "stop_sequence": 3, "gtfs_stops": {"stop_id": "HD", "stop_name": "Hôtel Dieu", "geom": {"type": "Point", "coordinates": [-1.55, 47.21]}}}
            ]
        """.trimIndent())

        val journey = repository.fetchJourney(SESSION, "1", 1)
        assertEquals(listOf("François Mitterrand", "Médiathèque", "Commerce", "Beaujoire"), journey.stops.map { it.name })
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
