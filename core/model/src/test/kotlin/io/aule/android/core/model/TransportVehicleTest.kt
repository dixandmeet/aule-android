package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Ce que la fiche d'un véhicule déduit de ce que le réseau publie. */
class TransportVehicleTest {

    private fun vehicle(
        occupancy: Double? = null,
        speedMps: Double? = null,
        dwellSeconds: Double = 0.0,
        updatedAt: Instant? = null,
        feed: VehicleFeed = VehicleFeed.LIVE,
    ) = TransportVehicle(
        id = "v1",
        mode = TransportMode.BUS,
        feed = feed,
        lineId = "C6",
        lineName = "C6",
        coordinate = Coordinate.NANTES,
        occupancy = occupancy,
        speedMps = speedMps,
        dwellSeconds = dwellSeconds,
        updatedAt = updatedAt,
    )

    @Test
    fun `les paliers de remplissage suivent les seuils annonces`() {
        assertEquals(VehicleLoad.QUIET, vehicle(occupancy = 0.0).load)
        assertEquals(VehicleLoad.QUIET, vehicle(occupancy = 0.34).load)
        assertEquals(VehicleLoad.STEADY, vehicle(occupancy = 0.35).load)
        assertEquals(VehicleLoad.BUSY, vehicle(occupancy = 0.70).load)
        assertEquals(VehicleLoad.FULL, vehicle(occupancy = 1.0).load)
    }

    /**
     * Une charge absente et une charge aberrante se disent pareil : **rien**.
     * Annoncer « Complet » sur un capteur qui remonte 3,0 ferait renoncer à un
     * véhicule vide.
     */
    @Test
    fun `une charge absente ou aberrante ne dit rien`() {
        assertNull(vehicle(occupancy = null).load)
        assertNull(vehicle(occupancy = -0.2).load)
        assertNull(vehicle(occupancy = 3.0).load)
        assertNull(vehicle(occupancy = Double.NaN).load)
    }

    @Test
    fun `une vitesse de trainee vaut un arret`() {
        assertNull(vehicle(speedMps = 0.0).speedKmh)
        assertNull(vehicle(speedMps = 0.9).speedKmh)
        assertEquals(36, vehicle(speedMps = 10.0).speedKmh)
        assertNull(vehicle(speedMps = null).speedKmh)
    }

    /**
     * Un véhicule calculé depuis l'horaire roule à la vitesse de l'horaire :
     * l'écrire en chiffres la ferait passer pour un relevé.
     */
    @Test
    fun `un vehicule theorique n annonce ni vitesse ni arret`() {
        val theoretical = vehicle(speedMps = 10.0, feed = VehicleFeed.SCHEDULED)
        assertNull(theoretical.speedKmh)
        assertFalse(vehicle(speedMps = 0.0, feed = VehicleFeed.SCHEDULED).isStopped)
    }

    /**
     * L'arrêt se lit sur la vitesse mesurée, **jamais** sur `dwellSeconds` : le
     * serveur y met cinq secondes pour toute la flotte, parce que c'est le
     * temps de pause de la glisse et non l'observation d'un véhicule à quai.
     */
    @Test
    fun `l arret se lit sur la vitesse et pas sur le temps de pause`() {
        assertTrue(vehicle(speedMps = 0.0).isStopped)
        assertTrue(vehicle(speedMps = 0.4).isStopped)
        assertFalse(vehicle(speedMps = 6.2, dwellSeconds = 5.0).isStopped)
        assertFalse(vehicle(speedMps = null, dwellSeconds = 5.0).isStopped)
    }

    /**
     * Une horloge de téléphone en avance sur celle du serveur donnerait un âge
     * négatif — donc une position relevée dans le futur.
     */
    @Test
    fun `l age d une position ne remonte jamais le temps`() {
        val recorded = Instant.parse("2026-08-17T21:42:00Z")
        val subject = vehicle(updatedAt = recorded)
        assertEquals(12L, subject.positionAgeSeconds(recorded.plusSeconds(12)))
        assertEquals(0L, subject.positionAgeSeconds(recorded.minusSeconds(30)))
        assertNull(vehicle(updatedAt = null).positionAgeSeconds(recorded))
    }
}
