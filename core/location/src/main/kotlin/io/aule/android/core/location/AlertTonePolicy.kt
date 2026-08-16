package io.aule.android.core.location

/**
 * Ce que le téléphone a effectivement fait pour une alerte au premier plan.
 *
 * Port de `SAE/lib/services/alert_tone.dart`. L'issue sert la trace et les
 * tests : personne n'en dépend pour décider quoi que ce soit — une alerte
 * qui changerait de comportement selon qu'elle a sonné ou non aurait deux
 * visages.
 */
enum class AlertToneOutcome {
    /** Mode sonnerie : bip et vibration. */
    SOUND,

    /** Mode vibreur : vibration seule. */
    VIBRATION,

    /** Mode silencieux, ou « Ne pas déranger » : rien. */
    SILENT,
}

/**
 * La décision, pure : DND d'abord, puis le mode sonnerie.
 *
 * ⚠️ « Ne pas déranger » se lit **avant** le mode sonnerie. Sous DND,
 * `AudioManager.getRingerMode()` rend souvent `RINGER_MODE_NORMAL` : sans
 * ce test, l'application biperait en pleine réunion.
 *
 * Les constantes reprennent celles d'`NotificationManager` et
 * d'`AudioManager` pour que la règle reste vérifiable en JVM, hors
 * framework Android.
 */
object AlertTonePolicy {

    /** `NotificationManager.INTERRUPTION_FILTER_ALL`. */
    const val INTERRUPTION_FILTER_ALL = 1

    /** `AudioManager.RINGER_MODE_SILENT`. */
    const val RINGER_MODE_SILENT = 0

    /** `AudioManager.RINGER_MODE_VIBRATE`. */
    const val RINGER_MODE_VIBRATE = 1

    /** `AudioManager.RINGER_MODE_NORMAL`. */
    const val RINGER_MODE_NORMAL = 2

    fun decide(interruptionFilter: Int, ringerMode: Int): AlertToneOutcome {
        if (interruptionFilter > INTERRUPTION_FILTER_ALL) return AlertToneOutcome.SILENT
        return when (ringerMode) {
            RINGER_MODE_NORMAL -> AlertToneOutcome.SOUND
            RINGER_MODE_VIBRATE -> AlertToneOutcome.VIBRATION
            else -> AlertToneOutcome.SILENT
        }
    }
}
