package io.aule.android.data.aule

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.MIN_PLACE_QUERY_LENGTH
import io.aule.android.core.model.Place
import io.aule.android.core.model.PlaceSearchSession
import io.aule.android.core.model.PlaceSuggestion
import io.aule.android.core.model.SEARCH_LIMIT_PER_KIND
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.dto.GeocodeLookupDto
import io.aule.android.data.dto.GeocodePayloadDto
import io.aule.android.data.dto.GeocodeResultDto

/**
 * Les lieux qui ne sont pas des arrêts : adresses, commerces, monuments.
 *
 * ## ⚠️ Le géocodeur d'Aule répond en **deux temps**, et c'est structurel
 *
 * `/api/geocode?q=` rend une autocomplétion — `{placeId, label, details, kind}` — et **jamais**
 * de coordonnées ; `/api/geocode?placeId=` rend le point. Le BFF n'a pas le choix : le
 * fournisseur facture l'autocomplétion et la résolution séparément.
 *
 * Ce dépôt a d'abord ignoré ce contrat : il lisait `lat`/`lng` dans la première réponse, ne les
 * trouvait jamais, et rendait donc **une liste vide sur chaque frappe** (recette du 22/09/2026 :
 * « rue de Strasbourg » et « Château des ducs » répondaient « Aucun lieu à ce nom »). Le premier
 * correctif, `686d1d3`, est tombé dans l'excès inverse : il situait les cinq premières
 * suggestions de **chaque** recherche. Taper « rue de Strasbourg » posait ainsi cinq résolutions
 * facturées par frappe reposée, pour une seule rue retenue au bout — quand le BFF écrit lui-même
 * « un seul appel, au moment du clic, et jamais pour les suggestions non retenues »
 * (`dashboard/app/api/geocode/route.ts`).
 *
 * [search] ne situe donc plus rien : il rend des [PlaceSuggestion.Prediction], et [resolve]
 * situe **celle qu'on touche**. C'est la règle du web (`de5f90c`) et celle d'iOS.
 *
 * ## Le jeton de session
 *
 * `session` part avec chaque frappe et avec la résolution qui les conclut : Google facture alors
 * la recherche comme une seule. C'est le modèle de recherche qui le tient — il est seul à savoir
 * quand une recherche commence et finit —, ce dépôt ne fait que le transmettre. Voir
 * [PlaceSearchSession].
 *
 * ⚠️ **Les fixtures de ce contrat sont des relevés de `www.aule.fr`**, jamais des charges
 * écrites à la main. Celle d'origine portait un `lat`/`lng` que la production ne renvoie pas, et
 * l'épreuve passait au vert sur un dépôt qui ne trouvait rien.
 */
class AulePlaceSearchRepository(
    private val endpoints: AuleEndpoints,
    private val client: AuleHttpClient,
) : PlaceSearchRepository {

    override suspend fun search(query: String, session: PlaceSearchSession?): List<PlaceSuggestion> {
        val cleaned = query.trim()
        // Ce n'est pas une préférence d'écran : `/api/geocode` répond **400** « Saisissez au
        // moins 3 caractères » en deçà (mesuré le 24/09/2026). Le filtre est ici plutôt que dans
        // l'écran parce que c'est une règle de la source.
        if (cleaned.length < MIN_PLACE_QUERY_LENGTH) return emptyList()

        return client.get(
            url = endpoints.geocode,
            query = mapOf("q" to cleaned, "session" to session?.token),
            deserializer = GeocodePayloadDto.serializer(),
        ).results
            .mapNotNull { it.toSuggestion() }
            // Cinq, comme les arrêts : au-delà, la liste noie ce qu'on cherchait. La coupe ne
            // protège plus la facture — une prédiction ne coûte rien tant qu'on ne la touche
            // pas —, elle protège l'écran. Google n'en rend d'ailleurs pas plus ; le repli IGN,
            // jusqu'à huit.
            .take(SEARCH_LIMIT_PER_KIND)
    }

    override suspend fun resolve(prediction: PlaceSuggestion.Prediction, session: PlaceSearchSession?): Place {
        val location = client.get(
            url = endpoints.geocode,
            query = mapOf("placeId" to prediction.placeId, "session" to session?.token),
            deserializer = GeocodeLookupDto.serializer(),
        ).result
        // Un 200 sans point exploitable n'est pas un lieu : le rendre approché mènerait
        // l'itinéraire ailleurs sans rien dire. L'écran le traite comme une résolution manquée —
        // le lieu se retouche, la liste reste.
        val point = coordinateOf(location?.lat, location?.lng)
            ?: throw ApiException.Decoding(
                IllegalStateException("Aucun point exploitable pour ${prediction.placeId}"),
            )
        // Le libellé de la fiche (`location.label`) n'est pas lu : c'est le nom **touché** qui
        // titre le lieu — voir `PlaceSuggestion.Prediction.locatedLabel`.
        return prediction.locatedAt(point, location?.address)
    }

    /**
     * Ce que la suggestion est : un lieu situé, une prédiction à situer, ou rien.
     *
     * Le point d'abord : une suggestion qui le porte — le repli IGN — n'a rien à demander à
     * personne. Ni point ni clé : rien ne pourra jamais être fait de ce résultat, et c'est le
     * seul cas où l'écarter est honnête.
     */
    private fun GeocodeResultDto.toSuggestion(): PlaceSuggestion? {
        val name = label?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        coordinateOf(lat, lng)?.let { point ->
            return PlaceSuggestion.Located(Place(label = name, coordinate = point))
        }
        val key = placeId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return PlaceSuggestion.Prediction(
            placeId = key,
            label = name,
            details = details?.trim()?.takeIf { it.isNotEmpty() },
            isTransitStop = kind?.category == STOP_CATEGORY,
        )
    }

    private fun coordinateOf(latitude: Double?, longitude: Double?): Coordinate? {
        if (latitude == null || longitude == null) return null
        return Coordinate(latitude, longitude).takeIf { it.isValid }
    }

    private companion object {
        /** La catégorie que le BFF donne aux arrêts de transport — `lib/place-kinds.ts`. */
        const val STOP_CATEGORY = "stop"
    }
}
