package io.aule.android.core.guet

import io.aule.android.core.model.normalizeStopName
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.pow

/**
 * Ce que l'utilisateur prend, et quand.
 *
 * ## Des compteurs, pas une trace
 *
 * La tentation serait de garder les trajets. On garde des **poids amortis** :
 * `(ligne, lieu, bande de deux heures, ouvré ou week-end)` → un nombre qui
 * décroît. **Aucune coordonnée, aucun horodatage de trajet**, quarante lignes au
 * plus. C'est ce qui permet à la déclaration de confidentialité de rester
 * honnête, et c'est aussi ce qui rend le fichier lisible par celui qu'il décrit.
 *
 * **Alimenté uniquement sur une acceptation.** Un signal rare et sûr — « j'ai dit
 * oui à ce véhicule » — vaut mieux qu'une piste GPS continue qu'il faudrait
 * interpréter.
 *
 * ## La décote est exponentielle
 *
 * Une habitude se compte en semaines : une demi-vie de trente jours laisse un
 * trajet quotidien peser, et laisse disparaître celui qu'on a pris deux fois en
 * mars. Le principe est **temporel, jamais binaire**.
 *
 * Port de `Native/Aule/Core/Guet/GuetHabits.swift`.
 */
data class GuetHabits(
    private val weights: Map<Slot, Weight> = emptyMap(),
    /**
     * Le rapport amorti entre marche réalisée et marche prédite.
     *
     * C'est « l'estimation automatique de la vitesse de marche », en **un seul
     * nombre** et sans historique de déplacements. `null` tant qu'aucune marche
     * n'a été mesurée — et `null` veut dire « on ne sait pas », jamais « vitesse
     * normale » : c'est le réglage d'allure qui décide du repli, et il le dit.
     */
    val paceFactor: Double? = null,
) {
    /**
     * L'unité de comptage. **Volontairement grossière** : une ligne, un lieu, un
     * moment de la journée. Plus fin ne dirait rien de plus et se mettrait à
     * décrire des déplacements.
     */
    data class Slot(
        val line: String,
        val place: String,
        /** La bande de deux heures, de 0 à 11. */
        val band: Int,
        val isWeekend: Boolean,
    ) {
        companion object {
            /**
             * @param zone injecté, jamais le fuseau de la machine en dur. Une
             *   habitude calculée sur le fuseau d'une machine de test n'aurait pas
             *   les mêmes bandes que sur celle de l'utilisateur, et le défaut ne
             *   se verrait qu'en voyage.
             */
            fun make(line: String, place: String, at: Instant, zone: ZoneId): Slot {
                val local = at.atZone(zone)
                val day = local.dayOfWeek
                return Slot(
                    line = normalizeStopName(line),
                    place = normalizeStopName(place),
                    band = (local.hour / 2).coerceIn(0, 11),
                    isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY,
                )
            }
        }
    }

    data class Weight(val value: Double, val at: Instant)

    // ------------------------------------------------------------------- lire

    /**
     * À quel point cette ligne, à ce lieu et à cette heure, ressemble à une
     * habitude.
     *
     * @return entre 0 et 1, **ou `null` quand il n'y a aucun historique**. `null`
     *   n'est pas zéro : un critère qu'on ne peut pas évaluer doit peser neutre,
     *   jamais pénaliser en silence quelqu'un dont la veille vient d'être allumée.
     */
    fun affinity(line: String, place: String, at: Instant, zone: ZoneId): Double? {
        if (weights.isEmpty()) return null
        val decayed = weights.mapValues { decay(it.value, at) }
        val peak = decayed.values.maxOrNull() ?: return null
        if (peak <= 0) return null
        val slot = Slot.make(line, place, at, zone)
        return ((decayed[slot] ?: 0.0) / peak).coerceIn(0.0, 1.0)
    }

    /** Combien d'habitudes sont retenues. Sert aux tests et au bornage, pas à un écran. */
    val count: Int get() = weights.size

    // ----------------------------------------------------------------- écrire

    /** L'utilisateur a accepté ce véhicule. **Le seul appelant légitime.** */
    fun record(line: String, place: String, at: Instant, zone: ZoneId): GuetHabits {
        val slot = Slot.make(line, place, at, zone)
        val current = weights[slot]?.let { decay(it, at) } ?: 0.0
        val next = weights + (slot to Weight(current + 1, at))
        return copy(weights = next).enforceCapacity(at)
    }

    /** Une marche vient d'être faite. Mesurée à l'arrivée au quai, jamais en chemin. */
    fun recordWalk(predictedSeconds: Int, actualSeconds: Int): GuetHabits {
        if (predictedSeconds <= 0 || actualSeconds <= 0) return this
        val ratio = actualSeconds.toDouble() / predictedSeconds.toDouble()
        // Un rapport hors de ces bornes vient d'un détour ou d'un arrêt en chemin,
        // pas d'une vitesse de marche. On le refuse plutôt que de laisser une
        // mesure aberrante s'installer dans une moyenne qu'aucun écran ne montre.
        if (ratio !in PACE_MIN..PACE_MAX) return this
        val current = paceFactor ?: return copy(paceFactor = ratio)
        return copy(paceFactor = current + (ratio - current) * PACE_SMOOTHING)
    }

    /** Oublie ce qui ne pèse plus assez pour changer un classement. */
    fun prune(at: Instant, below: Double = 0.05): GuetHabits =
        copy(weights = weights.filterValues { decay(it, at) >= below })

    // ----------------------------------------------------------------- interne

    private fun decay(weight: Weight, to: Instant): Double {
        val elapsed = Duration.between(weight.at, to).toMillis() / 1_000.0
        if (elapsed <= 0) return weight.value
        return weight.value * 2.0.pow(-elapsed / HALF_LIFE)
    }

    private fun enforceCapacity(at: Instant): GuetHabits {
        if (weights.size <= CAPACITY) return this
        val survivors = weights.entries
            .sortedByDescending { decay(it.value, at) }
            .take(CAPACITY)
            .associate { it.key to it.value }
        return copy(weights = survivors)
    }

    companion object {
        /**
         * Trente jours. Assez pour qu'un trajet quotidien pèse, assez peu pour
         * qu'un trajet d'avril ne décide plus de rien en juin.
         */
        const val HALF_LIFE = 30.0 * 24 * 3600

        /** Quarante entrées. Au-delà, on n'apprend plus rien et on décrit des habitudes. */
        const val CAPACITY = 40

        const val PACE_MIN = 0.5
        const val PACE_MAX = 2.0

        /**
         * La part que chaque mesure neuve prend dans [paceFactor]. Un tiers : trois
         * marches suffisent à corriger, une seule ne suffit pas à emporter.
         */
        const val PACE_SMOOTHING = 1.0 / 3
    }
}
