package io.aule.android.core.model

import io.aule.android.core.geo.GeoMath
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** Au-delà, la position ne décrit plus où est le véhicule : on l'affiche, on n'alerte pas. */
const val HANDOVER_STALE_FIX_SECONDS = 45

/** Rayon à quai au point de relève. */
const val HANDOVER_ARRIVED_METERS = 60.0

/** Rayon élargi, seulement si le véhicule est à l'arrêt. */
const val HANDOVER_AT_STOP_METERS = 120.0

/** En deçà, le véhicule est considéré arrêté (m/s). */
const val HANDOVER_STOPPED_SPEED_MPS = 1.5

/** Rayon d'annonce « le véhicule approche ». */
const val HANDOVER_APPROACH_METERS = 150.0

/** Marge ajoutée au temps de trajet pour le départ conseillé. */
const val HANDOVER_LEAVE_MARGIN_SECONDS = 2 * 60L

/**
 * L'état de la course relevée, vu depuis le conducteur qui attend.
 *
 * Avec une course GTFS calée, [pathMatched] veut dire : le véhicule a été
 * projeté sur le tracé (ou la polyligne arrêt→arrêt). Sans calage,
 * [measureHandoverProgress] retombe sur le décompte d'arrêts.
 */
data class HandoverProgress(
    val plannedAt: Instant,
    val fixAgeSeconds: Int,
    val pathMatched: Boolean,
    val arrived: Boolean,
    val approaching: Boolean,
    val passed: Boolean,
    val stopsRemaining: Int? = null,
    val metersRemaining: Double? = null,
    val estimatedAt: Instant? = null,
    val delay: Duration? = null,
) {
    val fresh: Boolean get() = fixAgeSeconds <= HANDOVER_STALE_FIX_SECONDS

    /** Position assez fraîche, et desserte reconnue, pour oser une alerte ou une ETA. */
    val reliable: Boolean get() = fresh && pathMatched

    fun minutesUntil(now: Instant): Int? {
        val eta = estimatedAt ?: return null
        val seconds = Duration.between(now, eta).seconds
        return if (seconds <= 0) 0 else ((seconds + 59) / 60).toInt()
    }

    /** Retard arrondi à la minute, tel qu'un conducteur l'énonce (« +1 »). */
    fun delayMinutes(): Int? {
        val value = delay ?: return null
        return ((value.seconds) / 60.0).roundToInt()
    }

    /**
     * Heure à laquelle partir pour être au point de relève à temps,
     * marge de deux minutes comprise. `null` sans ETA ou sans trajet.
     */
    fun leaveBy(travel: Duration?): Instant? {
        val eta = estimatedAt ?: return null
        val duration = travel ?: return null
        return eta.minus(duration).minusSeconds(HANDOVER_LEAVE_MARGIN_SECONDS)
    }
}

/**
 * Mesure d'approche à partir du point GPS et de la desserte, sans
 * projeter le véhicule sur un tracé.
 */
fun measureHandoverProgress(
    fix: HandoverFix,
    relief: LineJourneyStop,
    stops: List<LineJourneyStop>,
    now: Instant,
    timetableAt: Instant? = null,
): HandoverProgress {
    val meters = relief.coordinate?.let { GeoMath.distance(fix.coordinate, it) }
    val speed = fix.speed
    val stopped = speed != null && speed <= HANDOVER_STOPPED_SPEED_MPS
    val arrived = meters != null &&
        (meters <= HANDOVER_ARRIVED_METERS || (stopped && meters <= HANDOVER_AT_STOP_METERS))
    val approaching = meters != null && meters <= HANDOVER_APPROACH_METERS

    val reliefIndex = indexOfStop(stops, relief)
    val vehicleIndex = nearestStopIndex(stops, fix)
    val pathMatched = reliefIndex != null && vehicleIndex != null
    val passed = reliefIndex != null && vehicleIndex != null && vehicleIndex > reliefIndex
    val remaining = if (reliefIndex == null || vehicleIndex == null) {
        null
    } else if (vehicleIndex > reliefIndex) {
        0
    } else {
        reliefIndex - vehicleIndex
    }
    val estimatedAt = if (meters != null && speed != null && speed > HANDOVER_STOPPED_SPEED_MPS) {
        now.plusSeconds((meters / speed).toLong().coerceAtLeast(0L))
    } else {
        null
    }
    val plannedAt = timetableAt ?: now
    val delay = if (timetableAt != null && estimatedAt != null) {
        Duration.between(timetableAt, estimatedAt)
    } else {
        null
    }
    return HandoverProgress(
        plannedAt = plannedAt,
        fixAgeSeconds = fix.ageSeconds,
        pathMatched = pathMatched,
        arrived = arrived,
        approaching = approaching,
        passed = passed,
        stopsRemaining = remaining,
        metersRemaining = meters,
        estimatedAt = estimatedAt,
        delay = delay,
    )
}

private fun indexOfStop(stops: List<LineJourneyStop>, wanted: LineJourneyStop): Int? {
    val byId = stops.indexOfFirst { it.id == wanted.id }
    if (byId >= 0) return byId
    val name = normalizeStopName(wanted.name)
    if (name.isEmpty()) return null
    val byName = stops.indexOfFirst { normalizeStopName(it.name) == name }
    return byName.takeIf { it >= 0 }
}

private fun nearestStopIndex(stops: List<LineJourneyStop>, fix: HandoverFix): Int? {
    if (stops.isEmpty()) return null
    return stops.withIndex().minByOrNull { (_, stop) ->
        stop.coordinate?.let { GeoMath.distance(fix.coordinate, it) } ?: Double.POSITIVE_INFINITY
    }?.index
}
