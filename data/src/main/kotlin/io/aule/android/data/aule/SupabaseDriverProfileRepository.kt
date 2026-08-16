package io.aule.android.data.aule

import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AvatarException
import io.aule.android.core.model.AvatarFailureKind
import io.aule.android.core.model.Depot
import io.aule.android.core.model.DriverProfile
import io.aule.android.core.model.DriverProfileUpdate
import io.aule.android.core.model.TransportNetwork
import io.aule.android.core.model.repository.DriverProfileRepository
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.core.network.RawHttpResponse
import io.aule.android.data.dto.DepotDto
import io.aule.android.data.dto.DriverProfileDto
import io.aule.android.data.dto.NetworkDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Client PostgREST de la fiche agent, sur OkHttp.
 *
 * Pas de SDK supabase-kt : le projet a déjà OkHttp (ADR-004), et PostgREST
 * n'est qu'un GET authentifié. L'absence de fiche est un 200 `[]`, pas un
 * 404 — le dire autrement ferait afficher une panne là où il n'y a simplement
 * pas d'agent rattaché.
 */
class SupabaseDriverProfileRepository(
    private val client: AuleHttpClient,
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val json: Json = AuleHttpClient.defaultJson,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : DriverProfileRepository {

    private val restBase: String
        get() = supabaseUrl.trimEnd('/') + "/rest/v1"

    private val storageBase: String
        get() = supabaseUrl.trimEnd('/') + "/storage/v1"

    override suspend fun fetchProfile(session: AuthSession): DriverProfile? {
        if (!configured) return null
        val response = client.getRaw(
            url = "$restBase/drivers",
            headers = restHeaders(session),
            query = mapOf(
                "select" to DRIVER_SELECT,
                "email" to "ilike.${session.user.email}",
                "limit" to "1",
            ),
        )
        val rows = decodeList(response, DriverProfileDto.serializer())
        return rows.firstOrNull()?.toDomain()
    }

    override suspend fun fetchDepots(session: AuthSession): List<Depot> {
        if (!configured) return emptyList()
        val response = client.getRaw(
            url = "$restBase/depots",
            headers = restHeaders(session),
            query = mapOf(
                "select" to "id,code,name,network_id",
                "order" to "code",
            ),
        )
        return decodeList(response, DepotDto.serializer()).map { it.toDomain() }
    }

    override suspend fun fetchNetworks(session: AuthSession): List<TransportNetwork> {
        if (!configured) return emptyList()
        val response = client.getRaw(
            url = "$restBase/networks",
            headers = restHeaders(session),
            query = mapOf(
                "select" to "id,code,name",
                "status" to "eq.active",
                "order" to "name",
            ),
        )
        return decodeList(response, NetworkDto.serializer()).map { it.toDomain() }
    }

    override suspend fun updateProfile(
        session: AuthSession,
        driverId: String,
        update: DriverProfileUpdate,
    ): DriverProfile {
        if (!configured) throw ApiException.BadRequest(0)
        val body = buildJsonObject {
            putNullable("first_name", update.firstName)
            putNullable("last_name", update.lastName)
            putNullable("phone", update.phone)
            putNullable("driver_number", update.driverNumber)
            putNullable("depot_id", update.depotId)
            putNullable("network_id", update.networkId)
        }.toString()
        return patchDriver(session, driverId, body)
    }

    override suspend fun uploadAvatar(
        session: AuthSession,
        driverId: String,
        bytes: ByteArray,
        contentType: String,
        extension: String,
    ): DriverProfile {
        if (bytes.isEmpty()) throw AvatarException(AvatarFailureKind.EMPTY)
        if (!configured) throw AvatarException(AvatarFailureKind.NOT_CONFIGURED)
        return try {
            deleteAvatarObjects(session)
            val path = "${session.user.id}/avatar.$extension"
            val uploaded = client.postBytes(
                url = "$storageBase/object/$AVATAR_BUCKET/$path",
                bytes = bytes,
                contentType = contentType,
                headers = restHeaders(session) + mapOf("x-upsert" to "false"),
            )
            if (uploaded.code !in 200..299) throw storageFailure(uploaded)
            val publicUrl =
                "$storageBase/object/public/$AVATAR_BUCKET/$path?v=${nowMillis()}"
            patchDriver(
                session,
                driverId,
                buildJsonObject { put("avatar_url", publicUrl) }.toString(),
            )
        } catch (failure: AvatarException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Transport) {
            throw AvatarException(AvatarFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw AvatarException(AvatarFailureKind.UNKNOWN)
        }
    }

    override suspend fun removeAvatar(
        session: AuthSession,
        driverId: String,
    ): DriverProfile {
        if (!configured) throw AvatarException(AvatarFailureKind.NOT_CONFIGURED)
        return try {
            deleteAvatarObjects(session)
            patchDriver(
                session,
                driverId,
                buildJsonObject { putNullable("avatar_url", null) }.toString(),
            )
        } catch (failure: AvatarException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Transport) {
            throw AvatarException(AvatarFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw AvatarException(AvatarFailureKind.UNKNOWN)
        }
    }

    override suspend fun fetchAvatarImage(url: String): ByteArray? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val response = client.getBytes(trimmed)
            response.body.takeIf { response.code in 200..299 && it.isNotEmpty() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun patchDriver(
        session: AuthSession,
        driverId: String,
        body: String,
    ): DriverProfile {
        if (!configured) throw ApiException.BadRequest(0)
        val response = client.patchRaw(
            url = "$restBase/drivers",
            jsonBody = body,
            headers = restHeaders(session) + mapOf("Prefer" to "return=representation"),
            query = mapOf("id" to "eq.$driverId"),
        )
        val rows = decodeList(response, DriverProfileDto.serializer())
        return rows.firstOrNull()?.toDomain()
            ?: throw ApiException.Decoding(IllegalStateException("empty representation"))
    }

    /**
     * Supprime les quatre extensions possibles avant un envoi. Sans ça, un
     * `avatar.png` resterait à côté du nouveau `avatar.jpg`, et l'upsert
     * se heurte parfois à une politique UPDATE absente.
     */
    private suspend fun deleteAvatarObjects(session: AuthSession) {
        val prefixes = AVATAR_EXTENSIONS.map { "${session.user.id}/avatar.$it" }
        val body = buildJsonObject {
            put("prefixes", buildJsonArray { prefixes.forEach { add(it) } })
        }.toString()
        val response = client.deleteRaw(
            url = "$storageBase/object/$AVATAR_BUCKET",
            jsonBody = body,
            headers = restHeaders(session),
        )
        when (response.code) {
            in 200..299, 404 -> Unit
            else -> throw storageFailure(response)
        }
    }

    private fun storageFailure(response: RawHttpResponse): AvatarException {
        val lower = response.body.lowercase()
        val kind = when {
            response.code == 404 || "bucket" in lower -> AvatarFailureKind.NOT_CONFIGURED
            response.code == 403 || "policy" in lower || "denied" in lower ->
                AvatarFailureKind.DENIED
            "mime" in lower || "content" in lower -> AvatarFailureKind.UNSUPPORTED
            else -> AvatarFailureKind.UNKNOWN
        }
        return AvatarException(kind)
    }

    private val configured: Boolean
        get() = supabaseUrl.isNotBlank() && publishableKey.isNotBlank()

    private fun restHeaders(session: AuthSession): Map<String, String> = mapOf(
        "apikey" to publishableKey,
        "Authorization" to "Bearer ${session.accessToken}",
    )

    private fun <T> decodeList(
        response: RawHttpResponse,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> {
        when (response.code) {
            in 200..299 -> Unit
            404 -> throw ApiException.NotFound()
            502, 503, 504 -> throw ApiException.UpstreamUnavailable(response.code)
            in 400..499 -> throw ApiException.BadRequest(response.code)
            else -> throw ApiException.Server(response.code)
        }
        return try {
            json.decodeFromString(ListSerializer(serializer), response.body)
        } catch (failure: Throwable) {
            throw ApiException.Decoding(failure)
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        key: String,
        value: String?,
    ) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private companion object {
        const val DRIVER_SELECT =
            "id,email,first_name,last_name,phone,driver_number," +
                "depot_id,network_id,avatar_url,msr_control,msr_intervention"
        const val AVATAR_BUCKET = "driver-avatars"
        val AVATAR_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")
    }
}
