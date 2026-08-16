package io.aule.android.data.dto

import io.aule.android.core.geo.Coordinate
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
    )
}

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
