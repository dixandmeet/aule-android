package io.aule.android.core.guet

import java.time.Duration
import java.time.Instant

/**
 * Une notification à programmer, et de quoi l'écrire.
 *
 * **Aucune phrase** : la formulation vit dans les ressources, comme partout
 * (ADR-011). Ce type ne porte que des valeurs — c'est ce qui permet de le décider
 * ici, sans `AlarmManager` ni `NotificationManager`.
 */
data class GuetScheduledAlert(
    val key: PassageKey,
    /** Quand la notification doit tomber. */
    val fireAt: Instant,
    val expectedAt: Instant,
    val line: String,
    val destination: String,
    val place: String,
    val stopName: String,
) {
    val id: String get() = key.raw
}

/** Ce qu'on a programmé, **et ce qu'on a sacrifié**. */
data class GuetSchedulePlan(
    val alerts: List<GuetScheduledAlert>,
    /**
     * Combien de passages éligibles n'ont pas tenu sous le plafond.
     *
     * ⚠️ **Jamais tu.** Une troncature silencieuse se lit « on a tout couvert », et
     * c'est exactement ce qu'on ne peut pas laisser croire d'une veille : quelqu'un
     * qui compte dessus n'a aucun moyen de savoir qu'elle s'est arrêtée au douzième.
     */
    val dropped: Int,
) {
    companion object {
        val EMPTY = GuetSchedulePlan(emptyList(), 0)
    }
}

/**
 * Quels instants programmer, combien, et lesquels sacrifier.
 *
 * ## Pourquoi ce module est pur
 *
 * Le portage vers `AlarmManager` est une transcription : une boucle, un contenu,
 * un déclencheur. **Toutes les décisions sont ici**, où elles s'exercent en une
 * ligne sans appareil ni autorisation système — et où l'on peut les relire.
 *
 * ## L'inversion qui surprend, et qu'il ne faut pas « corriger »
 *
 * Une notification programmée porte l'heure calculée **au moment de la
 * programmation**. Une estimation temps réel dérive : programmée quarante minutes
 * à l'avance, elle sonne « partez maintenant » sur une heure qui était vraie il y
 * a quarante minutes, et rien ne peut la corriger — c'est tout l'intérêt d'une
 * notification pré-programmée, et c'est aussi sa limite.
 *
 * D'où la règle, contre-intuitive et juste : **loin dans le temps, le théorique
 * bat le temps réel.** Un horaire ne dérive pas ; il est faux d'une façon stable,
 * donc programmable. Une mesure, elle, ne vaut que tant qu'elle est fraîche.
 *
 * | Nature du passage | Horizon | Pourquoi |
 * |---|---|---|
 * | mesuré | [REALTIME_HORIZON] | au-delà, la mesure dérive plus vite que la marge ne l'absorbe |
 * | horaire | [TIMETABLE_HORIZON], avec [TIMETABLE_MARGIN] en plus | stable, mais grossier — on prévient un peu plus tôt |
 *
 * Port de `Native/Aule/Core/Guet/GuetSchedule.swift`.
 */
object GuetSchedule {

    /**
     * Douze notifications en attente.
     *
     * Android n'a pas le plafond de soixante-quatre d'iOS, mais il a d'autres
     * limites — les alarmes exactes se demandent, et le système peut les refuser.
     * Douze couvrent déjà une soirée, et la même valeur des deux côtés garde les
     * deux applications comparables.
     */
    const val MAX_SCHEDULED = 12

    /** Au-delà, une estimation temps réel n'est plus programmable. */
    const val REALTIME_HORIZON = 30.0 * 60

    /**
     * L'horizon du théorique. Trois heures : c'est aussi tout ce que le réseau
     * annonce — au-delà, l'API des passages rend « aucun passage prévu dans les
     * trois prochaines heures ». Programmer plus loin serait programmer sur du vide.
     */
    const val TIMETABLE_HORIZON = 3.0 * 3600

    /**
     * Ce qu'on retranche en plus à un horaire théorique. Deux minutes : un horaire
     * est juste en moyenne et faux à la minute, et il vaut mieux attendre au quai
     * que regarder partir.
     */
    const val TIMETABLE_MARGIN = 120L

    /**
     * Ce qu'il faut programmer, maintenant.
     *
     * @param ledger ce qui a déjà été dit. Un passage refusé, accepté ou déjà
     *   annoncé n'a plus rien à faire sonner — c'est la même règle qu'en
     *   application, et elle doit valoir hors d'elle sous peine de réveiller
     *   quelqu'un pour un bus qu'il a refusé.
     */
    fun plan(
        candidates: List<GuetCandidate>,
        ledger: GuetLedger,
        now: Instant,
    ): GuetSchedulePlan {
        val eligible = mutableListOf<GuetScheduledAlert>()
        val seen = mutableSetOf<PassageKey>()

        for (candidate in candidates) {
            if (candidate.key in seen) continue
            if (ledger.status(candidate.key)?.let(::isSilent) == true) continue
            if (!candidate.level.isReachable) continue

            val ahead = Duration.between(now, candidate.timing.expectedAt).toMillis() / 1000.0
            val horizon = if (candidate.isRealtime) REALTIME_HORIZON else TIMETABLE_HORIZON
            if (ahead <= 0 || ahead > horizon) continue

            val fireAt = if (candidate.isRealtime) {
                candidate.timing.alertAt
            } else {
                candidate.timing.alertAt.minusSeconds(TIMETABLE_MARGIN)
            }
            // Un instant déjà passé ne se programme pas : le présent est l'affaire
            // de l'alerte en application, qui est déjà là et qui sait répondre.
            if (!fireAt.isAfter(now)) continue

            seen += candidate.key
            eligible += GuetScheduledAlert(
                key = candidate.key,
                fireAt = fireAt,
                expectedAt = candidate.timing.expectedAt,
                line = candidate.line,
                destination = candidate.destination,
                place = candidate.place,
                stopName = candidate.stop.name,
            )
        }

        // Le plus tôt d'abord : c'est celui qu'on raterait si on le sacrifiait.
        val ordered = eligible.sortedBy { it.fireAt }
        return GuetSchedulePlan(
            alerts = ordered.take(MAX_SCHEDULED),
            dropped = (ordered.size - MAX_SCHEDULED).coerceAtLeast(0),
        )
    }

    /** Vrai quand cet état interdit de sonner à nouveau pour ce passage. */
    private fun isSilent(status: PassageStatus): Boolean = status != PassageStatus.Detected
}
