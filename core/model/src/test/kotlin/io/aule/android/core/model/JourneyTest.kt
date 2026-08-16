package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Port de `SAE/test/journey_test.dart`. */
class JourneyTest {

    private fun lngAfter(fromLng: Double, meters: Double) =
        fromLng + meters / (111_320 * 0.6785)

    private fun segment(
        walk: Boolean,
        fromLng: Double,
        meters: Double,
        routeId: String? = null,
        color: String = "#00A754",
        departureAt: Instant? = null,
    ) = RouteSegment(
        coordinates = listOf(
            Coordinate(latitude = 47.2184, longitude = fromLng),
            Coordinate(latitude = 47.2184, longitude = lngAfter(fromLng, meters)),
        ),
        color = color,
        walk = walk,
        routeId = routeId,
        departureAt = departureAt,
    )

    private fun candidate(
        segments: List<RouteSegment> = emptyList(),
        steps: List<RouteStep> = emptyList(),
        distanceM: Int = 1000,
        durationMin: Int = 12,
        arrivalAt: Instant? = null,
        departureAt: Instant? = null,
        walk: Duration? = null,
        wait: Duration? = null,
        transfers: Int? = null,
    ) = RouteCandidate(
        id = "c",
        coordinates = listOf(
            Coordinate(latitude = 47.2184, longitude = -1.56),
            Coordinate(latitude = 47.2184, longitude = -1.55),
        ),
        segments = segments,
        distanceMeters = distanceM,
        durationMinutes = durationMin,
        steps = steps,
        summary = "",
        accessible = false,
        alertCount = 0,
        profiles = emptyList(),
        arrivalAt = arrivalAt,
        departureAt = departureAt,
        walk = walk,
        wait = wait,
        transfers = transfers,
    )

    private fun seconds(value: Double) =
        Duration.ofNanos((value * 1_000_000_000.0).toLong())

    private fun step(kind: RouteStepKind, label: String) =
        RouteStep(kind = kind, label = label, detail = "", duration = "")

    private fun troisJambes(): List<RouteSegment> {
        val start = -1.56
        val afterWalk = lngAfter(start, 250.0)
        val afterTram = lngAfter(afterWalk, 750.0)
        return listOf(
            segment(walk = true, fromLng = start, meters = 250.0),
            segment(walk = false, fromLng = afterWalk, meters = 750.0, routeId = "C1"),
            segment(walk = true, fromLng = afterTram, meters = 250.0),
        )
    }

    @Test
    fun `une jambe par troncon, dans l ordre du trajet`() {
        val plan = journeyFromCandidate(candidate(segments = troisJambes(), distanceM = 1250))!!
        assertEquals(listOf(LegMode.WALK, LegMode.TRANSIT, LegMode.WALK), plan.legs.map { it.mode })
        assertEquals("C1", plan.legs[1].line)
    }

    @Test
    fun `les frontieres couvrent le trajet sans trou`() {
        val plan = journeyFromCandidate(candidate(segments = troisJambes(), distanceM = 1250))!!
        assertEquals(0.0, plan.legs.first().startT, 1e-9)
        assertEquals(1.0, plan.legs.last().endT, 1e-9)
        for (i in 1 until plan.legs.size) {
            assertEquals(plan.legs[i - 1].endT, plan.legs[i].startT, 1e-9)
        }
    }

    @Test
    fun `les frontieres sont dans la metrique de la progression`() {
        val plan = journeyFromCandidate(candidate(segments = troisJambes(), distanceM = 1250))!!
        assertEquals(0.2, plan.legs[1].startT, 1e-3)
        assertEquals(0.8, plan.legs[1].endT, 1e-3)
    }

    @Test
    fun `une correspondance se lit a deux jambes en vehicule`() {
        val start = -1.56
        val a = lngAfter(start, 200.0)
        val b = lngAfter(a, 400.0)
        val withTransfer = journeyFromCandidate(
            candidate(
                distanceM = 900,
                segments = listOf(
                    segment(walk = true, fromLng = start, meters = 200.0),
                    segment(walk = false, fromLng = a, meters = 400.0, routeId = "C1"),
                    segment(walk = false, fromLng = b, meters = 300.0, routeId = "11"),
                ),
            ),
        )!!
        assertTrue(withTransfer.hasTransfer)
        assertFalse(
            journeyFromCandidate(candidate(segments = troisJambes(), distanceM = 1250))!!.hasTransfer,
        )
    }

    @Test
    fun `comptes justes - chaque jambe recoit sa phrase`() {
        val start = -1.56
        val plan = journeyFromCandidate(
            candidate(
                segments = listOf(
                    segment(walk = true, fromLng = start, meters = 300.0),
                    segment(walk = false, fromLng = lngAfter(start, 300.0), meters = 700.0, routeId = "C1"),
                ),
                steps = listOf(
                    step(RouteStepKind.WALK, "Marche jusqu'à l'arrêt Ranzay"),
                    step(RouteStepKind.TRAM, "C1 direction Gare de Chantenay"),
                ),
            ),
        )!!
        assertEquals("Marche jusqu'à l'arrêt Ranzay", plan.legs[0].title)
        assertEquals("C1 direction Gare de Chantenay", plan.legs[1].title)
    }

