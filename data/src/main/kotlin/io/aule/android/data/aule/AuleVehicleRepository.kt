package io.aule.android.data.aule

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.repository.VehicleRepository
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.dto.FleetPayloadDto
import java.time.Clock
import java.time.Instant
import kotlin.math.roundToInt

/**
 * La flotte, vue par le BFF `www.aule.fr`.
 *
 * Le serveur fusionne lui-même les positions mesurées et théoriques — un
 * véhicule mesuré remplace son jumeau théorique par `twinId`. Le client ne
 * refait pas cette fusion : deux fusions divergentes afficheraient le même tram
 * deux fois selon l'écran qu'on regarde.
 */
class AuleVehicleRepository(
    private val endpoints: AuleEndpoints,
    private val client: AuleHttpClient,
    private val clock: Clock = Clock.systemUTC(),
) : VehicleRepository {

    override suspend fun vehicles(
        around: Coordinate,
        radiusMeters: Double,
        limit: Int,
    ): FleetSnapshot {
        val payload = client.get(
            url = endpoints.vehicles,
            query = mapOf(
                "lat" to around.latitude.toString(),
                "lon" to around.longitude.toString(),
                "radius" to radiusMeters.roundToInt().toString(),
                "limit" to limit.toString(),
            ),
            deserializer = FleetPayloadDto.serializer(),
        )

        // L'instant de **réception locale** et non `generatedAt` : l'horloge du
        // serveur dérive, et c'est sur cette origine que se calcule la glisse des
        // véhicules à l'écran.
        return payload.toDomain(receivedAt = Instant.now(clock))
    }
}
