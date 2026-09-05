package io.aule.android.data.aule

import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.ServiceNote
import io.aule.android.core.model.canonicalLineName
import io.aule.android.core.model.repository.ServiceNoteRepository
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Les notes de service, lues sur le BFF — `GET /api/driver/service-notes`.
 *
 * ## Pourquoi le BFF et non PostgREST
 *
 * Contrairement à [SupabaseDriverServiceRepository], qui écrit et a besoin des RPC.
 * Ici on **lit**, et la même route sert déjà l'iOS : deux clients qui composeraient
 * chacun leur filtre PostgREST finiraient par ne pas montrer les mêmes notes le
 * jour où la règle bouge — et c'est une consigne d'exploitation.
 *
 * La RLS reste juge : le jeton de l'appelant part tel quel, et un compte qui
 * n'exploite pas le réseau reçoit une liste vide plutôt qu'un refus.
 *
 * ## Le décodage est écrit à la main
 *
 * Pas de `@Serializable` : une note abîmée doit être **sautée** plutôt qu'emporter
 * les autres. `kotlinx` lèverait sur la première ; ici [noteFrom] rend `null` et on
 * continue. Même discipline que [SupabaseSavedPlaceRepository].
 */
class AuleServiceNoteRepository(
    private val client: AuleHttpClient,
    private val endpoints: AuleEndpoints,
    private val json: Json = AuleHttpClient.defaultJson,
) : ServiceNoteRepository {

    override suspend fun fetchNotes(session: AuthSession, line: String?): List<ServiceNote> {
        val wanted = line?.let(::canonicalLineName)?.takeIf { it.isNotBlank() }
        val response = client.getRaw(
            url = endpoints.driverServiceNotes,
            headers = mapOf("Authorization" to "Bearer ${session.accessToken}"),
            query = mapOf("line" to wanted),
        )
        when (response.code) {
            in 200..299 -> Unit
            // ⚠️ 404 est une **absence**, pas une panne : la route peut ne pas être
            // déployée sur cette instance, et l'écran n'a alors rien à montrer. Sur
            // une écriture le même statut voudrait dire l'inverse — voir
            // [io.aule.android.core.network.AuleEndpoints.driverServiceNotes].
            404 -> return emptyList()
            502, 503, 504 -> throw ApiException.UpstreamUnavailable(response.code)
            in 400..499 -> throw ApiException.BadRequest(response.code, response.body)
            else -> throw ApiException.Server(response.code)
        }

        val notes = runCatching {
            json.parseToJsonElement(response.body).jsonObject["notes"]?.jsonArray
        }.getOrNull() ?: return emptyList()

        return notes.mapNotNull { element ->
            val row = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            noteFrom(row)
        }
    }

    private fun noteFrom(row: kotlinx.serialization.json.JsonObject): ServiceNote? {
        val id = row.text("id")?.takeIf { it.isNotBlank() } ?: return null
        val title = row.text("title")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        // Une note sans date de prise d'effet n'a rien à dire à un conducteur, et la
        // dater d'aujourd'hui la ferait lire comme prenant effet à l'ouverture.
        val effectiveOn = ServiceNote.parseDay(row.text("effectiveOn")) ?: return null
        val issuedOn = ServiceNote.parseDay(row.text("issuedOn")) ?: return null

        return ServiceNote(
            id = id,
            reference = row.text("reference")?.trim().orEmpty(),
            issuer = row.text("issuer")?.trim()?.takeIf { it.isNotBlank() },
            signatory = row.text("signatory")?.trim()?.takeIf { it.isNotBlank() },
            title = title,
            summary = row.text("summary")?.trim()?.takeIf { it.isNotBlank() },
            body = row.text("body").orEmpty(),
            lines = runCatching {
                row["lines"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            }.getOrNull().orEmpty().map(::canonicalLineName).filter { it.isNotBlank() },
            kind = ServiceNote.Kind.parse(row.text("kind")),
            isScheduled = row["scheduled"]?.jsonPrimitive?.booleanOrNull,
            issuedOn = issuedOn,
            effectiveOn = effectiveOn,
            // ⚠️ Aucun repli : une date d'affichage absente veut dire « sans terme ».
            // La remplacer par une date lue de travers ferait disparaître de l'écran
            // une consigne que rien n'a retirée.
            displayUntil = ServiceNote.parseDay(row.text("displayUntil")),
        )
    }

    private fun kotlinx.serialization.json.JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}
