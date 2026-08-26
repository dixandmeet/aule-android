package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.DepartureWatch
import io.aule.android.core.model.Timetable
import io.aule.android.core.model.TimetableException
import io.aule.android.core.model.TimetableFailureKind
import io.aule.android.core.model.repository.TimetableRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La grille horaire affichée, et la date qu'on regarde.
 *
 * @param date la journée demandée. Elle change par le sélecteur, jamais toute
 *   seule : une grille qui basculerait au passage de minuit ferait disparaître
 *   sous les yeux la colonne qu'on était en train de lire.
 */
internal data class TimetableUiState(
    val line: DepartureWatch? = null,
    val date: LocalDate? = null,
    val timetable: Timetable? = null,
    val isLoading: Boolean = false,
    val failure: TimetableFailureKind? = null,
) {
    /** Vrai quand la grille affichée correspond bien à la date demandée. */
    val isReady: Boolean get() = timetable != null && timetable.date == date
}

/**
 * La grille théorique d'une ligne, pour une journée.
 *
 * ## Elle ne se rafraîchit pas, et c'est le point
 *
 * Tout ce que ce dépôt sonde en boucle — la flotte, les passages, la relève —
 * se périme en secondes. Une grille horaire, non : elle change à la fréquence
 * d'un dépôt GTFS, c'est-à-dire quelques fois par an. Un sondage périodique ne
 * ferait qu'ajouter de la charge à des tables volumineuses pour réécrire les
 * mêmes heures. On charge une fois par date demandée, et on garde.
 *
 * ## Un échec se dit, il ne se retente pas tout seul
 *
 * Le contrat distingue quatre causes, et deux d'entre elles ne se résolvent
 * pas en attendant : une ligne absente du catalogue le restera, et une session
 * refusée demande de se reconnecter. Retenter en boucle donnerait l'illusion
 * d'un chargement en cours là où il n'y a plus rien à attendre — l'écran dit
 * ce qui s'est passé, et l'usager décide.
 */
internal class TimetableModel(
    private val repository: TimetableRepository,
    private val session: () -> AuthSession?,
    private val dispatchers: AuleDispatchers,
    private val scope: CoroutineScope,
    private val logger: AuleLogger,
    private val today: () -> LocalDate,
) {
    private val _state = MutableStateFlow(TimetableUiState())
    val state: StateFlow<TimetableUiState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Le volet s'ouvre sur une ligne : on charge la journée en cours.
     *
     * Rouvrir la même ligne ne recharge rien — la grille du jour est déjà là,
     * et la redemander ne changerait pas une minute.
     */
    fun open(line: DepartureWatch) {
        val current = _state.value
        if (current.line?.id == line.id && current.timetable != null) return
        load(line = line, date = current.date?.takeIf { current.line?.id == line.id } ?: today())
    }

    /** Une autre journée. La ligne, elle, ne change pas. */
    fun setDate(date: LocalDate) {
        val line = _state.value.line ?: return
        if (_state.value.date == date && _state.value.isReady) return
        load(line = line, date = date)
    }

    /** Après un échec réseau : la seule reprise est celle qu'on demande. */
    fun retry() {
        val current = _state.value
        val line = current.line ?: return
        load(line = line, date = current.date ?: today())
    }

    fun close() {
        job?.cancel()
        job = null
        _state.value = TimetableUiState()
    }

    private fun load(line: DepartureWatch, date: LocalDate) {
        job?.cancel()
        _state.value = TimetableUiState(
            line = line,
            date = date,
            // La grille précédente disparaît : elle décrivait une autre journée,
            // et la garder à l'écran pendant le chargement de la suivante ferait
            // lire des heures fausses sous une date exacte.
            timetable = null,
            isLoading = true,
            failure = null,
        )
        val account = session()
        if (account == null) {
            _state.value = _state.value.copy(
                isLoading = false,
                failure = TimetableFailureKind.NOT_SIGNED_IN,
            )
            return
        }
        job = scope.launch {
            try {
                val loaded = withContext(dispatchers.io) {
                    repository.timetable(
                        session = account,
                        stopName = line.stopName,
                        line = line.line,
                        destination = line.destination,
                        date = date,
                    )
                }
                _state.value = _state.value.copy(
                    timetable = loaded,
                    isLoading = false,
                    failure = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: TimetableException) {
                logger.warn(LogDomain.NET, "Grille horaire indisponible (${failure.kind}).")
                _state.value = _state.value.copy(isLoading = false, failure = failure.kind)
            } catch (failure: Exception) {
                logger.warn(LogDomain.NET, "Grille horaire indisponible.", failure)
                _state.value = _state.value.copy(
                    isLoading = false,
                    failure = TimetableFailureKind.UNAVAILABLE,
                )
            }
        }
    }
}
