package io.aule.android.core.map.camera

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Ce que la main garde de la vue GPS d'un véhicule : un pincement ne doit pas tenir un battement, il
 * doit tenir tant que le véhicule roule — quelle que soit l'allure qui, elle, recule la caméra.
 */
class VehicleFramingOffsetsTest {

    private fun target(zoom: Double = 17.0, pitch: Double = 52.0, bearing: Double = 90.0) = CameraTarget(
        center = Coordinate(47.2, -1.55),
        bearing = bearing,
        pitch = pitch,
        zoom = zoom,
        forwardOffsetPx = 80.0,
    )

    @Test
    fun `sans geste, le cadrage du suivi passe tel quel`() {
        val base = target()
        assertEquals(base, VehicleFramingOffsets.ZERO.applied(base, maxPitch = 60.0))
    }

    @Test
    fun `un dezoom a la main reste un dezoom quand l allure recule la camera`() {
        // La main a dézoomé de deux crans autour d'un cadre à 17,0…
        val offsets = VehicleFramingOffsets.reading(zoom = 15.0, tilt = 52.0, bearing = 90.0, base = target(zoom = 17.0))
        // …le bus accélère : le suivi demande 16,6. Le dézoom de la main s'y ajoute.
        assertEquals(14.6, offsets.applied(target(zoom = 16.6), maxPitch = 60.0).zoom, 1e-9)
    }

    @Test
    fun `l inclinaison choisie reste dans les bornes du moteur`() {
        val offsets = VehicleFramingOffsets.reading(zoom = 17.0, tilt = 80.0, bearing = 90.0, base = target(pitch = 52.0))
        assertEquals(60.0, offsets.applied(target(pitch = 52.0), maxPitch = 60.0).pitch, 1e-9)
        val flat = VehicleFramingOffsets.reading(zoom = 17.0, tilt = 0.0, bearing = 90.0, base = target(pitch = 52.0))
        assertEquals(0.0, flat.applied(target(pitch = 40.0), maxPitch = 60.0).pitch, 1e-9)
    }

    @Test
    fun `une rotation garde son angle relatif au sens de marche, par le plus court chemin`() {
        // Cap du suivi 350°, la main a tourné jusqu'à 10° : +20°, pas −340°.
        val offsets = VehicleFramingOffsets.reading(zoom = 17.0, tilt = 52.0, bearing = 10.0, base = target(bearing = 350.0))
        assertEquals(20.0, offsets.bearing, 1e-9)
        // Le véhicule tourne au 80° : la carte le suit, gardant ses 20° d'écart.
        assertEquals(100.0, offsets.applied(target(bearing = 80.0), maxPitch = 60.0).bearing, 1e-9)
    }
}
