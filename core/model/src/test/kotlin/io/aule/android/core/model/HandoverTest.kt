package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.HandoverFix
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class HandoverTest {

    @Test
    fun `le fil se lit sans phrase a l ecran`() {
        assertEquals(HandoverStatus.ENGAGED, HandoverStatus.fromWire(null))
        assertEquals(HandoverStatus.ENGAGED, HandoverStatus.fromWire("engaged"))
        assertEquals(HandoverStatus.COMPLETED, HandoverStatus.fromWire("completed"))
        assertEquals(HandoverStatus.CANCELLED, HandoverStatus.fromWire("cancelled"))
        assertEquals(HandoverStatus.EXPIRED, HandoverStatus.fromWire("expired"))
    }

    @Test
    fun `sans service arrivant on n adopte rien`() {
        val summary = HandoverSummary(
            id = "hov-1",
            status = HandoverStatus.COMPLETED,
            lineId = "C6",
            outgoingServiceId = "svc-out",
        )
        val target = HandoverTarget(serviceId = "svc-out", lineId = "C6")
        assertNull(
            summary.toIncomingService(
                target = target,
                lineLabel = "C6",
                startedAt = java.time.Instant.EPOCH,
            ),
        )
    }

    @Test
    fun `la confirmation rend le service de l arrivant`() {
        val summary = HandoverSummary(
            id = "hov-1",
            status = HandoverStatus.COMPLETED,
            lineId = "C6",
            outgoingServiceId = "svc-out",
            incomingServiceId = "svc-in",
            vehicleId = "324",
        )
        val target = HandoverTarget(
            serviceId = "svc-out",
            lineId = "C6",
            directionId = 0,
            terminus = "Hermeland",
            trainNumber = "1-12",
        )
        val started = summary.toIncomingService(
            target = target,
            lineLabel = "C6",
            startedAt = java.time.Instant.parse("2026-08-16T16:00:00Z"),
        )
        assertEquals("svc-in", started?.id)
        assertEquals("C6", started?.lineLabel)
        assertEquals(0, started?.directionId)
        assertEquals("Hermeland", started?.terminus)
        assertEquals("324", started?.vehicleId)
        assertEquals("1-12", started?.trainNumber)
    }

    @Test
    fun `un point de plus de trente secondes n est plus une position`() {
        val fresh = HandoverFix(
            coordinate = Coordinate(47.21, -1.55),
            recordedAt = java.time.Instant.EPOCH,
            ageSeconds = 30,
        )
        val stale = fresh.copy(ageSeconds = 31)
        assertEquals(true, fresh.isReliable)
        assertEquals(false, stale.isReliable)
    }
}
