package io.aule.android.data.aule

import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.Timetable
import io.aule.android.core.model.TimetableException
import io.aule.android.core.model.TimetableFailureKind
import io.aule.android.core.model.normalizeStopName
import io.aule.android.core.model.repository.TimetableRepository
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.core.network.RawHttpResponse
import io.aule.android.data.dto.GtfsCalendarDateDto
import io.aule.android.data.dto.GtfsCalendarDto
import io.aule.android.data.dto.GtfsRouteDto
import io.aule.android.data.dto.GtfsStopMetaDto
import io.aule.android.data.dto.GtfsTripDepartureDto
import io.aule.android.data.dto.GtfsTripProfileDto
import io.aule.android.data.dto.GtfsTripProfileStopDto
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * La grille horaire d'une ligne à un arrêt, reconstruite depuis GTFS.
 *
 * ## Pourquoi elle se reconstruit au lieu de se lire
 *
 * Le catalogue publié ne contient pas de table « heures de passage » : il
 * contient des **profils** de course (la suite des arrêts et le temps écoulé
 * depuis le départ) et des **départs** (l'heure à laquelle chaque profil part,
 * les jours où il roule). L'heure de passage à un arrêt est la somme des deux.
 * C'est le stockage qui rend un réseau entier tenable — un profil sert des
 * centaines de départs — et c'est à l'application de faire l'addition.
 *
 * Cinq requêtes, dans cet ordre, chacune bornée par la précédente :
 *
 * 1. la ligne (`gtfs_routes`), parce que « 80 » à l'écran peut être un
 *    `route_id` comme un `route_short_name` ;
 * 2. ses profils (`gtfs_trip_profiles`), filtrés sur la destination affichée ;
 * 3. la desserte de ces profils (`gtfs_trip_profile_stops`) — c'est là qu'on
 *    trouve notre arrêt, **par son nom**, et son décalage depuis le départ ;
 * 4. les services actifs à la date demandée (`gtfs_calendar` et ses exceptions) ;
 * 5. les départs de ces profils pour ces services (`gtfs_trip_departures`).
 *
 * ## L'arrêt se reconnaît par son nom, pas par son identifiant
 *
 * L'identifiant d'arrêt vu par la carte vient du BFF temps réel, celui du
 * catalogue vient du GTFS, et rien ne garantit que ce soit le même. Le nom,
 * lui, est ce que l'usager lit sur le poteau et ce que les deux mondes
 * publient. On le compare **normalisé** ([normalizeStopName]), donc sans
 * accents ni ponctuation, et sur tous les quais d'un même lieu : « Ranzay »
 * désigne les deux sens, et on ne garde que ceux du profil qui va dans la
 * bonne direction.
 */
