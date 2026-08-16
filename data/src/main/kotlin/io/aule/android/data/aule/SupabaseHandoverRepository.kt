package io.aule.android.data.aule

import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.HandoverEngagement
import io.aule.android.core.model.HandoverException
import io.aule.android.core.model.HandoverFailureKind
import io.aule.android.core.model.HandoverSummary
import io.aule.android.core.model.HandoverTarget
import io.aule.android.core.model.HandoverTrack
import io.aule.android.core.model.repository.HandoverRepository
import java.time.Instant
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.core.network.RawHttpResponse
import io.aule.android.data.dto.HandoverEngagementDto
import io.aule.android.data.dto.HandoverSummaryDto
import io.aule.android.data.dto.HandoverTargetDto
import io.aule.android.data.dto.HandoverTrackDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Client PostgREST de la relève, sur OkHttp.
 *
 * Lookup, engagement, suivi, arrêt de relève, confirmation, annulation,
 * reprise. `handover_set_stop` enregistre le point choisi par l'arrivant.
 */
class SupabaseHandoverRepository(
    private val client: AuleHttpClient,
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val json: Json = AuleHttpClient.defaultJson,
) : HandoverRepository {

    private val restBase: String
        get() = supabaseUrl.trimEnd('/') + "/rest/v1"

    override suspend fun lookup(
        session: AuthSession,
        lineId: String,
        query: String,
    ): List<HandoverTarget> {
        if (!configured) throw HandoverException(HandoverFailureKind.NOT_CONFIGURED)
        val trimmed = query.trim()
        if (lineId.isBlank() || trimmed.isEmpty()) return emptyList()
        return try {
            val body = buildJsonObject {
                put("p_line_id", lineId)
                put("p_query", trimmed)
            }.toString()
            val response = client.postRaw(
                url = "$restBase/rpc/handover_lookup",
                jsonBody = body,
                headers = restHeaders(session),
            )
            decodeList(response, HandoverTargetDto.serializer()).map { it.toDomain() }
        } catch (failure: HandoverException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: ApiException.Transport) {
            throw HandoverException(HandoverFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw HandoverException(HandoverFailureKind.UNKNOWN)
        }
    }

    override suspend fun request(
        session: AuthSession,
        outgoingServiceId: String,
    ): HandoverSummary {
        if (!configured) throw HandoverException(HandoverFailureKind.NOT_CONFIGURED)
        return postSummary(session, "handover_request") {
            put("p_outgoing_service_id", outgoingServiceId)
        }
    }

    override suspend fun track(session: AuthSession, handoverId: String): HandoverTrack {
        if (!configured) throw HandoverException(HandoverFailureKind.NOT_CONFIGURED)
        return try {
            val body = buildJsonObject {
                put("p_handover_id", handoverId)
            }.toString()
            val response = client.postRaw(
                url = "$restBase/rpc/handover_track",
                jsonBody = body,
                headers = restHeaders(session),
            )
            decodeObject(response, HandoverTrackDto.serializer()).toDomain()
        } catch (failure: HandoverException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: ApiException.Transport) {
            throw HandoverException(HandoverFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw HandoverException(HandoverFailureKind.UNKNOWN)
        }
    }

    override suspend fun setStop(
        session: AuthSession,
        handoverId: String,
        stopId: String,
        stopName: String,
        latitude: Double,
        longitude: Double,
        plannedAt: Instant?,
    ): HandoverSummary {
        if (!configured) throw HandoverException(HandoverFailureKind.NOT_CONFIGURED)
        return postSummary(session, "handover_set_stop") {
            put("p_handover_id", handoverId)
            put("p_stop_id", stopId)
            put("p_stop_name", stopName)
            put("p_stop_lat", latitude)
            put("p_stop_lon", longitude)
            if (plannedAt == null) put("p_planned_at", JsonNull)
            else put("p_planned_at", plannedAt.toString())
        }
    }

    override suspend fun confirm(session: AuthSession, handoverId: String): HandoverSummary {
        if (!configured) throw HandoverException(HandoverFailureKind.NOT_CONFIGURED)
        return postSummary(session, "handover_confirm") {
            put("p_handover_id", handoverId)
        }
    }

    override suspend fun cancel(
        session: AuthSession,
        handoverId: String,
        reason: String?,
    ): HandoverSummary? {
        if (!configured) throw HandoverException(HandoverFailureKind.NOT_CONFIGURED)
        return try {
            postSummary(session, "handover_cancel") {
                put("p_handover_id", handoverId)
                if (reason == null) put("p_reason", JsonNull)
                else put("p_reason", reason)
            }
        } catch (failure: HandoverException) {
            if (failure.kind == HandoverFailureKind.NOT_FOUND) null else throw failure
        }
    }

    override suspend fun activeForMe(session: AuthSession): HandoverEngagement? {
        if (!configured) return null
        return try {
            val response = client.postRaw(
                url = "$restBase/rpc/handover_active_for_me",
                jsonBody = "{}",
                headers = restHeaders(session),
            )
            decodeNullable(response, HandoverEngagementDto.serializer())?.toDomain()
        } catch (failure: HandoverException) {
            if (failure.kind == HandoverFailureKind.NO_DRIVER) return null
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: ApiException.Transport) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun postSummary(
        session: AuthSession,
        rpc: String,
        body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): HandoverSummary {
        return try {
            val response = client.postRaw(
                url = "$restBase/rpc/$rpc",
                jsonBody = buildJsonObject(body).toString(),
                headers = restHeaders(session),
            )
            decodeObject(response, HandoverSummaryDto.serializer()).toDomain()
        } catch (failure: HandoverException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: ApiException.Transport) {
            throw HandoverException(HandoverFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw HandoverException(HandoverFailureKind.UNKNOWN)
        }
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
        ensureOk(response)
        return try {
            json.decodeFromString(ListSerializer(serializer), response.body)
        } catch (_: Throwable) {
            throw HandoverException(HandoverFailureKind.UNKNOWN)
        }
    }

    private fun <T> decodeObject(
        response: RawHttpResponse,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T {
        ensureOk(response)
        val body = response.body.trim()
        if (body.isEmpty() || body == "null") {
            throw HandoverException(HandoverFailureKind.UNKNOWN)
        }
        return try {
            json.decodeFromString(serializer, body)
        } catch (_: Throwable) {
            throw HandoverException(HandoverFailureKind.UNKNOWN)
        }
    }

    private fun <T> decodeNullable(
        response: RawHttpResponse,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T? {
        ensureOk(response)
        val body = response.body.trim()
        if (body.isEmpty() || body == "null") return null
        return try {
            json.decodeFromString(serializer, body)
        } catch (_: Throwable) {
            throw HandoverException(HandoverFailureKind.UNKNOWN)
        }
    }

    private fun ensureOk(response: RawHttpResponse) {
        when (response.code) {
            in 200..299 -> Unit
            else -> throw mappedRpcFailure(response.body)
        }
    }

    private fun mappedRpcFailure(body: String): HandoverException {
        val lower = body.lowercase()
        val kind = when {
            "no_driver_profile" in lower -> HandoverFailureKind.NO_DRIVER
            "not_authenticated" in lower -> HandoverFailureKind.NOT_SIGNED_IN
            "target_service_not_active" in lower -> HandoverFailureKind.TARGET_NOT_ACTIVE
            "cannot_relieve_self" in lower -> HandoverFailureKind.CANNOT_RELIEVE_SELF
            "target_other_network" in lower -> HandoverFailureKind.OTHER_NETWORK
            "already_relieving" in lower -> HandoverFailureKind.ALREADY_RELIEVING
            "already_being_relieved" in lower -> HandoverFailureKind.ALREADY_BEING_RELIEVED
            "incoming_already_on_service" in lower -> HandoverFailureKind.ALREADY_ON_SERVICE
            "handover_closed" in lower -> HandoverFailureKind.CLOSED
            "handover_not_found" in lower -> HandoverFailureKind.NOT_FOUND
            "handover_already_completed" in lower -> HandoverFailureKind.ALREADY_COMPLETED
            "not_a_party" in lower -> HandoverFailureKind.NOT_A_PARTY
            "not_my_service" in lower -> HandoverFailureKind.NOT_A_PARTY
            else -> HandoverFailureKind.REJECTED
        }
        return HandoverException(kind)
    }
}
