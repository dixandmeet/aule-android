package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.DepartureWatch
import io.aule.android.core.model.DepartureWatchAlert
import io.aule.android.core.model.DepartureWatchAlertKind
import io.aule.android.core.model.DepartureWatchEngine
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.StopDeparture
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.Wait
import io.aule.android.core.model.forLine
import io.aule.android.core.model.isFresh
import io.aule.android.core.model.matchWatchedVehicle
import io.aule.android.core.model.repository.StopRepository
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ce qu'on regarde, et ce qu'on surveille.
 *
 * Les deux sont **distincts**, et c'est la seule subtilité de cet état. On arme
 * une veille sur la C6, on referme le volet, puis on rouvre l'arrêt pour jeter
 * un œil à la 23 : la veille doit rester sur la C6. Un état à une seule cible
 * l'aurait déplacée en silence, et le bus attendu serait passé sans rien dire.
 *
 * @param viewed la ligne affichée par le volet. `null` quand il est fermé.
 * @param armed la ligne veillée. `null` quand aucune alerte n'est armée. Elle
 *   survit à [viewed], puisque veiller sert précisément à ne plus regarder.
 * @param table le dernier tableau reçu pour l'arrêt affiché. Il ne se vide pas
 *   sur échec : des horaires d'il y a une minute valent mieux qu'un écran
 *   blanc, pourvu qu'on n'en tire pas d'alerte ([StopDepartures.isFresh]).
 * @param vehicleId le véhicule reconnu comme portant le prochain passage de la
 *   ligne **affichée**, quand la flotte permet de le désigner sans se tromper.
 *   `null` la plupart du temps sur un passage théorique, et ce n'est pas une
 *   panne : c'est ce qui décide si l'on peut proposer de le suivre à l'écran.
 * @param isFocused vrai quand la carte accompagne ce véhicule. Alerter et
 *   suivre sont deux gestes distincts — on peut vouloir l'un sans l'autre, et
 *   c'est le cas le plus courant : on demande une alerte **pour ranger son
 *   téléphone**, on demande un suivi **pour le regarder**.
 */
internal data class DepartureWatchUiState(
    val viewed: DepartureWatch? = null,
    val armed: DepartureWatch? = null,
    val table: StopDepartures? = null,
    val isLoading: Boolean = false,
    val failed: Boolean = false,
    val vehicleId: String? = null,
    val isFocused: Boolean = false,
) {
    /** Vrai quand la ligne **affichée** est celle qu'on veille — l'état du bouton. */
    val isArmed: Boolean get() = armed != null && armed.id == viewed?.id

    /** Les passages de la ligne affichée, dans l'ordre. Vide tant que rien n'est arrivé. */
    fun times(): List<StopDeparture> {
        val line = viewed ?: return emptyList()
        return table?.forLine(line.line, line.destination).orEmpty()
    }
}

/**
 * La veille d'un passage : « préviens-moi quand celui-là approche ».
 *
 * ## Pourquoi elle ne vit pas dans le volet
 *
 * [StopDetailModel] meurt avec la fiche qu'il sert, et c'est correct pour un
 * tableau qu'on regarde. Une veille, elle, existe précisément pour qu'on **cesse**
 * de regarder : elle doit survivre à la fermeture du volet, sans quoi elle ne
 * rend aucun service. Elle est donc portée par le `ViewModel` de la carte, dont
 * la portée est celle de l'écran.
 *
 * Elle ne survit pas à la mort du processus, et l'écran ne le promet pas :
 * l'alerte est une notification système, pas un réveil. Un service de premier
 * plan changerait cela — il n'est pas nécessaire pour prévenir d'un bus qu'on
 * attend, et coûterait une notification permanente.
 *
 * ## Une boucle, un ou deux arrêts
 *
 * Le volet affiche des horaires, la veille en surveille : le plus souvent ceux
 * du **même** arrêt, et alors une seule requête sert les deux — deux pollers
 * auraient doublé la charge d'un endpoint qu'on prend déjà soin de ménager en
 * cas de panne. Quand les arrêts diffèrent, le tour en interroge deux, à la
 * suite, au même rythme. La boucle recule exactement comme [StopDetailModel] :
 * un fournisseur en difficulté n'est pas sondé plus souvent parce qu'il échoue.
 */
