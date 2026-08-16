package io.aule.android.core.geo

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {

    const val EARTH_RADIUS_M = 6_371_008.8

    /** Distance en mètres, formule de haversine. */
    fun distance(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Cap initial de [a] vers [b], en degrés depuis le nord, dans `[0, 360[`. */
    fun bearing(from: Coordinate, to: Coordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizeHeading(Math.toDegrees(atan2(y, x)))
    }

    /** Ramène un cap dans `[0, 360[`. */
    fun normalizeHeading(degrees: Double): Double {
        val value = degrees % 360
        return if (value < 0) value + 360 else value
    }

    /**
     * Le plus court chemin angulaire de [from] vers [to], dans `]-180, 180]`.
     *
     * Sans ça, un cap qui passe de 359° à 1° fait tourner la caméra de 358° dans
     * le mauvais sens — l'écran fait un tour complet là où le véhicule a bougé de
     * deux degrés.
     */
    fun shortestHeadingDelta(from: Double, to: Double): Double {
        var delta = normalizeHeading(to) - normalizeHeading(from)
        if (delta > 180) delta -= 360
        if (delta <= -180) delta += 360
        return delta
    }

    /**
     * Interpole entre deux points.
     *
     * [t] hors de `[0, 1]` extrapole, ce qui est voulu : la glisse d'un véhicule
     * peut dépasser légèrement sa cible entre deux sondages.
     */
    fun interpolate(a: Coordinate, b: Coordinate, t: Double): Coordinate = Coordinate(
        latitude = a.latitude + (b.latitude - a.latitude) * t,
        longitude = a.longitude + (b.longitude - a.longitude) * t,
    )

    /** Interpole entre deux caps par le plus court chemin. */
    fun interpolateHeading(a: Double, b: Double, t: Double): Double =
        normalizeHeading(a + shortestHeadingDelta(a, b) * t)

    /**
     * Formate une distance comme un humain la lit : en mètres tant qu'on peut la
     * marcher, en kilomètres au-delà.
     *
     * Le séparateur décimal est un paramètre plutôt qu'une virgule en dur : ce
     * module ne connaît pas la langue de l'écran, et l'appelant, lui, la connaît.
     */
    fun formatDistance(meters: Double, decimalSeparator: Char = ','): String {
        if (meters < 1000) {
            val rounded = if (meters < 100) {
                (meters / 10).roundToLong() * 10
            } else {
                (meters / 50).roundToLong() * 50
            }
            return "$rounded m"
        }
        val km = meters / 1000
        return if (km < 10) {
            String.format(Locale.ROOT, "%.1f", km).replace('.', decimalSeparator) + " km"
        } else {
            "${km.roundToInt()} km"
        }
    }

    /** Vrai si deux caps sont à moins de [toleranceDegrees] l'un de l'autre. */
    fun headingsAlign(a: Double, b: Double, toleranceDegrees: Double): Boolean =
        abs(shortestHeadingDelta(a, b)) <= toleranceDegrees
}
