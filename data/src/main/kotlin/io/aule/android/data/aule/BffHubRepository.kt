package io.aule.android.data.aule

import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.HubBootstrap
import io.aule.android.core.model.HubChannel
import io.aule.android.core.model.HubChannelKind
import io.aule.android.core.model.HubChannelMode
import io.aule.android.core.model.HubChannelStatus
import io.aule.android.core.model.HubColleague
import io.aule.android.core.model.HubException
import io.aule.android.core.model.HubFailureKind
import io.aule.android.core.model.HubFile
import io.aule.android.core.model.HubFileKind
import io.aule.android.core.model.HubLastMessage
import io.aule.android.core.model.HubMember
import io.aule.android.core.model.HubMemberBrief
import io.aule.android.core.model.HubMessage
import io.aule.android.core.model.HubMessagePage
import io.aule.android.core.model.HubMessageType
import io.aule.android.core.model.HubReaction
import io.aule.android.core.model.HubReplyTo
import io.aule.android.core.model.HubUnread
import io.aule.android.core.model.repository.HubRepository
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.core.network.Iso8601
import io.aule.android.core.network.RawHttpResponse
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * La messagerie, contre les routes `api/hub` du BFF (contrat §12).
 *
 * ## Ce que ce dépôt ne décide pas
 *
 * Ni qui peut écrire, ni qui voit quoi : la base le calcule, le BFF le relaie, et
 * [HubChannel.canWrite] le porte. Recalculer ces droits ici donnerait deux
 * réponses différentes au même agent selon l'écran qu'il regarde.
 *
 * ## Trois conventions du dépôt, tenues ici
 *
 * La session est un **paramètre**, jamais un champ : un jeton figé à la
 * construction se ferait refuser au bout d'une heure. Une lecture qui rend 404
 * vaut **vide** — un réseau calme n'est pas une panne. Une écriture qui rend 404
 * lève [HubFailureKind.NOT_DEPLOYED] : ce n'est pas une absence de donnée, c'est
 * un BFF plus ancien que ce binaire.
 *
 * ## Pas de `@Serializable`
 *
 * Le décodage est manuel, ligne par ligne : un message abîmé doit être sauté
 * plutôt qu'emporter les autres. `kotlinx` lèverait sur le premier.
 */
