package io.aule.android.core.guet

import io.aule.android.core.geo.ApproachDetector
import io.aule.android.core.geo.ApproachState
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.PositionSample
import java.time.Duration
import java.time.Instant

/**
 * Comment un accompagnement se termine.
 *
 * **Quatre sorties, et il en faut quatre.** Les trois premières sont celles qu'on
 * imagine ; c'est la quatrième qui compte. Sans délai de garde, un accompagnement
 * oublié — le téléphone remis en poche, le bus pris sans qu'on ait rien touché —
 * tient le GPS allumé jusqu'à ce que la batterie le dise. C'est le genre de défaut
 * qui ne se voit sur aucun écran.
 */
enum class GuetEscortOutcome {
    /** Rien n'a encore tranché. **Ce n'est pas une fin** : c'est l'absence de fin. */
    ONGOING,

    /** Arrivé au quai. C'est la fin normale. */
    ARRIVED,

    /**
     * Monté. On le sait plus tard qu'on n'aimerait, et c'est suffisant : une fin
     * n'a pas besoin d'être instantanée, elle a besoin d'être juste.
     */
    BOARDED,

    /** L'utilisateur a arrêté. */
    CANCELLED,

    /**
     * **Le délai de garde.** Cinq minutes après l'heure de passage, plus rien de
     * ce qu'on pourrait apprendre ne servirait.
     */
    EXPIRED,
    ;

    val isFinished: Boolean get() = this != ONGOING
}

/**
 * Un accompagnement en cours : vers quel passage, depuis quand, et où l'on en est.
 *
 * Rien ici ne touche à l'horloge, et c'est ce qui le rend vérifiable.
 *
 * Port de `Native/Aule/Core/Guet/GuetEscort.swift`.
 */
class GuetEscort(
    val key: PassageKey,
    val startedAt: Instant,
    /**
     * La marche **prédite** au moment de partir. Comparée à la marche réelle en
     * arrivant, elle donne le facteur d'allure de [GuetHabits] — l'estimation
     * automatique de la vitesse de marche, en un seul nombre et sans historique
     * de déplacements.
     */
    val predictedWalkSeconds: Int,
) {
    val approach = ApproachDetector()

    /**
     * Le niveau **suivi**, pas le niveau brut.
     *
     * ⚠️ C'est le seul endroit du projet où [GuetLevelTracker] sert, et c'est
     * celui qui le justifie : pendant un accompagnement, l'utilisateur **regarde**
     * le niveau bouger. Un unique point GPS pessimiste y ferait clignoter « il
     * faut presser le pas » chez quelqu'un qui marche normalement.
     *
     * Ailleurs — dans le classement, dans l'alerte —, le niveau brut suffit : il
     * n'est lu qu'une fois, au moment de décider, et personne ne le voit trembler.
     */
    val tracker = GuetLevelTracker()

    /** Le niveau à montrer, une fois la dégradation confirmée s'il y a lieu. */
    val level: GuetLevel get() = tracker.level

    /**
     * Nourrit le suivi.
     *
     * @param cause d'où vient l'observation. Une position se confirme, une heure
     *   remesurée passe tout de suite — voir [GuetLevelTracker].
     */
    fun track(observed: GuetLevel, cause: GuetLevelCause): GuetLevel =
        tracker.update(observed, cause)

    /**
     * Nourrit le détecteur d'approche. Rendu séparé du verdict pour que le verdict
     * reste une fonction pure de ce qu'on lui donne.
     */
    fun observe(sample: PositionSample, quay: Coordinate, now: Instant) {
        approach.update(sample, quay, now)
    }

    /** Combien de temps la marche a réellement pris. `null` tant qu'on n'est pas arrivé. */
    fun actualWalkSeconds(now: Instant): Int? {
        if (approach.state != ApproachState.ARRIVED) return null
        return Duration.between(startedAt, now).seconds.toInt()
    }

    companion object {
        /**
         * Cinq minutes après l'heure de passage. Assez pour qu'un bus en retard
         * arrive quand même, assez peu pour qu'un accompagnement oublié s'éteigne
         * avant d'avoir coûté quelque chose.
         */
        const val GUARD_DELAY_SECONDS = 5L * 60

        /**
         * Le verdict.
         *
         * ## L'ordre des questions **est** la règle
         *
         * 1. **Monté** d'abord, parce que c'est le plus certain et le plus utile.
         * 2. **Arrivé** ensuite. En pratique c'est celui-ci qui tombe le premier —
         *    on atteint le quai avant de monter —, et il clôt l'accompagnement ; la
         *    montée reste la sortie de celui dont le GPS n'a jamais confirmé le quai.
         * 3. **Périmé** en dernier, faute de mieux à conclure.
         *
         * L'annulation n'est pas ici : elle ne s'observe pas, elle se décide.
         */
        fun outcome(
            approach: ApproachState,
            hasBoarded: Boolean,
            expectedAt: Instant,
            now: Instant,
        ): GuetEscortOutcome {
            if (hasBoarded) return GuetEscortOutcome.BOARDED
            if (approach == ApproachState.ARRIVED) return GuetEscortOutcome.ARRIVED
            if (!now.isBefore(expectedAt.plusSeconds(GUARD_DELAY_SECONDS))) {
                return GuetEscortOutcome.EXPIRED
            }
            return GuetEscortOutcome.ONGOING
        }
    }
}
