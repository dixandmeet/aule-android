package io.aule.android.data.aule

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.repository.RoadProfile
import io.aule.android.core.model.repository.RoadRoute
import io.aule.android.core.model.repository.RoadRouter
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.dto.OsrmResponseDto
import io.aule.android.data.dto.toRoute
import kotlinx.coroutines.CancellationException

/**
 * Client du serveur OSRM public.
 *
 * Il comble le silence de `/api/route`, qui ne rend aucune manœuvre. Un
 * échec rend `null` : le bandeau retombe sur le libellé de la jambe, et
 * ce silence n'est pas une panne.
 *
 * L'hôte est injectable : le serveur public n'a pas de garantie de
 * service, et pouvoir en désigner un autre au build est ce qui permettra
 * d'en changer sans toucher au code.
 */
class OsrmRoadRouter(
    private val client: AuleHttpClient,
    private val origin: String = DEFAULT_ORIGIN,
) : RoadRouter {

    override suspend fun route(
        from: Coordinate,
        to: Coordinate,
        profile: RoadProfile,
    ): RoadRoute? {
        val url = "$origin/route/v1/${profile.osrmPath}/${from.apiPair};${to.apiPair}"
        val body = try {
            client.get(
                url = url,
                query = mapOf(
                    "overview" to "full",
                    "geometries" to "geojson",
                    "steps" to "true",
                    "alternatives" to "false",
                ),
                deserializer = OsrmResponseDto.serializer(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return null
        }
        return body.toRoute()
    }

    companion object {
        const val DEFAULT_ORIGIN = "https://router.project-osrm.org"
    }
}
