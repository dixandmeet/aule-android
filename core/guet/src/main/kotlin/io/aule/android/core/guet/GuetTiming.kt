package io.aule.android.core.guet

import java.time.Duration
import java.time.Instant

/**
 * La soustraction, et rien d'autre.
 *
 * ## Ce que le type existe pour empêcher
 *
 * Le calcul du Guet tient en une ligne — « heure de passage moins marche moins
 * marge moins préparation » — et c'est précisément pourquoi il mérite un type.
 * Écrit à la volée dans un modèle ou dans une vue, il s'y serait recopié trois
 * fois avec trois conventions de signe, et le jour où l'alerte tombe deux minutes
 * trop tard personne n'aurait su laquelle relire.
 *
 * Aucune horloge ici : `now` est toujours un paramètre. C'est ce qui rend chaque
 * exemple exerçable en une ligne de test, sans appareil et sans attendre.
 *
 * Port de `Native/Aule/Core/Guet/GuetTiming.swift`.
 */
data class GuetTiming(
    /** L'heure à laquelle le véhicule est annoncé. */
    val expectedAt: Instant,
    /**
     * Le temps de marche vers **le quai**, pas vers le lieu. La distinction coûte
     * une traversée de voies — voir [GuetEngine].
     */
    val walkSeconds: Int,
    /** La marge voulue sur le quai avant l'arrivée du véhicule. */
    val platformSeconds: Int,
    /**
     * Le temps qu'il faut pour se préparer avant de sortir. Zéro par défaut : le
     * cas de base est « l'alerte tombe quand il faut partir ».
     */
    val preparationSeconds: Int,
) {
    init {
        // Les durées négatives sont **ramenées à zéro** par le constructeur
        // nommé plutôt que refusées ici : elles ne peuvent venir que d'un réglage
        // aberrant ou d'une soustraction faite en amont, et faire échouer la
        // veille entière pour ça serait pire que de l'ignorer.
    }

    /**
     * L'instant où il faut partir.
     *
     * ⚠️ **Contient déjà [platformSeconds].** C'est `expectedAt − marche − marge`,
     * et non `expectedAt − marche`. Le nom ne le dit pas, et un appelant qui
     * retrancherait sa marge une seconde fois enverrait l'utilisateur quatre
     * minutes trop tôt pour une marge de deux. [slack] est l'autre bout de la
     * même règle : à `leaveAt`, la marge restante vaut exactement
     * [platformSeconds].
     */
    val leaveAt: Instant
        get() = expectedAt.minusSeconds((walkSeconds + platformSeconds).toLong())

    /**
     * L'instant où l'on sonne. Confondu avec [leaveAt] quand la préparation est à
     * zéro, ce qui est le réglage par défaut.
     */
    val alertAt: Instant get() = leaveAt.minusSeconds(preparationSeconds.toLong())

    /**
     * Dans combien de temps le véhicule passe. **Négatif une fois qu'il est
     * passé** — et il faut que ça le soit : ramener à zéro ferait dire « à
     * l'approche » à un tram parti.
     */
    fun vehicleSeconds(now: Instant): Double =
        Duration.between(now, expectedAt).toMillis() / 1_000.0

    /** Dans combien de temps l'utilisateur serait au quai s'il partait maintenant. */
    fun userSeconds(now: Instant): Double = walkSeconds.toDouble()

    /**
     * La marge : le temps qu'il resterait à passer sur le quai en partant
     * maintenant.
     *
     * C'est **ETA véhicule moins ETA utilisateur**, et c'est la seule grandeur
     * sur laquelle la faisabilité se juge. Négative, elle dit de combien on est
     * en retard.
     */
    fun slack(now: Instant): Double = vehicleSeconds(now) - userSeconds(now)

    companion object {
        /**
         * Le constructeur à utiliser : il **ramène les durées négatives à zéro**.
         *
         * Kotlin n'a pas l'équivalent d'un `init` qui réécrit ses paramètres sur
         * une `data class` ; la normalisation vit donc ici, et le constructeur
         * primaire reste ce que `copy` appellera. C'est le seul point où les deux
         * peuvent diverger, et c'est pourquoi il est écrit.
         */
        fun of(
            expectedAt: Instant,
            walkSeconds: Int,
            platformSeconds: Int,
            preparationSeconds: Int = 0,
        ): GuetTiming = GuetTiming(
            expectedAt = expectedAt,
            walkSeconds = walkSeconds.coerceAtLeast(0),
            platformSeconds = platformSeconds.coerceAtLeast(0),
            preparationSeconds = preparationSeconds.coerceAtLeast(0),
        )
    }
}
