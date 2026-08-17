package io.aule.android.core.designsystem.token

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * La garde des rôles de couleur Material 3.
 *
 * Un rôle Material va toujours par paire : un fond et l'encre qu'on y écrit.
 * La paire se décide ici, une fois, et se retrouve ensuite sur des dizaines
 * d'écrans par le seul jeu du thème — donc une paire illisible l'est partout à
 * la fois, et aucun test de mise en page ne la voit. Seul le contraste la voit.
 *
 * Les seuils sont ceux du WCAG : 4,5:1 pour du texte, 3:1 pour ce qui n'est
 * qu'une bordure ou une séparation.
 */
class AulePaletteTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("textPairs")
    fun `une encre reste lisible sur son fond`(pair: Pair) {
        val ratio = pair.ink.contrastRatio(pair.background)
        assertTrue(
            ratio >= TEXT_MINIMUM,
            "${pair.name} : ${"%.2f".format(ratio)}:1 pour $TEXT_MINIMUM:1 exigés",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("outlinePairs")
    fun `un contour se distingue de son fond`(pair: Pair) {
        val ratio = pair.ink.contrastRatio(pair.background)
        assertTrue(
            ratio >= OUTLINE_MINIMUM,
            "${pair.name} : ${"%.2f".format(ratio)}:1 pour $OUTLINE_MINIMUM:1 exigés",
        )
    }

    /**
     * Le vert Aule est l'identité du produit. S'il dérive ici, il dérive
     * partout : la rampe primaire entière en descend.
     */
    @Test
    fun `la rampe primaire est ancree sur le vert Aule`() {
        assertEquals(AuleBrand.teal, AulePalette.Teal.T30)
    }

    /** Le ton dit la luminosité : une rampe qui ne monte pas n'est pas une rampe. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("ramps")
    fun `une rampe s eclaircit du ton bas vers le ton haut`(ramp: Ramp) {
        ramp.tones.zipWithNext { darker, lighter ->
            assertTrue(
                lighter.relativeLuminance > darker.relativeLuminance,
                "${ramp.name} : un ton s'assombrit en montant",
            )
        }
    }

    data class Pair(val name: String, val ink: AuleRgba, val background: AuleRgba) {
        override fun toString(): String = name
    }

    data class Ramp(val name: String, val tones: List<AuleRgba>) {
        override fun toString(): String = name
    }

    private companion object {
        const val TEXT_MINIMUM = 4.5
        const val OUTLINE_MINIMUM = 3.0

        @JvmStatic
        fun textPairs(): List<Pair> = listOf(
            // Le jour.
            Pair("onPrimary sur primary", AulePalette.Teal.T100, AulePalette.Teal.T30),
            Pair("onPrimaryContainer sur primaryContainer", AulePalette.Teal.T10, AulePalette.Teal.T90),
            Pair("onSecondary sur secondary", AulePalette.Teal.T100, AulePalette.Green.T40),
            Pair(
                "onSecondaryContainer sur secondaryContainer",
                AulePalette.Hud.realtimeOnContainerDay,
                AulePalette.Hud.realtimeContainerDay,
            ),
            Pair("onTertiary sur tertiary", AulePalette.Teal.T100, AulePalette.Amber.T40),
            Pair(
                "onTertiaryContainer sur tertiaryContainer",
                AulePalette.Hud.delayOnContainerDay,
                AulePalette.Hud.delayContainerDay,
            ),

            // La nuit.
            Pair("onPrimary sur primary, nuit", AulePalette.Neutral.T8, AulePalette.Hud.nightOnSurface),
            Pair(
                "onPrimaryContainer sur primaryContainer, nuit",
                AulePalette.Hud.nightOnFill,
                AulePalette.Hud.nightFill,
            ),
            Pair("onSecondary sur secondary, nuit", AulePalette.Hud.realtimeOnNight, AulePalette.Hud.realtimeNight),
            Pair(
                "onSecondaryContainer sur secondaryContainer, nuit",
                AulePalette.Hud.realtimeOnContainerNight,
                AulePalette.Hud.realtimeContainerNight,
            ),
            Pair("onTertiary sur tertiary, nuit", AulePalette.Hud.delayOnNight, AulePalette.Hud.delayNight),
            Pair(
                "onTertiaryContainer sur tertiaryContainer, nuit",
                AulePalette.Hud.delayOnContainerNight,
                AulePalette.Hud.delayContainerNight,
            ),
            Pair("onError sur error, nuit", AulePalette.Red.T10, AulePalette.Hud.nightError),
            Pair("onErrorContainer sur errorContainer, nuit", AulePalette.Red.T90, AulePalette.Red.T30),
            Pair("onSurface sur surface, nuit", AulePalette.Neutral.inkNight, AulePalette.Neutral.T8),
            Pair(
                "onSurfaceVariant sur surface, nuit",
                AulePalette.Neutral.inkMutedNight,
                AulePalette.Neutral.T8,
            ),
            Pair("primary sur surface, nuit", AulePalette.Hud.nightOnSurface, AulePalette.Neutral.T8),
        )

        @JvmStatic
        fun outlinePairs(): List<Pair> = listOf(
            Pair("outline sur surface", AulePalette.Neutral.T50, AulePalette.Neutral.T100),
            Pair("outline sur surface, nuit", AulePalette.Neutral.T60, AulePalette.Neutral.T8),
        )

        @JvmStatic
        fun ramps(): List<Ramp> = listOf(
            Ramp(
                "primaire",
                listOf(
                    AulePalette.Teal.T10,
                    AulePalette.Teal.T20,
                    AulePalette.Teal.T30,
                    AulePalette.Teal.T40,
                    AulePalette.Teal.T50,
                    AulePalette.Teal.T60,
                    AulePalette.Teal.T70,
                    AulePalette.Teal.T80,
                    AulePalette.Teal.T90,
                    AulePalette.Teal.T95,
                ),
            ),
            Ramp(
                "secondaire",
                listOf(
                    AulePalette.Green.T10,
                    AulePalette.Green.T20,
                    AulePalette.Green.T30,
                    AulePalette.Green.T40,
                    AulePalette.Green.T50,
                    AulePalette.Green.T60,
                    AulePalette.Green.T70,
                    AulePalette.Green.T80,
                    AulePalette.Green.T90,
                    AulePalette.Green.T95,
                ),
            ),
            Ramp(
                "tertiaire",
                listOf(
                    AulePalette.Amber.T10,
                    AulePalette.Amber.T20,
                    AulePalette.Amber.T30,
                    AulePalette.Amber.T40,
                    AulePalette.Amber.T50,
                    AulePalette.Amber.T60,
                    AulePalette.Amber.T70,
                    AulePalette.Amber.T80,
                    AulePalette.Amber.T90,
                    AulePalette.Amber.T95,
                ),
            ),
            Ramp(
                "erreur",
                listOf(
                    AulePalette.Red.T10,
                    AulePalette.Red.T20,
                    AulePalette.Red.T30,
                    AulePalette.Red.T40,
                    AulePalette.Red.T50,
                    AulePalette.Red.T60,
                    AulePalette.Red.T70,
                    AulePalette.Red.T80,
                    AulePalette.Red.T90,
                    AulePalette.Red.T95,
                ),
            ),
        )
    }
}
