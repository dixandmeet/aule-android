package io.aule.android.core.designsystem.token

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Les échelles de mesure se gardent comme l'échelle typographique : par un
 * test, pas par la relecture. Une valeur ajoutée au milieu d'une échelle ne se
 * voit pas en revue — elle se voit à l'écran, trois écrans plus loin.
 */
class ElevationScaleTest {

    @ParameterizedTest(name = "nuit = {0}")
    @ValueSource(booleans = [false, true])
    fun `l echelle d elevation est strictement croissante`(night: Boolean) {
        AuleElevation.ladder.zipWithNext { lower, higher ->
            assertTrue(
                higher.height(night) > lower.height(night),
                "$lower doit rester sous $higher (nuit = $night)",
            )
        }
    }

    /**
     * Sur fond sombre une ombre noire disparaît : c'est l'étalement qui rend le
     * décollement. Si la nuit repassait sous le jour, les surfaces
     * s'aplatiraient sans qu'aucun test de mise en page ne le voie.
     */
    @Test
    fun `la nuit ombre au moins autant que le jour`() {
        AuleElevation.entries.forEach { level ->
            assertTrue(
                level.height(night = true) >= level.height(night = false),
                "$level : ${level.height(true)} de nuit contre ${level.height(false)} de jour",
            )
        }
    }

    @Test
    fun `le niveau posé ne porte aucune ombre`() {
        assertEquals(0f, AuleElevation.NONE.height(night = false).value)
        assertEquals(0f, AuleElevation.NONE.height(night = true).value)
    }

    @Test
    fun `l echelle couvre les cinq niveaux`() {
        assertEquals(AuleElevation.entries.size, AuleElevation.ladder.size)
        assertEquals(AuleElevation.entries.toSet(), AuleElevation.ladder.toSet())
    }
}

class ControlMetricsTest {

    /**
     * Le plancher tactile est un plancher : un contrôle plus petit est
     * atteignable en théorie et manqué en pratique.
     */
    @Test
    fun `aucun controle ne passe sous le plancher tactile`() {
        listOf(AuleControl.height, AuleControl.field, AuleControl.avatar).forEach { size ->
            assertTrue(size >= AuleTouch.minimum, "$size est sous ${AuleTouch.minimum}")
        }
    }

    /** Le champ loge un libellé au-dessus de la saisie ; le bouton, une ligne. */
    @Test
    fun `le champ est plus haut que le bouton`() {
        assertTrue(AuleControl.field > AuleControl.height)
    }

    /**
     * La grille de 24 est celle du style graphique commun aux trois
     * plateformes. La changer ici désaligne Android des deux autres.
     */
    @Test
    fun `la grille d icone tient la valeur commune aux plateformes`() {
        assertEquals(24f, AuleControl.icon.value)
        assertEquals(1.75f, AuleStroke.glyph.value)
    }

    @Test
    fun `le trait accentue se voit plus que le filet`() {
        assertTrue(AuleStroke.emphasis > AuleStroke.hairline)
    }
}

class ChromeMetricsTest {

    /**
     * Le chrome est plus serré que les contrôles d'une page — c'est sa raison
     * d'être. Si un jour les deux échelles se rejoignaient, il ne resterait
     * qu'une échelle en double, et le prochain arbitrage la ferait diverger
     * dans l'autre sens sans que personne ne le voie.
     */
    @Test
    fun `le chrome reste sous les controles d une page`() {
        assertTrue(AuleChrome.bar < AuleControl.height)
        assertTrue(AuleChrome.pillGlyph < AuleControl.icon)
    }

    /**
     * Serré, mais pas au point de se manquer : la barre du chrome **est** le
     * plancher tactile. C'est la seule valeur qui gagne des points sans en
     * coûter au doigt, et la descendre d'un cran de plus se paierait sur chaque
     * entrée du menu.
     */
    @Test
    fun `la barre du chrome tient le plancher tactile`() {
        assertEquals(AuleTouch.minimum, AuleChrome.bar)
    }

    /**
     * Sous la barre : un bouton de vue, puis une pastille. L'ordre porte le
     * rang — ce qui agit est plus gros que ce qui commande la vue, qui est plus
     * gros que ce qui se contente d'informer.
     */
    @Test
    fun `l echelle du chrome decroit par rang`() {
        assertTrue(AuleChrome.bar > AuleChrome.button)
        assertTrue(AuleChrome.button > AuleChrome.pill)
        assertTrue(AuleChrome.pill > AuleChrome.pillGlyph)
    }
}

class SpacingScaleTest {

    @Test
    fun `les espacements croissent sur une base de 4`() {
        val ladder = listOf(
            AuleSpacing.xs,
            AuleSpacing.sm,
            AuleSpacing.md,
            AuleSpacing.lg,
            AuleSpacing.xl,
            AuleSpacing.xxl,
        )
        ladder.zipWithNext { smaller, larger ->
            assertTrue(larger > smaller, "$smaller doit précéder $larger")
        }
        ladder.forEach { step ->
            assertEquals(0f, step.value % 4f, "$step n'est pas un multiple de 4")
        }
    }

    /**
     * Les cinq crans alimentent les cinq formes de Material dans l'ordre. Deux
     * crans égaux, et deux familles de composants que Material distingue —
     * la carte et le dialogue — se confondent à l'écran.
     */
    @Test
    fun `les rayons croissent, la pilule reste hors echelle`() {
        listOf(AuleRadius.sm, AuleRadius.md, AuleRadius.lg, AuleRadius.xl, AuleRadius.xxl)
            .zipWithNext { smaller, larger ->
                assertTrue(larger > smaller, "$smaller doit précéder $larger")
            }
        assertTrue(AuleRadius.pill > AuleRadius.xxl)
    }

    /**
     * Une opacité nommée hors de ]0 ; 1[ ne teinte pas : elle efface ou elle
     * masque.
     */
    @Test
    fun `les opacites nommees restent des opacites`() {
        listOf(
            AuleAlpha.DISABLED,
            AuleAlpha.TINT,
            AuleAlpha.WASH,
            AuleAlpha.OUTLINE,
            AuleAlpha.VEIL,
            AuleAlpha.SUBDUED,
            AuleAlpha.GLOW,
            AuleAlpha.GLOW_STRONG,
            AuleAlpha.HALO,
            AuleAlpha.HALO_STRONG,
            AuleAlpha.HALO_SOFT,
        ).forEach { alpha ->
            assertTrue(alpha > 0f && alpha < 1f, "$alpha n'est pas une opacité utile")
        }
    }
}
