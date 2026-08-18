package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Port de `Native/AuleTests/NearbyTests.swift`. */
class NearbyTest {

    private val center = Coordinate.NANTES

    private fun stop(id: String, place: String, offset: Double) = TransitStop(
        id = id,
        name = place,
        code = id,
        coordinate = Coordinate(latitude = center.latitude + offset, longitude = center.longitude),
        mode = TransportMode.TRAM,
        stationName = place,
        isWheelchairAccessible = true,
    )

    private fun vehicle(id: String, line: String, offset: Double) = TransportVehicle(
        id = id,
        mode = TransportMode.BUS,
        feed = VehicleFeed.LIVE,
        lineId = line,
        lineName = line,
        destination = "Quelque part",
        coordinate = Coordinate(latitude = center.latitude + offset, longitude = center.longitude),
    )

    @Test
    fun `un pole d echange compte pour un, pas pour sept`() {
        val quays = (1..7).map { stop("COMM$it", "Commerce", it * 0.0005) }
        val digest = NearbyDigestBuilder.build(stops = quays, vehicles = emptyList(), around = center)
        assertEquals(1, digest.stops.size)
        assertEquals("COMM1", digest.stops.first().stop.id)
    }

    @Test
    fun `les entrees sont ordonnees par distance`() {
        val digest = NearbyDigestBuilder.build(
            stops = listOf(
                stop("C", "Loin", 0.03),
                stop("A", "Pres", 0.001),
                stop("B", "Moyen", 0.01),
            ),
            vehicles = listOf(vehicle("v3", "3", 0.02), vehicle("v1", "1", 0.002)),
            around = center,
        )
        assertEquals(listOf("A", "B", "C"), digest.stops.map { it.stop.id })
        assertEquals(listOf("v1", "v3"), digest.vehicles.map { it.vehicle.id })
    }

    @Test
    fun `la liste est plafonnee`() {
        val many = (1..40).map { stop("S$it", "Lieu $it", it * 0.001) }
        val digest = NearbyDigestBuilder.build(stops = many, vehicles = emptyList(), around = center)
        assertEquals(NEARBY_LIMIT, digest.stops.size)
        assertEquals("S1", digest.stops.first().stop.id)
    }

    @Test
    fun `deux entrees a egale distance gardent le meme ordre`() {
        val tied = listOf(
            stop("Z", "Zed", 0.005),
            stop("A", "Alpha", 0.005),
            stop("M", "Mike", 0.005),
        )
        val first = NearbyDigestBuilder.build(stops = tied, vehicles = emptyList(), around = center)
        val second = NearbyDigestBuilder.build(stops = tied.reversed(), vehicles = emptyList(), around = center)
        assertEquals(first.stops.map { it.stop.id }, second.stops.map { it.stop.id })
        assertEquals(listOf("A", "M", "Z"), first.stops.map { it.stop.id })
    }

    @Test
    fun `une zone deserte se dit vide`() {
        assertTrue(NearbyDigestBuilder.build(emptyList(), emptyList(), center).isEmpty)
    }

    @Test
    fun `le temps de marche n est jamais nul`() {
        val here = NearbyDigest.StopEntry(stop("A", "Ici", 0.0), distanceMeters = 0.0)
        assertEquals(1, here.walkMinutes)
    }

    @Test
    fun `le temps de marche compte le detour, pas la corde`() {
        // 200 m à vol d'oiseau : 240 m de trottoir à 1,35 m/s, soit 2,96 min.
        val entry = NearbyDigest.StopEntry(stop("A", "Ranzay", 0.0), distanceMeters = 200.0)
        assertEquals(3, entry.walkMinutes)
    }

    @Test
    fun `le temps de marche arrondit vers le haut`() {
        // 210 m mènent à 3,11 min : on ne promet pas 3 minutes.
        val entry = NearbyDigest.StopEntry(stop("A", "Terray", 0.0), distanceMeters = 210.0)
        assertEquals(4, entry.walkMinutes)
    }

    @Test
    fun `le temps de marche croit avec la distance`() {
        val minutes = listOf(100.0, 500.0, 1000.0, 2000.0).map {
            NearbyDigest.StopEntry(stop("A", "Lieu", 0.0), distanceMeters = it).walkMinutes
        }
        assertEquals(minutes.sorted(), minutes)
        assertTrue(minutes.distinct().size == minutes.size)
    }

    @Test
    fun `la distance est celle du point de reference`() {
        val digest = NearbyDigestBuilder.build(
            stops = listOf(stop("A", "Ici", 0.0)),
            vehicles = emptyList(),
            around = center,
        )
        assertTrue(digest.stops.first().distanceMeters < 1.0)
    }
}
