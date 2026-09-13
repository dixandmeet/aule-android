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
 * Un **repli**, depuis que `/api/route` rend ses manœuvres avec son tracé :
 * il ne sert plus qu'aux jambes qui n'en portent aucune — une jambe piétonne
 * de correspondance, un trajet d'avant la bascule. Le préférer là où le BFF
 * en rend ferait décrire le chemin par un moteur et peindre l'autre, donc
 * annoncer un virage que le trait ne prend pas (`docs/CONTRAT-BFF.md` §10).
 *
 * Un échec rend `null` : le bandeau retombe sur le libellé de la jambe, et
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
