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

    private fun line(id: String, mode: TransportMode, label: String) = ServiceLine(
        id = id,
        label = label,
        description = label,
        mode = mode,
        directions = emptyList(),
    )
}
