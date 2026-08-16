package io.aule.android.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aule.android.core.model.HandoverSummary
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Les phrases du heartbeat. Elles vivent ici, une seule fois, collées
 * aux `strings.xml` (ADR-011) : le modèle ne porte aucun mot d'écran.
 *
 * On ne parle jamais de « fin de service » pour une relève aboutie : le
 * conducteur a remis son véhicule, ce n'est pas la même chose.
 */
@Composable
internal fun handoverAnnouncementMessage(handover: HandoverSummary): String {
    val who = handover.incomingDisplay?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.heartbeat_colleague)
    val where = handover.reliefStopName
    val whenClock = handover.reliefPlannedAt?.let { HEARTBEAT_CLOCK.format(it) }
    return when {
        where != null && whenClock != null ->
            stringResource(R.string.heartbeat_announce_where_when, who, where, whenClock)
        where != null ->
            stringResource(R.string.heartbeat_announce_where, who, where)
        whenClock != null ->
            stringResource(R.string.heartbeat_announce_when, who, whenClock)
        else ->
            stringResource(R.string.heartbeat_announce_plain, who)
    }
}

@Composable
internal fun handoverCompletedMessage(handover: HandoverSummary): String {
    val who = handover.incomingDisplay?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.heartbeat_colleague_mid)
    val where = handover.reliefStopName
    return if (where == null) {
        stringResource(R.string.heartbeat_completed_plain, who)
    } else {
        stringResource(R.string.heartbeat_completed_where, who, where)
    }
}

private val HEARTBEAT_CLOCK: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
