package io.aule.android.data.aule

import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.repository.LinePaletteRepository
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.dto.GtfsRouteColorDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Le nuancier, lu dans `gtfs_routes`.
 *
 * La table est **publique en lecture** : les couleurs de lignes sont affichées
 * sur les abribus, il n'y a rien à protéger et la carte s'ouvre sans compte.
 * D'où la clé publiable seule, sans `Authorization` — contrairement à la prise
 * de service, qui lit la même table avec la session du conducteur parce qu'elle
 * en tire aussi les terminus et les directions.
 *
 * Le BFF reste la bonne place à terme : une couleur posée dans la charge utile
 * de la flotte éviterait ce deuxième aller-retour, et surtout éviterait que
 * deux sources répondent un jour deux teintes pour la même ligne. Tant qu'il ne
 * la publie pas, on va la chercher là où elle est.
 */
class SupabaseLinePaletteRepository(
    private val client: AuleHttpClient,
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val json: Json = AuleHttpClient.defaultJson,
) : LinePaletteRepository {

    /**
     * Le nuancier tient en quelques kilo-octets et change à la fréquence d'un
     * GTFS : chargé une fois, gardé pour la session. Le verrou évite que deux
     * écrans ouverts en même temps le demandent deux fois.
     */
    private val mutex = Mutex()
    private var cached: LinePalette? = null

    override suspend fun palette(): LinePalette {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: load().also { cached = it }
        }
    }

    private suspend fun load(): LinePalette {
        // Sans configuration Supabase, pas de nuancier — et pas d'appel qui
        // échouerait à chaque ouverture de fiche pour l'annoncer.
        if (supabaseUrl.isBlank() || publishableKey.isBlank()) return LinePalette.EMPTY

        val response = client.getRaw(
            url = supabaseUrl.trimEnd('/') + "/rest/v1/gtfs_routes",
            headers = mapOf("apikey" to publishableKey),
            query = mapOf("select" to "route_id,route_color"),
        )
        when (response.code) {
            in 200..299 -> Unit
            in 400..499 -> throw ApiException.BadRequest(response.code)
            else -> throw ApiException.Server(response.code)
        }

        val rows = try {
            json.decodeFromString(ListSerializer(GtfsRouteColorDto.serializer()), response.body)
        } catch (failure: Throwable) {
            throw ApiException.Decoding(failure)
        }
        return LinePalette(
            rows.mapNotNull { row -> row.routeColor?.let { row.routeId to it } }.toMap(),
        )
    }
}
