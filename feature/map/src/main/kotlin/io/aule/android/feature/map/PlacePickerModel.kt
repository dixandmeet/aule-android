package io.aule.android.feature.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.MIN_PLACE_QUERY_LENGTH
import io.aule.android.core.model.Place
import io.aule.android.core.model.StopSearch
import io.aule.android.core.model.StopSearchHit
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.repository.PlaceSearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    var places by mutableStateOf<List<Place>>(emptyList())
        private set

    var isGeocoding by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private var geocodeJob: Job? = null

    val isEmpty: Boolean get() = stops.isEmpty() && places.isEmpty() && !isGeocoding

    fun search(catalog: List<TransitStop>, typed: String) {
        query = typed
        stops = StopSearch.search(catalog, typed)
        val trimmed = typed.trim()
        val willGeocode = trimmed.length >= MIN_PLACE_QUERY_LENGTH
        // Les adresses déjà affichées restent pendant la frappe suivante : les
        // vider à chaque lettre ferait clignoter la liste sous le doigt.
        if (!willGeocode) places = emptyList()
        isGeocoding = willGeocode

        geocodeJob?.cancel()
        if (!willGeocode) return
        geocodeJob = scope.launch {
            delay(PLACE_DEBOUNCE_MS)
            val found = runCatching {
                withContext(dispatchers.io) { repository.search(trimmed) }
            }.getOrElse { failure ->
                if (failure is CancellationException) throw failure
                logger.warn(LogDomain.NET, "Géocodeur muet (éditeur de favori).", failure)
                emptyList()
            }
            if (query != typed) return@launch
            places = found
            isGeocoding = false
        }
    }

    fun reset() {
        geocodeJob?.cancel()
        geocodeJob = null
        query = ""
        stops = emptyList()
        places = emptyList()
        isGeocoding = false
    }

    fun close() {
        scope.cancel()
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
