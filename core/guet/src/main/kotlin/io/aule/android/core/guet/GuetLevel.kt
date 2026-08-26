package io.aule.android.core.guet

import java.time.Instant

/**
 * Où l'on en est dans le temps. **Une phase, pas un jugement.**
 *
 * Elle ne dépend que de l'horloge et d'instants fixes tirés de [GuetTiming] —
 * donc elle ne porte aucun bruit de mesure, et rien ne justifie de la confirmer
 * avant de l'adopter. C'est la faisabilité, à côté, qui en porte.
 */
enum class GuetPhase {
    /**
     * **Ce n'est pas une phase, c'est l'absence de réponse.** Aucun point
     * exploitable, ou aucune heure fiable. L'appelant attend plutôt que de
     * choisir.
     */
    UNDECIDED,

    /** Trop tôt pour dire quoi que ce soit. */
    TOO_EARLY,

    /**
     * Il est temps de se préparer. **N'existe que si la préparation est non
     * nulle** — au réglage par défaut, `alertAt == leaveAt` et cette phase est un
     * intervalle vide.
     */
    PREPARE,

    /** Il faut partir. */
    LEAVE_NOW,

    /**
     * L'heure de partir est passée. **Ne dit pas que c'est perdu** : c'est la
     * faisabilité qui le dit, et « parti trop tard mais rattrapable » est un état
     * courant, pas un cas limite.
     */
    GONE,
}

/**
 * Est-ce encore faisable. Indépendant de la phase.
 *
 * L'ordre des cas **est** l'ordre de gravité : [severity] en dépend, et le suivi
 * s'en sert pour distinguer une dégradation d'une amélioration.
 */
enum class GuetFeasibility {
    /** Rien d'exploitable. Voir [GuetPhase.UNDECIDED]. */
    UNDECIDED,

    /** La marge tient : on arrivera au quai avant le véhicule, sans courir. */
    COMFORTABLE,

    /** Attrapable, mais il ne faut pas traîner. */
    TIGHT,

    /** La marge est passée sous zéro et reste rattrapable en pressant le pas. */
    RISKY,

    /** C'est perdu. */
    MISSED,
    ;

    /**
     * `null` pour [UNDECIDED] : une absence de réponse ne se compare à rien, et
     * lui donner un rang la ferait passer pour « meilleure » ou « pire » que
     * quelque chose.
     */
    val severity: Int?
        get() = when (this) {
            UNDECIDED -> null
            COMFORTABLE -> 0
            TIGHT -> 1
            RISKY -> 2
            MISSED -> 3
        }
}

/**
 * Les deux axes, ensemble.
 *
 * ## Pourquoi deux et non un
 *
 * Un seul énuméré — `tooEarly, prepare, leaveNow, onTime, tight, risky, missed` —
 * rend **inexprimable** « il faut partir maintenant, *et* c'est déjà serré », qui
 * est le cas le plus fréquent sur le trottoir. Il force alors une règle de
 * préséance que rien ne justifie : faut-il afficher « partez » ou « c'est
 * serré » ? Les deux, et c'est tout le sujet.
 *
 * Port de `Native/Aule/Core/Guet/GuetLevel.swift` et `GuetLevelAxes.swift`.
 */
data class GuetLevel(
    val phase: GuetPhase,
    val feasibility: GuetFeasibility,
) {
    /**
     * Vrai quand le véhicule reste attrapable. [GuetFeasibility.UNDECIDED] n'est
     * pas attrapable — il n'est rien.
     */
    val isReachable: Boolean
        get() = feasibility == GuetFeasibility.COMFORTABLE ||
            feasibility == GuetFeasibility.TIGHT ||
            feasibility == GuetFeasibility.RISKY

    companion object {
        val UNDECIDED = GuetLevel(GuetPhase.UNDECIDED, GuetFeasibility.UNDECIDED)

        /**
         * En deçà de cette marge, c'est serré. Quarante-cinq secondes : le temps
         * de traverser un carrefour qu'on n'avait pas prévu.
         */
        const val TIGHT_SLACK = 45.0

        /**
         * Au-delà de ce retard, c'est perdu. Une minute : au-delà, presser le pas
         * ne rattrape plus, et le dire ferait courir quelqu'un pour rien.
         */
        const val MISSED_SLACK = -60.0

        /**
         * Combien de temps « partez maintenant » reste affiché après l'instant de
         * départ. Sans cette fenêtre, la phase basculerait en [GuetPhase.GONE] à
         * la seconde même où l'alerte s'affiche.
         */
        const val LEAVE_WINDOW = 60L

        /**
         * Le niveau que ce minutage vaut à cet instant.
         *
         * **Toujours décidé** : `UNDECIDED` n'est pas produit ici, il n'existe que
         * faute d'entrée — voir [GuetLevelTracker].
         */
        fun of(timing: GuetTiming, now: Instant): GuetLevel {
            val slack = timing.slack(now)

            // Le véhicule est passé : la marge seule ne suffit pas à le dire. Avec
            // une marche nulle, un tram parti depuis dix secondes rendrait une
            // marge de −10 s, qu'on lirait « rattrapable ».
            val feasibility = when {
                timing.vehicleSeconds(now) <= 0 -> GuetFeasibility.MISSED
                slack <= MISSED_SLACK -> GuetFeasibility.MISSED
                slack < 0 -> GuetFeasibility.RISKY
                slack < TIGHT_SLACK -> GuetFeasibility.TIGHT
                else -> GuetFeasibility.COMFORTABLE
            }

            val phase = when {
                now < timing.alertAt -> GuetPhase.TOO_EARLY
                now < timing.leaveAt -> GuetPhase.PREPARE
                now < timing.leaveAt.plusSeconds(LEAVE_WINDOW) -> GuetPhase.LEAVE_NOW
                else -> GuetPhase.GONE
            }

            return GuetLevel(phase = phase, feasibility = feasibility)
        }
    }
}

