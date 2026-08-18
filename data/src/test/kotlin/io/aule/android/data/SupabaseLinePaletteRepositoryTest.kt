package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.SupabaseLinePaletteRepository
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

/** La charge utile est une capture réelle de `gtfs_routes`, colonnes comprises. */
class SupabaseLinePaletteRepositoryTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun repository(url: String = server.url("/").toString().trimEnd('/')) =
        SupabaseLinePaletteRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            supabaseUrl = url,
            publishableKey = "sb_publishable_test",
        )

    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse.Builder().code(status).body(body).build())
    }

    @Test
    fun `le nuancier se decode et ne demande que deux colonnes`() = runTest {
        respond(ROUTES_BODY)

        val palette = repository().palette()

        assertEquals("a877b2", palette.colorOf("C6"))
        assertEquals("e30613", palette.colorOf("2"))
        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.endsWith("/rest/v1/gtfs_routes"))
        assertEquals("route_id,route_color", recorded.url.queryParameter("select"))
        // Table publique en lecture : la clé publiable suffit, sans session.
        assertEquals("sb_publishable_test", recorded.headers["apikey"])
        assertNull(recorded.headers["Authorization"])
    }

    /** Un nuancier change à la fréquence d'un GTFS : une fois suffit. */
    @Test
    fun `le nuancier n est demande qu une fois`() = runTest {
        respond(ROUTES_BODY)

        val repository = repository()
        val first = repository.palette()
        val second = repository.palette()

        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    /**
     * Une ligne sans couleur n'est pas une ligne sans nom : elle reste dans le
     * réseau, elle garde simplement le gris de repli du badge.
     */
    @Test
    fun `une ligne sans couleur ne rend rien`() = runTest {
        respond(ROUTES_BODY)
        assertNull(repository().palette().colorOf("N1"))
    }

    @Test
    fun `une panne du catalogue leve plutot que de rendre un nuancier vide`() = runTest {
        respond("""{"message":"permission denied"}""", status = 401)
        assertThrows<ApiException.BadRequest> { repository().palette() }
    }

    /** Sans configuration Supabase, aucun appel : il échouerait à chaque ouverture. */
    @Test
    fun `sans configuration le nuancier est vide et muet`() = runTest {
        val palette = repository(url = "").palette()

        assertTrue(palette.isEmpty)
        assertEquals(0, server.requestCount)
    }

    private companion object {
        val ROUTES_BODY = """
            [{"route_id":"1","route_color":"00a754"},
             {"route_id":"2","route_color":"e30613"},
             {"route_id":"C6","route_color":"a877b2"},
             {"route_id":"N1","route_color":null}]
        """.trimIndent()
    }
}
