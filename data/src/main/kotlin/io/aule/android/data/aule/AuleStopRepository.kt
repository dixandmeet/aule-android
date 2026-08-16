package io.aule.android.data.aule

import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.dto.DeparturesPayloadDto
import io.aule.android.data.dto.ServingLinesPayloadDto
import io.aule.android.data.dto.StopsPayloadDto
import java.time.Clock
import java.time.Instant

class AuleStopRepository(
    private val endpoints: AuleEndpoints,
    private val client: AuleHttpClient,
    private val clock: Clock = Clock.systemUTC(),
) : StopRepository {

    /**
     * Le catalogue complet, chargé une fois.
     *
     * ~2 600 arrêts, ~600 Ko : assez peu pour un seul appel, et c'est ce qui
     * permet à la carte de les afficher sans redemander à chaque déplacement.
     */
    override suspend fun allStops(): List<TransitStop> =
        client.get(
            url = endpoints.stops,
            deserializer = StopsPayloadDto.serializer(),
        ).stops.mapNotNull { it.toDomain() }

    /**
     * Les passages annoncés.
     *
     * **404 et 502 sont des résultats, pas des pannes** — et surtout, pas le même
     * résultat. Un 404 dit « rien ne circule ici dans les trois prochaines
     * heures », ce qui est la bonne réponse à deux heures du matin. Un 502 dit
     * que le fournisseur temps réel est muet, et là il faut réessayer. Les deux
     * mènent à un écran vide ; les confondre fait annoncer une absence de bus
     * qu'on n'a pas constatée.
     *
     * Toutes les autres pannes lèvent.
     */
    override suspend fun departures(atStopNamed: String): StopDepartures {
        val now = Instant.now(clock)
        return try {
            val payload = client.get(
                url = endpoints.stopDepartures,
                query = mapOf("name" to atStopNamed),
                deserializer = DeparturesPayloadDto.serializer(),
            )
            val departures = payload.passages.mapNotNull { it.toDomain() }
            StopDepartures(
                stopName = atStopNamed,
                departures = departures,
                outcome = if (departures.isEmpty()) {
                    DeparturesOutcome.NOTHING_ANNOUNCED
                } else {
                    DeparturesOutcome.ANNOUNCED
                },
                fetchedAt = payload.updatedAt ?: now,
            )
        } catch (_: ApiException.NotFound) {
            StopDepartures(atStopNamed, emptyList(), DeparturesOutcome.NOTHING_ANNOUNCED, now)
        } catch (_: ApiException.UpstreamUnavailable) {
            StopDepartures(atStopNamed, emptyList(), DeparturesOutcome.PROVIDER_SILENT, now)
        }
    }

    /**
     * Les lignes qui desservent un arrêt, indépendamment de l'heure.
     *
     * C'est ce qui reste utile la nuit, quand plus rien n'est annoncé mais que
     * l'arrêt dessert toujours les mêmes lignes.
     */
    override suspend fun servingLines(atStopNamed: String): List<ServingLine> =
        client.get(
            url = endpoints.stopServingLines,
            query = mapOf("name" to atStopNamed),
            deserializer = ServingLinesPayloadDto.serializer(),
        ).lines.mapNotNull { it.toDomain() }
}
