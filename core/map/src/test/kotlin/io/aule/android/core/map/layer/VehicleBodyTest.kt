package io.aule.android.core.map.layer

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.map.MapZoom
import io.aule.android.core.model.TransportMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'empreinte des véhicules en volume.
 *
 * Elle se vérifie ici plutôt qu'à l'écran : une caisse tournée de quatre-vingt-dix
 * degrés, ou large de onze mètres pour deux et demi de long, se voit à peine sur
 * un bus de quelques pixels — et se lit très bien sur un tram.
 */
class VehicleBodyTest {

    private val commerce = Coordinate(latitude = 47.2136, longitude = -1.5573)

    private fun cornersOf(
        center: Coordinate,
        heading: Double,
        mode: TransportMode = TransportMode.BUS,
        scale: Double = 1.0,
    ): List<Coordinate> {
        val ring = DoubleArray(VehicleBody.VERTICES * 2)
        VehicleBody.footprint(
            latitude = center.latitude,
            longitude = center.longitude,
            headingDegrees = heading,
            gauge = VehicleBody.gauge(mode),
            scale = scale,
            out = ring,
        )
        return (0 until VehicleBody.VERTICES).map {
            Coordinate(latitude = ring[it * 2 + 1], longitude = ring[it * 2])
        }
    }

    @Test
    fun `la caisse fait les cotes du vehicule`() {
        val gauge = VehicleBody.gauge(TransportMode.BUS)
        val corners = cornersOf(commerce, heading = 0.0)

        // Sommets 0 et 5 : l'arrière gauche et l'entrée du nez, du même côté.
        val length = GeoMath.distance(corners[0], corners[4])
        assertTrue(abs(length - gauge.lengthMeters) < 0.5, "longueur = $length")

        val width = GeoMath.distance(corners[0], corners[1])
        assertTrue(abs(width - gauge.widthMeters) < 0.1, "largeur = $width")
    }

    /**
     * Le cap compte les degrés depuis le nord dans le sens des aiguilles. Une
     * inversion de sinus et cosinus met les bus en travers de la rue sans rien
     * casser d'autre — c'est exactement le genre d'erreur qui passe la revue.
     */
    @Test
    fun `la caisse pointe dans le sens du cap`() {
        for (heading in listOf(0.0, 45.0, 90.0, 180.0, 271.0)) {
            val corners = cornersOf(commerce, heading)
            // Le milieu du nez, contre le milieu de l'arrière.
            val nose = GeoMath.interpolate(corners[3], corners[4], 0.5)
            val tail = GeoMath.interpolate(corners[0], corners[1], 0.5)
            val actual = GeoMath.bearing(tail, nose)
            assertEquals(
                0.0,
                GeoMath.shortestHeadingDelta(heading, actual),
                1.0,
                "cap $heading rendu $actual",
            )
        }
    }

    @Test
    fun `le nez est plus etroit que la caisse`() {
        val corners = cornersOf(commerce, heading = 0.0)
        val nose = GeoMath.distance(corners[3], corners[4])
        val width = GeoMath.distance(corners[0], corners[1])
        assertTrue(nose < width, "nez = $nose, largeur = $width")
        assertTrue(nose > width / 2, "un nez trop fin fait une flèche, pas un véhicule : $nose")
    }

    /**
     * La caisse est posée **à cheval** sur la position du véhicule, moitié devant
     * moitié derrière. Une caisse qui partirait du point vers l'avant ferait
     * dépasser chaque bus d'une demi-longueur dans le carrefour qu'il n'a pas
     * encore atteint.
     */
    @Test
    fun `la caisse est posee a cheval sur la position du vehicule`() {
        val gauge = VehicleBody.gauge(TransportMode.TRAM)
        val corners = cornersOf(commerce, heading = 137.0, mode = TransportMode.TRAM)
        val nose = GeoMath.interpolate(corners[3], corners[4], 0.5)
        val tail = GeoMath.interpolate(corners[0], corners[1], 0.5)

        assertEquals(gauge.lengthMeters / 2, GeoMath.distance(commerce, nose), 0.2)
        assertEquals(gauge.lengthMeters / 2, GeoMath.distance(commerce, tail), 0.2)
    }

    @Test
    fun `le grossissement s eteint quand on descend dans la rue`() {
        val far = VehicleBody.emphasis(TransportMode.BUS, MapZoom.VEHICLE_BODIES_FROM)
        val near = VehicleBody.emphasis(TransportMode.BUS, 18.0)
        assertTrue(far > 1.4, "au seuil, un bus à l'échelle exacte se devine à peine : $far")
        assertEquals(1.0, near, 1e-9)

        // Le tram, déjà long de vingt-huit mètres, avalerait les carrefours au
        // même facteur.
        assertTrue(VehicleBody.emphasis(TransportMode.TRAM, MapZoom.VEHICLE_BODIES_FROM) < far)
    }

    @Test
    fun `les trois modes ont un volume`() {
        for (mode in TransportMode.entries) {
            val gauge = VehicleBody.gauge(mode)
            assertTrue(gauge.lengthMeters > gauge.widthMeters, "$mode")
            assertTrue(gauge.heightMeters > 2.0, "$mode")
        }
    }
}
