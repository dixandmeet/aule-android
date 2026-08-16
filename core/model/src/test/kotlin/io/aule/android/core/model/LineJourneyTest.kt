package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class LineJourneyTest {

    @Test
    fun `sans position on garde l ordre de la ligne`() {
        val ordered = fallbackStopsByProximity(STOPS, around = null)
        assertEquals(listOf("Hermeland", "Commerce", "Chantrerie"), ordered.map { it.name })
    }

    @Test
    fun `les arrets restants partent du plus proche du vehicule`() {
        val remaining = remainingReliefStops(STOPS, COMMERCE)
        assertEquals(listOf("Commerce", "Chantrerie"), remaining.map { it.name })
    }

    @Test
    fun `sans position du vehicule on garde toute la desserte`() {
        assertEquals(STOPS, remainingReliefStops(STOPS, vehicle = null))
    }

    @Test
    fun `avec une position on range du plus proche au plus loin`() {
        val around = Coordinate(47.213, -1.558)
        val ordered = fallbackStopsByProximity(STOPS, around)
        assertEquals("Commerce", ordered.first().name)
    }

    @Test
    fun `deux quais du meme nom ne se proposent qu une fois`() {
        val journey = LineJourney(
            tripId = "t1",
            stops = listOf(
                LineJourneyStop("a", "Commerce", COMMERCE),
                LineJourneyStop("b", "Commerce", COMMERCE),
                LineJourneyStop("c", "Chantrerie", CHANTRERIE),
            ),
        )
        assertEquals(listOf("Commerce", "Chantrerie"), journey.distinctStops().map { it.name })
    }

    @Test
    fun `les passages du terminus choisi passent devant`() {
        val result = selectFallbackPassages(
            lineLabel = "C6",
            terminus = "Hermeland",
            serving = listOf(
                ServingLine("C6", "Hermeland"),
                ServingLine("C6", "Chantrerie"),
                ServingLine("C1", "François Mitterrand"),
            ),
            departures = listOf(
                departure("C6", "Chantrerie", "16:10:00Z"),
                departure("C6", "Hermeland", "16:05:00Z"),
                departure("C1", "François Mitterrand", "16:01:00Z"),
            ),
        )
        assertFalse(result.showsAllDirections)
        assertEquals(listOf("Hermeland"), result.passages.map { it.destination })
    }

    @Test
    fun `a un terminus on montre les deux sens`() {
        val result = selectFallbackPassages(
            lineLabel = "C6",
            terminus = "Hermeland",
            serving = listOf(ServingLine("C6", "Chantrerie")),
            departures = listOf(
                departure("C6", "Chantrerie", "16:05:00Z"),
                departure("C6", "Chantrerie", "16:20:00Z"),
            ),
        )
        assertTrue(result.showsAllDirections)
        assertEquals(2, result.passages.size)
    }

    @Test
    fun `le passage retenu est le prochain encore devant`() {
        val now = Instant.parse("2026-08-16T16:10:00Z")
        val times = listOf(
            Instant.parse("2026-08-16T16:05:00Z"),
            Instant.parse("2026-08-16T16:12:00Z"),
            Instant.parse("2026-08-16T16:20:00Z"),
        )
        assertEquals(Instant.parse("2026-08-16T16:12:00Z"), plannedReliefPassage(times, now))
        assertEquals(
            Instant.parse("2026-08-16T16:05:00Z"),
            plannedReliefPassage(times, Instant.parse("2026-08-16T16:30:00Z")),
        )
        assertEquals(null, plannedReliefPassage(emptyList(), now))
    }

    @Test
    fun `un arret de releve se retrouve par position a 120 m`() {
        val summary = HandoverSummary(
            id = "hov-1",
            status = HandoverStatus.ENGAGED,
            lineId = "C6",
            outgoingServiceId = "svc",
            reliefStopId = "ghost",
            reliefStopName = "Autre nom",
            reliefStopCoordinate = Coordinate(47.2135, -1.5581),
        )
        val found = matchReliefStop(STOPS, summary)
        assertEquals("Commerce", found?.name)
        assertEquals("2", found?.id)
    }

    @Test
    fun `au dela de 120 m on ne rattache pas l arret`() {
        val summary = HandoverSummary(
            id = "hov-1",
            status = HandoverStatus.ENGAGED,
            lineId = "C6",
            outgoingServiceId = "svc",
            reliefStopCoordinate = Coordinate(48.0, -1.0),
        )
        assertEquals(null, matchReliefStop(STOPS, summary))
    }

    private fun departure(line: String, destination: String, at: String) = StopDeparture(
        id = "$line-$destination-$at",
        line = line,
        destination = destination,
        expectedAt = Instant.parse("2026-08-16T$at"),
        isRealtime = false,
    )

    private companion object {
        val HERMELAND = Coordinate(47.29, -1.52)
        val COMMERCE = Coordinate(47.2134, -1.558)
        val CHANTRERIE = Coordinate(47.28, -1.52)
        val STOPS = listOf(
            LineJourneyStop("1", "Hermeland", HERMELAND),
            LineJourneyStop("2", "Commerce", COMMERCE),
            LineJourneyStop("3", "Chantrerie", CHANTRERIE),
        )
    }
}
