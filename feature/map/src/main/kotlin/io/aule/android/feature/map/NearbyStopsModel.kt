package io.aule.android.feature.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.repository.StopRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Combien d'arrêts de la liste montrent leurs passages.
 *
 * **Ce plafond est la raison d'être de cette classe.** Le BFF n'expose que
 * `stop-departures` et `stop-serving-lines`, un lieu à la fois : garnir les
 * douze entrées de [io.aule.android.core.model.NEARBY_LIMIT] coûterait
 * vingt-quatre requêtes à chaque ouverture du volet, puis autant toutes les
 * trente secondes. Trois arrêts couvrent ceux sur lesquels on agit vraiment ;
 * les suivants gardent nom, distance et temps de marche, qui se calculent
 * sans réseau, et livrent leurs passages au tap.
 */
internal const val NEARBY_DETAIL_LIMIT = 3

/** Ce qu'on a pu apprendre d'un arrêt de la liste. */
internal data class NearbyStopDetail(
    val departures: StopDepartures? = null,
    val servingLines: List<ServingLine> = emptyList(),
)

/**
 * Les passages des arrêts de tête, rafraîchis tant que le volet est ouvert.
 *
 * Même discipline que [StopDetailModel] — cadence de trente secondes, recul
 * doublé sur échec — pour la même raison : un fournisseur en panne ne doit pas
 * être interrogé plus souvent parce qu'il répond mal.
 *
 * **Un échec est muet ici.** Le volet « autour de vous » est un sommaire, pas
 * une fiche : y afficher un bandeau d'erreur par carte ferait trois bandeaux
 * pour une seule panne. La carte retombe alors sur ce qu'elle sait sans
 * réseau, et le détail d'arrêt — lui — dira la panne quand on l'ouvrira. Un
 * fournisseur *muet qui répond*, en revanche, est un résultat : il voyage
 * dans [StopDepartures.outcome] et la carte le montre.
 */
internal class NearbyStopsModel(
    private val repository: StopRepository,
    private val dispatchers: AuleDispatchers,
) {
    var details by mutableStateOf<Map<String, NearbyStopDetail>>(emptyMap())
        private set

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private var refreshJob: Job? = null
    private var watched: List<String> = emptyList()

    /**
     * Suit ces lieux, et eux seuls.
     *
     * Appelée à chaque recomposition utile : elle ne relance la boucle que si
     * la tête de liste a bougé. Sans cette garde, marcher de dix mètres —
     * donc recalculer les distances — redémarrerait les requêtes en boucle.
     */
    fun watch(stopNames: List<String>) {
        val wanted = stopNames.distinct().take(NEARBY_DETAIL_LIMIT)
        if (wanted == watched) return
        watched = wanted
        refreshJob?.cancel()
        // On garde ce qu'on savait déjà des lieux encore suivis : les vider
        // ferait clignoter les passages à chaque pas de l'utilisateur.
        details = details.filterKeys { it in wanted }
        if (wanted.isEmpty()) {
            refreshJob = null
            return
        }
        refreshJob = scope.launch {
            var backoffMs = DEPARTURES_REFRESH_MS
            while (isActive) {
                val succeeded = fetch(wanted)
                val delayMs = if (succeeded) {
                    backoffMs = DEPARTURES_REFRESH_MS
                    DEPARTURES_REFRESH_MS
                } else {
                    backoffMs = minOf(backoffMs * 2, DEPARTURES_MAX_BACKOFF_MS)
                    jittered(backoffMs)
                }
                delay(delayMs)
            }
        }
    }

    fun close() {
        refreshJob?.cancel()
        refreshJob = null
        scope.cancel()
    }

    /**
     * @return vrai si **tous** les lieux ont répondu. Un seul échec fait
     *   reculer la cadence : c'est le fournisseur qui faiblit, pas un arrêt.
     */
    private suspend fun fetch(names: List<String>): Boolean {
        return try {
            val fetched = withContext(dispatchers.io) {
                coroutineScope {
                    names.map { name ->
                        async {
                            val departures = async { repository.departures(name) }
                            val lines = async { repository.servingLines(name) }
                            name to NearbyStopDetail(departures.await(), lines.await())
                        }
                    }.awaitAll()
                }
            }
            details = fetched.toMap()
            true
        } catch (_: CancellationException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
