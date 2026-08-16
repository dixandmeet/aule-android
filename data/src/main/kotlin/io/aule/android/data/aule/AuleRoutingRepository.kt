package io.aule.android.data.aule

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.RouteMode
import io.aule.android.core.model.RoutePlan
import io.aule.android.core.model.RoutePreferences
import io.aule.android.core.model.repository.RoutingRepository
import io.aule.android.core.model.RouteApi
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.dto.RoutePayloadDto
import io.aule.android.data.dto.toPlan
import java.time.Instant

class AuleRoutingRepository(
    private val endpoints: AuleEndpoints,
    private val client: AuleHttpClient,
) : RoutingRepository {

    override suspend fun plan(
        mode: RouteMode,
        from: Coordinate,
        to: Coordinate,
        preferences: RoutePreferences,
        departureAt: Instant?,
        arriveBy: Boolean,
    ): RoutePlan {
        val payload = client.get(
            url = endpoints.route,
            query = RouteApi.query(
                mode = mode,
                from = from,
                to = to,
                preferences = preferences,
                departureAt = departureAt,
                arriveBy = arriveBy,
            ),
            deserializer = RoutePayloadDto.serializer(),
        )
        return payload.toPlan()
            ?: error("Le service d'itinéraire a renvoyé un plan sans tracé.")
    }
}
