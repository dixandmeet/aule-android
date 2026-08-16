package io.aule.android.data.aule

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.DriverServiceException
import io.aule.android.core.model.DriverServiceFailureKind
import io.aule.android.core.model.LineJourney
import io.aule.android.core.model.PositionPublishRequest
import io.aule.android.core.model.ScheduledTrip
import io.aule.android.core.model.ScheduledTripStop
import io.aule.android.core.model.ServiceHeartbeat
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.ServiceStartRequest
import io.aule.android.core.model.compareServiceLines
import io.aule.android.core.model.normalizeStopName
import io.aule.android.core.model.positionAtElapsed
import io.aule.android.core.model.repository.DriverServiceRepository
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.core.network.RawHttpResponse
import io.aule.android.data.dto.DriverIdDto
import io.aule.android.data.dto.DriverServiceRowDto
import io.aule.android.data.dto.GtfsCalendarDateDto
import io.aule.android.data.dto.GtfsCalendarDto
import io.aule.android.data.dto.GtfsRouteDto
import io.aule.android.data.dto.GtfsStopMetaDto
import io.aule.android.data.dto.GtfsStopTimeDto
import io.aule.android.data.dto.GtfsTripDepartureDto
import io.aule.android.data.dto.GtfsTripDto
import io.aule.android.data.dto.GtfsTripProfileDto
import io.aule.android.data.dto.GtfsTripProfileStopDto
import io.aule.android.data.dto.ServiceHeartbeatDto
import io.aule.android.data.dto.toCoordinate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    override suspend fun nearestActiveTrip(
        session: AuthSession,
        lineId: String,
        directionId: Int,
        destinationHint: String?,
        near: Coordinate,
        at: Instant,
    ): ScheduledTrip? {
        if (!configured) return null
        val routeId = lineId.trim()
        if (routeId.isEmpty()) return null
        return try {
            resolveNearestActiveTrip(
                session = session,
                routeId = routeId,
                directionId = directionId,
                destinationHint = destinationHint,
                near = near,
                at = at,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiException.Cancelled) {
            throw CancellationException()
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun resolveNearestActiveTrip(
        session: AuthSession,
        routeId: String,
        directionId: Int,
        destinationHint: String?,
        near: Coordinate,
        at: Instant,
    ): ScheduledTrip? {
        val route = decodeList(
            client.getRaw(
                url = "$restBase/gtfs_routes",
                headers = restHeaders(session),
                query = mapOf(
                    "select" to "route_id,route_short_name,route_long_name,route_type,route_color,network_id",
                    "route_id" to "eq.$routeId",
                    "limit" to "1",
                ),
            ),
            GtfsRouteDto.serializer(),
        ).firstOrNull() ?: return null
        val lineLabel = route.shortName?.trim().orEmpty().ifEmpty { routeId }

        var profiles = decodeList(
            client.getRaw(
                url = "$restBase/gtfs_trip_profiles",
                headers = restHeaders(session),
                query = buildMap {
                    put("select", "profile_id,direction_id,headsign,route_id")
                    put("route_id", "eq.$routeId")
                    if (directionId >= 0) put("direction_id", "eq.$directionId")
                },
            ),
            GtfsTripProfileDto.serializer(),
        )
        if (profiles.isEmpty()) return null

        val hint = destinationHint?.trim().orEmpty()
        if (directionId < 0 && hint.isNotEmpty()) {
            var bestScore = 0
            for (profile in profiles) {
                bestScore = maxOf(bestScore, directionScore(profile.headsign, hint))
            }
            if (bestScore > 0) {
                profiles = profiles.filter { directionScore(it.headsign, hint) == bestScore }
            }
        }

        val zone = ZoneId.of("Europe/Paris")
        val local = at.atZone(zone)
        val serviceDate = local.toLocalDate()
        val serviceIds = activeServiceIds(session, serviceDate)
        if (serviceIds.isEmpty()) return null
        val elapsedBase = local.toLocalTime().toSecondOfDay()
        val profileIds = profiles.map { it.profileId }

        val departureRows = fetchAllPages { offset, limit ->
            decodeList(
                client.getRaw(
                    url = "$restBase/gtfs_trip_departures",
                    headers = restHeaders(session),
                    query = mapOf(
                        "select" to "departure_id,profile_id,start_seconds",
                        "profile_id" to "in.(${profileIds.joinToString(",")})",
                        "service_id" to "in.(${serviceIds.joinToString(",")})",
                        "and" to "(start_seconds.gte.${elapsedBase - 4 * 3600},start_seconds.lte.$elapsedBase)",
                        "order" to "start_seconds",
                        "offset" to "$offset",
                        "limit" to "$limit",
                    ),
                ),
                GtfsTripDepartureDto.serializer(),
            )
        }
        if (departureRows.isEmpty()) return null

        val usedProfileIds = departureRows.map { it.profileId }.distinct()
        val profileStopRows = fetchAllPages { offset, limit ->
            decodeList(
                client.getRaw(
                    url = "$restBase/gtfs_trip_profile_stops",
                    headers = restHeaders(session),
                    query = mapOf(
                        "select" to "profile_id,stop_sequence,stop_id,offset_seconds",
                        "profile_id" to "in.(${usedProfileIds.joinToString(",")})",
                        "order" to "profile_id,stop_sequence",
                        "offset" to "$offset",
                        "limit" to "$limit",
                    ),
                ),
                GtfsTripProfileStopDto.serializer(),
            )
        }

        val stopIds = profileStopRows.map { it.stopId }.distinct()
        val stopMeta = if (stopIds.isEmpty()) {
            emptyMap()
        } else {
            decodeList(
                client.getRaw(
                    url = "$restBase/gtfs_stops",
                    headers = restHeaders(session),
                    query = mapOf(
                        "select" to "stop_id,stop_name,geom",
                        "stop_id" to "in.(${stopIds.joinToString(",")})",
                    ),
                ),
                GtfsStopMetaDto.serializer(),
            ).associateBy { it.stopId }
        }

        data class ResolverStop(
            val name: String,
            val position: Coordinate?,
            val offsetSeconds: Int,
            val stopId: String,
        )

        val stopsByProfile = mutableMapOf<String, MutableList<ResolverStop>>()
        for (row in profileStopRows.sortedWith(compareBy({ it.profileId }, { it.stopSequence }))) {
            val meta = stopMeta[row.stopId]
            stopsByProfile.getOrPut(row.profileId) { mutableListOf() }.add(
                ResolverStop(
                    name = meta?.stopName?.trim().orEmpty(),
                    position = meta?.geom.toCoordinate(),
                    offsetSeconds = row.offsetSeconds,
                    stopId = row.stopId,
                ),
            )
        }

        val headsignByProfile = profiles.associate { it.profileId to it.headsign }
        var best: GtfsTripDepartureDto? = null
        var bestDistance = 500.0
        for (row in departureRows) {
            val stops = stopsByProfile[row.profileId] ?: continue
            if (stops.size < 2) continue
            val elapsed = elapsedBase - row.startSeconds
            if (elapsed < 0) continue
            if (elapsed > stops.last().offsetSeconds + 180) continue
            val position = positionAtElapsed(
                offsets = stops.map { it.offsetSeconds },
                positions = stops.map { it.position },
                elapsedSeconds = elapsed,
            ) ?: continue
            val distance = GeoMath.distance(near, position)
            if (distance >= bestDistance) continue
            bestDistance = distance
            best = row
        }
        val chosen = best ?: return null
        val chosenStops = stopsByProfile[chosen.profileId] ?: return null
        val midnight = serviceDate.atStartOfDay(zone).toInstant()
        val timedStops = chosenStops.map { stop ->
            ScheduledTripStop(
                stopId = stop.stopId,
                name = stop.name.ifEmpty { "Arrêt" },
                coordinate = stop.position,
                passageAt = midnight.plusSeconds(
                    (chosen.startSeconds + stop.offsetSeconds).toLong(),
                ),
            )
        }
        val headsign = headsignByProfile[chosen.profileId]?.trim().orEmpty()
        val terminus = timedStops.lastOrNull()?.name?.trim().orEmpty()
        return ScheduledTrip(
            departureId = chosen.departureId,
            lineId = routeId,
            lineLabel = lineLabel,
            directionId = directionId.coerceAtLeast(0),
            destination = when {
                terminus.isNotEmpty() && terminus != "Arrêt" -> terminus
                headsign.isNotEmpty() -> headsign
                else -> hint
            },
            stops = timedStops,
        )
    }

    private suspend fun activeServiceIds(session: AuthSession, date: LocalDate): List<String> {
        val iso = date.toString()
        val weekday = date.dayOfWeek.value
        val regular = decodeList(
            client.getRaw(
                url = "$restBase/gtfs_calendar",
                headers = restHeaders(session),
                query = mapOf(
                    "select" to "service_id,runs_on",
                    "start_date" to "lte.$iso",
                    "end_date" to "gte.$iso",
                ),
            ),
            GtfsCalendarDto.serializer(),
        )
        val exceptions = decodeList(
            client.getRaw(
                url = "$restBase/gtfs_calendar_dates",
                headers = restHeaders(session),
                query = mapOf(
                    "select" to "service_id,exception_type",
                    "service_date" to "eq.$iso",
                ),
            ),
            GtfsCalendarDateDto.serializer(),
        )
        val removed = exceptions.filter { it.exceptionType == 2 }.map { it.serviceId }.toSet()
        val added = exceptions.filter { it.exceptionType == 1 }.map { it.serviceId }.toSet()
        val active = mutableSetOf<String>()
        for (row in regular) {
            val runsToday = row.runsOn.size >= weekday && row.runsOn[weekday - 1]
            if (runsToday && row.serviceId !in removed) active += row.serviceId
        }
        active += added
        return active.toList()
    }

    private fun directionScore(headsign: String?, requested: String): Int {
        val actual = normalizeStopName(headsign.orEmpty())
        if (actual.isEmpty()) return 0
        val expected = normalizeStopName(requested)
        if (actual == expected) return 100
        if (actual.contains(expected) || expected.contains(actual)) return 80
        val aliases = requested.split('/', '|')
            .map { normalizeStopName(it) }
            .filter { it.length >= 3 }
        for (alias in aliases) {
            if (actual.contains(alias) || alias.contains(actual)) return 70
        }
        return 0
    }

    private suspend fun <T> fetchAllPages(
        pageSize: Int = 1000,
        fetch: suspend (offset: Int, limit: Int) -> List<T>,
    ): List<T> {
        val all = mutableListOf<T>()
        var offset = 0
        while (true) {
            val page = fetch(offset, pageSize)
            all += page
            if (page.size < pageSize) break
            offset += pageSize
        }
        return all
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
