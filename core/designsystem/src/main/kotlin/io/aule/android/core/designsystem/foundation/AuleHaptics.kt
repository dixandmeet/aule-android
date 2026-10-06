package io.aule.android.core.designsystem.foundation

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Le langage haptique d'Aule : quatre mots, pas une vibration par toucher.
 *
 * - [SELECTION] : un choix parmi d'autres — une puce, un segment, un mode. Léger.
 * - [ACTION] : un geste qui engage — partir, armer une veille, enregistrer. Léger à moyen.
 * - [SUCCESS] : quelque chose vient d'aboutir — un favori posé. Le retour « confirmé » du système.
 * - [ERROR] : quelque chose vient d'être refusé — un formulaire incomplet. Le retour « rejeté ».
 *
 * Un tap ordinaire — ouvrir une fiche, faire défiler — ne vibre pas : c'est l'écran qui répond.
 */
enum class AuleHaptic { SELECTION, ACTION, SUCCESS, ERROR }

class AuleHaptics internal constructor(private val view: View) {
    fun play(kind: AuleHaptic) {
        val constant = when (kind) {
            AuleHaptic.SELECTION -> HapticFeedbackConstants.CLOCK_TICK
            AuleHaptic.ACTION -> HapticFeedbackConstants.CONTEXT_CLICK
            // `CONFIRM` et `REJECT` n'existent qu'à partir d'Android 11 ; dessous, on garde une
            // réponse plutôt qu'aucune, mais une réponse différente pour les deux issues.
            AuleHaptic.SUCCESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
            AuleHaptic.ERROR -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        }
        view.performHapticFeedback(constant)
    }
}

/** L'haptique de l'écran courant. Elle suit le réglage système « retour tactile » de l'appareil. */
@Composable
fun rememberAuleHaptics(): AuleHaptics {
    val view = LocalView.current
    return remember(view) { AuleHaptics(view) }
}
