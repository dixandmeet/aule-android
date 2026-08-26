package io.aule.android.core.guet

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les deux axes du niveau, et la règle de confirmation qui les sépare.
 *
 * Ce fichier existe pour deux défauts distincts. Le premier est de conception :
 * un énuméré unique rendait « partez maintenant, et c'est déjà serré »
 * inexprimable, et forçait à choisir laquelle des deux moitiés on tait — celle
 * qui fait agir, ou celle qui fait courir. Le second est de comportement :
 * confirmer deux fois une heure remesurée afficherait pendant tout un cycle de
 * rafraîchissement une heure qu'on sait fausse.
 *
 * Port de `GuetLevelTests.swift`.
 */
class GuetLevelTest {

    private val reference: Instant = Instant.ofEpochSecond(1_755_792_000)

    private fun timing(
        inMinutes: Double,
        walk: Int,
        platform: Int = 0,
        preparation: Int = 0,
    ) = GuetTiming.of(
        expectedAt = reference.plusMillis((inMinutes * 60_000).toLong()),
        walkSeconds = walk,
        platformSeconds = platform,
        preparationSeconds = preparation,
    )

    // ------------------------------------------------------------- les deux axes

    /**
     * **Le test qui interdit de refusionner les deux axes.** Un véhicule dans cinq
     * minutes, quatre minutes trente de marche, une minute de marge de quai
     * souhaitée : l'heure de partir est passée de trente secondes, et il reste
     * trente secondes de marge. Les deux sont vraies en même temps, et
     * l'utilisateur a besoin des deux.
     */
    @Test
    fun `partir maintenant ET serre s expriment ensemble`() {
        val level = GuetLevel.of(timing(inMinutes = 5.0, walk = 270, platform = 60), reference)

        assertEquals(GuetPhase.LEAVE_NOW, level.phase)
        assertEquals(GuetFeasibility.TIGHT, level.feasibility)
        assertTrue(level.isReachable)
    }

    @Test
    fun `trop tot reste confortable`() {
        val level = GuetLevel.of(timing(inMinutes = 20.0, walk = 300), reference)

        assertEquals(GuetPhase.TOO_EARLY, level.phase)
        assertEquals(GuetFeasibility.COMFORTABLE, level.feasibility)
    }

    /**
     * La phase `PREPARE` n'existe qu'avec une préparation non nulle — au réglage
     * par défaut, `alertAt == leaveAt` et l'intervalle est vide. Le vérifier évite
     * de croire à une phase morte le jour où elle ne s'affiche jamais.
     */
    @Test
    fun `la preparation ouvre la phase prepare sinon elle est vide`() {
        val withPrep = GuetLevel.of(
            timing(inMinutes = 8.0, walk = 300, preparation = 180),
            reference,
        )
        assertEquals(GuetPhase.PREPARE, withPrep.phase)

        val without = GuetLevel.of(timing(inMinutes = 8.0, walk = 300), reference)
        assertEquals(GuetPhase.TOO_EARLY, without.phase)
    }

    /**
     * Parti trop tard **mais rattrapable** : la phase dit `GONE`, la faisabilité
     * dit `RISKY`. C'est précisément le couple qu'un énuméré unique ne savait pas
     * former.
     *
     * Le couple n'existe **que si une marge de quai a été demandée**, et ce n'est
     * pas un artefact du test. `GONE` veut dire « en retard de plus d'une minute
     * sur l'heure de partir », et cette heure contient la marge : sans marge, il
     * ne reste rien à brûler. La marge de quai *est* le coussin qui rend le retard
     * rattrapable.
     */
    @Test
    fun `parti trop tard mais rattrapable`() {
        // Véhicule dans 100 s, 130 s de marche, 2 min de marge souhaitée :
        // l'heure de partir est passée de 150 s, et la marge est à −30 s.
        val level = GuetLevel.of(
            timing(inMinutes = 100.0 / 60, walk = 130, platform = 120),
            reference,
        )

        assertEquals(GuetPhase.GONE, level.phase)
        assertEquals(GuetFeasibility.RISKY, level.feasibility)
        assertTrue(level.isReachable)
    }

    /**
     * Le corollaire, écrit pour qu'on ne le prenne pas pour un défaut : **sans
     * marge de quai, être en retard c'est être en retard.**
     */
    @Test
    fun `sans marge de quai un retard d une minute est manque`() {
        val level = GuetLevel.of(timing(inMinutes = 100.0 / 60, walk = 190), reference)

        assertEquals(GuetPhase.GONE, level.phase)
        assertEquals(GuetFeasibility.MISSED, level.feasibility)
    }

    /**
     * ⚠️ Le défaut : avec une marche nulle, un tram parti depuis dix secondes rend
     * une marge de −10 s, que le seul seuil de marge lirait « rattrapable ».
     * L'heure de passage doit trancher d'abord.
     */
    @Test
    fun `un vehicule passe est manque quelle que soit la marge`() {
        val level = GuetLevel.of(timing(inMinutes = -10.0 / 60, walk = 0), reference)

        assertEquals(GuetFeasibility.MISSED, level.feasibility)
        assertFalse(level.isReachable)
    }

