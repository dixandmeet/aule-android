package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Port de `SAE/test/trip_summary_test.dart`. */
class TripSummaryTest {

    private val now = Instant.parse("2026-08-14T09:25:00Z")
    private val points = listOf(
        Coordinate(latitude = 47.2184, longitude = -1.56),
        Coordinate(latitude = 47.2184, longitude = -1.55),
    )

    private fun leg(mode: LegMode, startT: Double, endT: Double, meters: Double, line: String? = null) =
        JourneyLeg(mode = mode, title = "jambe", startT = startT, endT = endT, distanceMeters = meters, line = line)

    private fun seule(mode: LegMode, meters: Double = 4700.0, arrivalAt: Instant? = null, duration: Duration? = null) =
        JourneyPlan(
            points = points,
            distanceMeters = meters,
            arrivalAt = arrivalAt,
            duration = duration,
            legs = listOf(leg(mode, 0.0, 1.0, meters)),
        )

    private fun multimodal(arrivalAt: Instant? = null) = JourneyPlan(
        points = points,
        distanceMeters = 1250.0,
        arrivalAt = arrivalAt,
        legs = listOf(
            leg(LegMode.WALK, 0.0, 0.2, 250.0),
            leg(LegMode.TRANSIT, 0.2, 0.8, 750.0, "C1"),
            leg(LegMode.WALK, 0.8, 1.0, 250.0),
        ),
    )

    private fun inputs(plan: JourneyPlan, t: Double, stopsRemaining: Int? = null, untilTransfer: Duration? = null) =
        SummaryInputs(
            plan = plan,
            progress = journeyProgressAt(plan, t)!!,
            stopsRemaining = stopsRemaining,
            untilTransfer = untilTransfer,
        )

    @Test
    fun `voiture - une distance`() {
        val metric = resolveSummaryMetric(inputs(seule(LegMode.CAR), 0.0))
        assertEquals(SummaryMetricKind.DISTANCE, metric.kind)
        assertEquals(4700.0, metric.meters!!, 1e-6)
    }

    @Test
    fun `bus tram bateau - des arrets`() {
        val metric = resolveSummaryMetric(inputs(multimodal(), 0.5, stopsRemaining = 5))
        assertEquals(SummaryMetricKind.STOPS, metric.kind)
        assertEquals(5, metric.stops)
    }

    @Test
    fun `multimodal au depart - des etapes`() {
        val metric = resolveSummaryMetric(inputs(multimodal(), 0.0))
        assertEquals(SummaryMetricKind.LEGS, metric.kind)
        assertEquals(3, metric.legs)
    }

    @Test
    fun `ajouter un mode en queue ne prend jamais la place d un autre`() {
        val extra: SummaryMetricResolver = { inputs ->
            if (inputs.leg.mode == LegMode.WALK) null
            else SummaryMetric(kind = SummaryMetricKind.STOPS, stops = 3)
        }
        val extended = SUMMARY_METRIC_RESOLVERS + extra
        assertEquals(SummaryMetricKind.LEGS, resolveSummaryMetric(inputs(multimodal(), 0.0), extended).kind)
        assertEquals(SummaryMetricKind.STOPS, resolveSummaryMetric(inputs(multimodal(), 0.5, 5), extended).kind)
        assertEquals(SummaryMetricKind.DISTANCE, resolveSummaryMetric(inputs(multimodal(), 0.99), extended).kind)
    }

    @Test
    fun `une duree mesuree prime sur tout`() {
        val s = tripSummary(
            plan = seule(LegMode.CAR, arrivalAt = Instant.parse("2026-08-14T10:00:00Z"), duration = Duration.ofMinutes(40)),
            progress = journeyProgressAt(seule(LegMode.CAR), 0.5)!!,
            now = now,
            remainingOverride = Duration.ofMinutes(10),
        )
        assertEquals(Duration.ofMinutes(10), s.remaining)
        assertEquals(now.plus(Duration.ofMinutes(10)), s.arrivalAt)
        assertFalse(s.estimated)
    }

    @Test
    fun `a defaut, une regle de trois — et elle se dit`() {
        val plan = seule(LegMode.CAR, duration = Duration.ofMinutes(20))
        val s = tripSummary(plan, journeyProgressAt(plan, 0.25)!!, now)
        assertEquals(Duration.ofMinutes(15), s.remaining)
        assertTrue(s.estimated)
    }

    @Test
    fun `sans rien, on n invente pas`() {
        val plan = seule(LegMode.TRANSIT)
        val s = tripSummary(plan, journeyProgressAt(plan, 0.5)!!, now)
        assertNull(s.arrivalAt)
        assertNull(s.remaining)
        assertEquals(SummaryMetricKind.DISTANCE, s.third.kind)
    }
    /**
     * Un cadran qui disparaît au feu rouge est un cadran cassé : contrairement
     * à la fiche d'un bus, on rend zéro et non `null`.
     */
    @Test
    fun `le cadran affiche zero a l arret plutot que de disparaitre`() {
        assertEquals(0, drivingSpeedKmh(0.0))
        assertEquals(0, drivingSpeedKmh(0.4), "sous le seuil, le GPS mesure sa dérive")
    }

    @Test
    fun `le cadran arrondit au kilometre-heure`() {
        assertEquals(50, drivingSpeedKmh(13.89))
        assertEquals(30, drivingSpeedKmh(8.33))
        assertEquals(3, drivingSpeedKmh(0.8))
    }

    @Test
    fun `une vitesse aberrante ne fait pas dérailler le cadran`() {
        assertEquals(0, drivingSpeedKmh(Double.NaN))
        assertEquals(0, drivingSpeedKmh(-4.0))
    }
}
