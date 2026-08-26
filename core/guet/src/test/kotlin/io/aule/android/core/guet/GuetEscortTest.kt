package io.aule.android.core.guet

import io.aule.android.core.geo.ApproachState
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.PositionSample
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'accompagnement et ses quatre sorties.
 *
 * **C'est la quatrième qui compte.** Sans délai de garde, un accompagnement
 * oublié — le téléphone remis en poche, le bus pris sans qu'on ait rien touché —
 * tient le GPS allumé jusqu'à ce que la batterie le dise. C'est le genre de défaut
 * qui ne se voit sur aucun écran.
 *
 * Port de `GuetEscortTests.swift`.
 */
class GuetEscortTest {

    private val reference: Instant = Instant.ofEpochSecond(1_755_792_000)
    private val quay = Coordinate(latitude = 47.2136, longitude = -1.5601)
    private val key = PassageKey.make("Commerce", "1", "Beaujoire", reference.plusSeconds(600))

    private fun escort(walk: Int = 300) =
        GuetEscort(key = key, startedAt = reference, predictedWalkSeconds = walk)

    private fun sample(
        meters: Double,
        accuracy: Double = 10.0,
        at: Instant = reference,
    ): PositionSample {
        // Un degré de latitude vaut environ 111 320 m.
        val offset = meters / 111_320.0
        return PositionSample(
            coordinate = Coordinate(latitude = quay.latitude + offset, longitude = quay.longitude),
            accuracyMeters = accuracy,
            speedMetersPerSecond = 1.4,
            at = at,
        )
    }

    // ------------------------------------------------------- les quatre sorties

    @Test
    fun `tant que rien n a tranche l accompagnement continue`() {
        val outcome = GuetEscort.outcome(
            approach = ApproachState.AWAY,
            hasBoarded = false,
            expectedAt = reference.plusSeconds(600),
            now = reference,
        )

        assertEquals(GuetEscortOutcome.ONGOING, outcome)
        assertFalse(outcome.isFinished)
    }

    @Test
    fun `arriver au quai est la fin normale`() {
        val outcome = GuetEscort.outcome(
            approach = ApproachState.ARRIVED,
            hasBoarded = false,
            expectedAt = reference.plusSeconds(600),
            now = reference,
        )

        assertEquals(GuetEscortOutcome.ARRIVED, outcome)
        assertTrue(outcome.isFinished)
    }

    /** Le plus certain d'abord : monté l'emporte même si le GPS n'a rien confirmé. */
    @Test
    fun `monter l emporte sur tout le reste`() {
        val outcome = GuetEscort.outcome(
            approach = ApproachState.AWAY,
            hasBoarded = true,
            expectedAt = reference.minusSeconds(3600),
            now = reference,
        )

        assertEquals(GuetEscortOutcome.BOARDED, outcome)
    }

    /**
     * ⚠️ **Le délai de garde, et la raison d'être de ce fichier.** Cinq minutes
     * après l'heure de passage, plus rien de ce qu'on pourrait apprendre ne
     * servirait — et le GPS doit s'éteindre.
     */
    @Test
    fun `cinq minutes apres le passage l accompagnement expire`() {
        val expectedAt = reference.plusSeconds(600)

        val justBefore = GuetEscort.outcome(
            ApproachState.AWAY,
            hasBoarded = false,
            expectedAt = expectedAt,
            now = expectedAt.plusSeconds(GuetEscort.GUARD_DELAY_SECONDS - 1),
        )
        assertEquals(GuetEscortOutcome.ONGOING, justBefore)

        val atDelay = GuetEscort.outcome(
            ApproachState.AWAY,
            hasBoarded = false,
            expectedAt = expectedAt,
            now = expectedAt.plusSeconds(GuetEscort.GUARD_DELAY_SECONDS),
        )
        assertEquals(GuetEscortOutcome.EXPIRED, atDelay)
        assertTrue(atDelay.isFinished)
    }

    /** L'annulation ne s'observe pas, elle se décide : elle n'est pas dans le verdict. */
    @Test
    fun `l annulation est une fin comme les autres`() {
        assertTrue(GuetEscortOutcome.CANCELLED.isFinished)
    }

    // ------------------------------------------------------------- la marche

    @Test
    fun `la marche reelle ne se mesure qu une fois arrive`() {
        val escort = escort()

        assertNull(escort.actualWalkSeconds(reference.plusSeconds(200)))

        // Vingt-deux mètres ou moins tranchent seuls, sans seconde confirmation.
        escort.observe(sample(meters = 10.0), quay, reference)

        assertEquals(ApproachState.ARRIVED, escort.approach.state)
        assertEquals(240, escort.actualWalkSeconds(reference.plusSeconds(240)))
    }

    @Test
    fun `un point imprecis ou perime ne decide de rien`() {
        val escort = escort()

        escort.observe(sample(meters = 5.0, accuracy = 500.0), quay, reference)
        assertEquals(ApproachState.UNDECIDED, escort.approach.state)

        // Un point sorti du cache système n'est pas un point : on attend le flux.
        escort.observe(sample(meters = 5.0, at = reference.minusSeconds(600)), quay, reference)
        assertEquals(ApproachState.UNDECIDED, escort.approach.state)
    }

    /**
     * Un unique point optimiste ferait annoncer l'arrivée deux rues trop tôt.
     * Au-delà du seuil certain, il faut deux confirmations.
     */
    @Test
    fun `au-dela du seuil certain il faut deux confirmations`() {
        val escort = escort()

        escort.observe(sample(meters = 35.0), quay, reference)
        assertEquals(ApproachState.UNDECIDED, escort.approach.state, "un seul point ne tranche pas")

        escort.observe(sample(meters = 35.0), quay, reference)
        assertEquals(ApproachState.ARRIVED, escort.approach.state)
    }

    // ------------------------------------------------------------- le niveau

    /**
     * ⚠️ **Le seul endroit du projet où le suivi de niveau sert.** Pendant un
     * accompagnement, l'utilisateur *regarde* le niveau bouger : un point GPS
     * pessimiste y ferait clignoter « pressez le pas » chez quelqu'un qui marche
     * normalement.
     */
    @Test
    fun `le niveau montre est le niveau suivi et non le brut`() {
        val escort = escort()

        escort.track(
            GuetLevel(GuetPhase.LEAVE_NOW, GuetFeasibility.COMFORTABLE),
            GuetLevelCause.POSITION,
        )
        escort.track(
            GuetLevel(GuetPhase.LEAVE_NOW, GuetFeasibility.RISKY),
            GuetLevelCause.POSITION,
        )

        assertEquals(
            GuetFeasibility.COMFORTABLE,
            escort.level.feasibility,
            "un seul point pessimiste ne dégrade pas ce qu'on montre",
        )
    }
}
