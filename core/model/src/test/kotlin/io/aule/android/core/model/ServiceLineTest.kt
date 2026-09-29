package io.aule.android.core.model

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ServiceLineTest {

    @Test
    fun `un libelle long se coupe sur le tiret Naolib`() {
        assertEquals(
            "Hermeland" to "Chantrerie",
            serviceLineEndpoints("Hermeland - Chantrerie"),
        )
        assertEquals(
            "Foch" to "Gare",
            serviceLineEndpoints("Foch  -  Gare"),
        )
    }

    @Test
    fun `sans tiret, le second terminus reste vide`() {
        assertEquals("C6" to "", serviceLineEndpoints("C6"))
        assertEquals("" to "", serviceLineEndpoints("  "))
    }

    @Test
    fun `les lignes se trient par mode puis numero`() {
        val tram = line("1", TransportMode.TRAM, "1")
        val busC6 = line("C6", TransportMode.BUS, "C6")
        val bus12 = line("12", TransportMode.BUS, "12")
        val bus3 = line("3", TransportMode.BUS, "3")
        val sorted = listOf(busC6, tram, bus12, bus3).sortedWith(::compareServiceLines)
        assertEquals(listOf("3", "12", "C6", "1"), sorted.map { it.label })
    }

    /**
     * `gtfs_routes` porte aussi les cars Aléop et les TER : sans filtre, un conducteur sans
     * réseau déclaré voyait deux « C6 » dans sa prise de service.
     */
    @Test
    fun `sans reseau declare le conducteur ne voit que Naolib et jamais un train`() {
        val chronobus = line("C6", TransportMode.BUS, "C6").copy(networkId = NAOLIB_NETWORK_ID)
        val ancienne = line("12", TransportMode.BUS, "12")
        val car = line("ALEOP:309", TransportMode.BUS, "E309").copy(networkId = ALEOP)
        val tramTrain = line("ALEOP:TER:FR:Line::359f7c82:", TransportMode.TER, "C6").copy(networkId = ALEOP)
        val all = listOf(chronobus, ancienne, car, tramTrain)

        assertEquals(listOf("C6", "12"), all.forDriverNetwork(null).map { it.id })
        // Un réseau déclaré garde ses lignes — sauf les trains, qu'on ne conduit pas ici.
        assertEquals(listOf("ALEOP:309"), all.forDriverNetwork(ALEOP).map { it.id })
        assertEquals(listOf("C6"), all.forDriverNetwork(NAOLIB_NETWORK_ID).map { it.id })
    }

    private fun line(id: String, mode: TransportMode, label: String) = ServiceLine(
        id = id,
        label = label,
        description = label,
        mode = mode,
        directions = emptyList(),
    )

    private companion object {
        const val ALEOP = "a1e0b000-0000-4000-8000-000000000001"
    }
}
