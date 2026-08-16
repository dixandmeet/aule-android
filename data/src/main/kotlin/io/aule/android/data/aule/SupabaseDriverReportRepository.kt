package io.aule.android.data.aule

import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.DriverReport
import io.aule.android.core.model.DriverReportException
import io.aule.android.core.model.DriverReportFailureKind
import io.aule.android.core.model.repository.DriverReportRepository
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.core.network.RawHttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Client PostgREST des signalements, sur OkHttp.
 *
 * Deux allers-retours : d'abord l'`id` de la fiche `drivers` (la RLS
 * n'accepte que celui de la session), puis l'INSERT. Un type hors CHECK
 * revient en 400 — [DriverReportFailureKind.REJECTED], pas un silence.
 */
class SupabaseDriverReportRepository(
    private val client: AuleHttpClient,
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val json: Json = AuleHttpClient.defaultJson,
) : DriverReportRepository {

    private val restBase: String
        get() = supabaseUrl.trimEnd('/') + "/rest/v1"

    override suspend fun submit(
        session: AuthSession,
        report: DriverReport,
        driverServiceId: String?,
        vehicleId: String?,
    ) {
        if (!configured) throw DriverReportException(DriverReportFailureKind.NOT_CONFIGURED)
        try {
            val driverId = currentDriverId(session)
            val body = buildJsonObject {
                report.toInsert(
                    driverId = driverId,
                    driverServiceId = driverServiceId,
                    vehicleId = vehicleId,
                ).forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, value)
                        is Number -> put(key, JsonPrimitive(value))
                        else -> put(key, value.toString())
                    }
                }
            }.toString()
            val response = client.postRaw(
                url = "$restBase/driver_reports",
                jsonBody = body,
                headers = restHeaders(session) + mapOf("Prefer" to "return=minimal"),
            )
            when (response.code) {
                in 200..299 -> Unit
                401, 403 -> throw DriverReportException(DriverReportFailureKind.REJECTED)
                in 400..499 -> throw DriverReportException(DriverReportFailureKind.REJECTED)
                else -> throw DriverReportException(DriverReportFailureKind.UNKNOWN)
            }
        } catch (failure: DriverReportException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: ApiException.Transport) {
            throw DriverReportException(DriverReportFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw DriverReportException(DriverReportFailureKind.UNKNOWN)
        }
    }

    private suspend fun currentDriverId(session: AuthSession): String {
        val response = client.getRaw(
            url = "$restBase/drivers",
            headers = restHeaders(session),
            query = mapOf(
                "select" to "id",
                "email" to "ilike.${session.user.email}",
                "limit" to "1",
            ),
        )
        val rows = decodeList(response, DriverIdDto.serializer())
        return rows.firstOrNull()?.id
            ?: throw DriverReportException(DriverReportFailureKind.NO_DRIVER)
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
            401, 403 -> throw DriverReportException(DriverReportFailureKind.REJECTED)
            404 -> throw DriverReportException(DriverReportFailureKind.NO_DRIVER)
            in 400..499 -> throw DriverReportException(DriverReportFailureKind.REJECTED)
            else -> throw DriverReportException(DriverReportFailureKind.UNKNOWN)
        }
        return try {
            json.decodeFromString(ListSerializer(serializer), response.body)
        } catch (failure: Throwable) {
            throw DriverReportException(DriverReportFailureKind.UNKNOWN)
        }
    }
}

@Serializable
private data class DriverIdDto(val id: String)
