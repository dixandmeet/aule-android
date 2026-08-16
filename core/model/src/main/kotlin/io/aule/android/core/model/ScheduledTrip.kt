package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.geo.PolylinePath
import io.aule.android.core.geo.PolylineProjection
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/** Écart au tracé au-delà duquel le véhicule ne roule plus la course attendue. */
const val HANDOVER_OFF_PATH_METERS = 120.0

/** Points hors tracé consécutifs avant de basculer en mode dégradé. */
const val HANDOVER_OFF_PATH_STRIKES = 3

/** Recul toléré sur la progression (GPS bruité). */
const val HANDOVER_BACKWARD_TOLERANCE = 0.01

/** Fenêtre d'avance admise entre deux points (boucles / branches). */
const val HANDOVER_FORWARD_WINDOW = 0.18

/**
 * Un arrêt d'une course GTFS, avec son heure de passage théorique.
 */
data class ScheduledTripStop(
    val stopId: String,
    val name: String,
    val coordinate: Coordinate?,
    val passageAt: Instant,
) {
    fun toJourneyStop(): LineJourneyStop = LineJourneyStop(
        id = stopId,
        name = name,
        coordinate = coordinate,
    )
}

/**
 * Tracé de la course et fraction (0..1) de chaque arrêt dessus.
 *
 * Sans shape publié, [fromStops] relie les arrêts en ligne droite — assez
 * pour un retard / ETA, moins précis qu'un tracé OSM.
 */
data class ScheduledTripPath(
    val points: List<Coordinate>,
    val stopFractions: List<Double>,
) {
    companion object {
        fun fromStops(stops: List<ScheduledTripStop>): ScheduledTripPath? {
            if (stops.size < 2) return null
            val points = stops.mapNotNull { it.coordinate }
            if (points.size != stops.size || points.size < 2) return null
            val path = PolylinePath(points)
            if (!path.isUsable) return null
            val fractions = DoubleArray(points.size)
            var travelled = 0.0
            fractions[0] = 0.0
            for (i in 1 until points.size) {
                travelled += GeoMath.distance(points[i - 1], points[i])
                fractions[i] = if (path.length <= 0) 0.0 else (travelled / path.length).coerceIn(0.0, 1.0)
            }
            fractions[fractions.lastIndex] = 1.0
            return ScheduledTripPath(points = points, stopFractions = fractions.toList())
        }
    }
}

/**
 * Course théorique du jour : desserte horodatée d'un départ GTFS.
 */
data class ScheduledTrip(
    val departureId: String,
    val lineId: String,
    val lineLabel: String,
    val directionId: Int,
    val destination: String,
    val stops: List<ScheduledTripStop>,
    val path: ScheduledTripPath? = ScheduledTripPath.fromStops(stops),
) {
    fun nextStopIndex(now: Instant): Int {
        for (i in stops.indices) {
            if (stops[i].passageAt.isAfter(now)) return i
        }
        return (stops.size - 1).coerceAtLeast(0)
    }

    fun stopsBefore(index: Int, now: Instant): Int? {
        if (index !in stops.indices) return null
        val remaining = index - nextStopIndex(now)
        return if (remaining < 0) null else remaining
    }
}

/**
 * Position théorique à [elapsedSeconds] après le départ, interpolée entre
 * les arrêts encadrants. Port de `StopScheduleService.positionAtElapsed`.
 */
fun positionAtElapsed(
    offsets: List<Int>,
    positions: List<Coordinate?>,
    elapsedSeconds: Int,
): Coordinate? {
    if (offsets.isEmpty() || offsets.size != positions.size) return null
    var previous: Coordinate? = null
    var previousOffset: Int? = null
    for (i in offsets.indices) {
        val position = positions[i] ?: continue
        if (offsets[i] >= elapsedSeconds) {
            val from = previous ?: return position
            val fromOffset = previousOffset ?: return position
            val total = offsets[i] - fromOffset
            if (total <= 0) return position
            val t = ((elapsedSeconds - fromOffset).toDouble() / total).coerceIn(0.0, 1.0)
            return GeoMath.interpolate(from, position, t)
        }
        previous = position
        previousOffset = offsets[i]
    }
    return previous
}

/**
 * Calcule retard, ETA et arrêts restants à chaque fix du collègue.
 *
 * Retard = maintenant − heure théorique à la progression projetée.
 * ETA = passage prévu à la relève + retard — juste même à l'arrêt.
 */
