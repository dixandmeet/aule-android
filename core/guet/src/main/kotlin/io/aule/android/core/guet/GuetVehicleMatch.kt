package io.aule.android.core.guet

import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.HeadsignMatch
import io.aule.android.core.model.TransportVehicle
import java.time.Instant
import kotlin.math.abs

/**
 * Quel véhicule de la flotte assure ce passage — ou aucun.
 *
 * ## Une heuristique, écrite comme telle
 *
 * Le temps réel ne porte **aucun identifiant de course**, et les deux bouts de
 * l'API ne nomment pas les mêmes objets : les passages désignent une course par
 * `ligne-sens-heure-rang`, le radar par son propre identifiant. Deux espaces
 * disjoints, mesurés côté iOS le 22/08/2026. Il ne reste que l'indice de ligne,
 * les libellés de terminus et l'heure — d'où [HeadsignMatch].
 *
 * Mesuré le même jour : l'appariement par `(ligne, destination)` retrouve **24
 * passages sur 25** à Commerce. C'est donc praticable, et ça reste une
 * heuristique.
 *
 * ## Unique ou rien
 *
 * ⚠️ C'est la règle du contrat, et l'enjeu est ici plus lourd qu'ailleurs. Un
 * titre pauvre vaut mieux qu'un titre faux ; désigner le **mauvais** véhicule
 * ferait suivre à la caméra un bus qui va ailleurs, pendant que l'utilisateur
 * décide de courir vers son quai. Deux candidats, donc aucun.
 *
 * Port de `Native/Aule/Core/Guet/GuetVehicleMatch.swift`.
 */
object GuetVehicleMatch {

    /**
     * De combien l'heure annoncée par le véhicule peut s'écarter de celle du
     * passage.
     *
     * ⚠️ **La même valeur que [GuetLedger.REMATCH_TOLERANCE], et pour la même
     * raison** : le temps réel dérive entre deux sources qui ne l'échantillonnent
     * pas au même instant. Mesuré côté iOS le 22/08 : le passage annonçait 13:03,
     * la course disait 13:04 pour le même arrêt. Deux constantes séparées auraient
     * divergé au premier ajustement.
     */
    const val ARRIVAL_TOLERANCE = GuetLedger.REMATCH_TOLERANCE

    /**
     * Le véhicule qui assure ce passage, s'il n'y a pas d'ambiguïté.
     *
     * @return `null` quand aucun véhicule ne répond, **et aussi** quand plusieurs
     *   répondent. Les deux cas se traitent pareil côté écran, et les distinguer
     *   inviterait à trancher là où rien ne permet de le faire.
     */
    fun vehicle(
        candidate: GuetCandidate,
        fleet: FleetSnapshot,
        now: Instant,
    ): TransportVehicle? {
        val found = fleet.vehicles.filter { serves(candidate, it, now) }
        // Unique ou rien. `size == 1` et non `firstOrNull` : c'est toute la règle.
        return if (found.size == 1) found.first() else null
    }

    /**
     * Ce véhicule peut-il être celui du passage.
     *
     * Trois conditions, dans l'ordre du moins cher au plus cher : la ligne, la
     * destination, puis l'heure.
     */
    fun serves(
        candidate: GuetCandidate,
        vehicle: TransportVehicle,
        now: Instant,
    ): Boolean {
        // Le théorique porte `ALEOP:300`, le suivi porte `300` — la clé comparable
        // prend ce qui suit le dernier `:`. Comparer les identifiants bruts
        // n'apparie jamais rien.
        val line = HeadsignMatch.routeKey(candidate.line)
        val matchesLine = HeadsignMatch.routeKey(vehicle.lineName) == line ||
            HeadsignMatch.routeKey(vehicle.lineId) == line
        if (!matchesLine) return false

        if (!HeadsignMatch.headsign(vehicle.destination, listOf(candidate.destination))) {
            return false
        }

        return agreesOnTime(candidate, vehicle, now)
    }

    /**
     * L'heure concorde-t-elle.
     *
     * Deux façons de le dire, et la seconde sauve les cas où le véhicule n'annonce
     * pas d'attente : soit son ETA le pose à l'heure du passage, à
     * [ARRIVAL_TOLERANCE] près, soit il annonce comme prochain arrêt **le lieu
     * même** du candidat.
     *
     * ⚠️ **Sans `etaSeconds`, on n'invente pas d'heure.** Un véhicule muet sur son
     * attente et dont le prochain arrêt ne correspond pas est écarté : le retenir
     * reviendrait à apparier sur la seule ligne, ce qui apparie n'importe quel
     * véhicule de la ligne — y compris celui qui vient de passer.
     */
    private fun agreesOnTime(
        candidate: GuetCandidate,
        vehicle: TransportVehicle,
        now: Instant,
    ): Boolean {
        val eta = vehicle.etaSeconds
        if (eta != null && eta.isFinite()) {
            val arrival = now.plusMillis((eta * 1000).toLong())
            val drift = (arrival.toEpochMilli() - candidate.timing.expectedAt.toEpochMilli()) / 1000.0
            if (abs(drift) <= ARRIVAL_TOLERANCE) return true
        }
        val next = vehicle.nextStop ?: return false
        return HeadsignMatch.folded(next) == HeadsignMatch.folded(candidate.place) ||
            HeadsignMatch.folded(next) == HeadsignMatch.folded(candidate.stop.name)
    }
}
