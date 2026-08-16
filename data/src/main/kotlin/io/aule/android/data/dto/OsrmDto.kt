package io.aule.android.data.dto

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.RoadManeuver
import io.aule.android.core.model.repository.RoadRoute
import kotlinx.serialization.Serializable

@Serializable
internal data class OsrmResponseDto(
    val code: String? = null,
    val routes: List<OsrmRouteDto> = emptyList(),
)

@Serializable
internal data class OsrmRouteDto(
    val distance: Double? = null,
    val duration: Double? = null,
    val geometry: OsrmGeometryDto? = null,
    val legs: List<OsrmLegDto> = emptyList(),
)

@Serializable
internal data class OsrmGeometryDto(
    val coordinates: List<List<Double>> = emptyList(),
)

@Serializable
internal data class OsrmLegDto(
    val steps: List<OsrmStepDto> = emptyList(),
)

@Serializable
internal data class OsrmStepDto(
    val name: String? = null,
    val distance: Double? = null,
    val duration: Double? = null,
    val maneuver: OsrmManeuverDto? = null,
)

@Serializable
internal data class OsrmManeuverDto(
    val type: String? = null,
    val modifier: String? = null,
    val location: List<Double> = emptyList(),
)

internal fun OsrmResponseDto.toRoute(): RoadRoute? {
    if (code != "Ok") return null
    val route = routes.firstOrNull() ?: return null
    val points = route.geometry?.coordinates.orEmpty().mapNotNull { Coordinate.fromGeoJsonPair(it) }
        .filter { it.isValid }
    if (points.size < 2) return null
    val distance = route.distance ?: return null
    val duration = route.duration ?: return null
    return RoadRoute(
        points = points,
        distanceMeters = distance,
        durationSeconds = duration,
        maneuvers = route.legs.flatMap { it.steps }.mapNotNull { it.toManeuver() },
    )
}

private fun OsrmStepDto.toManeuver(): RoadManeuver? {
    val type = maneuver?.type ?: return null
    val location = Coordinate.fromGeoJsonPair(maneuver.location) ?: return null
    if (!location.isValid) return null
    val name = name?.takeIf { it.isNotBlank() }
    return RoadManeuver(
        instruction = type,
        location = location,
        distanceMeters = distance ?: 0.0,
        durationSeconds = duration ?: 0.0,
        streetName = name,
        modifier = maneuver.modifier,
    )
}