    @Test
    fun `au-dela d une minute de retard c est manque`() {
        val level = GuetLevel.of(timing(inMinutes = 1.0, walk = 130), reference)

        assertEquals(GuetFeasibility.MISSED, level.feasibility)
    }

    // ------------------------------------------------------------------ le suivi

    private fun level(phase: GuetPhase, feasibility: GuetFeasibility) =
        GuetLevel(phase, feasibility)

    /**
     * Un unique point GPS pessimiste ne doit pas annoncer « vous risquez de ne pas
     * l'avoir » à quelqu'un qui marche normalement.
     */
    @Test
    fun `une degradation sur position demande deux confirmations`() {
        val tracker = GuetLevelTracker()
        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.COMFORTABLE), GuetLevelCause.POSITION)
        assertEquals(GuetFeasibility.COMFORTABLE, tracker.level.feasibility)

        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.TIGHT), GuetLevelCause.POSITION)
        assertEquals(
            GuetFeasibility.COMFORTABLE,
            tracker.level.feasibility,
            "un seul point ne dégrade pas",
        )

        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.TIGHT), GuetLevelCause.POSITION)
        assertEquals(GuetFeasibility.TIGHT, tracker.level.feasibility, "le second point tranche")
    }

    /**
     * ⚠️ Le défaut que ce test empêche : une heure remesurée n'est pas du bruit,
     * c'est le réseau qui se corrige. La confirmer ferait afficher pendant tout un
     * cycle une marge qu'on sait déjà fausse.
     */
    @Test
    fun `une heure remesuree degrade immediatement`() {
        val tracker = GuetLevelTracker()
        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.COMFORTABLE), GuetLevelCause.POSITION)
        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.RISKY), GuetLevelCause.SCHEDULE)

        assertEquals(GuetFeasibility.RISKY, tracker.level.feasibility)
    }

    /** Apprendre qu'on va y arriver n'a pas à attendre. */
    @Test
    fun `la remontee est immediate`() {
        val tracker = GuetLevelTracker()
        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.RISKY), GuetLevelCause.POSITION)
        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.COMFORTABLE), GuetLevelCause.POSITION)

        assertEquals(GuetFeasibility.COMFORTABLE, tracker.level.feasibility)
    }

    /**
     * **La phase ne se fait jamais retenir par la faisabilité.** Sans cela, « il
     * faut partir » resterait bloqué derrière « la marge s'est peut-être
     * effondrée », et l'alerte arriverait un point GPS trop tard.
     */
    @Test
    fun `la phase passe pendant qu une degradation attend sa confirmation`() {
        val tracker = GuetLevelTracker()
        tracker.update(level(GuetPhase.TOO_EARLY, GuetFeasibility.COMFORTABLE), GuetLevelCause.POSITION)
        val after = tracker.update(
            level(GuetPhase.LEAVE_NOW, GuetFeasibility.TIGHT),
            GuetLevelCause.POSITION,
        )

        assertEquals(GuetPhase.LEAVE_NOW, after.phase)
        assertEquals(GuetFeasibility.COMFORTABLE, after.feasibility)
    }

    /**
     * Deux dégradations **différentes** ne s'additionnent pas en une
     * confirmation : le compteur repart. Sans cette remise à zéro, deux points
     * erratiques de gravités différentes vaudraient une confirmation qu'aucun des
     * deux ne porte.
     */
    @Test
    fun `deux degradations differentes ne se confirment pas l une l autre`() {
        val tracker = GuetLevelTracker()
        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.COMFORTABLE), GuetLevelCause.POSITION)
        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.TIGHT), GuetLevelCause.POSITION)
        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.RISKY), GuetLevelCause.POSITION)

        assertEquals(GuetFeasibility.COMFORTABLE, tracker.level.feasibility)
    }

    /** Le premier verdict est adopté sans confirmation : il n'y a rien à confirmer contre. */
    @Test
    fun `le premier verdict est adopte d emblee`() {
        val tracker = GuetLevelTracker()
        assertEquals(GuetLevel.UNDECIDED, tracker.level)

        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.RISKY), GuetLevelCause.POSITION)

        assertEquals(GuetFeasibility.RISKY, tracker.level.feasibility)
    }

    /**
     * Changer de passage suivi remet à zéro : garder l'état du précédent ferait
     * hériter le nouveau d'une dégradation qui ne le concerne pas.
     */
    @Test
    fun `le suivi se remet a l absence de reponse`() {
        val tracker = GuetLevelTracker()
        tracker.update(level(GuetPhase.LEAVE_NOW, GuetFeasibility.RISKY), GuetLevelCause.POSITION)
        tracker.reset()

        assertEquals(GuetLevel.UNDECIDED, tracker.level)
    }
}