class BffHubRepository(
    private val client: AuleHttpClient,
    private val endpoints: AuleEndpoints,
    private val json: Json = AuleHttpClient.defaultJson,
) : HubRepository {

    // ------------------------------------------------------------------
    // Les deux formes d'appel
    // ------------------------------------------------------------------

    private fun headers(session: AuthSession): Map<String, String> =
        mapOf("Authorization" to "Bearer ${session.accessToken}")

    /** Une lecture : le 404 vaut vide, tout le reste lève. */
    private suspend fun read(
        session: AuthSession,
        url: String,
        query: Map<String, String?> = emptyMap(),
    ): JsonObject? = guarded {
        val response = client.getRaw(url, headers(session), query)
        when (response.code) {
            in 200..299 -> Unit
            // ⚠️ Une absence, pas une panne : la route peut ne pas être déployée
            // sur cette instance, et l'écran n'a alors rien à montrer.
            404 -> return@guarded null
            else -> throw refusalOf(response)
        }
        runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
    }

    /** Une écriture : rien n'est aplati, et un corps vide n'est pas un succès. */
    private suspend fun write(
        session: AuthSession,
        url: String,
        body: JsonObject = JsonObject(emptyMap()),
        method: String = "POST",
    ): JsonObject = guarded {
        val texte = body.toString()
        val entetes = headers(session)
        val response = when (method) {
            "PATCH" -> client.patchRaw(url, texte, entetes)
            "DELETE" -> client.deleteRaw(url, texte, entetes)
            else -> client.postRaw(url, texte, entetes)
        }
        if (response.code !in 200..299) throw refusalOf(response)
        runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
            ?: throw HubException(HubFailureKind.UNKNOWN)
    }

    /**
     * L'ordre des `catch` est invariable, et il n'est pas décoratif : un refus
     * déjà typé ne doit pas être ré-emballé en panne réseau, et une annulation
     * ne doit pas ressembler à un échec.
     */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (failure: HubException) {
        throw failure
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: ApiException.Cancelled) {
        throw CancellationException()
    } catch (_: ApiException.Transport) {
        throw HubException(HubFailureKind.UNKNOWN)
    } catch (_: Throwable) {
        throw HubException(HubFailureKind.UNKNOWN)
    }

    /**
     * Traduit un refus nommé du BFF.
     *
     * ⚠️ **Le nom d'abord, le statut ensuite.** Un BFF plus ancien peut répondre
     * 403 sans nommer son refus ; on retombe alors sur la lecture la plus
     * probable, plutôt que d'afficher « réessayer » sur un geste qui échouera
     * toujours.
     */
    private fun refusalOf(response: RawHttpResponse): HubException {
        val nom = runCatching {
            json.parseToJsonElement(response.body).jsonObject["refusal"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()

        val kind = when (nom) {
            "hub_not_authenticated" -> HubFailureKind.NOT_AUTHENTICATED
            "hub_not_member" -> HubFailureKind.NOT_MEMBER
            "hub_channel_readonly", "hub_cannot_write" -> HubFailureKind.READ_ONLY
            "hub_not_owner", "hub_not_a_group", "hub_use_leave" -> HubFailureKind.NOT_OWNER
            "hub_cannot_invite" -> HubFailureKind.CANNOT_INVITE
            "hub_not_sender" -> HubFailureKind.NOT_SENDER
            "hub_channel_closed" -> HubFailureKind.CHANNEL_CLOSED
            "hub_channel_not_found", "hub_message_not_found",
            "hub_file_not_found", "hub_colleague_not_found" -> HubFailureKind.NOT_FOUND
            "hub_colleague_other_network", "hub_cannot_direct_self" -> HubFailureKind.OTHER_NETWORK
            "hub_no_network" -> HubFailureKind.NO_NETWORK
            "hub_cannot_leave_direct", "hub_cannot_leave_automatic" -> HubFailureKind.CANNOT_LEAVE
            "hub_file_not_uploaded" -> HubFailureKind.FILE_NOT_UPLOADED
            null -> when (response.code) {
                // Sur une **écriture**, un 404 sans nom est un BFF plus ancien
                // que ce binaire — pas une absence de donnée.
                404 -> HubFailureKind.NOT_DEPLOYED
                401 -> HubFailureKind.NOT_AUTHENTICATED
                403 -> HubFailureKind.NOT_MEMBER
                409 -> HubFailureKind.CHANNEL_CLOSED
                in 400..499 -> HubFailureKind.BAD_REQUEST
                else -> HubFailureKind.UNKNOWN
            }
            // Un refus nommé que cette version ne connaît pas : mal formé plutôt
            // qu'inconnu, parce que le BFF ne nomme que ce qu'un geste a causé.
            else -> HubFailureKind.BAD_REQUEST
        }
        return HubException(kind)
    }

    // ------------------------------------------------------------------
    // Amorçage et liste
    // ------------------------------------------------------------------

    override suspend fun bootstrap(session: AuthSession): HubBootstrap {
        val row = write(session, endpoints.hubBootstrap)
        return HubBootstrap(
            networkChannelId = row.text("networkChannelId"),
            depotChannelId = row.text("depotChannelId"),
            isStaff = row.bool("isStaff") ?: false,
        )
    }

    override suspend fun channels(session: AuthSession): List<HubChannel> {
        val row = read(session, endpoints.hubChannels) ?: return emptyList()
        return row.rows("channels").mapNotNull { channelFrom(it) }
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    override suspend fun messages(
        session: AuthSession,
        channelId: String,
        before: Instant?,
    ): HubMessagePage = page(session, channelId, mapOf("before" to before?.let(Iso8601::format)))

    override suspend fun delta(
        session: AuthSession,
        channelId: String,
        after: Instant,
    ): HubMessagePage = page(session, channelId, mapOf("after" to Iso8601.format(after)))

    private suspend fun page(
        session: AuthSession,
        channelId: String,
        query: Map<String, String?>,
    ): HubMessagePage {
        val row = read(session, endpoints.hubMessages(channelId), query) ?: return HubMessagePage()
        val membres = runCatching { row["members"]?.jsonObject }.getOrNull()
        return HubMessagePage(
            messages = row.rows("messages").mapNotNull { messageFrom(it) },
            members = membres?.entries?.mapNotNull { (id, valeur) ->
                val objet = runCatching { valeur.jsonObject }.getOrNull() ?: return@mapNotNull null
                id to HubMemberBrief(
                    label = objet.text("label").orEmpty(),
                    avatarUrl = objet.text("avatarUrl"),
                    driverNumber = objet.text("driverNumber"),
                )
            }?.toMap().orEmpty(),
            hasMore = row.bool("hasMore") ?: false,
            nextAfter = row.instant("nextAfter"),
            serverTime = row.instant("serverTime"),
        )
    }

    override suspend fun send(
        session: AuthSession,
        channelId: String,
        body: String,
        clientId: String,
        replyTo: String?,
        fileId: String?,
    ): HubMessage {
        val charge = buildJsonObject {
            put("body", body)
            put("clientId", clientId)
            if (replyTo != null) put("replyToId", replyTo)
            if (fileId != null) {
                put("fileId", fileId)
                put("type", "document")
            }
        }
        val row = write(session, endpoints.hubMessages(channelId), charge)
        return messageFrom(row.obj("message")) ?: throw HubException(HubFailureKind.UNKNOWN)
    }

    override suspend fun edit(session: AuthSession, messageId: String, body: String): HubMessage {
        val row = write(
            session, endpoints.hubMessage(messageId),
            buildJsonObject { put("body", body) }, method = "PATCH",
        )
        return messageFrom(row.obj("message")) ?: throw HubException(HubFailureKind.UNKNOWN)
    }

    override suspend fun delete(session: AuthSession, messageId: String): HubMessage {
        val row = write(session, endpoints.hubMessage(messageId), method = "DELETE")
        return messageFrom(row.obj("message")) ?: throw HubException(HubFailureKind.UNKNOWN)
    }

    override suspend fun toggleReaction(
        session: AuthSession,
        messageId: String,
        emoji: String,
    ): HubMessage {
        val row = write(
            session, endpoints.hubReactions(messageId),
            buildJsonObject { put("emoji", emoji) },
        )
        return messageFrom(row.obj("message")) ?: throw HubException(HubFailureKind.UNKNOWN)
    }

    // ------------------------------------------------------------------
    // Adhésion, groupes, membres
    // ------------------------------------------------------------------

    override suspend fun markRead(
        session: AuthSession,
        channelId: String,
        at: Instant?,
    ): HubChannel = channelOf(
        write(
            session, endpoints.hubRead(channelId),
            buildJsonObject { if (at != null) put("at", Iso8601.format(at)) },
        ),
    )

    override suspend fun updateMembership(
        session: AuthSession,
        channelId: String,
        muted: Boolean?,
        favorite: Boolean?,
        archived: Boolean?,
    ): HubChannel = channelOf(
        write(
            session, endpoints.hubChannel(channelId),
            buildJsonObject {
                if (muted != null) put("muted", muted)
                if (favorite != null) put("favorite", favorite)
                if (archived != null) put("archived", archived)
            },
            method = "PATCH",
        ),
    )

    override suspend fun createGroup(
        session: AuthSession,
        name: String,
        memberIds: List<String>,
    ): HubChannel = channelOf(
        write(
            session, endpoints.hubChannels,
            buildJsonObject {
                put("name", name)
                put("memberIds", buildJsonArray { memberIds.forEach { add(JsonPrimitive(it)) } })
            },
        ),
    )

    override suspend fun rename(
        session: AuthSession,
        channelId: String,
        name: String,
    ): HubChannel = channelOf(
        write(
            session, endpoints.hubChannel(channelId),
            buildJsonObject { put("name", name) }, method = "PATCH",
        ),
    )

    override suspend fun addMembers(
        session: AuthSession,
        channelId: String,
        userIds: List<String>,
    ): HubChannel = channelOf(
        write(
            session, endpoints.hubMembers(channelId),
            buildJsonObject { put("userIds", buildJsonArray { userIds.forEach { add(JsonPrimitive(it)) } }) },
        ),
    )

    override suspend fun removeMember(
        session: AuthSession,
        channelId: String,
        userId: String,
    ): HubChannel = channelOf(
        write(
            session, endpoints.hubMembers(channelId),
            buildJsonObject { put("userId", userId) }, method = "DELETE",
        ),
    )

    override suspend fun leave(session: AuthSession, channelId: String) {
        write(session, endpoints.hubLeave(channelId))
    }

    override suspend fun closeGroup(session: AuthSession, channelId: String): HubChannel =
        channelOf(write(session, endpoints.hubChannel(channelId), method = "DELETE"))

    override suspend fun members(session: AuthSession, channelId: String): List<HubMember> {
        val row = read(session, endpoints.hubMembers(channelId)) ?: return emptyList()
        return row.rows("members").mapNotNull { membre ->
            val id = membre.text("userId") ?: return@mapNotNull null
            HubMember(
                userId = id,
                label = membre.text("label").orEmpty(),
                avatarUrl = membre.text("avatarUrl"),
                driverNumber = membre.text("driverNumber"),
                depotName = membre.text("depotName"),
                role = membre.text("role"),
                joinedAt = membre.instant("joinedAt"),
            )
        }
    }

    override suspend fun openDirect(session: AuthSession, userId: String): HubChannel =
        channelOf(write(session, endpoints.hubDirect, buildJsonObject { put("userId", userId) }))

    override suspend fun searchColleagues(
        session: AuthSession,
        query: String,
    ): List<HubColleague> {
        // Moins de deux caractères : la base rend une liste vide plutôt que
        // l'annuaire du réseau. On lui épargne l'aller-retour.
        if (query.length < 2) return emptyList()
        val row = read(session, endpoints.hubColleagues, mapOf("q" to query)) ?: return emptyList()
        return row.rows("colleagues").mapNotNull { collegue ->
            val label = collegue.text("label") ?: return@mapNotNull null
            HubColleague(
                userId = collegue.text("userId"),
                label = label,
                driverNumber = collegue.text("driverNumber"),
                depotName = collegue.text("depotName"),
                avatarUrl = collegue.text("avatarUrl"),
                hasAccount = collegue.bool("hasAccount") ?: (collegue.text("userId") != null),
                directChannelId = collegue.text("directChannelId"),
            )
        }
    }

    override suspend fun unread(session: AuthSession): HubUnread {
        val row = read(session, endpoints.hubUnread) ?: return HubUnread.AUCUN
        return HubUnread(
            notifications = row.int("notifications") ?: 0,
            discussions = row.int("discussions") ?: 0,
        )
    }

    // ------------------------------------------------------------------
    // Pièces jointes — trois temps, dont un hors du BFF
    // ------------------------------------------------------------------

    override suspend fun upload(
        session: AuthSession,
        channelId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        kind: HubFileKind,
    ): HubFile {
        // ① L'URL signée. Le BFF assainit le nom, vérifie le type et la taille.
        val signature = write(
            session, endpoints.hubSignUpload(channelId),
            buildJsonObject {
                put("fileName", fileName)
                put("mimeType", mimeType)
                put("sizeBytes", bytes.size)
            },
        )
        val url = signature.text("signedUrl") ?: throw HubException(HubFailureKind.UNKNOWN)
        val chemin = signature.text("path") ?: throw HubException(HubFailureKind.UNKNOWN)

        // ② Les octets, **en direct vers Storage**.
        val depot = guarded { client.putBytes(url, bytes, mimeType) }
        if (depot.code !in 200..299) throw HubException(HubFailureKind.FILE_NOT_UPLOADED)

        // ③ La déclaration. Sans elle, l'objet reste orphelin dans le bucket et
        // aucun message ne le porte.
        val row = write(
            session, endpoints.hubFiles(channelId),
            buildJsonObject {
                put("path", chemin)
                put("fileName", fileName)
                put("mimeType", mimeType)
                put("sizeBytes", bytes.size)
                put("kind", kind.name.lowercase())
            },
        )
        return fileFrom(row.obj("file")) ?: throw HubException(HubFailureKind.UNKNOWN)
    }

    // ------------------------------------------------------------------
    // Jeton d'appareil
    // ------------------------------------------------------------------

    override suspend fun registerPushToken(
        session: AuthSession,
        token: String,
        appEnv: String,
    ) {
        write(
            session, endpoints.hubPushToken,
            buildJsonObject {
                put("token", token)
                put("platform", "android")
                put("appEnv", appEnv)
            },
        )
    }

    override suspend fun unregisterPushToken(session: AuthSession, token: String) {
        write(
            session, endpoints.hubPushToken,
            buildJsonObject { put("token", token) }, method = "DELETE",
        )
    }

    // ------------------------------------------------------------------
    // Décodage — manuel, et tolérant
    // ------------------------------------------------------------------

    private fun channelOf(row: JsonObject): HubChannel =
        channelFrom(row.obj("channel")) ?: throw HubException(HubFailureKind.UNKNOWN)

    private fun channelFrom(row: JsonObject?): HubChannel? {
        if (row == null) return null
        val id = row.text("channelId") ?: return null
        return HubChannel(
            id = id,
            // ⚠️ Un type inconnu ne fait pas disparaître le canal : un canal
            // absent de la liste est un canal dont l'agent ne saura jamais qu'il
            // existe.
            kind = when (row.text("kind")) {
                "group" -> HubChannelKind.GROUP
                "direct" -> HubChannelKind.DIRECT
                "network" -> HubChannelKind.NETWORK
                "depot" -> HubChannelKind.DEPOT
                "support" -> HubChannelKind.SUPPORT
                else -> HubChannelKind.AUTRE
            },
            name = row.text("name").orEmpty(),
            mode = when (row.text("mode")) {
                "readonly" -> HubChannelMode.LECTURE_SEULE
                "announcement" -> HubChannelMode.ANNONCE
                else -> HubChannelMode.NORMAL
            },
            status = when (row.text("status")) {
                "closed" -> HubChannelStatus.FERME
                "paused" -> HubChannelStatus.SUSPENDU
                else -> HubChannelStatus.ACTIF
            },
            isAutomatic = row.text("creationMode") == "automatic",
            avatarUrl = row.text("avatarUrl"),
            peerUserId = row.text("peerUserId"),
            memberCount = row.int("memberCount") ?: 0,
            myRole = row.text("myRole"),
            canWrite = row.bool("canWrite") ?: false,
            canInvite = row.bool("canInvite") ?: false,
            canManage = row.bool("canManage") ?: false,
            canLeave = row.bool("canLeave") ?: false,
            isMuted = row.bool("isMuted") ?: false,
            isFavorite = row.bool("isFavorite") ?: false,
            isArchived = row.bool("isArchived") ?: false,
            lastReadAt = row.instant("lastReadAt"),
            unreadCount = row.int("unreadCount") ?: 0,
            lastMessage = row.obj("lastMessage")?.let { dernier ->
                val messageId = dernier.text("id") ?: return@let null
                HubLastMessage(
                    id = messageId,
                    bodyPreview = dernier.text("bodyPreview").orEmpty(),
                    senderId = dernier.text("senderId"),
                    senderLabel = dernier.text("senderLabel").orEmpty(),
                    type = dernier.text("type") ?: "text",
                    createdAt = dernier.instant("createdAt"),
                )
            },
        )
    }

    private fun messageFrom(row: JsonObject?): HubMessage? {
        if (row == null) return null
        val id = row.text("id") ?: return null
        val cree = row.instant("createdAt") ?: return null
        val supprime = row.instant("deletedAt")
            ?: if (row.bool("isDeleted") == true) cree else null
        return HubMessage(
            id = id,
            channelId = row.text("channelId").orEmpty(),
            senderId = row.text("senderId"),
            senderLabel = row.text("senderLabel").orEmpty(),
            type = when (row.text("type")) {
                "image" -> HubMessageType.IMAGE
                "document" -> HubMessageType.DOCUMENT
                "entity" -> HubMessageType.ENTITE
                "location" -> HubMessageType.LIEU
                "text" -> HubMessageType.TEXTE
                else -> HubMessageType.INCONNU
            },
            body = row.text("body").orEmpty(),
            // On ne garde que les chaînes : un JSON libre ne sert à personne côté
            // téléphone, et le client relit ce qu'il y a posé.
            metadata = row.obj("metadata")?.entries?.mapNotNull { (clef, valeur) ->
                val texte = runCatching { valeur.jsonPrimitive.contentOrNull }.getOrNull()
                if (texte == null) null else clef to texte
            }?.toMap().orEmpty(),
            clientId = row.text("clientId"),
            priority = row.text("priority") ?: "normal",
            createdAt = cree,
            editedAt = row.instant("editedAt"),
            deletedAt = supprime,
            activityAt = row.instant("activityAt") ?: cree,
            replyTo = row.obj("replyTo")?.let { reponse ->
                val reponseId = reponse.text("id") ?: return@let null
                HubReplyTo(
                    id = reponseId,
                    senderId = reponse.text("senderId"),
                    senderLabel = reponse.text("senderLabel").orEmpty(),
                    bodyPreview = reponse.text("bodyPreview").orEmpty(),
                )
            },
            replyCount = row.int("replyCount") ?: 0,
            reactions = row.rows("reactions").mapNotNull { reaction ->
                val emoji = reaction.text("emoji") ?: return@mapNotNull null
                HubReaction(
                    emoji = emoji,
                    count = reaction.int("count") ?: 0,
                    mine = reaction.bool("mine") ?: false,
                )
            },
            file = fileFrom(row.obj("file")),
        )
    }

    private fun fileFrom(row: JsonObject?): HubFile? {
        if (row == null) return null
        val id = row.text("id") ?: return null
        return HubFile(
            id = id,
            fileName = row.text("fileName") ?: "document",
            path = row.text("path").orEmpty(),
            mimeType = row.text("mimeType"),
            sizeBytes = row.long("sizeBytes") ?: 0,
            kind = when (row.text("kind")) {
                "image" -> HubFileKind.IMAGE
                "planning" -> HubFileKind.PLANNING
                else -> HubFileKind.DOCUMENT
            },
            signedUrl = row.text("signedUrl"),
            expiresAt = row.instant("expiresAt"),
        )
    }

    private fun JsonObject.text(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()

    private fun JsonObject.int(key: String): Int? =
        runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

    private fun JsonObject.long(key: String): Long? =
        runCatching { this[key]?.jsonPrimitive?.doubleOrNull?.toLong() }.getOrNull()

    private fun JsonObject.obj(key: String): JsonObject? =
        runCatching { this[key]?.jsonObject }.getOrNull()

    private fun JsonObject.rows(key: String): List<JsonObject> =
        runCatching { this[key]?.jsonArray?.mapNotNull { it.jsonObject } }.getOrNull().orEmpty()

    private fun JsonObject.instant(key: String): Instant? =
        text(key)?.let(Iso8601::parseOrNull)
}
