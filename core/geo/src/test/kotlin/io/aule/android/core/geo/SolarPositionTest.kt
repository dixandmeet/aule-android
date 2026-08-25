package io.aule.android.core.geo

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le soleil, vérifié contre ce que la géométrie impose — pas contre une autre
 * implémentation.
 *
 * Une éphéméride de référence dirait seulement que deux codes s'accordent. Les
 * culminations, elles, sont des vérités : au midi solaire d'un solstice, le
 * soleil monte exactement à `90° − |latitude − déclinaison|`, et la déclinaison
 * d'un solstice vaut l'inclinaison de l'axe terrestre. C'est vrai depuis
 * toujours et ça le restera, ce qui en fait le bon juge.
 */
class SolarPositionTest {

    @Test
    fun `au solstice de juin, le soleil culmine a la hauteur que la geometrie impose`() {
        val (_, elevation) = highestOfDay(Coordinate.NANTES, 2026, 6, 21)
        assertEquals(90.0 - NANTES_LATITUDE + AXIAL_TILT, elevation, TOLERANCE_DEGREES)
    }

    @Test
    fun `au solstice de decembre, il culmine d autant plus bas`() {
        val (_, elevation) = highestOfDay(Coordinate.NANTES, 2026, 12, 21)
        assertEquals(90.0 - NANTES_LATITUDE - AXIAL_TILT, elevation, TOLERANCE_DEGREES)
    }

    @Test
    fun `au plus haut, la lumiere vient du sud`() {
        val (azimuth, _) = highestOfDay(Coordinate.NANTES, 2026, 6, 21)
        assertEquals(180.0, azimuth, 0.5)
    }

    /**
     * Le même calcul, une hémisphère plus bas.
     *
     * La formule de l'azimut se réduit joliment tant qu'on reste au nord ; ce
     * test est là pour qu'elle ne se réduise pas **trop**. À Sydney, le soleil
     * culmine au nord, et une implémentation qui aurait supposé le contraire
     * passerait tous les tests précédents sans broncher.
     */
    @Test
    fun `dans l hemisphere sud, elle vient du nord`() {
        val sydney = Coordinate(latitude = -33.8688, longitude = 151.2093)
        val (azimuth, elevation) = highestOfDay(sydney, 2026, 12, 21)
        assertEquals(90.0 - abs(sydney.latitude + AXIAL_TILT), elevation, TOLERANCE_DEGREES)
        // Le nord se lit 0 ou 360 selon le côté par lequel on l'atteint : c'est
        // l'écart au tour complet qui compte, pas la valeur brute.
        val toNorth = minOf(azimuth, 360.0 - azimuth)
        assertTrue(toNorth < 0.5, "azimut $azimuth° : la lumière ne vient pas du nord")
    }

    @Test
    fun `au milieu de la nuit, le soleil est sous l horizon`() {
        val position = SolarPosition.at(Coordinate.NANTES, utc(2026, 1, 15, 2, 0))
        assertTrue(
            position.elevationDegrees < -10.0,
            "élévation ${position.elevationDegrees}° : le soleil n'est pas couché",
        )
    }

    @Test
    fun `au lever, la lumiere vient de l est`() {
        // Fin août à Nantes, le soleil se lève peu après 6 h UTC.
        val position = SolarPosition.at(Coordinate.NANTES, utc(2026, 8, 25, 6, 0))
        assertTrue(
            position.elevationDegrees in 0.0..15.0,
            "élévation ${position.elevationDegrees}° : le soleil n'est pas au ras de l'horizon",
        )
        assertTrue(
            position.azimuthDegrees in 60.0..110.0,
            "azimut ${position.azimuthDegrees}° : la lumière ne vient pas de l'est",
        )
    }

    /**
     * L'azimut est un cap, et un cap ne sort pas du tour.
     *
     * La garantie n'est pas cosmétique : la carte l'écrit tel quel dans le bloc
     * `light` du style, et un azimut négatif y passerait sans erreur — la
     * lumière viendrait simplement d'ailleurs.
     */
    @Test
    fun `l azimut reste dans le tour complet, toute l annee`() {
        var day = 0
        while (day < 365) {
            val position = SolarPosition.at(Coordinate.NANTES, utc(2026, 1, 1, 12, 0) + day * DAY_MS)
            assertTrue(
                position.azimuthDegrees >= 0.0 && position.azimuthDegrees < 360.0,
                "jour $day : azimut ${position.azimuthDegrees}° hors du tour",
            )
            day++
        }
    }

    /**
     * Le midi solaire dérive de la montre, et c'est l'équation du temps.
     *
     * Nantes est à 1,55° à l'ouest de Greenwich, soit un peu plus de six
     * minutes de retard sur l'heure UTC. Le reste de l'écart — jusqu'à un quart
     * d'heure selon la saison — vient de l'ellipticité de l'orbite. Une
     * implémentation qui aurait négligé la correction du centre culminerait
     * toujours à la même heure, et ce test tomberait.
     */
    @Test
    fun `le midi solaire ne tombe pas a la meme heure en fevrier et en novembre`() {
        val february = minuteOfHighest(Coordinate.NANTES, 2026, 2, 11)
        val november = minuteOfHighest(Coordinate.NANTES, 2026, 11, 3)
        assertTrue(
            abs(february - november) > 20,
            "midi solaire à $february et $november min UTC : l'équation du temps est absente",
        )
    }

    // ------------------------------------------------------------------ outils

    /** L'azimut et l'élévation du moment le plus haut de la journée. */
    private fun highestOfDay(at: Coordinate, year: Int, month: Int, day: Int): Pair<Double, Double> {
        val minute = minuteOfHighest(at, year, month, day)
        val position = SolarPosition.at(at, utc(year, month, day, 0, 0) + minute * MINUTE_MS)
        return position.azimuthDegrees to position.elevationDegrees
    }

    /**
     * La minute UTC où le soleil est au plus haut, balayée à la minute.
     *
     * Balayer plutôt que résoudre : le midi solaire dépend de l'équation du
     * temps, donc le calculer ici reviendrait à tester le code avec lui-même.
     * Une minute d'écart coûte moins d'un millième de degré sur la
     * culmination, où la courbe est plate.
     */
    private fun minuteOfHighest(at: Coordinate, year: Int, month: Int, day: Int): Int {
        val midnight = utc(year, month, day, 0, 0)
        var best = 0
        var highest = -90.0
        var minute = 0
        while (minute < MINUTES_PER_DAY) {
            val elevation = SolarPosition.at(at, midnight + minute * MINUTE_MS).elevationDegrees
            if (elevation > highest) {
                highest = elevation
                best = minute
            }
            minute++
        }
        return best
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private companion object {
        const val NANTES_LATITUDE = 47.2184

        /** L'inclinaison de l'axe terrestre : la déclinaison d'un solstice. */
        const val AXIAL_TILT = 23.44

        /**
         * La marge qu'on s'accorde sur une culmination.
         *
         * L'algorithme tient le centième de degré ; un vingtième laisse la
         * place à la minute de balayage et au fait qu'un solstice ne tombe pas
         * exactement à midi. C'est deux fois moins que la largeur apparente du
         * soleil, donc bien en deçà de ce qu'un rendu pourrait montrer.
         */
        const val TOLERANCE_DEGREES = 0.05

        const val MINUTE_MS = 60_000L
        const val DAY_MS = 86_400_000L
        const val MINUTES_PER_DAY = 24 * 60
    }
}
