package io.aule.android.core.guet

/**
 * Les six critères, et ce que la table de poids en fait.
 *
 * Chaque critère rend une valeur dans `[0, 1]`, les poids vivent dans **une seule
 * table**, et les tests portent sur les *renversements* qu'ils produisent, pas sur
 * les valeurs — ces chiffres sont un point de départ à calibrer sur le terrain.
 *
 * ## La règle du neutre
 *
 * Un critère qu'on **ne peut pas** évaluer vaut [NEUTRAL], jamais zéro. Zéro est
 * un jugement — « cette ligne ne vous ressemble pas » —, et le porter faute
 * d'historique pénaliserait en silence quelqu'un qui vient d'allumer la veille.
 * La distinction se lit dans les signatures : les critères qui peuvent ne pas
 * savoir prennent un paramètre nullable.
 *
 * Port de `Native/Aule/Core/Guet/GuetScoring.swift`.
 */
object GuetScoring {

    /** Ce que vaut un critère qu'on ne sait pas évaluer. */
    const val NEUTRAL = 0.5

    // ------------------------------------------------ reach — la marge, et rien d'autre

    /**
     * Ce que vaut la marge restante.
     *
     * **Monotone**, et c'est une divergence délibérée avec le scoring du Padeur.
     * Padeur *choisit maintenant* entre des options simultanées : il lui faut
     * décoter une marge d'un quart d'heure, qui signifie « attendre debout ». Le
     * Guet, lui, *attend le bon moment* — une marge de quinze minutes ne vaut pas
     * moins, elle n'est simplement pas encore l'heure de sonner, et c'est le
     * moteur qui le dit. Décoter ici ferait faire deux fois le même travail, en
     * désaccord.
     *
     * ⚠️ **Ce critère doit s'accorder avec [GuetFeasibility] à chacune de ses
     * frontières**, et il ne l'a pas fait du premier coup côté iOS : une marge
     * nulle était notée zéro quand l'axe de faisabilité la déclarait `TIGHT`, et
     * une marge de −30 s valait zéro quand il la déclarait `RISKY` —
     * c'est-à-dire rattrapable. Un moteur qui juge inatteignable ce que son
     * propre niveau annonce comme rattrapable ne sonne jamais dans le seul cas où
     * il devrait : au moment précis où il faut partir. Le désaccord est
     * verrouillé par un test.
     */
    fun reach(slackSeconds: Double): Double {
        if (slackSeconds >= GuetLevel.TIGHT_SLACK) return 1.0
        if (slackSeconds >= 0) {
            // Attrapable mais serré : on ne l'écarte pas, on ne le préfère pas non plus.
            return 0.5 + 0.5 * (slackSeconds / GuetLevel.TIGHT_SLACK)
        }
        if (slackSeconds > GuetLevel.MISSED_SLACK) {
            // En retard, mais rattrapable en pressant le pas. Ça vaut moins qu'une
            // marge — jamais rien : le noter zéro contredirait le mot « rattrapable ».
            return 0.5 * (1 - slackSeconds / GuetLevel.MISSED_SLACK)
        }
        return 0.0
    }

    // ----------------------------------------------- proximity — ce qu'on a à marcher

    /**
     * Deux minutes de marche ou moins valent 1 ; vingt minutes valent 0.
     *
     * Distinct de [reach] malgré l'apparence : deux quais peuvent offrir la même
     * marge, l'un à cent mètres et l'autre à un kilomètre avec un bus plus tardif.
     * Le premier est meilleur, et seul ce critère le dit.
     */
    fun proximity(walkSeconds: Int): Double {
        val seconds = walkSeconds.coerceAtLeast(0).toDouble()
        if (seconds <= COMFORTABLE_WALK) return 1.0
        return (1 - (seconds - COMFORTABLE_WALK) / (USELESS_WALK - COMFORTABLE_WALK))
            .coerceAtLeast(0.0)
    }

    private const val COMFORTABLE_WALK = 120.0
    private const val USELESS_WALK = 1200.0

    // ------------------------------------------- affinity — suivie, ou fréquentée

