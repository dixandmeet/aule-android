package io.aule.android.data.dto

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.ManeuverLane
import io.aule.android.core.model.RoadManeuver
import io.aule.android.core.model.RouteCandidate
import io.aule.android.core.model.RoutePlan
import io.aule.android.core.model.RouteProfile
import io.aule.android.core.model.RouteReliability
import io.aule.android.core.model.RouteSegment
import io.aule.android.core.model.RouteStep
import io.aule.android.core.model.anchorTransitSegments
import io.aule.android.core.model.durationMinutesFromSeconds
import io.aule.android.core.model.routeStepKindFromId
import io.aule.android.core.network.Iso8601
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
internal data class RoutePayloadDto(
    val coordinates: List<List<Double>> = emptyList(),
    val segments: List<RouteSegmentDto> = emptyList(),
    val distance: Double? = null,
    val duration: Double? = null,
    val departureAt: String? = null,
    val arrivalAt: String? = null,
    val steps: List<RouteStepDto> = emptyList(),
    val alternatives: List<RouteCandidateDto> = emptyList(),
    val departures: List<RouteCandidateDto> = emptyList(),
    val engine: String? = null,
    val maneuvers: List<RouteManeuverDto> = emptyList(),
)

@Serializable
internal data class RouteCandidateDto(
    val id: String? = null,
    val coordinates: List<List<Double>> = emptyList(),
    val segments: List<RouteSegmentDto> = emptyList(),
    val distance: Double? = null,
    val duration: Double? = null,
    val departureAt: String? = null,
    val arrivalAt: String? = null,
    val steps: List<RouteStepDto> = emptyList(),
    val summary: String? = null,
    val accessible: Boolean? = null,
    val alerts: List<kotlinx.serialization.json.JsonElement> = emptyList(),
    val profiles: List<String> = emptyList(),
    val reliability: String? = null,
    val walkSeconds: Double? = null,
    val waitSeconds: Double? = null,
    val transfers: Int? = null,
    val maneuvers: List<RouteManeuverDto> = emptyList(),
)

/**
 * Une manœuvre du fil, telle que le §10 du contrat la décrit.
 *
 * ⚠️ `location` est en **`lng,lat`**, comme tout le reste du contrat.
 * Inversée, elle reste dans les bornes du globe à la latitude de Nantes —
 * rien ne lèverait ici — et se retrouve écartée à l'agrafage, à quelques
 * milliers de kilomètres du tracé. C'est `MANEUVER_SNAP_M` qui rattrape ce
 * piège, pas le décodeur.
 *
 * Les champs facultatifs sont **absents, jamais nuls** : le BFF normalise les
 * `null` d'OSRM en absence, et un client n'a donc pas à distinguer les deux.
 */
@Serializable
internal data class RouteManeuverDto(
    val location: List<Double> = emptyList(),
    val type: String? = null,
    val modifier: String? = null,
    val street: String? = null,
    val ref: String? = null,
    val destinations: String? = null,
    val exit: Int? = null,
    val rotaryName: String? = null,
    val bearingBefore: Double? = null,
    val bearingAfter: Double? = null,
    val lanes: List<ManeuverLaneDto> = emptyList(),
)

@Serializable
internal data class ManeuverLaneDto(
    val indications: List<String> = emptyList(),
    val valid: Boolean = false,
)

@Serializable
internal data class RouteSegmentDto(
    val coordinates: List<List<Double>> = emptyList(),
    val color: String? = null,
    val type: String? = null,
    val routeId: String? = null,
    val departureAt: String? = null,
    val arrivalAt: String? = null,
)

@Serializable
internal data class RouteStepDto(
    val icon: String? = null,
    val label: String? = null,
    val detail: String? = null,
    val duration: String? = null,
)

internal fun RoutePayloadDto.toPlan(): RoutePlan? {
    val alternatives = alternatives.mapNotNull { it.toDomain() }
    val resolved = alternatives.ifEmpty {
        val primary = primaryCandidate() ?: return null
        listOf(primary)
    }
    val next = departures.mapNotNull { it.toDomain() }
    return RoutePlan(
        alternatives = resolved,
        departures = next.ifEmpty { resolved },
        selectedId = resolved.first().id,
        timetable = engine == "timetable",
    )
}

private fun RoutePayloadDto.primaryCandidate(): RouteCandidate? {
    val coords = coordinates.toCoordinates()
    if (coords.isEmpty()) return null
    val seconds = duration ?: return null
    return RouteCandidate(
        id = "primary",
        coordinates = coords,
        segments = anchorTransitSegments(segments.mapNotNull { it.toDomain() }),
        distanceMeters = (distance ?: 0.0).roundToInt(),
        durationMinutes = durationMinutesFromSeconds(seconds),
        steps = steps.mapNotNull { it.toDomain() },
        summary = "",
        accessible = false,
        alertCount = 0,
        profiles = emptyList(),
        departureAt = departureAt.toInstantOrNull(),
        arrivalAt = arrivalAt.toInstantOrNull(),
        maneuvers = maneuvers.mapNotNull { it.toDomain() },
    )
}

