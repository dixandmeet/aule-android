package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.ScheduledTrip
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.repository.DriverServiceRepository
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La course du véhicule qu'on suit, ou l'absence de course.
 *
 * [isUnavailable] n'est pas une panne : le catalogue GTFS ne connaît pas
 * toujours la course d'un véhicule — une ligne renforcée, un service spécial,
 * un dépôt pas encore publié. Le volet le dit en une ligne et n'affiche pas de
 * plan, ce qui est différent d'un plan vide.
 */
internal data class VehicleTripUiState(
    val vehicleId: String? = null,
    val trip: ScheduledTrip? = null,
    val isLoading: Boolean = false,
    val isUnavailable: Boolean = false,
)

/**
 * Le plan de ligne du véhicule suivi : sa desserte horodatée, chargée une fois.
 *
 * ## Pourquoi elle ne se recharge pas
 *
 * La desserte vient du catalogue GTFS, comme la grille horaire : elle change à
 * la fréquence d'un dépôt, pas d'un sondage. Ce qui bouge dans le plan de
 * ligne — quels arrêts sont derrière, dans combien de minutes est le prochain —
 * se recalcule à chaque position reçue, sans redemander une seule ligne au
 * serveur. Sonder la course au rythme de la flotte ferait interroger des tables
 * volumineuses quatre fois par minute pour réécrire exactement les mêmes heures.
 *
 * ## Elle ne se charge que pendant le suivi
 *
 * Ouvrir la fiche d'un véhicule est un geste de curiosité — « c'est quelle
 * ligne, il va où » — et il se joue en trois secondes. Le suivi, lui, est une
 * intention : on reste avec ce véhicule. C'est à ce moment-là que la desserte
 * vaut sa requête, et pas avant : charger à la sélection ferait payer une
 * course entière à chaque bus effleuré sur la carte.
 *
 * ## Sans session, pas de plan
 *
 * Les tables GTFS exigent une session, comme la grille horaire. Le repli est le
 * même : on ne montre rien, plutôt qu'un plan à moitié deviné.
 */
internal class VehicleTripModel(
    private val repository: DriverServiceRepository?,
    private val session: () -> AuthSession?,
    private val dispatchers: AuleDispatchers,
    private val scope: CoroutineScope,
    private val logger: AuleLogger,
    private val now: () -> Instant = { Instant.now() },
) {
    private val _state = MutableStateFlow(VehicleTripUiState())
    val state: StateFlow<VehicleTripUiState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * On suit ce véhicule : charge sa course si ce n'est pas déjà la sienne.
     *
     * Appelée à chaque sondage de flotte — le véhicule y arrive rafraîchi, avec
     * une nouvelle position — d'où le garde sur l'identifiant : sans lui, la
     * course serait redemandée quatre fois par minute pour rien.
     */
    fun follow(vehicle: TransportVehicle?) {
        if (vehicle == null) {
            close()
            return
        }
        if (_state.value.vehicleId == vehicle.id) return
        load(vehicle)
    }

    /** Le suivi s'arrête, ou le volet se ferme : le plan n'a plus d'objet. */
    fun close() {
        job?.cancel()
        job = null
        if (_state.value != VehicleTripUiState()) {
            _state.value = VehicleTripUiState()
        }
    }

    private fun load(vehicle: TransportVehicle) {
        job?.cancel()
        _state.value = VehicleTripUiState(
            vehicleId = vehicle.id,
            isLoading = true,
        )
        val account = session()
        val services = repository
        if (account == null || services == null) {
            _state.value = _state.value.copy(isLoading = false, isUnavailable = true)
            return
        }
        job = scope.launch {
            val trip = try {
                withContext(dispatchers.io) {
                    services.nearestActiveTrip(
                        session = account,
                        lineId = vehicle.lineId,
                        // Le sens n'est pas connu du flux de flotte : il ne
                        // publie qu'une destination. Un `directionId` négatif
                        // laisse le dépôt choisir le profil dont le terminus
                        // ressemble le plus à celle-ci, ce qui est exactement
                        // l'information dont on dispose.
                        directionId = UNKNOWN_DIRECTION,
                        destinationHint = vehicle.destination,
                        near = vehicle.coordinate,
                        at = now(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Course du véhicule suivi introuvable.", failure)
                null
            }
            // Le véhicule suivi a pu changer pendant la requête : on ne pose le
            // plan que s'il décrit toujours celui qu'on regarde.
            if (_state.value.vehicleId != vehicle.id) return@launch
            _state.value = _state.value.copy(
                trip = trip?.takeIf { it.stops.size >= 2 },
                isLoading = false,
                isUnavailable = trip == null || trip.stops.size < 2,
            )
        }
    }

    private companion object {
        /** « Je ne sais pas dans quel sens il roule, déduis-le du terminus. » */
        const val UNKNOWN_DIRECTION = -1
    }
}