    /**
     * @param isFollowed la ligne est-elle explicitement suivie. Un choix déclaré
     *   l'emporte sur une habitude devinée, et **sans condition** : c'est la seule
     *   chose que l'utilisateur ait dite à voix haute.
     * @param habit la fréquentation mesurée, ou `null` faute d'historique.
     */
    fun affinity(isFollowed: Boolean, habit: Double?): Double {
        if (isFollowed) return 1.0
        return habit ?: NEUTRAL
    }

    // ------------------------------------------------------------------ direction

    /**
     * @param match 1 quand la destination du passage est celle qu'on vise, 0 quand
     *   elle s'en éloigne, `null` quand ni trajet actif ni habitude ne permettent
     *   d'en juger.
     */
    fun direction(match: Double?): Double = match ?: NEUTRAL

    // ------------------------------------------------ silence — ce qu'on a déjà dit

    /**
     * Ce que l'état au registre autorise.
     *
     * Un refus met à zéro : c'est le seul état qui fait taire, et il ne vaut que
     * pour ce passage-là. Une alerte déjà envoyée décote fortement sans annuler —
     * le passage reste affichable dans le volet, il ne mérite simplement plus de
     * sonner.
     */
    fun silence(status: PassageStatus?): Double = when (status) {
        null, PassageStatus.Detected -> 1.0
        is PassageStatus.Alerted -> 0.15
        is PassageStatus.Declined,
        is PassageStatus.Accepted,
        PassageStatus.Expired,
        -> 0.0
    }

    // ------------------------------------------ freshness — à quel point l'heure est crue

    /**
     * **Pas de paramètre nullable ici, donc pas de neutre.** On sait toujours si un
     * passage est mesuré ou théorique — le champ est non nullable côté modèle. Un
     * horaire théorique n'est pas une absence d'information, c'est une information
     * moins précise, et lui donner le neutre reviendrait à dire qu'on ne sait pas
     * ce qu'on sait.
     */
    fun freshness(isRealtime: Boolean, isFleetStale: Boolean): Double {
        if (!isRealtime) return 0.45
        return if (isFleetStale) 0.7 else 1.0
    }

    // ------------------------------------------------------------------ la table

    enum class Criterion {
        REACH,
        PROXIMITY,
        AFFINITY,
        DIRECTION,
        SILENCE,
        FRESHNESS,
    }

    /**
     * Les poids. **Une seule table**, et des chiffres à calibrer sur le terrain.
     *
     * `REACH` domine parce qu'un véhicule qu'on ne peut pas attraper n'a aucune
     * valeur, quelle que soit la ligne. `SILENCE` vient juste après : réveiller
     * quelqu'un pour un passage qu'il vient de refuser est le seul défaut qui
     * fasse désinstaller une veille.
     */
    val WEIGHTS: Map<Criterion, Double> = mapOf(
        Criterion.REACH to 0.32,
        Criterion.SILENCE to 0.24,
        Criterion.PROXIMITY to 0.16,
        Criterion.AFFINITY to 0.12,
        Criterion.DIRECTION to 0.10,
        Criterion.FRESHNESS to 0.06,
    )

    /**
     * En deçà, le passage est repéré mais ne mérite pas de sonner.
     *
     * **Le seul nombre de ce module qui se réglera à l'usage.** Il vit ici, pas
     * dans une vue : un seuil d'alerte enfoui dans un `if` de modèle serait
     * introuvable le jour où le terrain dira qu'il sonne trop.
     */
    const val ALERT_THRESHOLD = 0.62
}

/**
 * Un score, et le détail qui l'explique.
 *
 * Le détail n'est pas décoratif : c'est ce qui permet à un test d'affirmer
 * *pourquoi* un classement s'est renversé, plutôt que de constater qu'il l'a fait.
 */
data class GuetScore(
    val criteria: Map<GuetScoring.Criterion, Double>,
) {
    val total: Double
        get() = GuetScoring.WEIGHTS.entries.sumOf { (criterion, weight) ->
            (criteria[criterion] ?: GuetScoring.NEUTRAL) * weight
        }

    val isAlertWorthy: Boolean get() = total >= GuetScoring.ALERT_THRESHOLD
}
