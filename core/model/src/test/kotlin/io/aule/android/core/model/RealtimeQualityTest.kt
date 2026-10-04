package io.aule.android.core.model

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class RealtimeQualityTest {
    private val now = Instant.parse("2026-10-03T18:00:00Z")
    private fun announcement(realtime: Boolean) = StopDepartures(
        stopName = "Commerce", outcome = DeparturesOutcome.ANNOUNCED, fetchedAt = now,
        departures = listOf(StopDeparture(id = "one", line = "C6", destination = "Hermeland",
            expectedAt = now.plusSeconds(30), isRealtime = realtime)),
    )

    @Test
    fun `approche requiert une annonce temps reel recente`() {
        assertEquals(Wait.Approaching, announcement(true).grouped(now).first().nextWait)
        assertEquals(Wait.Minutes(1), announcement(false).grouped(now).first().nextWait)
        assertFalse(announcement(true).grouped(now.plusSeconds(91)).first().isRealtime)
    }

    @Test
    fun `une panne garde la date sans garder le temps reel`() {
        val previous = announcement(true)
        val failure = StopDepartures("Commerce", outcome = DeparturesOutcome.PROVIDER_SILENT, fetchedAt = Instant.EPOCH)
        val retained = failure.retainingPreviousOnFailure(previous)
        assertEquals(previous.fetchedAt, retained.fetchedAt)
        assertEquals(previous.departures, retained.departures)
        assertFalse(retained.grouped(now).first().isRealtime)
        val empty = failure.copy(outcome = DeparturesOutcome.NOTHING_ANNOUNCED, fetchedAt = now)
        assertEquals(empty, empty.retainingPreviousOnFailure(previous))
    }
}
