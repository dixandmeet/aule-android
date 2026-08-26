package io.aule.android.core.map

import kotlin.math.cos
import kotlin.math.pow

/**
 * De combien de pixels d'écran un mètre au sol se paie.
 *
 * MapLibre mesure ses rayons et ses épaisseurs en **pixels**. Une distance
 * réelle — le rayon d'incertitude d'un GPS, la portée d'un capteur — doit
 * donc être convertie, et la conversion dépend de deux choses : le zoom, et
 * la latitude, parce que la projection Web Mercator étire les distances à
 * mesure qu'on monte vers les pôles.
 *
 * ⚠️ **512 et non 256.** La moitié des formules qu'on trouve écrites
 * supposent des tuiles de 256 pixels : elles donnent un résultat exactement
 * deux fois trop grand sur MapLibre, dont le monde fait `512 · 2^zoom`
 * pixels de large. L'erreur ne se voit pas — un anneau deux fois trop large
 * reste un anneau plausible — et c'est ce qui la rend coûteuse.
 */
internal object MapScale {

    /**
     * La circonférence de la Terre à l'équateur, divisée par la largeur du
     * monde au zoom zéro. C'est le nombre de mètres qu'un pixel couvre à
     * l'équateur, carte entièrement dézoomée.
     */
    private const val METERS_PER_PIXEL_AT_EQUATOR_Z0 = 40_075_016.686 / 512.0

    /**
     * Le zoom auquel on exprime les rayons avant de les laisser suivre
     * l'échelle.
     *
     * Vingt-deux est le zoom maximal de la carte : y calculer le rayon donne
     * le plus grand nombre de la plage, et tous les autres s'en déduisent en
     * divisant. L'inverse — partir d'un zoom bas — ferait travailler
     * l'interpolation sur des fractions de pixel, où la précision d'un
     * `float` commence à compter.
     */
    const val REFERENCE_ZOOM = 22

    /** Combien de pixels vaut un mètre, à cette latitude et à ce zoom. */
    fun pixelsPerMeter(latitude: Double, zoom: Double): Double =
        2.0.pow(zoom) / (METERS_PER_PIXEL_AT_EQUATOR_Z0 * cos(Math.toRadians(latitude)))

    /** Combien de mètres couvre un pixel, à cette latitude et à ce zoom. */
    fun metersPerPixel(latitude: Double, zoom: Double): Double =
        1.0 / pixelsPerMeter(latitude, zoom)
}
