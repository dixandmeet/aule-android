package io.aule.android.core.designsystem.token

import androidx.compose.ui.text.font.FontWeight
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
        val minimum = if (night) 4.5 else 8.0
        assertTrue(ratio >= minimum, "accent sur surface : $ratio pour $minimum:1 exigés")
    }

    /**
     * Le brief fixe `onSurfaceMuted` de nuit au-dessus de 10:1. De jour, 4,5:1
     * reste le plancher AA du corps de texte.
     */
    @ParameterizedTest(name = "nuit = {0}")
    @ValueSource(booleans = [false, true])
    fun `le texte secondaire depasse le seuil de son ambiance`(night: Boolean) {
        val t = tokens(night)
        val ratio = t.onSurfaceMuted.contrastRatio(t.surfaceSolid)
        val minimum = if (night) 10.0 else 4.5
        assertTrue(ratio >= minimum, "texte secondaire : $ratio pour $minimum:1 exigés")
    }

    @ParameterizedTest(name = "nuit = {0}")
    @ValueSource(booleans = [false, true])
    fun `un role metier reste lisible dans son conteneur`(night: Boolean) {
        val t = tokens(night)
        for (role in listOf(t.realtime, t.delay)) {
            val ratio = role.onContainer.contrastRatio(role.container)
            assertTrue(ratio >= 4.5, "onContainer : $ratio")
        }
    }

    @Test
    fun `le vert Aule est bien celui de la carte en production`() {
        assertEquals(AuleRgba(0x0D595E), AuleBrand.teal)
        // L'identité ne suit pas l'ambiance ; l'accent de jour, lui, coïncide avec elle.
        assertEquals(AuleBrand.teal, AuleTokens.day.accent)
    }

    @Test
    fun `les jetons HUD de jour sont ceux de la charte`() {
        val t = AuleTokens.day
        assertEquals(AuleRgba(0x0D595E), t.accent)
        assertEquals(AuleRgba(0xFFFFFF), t.onAccent)
        assertEquals(AuleRgba(0x0D595E), t.accentOnSurface)
        assertEquals(AuleRgba(0x19B37B), t.realtime.color)
        assertEquals(AuleRgba(0xFFFFFF), t.realtime.onColor)
        assertEquals(AuleRgba(0xD1F4E6), t.realtime.container)
        assertEquals(AuleRgba(0x005132), t.realtime.onContainer)
        assertEquals(AuleRgba(0xE8A13C), t.delay.color)
        assertEquals(AuleRgba(0xFFFFFF), t.delay.onColor)
        assertEquals(AuleRgba(0xFFE3C2), t.delay.container)
        assertEquals(AuleRgba(0x593600), t.delay.onContainer)
        assertEquals(AuleRgba(0xD64545), t.alert)
        assertEquals(AuleRgba(0xFFFFFF), t.onAlert)
        assertEquals(AuleRgba(0xFFFFFF, alpha = 0.90), t.surface)
        assertEquals(AuleRgba(0xFFFFFF), t.surfaceSolid)
        assertEquals(AuleRgba(0x171717, alpha = 0.08), t.hairline)
        assertEquals(AuleRgba(0x171717), t.onSurface)
        assertEquals(AuleRgba(0x4A4A4A), t.onSurfaceMuted)
    }

    @Test
    fun `les jetons HUD de nuit sont ceux de la charte`() {
        val t = AuleTokens.night
        assertEquals(AuleRgba(0x1A5C47), t.accent)
        assertEquals(AuleRgba(0xF1F6F3), t.onAccent)
        assertEquals(AuleRgba(0x8AC79B), t.accentOnSurface)
        assertEquals(AuleRgba(0x41C895), t.realtime.color)
        assertEquals(AuleRgba(0x003822), t.realtime.onColor)
        assertEquals(AuleRgba(0x005234), t.realtime.container)
        assertEquals(AuleRgba(0x67E5B0), t.realtime.onContainer)
        assertEquals(AuleRgba(0xF0B45C), t.delay.color)
        assertEquals(AuleRgba(0x432C00), t.delay.onColor)
        assertEquals(AuleRgba(0x5E4000), t.delay.container)
        assertEquals(AuleRgba(0xFFDCB3), t.delay.onContainer)
        assertEquals(AuleRgba(0xE86060), t.alert)
        assertEquals(AuleRgba(0x601410), t.onAlert)
        assertEquals(AuleRgba(0x0D1512, alpha = 0.95), t.surface)
        assertEquals(AuleRgba(0x0D1512), t.surfaceSolid)
        assertEquals(AuleRgba(0xFFFFFF, alpha = 0.20), t.hairline)
        assertEquals(AuleRgba(0xF3F5F7), t.onSurface)
        assertEquals(AuleRgba(0xBFC7C3), t.onSurfaceMuted)
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
     * Les cinq rôles Aule ne sont que des noms. Leurs chiffres sont ceux des
     * jetons Material 3 auxquels ils s'ancrent : un 18 à la place du 16 de
     * `titleMedium`, et l'écran entier sort de la charte.
     */
    @Test
    fun `les roles Aule suivent les jetons Material 3 auxquels ils s ancrent`() {
        assertRole(AuleRole.KICKER, size = 11f, lineHeight = 16f, tracking = 0.5f, weight = FontWeight.Medium)
        assertRole(AuleRole.BODY, size = 14f, lineHeight = 20f, tracking = 0.2f, weight = FontWeight.Normal)
        assertRole(AuleRole.TITLE, size = 16f, lineHeight = 24f, tracking = 0.2f, weight = FontWeight.Medium)
        assertRole(AuleRole.DATA, size = 22f, lineHeight = 28f, tracking = 0f, weight = FontWeight.Normal)
        assertRole(AuleRole.HERO, size = 28f, lineHeight = 36f, tracking = 0f, weight = FontWeight.Normal)
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

    private fun assertRole(
        role: AuleRole,
        size: Float,
        lineHeight: Float,
        tracking: Float,
        weight: FontWeight,
    ) {
        assertEquals(size, role.sizeSp, "$role.sizeSp")
        assertEquals(lineHeight, role.lineHeightSp, "$role.lineHeightSp")
        assertEquals(tracking, role.trackingSp, "$role.trackingSp")
        assertEquals(weight, role.weight, "$role.weight")
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
