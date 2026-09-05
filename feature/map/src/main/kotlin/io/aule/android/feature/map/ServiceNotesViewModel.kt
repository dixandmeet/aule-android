package io.aule.android.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.ServiceNote
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.ServiceNoteRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ServiceNotesUiState(
    val notes: List<ServiceNote> = emptyList(),
    val isLoading: Boolean = false,
    /**
     * La lecture a échoué.
     *
     * ⚠️ **Distinct d'une liste vide.** « Rien n'est affiché aujourd'hui » est le cas
     * courant et se tait ; « on n'a pas su lire » doit se voir, parce qu'un
     * conducteur qui ne voit aucune consigne en conclut qu'il n'y en a pas.
     */
    val isUnavailable: Boolean = false,
) {
    /**
     * Les notes qui valent pour cette ligne, dans l'ordre du serveur.
     *
     * @param line `null` rend tout ce qui est affiché — c'est le cas du volet avant
     * que l'agent n'ait choisi sa ligne, où cacher les notes reviendrait à lui
     * demander de deviner qu'il y en a.
     */
    fun notesFor(line: String?): List<ServiceNote> =
        if (line.isNullOrBlank()) notes else notes.filter { it.concerns(line) }
}

/**
 * Les notes de service du réseau.
 *
 * ## Pourquoi elles ne se relisent pas à chaque ouverture
 *
 * Une note dure un mois. Relire à chaque ouverture du volet coûterait un
 * aller-retour à chaque fois qu'un conducteur vérifie son heure de début, pour une
 * réponse identique. Un quart d'heure de fraîcheur : assez court pour qu'une note
 * publiée pendant la prise de service arrive avant le départ.
 *
 * ## Ce qu'il ne décide pas
 *
 * Il ne filtre pas par ligne. C'est [ServiceNote.concerns] qui le fait — pur, testé,
 * et appliqué par l'écran qui sait quelle ligne est affichée. Un modèle qui
 * retiendrait « les notes de la ligne 1 » devrait tout relire au moindre changement
 * de sélection dans le formulaire.
 */
class ServiceNotesViewModel(
    private val auth: AuthRepository,
    private val notes: ServiceNoteRepository,
    private val logger: AuleLogger,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val _state = MutableStateFlow(ServiceNotesUiState())
    val state: StateFlow<ServiceNotesUiState> = _state.asStateFlow()

    private var loadedAt: Instant? = null

    /**
     * Relit les notes si celles qu'on a sont trop vieilles.
     *
     * @param force relit quoi qu'il arrive — le geste explicite d'un agent qui tire
     * pour rafraîchir, et à qui « rien n'a changé » ne suffit pas comme réponse.
     */
    fun load(force: Boolean = false) {
        val since = loadedAt
        if (!force && since != null && !_state.value.isUnavailable &&
            Duration.between(since, now()) < FRESHNESS
        ) {
            return
        }
        if (_state.value.isLoading) return

        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val session = auth.currentSession()
                if (session == null) {
                    // Pas de session : ce n'est ni une panne ni une absence de note,
                    // c'est un écran qui n'a pas à en montrer. On se tait.
                    _state.value = ServiceNotesUiState()
                    return@launch
                }
                // Toutes lignes : le volet en montre une, mais l'écran de relève ne
                // connaît la sienne qu'après le choix du collègue. Une note pèse
                // quelques kilo-octets, et le réseau en porte une poignée.
                val fetched = notes.fetchNotes(session, line = null)
                loadedAt = now()
                _state.value = ServiceNotesUiState(notes = fetched, isLoading = false)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                logger.warn(LogDomain.NET, "Notes de service illisibles", error)
                // On garde ce qu'on avait lu : une consigne d'hier vaut mieux que
                // rien, et elle est probablement encore vraie.
                _state.value = _state.value.copy(isLoading = false, isUnavailable = true)
            }
        }
    }

    private companion object {
        val FRESHNESS: Duration = Duration.ofMinutes(15)
    }
}