private fun RouteCandidateDto.toDomain(): RouteCandidate? {
    val identifier = id?.takeIf { it.isNotBlank() } ?: return null
    val seconds = duration ?: return null
    return RouteCandidate(
        id = identifier,
        coordinates = coordinates.toCoordinates(),
        segments = anchorTransitSegments(segments.mapNotNull { it.toDomain() }),
        distanceMeters = (distance ?: 0.0).roundToInt(),
        durationMinutes = durationMinutesFromSeconds(seconds),
        steps = steps.mapNotNull { it.toDomain() },
        summary = summary.orEmpty(),
        accessible = accessible == true,
        alertCount = alerts.size,
        profiles = profiles.mapNotNull { RouteProfile.fromApiValue(it) },
        departureAt = departureAt.toInstantOrNull(),
        arrivalAt = arrivalAt.toInstantOrNull(),
        reliability = RouteReliability.fromApiValue(reliability),
        walk = walkSeconds.toDurationOrNull(),
        wait = waitSeconds.toDurationOrNull(),
        transfers = transfers,
        maneuvers = maneuvers.mapNotNull { it.toDomain() },
    )
}

/**
 * Sans type ni position, une manœuvre ne décrit rien et ne s'agrafe nulle
 * part : elle est écartée plutôt que complétée par un défaut.
 *
 * ⚠️ **Les caps ne se gardent que tous les deux.** Un angle est une
 * différence, et une différence à laquelle il manque un terme ne vaut pas
 * zéro degré — elle ne vaut rien. En garder un seul inviterait
 * `maneuverKindOf` à compléter par un défaut, c'est-à-dire à inventer un
 * angle. Le BFF les rend déjà ensemble ou pas du tout ; la garde est ici
 * parce que c'est ici qu'on cesserait de le savoir.
 *
 * Les distances et durées restent à zéro : `/api/route` n'en rend pas, et le
 * bandeau n'en veut pas — il mesure jusqu'au point agrafé, pas jusqu'à ce que
 * le moteur annonçait au moment du calcul.
 */
private fun RouteManeuverDto.toDomain(): RoadManeuver? {
    val instruction = type?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val point = Coordinate.fromGeoJsonPair(location)?.takeIf { it.isValid } ?: return null
    val bearings = bearingBefore != null && bearingAfter != null
    return RoadManeuver(
        instruction = instruction,
        location = point,
        distanceMeters = 0.0,
        durationSeconds = 0.0,
        streetName = street?.trim()?.takeIf { it.isNotEmpty() },
        modifier = modifier?.trim()?.takeIf { it.isNotEmpty() },
        bearingBefore = if (bearings) bearingBefore else null,
        bearingAfter = if (bearings) bearingAfter else null,
        ref = ref?.trim()?.takeIf { it.isNotEmpty() },
        destinations = destinations?.trim()?.takeIf { it.isNotEmpty() },
        exit = exit,
        rotaryName = rotaryName?.trim()?.takeIf { it.isNotEmpty() },
        lanes = lanes.map { it.toDomain() },
    )
}

private fun ManeuverLaneDto.toDomain(): ManeuverLane = ManeuverLane(
    indications = indications.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
    valid = valid,
)

private fun RouteSegmentDto.toDomain(): RouteSegment? {
    val coords = coordinates.toCoordinates()
    if (coords.size < 2) return null
    return RouteSegment(
        coordinates = coords,
        color = color?.takeIf { it.isNotBlank() } ?: io.aule.android.core.model.ROUTE_FALLBACK_COLOR,
        walk = type == "walk",
        routeId = routeId,
        departureAt = departureAt.toInstantOrNull(),
        arrivalAt = arrivalAt.toInstantOrNull(),
    )
}

private fun RouteStepDto.toDomain(): RouteStep? {
    val text = label?.takeIf { it.isNotBlank() } ?: return null
    return RouteStep(
        kind = routeStepKindFromId(icon),
        label = text,
        detail = detail.orEmpty(),
        duration = duration.orEmpty(),
    )
}

private fun List<List<Double>>.toCoordinates(): List<Coordinate> =
    mapNotNull { pair -> Coordinate.fromGeoJsonPair(pair)?.takeIf { it.isValid } }

private fun String?.toInstantOrNull() = this?.let { Iso8601.parseOrNull(it) }

private fun Double?.toDurationOrNull(): java.time.Duration? {
    val value = this ?: return null
    if (!value.isFinite() || value < 0) return null
    return java.time.Duration.ofNanos((value * 1_000_000_000.0).toLong())
}
