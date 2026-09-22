package io.aule.android.data.aule

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.MIN_PLACE_QUERY_LENGTH
import io.aule.android.core.model.Place
import io.aule.android.core.model.SEARCH_LIMIT_PER_KIND
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.dto.GeocodeLookupDto
import io.aule.android.data.dto.GeocodePayloadDto
import io.aule.android.data.dto.GeocodeResultDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Les lieux qui ne sont pas des arrêts : adresses, commerces, monuments.
 *
 * ## ⚠️ Le géocodeur d'Aule répond en **deux temps**, et c'est structurel
 *
 * `/api/geocode?q=` rend une autocomplétion — `{placeId, label, details}` — et **jamais**
 * de coordonnées ; `/api/geocode?placeId=` rend le point. Le BFF n'a pas le choix : le
 * fournisseur facture l'autocomplétion et la résolution séparément, et résoudre les dix
 * suggestions de chaque frappe coûterait dix fois le prix d'une recherche pour neuf
 * résultats que personne ne touchera.
 *
 * Ce dépôt a longtemps ignoré ce contrat : il lisait `lat`/`lng` dans la première réponse,
 * ne les trouvait jamais, et rendait donc **une liste vide sur chaque frappe**. Relevé en
 * recette le 22/09/2026 : « rue de Strasbourg » et « Château des ducs » répondaient « Aucun
 * lieu à ce nom » pendant que le serveur, lui, les connaissait tous les deux.
 *
 * ⚠️ **L'épreuve ne l'avait pas vu parce que sa fixture inventait la charge utile** : elle
 * portait `lat`/`lng`, que la production ne renvoie pas. Une fixture qui ne vient pas du
 * serveur ne garde rien — `geocode.json` a été remise à ce que `www.aule.fr` rend vraiment.
 */
class AulePlaceSearchRepository(
    private val endpoints: AuleEndpoints,
    private val client: AuleHttpClient,
) : PlaceSearchRepository {

    override suspend fun search(query: String): List<Place> = coroutineScope {
        val cleaned = query.trim()
        // Deux lettres rendent la moitié de la Loire-Atlantique, et l'appel
        // est payé pour rien. Le filtre est ici plutôt que dans l'écran :
        // c'est une règle de la source, pas une décision d'interface.
        if (cleaned.length < MIN_PLACE_QUERY_LENGTH) return@coroutineScope emptyList()

        val suggestions = client.get(
            url = endpoints.geocode,
            query = mapOf("q" to cleaned),
            deserializer = GeocodePayloadDto.serializer(),
        ).results
            .filter { !it.label.isNullOrBlank() }
            // ⚠️ **La coupe passe avant la résolution, et non l'inverse.** Chaque place
            // gardée coûte un aller-retour ; en résoudre dix pour n'en montrer cinq
            // doublerait la facture et le temps d'attente sans rien ajouter à l'écran.
            .take(SEARCH_LIMIT_PER_KIND)

        // Les résolutions partent ensemble : cinq allers-retours en série tiendraient
        // l'écran une demi-seconde de plus pour rien, et la frappe suivante les annule
        // toutes d'un coup par le scope.
        suggestions
            .map { suggestion -> async { resolve(suggestion) } }
            .mapNotNull { it.await() }
    }

    /**
     * Le point d'une suggestion — celui qu'elle porte déjà, ou celui qu'on va chercher.
     *
     * ⚠️ **Un échec de résolution écarte ce lieu-là, pas la recherche.** Le fournisseur
     * peut refuser une clé périmée ou plafonner le quota ; rendre alors la liste entière
     * vide effacerait quatre résultats bons à cause d'un cinquième. `CancellationException`
     * fait exception : c'est la frappe suivante qui a pris la main, et elle doit remonter.
     */
    private suspend fun resolve(suggestion: GeocodeResultDto): Place? {
        val label = suggestion.label?.takeIf { it.isNotBlank() } ?: return null

        suggestion.coordinate()?.let { return Place(label = label, coordinate = it) }

        val placeId = suggestion.placeId?.takeIf { it.isNotBlank() } ?: return null
        val resolved = try {
            client.get(
                url = endpoints.geocode,
                query = mapOf("placeId" to placeId),
                deserializer = GeocodeLookupDto.serializer(),
            ).result
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return null
        }

        val coordinate = resolved?.coordinate() ?: return null
        // Le libellé résolu est le plus complet des deux — « 5 Rue de Strasbourg, 44000
        // Nantes » plutôt que « 5 Rue de Strasbourg » —, mais il peut manquer.
        return Place(
            label = resolved.label?.takeIf { it.isNotBlank() } ?: label,
            coordinate = coordinate,
        )
    }

    private fun GeocodeResultDto.coordinate(): Coordinate? {
        val latitude = lat ?: return null
        val longitude = lng ?: return null
        return Coordinate(latitude, longitude).takeIf { it.isValid }
    }
}
