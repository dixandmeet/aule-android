package io.aule.android.core.guet

import java.time.Instant
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les six critères et leur table de poids.
 *
 * Les tests portent sur les **renversements** que les poids produisent, pas sur
 * les valeurs : ces chiffres sont un point de départ à calibrer sur le terrain, et
 * les figer dans des assertions rendrait le calibrage impossible sans réécrire la
 * suite.
 *
 * Port de `GuetScoringTests.swift`.
 */
class GuetScoringTest {

    private val reference: Instant = Instant.ofEpochSecond(1_755_792_000)

    // ------------------------------------------------------- la règle du neutre

    @Test
    fun `un critere qu on ne peut pas evaluer vaut neutre jamais zero`() {
        assertEquals(GuetScoring.NEUTRAL, GuetScoring.affinity(isFollowed = false, habit = null))
        assertEquals(GuetScoring.NEUTRAL, GuetScoring.direction(match = null))
        assertTrue(GuetScoring.NEUTRAL > 0)
    }

    /**
     * Un choix déclaré l'emporte sur une habitude devinée, et sans condition :
     * c'est la seule chose que l'utilisateur ait dite à voix haute.
     */
    @Test
    fun `une ligne suivie l emporte sur l habitude`() {
        assertEquals(1.0, GuetScoring.affinity(isFollowed = true, habit = 0.0))
    }

    // -------------------------------------------------------------------- reach

    @Test
    fun `une marge hors d atteinte vaut zero`() {
        assertEquals(0.0, GuetScoring.reach(GuetLevel.MISSED_SLACK))
        assertEquals(0.0, GuetScoring.reach(-600.0))
    }

    /**
     * ⚠️ **Le désaccord que ce test verrouille**, et qui a coûté le seul cas qui
     * compte côté iOS : la première écriture notait zéro une marge nulle —
     * l'instant exact où il faut partir — pendant que l'axe de faisabilité la
     * déclarait `TIGHT`. Le moteur ne sonnait alors **jamais** à l'heure de
     * partir. Les deux axes doivent s'accorder à chaque frontière.
     */
    @Test
    fun `reach vaut plus que zero partout ou le niveau dit atteignable`() {
        var slack = GuetLevel.MISSED_SLACK + 1
        while (slack <= 600.0) {
            val feasibility = GuetLevel.of(
                GuetTiming.of(
                    expectedAt = reference.plusMillis(((slack + 100) * 1000).toLong()),
                    walkSeconds = 100,
                    platformSeconds = 0,
                ),
                reference,
            ).feasibility
            val score = GuetScoring.reach(slack)
            if (feasibility == GuetFeasibility.MISSED) {
                assertEquals(0.0, score, "marge $slack : manquée, donc sans valeur")
            } else {
                assertTrue(score > 0, "marge $slack : $feasibility, donc pas zéro")
            }
            slack += 1.0
        }
    }

    @Test
    fun `une marge large ne se decote pas`() {
        // Le Guet **attend le bon moment** : une marge de quinze minutes ne vaut
        // pas moins, elle n'est simplement pas encore l'heure de sonner.
        assertEquals(1.0, GuetScoring.reach(60.0))
        assertEquals(1.0, GuetScoring.reach(900.0))
    }

    @Test
    fun `une marge serree vaut moins qu une marge confortable`() {
        assertTrue(GuetScoring.reach(20.0) < GuetScoring.reach(60.0))
    }

    // ------------------------------------------------------------------ silence

    /**
     * Réveiller quelqu'un pour un passage qu'il vient de refuser est le seul défaut
     * qui fasse désinstaller une veille.
     */
    @Test
    fun `un refus fait taire une alerte deja envoyee decote`() {
        assertEquals(0.0, GuetScoring.silence(PassageStatus.Declined(reference)))
        assertEquals(1.0, GuetScoring.silence(null))

        val alerted = GuetScoring.silence(PassageStatus.Alerted(reference))
        assertTrue(
            alerted > 0 && alerted < 0.5,
            "décote sans annuler : le volet peut l'afficher",
        )
    }

    // ---------------------------------------------------------------- freshness

    @Test
    fun `le theorique vaut moins que le mesure mais pas neutre par defaut`() {
        val live = GuetScoring.freshness(isRealtime = true, isFleetStale = false)
        val staleFleet = GuetScoring.freshness(isRealtime = true, isFleetStale = true)
        val theoretical = GuetScoring.freshness(isRealtime = false, isFleetStale = false)

        assertTrue(live > staleFleet)
        assertTrue(staleFleet > theoretical)
    }

    // ---------------------------------------------------------------- proximity

    @Test
    fun `deux minutes de marche valent autant qu aucune`() {
        assertEquals(1.0, GuetScoring.proximity(0))
        assertEquals(1.0, GuetScoring.proximity(120))
        assertTrue(GuetScoring.proximity(600) < GuetScoring.proximity(120))
        assertEquals(0.0, GuetScoring.proximity(1200))
        assertEquals(0.0, GuetScoring.proximity(3600))
    }

    // ------------------------------------------------------------------ la table

    /**
     * Les poids sont une table, pas des `if` éparpillés — et ils font une moyenne
     * pondérée. Une somme différente de 1 ferait dériver tous les scores sans que
     * le seuil d'alerte bouge, et le seuil cesserait de vouloir dire ce qu'il dit.
     */
    @Test
    fun `les poids couvrent tous les criteres et somment a un`() {
        assertEquals(GuetScoring.Criterion.entries.toSet(), GuetScoring.WEIGHTS.keys)
        assertTrue(abs(GuetScoring.WEIGHTS.values.sum() - 1) < 0.0001)
    }

    @Test
    fun `reach pese plus que tout silence vient ensuite`() {
        val sorted = GuetScoring.WEIGHTS.entries.sortedByDescending { it.value }.map { it.key }

        assertEquals(GuetScoring.Criterion.REACH, sorted.first())
        assertEquals(GuetScoring.Criterion.SILENCE, sorted[1])
    }

    /**
     * Un critère absent du détail est lu comme neutre, jamais comme zéro. C'est la
     * même règle que plus haut, appliquée à l'assemblage.
     */
    @Test
    fun `un critere absent du score compte neutre`() {
        val empty = GuetScore(criteria = emptyMap())

        assertTrue(abs(empty.total - GuetScoring.NEUTRAL) < 0.0001)
    }

    @Test
    fun `le seuil d alerte separe bien deux scores voisins`() {
        val worthy = GuetScore(
            GuetScoring.Criterion.entries.associateWith { 1.0 },
        )
        val unworthy = GuetScore(
            GuetScoring.Criterion.entries.associateWith { 0.0 },
        )

        assertTrue(worthy.isAlertWorthy)
        assertTrue(!unworthy.isAlertWorthy)
    }
}
