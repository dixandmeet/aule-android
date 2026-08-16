package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.VehicleFeed
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.AulePlaceSearchRepository
import io.aule.android.data.aule.AuleRoutingRepository
import io.aule.android.data.aule.AuleStopRepository
import io.aule.android.data.aule.AuleVehicleRepository
import io.aule.android.core.model.RouteMode
import io.aule.android.core.model.RouteProfile
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Port de `Native/AuleTests/BackendTests.swift`.
 *
 * Les charges utiles sont les **captures réelles** des points d'entrée du BFF,
 * reprises telles quelles. Un DTO qu'on modifie et qui cesse de décoder une
 * capture réelle est un défaut, et le test le dit avant l'appareil.
 *
 * `MockWebServer` plutôt qu'un intercepteur factice : il exerce le vrai chemin
 * OkHttp, codes d'état compris — c'est l'équivalent du `StubURLProtocol` d'iOS.
 */
class BffRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var endpoints: AuleEndpoints
    private lateinit var client: AuleHttpClient

    private val clock = Clock.fixed(Instant.parse("2026-08-16T06:02:19Z"), ZoneOffset.UTC)

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "fixture absente : $name" }
            .use { it.readBytes().decodeToString() }

    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse.Builder().code(status).body(body).build())
    }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        endpoints = AuleEndpoints(server.url("/").toString().trimEnd('/'))
        client = AuleHttpClient(OkHttpClient(), NoopLogger)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    // ------------------------------------------------------------------ flotte

    @Test
    fun `une reponse reelle de la flotte se decode entierement`() = runTest {
        respond(fixture("vehicles.json"))
        val repository = AuleVehicleRepository(endpoints, client, clock)

        val snapshot = repository.vehicles(Coordinate.NANTES, radiusMeters = 2500.0, limit = 250)
        val vehicle = snapshot.vehicles.firstOrNull()
        assertNotNull(vehicle)

        assertEquals("th-D003871", vehicle.id)
        assertEquals(TransportMode.TRAM, vehicle.mode)
        assertEquals(VehicleFeed.SCHEDULED, vehicle.feed)
        assertEquals("1", vehicle.lineId)
        assertEquals("Beaujoire / Babinière", vehicle.destination)
        assertEquals("Moutonnerie", vehicle.nextStop)
        assertEquals(38.0, vehicle.etaSeconds)
        assertEquals(5.0, vehicle.dwellSeconds)

        // Sans `ahead` ni `trajectory`, pas d'interpolation possible — c'est ce
        // qui rend le véhicule glissant plutôt que sautant.
        assertNotNull(vehicle.ahead)
        assertEquals(2, vehicle.trajectory.size)
        assertEquals(10.0, snapshot.horizonSeconds)
    }

    @Test
    fun `la requete de flotte porte bien lat, lon, radius et limit`() = runTest {
        respond(fixture("vehicles.json"))
        AuleVehicleRepository(endpoints, client, clock)
            .vehicles(Coordinate.NANTES, radiusMeters = 2500.0, limit = 250)

        val query = server.takeRequest().target
        assertTrue(query.contains("lat=47.2184"), query)
        assertTrue(query.contains("lon=-1.5536"), query)
        assertTrue(query.contains("radius=2500"), query)
        assertTrue(query.contains("limit=250"), query)
        // Les paramètres sont triés par nom : deux requêtes identiques doivent
        // produire la même URL, sinon les caches — celui d'OkHttp comme celui du
        // BFF — voient deux ressources là où il n'y en a qu'une.
        val positions = listOf("lat=", "limit=", "lon=", "radius=").map { query.indexOf(it) }
        assertEquals(positions.sorted(), positions, "paramètres non triés dans « $query »")
    }

    @Test
    fun `un vehicule abime est ecarte sans emporter les autres`() = runTest {
        respond(fixture("vehicles-damaged.json"))
        val snapshot = AuleVehicleRepository(endpoints, client, clock)
            .vehicles(Coordinate.NANTES, radiusMeters = 2500.0, limit = 250)

        assertEquals(
            listOf("c"),
            snapshot.vehicles.map { it.id },
            "mode inconnu et position nulle doivent être écartés",
        )
    }

    /**
     * Le défaut qu'on cherche à rendre impossible : une carte d'apparence
     * normale, sans véhicules et sans message, pendant une panne.
     */
    @Test
    fun `une panne reseau leve plutot que de rendre une liste vide`() = runTest {
        respond("""{"error":"upstream down"}""", status = 502)
        val repository = AuleVehicleRepository(endpoints, client, clock)

        val failure = assertThrows<ApiException.UpstreamUnavailable> {
            repository.vehicles(Coordinate.NANTES, radiusMeters = 2500.0, limit = 250)
        }
        assertEquals(502, failure.status)
    }

    // ------------------------------------------------------------------ arrêts

    @Test
    fun `une reponse reelle d arrets se decode`() = runTest {
        respond(fixture("stops.json"))
        val stop = AuleStopRepository(endpoints, client, clock).allStops().firstOrNull()
        assertNotNull(stop)

        assertEquals("50 Otages", stop.name)
        assertEquals(TransportMode.TRAM, stop.mode)
        assertTrue(stop.isWheelchairAccessible)
        // L'ordre GeoJSON est lng,lat — l'inverser placerait tous les arrêts au
        // large de la Somalie.
        assertEquals(47.219933, stop.coordinate.latitude, 1e-6)
        assertEquals(-1.555938, stop.coordinate.longitude, 1e-6)
    }

    @Test
    fun `les passages se decodent, quelle que soit la forme de la date`() = runTest {
        respond(fixture("stop-departures.json"))
        val result = AuleStopRepository(endpoints, client, clock).departures("Commerce")

        assertEquals(DeparturesOutcome.ANNOUNCED, result.outcome)
        assertEquals(3, result.departures.size)
        // La capture porte « …19Z » et « …19.000Z » dans la même charge utile :
        // c'est le cas qui avait imposé deux formateurs sur iOS.
        assertEquals(
            Instant.parse("2026-08-16T06:03:19Z"),
            result.departures.first { it.id == "p1" }.expectedAt,
        )
        assertEquals(
            Instant.parse("2026-08-16T06:07:19Z"),
            result.departures.first { it.id == "p2" }.expectedAt,
        )
    }

    /**
     * Les deux absences qui ne veulent pas dire la même chose. Les confondre fait
     * annoncer à l'app qu'il n'y a pas de bus alors qu'elle ne sait pas.
     */
    @Test
    fun `404 signifie rien ne circule, pas une panne`() = runTest {
        respond(
            """{"error":"Aucun passage prévu dans les trois prochaines heures"}""",
            status = 404,
        )
        val result = AuleStopRepository(endpoints, client, clock).departures("Commerce")

        assertEquals(DeparturesOutcome.NOTHING_ANNOUNCED, result.outcome)
        assertTrue(result.departures.isEmpty())
    }

    @Test
    fun `502 signifie que le fournisseur est muet`() = runTest {
        respond("""{"error":"upstream"}""", status = 502)
        val result = AuleStopRepository(endpoints, client, clock).departures("Commerce")

        assertEquals(DeparturesOutcome.PROVIDER_SILENT, result.outcome)
    }

    /**
     * Une panne qui n'est ni l'une ni l'autre doit remonter. Avaler un 500
     * ramènerait le défaut qu'on vient d'éliminer, par une autre porte.
     */
    @Test
    fun `une panne serveur sur les passages leve encore`() = runTest {
        respond("{}", status = 500)
        val repository = AuleStopRepository(endpoints, client, clock)
        assertThrows<ApiException.Server> { repository.departures("Commerce") }
    }

    @Test
    fun `les lignes desservies gardent leur couleur GTFS et leur mode`() = runTest {
        respond(fixture("serving-lines.json"))
        val lines = AuleStopRepository(endpoints, client, clock).servingLines("Commerce")

        assertEquals(2, lines.size)
        assertEquals("#00a754", lines[0].lineColor)
        assertEquals(TransportMode.TRAM, lines[1].mode)
    }

    // ------------------------------------------------------------------- lieux

    @Test
    fun `le geocodeur rend des lieux exploitables`() = runTest {
        respond(fixture("geocode.json"))
        val place = AulePlaceSearchRepository(endpoints, client).search("Beaujoire").firstOrNull()
        assertNotNull(place)

        assertEquals("Beaujoire, 44000 Nantes", place.label)
        assertTrue(place.coordinate.isValid)
    }

    /**
     * Deux lettres ne cherchent rien d'utile et coûteraient une requête par
     * frappe. Le test vérifie qu'**aucune requête n'est partie**, pas seulement
     * que le résultat est vide.
     */
    @Test
    fun `une requete trop courte ne part pas sur le reseau`() = runTest {
        val places = AulePlaceSearchRepository(endpoints, client).search("na")
        assertTrue(places.isEmpty())
        assertEquals(0, server.requestCount)
    }

    // ------------------------------------------------------------- itinéraires

    @Test
    fun `un plan transit reel se decode entierement`() = runTest {
        respond(fixture("route-transit.json"))
        val from = Coordinate(latitude = 47.2136, longitude = -1.5601)
        val to = Coordinate(latitude = 47.2412, longitude = -1.5232)
        val plan = AuleRoutingRepository(endpoints, client).plan(
            mode = RouteMode.TRANSIT,
            from = from,
            to = to,
        )

        assertTrue(plan.timetable)
        assertEquals(4, plan.alternatives.size)
        val first = plan.alternatives.first()
        assertEquals(32, first.durationMinutes)
        assertEquals(3, first.segments.size)
        assertTrue(first.segments.first().walk)
        assertEquals("1", first.segments[1].routeId)
        assertEquals(RouteProfile.LEAST_WALK, plan.alternatives.last().profiles.first())

        val query = java.net.URLDecoder.decode(server.takeRequest().target, Charsets.UTF_8)
        // OkHttp encode la virgule (`%2C`) : on décode pour lire l'ordre
        // `lng,lat`, le seul qui ne produise pas un 404 silencieux.
        assertTrue(query.contains("from=-1.5601,47.2136"), query)
        assertTrue(query.contains("to=-1.5232,47.2412"), query)
        assertTrue(query.contains("v=28"), query)
        assertTrue(query.contains("mode=transit"), query)
    }

    @Test
    fun `des coordonnees inversees portent le message du serveur`() = runTest {
        respond(fixture("route-inverted.json"), 404)
        val failure = assertThrows<ApiException.NotFound> {
            AuleRoutingRepository(endpoints, client).plan(
                mode = RouteMode.TRANSIT,
                from = Coordinate(latitude = 47.2136, longitude = -1.5601),
                to = Coordinate(latitude = 47.2412, longitude = -1.5232),
            )
        }
        assertEquals("Aucun arrêt de transport en commun à proximité", failure.serverMessage)
    }
}
