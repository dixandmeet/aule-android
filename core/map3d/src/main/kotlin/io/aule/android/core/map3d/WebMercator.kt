package io.aule.android.core.map3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * La projection de MapLibre, et le repère métrique dans lequel on lui parle.
 *
 * ⚠️ **Ce fichier doit rester d'accord avec `vehicle_layer.cpp`, à la constante
 * près.** Le natif remultiplie par la même échelle ce que celui-ci divise : une
 * divergence ne se verrait pas en compilation, elle décalerait la flotte.
 *
 * Calcul pur — vérifiable sur la JVM de l'hôte.
 */
object WebMercator {

    /**
     * Le tour de la Terre à l'équateur, en mètres.
     *
     * **Pas le rayon moyen de `GeoMath`** : celui-ci est bon pour une haversine,
     * faux pour une projection.
     */
    const val EQUATOR_METERS = 2 * PI * 6_378_137.0

    /** MapLibre travaille en tuiles de 512 px. */
    const val TILE_SIZE = 512.0

    /** La limite de la projection : au-delà, `tan` diverge. */
    private const val LATITUDE_LIMIT = 85.051_128_78

    fun x(longitude: Double): Double = (180.0 + longitude) / 360.0

    fun y(latitude: Double): Double {
        val clamped = latitude.coerceIn(-LATITUDE_LIMIT, LATITUDE_LIMIT)
        return (180.0 - (180.0 / PI) * ln(tan(PI / 4 + clamped * PI / 360))) / 360.0
    }

    /** Le tour de la Terre au parallèle donné. */
    fun circumference(latitude: Double): Double =
        EQUATOR_METERS * cos(latitude * PI / 180.0)

    /**
     * Où tombe une coordonnée dans le repère de scène, en mètres depuis l'ancre.
     *
     * La conversion passe par l'écart **en mercator** puis divise par l'échelle de
     * l'ancre, et non par une haversine : composée avec la matrice du natif, qui
     * remultiplie par la même échelle, elle redonne exactement la coordonnée
     * mercator d'origine. La variation de latitude à l'intérieur de la vue ne peut
     * donc pas décaler un véhicule — elle ne joue que sur la **taille** des
     * maillages, de deux centièmes de pour cent sur les deux kilomètres visibles.
     */
    fun eastOffsetMeters(longitude: Double, anchorMercX: Double, anchorLatitude: Double): Double =
        (x(longitude) - anchorMercX) * circumference(anchorLatitude)

    /** Idem vers le nord. Le y mercator croît vers le sud, d'où l'inversion. */
    fun northOffsetMeters(latitude: Double, anchorMercY: Double, anchorLatitude: Double): Double =
        (anchorMercY - y(latitude)) * circumference(anchorLatitude)
}
