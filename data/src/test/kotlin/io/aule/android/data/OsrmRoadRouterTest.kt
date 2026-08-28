package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.repository.RoadProfile
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.OsrmRoadRouter
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

/** Port de `SAE/test/routing_service_test.dart`. */
class OsrmRoadRouterTest {

    private lateinit var server: MockWebServer
    private lateinit var router: OsrmRoadRouter

    private val from = Coordinate(latitude = 47.2184, longitude = -1.5536)
    private val to = Coordinate(latitude = 47.2400, longitude = -1.5700)

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        router = OsrmRoadRouter(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            origin = server.url("/").toString().trimEnd('/'),
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "fixture absente : $name" }
            .use { it.readBytes().decodeToString() }

    @Test
    fun `le profil pieton demande walking`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(fixture("osrm-walk.json")).build())
        router.route(from, to, RoadProfile.PEDESTRIAN)
        val path = server.takeRequest().target
        assertTrue(path.contains("/route/v1/walking/"), path)
        assertTrue(path.contains("steps=true"), path)
    }

    @Test
    fun `une reponse reelle se decode avec ses manoeuvres`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(fixture("osrm-walk.json")).build())
        val route = router.route(from, to, RoadProfile.PEDESTRIAN)
        assertNotNull(route)
        assertEquals(2400.0, route.distanceMeters)
        assertEquals(2, route.maneuvers.size)
        assertEquals("turn", route.maneuvers.first().instruction)
        assertEquals("right", route.maneuvers.first().modifier)
        assertEquals("Rue Paul Bellamy", route.maneuvers.first().streetName)
        assertEquals(47.2300, route.maneuvers.first().location.latitude, 1e-9)
        assertEquals(-1.5600, route.maneuvers.first().location.longitude, 1e-9)
        assertNull(route.maneuvers.last().streetName)
    }

    /**
     * Le champ `exit` d'OSRM n'était pas décodé, et la consigne se réduisait à
     * « Prendre le rond-point ». Sur un rond-point à cinq branches, c'est une
     * phrase qui ne guide personne.
     */
    @Test
    fun `la sortie d un rond-point se decode`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body(fixture("osrm-roundabout.json")).build(),
        )
        val route = assertNotNull(router.route(from, to, RoadProfile.CAR))
        val roundabout = route.maneuvers.first()
        assertEquals("roundabout", roundabout.instruction)
        assertEquals(3, roundabout.exit)
        assertNull(route.maneuvers.last().exit, "une arrivée n'a pas de sortie")
    }

    @Test
    fun `un echec rend null plutot que de lever`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("nope").build())
        assertNull(router.route(from, to, RoadProfile.CAR))
    }

    @Test
    fun `un code autre que Ok rend null`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"code":"NoRoute","routes":[]}""").build())
        assertNull(router.route(from, to, RoadProfile.CAR))
    }
}
