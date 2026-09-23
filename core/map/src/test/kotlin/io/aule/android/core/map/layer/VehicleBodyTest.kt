package io.aule.android.core.map.layer

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.map.MapScale
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
        val lat = commerce.latitude
        val near = VehicleBody.emphasis(TransportMode.BUS, 18.0, lat)
        assertEquals(1.0, near, 1e-9)

        // Le tram, déjà long de vingt-huit mètres, avalerait les carrefours au
        // même facteur.
        val far = VehicleBody.emphasis(TransportMode.BUS, MapZoom.NEIGHBOURHOOD, lat)
        assertTrue(VehicleBody.emphasis(TransportMode.TRAM, MapZoom.NEIGHBOURHOOD, lat) < far)
    }

    /**
     * Le contrat qui a remplacé la rampe : **une longueur à l'écran**.
     *
     * L'ancienne rampe rendait ×1,6 à z15,5 — un bus de quinze points au cadre
     * du quartier, « beaucoup trop petit ». On vérifie en points, pas en
     * facteur : c'est ce que l'œil voit.
     */
    @Test
    fun `au cadre du quartier un vehicule garde sa longueur a l ecran`() {
        val lat = commerce.latitude
        for (zoom in listOf(MapZoom.VEHICLE_BODIES_FROM, MapZoom.NEIGHBOURHOOD, 16.0)) {
            for (mode in TransportMode.entries) {
                val meters = VehicleBody.gauge(mode).lengthMeters * VehicleBody.emphasis(mode, zoom, lat)
                val points = meters / MapScale.metersPerPixel(lat, zoom)
                assertEquals(VehicleBody.floorPoints(mode), points, 0.5, "$mode à z$zoom")
            }
        }
        val bus = VehicleBody.gauge(TransportMode.BUS).lengthMeters *
            VehicleBody.emphasis(TransportMode.BUS, MapZoom.NEIGHBOURHOOD, lat) /
            MapScale.metersPerPixel(lat, MapZoom.NEIGHBOURHOOD)
        assertTrue(bus >= 30.0, "un bus se lit d'un regard, pas en le cherchant : $bus pt")
    }

    @Test
    fun `le grossissement ne recule jamais quand on s eloigne`() {
        val lat = commerce.latitude
        for (mode in TransportMode.entries) {
            var previous = Double.MAX_VALUE
            var zoom = 12.0
            while (zoom <= 19.0) {
                val value = VehicleBody.emphasis(mode, zoom, lat)
                assertTrue(value <= previous + 1e-12, "$mode grossit en approchant à z$zoom")
                assertTrue(value >= 1.0, "$mode passe sous l'échelle vraie à z$zoom")
                previous = value
                zoom += 0.1
            }
        }
    }

    @Test
    fun `une donnee non finie rend l echelle vraie`() {
        assertEquals(1.0, VehicleBody.emphasis(TransportMode.BUS, Double.NaN, 47.2), 1e-9)
        assertEquals(1.0, VehicleBody.emphasis(TransportMode.BUS, 15.5, Double.NaN), 1e-9)
    }

    /**
     * Les caisses sont pleines au cadre du quartier, pas à mi-fondu.
     */
    @Test
    fun `le quartier s ouvre sur des volumes pleins`() {
        assertEquals(1.0, VehicleBody.bodyFade(MapZoom.NEIGHBOURHOOD, 0.3, MapZoom.VEHICLE_BODIES_FROM), 1e-9)
    }

    @Test
    fun `les trois modes ont un volume`() {
        for (mode in TransportMode.entries) {
            val gauge = VehicleBody.gauge(mode)
            assertTrue(gauge.lengthMeters > gauge.widthMeters, "$mode")
            assertTrue(gauge.heightMeters > 2.0, "$mode")
        }
    }

    /**
     * La rampe de fondu, partagée entre l'extrusion et les modèles.
     *
     * ⚠️ **C'est le seul endroit qui empêche les deux rendus de diverger.**
     * L'extrusion interpole son opacité par une `Expression` que MapLibre évalue,
     * la scène 3D reçoit son alpha calculé en Kotlin. Deux rampes écrites
     * séparément se décaleraient, et le passage de l'une à l'autre se verrait
     * comme un ressaut — exactement ce que le fondu existe pour éviter.
     */
    @Test
    fun `le fondu du volume est nul avant le seuil et plein apres`() {
        val fade = 0.3
        val from = MapZoom.VEHICLE_BODIES_FROM

        assertEquals(0.0, VehicleBody.bodyFade(from - fade, fade, from), 1e-9)
        assertEquals(0.0, VehicleBody.bodyFade(from - 2 * fade, fade, from), 1e-9)
        assertEquals(1.0, VehicleBody.bodyFade(from + fade, fade, from), 1e-9)
        assertEquals(1.0, VehicleBody.bodyFade(18.0, fade, from), 1e-9)
        // À mi-chemin, la moitié : c'est ce qui fait croiser les deux rendus.
        assertEquals(0.5, VehicleBody.bodyFade(from, fade, from), 1e-9)
    }

    @Test
    fun `le fondu ne recule jamais quand on approche`() {
        val fade = 0.3
        val from = MapZoom.VEHICLE_BODIES_FROM
        var previous = -1.0
        var zoom = from - 2 * fade
        while (zoom <= from + 2 * fade) {
            val value = VehicleBody.bodyFade(zoom, fade, from)
            assertTrue(value >= previous, "le fondu recule à z$zoom : $value après $previous")
            assertTrue(value in 0.0..1.0, "le fondu sort de l'intervalle à z$zoom : $value")
            previous = value
            zoom += 0.05
        }
    }
}
