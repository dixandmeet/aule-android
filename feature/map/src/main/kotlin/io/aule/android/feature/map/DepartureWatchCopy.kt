package io.aule.android.feature.map

import android.content.Context
import io.aule.android.core.model.DepartureWatch
import io.aule.android.core.model.DepartureWatchAlert
import io.aule.android.core.model.DepartureWatchAlertKind

/**
 * Ce qu'une veille écrit dans la barre de notifications.
 *
 * Hors des `@Composable` — une bannière part aussi quand l'écran n'est plus
 * là — donc avec un [Context] en main, comme [handoverAlertTitle]. Les phrases
 * restent dans les ressources (ADR-011) ; ce fichier ne fait que choisir
 * laquelle.
 *
 * Le titre porte la **ligne**, jamais « Aule » : une bannière qui s'affiche
 * pendant qu'on marche est lue en un coup d'œil, et ce coup d'œil doit répondre
 * « lequel de mes bus ». Le corps porte la destination et l'arrêt, qui
 * désambiguïsent quand deux veilles se sont succédé.
 */
fun departureAlertTitle(
    context: Context,
    alert: DepartureWatchAlert,
    watch: DepartureWatch,
): String = when (alert.kind) {
    DepartureWatchAlertKind.MINUTES_BEFORE -> {
        val minutes = alert.minutes ?: 0
        if (minutes <= 1) {
            context.getString(R.string.watch_alert_minute_title, watch.line)
        } else {
            context.getString(R.string.watch_alert_minutes_title, watch.line, minutes)
        }
    }
    DepartureWatchAlertKind.APPROACHING ->
        context.getString(R.string.watch_alert_approaching_title, watch.line)
}

fun departureAlertBody(
    context: Context,
    alert: DepartureWatchAlert,
    watch: DepartureWatch,
): String = when (alert.kind) {
    DepartureWatchAlertKind.MINUTES_BEFORE ->
        context.getString(R.string.watch_alert_minutes_body, watch.destination, watch.stopName)
    DepartureWatchAlertKind.APPROACHING ->
        context.getString(R.string.watch_alert_approaching_body, watch.stopName, watch.destination)
}
