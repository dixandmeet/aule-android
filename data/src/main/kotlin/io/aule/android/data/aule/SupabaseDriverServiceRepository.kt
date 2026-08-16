package io.aule.android.data.aule

import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.DriverServiceException
import io.aule.android.core.model.DriverServiceFailureKind
import io.aule.android.core.model.PositionPublishRequest
import io.aule.android.core.model.ServiceHeartbeat
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.ServiceStartRequest
import io.aule.android.core.model.LineJourney
import io.aule.android.core.model.compareServiceLines
import io.aule.android.core.model.repository.DriverServiceRepository
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.core.network.RawHttpResponse
import io.aule.android.data.dto.DriverIdDto
import io.aule.android.data.dto.DriverServiceRowDto
import io.aule.android.data.dto.GtfsRouteDto
import io.aule.android.data.dto.GtfsStopTimeDto
import io.aule.android.data.dto.GtfsTripDto
import io.aule.android.data.dto.ServiceHeartbeatDto
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Client PostgREST de la prise de service, sur OkHttp.
 *
 * Les lignes viennent de `gtfs_routes`. Le démarrage passe par le RPC
 * `driver_service_start`, qui sérialise deux appareils avant l'index unique.
 */
class SupabaseDriverServiceRepository(
    private val client: AuleHttpClient,
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val json: Json = AuleHttpClient.defaultJson,
    private val now: () -> Instant = Instant::now,
) : DriverServiceRepository {

    private val restBase: String
        get() = supabaseUrl.trimEnd('/') + "/rest/v1"

    override suspend fun fetchLines(session: AuthSession): List<ServiceLine> {
        if (!configured) throw DriverServiceException(DriverServiceFailureKind.NOT_CONFIGURED)
        return try {
            val response = client.getRaw(
                url = "$restBase/gtfs_routes",
                headers = restHeaders(session),
                query = mapOf(
                    "select" to "route_id,route_short_name,route_long_name,route_type,route_color,network_id",
                    "order" to "route_short_name",
                ),
            )
            val lines = decodeList(response, GtfsRouteDto.serializer()).map { it.toDomain() }
            if (lines.isEmpty()) throw DriverServiceException(DriverServiceFailureKind.LINES_EMPTY)
            lines.sortedWith(::compareServiceLines)
        } catch (failure: DriverServiceException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: ApiException.Transport) {
            throw DriverServiceException(DriverServiceFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        }
    }

    override suspend fun fetchJourney(
        session: AuthSession,
        lineId: String,
        directionId: Int,
    ): LineJourney {
        if (!configured) throw DriverServiceException(DriverServiceFailureKind.NOT_CONFIGURED)
        val routeId = lineId.trim()
        if (routeId.isEmpty()) throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        return try {
            val trips = decodeList(
                client.getRaw(
                    url = "$restBase/gtfs_trips",
                    headers = restHeaders(session),
                    query = mapOf(
                        "select" to "trip_id,shape_id,direction_id",
                        "route_id" to "eq.$routeId",
                        "direction_id" to "eq.$directionId",
                        "limit" to "40",
                    ),
                ),
                GtfsTripDto.serializer(),
            )
            if (trips.isEmpty()) throw DriverServiceException(DriverServiceFailureKind.LINES_EMPTY)
            val tripFilter = trips.joinToString(",") { "\"${it.tripId}\"" }
            val times = decodeList(
                client.getRaw(
                    url = "$restBase/gtfs_stop_times",
                    headers = restHeaders(session),
                    query = mapOf(
                        "select" to "trip_id,stop_sequence,gtfs_stops(stop_id,stop_name,geom)",
                        "trip_id" to "in.($tripFilter)",
                        "order" to "stop_sequence",
                        "limit" to "5000",
                    ),
                ),
                GtfsStopTimeDto.serializer(),
            )
            val byTrip = times.groupBy { it.tripId }
            val trip = trips
                .filter { (byTrip[it.tripId]?.size ?: 0) >= 2 }
                .maxByOrNull { byTrip[it.tripId]?.size ?: 0 }
                ?: throw DriverServiceException(DriverServiceFailureKind.LINES_EMPTY)
            val stops = byTrip[trip.tripId]
                .orEmpty()
                .sortedBy { it.stopSequence }
                .mapNotNull { it.stop?.toDomain() }
            if (stops.size < 2) throw DriverServiceException(DriverServiceFailureKind.LINES_EMPTY)
            LineJourney(tripId = trip.tripId, stops = stops)
        } catch (failure: DriverServiceException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: ApiException.Transport) {
            throw DriverServiceException(DriverServiceFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        }
    }

    override suspend fun fetchActiveService(session: AuthSession): ActiveDriverService? {
        if (!configured) return null
        return try {
            val driverId = currentDriverId(session)
            val response = client.getRaw(
                url = "$restBase/driver_services",
                headers = restHeaders(session),
                query = mapOf(
                    "select" to ACTIVE_SELECT,
                    "driver_id" to "eq.$driverId",
                    "status" to "in.(active,paused)",
                    "order" to "created_at.desc",
                    "limit" to "1",
                ),
            )
            decodeList(response, DriverServiceRowDto.serializer())
                .firstOrNull()
                ?.toDomain(lineLabel = "")
        } catch (failure: DriverServiceException) {
            if (failure.kind == DriverServiceFailureKind.NO_DRIVER) return null
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: ApiException.Transport) {
            throw DriverServiceException(DriverServiceFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        }
    }

    override suspend fun startService(
        session: AuthSession,
        request: ServiceStartRequest,
    ): ActiveDriverService {
        if (!configured) throw DriverServiceException(DriverServiceFailureKind.NOT_CONFIGURED)
        if (fetchActiveService(session) != null) {
            throw DriverServiceException(DriverServiceFailureKind.ALREADY_ON_SERVICE)
        }
        return try {
            val body = buildJsonObject {
                put("p_line_id", request.lineId)
                put("p_direction_id", request.directionId)
                put("p_headsign", request.terminus)
                if (request.vehicleId == null) put("p_vehicle_id", JsonNull)
                else put("p_vehicle_id", request.vehicleId)
                if (request.trainNumber == null) put("p_train_number", JsonNull)
                else put("p_train_number", request.trainNumber)
            }.toString()
            val response = client.postRaw(
                url = "$restBase/rpc/driver_service_start",
                jsonBody = body,
                headers = restHeaders(session),
            )
            when (response.code) {
                in 200..299 -> {
                    val id = parseRpcUuid(response.body)
                    ActiveDriverService(
                        id = id,
                        lineId = request.lineId,
                        lineLabel = request.lineLabel,
                        directionId = request.directionId,
                        terminus = request.terminus,
                        startedAt = now(),
                        vehicleId = request.vehicleId,
                        trainNumber = request.trainNumber,
                    )
                }
                else -> throw mappedRpcFailure(response.body)
            }
        } catch (failure: DriverServiceException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: ApiException.Transport) {
            throw DriverServiceException(DriverServiceFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        }
    }

    override suspend fun endService(session: AuthSession, serviceId: String) {
        if (!configured) throw DriverServiceException(DriverServiceFailureKind.NOT_CONFIGURED)
        try {
            val body = buildJsonObject {
                put("status", "completed")
                put("end_time_real", now().toString())
            }.toString()
            val response = client.patchRaw(
                url = "$restBase/driver_services",
                jsonBody = body,
                headers = restHeaders(session) + mapOf("Prefer" to "return=representation"),
                query = mapOf("id" to "eq.$serviceId"),
            )
            val rows = decodeList(response, DriverServiceRowDto.serializer())
            if (rows.isEmpty()) throw DriverServiceException(DriverServiceFailureKind.REJECTED)
        } catch (failure: DriverServiceException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: ApiException.Transport) {
            throw DriverServiceException(DriverServiceFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        }
    }

    override suspend fun publishPosition(
        session: AuthSession,
        request: PositionPublishRequest,
    ): ServiceHeartbeat {
        if (!configured) throw DriverServiceException(DriverServiceFailureKind.NOT_CONFIGURED)
        return try {
            val body = buildJsonObject {
                put("p_driver_service_id", request.driverServiceId)
                put("p_latitude", request.latitude)
                put("p_longitude", request.longitude)
                if (request.vehicleId == null) put("p_vehicle_id", JsonNull)
                else put("p_vehicle_id", request.vehicleId)
                if (request.speed == null) put("p_speed", JsonNull)
                else put("p_speed", request.speed)
                if (request.heading == null) put("p_heading", JsonNull)
                else put("p_heading", request.heading)
                if (request.accuracy == null) put("p_accuracy", JsonNull)
                else put("p_accuracy", request.accuracy)
            }.toString()
            val response = client.postRaw(
                url = "$restBase/rpc/publish_position_with_state",
                jsonBody = body,
                headers = restHeaders(session),
            )
            when (response.code) {
                in 200..299 -> decodeHeartbeat(response)
                else -> throw mappedRpcFailure(response.body)
            }
        } catch (failure: DriverServiceException) {
            throw failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: ApiException.Transport) {
            throw DriverServiceException(DriverServiceFailureKind.NETWORK)
        } catch (_: Throwable) {
            throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
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
        return decodeList(response, DriverIdDto.serializer()).firstOrNull()?.id
            ?: throw DriverServiceException(DriverServiceFailureKind.NO_DRIVER)
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
            401, 403 -> throw DriverServiceException(DriverServiceFailureKind.REJECTED)
            in 400..499 -> throw DriverServiceException(DriverServiceFailureKind.REJECTED)
            else -> throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        }
        return try {
            json.decodeFromString(ListSerializer(serializer), response.body)
        } catch (failure: DriverServiceException) {
            throw failure
        } catch (_: Throwable) {
            throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        }
    }

    private fun decodeHeartbeat(response: RawHttpResponse): ServiceHeartbeat {
        val body = response.body.trim()
        if (body.isEmpty() || body == "null") {
            throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        }
        return try {
            json.decodeFromString(ServiceHeartbeatDto.serializer(), body).toDomain()
        } catch (_: Throwable) {
            throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        }
    }

    private fun parseRpcUuid(body: String): String {
        val trimmed = body.trim().trim('"')
        if (trimmed.isEmpty() || trimmed.startsWith("{")) {
            throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        }
        return trimmed
    }

    private fun mappedRpcFailure(body: String): DriverServiceException {
        val lower = body.lowercase()
        val kind = when {
            "driver_already_on_service" in lower -> DriverServiceFailureKind.ALREADY_ON_SERVICE
            "no_driver_profile" in lower -> DriverServiceFailureKind.NO_DRIVER
            "not_authenticated" in lower -> DriverServiceFailureKind.NOT_SIGNED_IN
            else -> DriverServiceFailureKind.REJECTED
        }
        return DriverServiceException(kind)
    }

    private companion object {
        const val ACTIVE_SELECT =
            "id,line_id,direction_id,headsign,vehicle_id,train_number," +
                "start_time_real,created_at"
    }
}
