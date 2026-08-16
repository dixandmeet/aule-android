package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ScheduledTripTest {

    private val stops = listOf(
        ScheduledTripStop("a", "Départ", Coordinate(47.20, -1.55), Instant.parse("2026-08-16T15:50:00Z")),
        ScheduledTripStop("b", "Milieu", Coordinate(47.21, -1.55), Instant.parse("2026-08-16T16:00:00Z")),
        ScheduledTripStop("c", "Terminus", Coordinate(47.22, -1.55), Instant.parse("2026-08-16T16:10:00Z")),
    )

    private val trip = ScheduledTrip(
        departureId = "dep-1",
        lineId = "1",
        lineLabel = "1",
        directionId = 0,
        destination = "Terminus",
        stops = stops,
    )

    @Test
    fun `positionAtElapsed interpole entre deux arrets`() {
        val point = positionAtElapsed(
            offsets = listOf(0, 600, 1200),
            positions = stops.map { it.coordinate },
            elapsedSeconds = 300,
        )
        assertNotNull(point)
        assertEquals(47.205, point.latitude, 0.0001)
    }

    @Test
    fun `un vehicule arrete garde une ETA via le retard`() {
        val engine = HandoverProgressEngine(trip, reliefStopIndex = 2)
        val now = Instant.parse("2026-08-16T16:05:00Z")
        // À quai au milieu, 5 min après l'horaire → retard +5 → ETA terminus 16:15
        val progress = engine.update(
            position = Coordinate(47.21, -1.55),
            recordedAt = now.minusSeconds(2),
            now = now,
            speedMps = 0.0,
            fixAgeSeconds = 2,
        )
        assertNotNull(progress)
        assertTrue(progress.pathMatched)
        assertNotNull(progress.estimatedAt)
        assertEquals(Instant.parse("2026-08-16T16:15:00Z"), progress.estimatedAt)
        assertEquals(Duration.ofMinutes(5), progress.delay)
        assertEquals(1, progress.stopsRemaining)
    }

    @Test
    fun `sans tracé le moteur reste degradable`() {
        val bare = ScheduledTrip(
            departureId = "dep-2",
            lineId = "C6",
            lineLabel = "C6",
            directionId = 0,
            destination = "Terminus",
            stops = listOf(
                ScheduledTripStop("a", "A", null, Instant.parse("2026-08-16T16:00:00Z")),
                ScheduledTripStop("b", "B", null, Instant.parse("2026-08-16T16:10:00Z")),
            ),
            path = null,
        )
        val engine = HandoverProgressEngine(bare, reliefStopIndex = 1)
        val now = Instant.parse("2026-08-16T16:05:00Z")
        val progress = engine.update(
            position = Coordinate(47.21, -1.55),
            recordedAt = now,
            now = now,
            speedMps = 0.0,
            fixAgeSeconds = 0,
        )
        assertNotNull(progress)
        assertEquals(false, progress.pathMatched)
        assertNull(progress.estimatedAt)
    }
}
