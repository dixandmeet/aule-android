package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import java.time.Instant

/**
 * Un **parcours** d'une ligne dans un sens : ses arrêts, dans l'ordre.
 *
 * ## Ce n'est pas « la desserte du sens »
 *
 * Une ligne n'a pas un parcours par sens. La ligne 1 dessert Beaujoire **ou**
 * Babinière dans le même sens ; la C1 publie trois parcours entre les deux
 * mêmes terminus, dont deux ajoutent un crochet. La maille qui décrit un trajet
 * est donc celle-ci, et tout raisonnement « par ligne » répond faux à « si je
 * monte ici, où puis-je descendre ».
 *
 * Ce n'est pas non plus un trajet guidé : on n'a besoin que des arrêts, dans
 * l'ordre. Sans tracé — ni le repli de relève ni la fiche d'une ligne n'en ont
 * besoin, la carte peignant celui des tuiles.
 */
data class LineJourney(
    val tripId: String,
    val stops: List<LineJourneyStop>,
    /**
     * L'identité du parcours — le `shape_id` GTFS, ou [tripId] à défaut.
     *
     * ⚠️ **Deux branches d'un même sens en ont deux différentes**, et c'est tout
     * l'intérêt : c'est par elle qu'un écran retient lequel il affiche.
     */
    val profileId: String = tripId,
    /**
     * Le terminus annoncé, tel que le référentiel l'écrit.
     *
     * Vide quand on ne l'a pas. **Il ne nomme pas la branche** : la ligne 1
     * porte « Beaujoire / Babinière » sur les deux, ce qui désigne la paire.
     * Pour nommer un parcours, voir [label].
     */
    val headsign: String = "",
) {
    /**
     * Ce qui nomme un parcours **sans ambiguïté** : ses deux bouts.
     *
     * Le terminus annoncé ne suffit pas — un menu bâti dessus proposerait deux
     * fois la même entrée sur une ligne à branches, et l'on ne saurait pas
     * laquelle on regarde. Le premier et le dernier arrêt, eux, décrivent le
     * parcours en toutes lettres, et c'est aussi ce que la liste montre en tête
     * et en pied. Repris d'iOS (`LineDetailSheet.label(of:)`).
     */
    val label: String
        get() = when {
            stops.isEmpty() -> headsign
            else -> "${stops.first().name} → ${stops.last().name}"
        }
}

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

/**
 * Rayon pour rattacher un arrêt de relève à la course re-résolue.
 *
 * De quoi rattraper deux quais du même arrêt, pas le voisin sur la ligne.
 * Aligné sur Flutter `_restoreReliefStopIndex`.
 */
const val HANDOVER_RELIEF_MATCH_METERS = 120.0

/**
 * Retrouve l'arrêt de relève dans une desserte rechargée.
 *
 * Ordre : id → nom → position ≤ [HANDOVER_RELIEF_MATCH_METERS]. Une course
 * re-résolue peut venir d'un autre profil horaire, où le même quai porte un
 * autre id. Ne fabrique **pas** d'arrêt inventé : `null` force à redemander.
 */
fun matchReliefStop(
    stops: List<LineJourneyStop>,
    summary: HandoverSummary,
): LineJourneyStop? {
    if (stops.isEmpty()) return null
    summary.reliefStopId?.let { id ->
        stops.find { it.id == id }?.let { return it }
    }
    val wanted = summary.reliefStopName?.let { normalizeStopName(it) }.orEmpty()
    if (wanted.isNotEmpty()) {
        stops.find { normalizeStopName(it.name) == wanted }?.let { return it }
    }
    val position = summary.reliefStopCoordinate ?: return null
    var bestMeters = Double.POSITIVE_INFINITY
    var best: LineJourneyStop? = null
    for (stop in stops) {
        val coordinate = stop.coordinate ?: continue
        val meters = GeoMath.distance(position, coordinate)
        if (meters < bestMeters && meters <= HANDOVER_RELIEF_MATCH_METERS) {
            bestMeters = meters
            best = stop
        }
    }
    return best
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