    @Test
    fun `un seul ecart de compte rend tous les libelles`() {
        val start = -1.56
        val plan = journeyFromCandidate(
            candidate(
                segments = listOf(
                    segment(walk = true, fromLng = start, meters = 300.0),
                    segment(walk = false, fromLng = lngAfter(start, 300.0), meters = 700.0, routeId = "C1"),
                ),
                steps = listOf(
                    step(RouteStepKind.WALK, "Marche jusqu'à l'arrêt Ranzay"),
                    step(RouteStepKind.WALK, "Traverser la place"),
                    step(RouteStepKind.TRAM, "C1 direction Gare de Chantenay"),
                ),
            ),
        )!!
        assertEquals("Marche", plan.legs[0].title)
        assertEquals("Ligne C1", plan.legs[1].title)
    }

    @Test
    fun `sans troncon, le candidat devient une jambe unique`() {
        val plan = journeyFromCandidate(
            candidate(
                distanceM = 4700,
                durationMin = 10,
                steps = listOf(step(RouteStepKind.CAR, "En voiture jusqu'au dépôt")),
            ),
            destinationLabel = "Dépôt",
        )!!
        assertEquals(1, plan.legs.size)
        assertEquals(LegMode.CAR, plan.legs.single().mode)
        assertEquals("En voiture jusqu'au dépôt", plan.legs.single().title)
        assertEquals(0.0, plan.legs.single().startT)
        assertEquals(1.0, plan.legs.single().endT)
    }

    @Test
    fun `une geometrie trop courte ne rend aucun plan`() {
        assertNull(
            journeyFromCandidate(
                RouteCandidate(
                    id = "x",
                    coordinates = emptyList(),
                    segments = emptyList(),
                    distanceMeters = 0,
                    durationMinutes = 1,
                    steps = emptyList(),
                    summary = "",
                    accessible = false,
                    alertCount = 0,
                    profiles = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun `le troncon qui porte l heure prime sur toute deduction`() {
        val start = -1.56
        val after = lngAfter(start, 542.0)
        val published = Instant.parse("2026-08-14T13:00:00Z")
        val plan = journeyFromCandidate(
            candidate(
                distanceM = 6541,
                departureAt = Instant.parse("2026-08-14T12:32:20Z"),
                walk = seconds(723.185),
                wait = Duration.ZERO,
                transfers = 0,
                segments = listOf(
                    segment(walk = true, fromLng = start, meters = 542.0),
                    segment(
                        walk = false,
                        fromLng = after,
                        meters = 5565.0,
                        routeId = "23",
                        departureAt = published,
                    ),
                ),
            ),
        )!!
        assertEquals(published, plan.legs[1].departureAt)
    }

    @Test
    fun `la deduction retombe sur la seconde — bus 23`() {
        val start = -1.56
        val after = lngAfter(start, 542.0)
        val fin = lngAfter(after, 5000.0)
        val plan = journeyFromCandidate(
            candidate(
                distanceM = 5976,
                departureAt = Instant.parse("2026-08-14T12:32:20Z"),
                walk = seconds(723.1851851851852),
                wait = Duration.ZERO,
                transfers = 0,
                segments = listOf(
                    segment(walk = true, fromLng = start, meters = 542.0),
                    segment(walk = false, fromLng = after, meters = 5000.0, routeId = "1"),
                    segment(walk = true, fromLng = fin, meters = 434.0),
                ),
            ),
        )!!
        val boarding = assertNotNull(plan.legs[1].departureAt)
        val announced = Instant.parse("2026-08-14T12:39:01Z")
        assertTrue(kotlin.math.abs(Duration.between(boarding, announced).seconds) <= 1)
        assertEquals(1.35, plan.walkSpeedMps!!, 0.02)
    }

    @Test
    fun `une correspondance laisse la jambe sans heure`() {
        val start = -1.56
        val a = lngAfter(start, 300.0)
        val b = lngAfter(a, 2000.0)
        val c = lngAfter(b, 200.0)
        val plan = journeyFromCandidate(
            candidate(
                distanceM = 4500,
                departureAt = Instant.parse("2026-08-14T12:30:00Z"),
                walk = seconds(400.0),
                wait = seconds(600.0),
                transfers = 1,
                segments = listOf(
                    segment(walk = true, fromLng = start, meters = 300.0),
                    segment(walk = false, fromLng = a, meters = 2000.0, routeId = "1"),
                    segment(walk = true, fromLng = b, meters = 200.0),
                    segment(walk = false, fromLng = c, meters = 2000.0, routeId = "C6"),
                ),
            ),
        )!!
        assertTrue(plan.legs.all { it.departureAt == null })
    }
}
