package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le plan de ligne d'un véhicule suivi : où il en est, et ce que valent les
 * heures qu'on affiche devant lui.
 */
class VehicleRunTest {

    private val stops = listOf(
        ScheduledTripStop("a", "Gare de Chantenay", Coordinate(47.20, -1.58), Instant.parse("2026-08-16T15:50:00Z")),
        ScheduledTripStop("b", "Platanes", Coordinate(47.21, -1.58), Instant.parse("2026-08-16T16:00:00Z")),
        ScheduledTripStop("c", "Ranzay", Coordinate(47.22, -1.58), Instant.parse("2026-08-16T16:10:00Z")),
    )

    private val trip = ScheduledTrip(
        departureId = "dep-1",
        lineId = "C1",
        lineLabel = "C1",
        directionId = 0,
        destination = "Ranzay",
        stops = stops,
    )

    private fun vehicle(
        coordinate: Coordinate = Coordinate(47.205, -1.58),
        feed: VehicleFeed = VehicleFeed.LIVE,
        nextStop: String? = null,
    ) = TransportVehicle(
        id = "v1",
        mode = TransportMode.BUS,
        feed = feed,
        lineId = "C1",
        lineName = "C1",
        coordinate = coordinate,
        nextStop = nextStop,
    )

    @Test
    fun `une position mesuree coupe la desserte la ou le vehicule se trouve`() {
        val run = trip.runFor(vehicle(), Instant.parse("2026-08-16T15:55:00Z"))

        assertEquals(RunStopState.SERVED, run.stops[0].state)
        assertEquals(RunStopState.NEXT, run.stops[1].state)
        assertEquals(RunStopState.AHEAD, run.stops[2].state)
        assertEquals("Platanes", run.nextStop?.name)
        assertEquals(2, run.remaining)
        assertFalse(run.isFinished)
        assertTrue(run.isLive)
    }

    /**
     * À mi-chemin du premier tronçon, la course aurait dû être là à 15:55. Il
     * est 15:58 : trois minutes de retard, et elles se reportent devant.
     */
    @Test
    fun `le retard mesure decale les heures a venir et seulement elles`() {
        val run = trip.runFor(vehicle(), Instant.parse("2026-08-16T15:58:00Z"))

        assertEquals(Duration.ofMinutes(3), run.delay)
        assertNull(run.stops[0].expectedAt)
        assertEquals(Instant.parse("2026-08-16T16:03:00Z"), run.stops[1].expectedAt)
        assertEquals(Instant.parse("2026-08-16T16:13:00Z"), run.stops[2].expectedAt)
        // L'horaire du dépôt ne bouge pas pour autant : c'est lui qu'on compare
        // à la feuille de route.
        assertEquals(Instant.parse("2026-08-16T16:00:00Z"), run.stops[1].plannedAt)
        assertEquals(run.stops[0].plannedAt, run.stops[0].at)
    }

    /**
     * Une position calculée depuis l'horaire retomberait pile sur le tracé
     * d'où elle sort : elle annoncerait zéro minute de retard avec l'aplomb
     * d'une mesure.
     */
    @Test
    fun `un vehicule theorique n annonce aucun retard`() {
        val run = trip.runFor(
            vehicle(feed = VehicleFeed.SCHEDULED, nextStop = "Platanes"),
            Instant.parse("2026-08-16T15:58:00Z"),
        )

        assertFalse(run.isLive)
        assertNull(run.delay)
        assertTrue(run.stops.all { it.expectedAt == null })
        assertEquals("Platanes", run.nextStop?.name)
    }

    /** Le flux et le catalogue n'accentuent pas toujours pareil. */
    @Test
    fun `le prochain arret annonce se retrouve malgre la casse et les accents`() {
        val run = trip.runFor(
            vehicle(feed = VehicleFeed.SCHEDULED, nextStop = "GARE DE CHANTENAY"),
            Instant.parse("2026-08-16T16:05:00Z"),
        )

        assertEquals(0, run.nextIndex)
        assertEquals(RunStopState.NEXT, run.stops[0].state)
    }

    /** Sans rien d'annoncé, il reste l'horloge et l'horaire. */
    @Test
    fun `sans position ni prochain arret la coupure suit l heure`() {
        val run = trip.runFor(
            vehicle(feed = VehicleFeed.SCHEDULED),
            Instant.parse("2026-08-16T16:05:00Z"),
        )

        assertEquals(2, run.nextIndex)
        assertEquals(RunStopState.SERVED, run.stops[1].state)
        assertEquals("Ranzay", run.nextStop?.name)
    }

    /**
     * Un véhicule dérouté — déviation, terminus partiel — n'est plus sur la
     * course qu'on affiche. On ne lui invente ni retard ni avancement.
     */
    @Test
    fun `hors du trace on retombe sur ce que le flux annonce`() {
        val run = trip.runFor(
            vehicle(coordinate = Coordinate(47.205, -1.60), nextStop = "Ranzay"),
            Instant.parse("2026-08-16T15:58:00Z"),
        )

        assertFalse(run.isLive)
        assertNull(run.delay)
        assertEquals(2, run.nextIndex)
    }

    @Test
    fun `passe le terminus il ne reste rien a desservir`() {
        val run = trip.runFor(
            vehicle(coordinate = Coordinate(47.22, -1.58)),
            Instant.parse("2026-08-16T16:10:00Z"),
        )

        assertTrue(run.isFinished)
        assertEquals(0, run.remaining)
        assertNull(run.nextStop)
        assertTrue(run.stops.all { it.state == RunStopState.SERVED })
    }

    @Test
    fun `l heure theorique s interpole entre deux arrets`() {
        assertEquals(Instant.parse("2026-08-16T15:55:00Z"), trip.scheduleAt(0.25))
        assertEquals(Instant.parse("2026-08-16T15:50:00Z"), trip.scheduleAt(0.0))
        assertEquals(Instant.parse("2026-08-16T16:10:00Z"), trip.scheduleAt(1.0))
    }
}
