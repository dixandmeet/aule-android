package io.aule.android.data.dto

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.VehicleFeed
import io.aule.android.core.network.InstantIso8601Serializer
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class FleetPayloadDto(
    val vehicles: List<VehicleDto> = emptyList(),
    @Serializable(with = InstantIso8601Serializer::class)
    val generatedAt: Instant? = null,
    val horizonSeconds: Double? = null,
    /** Le serveur signale qu'une de ses deux sources est tombée. */
    val degraded: String? = null,
) {
    /**
     * Un enregistrement abîmé est **écarté**, il ne fait pas échouer le lot.
     *
     * Un mode inconnu ou une position nulle arrive réellement dans le flux ; les
     * laisser passer afficherait un véhicule inventé, et lever effacerait les
     * quarante autres qui, eux, sont bons.
     */
    fun toDomain(receivedAt: Instant): FleetSnapshot = FleetSnapshot(
        vehicles = vehicles.mapNotNull { it.toDomain() },
        generatedAt = generatedAt ?: receivedAt,
        horizonSeconds = horizonSeconds ?: 10.0,
        isStale = false,
        degraded = degraded,
    )
}

@Serializable
internal data class VehicleDto(
    val id: String? = null,
    /** Le mode de transport : `bus`, `tram`, `navibus`… */
    val type: String? = null,
    /** La provenance de la position : `live` ou `scheduled`. */
    val mode: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val heading: Double? = null,
    val aheadLat: Double? = null,
    val aheadLng: Double? = null,
    val trajectory: List<TrajectoryPointDto> = emptyList(),
    val stopProgress: Double? = null,
    val dwellSeconds: Double? = null,
    val speedMps: Double? = null,
    @Serializable(with = InstantIso8601Serializer::class)
    val recordedAt: Instant? = null,
    val routeId: String? = null,
    val destination: String? = null,
    val nextStop: String? = null,
    val etaSeconds: Double? = null,
    val twinId: String? = null,
    val occupancy: Double? = null,
) {
    fun toDomain(): TransportVehicle? {
        val identifier = id?.takeIf { it.isNotBlank() } ?: return null
        val transportMode = TransportMode.fromApiValue(type) ?: return null
        val latitude = lat ?: return null
        val longitude = lng ?: return null
        val coordinate = Coordinate(latitude, longitude)
        if (!coordinate.isValid) return null
        val line = routeId?.takeIf { it.isNotBlank() } ?: return null

        val ahead = if (aheadLat != null && aheadLng != null) {
            Coordinate(aheadLat, aheadLng).takeIf { it.isValid }
        } else {
            null
        }

        return TransportVehicle(
            id = identifier,
            mode = transportMode,
            feed = VehicleFeed.fromApiValue(mode),
            lineId = line,
            lineName = line,
            destination = destination,
            coordinate = coordinate,
            headingDegrees = heading ?: 0.0,
            updatedAt = recordedAt,
            occupancy = occupancy,
            ahead = ahead,
            trajectory = trajectory.mapNotNull { it.toDomain() },
            speedMps = speedMps,
            stopProgress = stopProgress,
            dwellSeconds = dwellSeconds ?: 0.0,
            nextStop = nextStop,
            etaSeconds = etaSeconds,
            twinId = twinId,
        )
    }
}

@Serializable
internal data class TrajectoryPointDto(val lat: Double? = null, val lng: Double? = null) {
    fun toDomain(): Coordinate? {
        val latitude = lat ?: return null
        val longitude = lng ?: return null
        return Coordinate(latitude, longitude).takeIf { it.isValid }
    }
}
