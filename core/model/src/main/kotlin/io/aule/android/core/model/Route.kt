package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Version d'algorithme attendue. Elle voyage dans l'URL pour que le serveur
 * puisse invalider ses caches quand le contrat change.
 *
 * **28**, pas 27. La 28 correspond au découpage des jambes sur le tracé fin.
 * Envoyer 27 partagerait le cache du Flutter, donc son défaut de tracé.
 * Voir `docs/CONTRAT-BFF.md`.
 */
const val ROUTE_ALGORITHM_VERSION = "28"

/** Couleur d'un tronçon dont la ligne n'a pas de teinte déclarée. */
const val ROUTE_FALLBACK_COLOR = "#33BFA3"

/**
 * Distance en deçà de laquelle une ancre (arrêt de montée ou de descente)
 * est considérée comme **sur** le tracé, et on coupe plutôt que de raccorder.
 *
 * Port de `kRouteAnchorSnapM` : le défaut « le tracé ne commence pas à
 * Ranzay » venait d'un tronçon qui dépassait l'arrêt de 500 m.
 */
const val ROUTE_ANCHOR_SNAP_M = 150.0

enum class RouteMode { TRANSIT, CAR }

/**
 * Ce qu'une variante apporte de mieux que les autres, décidé par le moteur.
 *
 * Le libellé (« La plus rapide ») n'est pas ici : c'est une phrase, elle
 * vit dans les ressources (ADR-011).
 */
enum class RouteProfile {
    FASTEST,
    LEAST_WALK,
    LEAST_TRANSFERS,
    MOST_RELIABLE,
    ;

    companion object {
        fun fromApiValue(value: String?): RouteProfile? = when (value) {
            "fastest" -> FASTEST
            "least_walk" -> LEAST_WALK
            "least_transfers" -> LEAST_TRANSFERS
            "most_reliable" -> MOST_RELIABLE
            else -> null
        }
    }
}

/**
 * Fiabilité de la correspondance la plus tendue.
 *
 * `theoretical` se lit comme une absence : le panneau n'affiche aucun badge
 * plutôt qu'une assurance qu'il n'a pas.
 */
enum class RouteReliability {
    COMFORTABLE,
    TIGHT,
    RISKY,
    ;

    companion object {
        fun fromApiValue(value: String?): RouteReliability? = when (value) {
            "comfortable" -> COMFORTABLE
            "tight" -> TIGHT
            "risky" -> RISKY
            else -> null
        }
    }
}

/** Le signe d'une étape, nommé par son sens et non par son dessin. */
enum class RouteStepKind { WALK, CAR, TRAM, BUS, NAVIBUS }

/** Un point nommé : origine ou destination. */
data class RoutePlace(
    val coordinate: Coordinate,
    val label: String,
)

data class RoutePreferences(
    val accessible: Boolean = false,
    val avoidDisruptions: Boolean = true,
    val maxTransfers: Int = 2,
)

data class RouteStep(
    val kind: RouteStepKind,
    val label: String,
    val detail: String,
    val duration: String,
)

data class RouteSegment(
    val coordinates: List<Coordinate>,
    val color: String,
    val walk: Boolean,
    val routeId: String? = null,
    val departureAt: Instant? = null,
    val arrivalAt: Instant? = null,
)

data class RouteCandidate(
    val id: String,
    val coordinates: List<Coordinate>,
    val segments: List<RouteSegment>,
    val distanceMeters: Int,
    val durationMinutes: Int,
    val steps: List<RouteStep>,
    val summary: String,
    val accessible: Boolean,
    val alertCount: Int,
    val profiles: List<RouteProfile>,
    val departureAt: Instant? = null,
    val arrivalAt: Instant? = null,
    val reliability: RouteReliability? = null,
    val walk: Duration? = null,
    val wait: Duration? = null,
    val transfers: Int? = null,
) {
    /**
     * Les coordonnées réellement peintes — la même source que la couche,
     * pour que le cadrage ne puisse pas décrire autre chose que ce qui
     * est à l'écran.
     */
    val paintedCoordinates: List<Coordinate>
        get() = if (segments.isEmpty()) coordinates else segments.flatMap { it.coordinates }
}