/** D'où vient l'observation. **C'est ce paramètre qui décide s'il faut confirmer.** */
enum class GuetLevelCause {
    /** Un point de position neuf : le marcheur avance moins vite — ou le GPS le croit. */
    POSITION,

    /** Une heure de passage remesurée. */
    SCHEDULE,
}

/**
 * « Le niveau s'est-il vraiment dégradé, ou est-ce un point de travers ? »
 *
 * ## Ce qui est repris du détecteur d'approche, et ce qui ne l'est pas
 *
 * Repris : **deux confirmations consécutives avant de dégrader, remontée
 * immédiate.** Un unique point GPS pessimiste ne doit pas annoncer « vous risquez
 * de ne pas l'avoir » à quelqu'un qui marche normalement ; apprendre qu'on va y
 * arriver, en revanche, n'a aucune raison d'attendre.
 *
 * Pas repris : **l'indifférence à la cause.** Le détecteur d'approche est nourri
 * d'un seul flux, des points GPS toutes les cinq secondes. Le Guet en a deux, qui
 * n'ont pas la même horloge — la position, et l'heure de passage. Confirmer deux
 * fois une heure remesurée, c'est afficher sciemment pendant tout un cycle de
 * rafraîchissement une heure qu'on sait fausse. Une mesure qui bouge n'est pas du
 * bruit : c'est le réseau qui se corrige.
 *
 * D'où la règle, en une phrase : **seule une dégradation de faisabilité imputée à
 * la position se confirme.** La phase, elle, passe toujours — elle ne dépend que
 * de l'horloge.
 */
class GuetLevelTracker {

    var level: GuetLevel = GuetLevel.UNDECIDED
        private set

    private var pending: GuetFeasibility = GuetFeasibility.UNDECIDED
    private var confirmations = 0

    /** @return le niveau **après** cette observation. */
    fun update(observed: GuetLevel, cause: GuetLevelCause): GuetLevel {
        // La phase suit toujours, y compris pendant qu'une dégradation de
        // faisabilité attend sa confirmation : « il faut partir » ne doit pas
        // rester bloqué derrière « la marge s'est peut-être effondrée ».
        level = level.copy(phase = observed.phase)

        val observedRank = observed.feasibility.severity
        val currentRank = level.feasibility.severity
        val degrading = cause == GuetLevelCause.POSITION &&
            observedRank != null &&
            currentRank != null &&
            observedRank > currentRank

        if (!degrading) {
            // Amélioration, égalité, heure remesurée, ou premier verdict : on adopte.
            level = level.copy(feasibility = observed.feasibility)
            pending = observed.feasibility
            confirmations = 0
            return level
        }

        if (observed.feasibility == pending) {
            confirmations += 1
        } else {
            pending = observed.feasibility
            confirmations = 1
        }
        if (confirmations >= CONFIRMATIONS_REQUIRED) {
            level = level.copy(feasibility = observed.feasibility)
            confirmations = 0
        }
        return level
    }

    /**
     * Remet à l'absence de réponse. Appelé quand on change de passage suivi :
     * garder l'état du précédent ferait hériter le nouveau d'une dégradation qui
     * ne le concerne pas.
     */
    fun reset() {
        level = GuetLevel.UNDECIDED
        pending = GuetFeasibility.UNDECIDED
        confirmations = 0
    }

    private companion object {
        const val CONFIRMATIONS_REQUIRED = 2
    }
}
