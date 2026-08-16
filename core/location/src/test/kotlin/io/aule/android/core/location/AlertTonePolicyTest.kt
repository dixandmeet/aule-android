package io.aule.android.core.location

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/** Port des règles de `SAE/android/.../MainActivity.java`. */
class AlertTonePolicyTest {

    @Test
    fun `ne pas deranger passe avant le mode sonnerie`() {
        assertEquals(
            AlertToneOutcome.SILENT,
            AlertTonePolicy.decide(
                interruptionFilter = AlertTonePolicy.INTERRUPTION_FILTER_ALL + 1,
                ringerMode = AlertTonePolicy.RINGER_MODE_NORMAL,
            ),
        )
    }

    @Test
    fun `mode sonnerie bip et vibre`() {
        assertEquals(
            AlertToneOutcome.SOUND,
            AlertTonePolicy.decide(
                interruptionFilter = AlertTonePolicy.INTERRUPTION_FILTER_ALL,
                ringerMode = AlertTonePolicy.RINGER_MODE_NORMAL,
            ),
        )
    }

    @Test
    fun `mode vibreur vibre seulement`() {
        assertEquals(
            AlertToneOutcome.VIBRATION,
            AlertTonePolicy.decide(
                interruptionFilter = AlertTonePolicy.INTERRUPTION_FILTER_ALL,
                ringerMode = AlertTonePolicy.RINGER_MODE_VIBRATE,
            ),
        )
    }

    @Test
    fun `mode silencieux ne fait rien`() {
        assertEquals(
            AlertToneOutcome.SILENT,
            AlertTonePolicy.decide(
                interruptionFilter = AlertTonePolicy.INTERRUPTION_FILTER_ALL,
                ringerMode = AlertTonePolicy.RINGER_MODE_SILENT,
            ),
        )
    }
}