data class RoutePlan(
    val alternatives: List<RouteCandidate>,
    val departures: List<RouteCandidate>,
    val selectedId: String,
    val timetable: Boolean,
) {
    fun selected(id: String? = selectedId): RouteCandidate? =
        alternatives.firstOrNull { it.id == id } ?: alternatives.firstOrNull()
}

/**
 * Paramètres de `GET /api/route`.
 *
 * `from` et `to` s'écrivent en **`lng,lat`** — [Coordinate.apiPair]. Inversés,
 * le serveur répond 404 « aucun arrêt à proximité » sans rien expliquer.
 */
object RouteApi {

    fun query(
        mode: RouteMode,
        from: Coordinate,
        to: Coordinate,
        preferences: RoutePreferences = RoutePreferences(),
        preview: Boolean = false,
        departureAt: Instant? = null,
        arriveBy: Boolean = false,
    ): Map<String, String> {
        val params = mutableMapOf(
            "v" to ROUTE_ALGORITHM_VERSION,
            "mode" to when (mode) {
                RouteMode.TRANSIT -> "transit"
                RouteMode.CAR -> "car"
            },
            "from" to from.apiPair,
            "to" to to.apiPair,
        )
        if (mode == RouteMode.TRANSIT) {
            params["accessible"] = if (preferences.accessible) "1" else "0"
            params["avoidDisruptions"] = if (preferences.avoidDisruptions) "1" else "0"
            params["maxTransfers"] = preferences.maxTransfers.toString()
            if (preview) params["preview"] = "1"
            if (departureAt != null) {
                params["departureAt"] = departureAt.toString()
                if (arriveBy) params["arriveBy"] = "1"
            }
        }
        return params
    }
}

fun routeStepKindFromId(raw: String?): RouteStepKind = when (raw) {
    "car" -> RouteStepKind.CAR
    "tram" -> RouteStepKind.TRAM
    "bus" -> RouteStepKind.BUS
    "navibus" -> RouteStepKind.NAVIBUS
    else -> RouteStepKind.WALK
}

fun durationMinutesFromSeconds(seconds: Double): Int =
    (seconds / 60.0).roundToInt().coerceAtLeast(1)

/**
 * Recolle les tronçons en véhicule sur les arrêts de montée et de descente.
 *
 * Le serveur a bien une parade, mais elle abandonne au-delà de 180 m : elle
 * ne rattrape donc jamais le cas relevé sur le terrain, où la jambe C6
 * commençait 533 m après l'arrêt. La marche voisine sert d'ancre : ses
 * extrémités **sont** les arrêts.
 *
 * Deux tronçons en véhicule qui se suivent ne s'ancrent pas l'un l'autre :
 * le second hériterait de l'erreur du premier.
 */
fun anchorTransitSegments(segments: List<RouteSegment>): List<RouteSegment> {
    if (segments.size < 2) return segments
    return segments.mapIndexed { index, segment ->
        if (segment.walk) return@mapIndexed segment
        val before = segments.getOrNull(index - 1)
        val after = segments.getOrNull(index + 1)
        val head = if (before != null && before.walk) before.coordinates.lastOrNull() else null
        val tail = if (after != null && after.walk) after.coordinates.firstOrNull() else null
        if (head == null && tail == null) return@mapIndexed segment
        segment.copy(coordinates = anchoredSegmentCoordinates(segment.coordinates, head, tail))
    }
}

/**
 * Une polyligne ramenée entre les deux points qu'on lui désigne.
 *
 * 1. l'ancre tombe **sur** le tracé (moins de [ROUTE_ANCHOR_SNAP_M]) : on
 *    coupe là, et ce qui dépassait s'en va ;
 * 2. l'ancre est **ailleurs** : on s'y raccorde par un trait droit.
 *
 * La tête ne se cherche que dans la première moitié, la queue que dans la
 * seconde : une ligne qui repasse près de son propre départ couperait sinon
 * presque tout le trajet.
 */
