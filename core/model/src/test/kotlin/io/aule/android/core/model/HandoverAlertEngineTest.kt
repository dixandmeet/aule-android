package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class HandoverAlertEngineTest {

    @Test
    fun `un seuil demande deux mesures consecutives`() {
        val engine = engine()
        val under = progress(stopsRemaining = 2)
        assertTrue(engine.evaluate(under, NOW).isEmpty())
        val second = engine.evaluate(under, NOW)
        assertEquals(listOf(HandoverAlertKind.STOPS_BEFORE), second.map { it.kind })
    }

    @Test
    fun `un seuil ne se declenche qu une fois`() {
        val engine = engine()
        val under = progress(stopsRemaining = 1)
        engine.evaluate(under, NOW)
        engine.evaluate(under, NOW)
        assertTrue(engine.hasFired(HandoverAlertKind.STOPS_BEFORE))
        repeat(5) {
            assertTrue(
                engine.evaluate(under, NOW).none { it.kind == HandoverAlertKind.STOPS_BEFORE },
            )
        }
    }

    @Test
    fun `une oscillation autour du seuil ne declenche pas en rafale`() {
        val engine = engine(HandoverAlertPrefs(minutesBefore = 5))
        val under = progress(estimatedAt = NOW.plusSeconds(4 * 60))
        val over = progress(estimatedAt = NOW.plusSeconds(7 * 60))
        var fired = 0
        repeat(6) { index ->
            fired += engine.evaluate(if (index % 2 == 0) under else over, NOW)
                .count { it.kind == HandoverAlertKind.MINUTES_BEFORE }
        }
        assertEquals(0, fired)
        engine.evaluate(under, NOW)
        val alerts = engine.evaluate(under, NOW)
        assertEquals(listOf(HandoverAlertKind.MINUTES_BEFORE), alerts.map { it.kind })
    }

    @Test
    fun `rien ne part sur une position perimee`() {
        val engine = engine()
        val stale = progress(
            stopsRemaining = 1,
            arrived = true,
            approaching = true,
            estimatedAt = NOW,
            fixAgeSeconds = 180,
        )
        repeat(5) {
            assertTrue(engine.evaluate(stale, NOW).isEmpty())
        }
    }

    @Test
    fun `l arrivee ne fait pas attendre une seconde mesure`() {
        val engine = engine()
        val alerts = engine.evaluate(progress(arrived = true), NOW)
        assertEquals(listOf(HandoverAlertKind.ARRIVED), alerts.map { it.kind })
    }

    @Test
    fun `un seuil desactive ne declenche jamais`() {
        val engine = engine(
            HandoverAlertPrefs(stopsBefore = null, minutesBefore = null, onArrival = false),
        )
        val under = progress(stopsRemaining = 0, estimatedAt = NOW, arrived = true)
        repeat(4) {
            assertTrue(engine.evaluate(under, NOW).isEmpty())
        }
    }

    @Test
    fun `sans trace reconnu arrets et minutes se taisent mais pas l arrivee`() {
        val engine = engine()
        val degraded = progress(
            stopsRemaining = 1,
            estimatedAt = NOW,
            pathMatched = false,
            arrived = true,
        )
        val alerts = engine.evaluate(degraded, NOW)
        assertEquals(listOf(HandoverAlertKind.ARRIVED), alerts.map { it.kind })
    }

    @Test
    fun `le point de releve depasse ne declenche pas les seuils d approche`() {
        val engine = engine()
        val past = progress(stopsRemaining = 0, estimatedAt = NOW, passed = true)
        engine.evaluate(past, NOW)
        assertTrue(engine.evaluate(past, NOW).isEmpty())
    }

    @Test
    fun `relever un seuil le rearme`() {
        val engine = engine(HandoverAlertPrefs(stopsBefore = 1))
        val at1 = progress(stopsRemaining = 1)
        val at2 = progress(stopsRemaining = 2)
        engine.evaluate(at1, NOW)
        engine.evaluate(at1, NOW)
        assertTrue(engine.hasFired(HandoverAlertKind.STOPS_BEFORE))
        engine.prefs = HandoverAlertPrefs(stopsBefore = 3)
        engine.evaluate(at2, NOW)
        val alerts = engine.evaluate(at2, NOW)
        assertEquals(listOf(HandoverAlertKind.STOPS_BEFORE), alerts.map { it.kind })
    }

    @Test
    fun `changer de point de releve remet tous les loquets`() {
        val engine = engine()
        val under = progress(stopsRemaining = 1, arrived = true)
        engine.evaluate(under, NOW)
        engine.evaluate(under, NOW)
        assertTrue(engine.hasFired(HandoverAlertKind.ARRIVED))
        engine.reset()
        assertFalse(engine.hasFired(HandoverAlertKind.ARRIVED))
        assertTrue(engine.evaluate(under, NOW).any { it.kind == HandoverAlertKind.ARRIVED })
    }

    @Test
    fun `un json vide retrouve les defauts`() {
        assertEquals(2, HandoverAlertPrefs.decode(null).stopsBefore)
        assertEquals(5, HandoverAlertPrefs.decode("").minutesBefore)
    }

    private fun engine(prefs: HandoverAlertPrefs = HandoverAlertPrefs()) =
        HandoverAlertEngine(prefs = prefs)

    private fun progress(
        stopsRemaining: Int? = null,
        estimatedAt: Instant? = null,
        pathMatched: Boolean = true,
        arrived: Boolean = false,
        approaching: Boolean = false,
        passed: Boolean = false,
        fixAgeSeconds: Int = 3,
    ) = HandoverProgress(
        plannedAt = NOW.plusSeconds(8 * 60),
        fixAgeSeconds = fixAgeSeconds,
        pathMatched = pathMatched,
        arrived = arrived,
        approaching = approaching,
        passed = passed,
        stopsRemaining = stopsRemaining,
        estimatedAt = estimatedAt,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-28T17:30:00Z")
    }
}

