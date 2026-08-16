package io.aule.android.core.model

import kotlin.math.max
import kotlin.math.min

/**
 * Détecteur de sortie de parcours, avec hystérésis.
 *
 * Port de `SAE/lib/services/navigation_engine.dart`.
 *
 * Une unique mesure GPS imprécise ne déclenche jamais l'alarme. Sortir et
 * rentrer au même seuil ferait clignoter le bandeau — et un bandeau qui
 * clignote, on cesse de le lire.
 */
class OffRouteDetector(
    private val requiredFixes: Int = 3,
    val baseThresholdMeters: Double = 32.0,
    rejoinThresholdMeters: Double? = null,
) {
    val rejoinThresholdMeters: Double = rejoinThresholdMeters ?: baseThresholdMeters * 0.7

    var outsideFixes: Int = 0
        private set

    val warning: Boolean get() = outsideFixes > 0

    fun rejoined(deviationMeters: Double): Boolean =
        deviationMeters < rejoinThresholdMeters

    fun update(deviationMeters: Double, accuracy: Double): Boolean {
        val threshold = max(baseThresholdMeters, min(70.0, accuracy * 1.25))
        if (deviationMeters > threshold) {
            outsideFixes++
        } else if (deviationMeters < threshold * 0.65) {
            outsideFixes = 0
        }
        if (outsideFixes < requiredFixes) return false
        outsideFixes = 0
        return true
    }

    fun reset() {
        outsideFixes = 0
    }
}
