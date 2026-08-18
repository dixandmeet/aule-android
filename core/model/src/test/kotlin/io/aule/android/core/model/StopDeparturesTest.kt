package io.aule.android.core.model

import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/** Port de `DeparturesGroupingTests` dans `Native/AuleTests/BackendTests.swift`. */
class StopDeparturesTest {

    private val origin: Instant = Instant.ofEpochSecond(1_000)

    private fun departure(
        line: String,
        destination: String,
        inMinutes: Long,
        live: Boolean = true,
    ) = StopDeparture(
        id = "$line-$inMinutes",
        line = line,
        lineColor = "#00a754",
        destination = destination,
        expectedAt = origin.plusSeconds(inMinutes * 60),
        isRealtime = live,
        mode = TransportMode.TRAM,
    )

    /**
     * Sans regroupement, un arrêt de tram affiche huit lignes pour dire une seule
     * chose : la 1 passe dans 2, 6 et 11 minutes.
     */
    @Test
    fun `les passages se regroupent par ligne et destination`() {
        val result = StopDepartures(
            stopName = "Commerce",
            departures = listOf(
                departure("1", "Beaujoire", 11),
                departure("2", "Orvault", 4),
                departure("1", "Beaujoire", 2),
                departure("1", "Beaujoire", 6),
            ),
            outcome = DeparturesOutcome.ANNOUNCED,
            fetchedAt = origin,
        )

        val rows = result.grouped(from = origin)
        assertEquals(2, rows.size)
        // La ligne dont le prochain passage est le plus tôt vient en premier.
        assertEquals("1", rows[0].line)
        assertEquals(listOf(2, 6, 11), rows[0].waits)
        assertEquals(listOf(4), rows[1].waits)
    }

    /** Zéro minute n'est pas « 0 min » : ça se lit comme une panne d'affichage. */
    @Test
    fun `zero minute se dit approche, pas zero min`() {
        val result = StopDepartures(
            stopName = "Commerce",
            departures = listOf(departure("1", "Beaujoire", 0)),
            outcome = DeparturesOutcome.ANNOUNCED,
            fetchedAt = origin,
        )
        val row = result.grouped(from = origin).single()
        assertEquals(Wait.Approaching, row.nextWait)
        assertEquals(emptyList(), row.followingWaits)
    }

    /** Au-delà de trois attentes, la rangée n'apprend plus rien de plus. */
    @Test
    fun `une rangee garde au plus trois attentes`() {
        val result = StopDepartures(
            stopName = "Commerce",
            departures = (1L..6L).map { departure("1", "Beaujoire", it * 2) },
            outcome = DeparturesOutcome.ANNOUNCED,
            fetchedAt = origin,
        )
        assertEquals(listOf(2, 4, 6), result.grouped(from = origin).single().waits)
    }

    @Test
    fun `les attentes suivantes s arretent a l heure`() {
        val departures = StopDepartures(
            stopName = "Ranzay",
            departures = listOf(
                departure("80", "Bellevue", 20),
                departure("80", "Bellevue", 80),
                departure("80", "Bellevue", 140),
            ),
            outcome = DeparturesOutcome.ANNOUNCED,
            fetchedAt = origin,
        )

        val row = departures.grouped(from = origin).single()

        // La prochaine reste, quoi qu'il arrive : c'est elle qu'on est venu
        // chercher. Ce sont les suivantes qui cessent d'aider.
        assertEquals(Wait.Minutes(20), row.nextWait)
        assertEquals(emptyList(), row.followingWaits)
    }

    @Test
    fun `un passage en retard n affiche pas une attente negative`() {
        val late = departure("1", "Beaujoire", 0)
        assertEquals(0, late.waitMinutes(from = origin.plusSeconds(1_000)))
    }

    /** Deux destinations d'une même ligne sont deux rangées, pas une. */
    @Test
    fun `une meme ligne vers deux destinations donne deux rangees`() {
        val result = StopDepartures(
            stopName = "Commerce",
            departures = listOf(
                departure("1", "Beaujoire", 3),
                departure("1", "François Mitterrand", 5),
            ),
            outcome = DeparturesOutcome.ANNOUNCED,
            fetchedAt = origin,
        )
        assertEquals(2, result.grouped(from = origin).size)
    }
}