class HandoverProgressTest {

    @Test
    fun `les arrets restants se comptent sur la desserte`() {
        val progress = measureHandoverProgress(
            fix = fix(COMMERCE),
            relief = STOPS.last(),
            stops = STOPS,
            now = NOW,
        )
        assertEquals(1, progress.stopsRemaining)
        assertTrue(progress.pathMatched)
        assertTrue(progress.reliable)
        assertFalse(progress.arrived)
        assertFalse(progress.passed)
    }

    @Test
    fun `un collegue deja au-dela du point de releve a passe`() {
        val progress = measureHandoverProgress(
            fix = fix(CHANTRERIE),
            relief = STOPS[1],
            stops = STOPS,
            now = NOW,
        )
        assertTrue(progress.passed)
        assertEquals(0, progress.stopsRemaining)
    }

    @Test
    fun `une position perimee n est plus fiable`() {
        val progress = measureHandoverProgress(
            fix = fix(COMMERCE, ageSeconds = 90),
            relief = STOPS.last(),
            stops = STOPS,
            now = NOW,
        )
        assertFalse(progress.fresh)
        assertFalse(progress.reliable)
    }

    @Test
    fun `le depart conseille recule de la duree du trajet et de la marge`() {
        val eta = NOW.plusSeconds(12 * 60)
        val progress = HandoverProgress(
            plannedAt = NOW,
            fixAgeSeconds = 3,
            pathMatched = true,
            arrived = false,
            approaching = false,
            passed = false,
            estimatedAt = eta,
        )
        assertEquals(
            eta.minusSeconds(3 * 60).minusSeconds(HANDOVER_LEAVE_MARGIN_SECONDS),
            progress.leaveBy(Duration.ofMinutes(3)),
        )
        assertNull(progress.leaveBy(null))
        assertNull(progress.copy(estimatedAt = null).leaveBy(Duration.ofMinutes(3)))
    }

    @Test
    fun `a moins de soixante metres le vehicule est arrive`() {
        val atStop = Coordinate(47.28, -1.5201)
        val progress = measureHandoverProgress(
            fix = fix(atStop, speed = 0.4),
            relief = STOPS.last(),
            stops = STOPS,
            now = NOW,
        )
        assertTrue(progress.arrived)
        assertTrue(progress.approaching)
        assertEquals(0, progress.stopsRemaining)
    }

    @Test
    fun `une eta n existe que si le vehicule roule`() {
        val moving = measureHandoverProgress(
            fix = fix(COMMERCE, speed = 8.0),
            relief = STOPS.last(),
            stops = STOPS,
            now = NOW,
        )
        assertTrue(moving.estimatedAt != null)
        val stopped = measureHandoverProgress(
            fix = fix(COMMERCE, speed = 0.5),
            relief = STOPS.last(),
            stops = STOPS,
            now = NOW,
        )
        assertEquals(null, stopped.estimatedAt)
    }

    @Test
    fun `un horaire de passage donne le retard sur l ETA`() {
        val timetable = NOW.plusSeconds(8 * 60)
        val progress = measureHandoverProgress(
            fix = fix(COMMERCE, speed = 8.0),
            relief = STOPS.last(),
            stops = STOPS,
            now = NOW,
            timetableAt = timetable,
        )
        assertEquals(timetable, progress.plannedAt)
        assertTrue(progress.delay != null)
    }

    private fun fix(at: Coordinate, speed: Double? = 8.0, ageSeconds: Int = 4) = HandoverFix(
        coordinate = at,
        recordedAt = NOW,
        ageSeconds = ageSeconds,
        speed = speed,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-16T16:00:00Z")
        val HERMELAND = Coordinate(47.29, -1.52)
        val COMMERCE = Coordinate(47.2134, -1.558)
        val CHANTRERIE = Coordinate(47.28, -1.52)
        val STOPS = listOf(
            LineJourneyStop("1", "Hermeland", HERMELAND),
            LineJourneyStop("2", "Commerce", COMMERCE),
            LineJourneyStop("3", "Chantrerie", CHANTRERIE),
        )
    }
}
