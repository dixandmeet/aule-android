package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.Place
import io.aule.android.core.model.PlaceSearchSession
import io.aule.android.core.model.PlaceSuggestion
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.AulePlaceSearchRepository
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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

/**
 * Le géocodeur, **en deux temps** : `?q=` propose, `?placeId=` situe — et seulement ce qu'on
 * touche.
 *
 * ⚠️ **Chaque fixture `geocode-*.json` est un relevé de `www.aule.fr` du 24/09/2026**, gardé
 * octet pour octet. Jusqu'au 22/09, l'épreuve servait une charge écrite à la main, avec un
 * `lat`/`lng` que la production ne rend pas : elle passait au vert sur un dépôt qui rendait une
 * liste vide à chaque frappe.
 *
 * ⚠️ **Ce que ces épreuves gardent fermé se lit dans les requêtes, pas dans les lieux rendus.**
 * `686d1d3` situait les cinq premières suggestions de chaque recherche : les lieux sortaient
 * justes, et six requêtes facturées partaient pour une frappe. On compte donc ce qui part, et on
 * lit ce que chaque requête porte.
 */
class PlaceSearchRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AulePlaceSearchRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = AulePlaceSearchRepository(
            endpoints = AuleEndpoints(server.url("/").toString().trimEnd('/')),
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "fixture absente : $name" }
            .use { it.readBytes().decodeToString() }

    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse.Builder().code(status).body(body).build())
    }

    /** La requête suivante, décodée : OkHttp écrit l'espace `%20`, et le jeton se lit mieux en clair. */
    private fun nextQuery(): String = URLDecoder.decode(server.takeRequest().target, Charsets.UTF_8)

    // --- Proposer ---

    /** La forme exacte que la production rend, et qui faisait tout écarter avant le 22/09. */
    @Test
    fun `une frappe propose sans rien situer`() = runTest {
        respond(fixture("geocode-strasbourg.json"))

        val found = repository.search("rue de Strasbourg", PlaceSearchSession.start())

        assertEquals(5, found.size)
        val first = assertIs<PlaceSuggestion.Prediction>(found.first())
        assertEquals("Rue de Strasbourg, Nantes, France", first.label)
        assertTrue(first.placeId.startsWith("EiFSdWUgZGUgU3RyYXNib3VyZyw"), first.placeId)
        assertEquals("Rue de Strasbourg", first.name)
        assertFalse(first.isTransitStop)
        assertNull(first.coordinate, "aucun point ne doit être inventé")
        // Le cœur de l'épreuve : une requête, et une seule. `686d1d3` en envoyait six.
        assertEquals(1, server.requestCount)
    }

    /** Le jeton relie la frappe à sa résolution, et rien dans la réponse ne dit s'il est parti. */
    @Test
    fun `la frappe part avec le jeton de sa session`() = runTest {
        respond(fixture("geocode-strasbourg.json"))
        val session = PlaceSearchSession.start()

        repository.search("rue de Strasbourg", session)

        val query = nextQuery()
        assertTrue(query.contains("q=rue de Strasbourg"), query)
        assertTrue(query.contains("session=${session.token}"), query)
    }

    @Test
    fun `une recherche isolee n invente pas de session`() = runTest {
        respond(fixture("geocode-strasbourg.json"))

        repository.search("rue de Strasbourg")

        val query = nextQuery()
        assertFalse(query.contains("session"), query)
    }

    /** « commerce » : Google annonce lui-même l'arrêt de tram — c'est ce qui en fera un jumeau. */
    @Test
    fun `le fournisseur dit lui-meme quelles predictions sont des arrets`() = runTest {
        respond(fixture("geocode-commerce.json"))

        val found = repository.search("commerce").map { assertIs<PlaceSuggestion.Prediction>(it) }

        assertEquals(listOf(true, false, false, false, false), found.map { it.isTransitStop })
        assertEquals("Commerce", found.first().name)
    }

    /**
     * « Beaujoire » : l'arrêt, et un hôtel dont le nom porte deux virgules. C'est la précision du
     * fournisseur qui dit où il finit.
     */
    @Test
    fun `une prediction se lit jusqu a sa precision`() = runTest {
        respond(fixture("geocode-beaujoire.json"))

        val found = repository.search("Beaujoire").map { assertIs<PlaceSuggestion.Prediction>(it) }

        assertEquals("Beaujoire", found[1].name)
        assertTrue(found[1].isTransitStop)
        assertEquals("The Originals City, Hôtel le Beaujoire, Nantes", found.last().name)
    }

    /**
     * Le repli, quand Google refuse : l'IGN rend le point d'emblée, et ses résultats arrivent
     * situés — rien à résoudre, donc rien à facturer.
     *
     * ⚠️ **La seule charge de cette suite qui ne soit pas un relevé de `www.aule.fr`** : la
     * production ne la rend qu'en panne de Google, et on ne la provoque pas. Ce sont les trois
     * adresses que la Géoplateforme rend sur « rue de Strasbourg » (`data.geopf.fr`, 24/09/2026),
     * passées par `parseCompletionResponse` (`dashboard/lib/server/geocode-provider.ts`). Les
     * sociétés de l'annuaire, que le BFF peut y ajouter, n'y figurent pas : elles arrivent
     * situées de la même façon, et n'apprendraient rien de plus à l'épreuve.
     */
    @Test
    fun `le repli IGN rend des lieux deja situes`() = runTest {
        respond(IGN_STRASBOURG)

        val found = repository.search("rue de Strasbourg")

        assertEquals(3, found.size)
        assertEquals(
            PlaceSuggestion.Located(
                Place(
                    label = "Rue de Strasbourg, 44000 Nantes",
                    coordinate = Coordinate(latitude = 47.216979, longitude = -1.552075),
                ),
            ),
            found.first(),
        )
        assertEquals(1, server.requestCount)
    }

    /**
     * Le serveur refuse en 400 sous trois caractères. Servir **ce refus-là** rend l'épreuve
     * négative : si la requête partait, la recherche lèverait.
     */
    @Test
    fun `deux lettres ne partent pas au geocodeur`() = runTest {
        respond("""{"error":"Saisissez au moins 3 caractères"}""", status = 400)

        assertTrue(repository.search("b").isEmpty())
        assertTrue(repository.search("be").isEmpty())
        assertEquals(0, server.requestCount)
    }

    // --- Situer ---

    /**
     * La fiche Google nomme la gare « Nantes » : située telle quelle, elle aurait intitulé le
     * trajet « Nantes ». Et la résolution part **dans la session des frappes** qui l'ont
     * proposée — c'est ce qui les fait facturer comme une seule recherche.
     */
    @Test
    fun `situer la prediction touchee garde son nom et sa session`() = runTest {
        respond(fixture("geocode-gare.json"))
        respond(fixture("geocode-gare-lookup.json"))
        val session = PlaceSearchSession.start()

        val gare = repository.search("gare de nantes", session)
            .filterIsInstance<PlaceSuggestion.Prediction>()
            .first { it.label == "Gare de Nantes, Boulevard de Stalingrad, Nantes, France" }
        val place = repository.resolve(gare, session)

        assertEquals("Gare de Nantes, 27 Bd de Stalingrad, 44041 Nantes, France", place.label)
        assertEquals(47.2174401, place.coordinate.latitude, 1e-7)
        assertEquals(-1.5426706, place.coordinate.longitude, 1e-7)
        // Google annonce une gare ; seul le catalogue dit ce qu'on peut interroger en passages.
        assertNull(place.stopMode)

        nextQuery()
        val resolution = nextQuery()
        assertTrue(resolution.contains("placeId=ChIJkXDHRLnuBUgRlpH_NZEcZ1s"), resolution)
        assertTrue(resolution.contains("session=${session.token}"), resolution)
        assertEquals(2, server.requestCount)
    }

    /** L'adresse postale porte déjà le nom de la rue : il ne se répète pas devant. */
    @Test
    fun `une rue situee ne repete pas son nom`() = runTest {
        respond(fixture("geocode-strasbourg.json"))
        respond(fixture("geocode-strasbourg-lookup.json"))

        val rue = assertIs<PlaceSuggestion.Prediction>(repository.search("rue de Strasbourg").first())
        val place = repository.resolve(rue)

        assertEquals("Rue de Strasbourg, 44000 Nantes, France", place.label)
    }

    /**
     * Une clé que le fournisseur ne sait plus situer : le BFF répond 502, avec le corps qu'écrit
     * `providerError()` (`app/api/geocode/route.ts`). La résolution lève — un point approché
     * mènerait l'itinéraire ailleurs sans rien dire.
     */
    @Test
    fun `un lieu que le serveur ne sait pas situer leve`() = runTest {
        respond("""{"error":"Service de géocodage temporairement indisponible"}""", status = 502)
        val orpheline = PlaceSuggestion.Prediction(placeId = "ZZZZ_nope", label = "Nulle part, France")

        assertThrows<ApiException.UpstreamUnavailable> { repository.resolve(orpheline) }
    }

    private companion object {
        const val IGN_STRASBOURG =
            """{"results":[{"label":"Rue de Strasbourg, 44000 Nantes","lng":-1.552075,"lat":47.216979,""" +
                """"kind":{"category":"address","icon":"pin"}},{"label":"Rue de Strasbourg, 44980 """ +
                """Sainte-Luce-sur-Loire","lng":-1.476089,"lat":47.255769,"kind":{"category":"address",""" +
                """"icon":"pin"}},{"label":"Rue de Strasbourg, 44400 Rezé","lng":-1.547658,"lat":47.166727,""" +
                """"kind":{"category":"address","icon":"pin"}}],"provider":"ign"}"""
    }
}
