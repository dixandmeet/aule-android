package io.aule.android.core.location

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Le bip de l'alerte quand l'application est **au premier plan**.
 *
 * Port de `SAE/android/.../MainActivity.java` et de
 * `SAE/lib/services/alert_tone.dart`. En natif ce n'est plus un
 * MethodChannel : une classe, appelée depuis le domaine qui sait qu'une
 * alerte vient de s'afficher à l'écran.
 *
 * En arrière-plan, le son appartient au canal de notification — Android
 * y applique tout seul le mode sonnerie. Ici, aucune bannière ne part,
 * donc rien ne sonnerait sans ces lignes.
 *
 * **Ne lève jamais.** Le bip est un confort : la carte (ou la carte
 * flottante) est déjà à l'écran, et une exception pour un son manquant
 * ferait tomber l'écran pour rien.
 */
class AlertTone(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun alert(sound: Boolean = true, vibration: Boolean = true): AlertToneOutcome {
        if (!sound && !vibration) return AlertToneOutcome.SILENT
        return try {
            val notifications = appContext.getSystemService(NotificationManager::class.java)
            val filter = notifications?.currentInterruptionFilter
                ?: AlertTonePolicy.INTERRUPTION_FILTER_ALL
            val audio = appContext.getSystemService(AudioManager::class.java)
                ?: return AlertToneOutcome.SILENT
            val outcome = AlertTonePolicy.decide(filter, audio.ringerMode)
            when (outcome) {
                AlertToneOutcome.SOUND -> {
                    if (sound) beep()
                    if (vibration) vibrate()
                }
                AlertToneOutcome.VIBRATION -> if (vibration) vibrate()
                AlertToneOutcome.SILENT -> Unit
            }
            outcome
        } catch (_: Throwable) {
            AlertToneOutcome.SILENT
        }
    }

    /**
     * Un bip court sur le flux des notifications.
     *
     * `STREAM_NOTIFICATION` et non `STREAM_MUSIC` : le second hurlerait dans
     * un casque, au volume de la musique.
     *
     * Le `release()` est différé : libérer avant la fin du bip le coupe, et
     * ne jamais libérer épuise les pistes du système.
     */
    private fun beep() {
        val generator = try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        } catch (_: RuntimeException) {
            return
        }
        generator.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
        Handler(Looper.getMainLooper()).postDelayed({ generator.release() }, BEEP_MS + 150L)
    }

    private fun vibrate() {
        val vibrator = vibrator() ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(PATTERN, -1)
        }
    }

    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        }
    }

    private companion object {
        const val BEEP_MS = 250

        /** Deux impulsions courtes : reconnaissable sans être une sonnerie d'appel. */
        val PATTERN = longArrayOf(0, 250, 150, 250)
    }
}
