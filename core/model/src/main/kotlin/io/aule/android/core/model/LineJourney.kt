package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import java.time.Instant

/**
 * La desserte d'une ligne dans un sens, pour le repli de relève.
 *
 * Ce n'est pas un trajet guidé : on n'a besoin que des arrêts, dans l'ordre,
 * pour proposer un point de relève quand personne n'est connecté.
 */
data class LineJourney(
    val tripId: String,
    val stops: List<LineJourneyStop>,
)

data class LineJourneyStop(
    val id: String,
    val name: String,
    val coordinate: Coordinate? = null,
)

data class FallbackPassages(
    val passages: List<StopDeparture> = emptyList(),
    val showsAllDirections: Boolean = false,
)

/**
 * Un conducteur qui relève choisit d'abord un arrêt qu'il peut rejoindre :
 * l'ordre de la desserte ne lui apprend rien, la distance si. Sans position,
 * on garde l'ordre de la ligne.
 */
fun fallbackStopsByProximity(
    stops: List<LineJourneyStop>,
    around: Coordinate?,
): List<LineJourneyStop> {
    if (around == null) return stops
    return stops.sortedWith(
        compareBy<LineJourneyStop> { stop ->
            stop.coordinate?.let { GeoMath.distance(around, it) } ?: Double.POSITIVE_INFINITY
        }.thenBy { it.name },
    )
}

/**
 * Arrêts encore devant le véhicule, pour le point de relève.
 *
 * Le plus proche du collègue est le premier encore servi ; tout ce qui
 * précède est déjà derrière. Sans position, on garde la desserte entière.
 * Le terminus (dernier) est le point de relève le plus courant.
 */
fun remainingReliefStops(
    stops: List<LineJourneyStop>,
    vehicle: Coordinate?,
): List<LineJourneyStop> {
    if (stops.isEmpty()) return emptyList()
    if (vehicle == null) return stops
    val nearest = stops.withIndex().minByOrNull { (_, stop) ->
        stop.coordinate?.let { GeoMath.distance(vehicle, it) } ?: Double.POSITIVE_INFINITY
    }?.index ?: 0
    return stops.drop(nearest)
}

/** Un nom par lieu : deux quais « Commerce » ne se choisissent pas deux fois. */
fun LineJourney.distinctStops(): List<LineJourneyStop> {
    val seen = mutableSetOf<String>()
    return stops.filter { stop ->
        val key = normalizeStopName(stop.name).ifEmpty { stop.id }
        seen.add(key)
    }
}

/**
 * Passages à proposer après le choix d'un arrêt.
 *
 * Même ligne, même terminus. À un terminus, aucun départ ne porte le
 * terminus choisi : on montre alors les deux sens, et on le dit.
 */
fun selectFallbackPassages(
    lineLabel: String,
    terminus: String,
    serving: List<ServingLine>,
    departures: List<StopDeparture>,
    limit: Int = 6,
): FallbackPassages {
    val wanted = wantedTermini(terminus)
    val sameLine = serving.filter { it.line.equals(lineLabel, ignoreCase = true) }
    val matching = if (wanted.isEmpty()) {
        sameLine
    } else {
        sameLine.filter { normalizeStopName(it.direction) in wanted }
    }
    val showsAll = matching.isEmpty() && sameLine.isNotEmpty()
    val lookupNames = (if (matching.isEmpty()) sameLine else matching)
        .map { normalizeStopName(it.direction) }
        .filter { it.isNotEmpty() }
        .toSet()
    val filtered = departures
        .filter { departure ->
            departure.line.equals(lineLabel, ignoreCase = true) &&
                (lookupNames.isEmpty() || normalizeStopName(departure.destination) in lookupNames)
        }
        .sortedBy { it.expectedAt }
        .take(limit)
    return FallbackPassages(passages = filtered, showsAllDirections = showsAll)
}

/**
 * Prochain passage encore devant, ou le dernier connu si tous sont passés.
 */
fun plannedReliefPassage(passages: List<Instant>, at: Instant): Instant? =
    passages.firstOrNull { !it.isBefore(at) } ?: passages.firstOrNull()

internal fun wantedTermini(terminus: String): Set<String> =
    terminus.split('/')
        .map { normalizeStopName(it) }
        .filter { it.isNotEmpty() }
        .toSet()
