package io.aule.android.feature.map

import android.content.Context
import io.aule.android.core.model.HandoverAlert
import io.aule.android.core.model.HandoverAlertKind

fun handoverAlertTitle(context: Context, alert: HandoverAlert): String =
    when (alert.kind) {
        HandoverAlertKind.STOPS_BEFORE -> {
            val remaining = alert.stopsRemaining ?: 0
            if (remaining <= 1) {
                context.getString(R.string.handover_alert_next_stop)
            } else {
                context.getString(R.string.handover_alert_stops_title, remaining)
            }
        }
        HandoverAlertKind.MINUTES_BEFORE -> {
            val minutes = alert.minutes ?: 0
            if (minutes <= 1) {
                context.getString(R.string.handover_alert_minute_title)
            } else {
                context.getString(R.string.handover_alert_minutes_title, minutes)
            }
        }
        HandoverAlertKind.APPROACHING ->
            context.getString(R.string.handover_alert_approaching_title)
        HandoverAlertKind.ARRIVED ->
            context.getString(R.string.handover_alert_arrived_title)
    }

fun handoverAlertBody(
    context: Context,
    alert: HandoverAlert,
    stopName: String,
): String = when (alert.kind) {
    HandoverAlertKind.STOPS_BEFORE -> {
        val remaining = alert.stopsRemaining ?: 0
        if (remaining <= 1) {
            context.getString(R.string.handover_alert_next_stop_body, stopName)
        } else {
            context.getString(R.string.handover_alert_stops_body, remaining, stopName)
        }
    }
    HandoverAlertKind.MINUTES_BEFORE ->
        context.getString(R.string.handover_alert_minutes_body, stopName)
    HandoverAlertKind.APPROACHING ->
        context.getString(R.string.handover_alert_approaching_body, stopName)
    HandoverAlertKind.ARRIVED ->
        context.getString(R.string.handover_alert_arrived_body)
}

fun handoverTakenTitle(context: Context): String =
    context.getString(R.string.handover_notif_taken_title)

fun handoverTakenBody(context: Context, lineLabel: String, reliefStopName: String?): String {
    val stop = reliefStopName?.trim().orEmpty()
    return if (stop.isEmpty()) {
        context.getString(R.string.handover_notif_taken_body, lineLabel)
    } else {
        context.getString(R.string.handover_notif_taken_body_stop, stop, lineLabel)
    }
}
