package io.aule.android.data.dto

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.HandoverEngagement
import io.aule.android.core.model.HandoverFix
import io.aule.android.core.model.HandoverStatus
import io.aule.android.core.model.HandoverSummary
import io.aule.android.core.model.HandoverTarget
import io.aule.android.core.model.HandoverTrack
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class HandoverTargetDto(
    @SerialName("outgoing_service_id") val serviceId: String,
    @SerialName("driver_display") val driverDisplay: String? = null,
    @SerialName("line_id") val lineId: String? = null,
    @SerialName("direction_id") val directionId: Int? = null,
    val headsign: String? = null,
    @SerialName("vehicle_id") val vehicleId: String? = null,
    @SerialName("train_number") val trainNumber: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("last_position_at") val lastPositionAt: String? = null,
    @SerialName("position_age_seconds") val positionAgeSeconds: Int? = null,
) {
    fun toDomain(): HandoverTarget = HandoverTarget(
        serviceId = serviceId,
        lineId = lineId.orEmpty(),
        driverDisplay = driverDisplay?.trim()?.takeIf { it.isNotEmpty() },
        directionId = directionId,
        terminus = cleanTerminus(headsign),
        vehicleId = vehicleId?.trim()?.takeIf { it.isNotEmpty() },
        trainNumber = trainNumber?.trim()?.takeIf { it.isNotEmpty() },
        startedAt = parseInstant(startedAt),
        lastPositionAt = parseInstant(lastPositionAt),
        positionAgeSeconds = positionAgeSeconds,
    )
}

@Serializable
internal data class HandoverSummaryDto(
    val id: String,
    val status: String? = null,
    @SerialName("line_id") val lineId: String? = null,
    @SerialName("vehicle_id") val vehicleId: String? = null,
    @SerialName("outgoing_service_id") val outgoingServiceId: String,
    @SerialName("incoming_service_id") val incomingServiceId: String? = null,
    @SerialName("outgoing_display") val outgoingDisplay: String? = null,
    @SerialName("incoming_display") val incomingDisplay: String? = null,
    @SerialName("relief_stop_id") val reliefStopId: String? = null,
    @SerialName("relief_stop_name") val reliefStopName: String? = null,
    @SerialName("relief_stop_lat") val reliefStopLat: Double? = null,
    @SerialName("relief_stop_lon") val reliefStopLon: Double? = null,
    @SerialName("relief_planned_at") val reliefPlannedAt: String? = null,
    @SerialName("cancel_reason") val cancelReason: String? = null,
) {
    fun toDomain(): HandoverSummary = HandoverSummary(
        id = id,
        status = HandoverStatus.fromWire(status),
        lineId = lineId.orEmpty(),
        outgoingServiceId = outgoingServiceId,
        incomingServiceId = incomingServiceId,
        vehicleId = vehicleId,
        outgoingDisplay = outgoingDisplay?.trim()?.takeIf { it.isNotEmpty() },
        incomingDisplay = incomingDisplay?.trim()?.takeIf { it.isNotEmpty() },
        reliefStopId = reliefStopId?.trim()?.takeIf { it.isNotEmpty() },
        reliefStopName = reliefStopName?.trim()?.takeIf { it.isNotEmpty() },
        reliefStopCoordinate = reliefCoordinate(),
        reliefPlannedAt = parseInstant(reliefPlannedAt),
        cancelReason = cancelReason?.trim()?.takeIf { it.isNotEmpty() },
    )

    private fun reliefCoordinate(): Coordinate? {
        val lat = reliefStopLat ?: return null
        val lon = reliefStopLon ?: return null
        return Coordinate(lat, lon).takeIf { it.isValid }
    }
}

@Serializable
internal data class HandoverEngagementDto(
    val handover: HandoverSummaryDto,
    val target: HandoverTargetDto,
) {
    fun toDomain(): HandoverEngagement = HandoverEngagement(
        handover = handover.toDomain(),
        target = target.toDomain(),
    )
}

@Serializable
internal data class HandoverFixDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speed: Double? = null,
    val heading: Double? = null,
    val accuracy: Double? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("age_seconds") val ageSeconds: Int? = null,
) {
    fun toDomain(): HandoverFix? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        val at = parseInstant(recordedAt) ?: return null
        val coordinate = Coordinate(lat, lon)
        if (!coordinate.isValid) return null
        return HandoverFix(
            coordinate = coordinate,
            recordedAt = at,
            ageSeconds = ageSeconds?.coerceAtLeast(0) ?: 0,
            speed = speed,
            heading = heading,
            accuracy = accuracy,
        )
    }
}

@Serializable
internal data class HandoverTrackDto(
    val handover: HandoverSummaryDto,
    @SerialName("service_status") val serviceStatus: String? = null,
    val position: HandoverFixDto? = null,
    @SerialName("server_time") val serverTime: String? = null,
) {
    fun toDomain(): HandoverTrack = HandoverTrack(
        handover = handover.toDomain(),
        serverTime = parseInstant(serverTime) ?: Instant.EPOCH,
        serviceStatus = serviceStatus?.trim()?.takeIf { it.isNotEmpty() },
        fix = position?.toDomain(),
    )
}

/**
 * Le headsign Flutter s'écrivait avec une flèche ; le modèle Android ne
 * porte que le terminus. L'échappement évite le glyphe interdit dans
 * `feature/` / `app/`.
 */
internal fun cleanTerminus(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var value = raw.trim()
    val arrow = '\u2192'
    while (value.isNotEmpty() && (value.first() == arrow || value.first() == '>')) {
        value = value.drop(1).trimStart()
    }
    return value.ifEmpty { null }
}

private fun parseInstant(value: String?): Instant? =
    value?.let { runCatching { Instant.parse(it) }.getOrNull() }
