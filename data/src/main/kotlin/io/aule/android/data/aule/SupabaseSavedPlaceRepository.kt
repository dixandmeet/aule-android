package io.aule.android.data.aule

import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.SavedPlace
import io.aule.android.core.model.repository.SavedPlaceRepository
import io.aule.android.core.model.savedPlaceFromRemote
import io.aule.android.core.model.toRemoteRow
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client PostgREST des adresses favorites — table `user_saved_places`.
 *
 * ## Ce que le serveur porte, et ce qu'il ne décide pas
 *
 * Il ne décide rien. La source de vérité est l'appareil : ce dépôt sert à
 * retrouver son domicile après avoir changé de téléphone, pas à autoriser
 * l'affichage d'un raccourci. Ce qui arrive d'ici passe par
 * [io.aule.android.core.model.mergeSavedPlaces], qui tranche à l'horodatage.
 *
 * `user_id` n'est jamais envoyé : la RLS n'accepte que `auth.uid()`, et le
 * laisser au client promettrait d'écrire au nom d'un autre. C'est le `DEFAULT`
 * de la colonne qui le pose — même discipline que `driver_reports`.
 *
 * ## Le décodage est écrit à la main
 *
 * Pas de `@Serializable` : le modèle vit dans un module que la sérialisation ne
 * traverse pas, et une ligne abîmée doit être **sautée** plutôt qu'emporter les
 * dix-neuf autres. `kotlinx` lèverait sur la première ; ici, [savedPlaceFromRemote]
 * rend `null` et on continue.
 */
class SupabaseSavedPlaceRepository(
    private val client: AuleHttpClient,
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val json: Json = AuleHttpClient.defaultJson,
) : SavedPlaceRepository {

    private val restBase: String
        get() = supabaseUrl.trimEnd('/') + "/rest/v1"

    override suspend fun fetch(session: AuthSession): List<SavedPlace> {
        if (!configured) return emptyList()
        val response = client.getRaw(
            url = "$restBase/$TABLE",
            headers = restHeaders(session),
            query = mapOf("select" to SELECT),
        )
        when (response.code) {
            in 200..299 -> Unit
            // 404 : la table n'existe pas encore sur cette instance. Ce n'est
            // pas « aucun favori » — c'est « on n'a pas pu demander » —, et
            // l'appelant garde ses favoris locaux plutôt que de croire à une
            // liste vide qu'il pousserait ensuite par-dessus les vrais.
            404 -> throw ApiException.NotFound()
            502, 503, 504 -> throw ApiException.UpstreamUnavailable(response.code)
            in 400..499 -> throw ApiException.BadRequest(response.code, response.body)
            else -> throw ApiException.Server(response.code)
        }
        val rows = runCatching { json.parseToJsonElement(response.body).jsonArray }
            .getOrElse { return emptyList() }
        return rows.mapNotNull { element ->
            val row = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            savedPlaceFromRemote(
                id = row.text("id"),
                slot = row.text("slot"),
                symbol = row.text("symbol"),
                name = row.text("name"),
                label = row.text("label"),
                lat = row["lat"]?.jsonPrimitive?.doubleOrNull,
                lng = row["lng"]?.jsonPrimitive?.doubleOrNull,
                stopMode = row.text("stop_mode"),
                createdAt = row.text("created_at"),
                updatedAt = row.text("updated_at"),
                deletedAt = row.text("deleted_at"),
            )
        }
    }

    override suspend fun push(session: AuthSession, places: List<SavedPlace>) {
        if (!configured || places.isEmpty()) return
        val body = buildJsonArray {
            places.forEach { place ->
                add(
                    buildJsonObject {
                        place.toRemoteRow().forEach { (key, value) ->
                            put(
                                key,
                                when (value) {
                                    null -> JsonNull
                                    is String -> JsonPrimitive(value)
                                    is Number -> JsonPrimitive(value)
                                    is Boolean -> JsonPrimitive(value)
                                    else -> JsonPrimitive(value.toString())
                                },
                            )
                        }
                    },
                )
            }
        }.toString()

        val response = client.postRaw(
            url = "$restBase/$TABLE",
            jsonBody = body,
            headers = restHeaders(session) + mapOf(
                // `merge-duplicates` fait de l'INSERT un upsert sur la clé
                // primaire `(user_id, id)`. Sans lui, rejouer une poussée après
                // une coupure renverrait un 409 sur chaque favori déjà écrit —
                // et l'appel ne serait plus sûr à répéter.
                "Prefer" to "resolution=merge-duplicates,return=minimal",
            ),
        )
        when (response.code) {
            in 200..299 -> Unit
            404 -> throw ApiException.NotFound()
            502, 503, 504 -> throw ApiException.UpstreamUnavailable(response.code)
            in 400..499 -> throw ApiException.BadRequest(response.code, response.body)
            else -> throw ApiException.Server(response.code)
        }
    }

    private val configured: Boolean
        get() = supabaseUrl.isNotBlank() && publishableKey.isNotBlank()

    private fun restHeaders(session: AuthSession): Map<String, String> = mapOf(
        "apikey" to publishableKey,
        "Authorization" to "Bearer ${session.accessToken}",
        "Content-Type" to "application/json",
    )

    private fun kotlinx.serialization.json.JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        const val TABLE = "user_saved_places"
        const val SELECT =
            "id,slot,symbol,name,label,lat,lng,stop_mode,created_at,updated_at,deleted_at"
    }
}