internal class DepartureWatchModel(
    private val repository: StopRepository,
    private val dispatchers: AuleDispatchers,
    private val scope: CoroutineScope,
    private val logger: AuleLogger,
    private val clock: () -> Instant = Instant::now,
    private val onAlert: (DepartureWatchAlert, DepartureWatch) -> Unit = { _, _ -> },
) {
    private val _state = MutableStateFlow(DepartureWatchUiState())
    val state: StateFlow<DepartureWatchUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var engine = DepartureWatchEngine()

    /** Le tableau de l'arrêt veillé, quand ce n'est pas celui qu'on affiche. */
    private var watchTable: StopDepartures? = null

    /** Le volet s'ouvre sur une ligne. La veille en cours, s'il y en a une, ne bouge pas. */
    fun open(watch: DepartureWatch) {
        val current = _state.value
        if (current.viewed?.id == watch.id) return
        _state.value = current.copy(
            viewed = watch,
            // Le tableau vaut pour l'arrêt entier : le garder évite un état de
            // chargement en passant d'une ligne à l'autre du même arrêt.
            table = current.table?.takeIf { it.stopName == watch.stopName },
            isLoading = current.table?.stopName != watch.stopName,
            failed = false,
            // Le véhicule et le suivi appartenaient à la ligne précédente.
            vehicleId = null,
            isFocused = false,
        )
        restartPolling()
    }

    /** Le volet se ferme. La veille, si elle est armée, continue sans lui. */
    fun close() {
        val current = _state.value
        if (current.viewed == null) return
        _state.value = current.copy(
            viewed = null,
            table = null,
            isLoading = false,
            failed = false,
            vehicleId = null,
            // Le suivi s'arrête avec le volet qui le porte : une caméra qui
            // continuerait d'accompagner un bus sans que rien ne l'explique
            // passerait pour une carte bloquée.
            isFocused = false,
        )
        restartPolling()
    }

    /** La carte accompagne le véhicule, ou le laisse repartir. */
    fun setFocused(focused: Boolean) {
        val current = _state.value
        if (current.isFocused == focused) return
        if (focused && current.vehicleId == null) return
        _state.value = current.copy(isFocused = focused)
    }

    /**
     * Arme la veille sur la ligne affichée.
     *
     * Le moteur repart de zéro, et la veille précédente est remplacée : on
     * attend **un** bus. Deux alertes concurrentes demanderaient de dire
     * laquelle a parlé, donc une liste, donc un écran de gestion — pour un
     * besoin qui, sur le terrain, s'exprime au singulier.
     */
    fun arm() {
        val target = _state.value.viewed ?: return
        engine = DepartureWatchEngine()

        // Le tableau sous les yeux est déjà celui de la cible : le reprendre
        // permet d'alerter sans attendre un sondage de plus, et c'est le cas
        // normal — on arme depuis la ligne qu'on regarde.
        watchTable = _state.value.table?.takeIf { it.stopName == target.stopName }
        _state.value = _state.value.copy(armed = target)
        logger.info(LogDomain.APP, "Veille armée sur ${target.line} vers ${target.destination}.")
        restartPolling()
        // Le tableau déjà affiché peut suffire à décider : un bus à deux minutes
        // n'attend pas le prochain sondage pour être annoncé.
        evaluate()
    }

    fun disarm() {
        if (_state.value.armed == null) return
        watchTable = null
        _state.value = _state.value.copy(armed = null)
        restartPolling()
    }

    /** Tout s'éteint : l'écran s'en va. */
    fun clear() {
        stopPolling()
        watchTable = null
        _state.value = DepartureWatchUiState()
    }

    /**
     * Le dernier état de la flotte, d'où sort le véhicule à suivre.
     *
     * Un instantané périmé ne désigne personne : la carte suivrait un marqueur
     * qui n'est plus là, ce qui est exactement l'erreur que
     * [FleetSnapshot.isStale] existe pour éviter.
     */
    fun onFleetSnapshot(snapshot: FleetSnapshot) {
        val current = _state.value
        val target = current.viewed ?: return
        val matched = if (snapshot.isStale) {
            current.vehicleId
        } else {
            matchWatchedVehicle(snapshot.vehicles, target)?.id
        }
        if (matched == current.vehicleId) return
        _state.value = current.copy(
            vehicleId = matched,
            // Le véhicule qu'on suivait a disparu du flux : le suivi tombe avec
            // lui, plutôt que de rester allumé sur une carte qui ne bouge plus.
            isFocused = current.isFocused && matched != null,
        )
    }

    /** Les arrêts qu'un tour doit interroger — un le plus souvent, deux au plus. */
    private fun wantedStops(): List<String> {
        val current = _state.value
        return listOfNotNull(current.viewed?.stopName, current.armed?.stopName).distinct()
    }

    private fun restartPolling() {
        stopPolling()
        if (wantedStops().isEmpty()) return
        pollJob = scope.launch {
            var backoffMs = DEPARTURES_REFRESH_MS
            while (isActive) {
                val stops = wantedStops()
                if (stops.isEmpty()) return@launch
                val succeeded = stops.map { fetch(it) }.all { it }
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

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun fetch(stopName: String): Boolean = try {
        val table = withContext(dispatchers.io) { repository.departures(stopName) }
        val current = _state.value
        if (current.armed?.stopName == stopName) watchTable = table
        if (current.viewed?.stopName == stopName) {
            _state.value = current.copy(table = table, isLoading = false, failed = false)
        }
        evaluate()
        true
    } catch (_: CancellationException) {
        false
    } catch (failure: Exception) {
        logger.warn(LogDomain.NET, "Veille : sondage des passages en échec.", failure)
        // Le tableau reste affiché, mais il vieillit — et un tableau qui vieillit
        // cesse tout seul d'alerter.
        if (_state.value.viewed?.stopName == stopName) {
            _state.value = _state.value.copy(isLoading = false, failed = true)
        }
        false
    }

    /**
     * Confronte le tableau au moteur, et émet ce qu'il en sort.
     *
     * Une approche annoncée **termine** la veille : elle a rempli son office, et
     * une veille qui resterait armée préviendrait ensuite pour tous les bus de la
     * soirée. Le réarmement du moteur, lui, ne sert qu'au cas contraire — celui
     * où l'approche a été manquée faute de sondage, et où c'est le passage
     * suivant qu'il faut annoncer.
     */
    private fun evaluate() {
        val current = _state.value
        val target = current.armed ?: return
        val table = watchTable ?: return
        val now = clock()
        val next = table.forLine(target.line, target.destination).firstOrNull()
        val wait = next?.let { departure ->
            val minutes = departure.waitMinutes(now)
            if (minutes == 0) Wait.Approaching else Wait.Minutes(minutes)
        }
        val alerts = engine.evaluate(wait = wait, fresh = table.isFresh(now))
        if (alerts.isEmpty()) return

        for (alert in alerts) {
            onAlert(alert, target)
        }
        if (alerts.any { it.kind == DepartureWatchAlertKind.APPROACHING }) {
            disarm()
        }
    }
}
