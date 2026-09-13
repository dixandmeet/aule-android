package io.aule.android.core.map3d

import kotlin.math.cos
import kotlin.math.sin

/**
 * La lumière qui éclaire les véhicules : **celle du style**, et rien d'autre.
 *
 * Le nuancier natif éclaire les modèles (ADR-017). Pour qu'un bus et la façade
 * devant laquelle il passe reçoivent le jour du même côté, la direction, la
 * couleur et l'intensité sont celles du `light` de `style-light.json` et
 * `style-dark.json`, qui ombre les bâtiments en `fill-extrusion`.
 *
 * ⚠️ **Ces valeurs sont recopiées, pas lues.** `Light.getPosition()` de MapLibre
 * Android rend une `Position` dont les trois angles sont privés et sans
 * accesseur — la seule voie serait la réflexion ou `toString()`. Un test
 * relit les deux fichiers de style et échoue à la première divergence :
 * `VehicleLightingTest`.
 *
 * Ce que le style ne dit pas — l'ambiante hémisphérique, la force de l'ombre de
 * contact, l'allumage des feux — est fixé ici, par ambiance.
 */
data class VehicleLighting(
    /** L'azimut de la lumière, en degrés, tel que le style l'écrit. */
    val azimuthDegrees: Double,
    /** L'angle polaire depuis le zénith, en degrés, tel que le style l'écrit. */
    val polarDegrees: Double,
    /** La couleur du style, `0xRRGGBB`. */
    val color: Int,
    /** L'intensité du style. */
    val intensity: Double,
    /** Ce que reçoit une face tournée vers le ciel, hors soleil. */
    val skyAmbient: Int,
    /** Ce que reçoit une face tournée vers la chaussée. */
    val groundAmbient: Int,
    /** 0 le jour, 1 la nuit : les feux s'allument. */
    val lampGlow: Double,
    /** L'opacité de l'ombre de contact sous un véhicule opaque. */
    val shadowStrength: Double,
) {

    /**
     * Le vecteur unitaire **vers** la lumière, dans le repère de scène — est,
     * nord, haut.
     *
     * C'est la formule du moteur, pas la prose de la spécification. Celle-ci
     * dit que l'azimut « indique la direction d'où vient la lumière, 0° au nord »
     * ; le nuancier `fill-extrusion`, lui, calcule
     * `x = cos(a + 90°) · sin(p)`, `y = sin(a + 90°) · sin(p)` dans le repère des
     * tuiles, où `y` croît vers le **sud**, et prend le produit scalaire avec la
     * normale. À `a = 0`, ce sont donc les faces tournées vers le sud qui
     * s'éclairent : la lumière vient du sud. Le style clair, à 175°, éclaire par
     * le nord — c'est ce que montrent ses façades, et ce que doit montrer un bus.
     */
    fun sunDirection(): DoubleArray {
        val azimuth = Math.toRadians(azimuthDegrees + 90.0)
        val polar = Math.toRadians(polarDegrees)
        val tileX = cos(azimuth) * sin(polar)
        val tileY = sin(azimuth) * sin(polar)
        return doubleArrayOf(tileX, -tileY, cos(polar))
    }

    /**
     * Le bloc que le natif attend, dans l'ordre de `Lighting` (`scene_state.hpp`).
     *
     * La couleur du soleil part déjà multipliée par l'intensité et par un gain
     * fixe : le style dose sa lumière pour un nuancier qui la mêle à la couleur
     * de façade d'une façon qui lui est propre ; le nôtre est un Lambert, et le
     * gain ramène une face en plein jour au voisinage du blanc de la livrée.
     */
    fun toFloatArray(): FloatArray {
        val sun = sunDirection()
        val gain = intensity * SUN_GAIN
        return floatArrayOf(
            sun[0].toFloat(), sun[1].toFloat(), sun[2].toFloat(),
            (red(color) * gain).toFloat(), (green(color) * gain).toFloat(), (blue(color) * gain).toFloat(),
            red(skyAmbient).toFloat(), green(skyAmbient).toFloat(), blue(skyAmbient).toFloat(),
            red(groundAmbient).toFloat(), green(groundAmbient).toFloat(), blue(groundAmbient).toFloat(),
            lampGlow.toFloat(),
            shadowStrength.toFloat(),
        )
    }

    companion object {
        /** Le nombre de flottants de [toFloatArray] — contrat avec `kLightingFloats`. */
        const val FLOATS = 14

        /**
         * Le gain appliqué à l'intensité du style.
         *
         * ⚠️ **C'est le réglage qui décide si une pièce sombre reste lisible ou
         * devient un trou noir**, parce que l'éclairage est multiplicatif :
         * l'ombrage cuit d'avant allait de 0,72 (flanc) à 1,02 (toit), jamais
         * plus bas. Le premier jeu de valeurs livrait 0,48 sur un flanc au sud,
         * et un bogie à 0,23 d'aplat y tombait à 0,11 — noir à l'écran, mesuré
         * le 12/09 sur le S21.
         *
         * Les valeurs tiennent donc la **même plage que l'ombrage cuit** :
         * ambiante verticale ≈ 0,70, toit ≈ 0,78, soleil ≈ 0,32 au zénith de sa
         * course. Un toit face au soleil atteint 1,0, un flanc au sud reste à
         * 0,70. Changer l'un des trois sans vérifier les deux autres ramène le
         * noir.
         */
        const val SUN_GAIN = 0.58

        /**
         * Le jour — `style-light.json`.
         *
         * L'ambiante est un ciel légèrement froid sur une chaussée chaude, ce
         * qui donne aux flancs un ton différent du toit même sans soleil. Leur
         * **moyenne** vaut ce que reçoit une face verticale, ≈ 0,70 : voir
         * [SUN_GAIN], c'est le nombre qui compte.
         */
        val DAY = VehicleLighting(
            azimuthDegrees = 175.34931199611484,
            polarDegrees = 47.35609482027159,
            color = 0xFFF6E8,
            intensity = 0.55,
            skyAmbient = 0xC6CBD4,
            groundAmbient = 0x9E9A92,
            lampGlow = 0.0,
            shadowStrength = 0.32,
        )

        /**
         * La nuit — `style-dark.json`.
         *
         * La lumière du style y est rasante et faible ; l'ambiante porte
         * l'essentiel, et elle est **haute** : à `0x4D556B` de ciel, un tram
         * théorique était un bloc noir sur les façades `#434E66` du style —
         * mesuré à z16,5 sur l'appareil, seuls les phares se voyaient. Une
         * caisse doit rester plus claire que la façade derrière elle. Les feux
         * s'allument et l'ombre s'efface — de nuit, elle ne se verrait pas.
         */
        val NIGHT = VehicleLighting(
            azimuthDegrees = 355.27765493478364,
            polarDegrees = 84.0,
            color = 0xB9B1AC,
            intensity = 0.2525,
            skyAmbient = 0xA6AEC2,
            groundAmbient = 0x64687A,
            lampGlow = 1.0,
            shadowStrength = 0.2,
        )

        fun of(night: Boolean): VehicleLighting = if (night) NIGHT else DAY

        private fun red(rgb: Int): Double = (rgb shr 16 and 0xFF) / 255.0
        private fun green(rgb: Int): Double = (rgb shr 8 and 0xFF) / 255.0
        private fun blue(rgb: Int): Double = (rgb and 0xFF) / 255.0
    }
}
