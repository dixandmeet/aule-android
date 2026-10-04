package io.aule.android.feature.auth

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class RegistrationNetworkTest {
    private val stops = listOf(0.45f, 0.6f, 0.93f)

    @Test
    fun `le vehicule conserve chaque arret pendant 1 virgule 6 seconde`() {
        val travel = 38f - stops.size * 1.6f
        stops.forEachIndexed { index, stop ->
            val arrival = stop * travel + index * 1.6f
            assertEquals(stop, networkVehicleFraction(arrival + 0.01f, 38f, stops))
            assertEquals(stop, networkVehicleFraction(arrival + 1.59f, 38f, stops))
            assertTrue(networkVehicleFraction(arrival + 1.61f, 38f, stops) > stop)
        }
    }

    @Test
    fun `la reprise de l horloge garde la meme phase pour toutes les lignes`() {
        for (duration in listOf(38f, 24f, 22f)) {
            assertEquals(networkVehicleFraction(7f, duration, stops),
                networkVehicleFraction(5016f + 7f, duration, stops), 0.00001f)
        }
    }

    @Test
    fun `le trajet avance entre les arrets et revient hors champ au debut`() {
        assertEquals(0f, networkVehicleFraction(0f, 38f, stops))
        assertEquals(0f, networkVehicleFraction(38f, 38f, stops))
        assertTrue(networkVehicleFraction(37.9f, 38f, stops) > 0.99f)
        assertTrue(networkVehicleFraction(2f, 38f, stops) > networkVehicleFraction(1f, 38f, stops))
    }
}
