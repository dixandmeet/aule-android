package io.aule.android.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Seuils d'alerte, choisis par le conducteur. `null` = alerte désactivée.
 *
 * Ils survivent d'une relève à l'autre : c'est le temps qu'il faut pour
 * rejoindre le quai, pas un réglage de la course du jour. Les phrases
 * d'écran restent dans `strings.xml` (ADR-011).
 */
data class HandoverAlertPrefs(
    val stopsBefore: Int? = 2,
    val minutesBefore: Int? = 5,
    val onArrival: Boolean = true,
    val vibration: Boolean = true,
    val sound: Boolean = true,
) {
    fun withStopsBefore(value: Int?): HandoverAlertPrefs = copy(stopsBefore = value)

    fun withMinutesBefore(value: Int?): HandoverAlertPrefs = copy(minutesBefore = value)

    fun encode(): String = buildJsonObject {
        if (stopsBefore == null) put("stops_before", JsonNull) else put("stops_before", stopsBefore)
        if (minutesBefore == null) put("minutes_before", JsonNull) else put("minutes_before", minutesBefore)
        put("on_arrival", onArrival)
        put("vibration", vibration)
        put("sound", sound)
    }.toString()

    companion object {
        val DEFAULTS = HandoverAlertPrefs()

        const val STOPS_MIN = 1
        const val STOPS_MAX = 5
        const val MINUTES_MIN = 1
        const val MINUTES_MAX = 20

        fun decode(raw: String?): HandoverAlertPrefs {
            if (raw.isNullOrBlank()) return DEFAULTS
            val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: return DEFAULTS
            return HandoverAlertPrefs(
                stopsBefore = obj.optionalInt("stops_before", DEFAULTS.stopsBefore),
                minutesBefore = obj.optionalInt("minutes_before", DEFAULTS.minutesBefore),
                onArrival = obj.optionalBoolean("on_arrival", true),
                vibration = obj.optionalBoolean("vibration", true),
                sound = obj.optionalBoolean("sound", true),
            )
        }

        private fun JsonObject.optionalInt(key: String, fallback: Int?): Int? {
            if (key !in this) return fallback
            val value = this[key] ?: return fallback
            if (value is JsonNull) return null
            return value.jsonPrimitive.intOrNull
        }

        private fun JsonObject.optionalBoolean(key: String, fallback: Boolean): Boolean {
            if (key !in this) return fallback
            val value = this[key] ?: return fallback
            if (value is JsonNull) return fallback
            return value.jsonPrimitive.booleanOrNull ?: fallback
        }
    }
}

enum class HandoverAlertKind {
    STOPS_BEFORE,
    MINUTES_BEFORE,
    APPROACHING,
    ARRIVED,
}

/**
 * Une alerte à émettre : le genre et les chiffres, pas la phrase.
 */
data class HandoverAlert(
    val kind: HandoverAlertKind,
    val stopsRemaining: Int? = null,
    val minutes: Int? = null,
)

/**
 * Déclenche les alertes d'arrivée du véhicule à relever.
 *
 * Deux règles gouvernent tout le reste :
 *
 *  - un seuil ne se déclenche qu'une fois (loquet) : un véhicule qui
 *    oscille autour de cinq minutes n'envoie pas une dizaine de
 *    notifications ;
 *  - rien ne part sur une position périmée. Annoncer « il arrive » à
 *    partir d'un point vieux de plusieurs minutes ferait rater la
 *    relève, ce qui est pire que se taire.
 *
 * S'y ajoute une hystérésis : deux mesures consécutives sous le seuil,
 * comme pour la sortie d'itinéraire.
 */
class HandoverAlertEngine(
    prefs: HandoverAlertPrefs,
    private val confirmations: Int = 2,
) {
    init {
        require(confirmations >= 1)
    }

    var prefs: HandoverAlertPrefs = prefs
        set(value) {
            if (value.stopsBefore != field.stopsBefore) {
                fired.remove(HandoverAlertKind.STOPS_BEFORE)
                streaks.remove(HandoverAlertKind.STOPS_BEFORE)
            }
            if (value.minutesBefore != field.minutesBefore) {
                fired.remove(HandoverAlertKind.MINUTES_BEFORE)
                streaks.remove(HandoverAlertKind.MINUTES_BEFORE)
            }
            field = value
        }

    private val fired = mutableSetOf<HandoverAlertKind>()
    private val streaks = mutableMapOf<HandoverAlertKind, Int>()

    fun reset() {
        fired.clear()
        streaks.clear()
    }

    fun hasFired(kind: HandoverAlertKind): Boolean = kind in fired

    /**
     * Les alertes à émettre pour cette mesure — au plus une par seuil, et
     * une seule fois dans la vie de la relève.
     */
    fun evaluate(progress: HandoverProgress, now: java.time.Instant): List<HandoverAlert> {
        if (!progress.fresh) return emptyList()
        val alerts = mutableListOf<HandoverAlert>()

        val stopsBefore = prefs.stopsBefore
        val stopsRemaining = progress.stopsRemaining
        if (stopsBefore != null &&
            progress.pathMatched &&
            stopsRemaining != null &&
            !progress.passed &&
            stopsRemaining <= stopsBefore
        ) {
            arm(
                HandoverAlertKind.STOPS_BEFORE,
                HandoverAlert(HandoverAlertKind.STOPS_BEFORE, stopsRemaining = stopsRemaining),
            )?.let(alerts::add)
        } else {
            streaks.remove(HandoverAlertKind.STOPS_BEFORE)
        }

        val minutesBefore = prefs.minutesBefore
        val minutes = progress.minutesUntil(now)
        if (minutesBefore != null &&
            progress.pathMatched &&
            minutes != null &&
            !progress.passed &&
            minutes <= minutesBefore
        ) {
            arm(
                HandoverAlertKind.MINUTES_BEFORE,
                HandoverAlert(HandoverAlertKind.MINUTES_BEFORE, minutes = minutes),
            )?.let(alerts::add)
        } else {
            streaks.remove(HandoverAlertKind.MINUTES_BEFORE)
        }

        if (progress.approaching && !progress.arrived) {
            arm(
                HandoverAlertKind.APPROACHING,
                HandoverAlert(HandoverAlertKind.APPROACHING),
            )?.let(alerts::add)
        } else {
            streaks.remove(HandoverAlertKind.APPROACHING)
        }

        if (prefs.onArrival && progress.arrived) {
            arm(
                kind = HandoverAlertKind.ARRIVED,
                alert = HandoverAlert(HandoverAlertKind.ARRIVED),
                immediate = true,
            )?.let(alerts::add)
        }

        return alerts
    }

    private fun arm(
        kind: HandoverAlertKind,
        alert: HandoverAlert,
        immediate: Boolean = false,
    ): HandoverAlert? {
        if (kind in fired) return null
        val streak = (streaks[kind] ?: 0) + 1
        streaks[kind] = streak
        if (!immediate && streak < confirmations) return null
        fired.add(kind)
        return alert
    }
}
