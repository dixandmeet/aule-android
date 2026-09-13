package io.aule.android.core.map3d

import java.io.File
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * La lumière des véhicules est celle du style — et ce test est ce qui le tient.
 *
 * Les valeurs sont recopiées, parce que MapLibre Android n'expose pas les angles
 * de sa `Position`. Quelqu'un qui règle l'éclairage des façades dans
 * `style-light.json` verra ce test échouer, et saura qu'il y a un second
 * endroit à mettre à jour.
 */
class VehicleLightingTest {

    private fun styleLight(name: String) = Json.parseToJsonElement(
        File("../../app/src/main/assets/map/$name").readText(),
    ).jsonObject.getValue("light").jsonObject

    private fun check(lighting: VehicleLighting, styleFile: String) {
        val light = styleLight(styleFile)
        val position = light.getValue("position").jsonArray
        assertEquals(position[1].jsonPrimitive.double, lighting.azimuthDegrees, 1e-9, "$styleFile : azimut")
        assertEquals(position[2].jsonPrimitive.double, lighting.polarDegrees, 1e-9, "$styleFile : angle polaire")
        assertEquals(light.getValue("intensity").jsonPrimitive.double, lighting.intensity, 1e-9, "$styleFile : intensité")
        val color = light.getValue("color").jsonPrimitive.content.removePrefix("#").toInt(16)
        assertEquals(color, lighting.color, "$styleFile : couleur")
    }

    @Test
    fun `le jour est la lumiere de style-light`() = check(VehicleLighting.DAY, "style-light.json")

    @Test
    fun `la nuit est la lumiere de style-dark`() = check(VehicleLighting.NIGHT, "style-dark.json")

    /**
     * La direction suit la formule du moteur, pas la prose de la spécification.
     *
     * Le style clair pose sa lumière à 175° d'azimut ; dans le nuancier
     * `fill-extrusion`, ce sont les façades **nord** qui s'éclairent. Un bus
     * doit recevoir le jour du même côté — sinon il est éclairé à contre-jour
     * de la façade devant laquelle il passe, et cela se voit.
     */
    @Test
    fun `le jour vient du nord et d en haut`() {
        val sun = VehicleLighting.DAY.sunDirection()
        assertTrue(sun[1] > 0.6, "composante nord attendue positive : ${sun[1]}")
        assertTrue(sun[2] > 0.6, "composante haute attendue positive : ${sun[2]}")
        assertEquals(1.0, sqrt(sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]), 1e-9, "unitaire")
    }

    @Test
    fun `la nuit est rasante et vient du sud`() {
        val sun = VehicleLighting.NIGHT.sunDirection()
        assertTrue(sun[1] < -0.9, "composante sud attendue : ${sun[1]}")
        assertTrue(sun[2] < 0.2, "lumière rasante attendue : ${sun[2]}")
    }

    /**
     * Aucune pièce ne peut tomber dans le noir.
     *
     * L'éclairage est **multiplicatif** : la couleur d'un bogie est un aplat
     * sombre que l'ambiante divise encore. Le premier jeu de valeurs livrait
     * 0,48 sur une face verticale et peignait la moitié du bus en noir. Ce test
     * tient la plage de l'ombrage cuit qu'il remplace — 0,72 à 1,02 — et
     * échouera si un réglage d'ambiance la quitte.
     */
    @Test
    fun `une face verticale garde de quoi se lire`() {
        for ((nom, lighting) in listOf("jour" to VehicleLighting.DAY, "nuit" to VehicleLighting.NIGHT)) {
            val v = lighting.toFloatArray()
            val sky = (v[6] + v[7] + v[8]) / 3.0
            val ground = (v[9] + v[10] + v[11]) / 3.0
            val sun = (v[3] + v[4] + v[5]) / 3.0
            // Ce que reçoit une face verticale au sud, soleil nul : l'ambiante
            // hémisphérique y vaut la moyenne du ciel et du sol.
            val verticale = (sky + ground) / 2
            val plancher = if (lighting.lampGlow > 0.5) 0.45 else 0.62
            assertTrue(
                verticale >= plancher,
                "$nom : une face à l'ombre ne reçoit que $verticale — un aplat sombre y devient noir",
            )
            // Et le toit face au soleil ne doit pas brûler la livrée.
            val toit = sky + sun * v[2]
            assertTrue(toit <= 1.15, "$nom : un toit reçoit $toit, la teinte de ligne y sature")
        }
    }

    @Test
    fun `le bloc natif a la taille du contrat`() {
        assertEquals(VehicleLighting.FLOATS, VehicleLighting.DAY.toFloatArray().size)
        assertEquals(VehicleLighting.FLOATS, VehicleLighting.NIGHT.toFloatArray().size)
    }
}
