package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.PolylineProjection
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * Ce qu'on fait pendant une jambe — trois comportements, pas cinq modes.
 *
 * Un bus, un tram et un navibus se **naviguent** identiquement : on compte
 * des arrêts. Les distinguer ici obligerait chaque règle en aval à les
 * réunir de nouveau.
 *
 * Port de `SAE/lib/navigation/journey.dart`.
 */
enum class LegMode {
    /** À pied. Manœuvres piétonnes, distance en mètres. */
    WALK,
    /** Au volant. Manœuvres routières. */
    CAR,
    /** Dans un véhicule qu'on ne conduit pas. On compte des arrêts. */
    TRANSIT,
}

data class JourneyLeg(
    val mode: LegMode,
    val title: String,
    val startT: Double,
    val endT: Double,
    val distanceMeters: Double,
    val startIndex: Int = 0,
    val endIndex: Int = 0,
    val vehicle: TransportMode? = null,
    val line: String? = null,
    val lineColor: String? = null,
    val stopCount: Int? = null,
    val duration: Duration? = null,
    val departureAt: Instant? = null,
    val arrivalAt: Instant? = null,
    val maneuvers: List<RoadManeuver> = emptyList(),
) {
    /** Vrai quand des manœuvres de voirie ont un sens. Un tram ne « tourne » pas. */
    val isRoad: Boolean get() = mode == LegMode.WALK || mode == LegMode.CAR

    val spanT: Double get() = if (endT > startT) endT - startT else 0.0
}

data class JourneyPlan(
    val points: List<Coordinate>,
    val legs: List<JourneyLeg>,
    val distanceMeters: Double,
    val destinationLabel: String? = null,
    val arrivalAt: Instant? = null,
    val duration: Duration? = null,
    val walkSpeedMps: Double? = null,
) {
    val isEmpty: Boolean get() = points.size < 2 || legs.isEmpty()

    /** Au moins deux jambes en véhicule — donc au moins une correspondance. */
    val hasTransfer: Boolean get() = legs.count { it.mode == LegMode.TRANSIT } > 1
}

/**
 * Ce qu'on fait pendant une jambe unique — le trajet porte-à-porte.
 *
 * ## ⚠️ La demande prime sur la réponse, sauf là où la demande ne dit rien
 *
 * « Voiture » et « à pied » désignent chacun un comportement, et le moteur ne
 * peut pas les contredire : `mode=foot` et `mode=car` rendent la **même forme**
 * — une géométrie, une distance, une durée, des manœuvres — sans un mot pour
 * les distinguer, ni `segments` ni `steps` (`docs/CONTRAT-BFF.md` §10, et
 * `dashboard/app/api/route/route.ts`, chemin non-transit). Lire ses `steps`,
 * comme on le faisait, revenait donc à interroger une information qu'il
 * n'envoie pas : la liste étant toujours vide, tout trajet en voiture
 * retombait sur [LegMode.WALK].
 *
 * **[RouteMode.TRANSIT] est le seul mode qui laisse le moteur trancher**, et
 * c'est légitime : une demande de transports qui revient sans le moindre
 * tronçon est une demande à laquelle il a répondu autrement — par une marche,
 * ou par une voirie s'il l'a jugée seule praticable. Là, sa parole est la
 * seule qu'on ait.
 *
 * Port de `Native/Aule/Models/Journey.swift`, `doorToDoorLegMode`.
 */
fun RouteMode.doorToDoorLegMode(steps: List<RouteStep>): LegMode = when (this) {
    RouteMode.CAR -> LegMode.CAR
    RouteMode.WALK -> LegMode.WALK
    RouteMode.TRANSIT -> if (steps.firstOrNull()?.kind == RouteStepKind.CAR) {
        LegMode.CAR
    } else {
        LegMode.WALK
    }
}

