package io.aule.android.core.guet

import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les habitudes : des compteurs amortis, **jamais une trace**.
 *
 * Aucune coordonnée, aucun horodatage de trajet. Ce qui se vérifie ici, c'est que
 * l'absence d'historique répond `null` — et non zéro : un critère qu'on ne peut
 * pas évaluer doit peser neutre, jamais pénaliser en silence quelqu'un dont la
 * veille vient d'être allumée.
 *
 * Port de la partie « habitudes » de `GuetScoringTests.swift`.
 */
class GuetHabitsTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val reference: Instant = Instant.ofEpochSecond(1_755_792_000)
    private val day = 24 * 3600L

    @Test
    fun `sans historique l affinite ne repond pas`() {
        val habits = GuetHabits()

        // `null`, pas zéro : c'est ce qui alimente la règle du neutre en amont.
        assertNull(habits.affinity("1", "Commerce", reference, utc))
    }

    @Test
    fun `la ligne la plus prise atteint le sommet`() {
        var habits = GuetHabits()
        repeat(5) { habits = habits.record("1", "Commerce", reference, utc) }
        habits = habits.record("2", "Commerce", reference, utc)

        assertEquals(1.0, habits.affinity("1", "Commerce", reference, utc))
        val other = assertNotNull(habits.affinity("2", "Commerce", reference, utc))
        assertTrue(other > 0 && other < 1)
    }

    /**
     * Une ligne prise chaque jour pèse plus que celle qu'on a prise deux fois en
     * mars. La décote est **temporelle, jamais binaire**.
     *
     * ⚠️ **Quatre semaines, et non « trente jours ».** La bande horaire *et* le
     * caractère ouvré font partie de la clé : un décalage qui n'est pas un
     * multiple de sept jours change de créneau, et l'ancienne habitude ne se
     * compare alors plus à rien — elle vaut zéro parce qu'elle décrit un autre
     * moment de la semaine, pas parce qu'elle s'est amortie. Le test dirait vrai
     * pour la mauvaise raison. Voir `la bande horaire fait partie de l identite`.
     */
    @Test
    fun `une habitude ancienne pese moins qu une recente`() {
        var habits = GuetHabits()
        habits = habits.record("1", "Commerce", reference.minusSeconds(28 * day), utc)
        habits = habits.record("2", "Commerce", reference, utc)

        val old = assertNotNull(habits.affinity("1", "Commerce", reference, utc))
        val fresh = assertNotNull(habits.affinity("2", "Commerce", reference, utc))
        assertTrue(old < fresh, "quatre semaines plus tard, l'ancienne pèse moins")
    }

    @Test
    fun `la decote suit bien la demi-vie`() {
        // Quatre semaines, même jour de la semaine, même heure : le créneau est
        // identique, et seul l'amortissement les sépare. 2^(−28/30) ≈ 0,52.
        var habits = GuetHabits().record("1", "Commerce", reference.minusSeconds(28 * day), utc)
        habits = habits.record("2", "Commerce", reference, utc)

        val decayed = assertNotNull(habits.affinity("1", "Commerce", reference, utc))
        assertTrue(decayed in 0.48..0.56, "attendu ≈ 0,52, obtenu $decayed")
    }

    /**
     * **Ce test documente ce que la clé contient**, et il existe parce que deux
     * autres passaient pour la mauvaise raison sans lui.
     *
     * Une habitude décrit « cette ligne, à ce lieu, **à ce moment de la
     * semaine** ». Chercher la même ligne un samedi ne retrouve pas l'habitude du
     * mardi : ce n'est pas un défaut d'amortissement, c'est un autre créneau. Le
     * résultat est zéro, et non `null` — il y a bien un historique, il ne parle
     * simplement pas de ce moment-ci.
     */
    @Test
    fun `la bande horaire fait partie de l identite`() {
        val habits = GuetHabits().record("1", "Commerce", reference, utc)

        // Trois heures plus tard : autre bande, donc rien.
        assertEquals(0.0, habits.affinity("1", "Commerce", reference.plusSeconds(3 * 3600), utc))
        // Un jour plus tard, même heure : même bande, mais le jour a changé —
        // le poids est là si le caractère ouvré n'a pas basculé.
        val nextWeek = assertNotNull(habits.affinity("1", "Commerce", reference.plusSeconds(7 * day), utc))
        assertTrue(nextWeek > 0, "une semaine plus tard, c'est le même créneau")
    }

    /**
     * L'unité de comptage est **volontairement grossière**. Deux heures d'écart
     * changent de bande ; vingt minutes, non.
     */
    @Test
    fun `la bande horaire regroupe deux heures`() {
        val morning = GuetHabits.Slot.make("1", "Commerce", reference, utc)
        val twentyMinutesLater = GuetHabits.Slot.make("1", "Commerce", reference.plusSeconds(1200), utc)
        val threeHoursLater = GuetHabits.Slot.make("1", "Commerce", reference.plusSeconds(3 * 3600), utc)

        assertEquals(morning, twentyMinutesLater)
        assertTrue(morning != threeHoursLater)
    }

    @Test
    fun `le fuseau est injecte et change la bande`() {
        // Une habitude calculée sur le fuseau de la machine de test n'aurait pas
        // les mêmes bandes que sur celle de l'utilisateur, et le défaut ne se
        // verrait qu'en voyage.
        val utcSlot = GuetHabits.Slot.make("1", "Commerce", reference, utc)
        val tokyo = GuetHabits.Slot.make("1", "Commerce", reference, ZoneId.of("Asia/Tokyo"))

        assertTrue(utcSlot.band != tokyo.band)
    }

    @Test
    fun `le registre d habitudes est borne`() {
        var habits = GuetHabits()
        repeat(GuetHabits.CAPACITY + 20) { index ->
            habits = habits.record("L$index", "Commerce", reference.plusSeconds(index.toLong()), utc)
        }

        assertEquals(GuetHabits.CAPACITY, habits.count)
    }

    @Test
    fun `l elagage oublie ce qui ne pese plus`() {
        var habits = GuetHabits().record("1", "Commerce", reference, utc)
        // Dix demi-vies plus tard, le poids vaut moins d'un millième.
        val later = reference.plusSeconds((GuetHabits.HALF_LIFE * 10).toLong())

        habits = habits.prune(at = later)

        assertEquals(0, habits.count)
    }

    // ------------------------------------------------------- l'allure de marche

    @Test
    fun `sans marche mesuree l allure ne repond pas`() {
        // `null` veut dire « on ne sait pas », jamais « vitesse normale ».
        assertNull(GuetHabits().paceFactor)
    }

    @Test
    fun `la premiere marche s adopte telle quelle`() {
        val habits = GuetHabits().recordWalk(predictedSeconds = 100, actualSeconds = 120)

        assertEquals(1.2, habits.paceFactor)
    }

    /** Trois marches suffisent à corriger, une seule ne suffit pas à emporter. */
    @Test
    fun `une marche suivante lisse au lieu de remplacer`() {
        var habits = GuetHabits().recordWalk(100, 100)
        habits = habits.recordWalk(100, 160)

        val pace = assertNotNull(habits.paceFactor)
        assertTrue(pace > 1.0 && pace < 1.6, "lissé, pas remplacé — obtenu $pace")
    }

    /**
     * Un rapport aberrant vient d'un détour ou d'un arrêt en chemin, pas d'une
     * vitesse de marche. Le laisser entrer installerait l'erreur dans une moyenne
     * qu'aucun écran ne montre.
     */
    @Test
    fun `un rapport aberrant est refuse`() {
        val habits = GuetHabits()

        assertNull(habits.recordWalk(100, 1000).paceFactor)
        assertNull(habits.recordWalk(100, 10).paceFactor)
        assertNull(habits.recordWalk(0, 100).paceFactor)
        assertNull(habits.recordWalk(100, 0).paceFactor)
    }
}
