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
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
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
    /** Optionnel : un test qui ne s'intéresse pas au journal n'en fabrique pas. */
    private val logger: AuleLogger? = null,
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

    /** Le parcours de référence : le premier de [fetchJourneyProfiles]. */
    override suspend fun fetchJourney(
        session: AuthSession,
        lineId: String,
        directionId: Int,
        expectedTerminus: String,
    ): LineJourney = fetchJourneyProfiles(session, lineId, directionId, expectedTerminus).first()

    override suspend fun fetchJourneyProfiles(
        session: AuthSession,
        lineId: String,
        directionId: Int,
        expectedTerminus: String,
    ): List<LineJourney> {
        if (!configured) throw DriverServiceException(DriverServiceFailureKind.NOT_CONFIGURED)
        val routeId = lineId.trim()
        if (routeId.isEmpty()) throw DriverServiceException(DriverServiceFailureKind.UNKNOWN)
        return try {
            val profiles = fetchDirectProfiles(session, routeId, directionId, expectedTerminus)
            // ⚠️ **Le relais ne s'assemble qu'au parcours de référence.**
            //
            // Le montage 1 + 1B recolle deux routes GTFS scindées par des
            // travaux ; croiser leurs branches donnerait six combinaisons dont
            // la plupart ne circulent pas. Les autres parcours de la 1 restent
            // proposés tels quels — ce sont ses branches, et elles se choisissent
            // sur la partie que le relais ne touche pas.
            val merged = mergeRelayJourneyIfApplicable(session, routeId, directionId, profiles.first())
            listOf(merged) + profiles.drop(1)
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

    /**
     * Tous les parcours d'un sens, **le référence en tête**.
     *
     * Voir le choix du parcours de référence plus bas : c'est lui qui décide de
     * ce que la fiche affiche sans qu'on demande rien. Les autres suivent par
     * longueur croissante, ce qui met les branches voisines côte à côte et
     * relègue les détours au bout — l'ordre dans lequel un menu se lit.
     */
    private suspend fun fetchDirectProfiles(
        session: AuthSession,
        routeId: String,
        directionId: Int,
        expectedTerminus: String = "",
    ): List<LineJourney> {
        val trips = decodeList(
            client.getRaw(
                url = "$restBase/gtfs_trips",
                headers = restHeaders(session),
                query = mapOf(
                    "select" to "trip_id,shape_id,direction_id",
                    "route_id" to "eq.$routeId",
                    "direction_id" to "eq.$directionId",
                    // ⚠️ **Un ordre explicite, et ce n'est pas cosmétique.**
                    // PostgREST n'en garantit aucun sans lui : c'est celui que le
                    // planificateur choisit, et il peut changer d'un jour à
                    // l'autre. Tout le soin pris plus bas à départager les
                    // égalités « pour que deux lancements peignent la même
                    // carte » ne sert à rien si l'échantillon d'entrée, lui,
                    // varie.
                    "order" to "trip_id",
                    "limit" to "$TRIP_WINDOW",
                ),
            ),
            GtfsTripDto.serializer(),
        )
        if (trips.isEmpty()) throw DriverServiceException(DriverServiceFailureKind.LINES_EMPTY)
        val distinctShapes = trips.distinctBy { it.shapeId ?: it.tripId }
        val candidates = distinctShapes.take(MAX_PROFILES)
        val tripFilter = candidates.joinToString(",") { "\"${it.tripId}\"" }
        val times = decodeList(
            client.getRaw(
                url = "$restBase/gtfs_stop_times",
                headers = restHeaders(session),
                query = mapOf(
                    "select" to "trip_id,stop_sequence,gtfs_stops(stop_id,stop_name,geom)",
                    "trip_id" to "in.($tripFilter)",
                    "order" to "trip_id,stop_sequence",
                    "limit" to "5000",
                ),
            ),
            GtfsStopTimeDto.serializer(),
        )
        val byTrip = times.groupBy { it.tripId }
        val served = candidates
            .map { candidate -> candidate to byTrip[candidate.tripId].orEmpty().sortedBy { it.stopSequence } }
            .filter { (_, sequence) -> sequence.size >= 2 }

        // ## Le parcours de référence est le plus **court** entre les deux mêmes bouts
        //
        // ⚠️ Une ligne n'a pas un parcours par sens : la C1 en publie douze. Ils
        // relient tous Gare de Chantenay à Haluchère, et ne diffèrent que par ce
        // qu'ils traversent — 30 arrêts pour le service courant, 37 et 39 pour
        // des courses qui ajoutent un crochet par Procé.
        //
        // On retenait le plus fourni. C'est exactement l'inverse de ce qu'il
        // fallait : entre deux mêmes terminus, **des arrêts en plus sont un
        // détour**, jamais le trajet ordinaire. La carte peignait donc neuf
        // arrêts que la C1 ne dessert pas, très loin de son tracé — signalé à
        // l'écran, et c'est ce qui a renversé cette règle.
        //
        // Le filtre sur les extrémités est ce qui rend le « plus court » sûr :
        // une course partielle est plus courte elle aussi, mais elle s'arrête en
        // chemin, et on l'écarte parce qu'elle ne relie pas les mêmes bouts que
        // le parcours complet. À égalité de longueur, le parcours est départagé
        // par son identifiant : deux lancements doivent peindre la même carte.
        // ⚠️ **Départagé jusqu'au bout, y compris ici.** `maxByOrNull` rend le
        // premier maximum rencontré, donc l'ordre où PostgREST a servi les
        // courses : deux branches de même longueur — Beaujoire et Babinière en
        // ont quinze chacune — auraient fait la référence à tour de rôle d'un
        // lancement à l'autre. L'identifiant tranche, et la fiche s'ouvre
        // toujours sur la même.
        // ## Ce que « le plus long » ne peut pas savoir, et d'où vient le repère
        //
        // ⚠️ Les deux pas qui suivent tiennent l'un par l'autre : les bouts
        // viennent du parcours le plus long, puis on retient le plus court qui
        // les relie. Le second écarte les détours ; le premier **suppose** que le
        // plus long relie les vrais terminus.
        //
        // Une course qui pousse au-delà du terminus — un dépôt, une antenne de
        // service — renverse cette supposition, et rien dans sa forme ne la
        // distingue d'un trajet ordinaire : elle contient l'autre, exactement
        // comme un parcours complet contient une course partielle. Le repère ne
        // peut donc pas sortir des courses.
        //
        // Il vient du terminus que le référentiel **annonce** pour ce sens
        // (`route_long_name`, via `ServiceLine.directions`). Quand un parcours
        // finit là, ce sont ceux-là qui donnent les bouts ; sinon — le terminus
        // manque, ou il nomme une paire comme « Beaujoire / Babinière » — la
        // règle d'origine reprend la main. **Un repère qu'on ne sait pas lire ne
        // doit rien décider.**
        val wanted = normalizeStopName(expectedTerminus)
        val ending = served.filter { (_, candidateStops) ->
            wanted.isNotEmpty() && candidateStops.terminals().second == wanted
        }
        val fullest = (ending.ifEmpty { served })
            .sortedWith(
                compareByDescending<Pair<GtfsTripDto, List<GtfsStopTimeDto>>> { (_, sequence) ->
                    sequence.size
                }.thenBy { (candidate, _) -> candidate.shapeId ?: candidate.tripId },
            )
            .firstOrNull()
            ?: throw DriverServiceException(DriverServiceFailureKind.LINES_EMPTY)
        val terminals = fullest.second.terminals()
        val (trip, sequence) = served
            .filter { (_, candidateStops) -> candidateStops.terminals() == terminals }
            .minWithOrNull(
                compareBy(
                    { (_, candidateStops) -> candidateStops.size },
                    { (candidate, _) -> candidate.shapeId ?: candidate.tripId },
                ),
            )
            ?: fullest

        val stops = sequence.mapNotNull { it.stop?.toDomain() }
        if (stops.size < 2) throw DriverServiceException(DriverServiceFailureKind.LINES_EMPTY)
        // Le choix ci-dessus est invisible partout ailleurs : c'est lui qui
        // décide de ce que la carte peindra, et rien à l'écran ne dit qu'il a eu
        // lieu. On le dit ici, une fois par ouverture de fiche.
        logger?.info(
            LogDomain.NET,
            "Desserte $routeId sens $directionId : ${trips.size} courses, " +
                "${distinctShapes.size} parcours distincts, " +
                "${candidates.size} candidats [" +
                served.joinToString(" | ") { (candidate, candidateStops) ->
                    val ends = candidateStops.endpointNames()
                    "${candidate.shapeId ?: candidate.tripId}=${candidateStops.size} " +
                        "(${ends.first} → ${ends.second})"
                } +
                "] — retenu ${trip.shapeId ?: trip.tripId} (${stops.size} arrêts).",
        )
        // ## Les trois troncatures, et pourquoi elles se disent
        //
        // Chacune se lit à l'écran comme une affirmation sur le réseau — « cette
        // ligne n'a pas d'autre branche », « elle ne dessert que ça » — alors
        // qu'aucune n'en est une : ce sont des plafonds de requête. C'est la
        // règle d'iOS pour le plafond de dessertes, appliquée aux trois endroits
        // où elle mord ici.
        if (trips.size >= TRIP_WINDOW) {
            logger?.warn(
                LogDomain.NET,
                "Desserte $routeId sens $directionId : échantillon de courses plafonné " +
                    "à $TRIP_WINDOW — des parcours peuvent manquer.",
            )
        }
        if (distinctShapes.size > MAX_PROFILES) {
            logger?.warn(
                LogDomain.NET,
                "Desserte $routeId sens $directionId : " +
                    "${distinctShapes.size - MAX_PROFILES} parcours au-delà du plafond, non lu(s) " +
                    "[${distinctShapes.drop(MAX_PROFILES).joinToString(", ") {
                        it.shapeId ?: it.tripId
                    }}].",
            )
        }
        val mute = candidates.filterNot { candidate ->
            served.any { it.first.tripId == candidate.tripId }
        }
        if (mute.isNotEmpty()) {
            logger?.warn(
                LogDomain.NET,
                "Desserte $routeId sens $directionId : " +
                    "${mute.size} candidat(s) sans desserte lisible " +
                    "[${mute.joinToString(", ") { it.shapeId ?: it.tripId }}].",
            )
        }
        // Le référence d'abord, puis les autres du plus court au plus long : ce
        // sont les branches qui voisinent le trajet ordinaire, et les détours qui
        // s'en éloignent. Un menu se lit dans cet ordre-là.
        val others = served
            .filterNot { (candidate, _) -> candidate.tripId == trip.tripId }
            .sortedWith(
                compareBy(
                    { (_, candidateStops) -> candidateStops.size },
                    { (candidate, _) -> candidate.shapeId ?: candidate.tripId },
                ),
            )
            .mapNotNull { (candidate, candidateStops) ->
                val candidateDomain = candidateStops.mapNotNull { it.stop?.toDomain() }
                if (candidateDomain.size < 2) {
                    null
                } else {
                    LineJourney(
                        tripId = candidate.tripId,
                        stops = candidateDomain,
                        profileId = candidate.shapeId ?: candidate.tripId,
                    )
                }
            }

        return listOf(
            LineJourney(
                tripId = trip.tripId,
                stops = stops,
                profileId = trip.shapeId ?: trip.tripId,
            ),
        ) + others
    }

    /**
     * Les deux bouts d'un parcours, par quoi on reconnaît qu'il est complet.
     *
     * Par **nom** et non par identifiant : un même terminus porte plusieurs
     * quais, et deux courses qui finissent au même endroit n'y arrivent pas
     * forcément par le même.
     *
     * ⚠️ **Et par nom normalisé.** Le référentiel écrit « Hôtel Dieu » et
     * « HOTEL-DIEU » pour le même lieu ; comparés bruts, deux courses qui
     * finissent au même endroit passeraient pour deux parcours de bouts
     * différents, et le filtre ci-dessous écarterait le parcours ordinaire au
     * profit d'un détour. C'est la règle de [normalizeStopName], celle qu'applique
     * déjà le menu des branches.
     */
    private fun List<GtfsStopTimeDto>.terminals(): Pair<String, String> =
        normalizeStopName(first().stop?.stopName.orEmpty()) to
            normalizeStopName(last().stop?.stopName.orEmpty())

    /** Les deux bouts **tels qu'ils s'écrivent**, pour le journal et rien d'autre. */
    private fun List<GtfsStopTimeDto>.endpointNames(): Pair<String, String> =
        first().stop?.stopName.orEmpty() to last().stop?.stopName.orEmpty()

    /**
     * Reconstitue la ligne 1 complète quand le jeu GTFS est scindé par des travaux.
     *
     * Dans le jeu GTFS Naolib en période de travaux, la ligne 1 de tramway a été coupée
     * au centre : la ligne « 1 » ne contient que les 15 arrêts entre Beaujoire et Commerce,
     * tandis que le bus relais « 1B » assure les 18 arrêts entre Hôtel Dieu (Commerce)
     * et François Mitterrand. Si le terminus « François Mitterrand » n'est pas dans la
     * desserte principale, on assemble les deux tronçons pour retrouver les 32 arrêts
     * continus de la ligne.
     */
    private suspend fun mergeRelayJourneyIfApplicable(
        session: AuthSession,
        routeId: String,
        directionId: Int,
        primary: LineJourney,
    ): LineJourney {
        if (routeId != "1") return primary
        if (primary.stops.any { it.name.contains("Mitterrand", ignoreCase = true) }) {
            return primary
        }
        // Le parcours de référence du relais, et lui seul : ses autres courses
        // sont partielles, et le filtre sur les extrémités les a déjà écartées.
        val relay = runCatching { fetchDirectProfiles(session, "1B", directionId).first() }
            .getOrNull()
            ?: return primary

        val mergedStops = if (directionId == 0) {
            // Sens 0 : tronçon Est (1 : Beaujoire -> Commerce) suivi du tronçon Ouest (1B : Hôtel Dieu -> François Mitterrand)
            val eastStops = primary.stops
            val westStops = relay.stops.filterNot { it.name.equals("Hôtel Dieu", ignoreCase = true) }
            eastStops + westStops
        } else {
            // Sens 1 : tronçon Ouest (1B : François Mitterrand -> Hôtel Dieu) suivi du tronçon Est (1 : Commerce -> Beaujoire)
            val westStops = relay.stops.filterNot { it.name.equals("Hôtel Dieu", ignoreCase = true) }
            val eastStops = primary.stops
            westStops + eastStops
        }
        // L'identité reste celle de la branche de la ligne 1 : c'est elle qu'un
        // menu propose, et le relais est la même portion pour toutes.
        return primary.copy(stops = mergedStops)
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
        return activeServiceIds(regular = regular, exceptions = exceptions, date = date)
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

        /**
         * Combien de courses on lit pour y chercher les parcours d'un sens.
         *
         * Le plafond n'est pas là pour choisir, il est là pour qu'une ligne
         * aberrante ne parte pas en requête sans fin. Les lignes du réseau en
         * publient entre cinq et quinze — mesuré le 05/09/2026 sur la C1 (7),
         * la C3 (5), la 1 (14) et la 2 (6) —, donc il ne mord pas. **S'il
         * mordait, on le dirait** : c'est ce que journalise `fetchDirectProfiles`.
         */
        const val TRIP_WINDOW = 40

        /**
         * Combien de parcours distincts une fiche propose au plus.
         *
         * Six, comme iOS (`AuleNetworkLineRepository.maxDessertes`), et pour la
         * même raison : le réseau nantais n'en distingue pas plus de deux par
         * sens, et un service partiel en ajoute rarement plus d'un.
         */
        const val MAX_PROFILES = 6
    }
}
