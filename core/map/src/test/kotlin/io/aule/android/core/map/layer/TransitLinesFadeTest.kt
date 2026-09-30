package io.aule.android.core.map.layer

import com.google.gson.JsonParser
import kotlin.test.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Le fondu du réseau : la rampe de zoom reste au premier niveau, seules ses sorties s'atténuent.
 *
 * C'est la règle que MapLibre impose (`["zoom"]` n'entre que dans un `interpolate` ou un `step`
 * de premier niveau) et qu'un `product` autour de la rampe violait — sans erreur visible sur
 * Android, en plantage sur iOS. Voir [TransitLinesLayer.fadedRamp].
 */
@DisplayName("Le fondu du réseau atténue les sorties de la rampe, jamais la rampe")
class TransitLinesFadeTest {

    private fun json(text: String) = JsonParser.parseString(text).asJsonArray

    @Test
    @DisplayName("Un interpolate garde son entrée zoom au premier niveau ; chaque sortie est multipliée")
    fun interpolate() {
        val ramp = json("""["interpolate", ["linear"], ["zoom"], 14, 0, 15, 0.55, 16, 0.7]""")
        val faded = TransitLinesLayer.fadedRamp(ramp, 0.5f)
        assertEquals("interpolate", faded[0].asString)
        assertEquals(json("""["zoom"]"""), faded[2])
        assertEquals(listOf(14f, 0f, 15f, 0.275f, 16f, 0.35f), (3 until faded.size()).map { faded[it].asFloat })
        // L'original n'est pas touché : il sert encore quand le fondu revient à 1.
        assertEquals(0.55f, ramp[6].asFloat)
    }

    @Test
    @DisplayName("Un step atténue sa valeur de départ et ses paliers, pas ses seuils")
    fun step() {
        val faded = TransitLinesLayer.fadedRamp(json("""["step", ["zoom"], 0.2, 14, 0.8]"""), 0f)
        assertEquals(listOf(0f, 14f, 0f), (2 until faded.size()).map { faded[it].asFloat })
    }

    @Test
    @DisplayName("Ce qui n'est pas une rampe est rendu tel quel")
    fun autre() {
        val constante = json("""["literal", 0.8]""")
        assertEquals(constante, TransitLinesLayer.fadedRamp(constante, 0.5f))
    }
}