class SupabaseTimetableRepository(
    private val client: AuleHttpClient,
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val json: Json = AuleHttpClient.defaultJson,
) : TimetableRepository {

    private val restBase: String
        get() = supabaseUrl.trimEnd('/') + "/rest/v1"

    override suspend fun timetable(
        session: AuthSession,
        stopName: String,
        line: String,
        destination: String,
        date: LocalDate,
    ): Timetable {
        if (supabaseUrl.isBlank() || publishableKey.isBlank()) {
            throw TimetableException(TimetableFailureKind.NOT_CONFIGURED)
        }
        val empty = Timetable(
            date = date,
            line = line,
            destination = destination,
            stopName = stopName,
        )
        val headers = mapOf(
            "apikey" to publishableKey,
            "Authorization" to "Bearer ${session.accessToken}",
        )

        val wanted = line.trim()
        if (wanted.isEmpty()) return empty

        // 1. La ligne. Le libellé de l'écran peut être l'un ou l'autre des deux
        // champs, et le catalogue de Nantes se sert des deux.
        val routes = decode(
            client.getRaw(
                url = "$restBase/gtfs_routes",
                headers = headers,
                query = mapOf(
                    "select" to "route_id,route_short_name,route_long_name,route_type",
                    "or" to "(route_id.eq.$wanted,route_short_name.eq.$wanted)",
                    "limit" to "8",
                ),
            ),
            GtfsRouteDto.serializer(),
        )
        val routeIds = routes.map { it.routeId }.distinct()
        if (routeIds.isEmpty()) throw TimetableException(TimetableFailureKind.NOT_IN_CATALOG)

        // 2. Les profils qui vont dans la direction affichée. Sans aucune
        // correspondance, on garde **tous** les profils de la ligne plutôt que
        // de rendre une grille vide : un panneau de destination qui ne
        // ressemble à rien de connu est un défaut de données, et la grille des
        // deux sens reste plus utile qu'une page blanche.
        val profiles = decode(
            client.getRaw(
                url = "$restBase/gtfs_trip_profiles",
                headers = headers,
                query = mapOf(
                    "select" to "profile_id,direction_id,headsign,route_id",
                    "route_id" to "in.(${routeIds.joinToString(",")})",
                ),
            ),
            GtfsTripProfileDto.serializer(),
        )
        if (profiles.isEmpty()) throw TimetableException(TimetableFailureKind.NOT_IN_CATALOG)
        val heading = profiles.matchingDirection(destination)

        // 3. Le décalage de notre arrêt dans chacun de ces profils.
        val profileIds = heading.map { it.profileId }
        val profileStops = fetchAllPages { offset, limit ->
            decode(
                client.getRaw(
                    url = "$restBase/gtfs_trip_profile_stops",
                    headers = headers,
                    query = mapOf(
                        "select" to "profile_id,stop_sequence,stop_id,offset_seconds",
                        "profile_id" to "in.(${profileIds.joinToString(",")})",
                        "order" to "profile_id,stop_sequence",
                        "offset" to "$offset",
                        "limit" to "$limit",
                    ),
                ),
                GtfsTripProfileStopDto.serializer(),
            )
        }
        if (profileStops.isEmpty()) return empty

        val stopIds = profileStops.map { it.stopId }.distinct()
        val names = decode(
            client.getRaw(
                url = "$restBase/gtfs_stops",
                headers = headers,
                query = mapOf(
                    "select" to "stop_id,stop_name",
                    "stop_id" to "in.(${stopIds.joinToString(",")})",
                ),
            ),
            GtfsStopMetaDto.serializer(),
        ).associate { it.stopId to normalizeStopName(it.stopName.orEmpty()) }

        val wantedStop = normalizeStopName(stopName)
        val offsetByProfile = mutableMapOf<String, Int>()
        for (row in profileStops) {
            if (names[row.stopId] != wantedStop) continue
            // Le premier passage du profil par ce nom : une ligne en boucle
            // dessert deux fois le même quai, et c'est l'aller qu'on annonce.
            offsetByProfile.putIfAbsent(row.profileId, row.offsetSeconds)
        }
        if (offsetByProfile.isEmpty()) return empty

        // 4. Les services actifs ce jour-là.
        val iso = date.toString()
        val services = activeServiceIds(
            regular = decode(
                client.getRaw(
                    url = "$restBase/gtfs_calendar",
                    headers = headers,
                    query = mapOf(
                        "select" to "service_id,runs_on",
                        "start_date" to "lte.$iso",
                        "end_date" to "gte.$iso",
                    ),
                ),
                GtfsCalendarDto.serializer(),
            ),
            exceptions = decode(
                client.getRaw(
                    url = "$restBase/gtfs_calendar_dates",
                    headers = headers,
                    query = mapOf(
                        "select" to "service_id,exception_type",
                        "service_date" to "eq.$iso",
                    ),
                ),
                GtfsCalendarDateDto.serializer(),
            ),
            date = date,
        )
        // Aucun service actif n'est une **réponse** : c'est un dimanche sans
        // desserte, un jour férié, une date hors calendrier publié.
        if (services.isEmpty()) return empty

        // 5. Les départs, et l'addition.
        val departures = fetchAllPages { offset, limit ->
            decode(
                client.getRaw(
                    url = "$restBase/gtfs_trip_departures",
                    headers = headers,
                    query = mapOf(
                        "select" to "departure_id,profile_id,start_seconds",
                        "profile_id" to "in.(${offsetByProfile.keys.joinToString(",")})",
                        "service_id" to "in.(${services.joinToString(",")})",
                        "order" to "start_seconds",
                        "offset" to "$offset",
                        "limit" to "$limit",
                    ),
                ),
                GtfsTripDepartureDto.serializer(),
            )
        }

        val midnight = date.atStartOfDay(NETWORK_ZONE).toInstant()
        val passages = departures
            .mapNotNull { row ->
                val offsetSeconds = offsetByProfile[row.profileId] ?: return@mapNotNull null
                midnight.plusSeconds((row.startSeconds + offsetSeconds).toLong())
            }
            // Deux profils peuvent produire la même minute — un renfort
            // scolaire qui double un service ordinaire. L'usager n'attend qu'un
            // bus.
            .distinct()
            .sorted()

        return empty.copy(passages = passages)
    }

    /**
     * Les profils qui vont dans la direction demandée, au meilleur score.
     *
     * Le meilleur, et pas « tous ceux qui ressemblent un peu » : sur une ligne
     * dont les deux terminus partagent un mot, retenir les deux mélangerait les
     * sens dans une seule colonne d'heures.
     */
    private fun List<GtfsTripProfileDto>.matchingDirection(
        destination: String,
    ): List<GtfsTripProfileDto> {
        val scored = map { it to directionScore(it.headsign, destination) }
        val best = scored.maxOf { it.second }
        if (best <= 0) return this
        return scored.filter { it.second == best }.map { it.first }
    }

    private fun <T> decode(response: RawHttpResponse, serializer: KSerializer<T>): List<T> {
        when (response.code) {
            in 200..299 -> Unit
            401, 403 -> throw TimetableException(TimetableFailureKind.NOT_SIGNED_IN)
            else -> throw TimetableException(TimetableFailureKind.UNAVAILABLE)
        }
        return try {
            json.decodeFromString(ListSerializer(serializer), response.body)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            throw TimetableException(TimetableFailureKind.UNAVAILABLE, failure)
        }
    }
}
