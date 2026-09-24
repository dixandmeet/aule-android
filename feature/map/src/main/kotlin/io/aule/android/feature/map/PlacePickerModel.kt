package io.aule.android.feature.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.MIN_PLACE_QUERY_LENGTH
import io.aule.android.core.model.Place
import io.aule.android.core.model.PlaceSearchSession
import io.aule.android.core.model.PlaceSuggestion
import io.aule.android.core.model.StopSearch
import io.aule.android.core.model.StopSearchHit
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.model.withoutStopTwins
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La recherche d'adresse de l'éditeur de favori.
 *
 * ## Pourquoi elle ne réutilise pas celle de la carte
 *
 * `MapViewModel.setSearchQuery` fait plus que chercher : il ferme les volets,
 * abandonne l'itinéraire en cours et vide la sélection. C'est ce qu'on veut du
 * socle de la carte, et exactement ce qu'on ne veut pas d'un champ posé **dans**
 * un volet — enregistrer une adresse ne doit pas défaire ce qu'on regardait.
 *
 * Ce modèle-ci ne fait donc que ça : deux sources, le même débrayage, et rien
 * d'autre. Les arrêts viennent du catalogue déjà en mémoire — disponibles sans
 * réseau — et les adresses du géocodeur, à partir de [MIN_PLACE_QUERY_LENGTH]
 * lettres.
 *
 * Un géocodeur muet n'est pas une panne : la liste garde ses arrêts, qui sont
 * souvent la réponse. Même règle que la recherche de la carte.
 *
 * ## Une adresse proposée n'est pas encore située
 *
 * Le géocodeur nomme les adresses sans les situer — voir [PlaceSuggestion] — et
 * un favori sans point ne mène nulle part. Le point se demande donc ici, par
 * [choose], pour le seul lieu touché, dans la session des frappes qui l'ont
 * proposé. Les mêmes règles que `MapViewModel.choose`, pour les mêmes raisons :
 * une frappe ou le volet refermé — [close] — abandonnent la résolution en vol,
 * et une résolution manquée se dit sans effacer la liste.
 */
internal class PlacePickerModel(
    private val repository: PlaceSearchRepository,
    private val dispatchers: AuleDispatchers,
    private val logger: AuleLogger,
) {
    var query by mutableStateOf("")
        private set

    var stops by mutableStateOf<List<StopSearchHit>>(emptyList())
        private set

    var places by mutableStateOf<List<PlaceSuggestion>>(emptyList())
        private set

    var isGeocoding by mutableStateOf(false)
        private set

    /** L'adresse touchée qu'on est en train de situer. */
    var locating by mutableStateOf<PlaceSuggestion.Prediction?>(null)
        private set

    /** La dernière adresse touchée qu'on n'a pas pu situer — la liste, elle, reste. */
    var unlocated by mutableStateOf<PlaceSuggestion.Prediction?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private var geocodeJob: Job? = null
    private var resolveJob: Job? = null

    /**
     * La recherche en cours, pour le fournisseur. Elle naît avec la première requête qui part
     * au géocodeur et meurt au choix, ou quand on vide le champ — voir [PlaceSearchSession].
     */
    private var session: PlaceSearchSession? = null

    val isEmpty: Boolean get() = stops.isEmpty() && places.isEmpty() && !isGeocoding

    fun search(catalog: List<TransitStop>, typed: String) {
        // Taper, c'est changer d'avis : un lieu touché juste avant ne doit plus s'enregistrer.
        abandonChoice()
        unlocated = null
        query = typed
        stops = StopSearch.search(catalog, typed)
        val trimmed = typed.trim()
        // Un champ vidé, c'est une recherche qu'on recommence : la suivante aura son jeton.
        if (trimmed.isEmpty()) session = null
        val willGeocode = trimmed.length >= MIN_PLACE_QUERY_LENGTH
        // Les adresses déjà affichées restent pendant la frappe suivante : les
        // vider à chaque lettre ferait clignoter la liste sous le doigt.
        if (!willGeocode) places = emptyList()
        isGeocoding = willGeocode

        geocodeJob?.cancel()
        if (!willGeocode) return
        geocodeJob = scope.launch {
            delay(PLACE_DEBOUNCE_MS)
            val current = session ?: PlaceSearchSession.start().also { session = it }
            val found = runCatching {
                withContext(dispatchers.io) { repository.search(trimmed, current) }
            }.getOrElse { failure ->
                // Coupé par la frappe suivante : ce n'est pas le géocodeur qui s'est tu.
                ensureActive()
                if (failure is CancellationException) throw failure
                logger.warn(LogDomain.NET, "Géocodeur muet (éditeur de favori).", failure)
                emptyList()
            }
            if (query != typed) return@launch
            // Les stations que la liste montre déjà ne reviennent pas en adresse.
            places = found.withoutStopTwins(stops.flatMap { it.spellings })
            isGeocoding = false
        }
    }

    /**
     * Retient une adresse touchée, et la rend à [conclude] une fois située.
     *
     * Un lieu déjà situé passe tout de suite ; une prédiction se situe d'abord, par le seul
     * appel facturé du parcours. [conclude] n'est jamais appelé pour un choix dépassé.
     */
    fun choose(suggestion: PlaceSuggestion, conclude: (Place) -> Unit) {
        // Retoucher le rang qu'on situe déjà ne relance rien : la facture serait double.
        if (suggestion == locating && resolveJob?.isActive == true) return
        abandonChoice()
        unlocated = null
        when (suggestion) {
            is PlaceSuggestion.Located -> conclude(suggestion.place)
            is PlaceSuggestion.Prediction -> {
                locating = suggestion
                val current = session
                resolveJob = scope.launch {
                    val outcome = runCatching {
                        withContext(dispatchers.io) { repository.resolve(suggestion, current) }
                    }
                    // Abandonné entre-temps : rien ne s'enregistre, rien ne se dit.
                    ensureActive()
                    // Détachée avant de conclure : ce qui suit — [reset] — ne doit pas annuler
                    // la tâche même qui le lui demande.
                    resolveJob = null
                    locating = null
                    outcome.onSuccess { place ->
                        session = null
                        conclude(place)
                    }.onFailure { failure ->
                        logger.warn(LogDomain.NET, "Lieu impossible à situer (éditeur de favori).", failure)
                        unlocated = suggestion
                    }
                }
            }
        }
    }

    fun reset() {
        geocodeJob?.cancel()
        geocodeJob = null
        abandonChoice()
        session = null
        query = ""
        stops = emptyList()
        places = emptyList()
        isGeocoding = false
        unlocated = null
    }

    /** Le volet se referme : la recherche et la résolution en vol partent avec lui. */
    fun close() {
        scope.cancel()
    }

    private fun abandonChoice() {
        resolveJob?.cancel()
        resolveJob = null
        locating = null
    }

    private companion object {
        /**
         * Le temps que la frappe doit reposer avant d'appeler le géocodeur.
         *
         * La même valeur que la recherche de la carte
         * (`MapViewModel.PLACE_DEBOUNCE_MS`), recopiée plutôt que partagée : ce
         * sont deux champs différents, et les lier ferait régler l'un en croyant
         * régler l'autre. Ce qu'ils ont en commun est la raison — une adresse
         * coûte un aller-retour, un arrêt non.
         */
        const val PLACE_DEBOUNCE_MS = 320L
    }
}
