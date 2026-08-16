package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Port de `SAE/test/map_search_test.dart`.
 *
 * Ce qui se vérifie ici n'est pas qu'elle rende quelque chose, mais qu'elle
 * rende ce qu'on visait : le pôle plutôt que l'arrêt de desserte fine qui en
 * reprend le nom, un lieu plutôt que ses cinq quais, et deux « Mairie » de
 * deux communes plutôt qu'un seul lieu inventé par la fusion.
 */
class StopSearchTest {

    private fun stop(
        name: String,
        lat: Double = 47.2184,
        lng: Double = -1.5536,
        mode: TransportMode = TransportMode.BUS,
        id: String = "$name|$lat|$lng",
    ) = TransitStop(
        id = id,
        name = name,
        code = id,
        coordinate = Coordinate(latitude = lat, longitude = lng),
        mode = mode,
        stationName = name,
    )

    private fun quays(
        name: String,
        lat: Double,
        count: Int,
        lng: Double = -1.5536,
        mode: TransportMode = TransportMode.BUS,
    ): List<TransitStop> = (1..count).map { index ->
        stop(
            name = name,
            lat = lat,
            lng = lng + index * 0.00001,
            mode = mode,
            id = "$name-$index",
        )
    }

    @Test
    fun `une saisie vide ne rend rien`() {
        // Le champ au repos ne propose pas 2 600 lieux.
        assertTrue(StopSearch.search(listOf(stop("Commerce")), "  ").isEmpty())
    }

    @Test
    fun `ce qui commence par la saisie passe devant ce qui la contient`() {
        val found = StopSearch.search(
            listOf(
                stop("Place du Commerce", lat = 47.3),
                stop("Commerce"),
            ),
            "commerce",
        )
        assertEquals("Commerce", found.first().label)
        assertTrue(found.any { it.label == "Place du Commerce" })
    }

    @Test
    fun `un mot du nom suffit, mais compte moins que son debut`() {
        val found = StopSearch.search(
            listOf(
                stop("Gare de Chantenay", lat = 47.20),
                stop("Chantenay", lat = 47.21),
            ),
            "chantenay",
        )
        assertEquals("Chantenay", found.first().label)
    }

    @Test
    fun `accents casse et tirets ne comptent pas`() {
        val found = StopSearch.search(
            listOf(stop("Haluchère - Batignolles")),
            "haluchere",
        )
        assertEquals(1, found.size)
        assertEquals("Haluchère - Batignolles", found.first().label)
    }

    @Test
    fun `a rang egal le pole passe avant l arret qui en reprend le nom`() {
        val found = StopSearch.search(
            quays("Ranzay", lat = 47.25, count = 1) +
                quays("Ranzay", lat = 47.30, count = 5),
            "ranzay",
        )
        // Deux lieux distants de plusieurs kilomètres : ils ne fusionnent pas,
        // et c'est celui qui porte cinq quais qu'on cherchait.
        assertEquals(2, found.size)
        assertEquals(5, found.first().quays)
    }

    @Test
    fun `les orthographes concurrentes du meme lieu se reunissent`() {
        // Deux réseaux écrivent le même pôle différemment. Les laisser côte
        // à côte donnerait deux rangées identiques, chacune avec la moitié
        // des lignes.
        val found = StopSearch.search(
            quays("Haluchère - Batignolles", lat = 47.2400, lng = -1.5200, count = 4) +
                quays("Haluchère-Batignolles", lat = 47.2401, lng = -1.5201, count = 2),
            "haluchere",
        )
        assertEquals(1, found.size)
        assertEquals(2, found.first().names.size)
        assertEquals(6, found.first().quays)
    }

    @Test
    fun `deux lieux de meme nom eloignes restent deux lieux`() {
        // « Mairie » existe dans presque chaque commune : les fusionner
        // ferait afficher des lignes qui ne passent pas là.
        val found = StopSearch.search(
            listOf(
                stop("Mairie", lat = 47.2000, lng = -1.5500),
                stop("Mairie", lat = 47.2600, lng = -1.5100),
            ),
            "mairie",
        )
        assertEquals(2, found.size)
    }

    @Test
    fun `le tram l emporte sur le bus au meme lieu`() {
        val found = StopSearch.search(
            quays("Commerce", lat = 47.2184, count = 3, mode = TransportMode.BUS) +
                quays("Commerce", lat = 47.21841, count = 2, mode = TransportMode.TRAM),
            "commerce",
        )
        assertEquals(1, found.size)
        assertEquals(TransportMode.TRAM, found.first().mode)
        assertEquals(5, found.first().quays)
    }

    @Test
    fun `la liste est bornee`() {
        val index = (0 until 20).map { stop("Arrêt $it", lat = 47.2 + it / 100.0, lng = -1.5 + it / 100.0) }
        assertEquals(SEARCH_LIMIT_PER_KIND, StopSearch.search(index, "arret").size)
    }

    @Test
    fun `le titre d une adresse garde la rue`() {
        assertEquals(
            "12 Rue de Strasbourg",
            shortPlaceName("12 Rue de Strasbourg, Nantes, France"),
        )
        assertEquals("Beaujoire", shortPlaceName("Beaujoire"))
        assertEquals("A", shortPlaceName("  A, B  "))
    }
}
