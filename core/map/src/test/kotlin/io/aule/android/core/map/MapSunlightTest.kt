package io.aule.android.core.map

import io.aule.android.core.geo.SolarPosition
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que le soleil a le droit de faire à la carte.
 *
 * Comme pour [io.aule.android.core.map.camera.BuildingEmphasis], le contrat est
 * un **ordre**, pas une valeur : la lumière doit raser plus tôt qu'à midi, être
 * plus chaude au ras de l'horizon, plus faible la nuit. Les teintes exactes sont
 * un réglage et bougeront ; ces relations-là sont la promesse.
 */
class MapSunlightTest {

    /**
     * Le seul vrai garde-fou du lot.
     *
     * Sous l'horizon, l'angle polaire naïf dépasse 90° et la ville s'éclaire
     * par en dessous. C'est le défaut qu'on ne verrait pas en relisant le code
     * — il n'apparaît qu'entre le coucher et le lever, et il est spectaculaire.
     */
    @Test
    fun `la lumiere ne vient jamais d en dessous`() {
        var elevation = -90.0
        while (elevation <= 90.0) {
            val setting = MapSunlight.of(sunAt(elevation), MapAmbiance.LIGHT)
            assertTrue(
                setting.polarDegrees <= MapSunlight.HORIZON_POLAR,
                "à $elevation° de hauteur, l'angle polaire vaut ${setting.polarDegrees}°",
            )
            elevation += 0.5
        }
    }

    @Test
    fun `au ras de l horizon elle rase, au zenith elle tombe`() {
        val dawn = MapSunlight.of(sunAt(2.0), MapAmbiance.LIGHT)
        val noon = MapSunlight.of(sunAt(60.0), MapAmbiance.LIGHT)
        assertTrue(
            dawn.polarDegrees > noon.polarDegrees,
            "aube ${dawn.polarDegrees}° contre midi ${noon.polarDegrees}°",
        )
    }

    @Test
    fun `au ras de l horizon, elle est plus chaude qu a midi`() {
        val dawn = MapSunlight.of(sunAt(1.0), MapAmbiance.LIGHT)
        val noon = MapSunlight.of(sunAt(60.0), MapAmbiance.LIGHT)
        assertTrue(warmth(dawn) > warmth(noon), "aube ${warmth(dawn)} contre midi ${warmth(noon)}")
    }

    /**
     * Le rasant doit se **voir**, pas écraser.
     *
     * Le nuanceur n'a pas de terme de ciel : le plancher d'ambiant vaut
     * `1 − force`, donc une force de plein midi sous un soleil rasant éteint
     * tout ce qui est horizontal. Les toits d'une ville du matin viraient au
     * brun sombre, et c'est ce qu'on a constaté sur appareil avant d'écrire ce
     * test.
     */
    @Test
    fun `au ras de l horizon, la part directionnelle pese moins qu a midi`() {
        val dawn = MapSunlight.of(sunAt(3.0), MapAmbiance.LIGHT)
        val noon = MapSunlight.of(sunAt(50.0), MapAmbiance.LIGHT)
        assertTrue(dawn.intensity < noon.intensity, "${dawn.intensity} n'est pas sous ${noon.intensity}")
    }

    @Test
    fun `la nuit, elle est plus faible que le jour`() {
        val night = MapSunlight.of(sunAt(-30.0), MapAmbiance.DARK)
        val day = MapSunlight.of(sunAt(45.0), MapAmbiance.LIGHT)
        assertTrue(night.intensity < day.intensity, "${night.intensity} n'est pas sous ${day.intensity}")
    }

    /**
     * L'ambiance sombre est un choix de luminosité d'écran, pas une heure.
     *
     * À soleil rigoureusement égal, elle doit rendre moins de lumière — sinon
     * un utilisateur qui force le mode nuit à midi retrouve sa ville en feu sur
     * un fond noir.
     */
    @Test
    fun `a soleil egal, l ambiance sombre rend moins de lumiere`() {
        val sun = sunAt(45.0)
        val light = MapSunlight.of(sun, MapAmbiance.LIGHT)
        val dark = MapSunlight.of(sun, MapAmbiance.DARK)
        assertTrue(dark.intensity < light.intensity, "${dark.intensity} n'est pas sous ${light.intensity}")
    }

    /**
     * La nuit profonde doit rendre **exactement** ce que le style sombre porte
     * déjà dans son JSON.
     *
     * C'est ce qui garantit qu'allumer le soleil ne change pas la nuit : la
     * palette de nuit a été réglée à l'œil, et elle doit survivre intacte à
     * l'arrivée d'un calcul.
     */
    @Test
    fun `la nuit profonde retrouve la palette du style sombre`() {
        val setting = MapSunlight.of(sunAt(-40.0), MapAmbiance.DARK)
        assertEquals(MapSunlight.NIGHT_POLAR, setting.polarDegrees, 1e-9)
        assertEquals(MapSunlight.NIGHT_INTENSITY, setting.intensity, 1e-9)
        assertEquals(MapSunlight.NIGHT_BLUE, setting.color)
    }

    @Test
    fun `l azimut du soleil passe tel quel`() {
        val setting = MapSunlight.of(SolarPosition(azimuthDegrees = 123.4, elevationDegrees = 20.0), MapAmbiance.LIGHT)
        assertEquals(123.4, setting.azimuthDegrees, 1e-9)
    }

    /**
     * Le passage de l'horizon ne doit pas se voir.
     *
     * La lumière est réécrite toutes les minutes, et le soleil bouge d'un quart
     * de degré dans ce temps-là. Si la fonction sautait quelque part — au ras de
     * l'horizon, là où trois rampes se croisent —, la carte changerait d'un bloc
     * sous les yeux de quelqu'un qui ne fait rien.
     */
    @Test
    fun `rien ne saute en traversant l horizon`() {
        var elevation = -20.0
        var previous = MapSunlight.of(sunAt(elevation), MapAmbiance.LIGHT)
        while (elevation <= 20.0) {
            val current = MapSunlight.of(sunAt(elevation), MapAmbiance.LIGHT)
            assertTrue(
                abs(current.polarDegrees - previous.polarDegrees) < 1.0,
                "à $elevation° : l'angle polaire saute de " +
                    "${previous.polarDegrees}° à ${current.polarDegrees}°",
            )
            assertTrue(
                abs(current.intensity - previous.intensity) < 0.01,
                "à $elevation° : la force saute de ${previous.intensity} à ${current.intensity}",
            )
            previous = current
            elevation += 0.05
        }
    }

    private fun sunAt(elevationDegrees: Double) =
        SolarPosition(azimuthDegrees = 180.0, elevationDegrees = elevationDegrees)

    /** Combien la couleur penche vers l'or plutôt que vers le bleu. */
    private fun warmth(setting: SunlightSetting): Double = setting.color.red - setting.color.blue
}
