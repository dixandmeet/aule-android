package io.aule.android.core.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Où est le soleil, vu d'un point de la Terre à un instant donné.
 *
 * Deux angles suffisent à éclairer une ville : celui d'où vient la lumière et
 * la hauteur à laquelle elle vient. La couleur et la force s'en déduisent, mais
 * ailleurs — ici on ne fait que de l'astronomie, et l'astronomie n'a pas d'avis
 * sur le rendu.
 *
 * Le calcul est **purement géométrique** : ni réfraction atmosphérique, ni
 * parallaxe. Près de l'horizon, le soleil qu'on voit est donc environ un demi-
 * degré plus haut que celui qu'on calcule. C'est sans conséquence pour ce qui
 * nous occupe — on éclaire des façades, on ne prédit pas l'heure du lever.
 */
data class SolarPosition(
    /**
     * D'où vient la lumière, en degrés depuis le nord et dans le sens des
     * aiguilles — la convention des caps de [GeoMath], pour qu'un azimut solaire
     * et un cap de véhicule se comparent sans conversion.
     */
    val azimuthDegrees: Double,

    /**
     * À quelle hauteur au-dessus de l'horizon, en degrés.
     *
     * ⚠️ **Négative sous l'horizon, et c'est la valeur utile.** C'est elle, et
     * elle seule, qui distingue un crépuscule d'une nuit noire : à −3° le ciel
     * tient encore une lueur, à −20° il n'y a plus rien. L'écrêter à zéro ferait
     * de toutes les nuits la même nuit.
     */
    val elevationDegrees: Double,
) {

    companion object {

        /**
         * La position du soleil au-dessus de [coordinate], à [epochMillis].
         *
         * C'est l'algorithme de faible précision de l'almanach astronomique :
         * une orbite circulaire corrigée des deux premiers termes de
         * l'équation du centre. Il vaut le centième de degré sur 1950–2050,
         * soit **cent fois mieux que ce qu'un rendu peut montrer** — le
         * diamètre apparent du soleil lui-même est d'un demi-degré.
         *
         * Rien n'y dépend du fuseau horaire ni de l'heure d'été : [epochMillis]
         * est un instant absolu, et la longitude porte à elle seule le décalage
         * entre l'heure de la montre et celle du soleil. C'est ce qui évite le
         * défaut classique — une carte éclairée avec une heure de retard le
         * dernier dimanche de mars.
         */
        fun at(coordinate: Coordinate, epochMillis: Long): SolarPosition {
            // Jours écoulés depuis J2000.0, fraction comprise. Toute la suite
            // s'exprime en fonction de ce seul nombre.
            val days = epochMillis / MILLIS_PER_DAY - J2000_FROM_UNIX_DAYS

            // La position qu'aurait le soleil si l'orbite était circulaire,
            // puis la correction de son ellipticité : c'est elle qui donne
            // l'équation du temps, donc le fait que midi solaire dérive de
            // presque un quart d'heure au fil de l'année.
            val meanLongitude = MEAN_LONGITUDE_AT_J2000 + MEAN_LONGITUDE_PER_DAY * days
            val meanAnomaly = Math.toRadians(MEAN_ANOMALY_AT_J2000 + MEAN_ANOMALY_PER_DAY * days)
            val eclipticLongitude = Math.toRadians(
                meanLongitude +
                    EQUATION_OF_CENTER_1 * sin(meanAnomaly) +
                    EQUATION_OF_CENTER_2 * sin(2 * meanAnomaly),
            )

            // L'inclinaison de l'axe terrestre : la seule raison qu'il y ait
            // des saisons, et donc que la lumière de décembre rase quand celle
            // de juin tombe d'aplomb.
            val obliquity = Math.toRadians(OBLIQUITY_AT_J2000 + OBLIQUITY_PER_DAY * days)

            // Passage de l'écliptique à l'équateur céleste.
            val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))
            val declination = asin(sin(obliquity) * sin(eclipticLongitude))

            // Le temps sidéral dit de combien la Terre a tourné sous le ciel ;
            // l'angle horaire, de combien elle a tourné sous le soleil.
            val siderealHours = (GMST_AT_J2000_HOURS + SIDEREAL_HOURS_PER_DAY * days) % HOURS_PER_DAY
            val hourAngle = Math.toRadians(
                siderealHours * DEGREES_PER_HOUR + coordinate.longitude - Math.toDegrees(rightAscension),
            )

            val latitude = Math.toRadians(coordinate.latitude)
            val elevation = asin(
                sin(latitude) * sin(declination) +
                    cos(latitude) * cos(declination) * cos(hourAngle),
            )
            // La déclinaison reste dans ±23,5° : sa tangente ne part jamais à
            // l'infini, et la forme réduite de l'azimut est donc sûre ici —
            // ce qu'elle ne serait pas pour un astre quelconque.
            val azimuth = atan2(
                -sin(hourAngle),
                tan(declination) * cos(latitude) - sin(latitude) * cos(hourAngle),
            )

            return SolarPosition(
                azimuthDegrees = GeoMath.normalizeHeading(Math.toDegrees(azimuth)),
                elevationDegrees = Math.toDegrees(elevation),
            )
        }

        private const val MILLIS_PER_DAY = 86_400_000.0
        private const val HOURS_PER_DAY = 24.0
        private const val DEGREES_PER_HOUR = 15.0

        /**
         * Ce qui sépare l'époque Unix de J2000.0, en jours.
         *
         * Le demi-jour n'est pas une coquille : les jours juliens commencent à
         * midi, l'époque Unix à minuit. L'oublier décale toute la journée de
         * douze heures — une erreur qui se voit tout de suite, au moins.
         */
        private const val J2000_FROM_UNIX_DAYS = 10_957.5

        private const val MEAN_LONGITUDE_AT_J2000 = 280.460
        private const val MEAN_LONGITUDE_PER_DAY = 0.985_647_4
        private const val MEAN_ANOMALY_AT_J2000 = 357.528
        private const val MEAN_ANOMALY_PER_DAY = 0.985_600_3
        private const val EQUATION_OF_CENTER_1 = 1.915
        private const val EQUATION_OF_CENTER_2 = 0.020
        private const val OBLIQUITY_AT_J2000 = 23.439
        private const val OBLIQUITY_PER_DAY = -0.000_000_4

        /** Le temps sidéral de Greenwich à J2000.0, et sa marche quotidienne. */
        private const val GMST_AT_J2000_HOURS = 18.697_374_558
        private const val SIDEREAL_HOURS_PER_DAY = 24.065_709_824_419_08
    }
}