fun anchoredSegmentCoordinates(
    coordinates: List<Coordinate>,
    head: Coordinate?,
    tail: Coordinate?,
): List<Coordinate> {
    if (coordinates.size < 2 || (head == null && tail == null)) return coordinates
    val last = coordinates.lastIndex
    val middle = last / 2
    var first = 0
    var stop = last
    if (head != null) {
        val hit = projectOnPolyline(coordinates, head, 0, max(0, middle - 1))
        if (hit.meters <= ROUTE_ANCHOR_SNAP_M) first = hit.segment + 1
    }
    if (tail != null) {
        val hit = projectOnPolyline(coordinates, tail, min(middle, last - 1), last - 1)
        if (hit.meters <= ROUTE_ANCHOR_SNAP_M) stop = hit.segment
    }
    val result = ArrayList<Coordinate>(stop - first + 3)
    fun push(point: Coordinate) {
        val previous = result.lastOrNull()
        if (previous == null || !samePoint(previous, point)) result += point
    }
    push(head ?: coordinates.first())
    for (i in first..stop) push(coordinates[i])
    push(tail ?: coordinates.last())
    // Une coupe qui ne laisserait qu'un point ne se peint pas : mieux vaut
    // le tronçon d'origine, faux mais visible, qu'un trait disparu.
    return if (result.size >= 2) result else coordinates
}

data class GeoBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
)

/**
 * Boîte englobante. Une seule coordonnée non finie suffirait à étirer la
 * boîte jusqu'à l'infini, et le cadrage montrerait la planète entière.
 */
fun coordinatesBounds(points: List<Coordinate>): GeoBounds? {
    var west = Double.POSITIVE_INFINITY
    var south = Double.POSITIVE_INFINITY
    var east = Double.NEGATIVE_INFINITY
    var north = Double.NEGATIVE_INFINITY
    var any = false
    for (point in points) {
        if (!point.coordinateIsFinite()) continue
        any = true
        west = min(west, point.longitude)
        east = max(east, point.longitude)
        south = min(south, point.latitude)
        north = max(north, point.latitude)
    }
    return if (any) GeoBounds(west, south, east, north) else null
}

private data class ProjectionHit(val segment: Int, val meters: Double)

private fun projectOnPolyline(
    line: List<Coordinate>,
    point: Coordinate,
    firstSegment: Int,
    lastSegment: Int,
): ProjectionHit {
    var bestSegment = firstSegment
    var bestMeters = Double.POSITIVE_INFINITY
    for (i in firstSegment..lastSegment) {
        val meters = distanceToSegmentMeters(point, line[i], line[i + 1])
        if (meters < bestMeters) {
            bestMeters = meters
            bestSegment = i
        }
    }
    return ProjectionHit(bestSegment, bestMeters)
}

/**
 * Distance d'un point au segment, en mètres.
 *
 * La longitude est resserrée par le cosinus de la latitude avant de
 * raisonner en plan : à cette échelle, l'oublier ferait mesurer un tiers
 * de trop à Nantes.
 */
private fun distanceToSegmentMeters(point: Coordinate, a: Coordinate, b: Coordinate): Double {
    val metersPerDegree = 111_320.0
    val scale = cos(Math.toRadians(point.latitude))
    val px = (point.longitude - a.longitude) * scale
    val py = point.latitude - a.latitude
    val bx = (b.longitude - a.longitude) * scale
    val by = b.latitude - a.latitude
    val squared = bx * bx + by * by
    val t = if (squared == 0.0) 0.0 else ((px * bx + py * by) / squared).coerceIn(0.0, 1.0)
    val dx = px - bx * t
    val dy = py - by * t
    return sqrt(dx * dx + dy * dy) * metersPerDegree
}

private fun samePoint(a: Coordinate, b: Coordinate): Boolean =
    GeoMath.distance(a, b) < 0.01 ||
        (kotlin.math.abs(a.longitude - b.longitude) < 1e-7 &&
            kotlin.math.abs(a.latitude - b.latitude) < 1e-7)

private fun Coordinate.coordinateIsFinite(): Boolean =
    latitude.isFinite() && longitude.isFinite()
