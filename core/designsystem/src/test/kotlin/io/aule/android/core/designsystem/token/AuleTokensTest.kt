package io.aule.android.core.designsystem.token

import io.aule.android.core.model.TransportMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Port de `Native/AuleTests/DesignSystemTests.swift`.
 *
 * Le brief demande un contraste important. On ne l'affirme pas : on mesure le
 * seuil AA du corps de texte (4,5:1) **sur les deux ambiances**, parce qu'un
 * jeton ajusté d'un côté se dégrade silencieusement de l'autre.
 */
class ContrastTest {

    private fun tokens(night: Boolean) = AuleTokens.of(night)

    @ParameterizedTest(name = "nuit = {0}")
    @ValueSource(booleans = [false, true])
    fun `le texte reste lisible sur sa surface`(night: Boolean) {
        val t = tokens(night)
        val surface = t.surfaceSolid

        val body = t.onSurface.contrastRatio(surface)
        assertTrue(body >= 4.5, "texte courant : $body")

        val muted = t.onSurfaceMuted.contrastRatio(surface)
        assertTrue(muted >= 4.5, "texte secondaire : $muted")
    }

    /**
     * [AuleTokens.accent] est un **aplat** ; ce qui s'écrit dessus est
     * [AuleTokens.onAccent]. Confondre les deux rend le texte illisible sans
     * qu'aucun test de mise en page ne le voie.
     */
    @ParameterizedTest(name = "nuit = {0}")
    @ValueSource(booleans = [false, true])
    fun `ce qui s ecrit sur l accent tient le contraste`(night: Boolean) {
        val t = tokens(night)
        val ratio = t.onAccent.contrastRatio(t.accent)
        assertTrue(ratio >= 4.5, "encre sur aplat : $ratio")
    }

    @ParameterizedTest(name = "nuit = {0}")
    @ValueSource(booleans = [false, true])
    fun `l accent ecrit sur la surface tient aussi`(night: Boolean) {
        val t = tokens(night)
        val ratio = t.accentOnSurface.contrastRatio(t.surfaceSolid)
        assertTrue(ratio >= 4.5, "accent sur surface : $ratio")
    }

    @Test
    fun `le vert Aule est bien celui de la carte en production`() {
        assertEquals(AuleRgba(0x0D595E), AuleBrand.teal)
        // L'identité ne suit pas l'ambiance ; l'accent de jour, lui, coïncide avec elle.
        assertEquals(AuleBrand.teal, AuleTokens.day.accent)
    }

    /**
     * Le rapport de contraste est symétrique et borné à 21:1. Si cette identité
     * casse, tous les seuils ci-dessus deviennent des affirmations creuses.
     */
    @Test
    fun `le rapport de contraste est symetrique et borne`() {
        val white = AuleRgba(0xFFFFFF)
        val black = AuleRgba(0x000000)
        assertEquals(21.0, white.contrastRatio(black), 0.01)
        assertEquals(white.contrastRatio(black), black.contrastRatio(white), 1e-9)
        assertEquals(1.0, white.contrastRatio(white), 1e-9)
    }
}

class TypeScaleTest {

    /**
     * Le rapport d'environ 1,27 est ce qui rend deux paliers distinguables
     * **sans les comparer**. C'est aussi lui qui a décidé que le palier haut
     * valait 28 et non 30 : 28/22 = 1,273 passe, 30/22 = 1,364 non.
     */
    @Test
    fun `l echelle typographique garde son rapport`() {
        val ladder = AuleRole.ladder
        ladder.zipWithNext { smaller, larger ->
            val ratio = larger.sizeSp / smaller.sizeSp
            assertTrue(
                ratio >= 1.2f && ratio <= 1.35f,
                "$smaller → $larger : rapport $ratio, hors de [1,2 ; 1,35]",
            )
        }
    }

    @Test
    fun `les chiffres qui comptent sont a chasse fixe`() {
        assertTrue(AuleRole.DATA.usesTabularFigures)
        assertTrue(AuleRole.HERO.usesTabularFigures)
        assertTrue(!AuleRole.BODY.usesTabularFigures)
    }

    @Test
    fun `l echelle couvre les cinq roles, dans l ordre croissant`() {
        assertEquals(5, AuleRole.ladder.size)
        AuleRole.ladder.zipWithNext { smaller, larger ->
            assertTrue(larger.sizeSp > smaller.sizeSp, "$smaller doit précéder $larger")
        }
    }
}

class LineColorTest {

    @Test
    fun `les formes de couleur GTFS sont toutes acceptees`() {
        assertEquals(AuleRgba(0x00A754), parseLineColor("#00a754"))
        assertEquals(AuleRgba(0x00A754), parseLineColor("00a754"))
        assertEquals(AuleRgba(0xFFFFFF), parseLineColor("#fff"))
        assertEquals(AuleRgba(0xE30613), parseLineColor("  #E30613  "))
    }

    /** Le badge porte le numéro de ligne : mieux vaut un gris qu'un badge absent. */
    @Test
    fun `une couleur illisible donne le repli, pas une disparition`() {
        assertEquals(LINE_FALLBACK_COLOR, parseLineColor(null))
        assertEquals(LINE_FALLBACK_COLOR, parseLineColor(""))
        assertEquals(LINE_FALLBACK_COLOR, parseLineColor("bleu"))
        assertEquals(LINE_FALLBACK_COLOR, parseLineColor("#12345"))
        assertEquals(LINE_FALLBACK_COLOR, parseLineColor("#ZZZZZZ"))
    }

    /**
     * Le seuil existe pour une raison très concrète : le réseau de nuit a une
     * couleur GTFS **blanche**, et sans bascule « N1 » s'écrivait en blanc sur
     * blanc.
     */
    @Test
    fun `le texte du badge bascule sur un fond clair`() {
        assertTrue(parseLineColor("#FFFFFF").perceivedLuminance > LINE_BADGE_LUMINANCE_FLIP)
        assertTrue(parseLineColor("#00a754").perceivedLuminance < LINE_BADGE_LUMINANCE_FLIP)
        assertTrue(parseLineColor("#e30613").perceivedLuminance < LINE_BADGE_LUMINANCE_FLIP)

        assertEquals(AuleRgba(0x171717), badgeInk(parseLineColor("#FFFFFF")))
        assertEquals(AuleRgba(0xFFFFFF), badgeInk(parseLineColor("#00a754")))
    }
}

class TransportModeColorTest {

    @Test
    fun `chaque mode se distingue de nuit comme de jour`() {
        for (night in listOf(false, true)) {
            val colors = TransportMode.entries.map { it.markerColor(night) }
            assertEquals(
                TransportMode.entries.size,
                colors.toSet().size,
                "deux modes partagent une couleur (nuit = $night)",
            )
        }
    }

    /**
     * Un marqueur doit se détacher du fond de carte. On ne vise pas le 4,5:1 du
     * texte — un aplat de 26 dp n'est pas un caractère — mais 3:1, le seuil AA
     * des éléments non textuels.
     */
    @Test
    fun `chaque marqueur se detache de sa surface`() {
        for (night in listOf(false, true)) {
            val surface = AuleTokens.of(night).surfaceSolid
            for (mode in TransportMode.entries) {
                val ratio = mode.markerColor(night).contrastRatio(surface)
                assertTrue(ratio >= 3.0, "$mode sur surface (nuit = $night) : $ratio")
            }
        }
    }
}
