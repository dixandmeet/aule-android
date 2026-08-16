package io.aule.android.data.dto

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDeparture
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import io.aule.android.core.network.InstantIso8601Serializer
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class StopsPayloadDto(val stops: List<StopDto> = emptyList())

@Serializable
internal data class StopDto(
    val id: String? = null,
    val name: String? = null,
    val code: String? = null,
    /** Paire **GeoJSON**, donc `[lng, lat]`. L'inverser place tous les arrêts au large de la Somalie. */
    val coordinates: List<Double> = emptyList(),
    val wheelchairAccessible: Boolean? = null,
    val stationName: String? = null,
    val transportMode: String? = null,
) {
    fun toDomain(): TransitStop? {
        val identifier = id?.takeIf { it.isNotBlank() } ?: return null
        val label = name?.takeIf { it.isNotBlank() } ?: return null
        val coordinate = Coordinate.fromGeoJsonPair(coordinates)?.takeIf { it.isValid } ?: return null
        val mode = TransportMode.fromApiValue(transportMode) ?: return null

        return TransitStop(
            id = identifier,
            name = label,
            code = code,
            coordinate = coordinate,
            mode = mode,
            stationName = stationName,
            isWheelchairAccessible = wheelchairAccessible == true,
        )
    }
}

@Serializable
internal data class DeparturesPayloadDto(
    val stopId: String? = null,
    val passages: List<PassageDto> = emptyList(),
    @Serializable(with = InstantIso8601Serializer::class)
    val updatedAt: Instant? = null,
)

@Serializable
internal data class PassageDto(
    val id: String? = null,
    val line: String? = null,
    val direction: String? = null,
    val destination: String? = null,
    @Serializable(with = InstantIso8601Serializer::class)
    val expectedAt: Instant? = null,
    val realtime: Boolean? = null,
    val lineColor: String? = null,
    val vehicleType: String? = null,
) {
    fun toDomain(): StopDeparture? {
        val label = line?.takeIf { it.isNotBlank() } ?: return null
        val expected = expectedAt ?: return null
        // La destination manque parfois ; la direction dit la même chose et vaut
        // mieux qu'un passage écarté.
        val target = destination?.takeIf { it.isNotBlank() }
            ?: direction?.takeIf { it.isNotBlank() }
            ?: return null

        return StopDeparture(
            id = id?.takeIf { it.isNotBlank() } ?: "$label|$target|$expected",
            line = label,
            lineColor = lineColor,
            destination = target,
            expectedAt = expected,
            isRealtime = realtime == true,
            mode = TransportMode.fromApiValue(vehicleType),
        )
    }
}

@Serializable
internal data class ServingLinesPayloadDto(
    val stopName: String? = null,
    val scope: String? = null,
    val lines: List<ServingLineDto> = emptyList(),
)

@Serializable
internal data class ServingLineDto(
    val line: String? = null,
    val direction: String? = null,
    val lineColor: String? = null,
    val vehicleType: String? = null,
) {
    fun toDomain(): ServingLine? {
        val label = line?.takeIf { it.isNotBlank() } ?: return null
        return ServingLine(
            line = label,
            direction = direction.orEmpty(),
            lineColor = lineColor,
            mode = TransportMode.fromApiValue(vehicleType),
        )
    }
}

@Serializable
internal data class GeocodePayloadDto(val results: List<GeocodeResultDto> = emptyList())

@Serializable
internal data class GeocodeResultDto(
    val label: String? = null,
    val lng: Double? = null,
    val lat: Double? = null,
)
