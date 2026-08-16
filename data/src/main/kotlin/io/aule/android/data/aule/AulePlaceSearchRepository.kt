package io.aule.android.data.aule

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.MIN_PLACE_QUERY_LENGTH
import io.aule.android.core.model.Place
import io.aule.android.core.model.SEARCH_LIMIT_PER_KIND
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.dto.GeocodePayloadDto

class AulePlaceSearchRepository(
    private val endpoints: AuleEndpoints,
    private val client: AuleHttpClient,
) : PlaceSearchRepository {

    override suspend fun search(query: String): List<Place> {
        val cleaned = query.trim()
        // Deux lettres rendent la moitié de la Loire-Atlantique, et l'appel
        // est payé pour rien. Le filtre est ici plutôt que dans l'écran :
        // c'est une règle de la source, pas une décision d'interface.
        if (cleaned.length < MIN_PLACE_QUERY_LENGTH) return emptyList()

        return client.get(
            url = endpoints.geocode,
            query = mapOf("q" to cleaned),
            deserializer = GeocodePayloadDto.serializer(),
        ).results.mapNotNull { result ->
            val label = result.label?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val latitude = result.lat ?: return@mapNotNull null
            val longitude = result.lng ?: return@mapNotNull null
            val coordinate = Coordinate(latitude, longitude)
            if (!coordinate.isValid) return@mapNotNull null
            Place(label = label, coordinate = coordinate)
        }.take(SEARCH_LIMIT_PER_KIND)
    }
}