/**
 * Le trajet que décrit un candidat de `/api/route`.
 *
 * Rend `null` quand la géométrie ne vaut rien. Un plan vide serait pire
 * qu'aucun plan : l'écran afficherait une barre de résumé sur un trajet
 * qui n'existe pas.
 *
 * [mode] est le mode **demandé**, et il n'a pas de défaut : sans tronçons, la
 * réponse ne dit pas comment on avance, et c'est lui seul qui le sait — voir
 * [doorToDoorLegMode].
 *
 * ⚠️ La structure vient de `segments`, seuls. Les `steps` n'habillent une
 * jambe que si l'appariement se **vérifie** — un écart d'une entrée rend
 * tous les libellés.
 */
fun journeyFromCandidate(
    candidate: RouteCandidate,
    mode: RouteMode,
    destinationLabel: String? = null,
): JourneyPlan? {
    val segments = candidate.segments
    val totalMeters = candidate.distanceMeters.toDouble()
    val points = mutableListOf<Coordinate>()
    val bounds = mutableListOf<IntRange?>()

    fun push(raw: List<Coordinate>) {
        raw.forEach { coordinate ->
            if (coordinate.latitude.isFinite() && coordinate.longitude.isFinite()) {
                points += coordinate
            }
        }
    }

    if (segments.isEmpty()) {
        push(candidate.coordinates)
    } else {
        for (segment in segments) {
            val from = points.size
            push(segment.coordinates)
            val to = points.lastIndex
            bounds += if (to - from >= 1) from..to else null
        }
    }
    if (points.size < 2) return null

    if (segments.isEmpty()) {
        val legMode = mode.doorToDoorLegMode(candidate.steps)
        val title = candidate.steps.firstOrNull()?.label
            ?: if (destinationLabel == null) "Continuer" else "Jusqu'à $destinationLabel"
        return JourneyPlan(
            points = points,
            distanceMeters = totalMeters,
            destinationLabel = destinationLabel,
            arrivalAt = candidate.arrivalAt,
            duration = Duration.ofMinutes(candidate.durationMinutes.toLong()),
            legs = listOf(
                JourneyLeg(
                    mode = legMode,
                    title = title,
                    startT = 0.0,
                    endT = 1.0,
                    startIndex = 0,
                    endIndex = points.lastIndex,
                    distanceMeters = totalMeters,
                    duration = Duration.ofMinutes(candidate.durationMinutes.toLong()),
                    departureAt = candidate.departureAt,
                    arrivalAt = candidate.arrivalAt,
                ),
            ),
        )
    }

    val titles = titlesFor(candidate, bounds)
    val legs = mutableListOf<JourneyLeg>()
    val boarding = boardingInstant(candidate, points, bounds)

    for (index in segments.indices) {
        val span = bounds[index] ?: continue
        val segment = segments[index]
        val startT = PolylineProjection.tAtIndex(points, span.first)
        val endT = PolylineProjection.tAtIndex(points, span.last)
        val line = segment.routeId?.trim()?.takeIf { it.isNotEmpty() }
        val legMode = if (segment.walk) LegMode.WALK else LegMode.TRANSIT
        val deduced = if (legMode == LegMode.TRANSIT && index == boarding?.segmentIndex) {
            boarding.at
        } else {
            null
        }
        legs += JourneyLeg(
            mode = legMode,
            title = titles[index] ?: fallbackTitle(legMode, line, destinationLabel),
            startT = startT,
            endT = endT,
            startIndex = span.first,
            endIndex = span.last,
            distanceMeters = totalMeters * (endT - startT).coerceIn(0.0, 1.0),
            line = line,
            lineColor = segment.color.trim().takeIf { it.isNotEmpty() },
            departureAt = segment.departureAt ?: deduced,
            arrivalAt = segment.arrivalAt,
        )
    }
    if (legs.isEmpty()) return null
    return JourneyPlan(
        points = points,
        legs = legs,
        distanceMeters = totalMeters,
        destinationLabel = destinationLabel,
        arrivalAt = candidate.arrivalAt,
        duration = Duration.ofMinutes(candidate.durationMinutes.toLong()),
        walkSpeedMps = walkSpeed(candidate, legs),
    )
}

