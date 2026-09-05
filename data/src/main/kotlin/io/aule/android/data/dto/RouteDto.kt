package io.aule.android.data.dto

import io.aule.android.core.geo.Coordinate
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

/**
 * Une manœuvre telle que `/api/route` la rend.
 *
 * ## Ce champ arrivait, et on le jetait
 *
 * Le contrat publie `maneuvers` sur les modes porte-à-porte : **21 entrées**
 * pour le trajet de recette en voiture, **12** pour le même à pied, avec le
 * type, le modificateur, le nom de rue et le numéro de sortie — c'est-à-dire
 * tout ce que le guidage affiche. Le DTO ne le déclarait pas, `kotlinx`
 * l'ignorait donc en silence, et l'application allait redemander la même chose
 * à un **second** serveur : `router.project-osrm.org`, la démonstration
 * publique d'OSRM, dont les conditions d'usage excluent la production.
 *
 * Ce que ce détour coûtait, mesuré le 28/08/2026 : une dépendance de production
 * sur le chemin du guidage (AND-BUG-004), le risque d'agrafer sur un tracé les
 * manœuvres d'un **autre** (AND-BUG-005) — dont la garde a tiré en trajet réel,
 * « le routeur rend 1689 m pour une jambe de 266 m », laissant le conducteur
 * sans consigne —, et un aller-retour réseau de plus par jambe.
 *
 * Le profil, en prime, est respecté ici : le serveur public sert des consignes
 * voiture même quand on lui demande la marche, là où `/api/route?mode=foot`
 * rend bien douze manœuvres piétonnes.
 *
 * Pas de `distance` ni de `duration` par manœuvre — `pinManeuvers` n'en lit
 * aucune : il projette la position sur le tracé peint et garde le reste.
 */
@Serializable
internal data class RouteManeuverDto(
    val type: String? = null,
    val modifier: String? = null,
    val street: String? = null,
    /** `[lng, lat]`, comme partout dans ce contrat. */
    val location: List<Double> = emptyList(),
    /** La sortie du rond-point, comptée à partir de 1. */
    val exit: Int? = null,
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
 * Le type sert d'`instruction`, comme pour OSRM : c'est `maneuverKindOf` qui le
 * traduit en geste, et `DomainText` qui en fait une phrase (ADR-011).
 */
private fun RouteManeuverDto.toDomain(): RoadManeuver? {
    val kind = type?.takeIf { it.isNotBlank() } ?: return null
    val where = Coordinate.fromGeoJsonPair(location)?.takeIf { it.isValid } ?: return null
    return RoadManeuver(
        instruction = kind,
        location = where,
        distanceMeters = 0.0,
        durationSeconds = 0.0,
        streetName = street?.takeIf { it.isNotBlank() },
        modifier = modifier,
        // Zéro ou négatif n'est pas une sortie : mieux vaut l'absence, qui a sa
        // formulation, qu'un « prendre la 0e sortie ».
        exit = exit?.takeIf { it > 0 },
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
