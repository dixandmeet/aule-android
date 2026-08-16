package io.aule.android.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class DriverReportTest {

    @Test
    fun `les dix types portent le vocabulaire du CHECK`() {
        assertEquals(
            setOf(
                "traffic",
                "delay",
                "detour",
                "crowded",
                "stop_skipped",
                "breakdown",
                "accident",
                "passenger_illness",
                "incivility",
                "other",
            ),
            DriverReportType.entries.map { it.wire }.toSet(),
        )
        assertEquals(10, DriverReportType.entries.size)
        assertEquals(DriverReportType.entries.size, DriverReportType.entries.map { it.wire }.toSet().size)
    }

    @Test
    fun `les trois urgences aussi`() {
        assertEquals(
            setOf("low", "medium", "high"),
            DriverReportUrgency.entries.map { it.wire }.toSet(),
        )
    }

    @Test
    fun `le corps porte le conducteur, le type et l urgence`() {
        val body = DriverReport(
            type = DriverReportType.ACCIDENT,
            urgency = DriverReportUrgency.HIGH,
        ).toInsert(driverId = "d-1")

        assertEquals("d-1", body["driver_id"])
        assertEquals("accident", body["type"])
        assertEquals("high", body["urgency"])
    }

    @Test
    fun `l urgence par defaut est medium`() {
        val body = DriverReport(type = DriverReportType.TRAFFIC).toInsert(driverId = "d-1")
        assertEquals("medium", body["urgency"])
    }

    @Test
    fun `un message vide ne part pas`() {
        for (blank in listOf("", "   ", "\n")) {
            val body = DriverReport(
                type = DriverReportType.OTHER,
                message = blank,
            ).toInsert(driverId = "d-1")
            assertFalse(body.containsKey("message"))
        }
        assertFalse(
            DriverReport(type = DriverReportType.OTHER).toInsert(driverId = "d-1").containsKey("message"),
        )
    }

    @Test
    fun `un message utile part, debarrasse de ses espaces`() {
        val body = DriverReport(
            type = DriverReportType.DETOUR,
            message = "  Rue barrée au carrefour  ",
        ).toInsert(driverId = "d-1")
        assertEquals("Rue barrée au carrefour", body["message"])
    }

    @Test
    fun `sans position, le signalement part quand meme`() {
        val body = DriverReport(type = DriverReportType.BREAKDOWN).toInsert(driverId = "d-1")
        assertFalse(body.containsKey("latitude"))
        assertFalse(body.containsKey("longitude"))
    }

    @Test
    fun `le service et le vehicule s attachent quand on les connait`() {
        val body = DriverReport(
            type = DriverReportType.DELAY,
            latitude = 47.21,
            longitude = -1.55,
        ).toInsert(driverId = "d-1", driverServiceId = "s-1", vehicleId = "BUS-42")

        assertEquals("s-1", body["driver_service_id"])
        assertEquals("BUS-42", body["vehicle_id"])
        assertEquals(47.21, body["latitude"])
        assertEquals(-1.55, body["longitude"])
    }

    @Test
    fun `ils sont absents plutot que nuls quand on ne les connait pas`() {
        val body = DriverReport(type = DriverReportType.DELAY).toInsert(driverId = "d-1")
        assertFalse(body.containsKey("driver_service_id"))
        assertFalse(body.containsKey("vehicle_id"))
        assertTrue(body.containsKey("driver_id"))
    }
}
