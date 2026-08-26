package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'historique des destinations : la règle, sans disque.
 *
 * Port de `SearchHistoryTests` — trois choses en une fonction, et chacune se
 * voit à l'usage : le dernier choisi est en tête, un lieu déjà connu remonte au
 * lieu de se dupliquer, et la liste ne dépasse jamais huit.
 */
class SearchHistoryTest {

    private fun place(label: String, lat: Double = 47.21, lng: Double = -1.55) = Place(
        label = label,
        coordinate = Coordinate(latitude = lat, longitude = lng),
    )

    @Test
    fun `le dernier choisi passe en tete`() {
        val history = rememberPlace(place("Commerce"), emptyList())
        val next = rememberPlace(place("Beaujoire"), history)

        assertEquals(listOf("Beaujoire", "Commerce"), next.map { it.label })
    }

    @Test
    fun `un lieu deja connu remonte au lieu de se dupliquer`() {
        var history = emptyList<Place>()
        history = rememberPlace(place("Commerce"), history)
        history = rememberPlace(place("Beaujoire"), history)
        history = rememberPlace(place("Commerce"), history)

        // Sans la remontée, chercher deux fois la même destination dans la
        // journée l'aurait laissée au fond, sous des lieux visités une fois.
        assertEquals(listOf("Commerce", "Beaujoire"), history.map { it.label })
    }

    @Test
    fun `la liste ne depasse jamais huit`() {
        var history = emptyList<Place>()
        repeat(12) { index ->
            history = rememberPlace(place("Arrêt $index", lat = 47.0 + index), history)
        }

        assertEquals(SEARCH_HISTORY_LIMIT, history.size)
        assertEquals("Arrêt 11", history.first().label)
        // Les quatre plus anciens sont sortis par le bas, pas mélangés.
        assertEquals("Arrêt 4", history.last().label)
    }

    @Test
    fun `deux geocodages du meme lieu se reconnaissent`() {
        // Le géocodeur ne rend pas deux fois le même flottant. Une égalité
        // stricte ferait passer ces deux réponses pour deux lieux, et
        // l'historique garderait « Commerce » en double.
        val first = place("Commerce, 44000 Nantes", lat = 47.213600, lng = -1.560100)
        val second = place("Commerce, 44000 Nantes", lat = 47.2136001, lng = -1.5601004)

        val history = rememberPlace(second, rememberPlace(first, emptyList()))

        assertEquals(1, history.size)
    }

    @Test
    fun `deux lieux de meme nom a des endroits differents restent deux lieux`() {
        // « Mairie » existe dans chaque commune de la métropole.
        val nantes = place("Mairie", lat = 47.2184, lng = -1.5536)
        val reze = place("Mairie", lat = 47.1930, lng = -1.5490)

        val history = rememberPlace(reze, rememberPlace(nantes, emptyList()))

        assertEquals(2, history.size)
    }

    @Test
    fun `le mode d arret survit a un aller-retour sur le disque`() {
        // C'est le mode, jamais le libellé, qui dira plus tard qu'on peut
        // demander les passages de ce lieu. Le perdre transformerait chaque
        // arrêt retenu en adresse au relancement.
        val stop = Place(
            label = "Beaujoire",
            coordinate = Coordinate(latitude = 47.2560, longitude = -1.5250),
            stopMode = TransportMode.TRAM,
        )
        val address = place("12 Rue de Strasbourg, Nantes")

        val restored = decodeSearchHistory(listOf(stop, address).encodeHistory())

        assertEquals(2, restored.size)
        assertEquals(TransportMode.TRAM, restored[0].stopMode)
        assertEquals("Beaujoire", restored[0].label)
        assertEquals(null, restored[1].stopMode)
        assertEquals(stop.coordinate, restored[0].coordinate)
    }

    @Test
    fun `un historique illisible rend une liste vide plutot que de lever`() {
        assertTrue(decodeSearchHistory(null).isEmpty())
        assertTrue(decodeSearchHistory("").isEmpty())
        assertTrue(decodeSearchHistory("ceci n'est pas du JSON").isEmpty())
        assertTrue(decodeSearchHistory("""{"label":"pas un tableau"}""").isEmpty())
    }

    @Test
    fun `une entree abimee est sautee et les autres restent`() {
        // Un historique à demi lisible vaut mieux qu'un historique vidé : rien
        // de ce qu'il contient n'est irremplaçable, mais tout est utile.
        val raw = """
            [
              {"label":"Commerce","lat":47.2136,"lng":-1.5601},
              {"label":"Sans coordonnées"},
              {"lat":47.25,"lng":-1.52},
              {"label":"Beaujoire","lat":47.2560,"lng":-1.5250}
            ]
        """.trimIndent()

        val restored = decodeSearchHistory(raw)

        assertEquals(listOf("Commerce", "Beaujoire"), restored.map { it.label })
    }

    @Test
    fun `un fichier trop long est tronque a la relecture`() {
        // La limite ne se contourne pas en écrivant le fichier à la main, ni en
        // relisant celui d'une version qui en gardait davantage.
        val many = (0 until 20).map { place("Arrêt $it", lat = 47.0 + it) }

        assertEquals(SEARCH_HISTORY_LIMIT, decodeSearchHistory(many.encodeHistory()).size)
    }
}
