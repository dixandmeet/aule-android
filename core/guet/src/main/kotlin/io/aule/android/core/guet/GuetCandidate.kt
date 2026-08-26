package io.aule.android.core.guet

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode

/**
 * Une marche mesurée, **et d'où elle l'a été**.
 *
 * ## Pourquoi les deux ne se séparent pas
 *
 * Une durée de marche sans son point de départ ne vaut rien : elle serait vraie
 * pour toujours et depuis n'importe où, c'est-à-dire que **la marche ne bougerait
 * pas pendant qu'on marche**. La première écriture iOS les tenait dans deux
 * champs, avec un commentaire pour dire qu'ils allaient ensemble ; les tests ont
 * montré qu'un commentaire ne suffit pas — ils passaient une durée sans origine,
 * et le moteur ne pouvait plus rien en faire.
 *
 * Un seul type les rend inséparables, et c'est le compilateur qui le tient.
 */
data class MeasuredWalk(
    val seconds: Int,
    val from: Coordinate,
)

/**
 * Un passage que la veille a retenu, avec de quoi le juger et de quoi l'annoncer.
 *
 * Port de `GuetCandidate` dans `Native/Aule/Core/Guet/GuetContext.swift`.
 */
data class GuetCandidate(
    val key: PassageKey,
    /** Le nom de lieu, celui que l'API des passages connaît. */
    val place: String,
    /** Le quai retenu — celui vers lequel on marchera, et dont on donnera la position. */
    val stop: TransitStop,
    val line: String,
    val lineColor: String? = null,
    val destination: String,
    val mode: TransportMode? = null,
    /**
     * L'heure vient-elle d'une mesure ou de l'horaire. Portée jusqu'ici parce que
     * la programmation d'une notification en dépend **et l'inverse de ce qu'on
     * croit** : voir [GuetSchedule].
     */
    val isRealtime: Boolean,
    val timing: GuetTiming,
    val level: GuetLevel,
    val score: GuetScore,
) {
    val id: String get() = key.raw
}
