package io.aule.android.data.dto

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.LineJourneyStop
import io.aule.android.core.model.ServiceDirection
import io.aule.android.core.model.ServiceHeartbeat
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.serviceLineEndpoints
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class GtfsRouteDto(
    @SerialName("route_id") val routeId: String,
    @SerialName("route_short_name") val shortName: String? = null,
    @SerialName("route_long_name") val longName: String? = null,
    @SerialName("route_type") val routeType: Int = 3,
    @SerialName("route_color") val routeColor: String? = null,
    @SerialName("network_id") val networkId: String? = null,
) {
    fun toDomain(): ServiceLine {
        val label = shortName?.trim().orEmpty().ifEmpty { routeId }
        val description = longName?.trim().orEmpty()
        val endpoints = serviceLineEndpoints(description)
        return ServiceLine(
            id = routeId,
            label = label,
            description = description.ifEmpty { label },
            mode = TransportMode.fromGtfsRouteType(routeType),
            colorHex = routeColor,
            networkId = networkId,
            directions = listOf(
                ServiceDirection(key = "0", terminus = endpoints.first),
                ServiceDirection(key = "1", terminus = endpoints.second),
            ),
        )
    }
}

@Serializable
internal data class DriverIdDto(val id: String)

@Serializable
internal data class DriverServiceRowDto(
    val id: String,
    @SerialName("line_id") val lineId: String,
    @SerialName("direction_id") val directionId: Int = 0,
    val headsign: String? = null,
    @SerialName("vehicle_id") val vehicleId: String? = null,
    @SerialName("train_number") val trainNumber: String? = null,
    @SerialName("start_time_real") val startTimeReal: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toDomain(lineLabel: String): ActiveDriverService = ActiveDriverService(
        id = id,
        lineId = lineId,
        lineLabel = lineLabel,
        directionId = directionId,
        terminus = headsign.orEmpty(),
        startedAt = parseInstant(startTimeReal) ?: parseInstant(createdAt) ?: Instant.EPOCH,
        vehicleId = vehicleId,
        trainNumber = trainNumber,
    )
}

private fun parseInstant(value: String?): Instant? =
    value?.let { runCatching { Instant.parse(it) }.getOrNull() }

@Serializable
internal data class GtfsTripDto(
    @SerialName("trip_id") val tripId: String,
    @SerialName("shape_id") val shapeId: String? = null,
    @SerialName("direction_id") val directionId: Int = 0,
)

@Serializable
internal data class GtfsStopTimeDto(
    @SerialName("trip_id") val tripId: String,
    @SerialName("stop_sequence") val stopSequence: Int = 0,
    @SerialName("gtfs_stops") val stop: GtfsStopEmbedDto? = null,
)

@Serializable
internal data class GtfsStopEmbedDto(
    @SerialName("stop_id") val stopId: String? = null,
    @SerialName("stop_name") val stopName: String? = null,
    val geom: JsonElement? = null,
) {
    fun toDomain(): LineJourneyStop? {
        val name = stopName?.trim().orEmpty()
        if (name.isEmpty()) return null
        return LineJourneyStop(
            id = stopId?.trim().orEmpty().ifEmpty { name },
            name = name,
            coordinate = geom.toCoordinate(),
        )
    }
}

internal fun JsonElement?.toCoordinate(): Coordinate? {
    val obj = this as? JsonObject ?: return null
    val coords = obj["coordinates"] as? JsonArray ?: return null
    if (coords.size < 2) return null
    val lon = coords[0].jsonPrimitive.doubleOrNull ?: return null
    val lat = coords[1].jsonPrimitive.doubleOrNull ?: return null
    return Coordinate(lat, lon).takeIf { it.isValid }
}

@Serializable
internal data class ServiceHeartbeatDto(
    @SerialName("service_status") val serviceStatus: String,
    val published: Boolean = false,
    val handover: HandoverSummaryDto? = null,
    @SerialName("server_time") val serverTime: String? = null,
) {
    fun toDomain(): ServiceHeartbeat = ServiceHeartbeat(
        serviceStatus = serviceStatus,
        published = published,
        serverTime = parseInstant(serverTime) ?: Instant.EPOCH,
        handover = handover?.toDomain(),
    )
}

@Serializable
internal data class GtfsTripProfileDto(
    @SerialName("profile_id") val profileId: String,
    @SerialName("direction_id") val directionId: Int = 0,
    val headsign: String? = null,
    @SerialName("route_id") val routeId: String? = null,
)

@Serializable
internal data class GtfsTripDepartureDto(
    @SerialName("departure_id") val departureId: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("start_seconds") val startSeconds: Int,
)

@Serializable
internal data class GtfsTripProfileStopDto(
    @SerialName("profile_id") val profileId: String,
    @SerialName("stop_sequence") val stopSequence: Int = 0,
    @SerialName("stop_id") val stopId: String,
    @SerialName("offset_seconds") val offsetSeconds: Int = 0,
)

@Serializable
internal data class GtfsStopMetaDto(
    @SerialName("stop_id") val stopId: String,
    @SerialName("stop_name") val stopName: String? = null,
    val geom: JsonElement? = null,
)

@Serializable
internal data class GtfsCalendarDto(
    @SerialName("service_id") val serviceId: String,
    @SerialName("runs_on") val runsOn: List<Boolean> = emptyList(),
)

@Serializable
internal data class GtfsCalendarDateDto(
    @SerialName("service_id") val serviceId: String,
    @SerialName("exception_type") val exceptionType: Int = 0,
)
