package io.aule.android.core.model

import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ServiceHeartbeatTest {

    private val now: Instant = Instant.parse("2026-08-12T09:00:00Z")

    private fun publish(
        fixAt: Instant = now,
        mocked: Boolean = false,
        publishInFlight: Boolean = false,
        serviceClosed: Boolean = false,
        inBackground: Boolean = false,
        lastPublishAt: Instant? = null,
    ): Boolean = shouldPublishHeartbeat(
        now = now,
        fixAt = fixAt,
        mocked = mocked,
        publishInFlight = publishInFlight,
        serviceClosed = serviceClosed,
        inBackground = inBackground,
        lastPublishAt = lastPublishAt,
    )

    @Test
    fun `le premier fix se publie tout de suite`() {
        assertTrue(publish())
    }

    @Test
    fun `cinq secondes separent deux publications ecran allume`() {
        assertFalse(publish(lastPublishAt = now.minus(Duration.ofSeconds(4))))
        assertTrue(publish(lastPublishAt = now.minus(Duration.ofSeconds(5))))
    }

    @Test
    fun `quinze secondes en arriere-plan`() {
        assertFalse(
            publish(
                inBackground = true,
                lastPublishAt = now.minus(Duration.ofSeconds(14)),
            ),
        )
        assertTrue(
            publish(
                inBackground = true,
                lastPublishAt = now.minus(Duration.ofSeconds(15)),
            ),
        )
    }

    @Test
    fun `un service solde ne se publie plus`() {
        assertFalse(publish(serviceClosed = true))
    }

    @Test
    fun `une position simulee n entre pas dans l information voyageurs`() {
        assertFalse(publish(mocked = true))
    }

    @Test
    fun `une publication deja en vol`() {
        assertFalse(publish(publishInFlight = true))
    }

    @Test
    fun `un fix perime ne se publie pas`() {
        assertTrue(publish(fixAt = now.minus(Duration.ofSeconds(59))))
        assertFalse(publish(fixAt = now.minus(Duration.ofSeconds(61))))
    }

    @Test
    fun `un refus l emporte sur une cadence echue`() {
        assertFalse(
            publish(
                serviceClosed = true,
                lastPublishAt = now.minus(Duration.ofHours(1)),
            ),
        )
    }

    @Test
    fun `la premiere fois la releve s annonce`() {
        val verdict = readHeartbeat(beat(handover = handover()))
        assertTrue(verdict.announceHandover)
        assertEquals("h-1", verdict.liveHandover?.id)
        assertTrue(verdict.serviceStillOpen)
    }

    @Test
    fun `les fois suivantes elle s affiche sans sonner`() {
        val verdict = readHeartbeat(beat(handover = handover()), knownHandoverId = "h-1")
        assertFalse(verdict.announceHandover)
        assertEquals("h-1", verdict.liveHandover?.id)
    }

    @Test
    fun `une autre releve s annonce a son tour`() {
        val verdict = readHeartbeat(
            beat(handover = handover(id = "h-2")),
            knownHandoverId = "h-1",
        )
        assertTrue(verdict.announceHandover)
    }

    @Test
    fun `annulee elle disparait du bandeau`() {
        val verdict = readHeartbeat(
            beat(handover = handover(status = HandoverStatus.CANCELLED)),
            knownHandoverId = "h-1",
        )
        assertNull(verdict.liveHandover)
        assertFalse(verdict.announceHandover)
        assertTrue(verdict.serviceStillOpen)
    }

    @Test
    fun `sans releve rien a dire`() {
        val verdict = readHeartbeat(beat())
        assertNull(verdict.liveHandover)
        assertFalse(verdict.announceHandover)
        assertFalse(verdict.stopPublishing)
    }

    @Test
    fun `un service solde arrete la publication`() {
        val verdict = readHeartbeat(beat(status = "ended"))
        assertFalse(verdict.serviceStillOpen)
        assertTrue(verdict.stopPublishing)
        assertNull(verdict.handedOverTo)
    }

    @Test
    fun `un vehicule remis se distingue d une fin de service`() {
        val verdict = readHeartbeat(
            beat(
                status = "ended",
                handover = handover(status = HandoverStatus.COMPLETED),
            ),
        )
        assertTrue(verdict.stopPublishing)
        assertEquals("Camille", verdict.handedOverTo?.incomingDisplay)
    }

    @Test
    fun `solde sans releve aboutie n est pas une remise`() {
        val verdict = readHeartbeat(
            beat(
                status = "ended",
                handover = handover(status = HandoverStatus.CANCELLED),
            ),
        )
        assertNull(verdict.handedOverTo)
        assertEquals("h-1", verdict.liveHandover?.id)
    }

    private fun handover(
        id: String = "h-1",
        status: HandoverStatus = HandoverStatus.ENGAGED,
    ) = HandoverSummary(
        id = id,
        status = status,
        lineId = "L1",
        outgoingServiceId = "s-out",
        incomingDisplay = "Camille",
        reliefStopName = "Commerce",
    )

    private fun beat(
        status: String = "active",
        handover: HandoverSummary? = null,
    ) = ServiceHeartbeat(
        serviceStatus = status,
        published = true,
        serverTime = Instant.parse("2026-08-12T09:00:00Z"),
        handover = handover,
    )
}