class HandoverProgressEngine(
    val trip: ScheduledTrip,
    val reliefStopIndex: Int,
) {
    init {
        require(reliefStopIndex >= 0)
    }

    private val totalMeters: Double = trip.path?.points?.let { points ->
        if (points.size < 2) 0.0
        else {
            var total = 0.0
            for (i in 0 until points.size - 1) {
                total += GeoMath.distance(points[i], points[i + 1])
            }
            total
        }
    } ?: 0.0

    private var lastProgress: Double? = null
    private var offPathStrikes: Int = 0

    val reliefStop: ScheduledTripStop?
        get() = trip.stops.getOrNull(reliefStopIndex)

    fun reset() {
        lastProgress = null
        offPathStrikes = 0
    }

    fun update(
        position: Coordinate,
        recordedAt: Instant,
        now: Instant,
        speedMps: Double?,
        fixAgeSeconds: Int,
    ): HandoverProgress? {
        val stop = reliefStop ?: return null
        val straightMeters = stop.coordinate?.let { GeoMath.distance(position, it) }
        val stopped = speedMps != null && speedMps <= HANDOVER_STOPPED_SPEED_MPS
        val arrived = straightMeters != null &&
            (straightMeters <= HANDOVER_ARRIVED_METERS ||
                (stopped && straightMeters <= HANDOVER_AT_STOP_METERS))
        val approaching = straightMeters != null && straightMeters <= HANDOVER_APPROACH_METERS

        val match = project(position)
        if (match == null) {
            return HandoverProgress(
                plannedAt = stop.passageAt,
                fixAgeSeconds = fixAgeSeconds,
                pathMatched = false,
                arrived = arrived,
                approaching = approaching,
                passed = false,
                metersRemaining = straightMeters,
                stopsRemaining = trip.stopsBefore(reliefStopIndex, now),
            )
        }

        val fractions = trip.path?.stopFractions.orEmpty()
        val progress = match.t
        lastProgress = progress
        val reliefFraction = fractions.getOrElse(reliefStopIndex) { 1.0 }
        val passed = progress > reliefFraction + 1e-6
        val delay = delayAt(progress, now)
        val estimatedAt = delay?.let { stop.passageAt.plus(it) }

        return HandoverProgress(
            plannedAt = stop.passageAt,
            fixAgeSeconds = fixAgeSeconds,
            pathMatched = true,
            arrived = arrived,
            approaching = approaching,
            passed = passed,
            stopsRemaining = if (passed) 0 else stopsRemaining(progress),
            metersRemaining = when {
                passed -> 0.0
                totalMeters > 0 -> (reliefFraction - progress) * totalMeters
                else -> straightMeters
            },
            estimatedAt = estimatedAt,
            delay = delay,
        )
    }

    private fun project(position: Coordinate): io.aule.android.core.geo.PolylineMatch? {
        val path = trip.path ?: return null
        if (path.points.size < 2) return null
        val last = lastProgress
        val match = PolylineProjection.project(
            position = position,
            onto = path.points,
            currentT = last,
            backWindow = HANDOVER_BACKWARD_TOLERANCE,
            forwardWindow = HANDOVER_FORWARD_WINDOW,
        ) ?: return null

        if (match.deviationMeters > HANDOVER_OFF_PATH_METERS) {
            offPathStrikes++
            if (offPathStrikes >= HANDOVER_OFF_PATH_STRIKES) return null
            return if (last == null) null else match
        }
        offPathStrikes = 0
        return match
    }

    private fun delayAt(progress: Double, now: Instant): Duration? {
        val fractions = trip.path?.stopFractions ?: return null
        val stops = trip.stops
        if (fractions.size != stops.size || stops.size < 2) return null
        if (progress <= fractions.first()) {
            return Duration.between(stops.first().passageAt, now)
        }
        if (progress >= fractions.last()) {
            return Duration.between(stops.last().passageAt, now)
        }
        for (i in 0 until fractions.size - 1) {
            val from = fractions[i]
            val to = fractions[i + 1]
            if (progress < from || progress > to) continue
            val span = to - from
            val theoretical = if (span <= 0) {
                stops[i].passageAt
            } else {
                val millis = Duration.between(stops[i].passageAt, stops[i + 1].passageAt)
                    .toMillis()
                val fraction = (progress - from) / span
                stops[i].passageAt.plusMillis((millis * fraction).roundToLong())
            }
            return Duration.between(theoretical, now)
        }
        return null
    }

    private fun stopsRemaining(progress: Double): Int? {
        val fractions = trip.path?.stopFractions ?: return null
        if (reliefStopIndex >= fractions.size) return null
        var next = fractions.indexOfFirst { it > progress }
        if (next < 0) next = fractions.lastIndex
        return (reliefStopIndex - next + 1).coerceAtLeast(0)
    }
}
