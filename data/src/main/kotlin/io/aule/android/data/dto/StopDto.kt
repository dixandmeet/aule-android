package io.aule.android.data.dto

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.ScheduledPassage
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
            // Les **deux** libellés, et non celui qu'on a retenu pour l'affichage :
            // le GTFS ne connaît que le sens, le poteau n'affiche que la girouette,
            // et rapprocher un passage d'une desserte demande d'essayer les deux.
            directionLabel = direction?.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * La grille théorique d'un jour.
 *
 * Capture de production du 08/09/2026, `name=Commerce&line=1&direction=Beaujoire` :
 * `{"date":"2026-09-08","line":"1","direction":"Beaujoire","lineColor":"#00a754",
 *   "times":[{"seconds":16980,"departureId":"D003958","profileId":"P00055",
 *             "routeId":"1","vehicleType":"tram","time":"04:43","dayOffset":0}]}`
 */
@Serializable
internal data class DaySchedulePayloadDto(
    val date: String? = null,
    val line: String? = null,
    val direction: String? = null,
    val lineColor: String? = null,
    val times: List<ScheduledTimeDto> = emptyList(),
)

@Serializable
internal data class ScheduledTimeDto(
    val seconds: Int? = null,
    val departureId: String? = null,
    val vehicleType: String? = null,
) {
    /**
     * ⚠️ **`time` et `dayOffset` ne sont pas décodés.** Le serveur les dérive de
     * `seconds` — `serviceTime()` dans `stop-day-schedule/route.ts` — et les relire
     * reviendrait à tenir deux vérités pour un seul fait. Le modèle les recalcule,
     * et un test les tient.
     */
    fun toDomain(): ScheduledPassage? {
        val offset = seconds ?: return null
        return ScheduledPassage(
            seconds = offset,
            departureId = departureId?.takeIf { it.isNotBlank() },
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

/**
 * Un résultat d'autocomplétion : **un nom et une clé, jamais un point**.
 *
 * ⚠️ `lat`/`lng` restent déclarés, et restent vides en production. `/api/geocode?q=`
 * rend `{placeId, label, details}` — le fournisseur facture la position à part, et le
 * BFF ne la demande que sur `?placeId=`. Les garder ici coûte deux champs et évite de
 * casser un appelant qui, lui, recevrait un jour la position d'un autre fournisseur.
 *
 * Le champ qui compte est [placeId] : sans lui, un résultat n'est qu'un libellé qu'on
 * ne peut poser nulle part, et c'est très exactement ce qui arrivait avant le 22/09/2026
 * — `AulePlaceSearchRepository` écartait chaque résultat faute de coordonnées, et
 * « Château des ducs » comme « rue de Strasbourg » répondaient « Aucun lieu à ce nom ».
 */
@Serializable
internal data class GeocodeResultDto(
    val placeId: String? = null,
    val label: String? = null,
    val details: String? = null,
    val lng: Double? = null,
    val lat: Double? = null,
)

/** La réponse de `/api/geocode?placeId=` : le point que l'autocomplétion ne donne pas. */
@Serializable
internal data class GeocodeLookupDto(val result: GeocodeResultDto? = null)