private fun walkSpeed(candidate: RouteCandidate, legs: List<JourneyLeg>): Double? {
    val seconds = candidate.walk ?: return null
    if (seconds <= Duration.ZERO) return null
    val meters = legs.filter { it.mode == LegMode.WALK }.sumOf { it.distanceMeters }
    if (!meters.isFinite() || meters <= 0) return null
    val speed = meters / (seconds.toNanos() / 1_000_000_000.0)
    return if (speed.isFinite() && speed > 0.3 && speed < 4.0) speed else null
}

private data class Boarding(val segmentIndex: Int, val at: Instant)

/**
 * Quand part le véhicule qu'on va prendre — déduit du budget du candidat.
 *
 * `embarquement = departureAt + part de marche qui précède × walk + wait`
 *
 * Un seul véhicule, et les deux lectures (`transfers` et le décompte des
 * tronçons) doivent tomber d'accord. Sinon on se tait.
 */
private fun boardingInstant(
    candidate: RouteCandidate,
    points: List<Coordinate>,
    bounds: List<IntRange?>,
): Boarding? {
    val departure = candidate.departureAt ?: return null
    val walk = candidate.walk ?: return null
    if (walk <= Duration.ZERO) return null
    if (candidate.transfers != null && candidate.transfers != 0) return null

    val segments = candidate.segments
    var boardingIndex = -1
    var rides = 0
    for (i in segments.indices) {
        if (bounds[i] == null || segments[i].walk) continue
        rides++
        if (boardingIndex < 0) boardingIndex = i
    }
    if (rides != 1) return null

    var before = 0.0
    var total = 0.0
    for (i in segments.indices) {
        val span = bounds[i] ?: continue
        if (!segments[i].walk) continue
        val length = PolylineProjection.tAtIndex(points, span.last) -
            PolylineProjection.tAtIndex(points, span.first)
        if (!length.isFinite() || length <= 0) continue
        total += length
        if (i < boardingIndex) before += length
    }
    val approach = if (total <= 0) {
        Duration.ZERO
    } else {
        Duration.ofNanos((walk.toNanos() * (before / total)).roundToLong())
    }
    return Boarding(boardingIndex, departure.plus(approach).plus(candidate.wait ?: Duration.ZERO))
}

/**
 * Les libellés du moteur, appariés seulement si l'appariement se vérifie.
 *
 * Un écart d'une seule entrée suffit à tout rendre : mieux vaut une jambe
 * décrite par un repli générique qu'une jambe décrite par la phrase de sa
 * voisine.
 */
private fun titlesFor(candidate: RouteCandidate, bounds: List<IntRange?>): List<String?> {
    val segments = candidate.segments
    val blank = MutableList<String?>(segments.size) { null }
    if (candidate.steps.isEmpty()) return blank

    val walkSteps = candidate.steps.filter { it.kind == RouteStepKind.WALK }
    val rideSteps = candidate.steps.filter { it.kind != RouteStepKind.WALK }

    val walkAt = mutableListOf<Int>()
    val rideAt = mutableListOf<Int>()
    for (i in segments.indices) {
        if (bounds[i] == null) continue
        if (segments[i].walk) walkAt += i else rideAt += i
    }
    if (walkAt.size != walkSteps.size || rideAt.size != rideSteps.size) return blank
    walkAt.forEachIndexed { i, at -> blank[at] = walkSteps[i].label }
    rideAt.forEachIndexed { i, at -> blank[at] = rideSteps[i].label }
    return blank
}

private fun fallbackTitle(mode: LegMode, line: String?, destination: String?): String = when (mode) {
    LegMode.WALK -> if (destination == null) "Marche" else "Marche jusqu'à $destination"
    LegMode.CAR -> if (destination == null) "En voiture" else "En voiture jusqu'à $destination"
    LegMode.TRANSIT -> if (line.isNullOrEmpty()) "En transport" else "Ligne $line"
}
