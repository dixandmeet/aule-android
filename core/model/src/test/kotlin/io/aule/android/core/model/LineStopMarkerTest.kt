package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class LineStopMarkerTest {

    @Test
    fun `un parcours ordinaire donne un marqueur par arret`() {
        val markers = buildLineStopMarkers("C3-3", stops)

        assertEquals(listOf("Doulon", "Commerce", "Procé", "Armor"), markers.map { it.name })
        assertEquals(
            listOf(
                LineStopMarker.Role.ORIGIN,
                LineStopMarker.Role.INTERMEDIATE,
                LineStopMarker.Role.INTERMEDIATE,
                LineStopMarker.Role.TERMINUS,
            ),
            markers.map { it.role },
        )
    }

    /**
     * Le cas qui justifie le regroupement : deux pastilles exactement
     * superposées, dont la seconde masque la première sans que rien ne le dise.
     */
    @Test
    fun `un lieu desservi deux fois ne pose qu un marqueur`() {
        val markers = buildLineStopMarkers(
            "C6-0",
            listOf(
                LineJourneyStop("a", "Hermeland", HERMELAND),
                LineJourneyStop("b", "Commerce", COMMERCE),
                LineJourneyStop("c", "Hermeland", HERMELAND),
            ),
        )

        assertEquals(listOf("Hermeland", "Commerce"), markers.map { it.name })
        assertEquals(listOf(0, 2), markers.first().sequences)
        assertEquals(listOf("a", "c"), markers.first().stopIds)
    }

    /**
     * Un parcours en boucle part et revient au même lieu : le marqueur porte les
     * deux rôles, faute de quoi l'un des deux bouts serait peint comme un arrêt
     * ordinaire.
     */
    @Test
    fun `une boucle porte les deux roles sur un seul marqueur`() {
        val markers = buildLineStopMarkers(
            "boucle",
            listOf(
                LineJourneyStop("a", "Hermeland", HERMELAND),
                LineJourneyStop("b", "Commerce", COMMERCE),
                LineJourneyStop("c", "Hermeland", HERMELAND),
            ),
        )

        assertEquals(LineStopMarker.Role.BOTH, markers.first().role)
    }

    /**
     * Deux quais du même pôle sont le même lieu ; le marqueur se pose entre les
     * deux plutôt que sur l'un d'eux, qui n'a pas plus de titre que l'autre.
     */
    @Test
    fun `deux quais voisins se reunissent au milieu`() {
        val nord = Coordinate(47.2130, -1.5580)
        val sud = Coordinate(47.2120, -1.5580)
        val markers = buildLineStopMarkers(
            "p",
            listOf(
                LineJourneyStop("a", "Commerce", nord),
                LineJourneyStop("b", "Commerce", sud),
            ),
        )

        assertEquals(1, markers.size)
        assertEquals(47.2125, markers.first().coordinate?.latitude ?: 0.0, 1e-6)
    }

    /**
     * Le second critère du regroupement, et la raison pour laquelle le nom seul
     * ne suffit pas : « Mairie » existe dans presque chaque commune.
     */
    @Test
    fun `deux homonymes eloignes restent deux marqueurs`() {
        val markers = buildLineStopMarkers(
            "p",
            listOf(
                LineJourneyStop("a", "Mairie", Coordinate(47.2130, -1.5580)),
                LineJourneyStop("b", "Bourg", Coordinate(47.2500, -1.6000)),
                LineJourneyStop("c", "Mairie", Coordinate(47.3000, -1.7000)),
            ),
        )

        assertEquals(3, markers.size)
        assertEquals(listOf("Mairie", "Bourg", "Mairie"), markers.map { it.name })
    }

    /** Casse et accents ne font pas deux lieux — c'est la règle du réseau. */
    @Test
    fun `le nom se compare sans accent ni casse`() {
        val markers = buildLineStopMarkers(
            "p",
            listOf(
                LineJourneyStop("a", "Hôtel Dieu", COMMERCE),
                LineJourneyStop("b", "HOTEL-DIEU", COMMERCE),
            ),
        )

        assertEquals(1, markers.size)
    }

    /**
     * Un rang sans position **reste dans la liste** : c'est la carte qui ne peut
     * pas le poser, pas le réseau qui cesse de le desservir.
     */
    @Test
    fun `un arret sans position garde son rang`() {
        val markers = buildLineStopMarkers(
            "p",
            listOf(
                LineJourneyStop("a", "Doulon", DOULON),
                LineJourneyStop("b", "Sans position", null),
                LineJourneyStop("c", "Armor", ARMOR),
            ),
        )

        assertEquals(3, markers.size)
        assertNull(markers[1].coordinate)
    }

    /** L'identité vient du parcours : deux parcours ne se disputent pas un rang. */
    @Test
    fun `l identifiant porte le parcours et le premier rang`() {
        val markers = buildLineStopMarkers("C3-3", stops)
        assertEquals(listOf("C3-3#0", "C3-3#1", "C3-3#2", "C3-3#3"), markers.map { it.id })
    }

    @Test
    fun `un parcours vide ne donne aucun marqueur`() {
        assertEquals(emptyList(), buildLineStopMarkers("p", emptyList()))
    }

    private companion object {
        val DOULON = Coordinate(47.2320, -1.5140)
        val COMMERCE = Coordinate(47.2130, -1.5580)
        val PROCE = Coordinate(47.2260, -1.5820)
        val ARMOR = Coordinate(47.2350, -1.6280)
        val HERMELAND = Coordinate(47.2300, -1.6100)

        val stops = listOf(
            LineJourneyStop("1", "Doulon", DOULON),
            LineJourneyStop("2", "Commerce", COMMERCE),
            LineJourneyStop("3", "Procé", PROCE),
            LineJourneyStop("4", "Armor", ARMOR),
        )
    }
}
