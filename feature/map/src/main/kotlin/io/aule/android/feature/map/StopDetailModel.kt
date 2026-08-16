package io.aule.android.feature.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.repository.StopRepository
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cadence de rafraîchissement des passages, et plafond du recul en cas d'échec. */
internal const val DEPARTURES_REFRESH_MS = 30_000L
internal const val DEPARTURES_MAX_BACKOFF_MS = 120_000L

/**
 * Ce qu'on sait d'un arrêt, chargé à la demande.
 *
 * Un tableau de passages qui ne se rafraîchit pas devient faux en une minute,
 * et il ne dit pas qu'il l'est devenu — c'est le pire des deux mondes.
 *
 * **Mais on recule en cas d'échec.** Sans cela, un fournisseur en panne était
 * interrogé toutes les trente secondes aussi longtemps que le volet restait
 * ouvert — c'est-à-dire que l'app aggravait la panne qu'elle subissait.
 */
internal class StopDetailModel(
    private val repository: StopRepository,
    private val dispatchers: AuleDispatchers,
) {
    var departures by mutableStateOf<StopDepartures?>(null)
        private set
    var servingLines by mutableStateOf<List<ServingLine>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private var refreshJob: Job? = null

    fun load(stopNamed: String) {
        refreshJob?.cancel()
        isLoading = true
        refreshJob = scope.launch {
            var backoffMs = DEPARTURES_REFRESH_MS
            while (isActive) {
                val succeeded = fetch(stopNamed)
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
     * @return vrai si le chargement a abouti. Une annulation n'est ni l'un ni
     *   l'autre : elle interrompt la boucle, qui n'aura pas à décider d'un recul.
     */
    private suspend fun fetch(name: String): Boolean {
        return try {
            val result = withContext(dispatchers.io) {
                coroutineScope {
                    val departuresTask = async { repository.departures(name) }
                    val linesTask = async { repository.servingLines(name) }
                    departuresTask.await() to linesTask.await()
                }
            }
            departures = result.first
            servingLines = result.second
            error = null
            isLoading = false
            true
        } catch (_: CancellationException) {
            false
        } catch (failure: Exception) {
            error = failure.message
            isLoading = false
            false
        }
    }
}

/**
 * La gigue s'applique au **délai**, jamais à la base : la réinjecter ferait
 * dériver le doublement vers le bas et le recul ne croîtrait plus comme
 * annoncé. Et elle est de moitié plutôt que complète — tirer entre zéro et
 * le plafond ramènerait parfois à un intervalle quasi nul, martelant
 * précisément l'API en difficulté.
 */
internal fun jittered(backoffMs: Long, random: Random = Random.Default): Long {
    if (backoffMs <= 0L) return backoffMs
    val half = backoffMs / 2
    return half + random.nextLong(half + 1)
}
