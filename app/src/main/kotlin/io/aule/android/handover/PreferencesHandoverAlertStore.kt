package io.aule.android.handover

import android.content.Context
import io.aule.android.core.model.HandoverAlertPrefs
import io.aule.android.core.model.repository.HandoverAlertPrefsStore

/**
 * Seuils d'alerte et lignes récentes, clés alignées sur Flutter
 * (`sae.handover.alerts`, `sae.handover.recent_lines`).
 */
class PreferencesHandoverAlertStore(
    context: Context,
) : HandoverAlertPrefsStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(): HandoverAlertPrefs =
        HandoverAlertPrefs.decode(prefs.getString(ALERTS_KEY, null))

    override fun write(prefs: HandoverAlertPrefs) {
        this.prefs.edit().putString(ALERTS_KEY, prefs.encode()).apply()
    }

    override fun readRecentLines(): List<String> =
        prefs.getString(RECENT_KEY, null)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    override fun pushRecentLine(lineId: String): List<String> {
        val id = lineId.trim()
        if (id.isEmpty()) return readRecentLines()
        val next = listOf(id) + readRecentLines().filter { it != id }
        val clipped = next.take(RECENT_MAX)
        prefs.edit().putString(RECENT_KEY, clipped.joinToString(SEPARATOR)).apply()
        return clipped
    }

    private companion object {
        const val PREFS = "io.aule.android.handover"
        const val ALERTS_KEY = "sae.handover.alerts"
        const val RECENT_KEY = "sae.handover.recent_lines"
        const val SEPARATOR = "\u001f"
        const val RECENT_MAX = 4
    }
}
