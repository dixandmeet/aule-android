package io.aule.android.core.guet

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.NearbyDigest
import io.aule.android.core.model.StopDepartures
import java.time.Instant
import java.time.ZoneId

/**
 * Tout ce dont le moteur a besoin, et rien d'autre.
 *
 * Immuable, sans aucune entrée-sortie : c'est ce qui permet d'exercer une
 * décision en une ligne de test. La couche Android remplit ce type ; **le moteur
 * ne sait pas qu'un réseau existe.**
 *
 * Port de `Native/Aule/Core/Guet/GuetContext.swift`.
 *
 * ## Ce qui manque par rapport à l'iOS, et pourquoi
 *
 * Le contexte iOS porte un **tamis de quai** — la desserte d'un quai précis, qui
 * permet d'écarter les passages du pôle qui ne partent pas de là où l'on va. Il
 * n'est pas ici parce que le modèle Android n'a pas encore la notion de *portée*
 * d'une desserte : `ServingLine` ne dit pas si elle vaut pour un quai ou pour un
 * pôle entier, et tamiser avec une desserte de portée « pôle » retirerait des
 * passages parfaitement légitimes en affirmant qu'ils partent d'ailleurs.
 *
 * Le comportement obtenu est donc **exactement celui de l'iOS quand le tamis est
 * absent** : on garde les passages du pôle entier. C'est la branche que l'iOS
 * qualifie de « seule réponse honnête à une absence », et non une régression.
 */
data class GuetContext(
    val now: Instant,
    val position: Coordinate,

    /**
     * Les lieux autour de l'utilisateur, tels que l'inventaire les rend — **un
     * lieu, une entrée**.
     */
    val nearby: List<NearbyDigest.StopEntry> = emptyList(),

    /** Les passages, indexés par nom de lieu (`TransitStop.departuresKey`). */
    val departures: Map<String, StopDepartures> = emptyMap(),

    /** Les marches **mesurées**, indexées par nom de lieu. Absent = estimation géométrique. */
    val walkSeconds: Map<String, MeasuredWalk> = emptyMap(),

    val preferences: GuetPreferences = GuetPreferences(),
    val habits: GuetHabits = GuetHabits(),
    val ledger: GuetLedger = GuetLedger(),

    /** Les positions de la flotte sont-elles périmées. Pèse sur la fraîcheur. */
    val isFleetStale: Boolean = false,

    /**
     * La destination visée, quand un trajet est actif. `null` quand il n'y en a
     * pas — et `null` fait peser le critère de direction **neutre**, jamais zéro.
     */
    val activeDestination: String? = null,

    /**
     * Injecté plutôt que lu : les bandes horaires des habitudes dépendent du
     * fuseau, et un test qui prendrait celui de la machine échouerait en voyage.
     */
    val zone: ZoneId = ZoneId.systemDefault(),
)
