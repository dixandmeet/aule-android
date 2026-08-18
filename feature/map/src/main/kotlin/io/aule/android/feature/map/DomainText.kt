package io.aule.android.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aule.android.core.geo.GeoMath
import java.text.DecimalFormatSymbols
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.FleetStatus
import io.aule.android.core.model.ManeuverKind
import io.aule.android.core.model.NextAction
import io.aule.android.core.model.NextActionKind
import io.aule.android.core.model.SummaryMetric
import io.aule.android.core.model.SummaryMetricKind
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.VehicleLoad
import io.aule.android.core.model.Wait

/**
 * Tout ce que l'application dit à l'usager sur son domaine.
 *
 * Un modèle expose *ce qui est*, une vue dit *comment on le formule*. Les
 * phrases vivent dans les ressources : un modèle qui porte du français est
 * un modèle qu'il faut rouvrir pour traduire, et rouvrir un modèle pour un
 * mot est exactement ce qui finit par en changer le sens.
 */

@Composable
fun FleetStatus.label(): String = when (this) {
    FleetStatus.Stale -> stringResource(R.string.fleet_stale)
    FleetStatus.Empty -> stringResource(R.string.fleet_empty)
    is FleetStatus.LiveOnly -> stringResource(R.string.fleet_live, count)
    is FleetStatus.ScheduledOnly -> stringResource(R.string.fleet_scheduled, count)
    is FleetStatus.Mixed -> stringResource(R.string.fleet_mixed, live, scheduled)
}

@Composable
fun Wait.label(): String = when (this) {
    Wait.Approaching -> stringResource(R.string.wait_approaching)
    is Wait.Minutes -> stringResource(R.string.wait_minutes, minutes)
}

@Composable
fun DeparturesOutcome.title(): String = when (this) {
    DeparturesOutcome.ANNOUNCED -> ""
    DeparturesOutcome.NOTHING_ANNOUNCED -> stringResource(R.string.departures_nothing_title)
    DeparturesOutcome.PROVIDER_SILENT -> stringResource(R.string.departures_silent_title)
}

@Composable
fun DeparturesOutcome.detail(): String? = when (this) {
    DeparturesOutcome.ANNOUNCED -> null
    DeparturesOutcome.NOTHING_ANNOUNCED -> stringResource(R.string.departures_nothing_detail)
    DeparturesOutcome.PROVIDER_SILENT -> stringResource(R.string.departures_silent_detail)
}

@Composable
fun TransportMode.label(): String = when (this) {
    TransportMode.BUS -> stringResource(R.string.mode_bus)
    TransportMode.TRAM -> stringResource(R.string.mode_tram)
    TransportMode.BOAT -> stringResource(R.string.mode_boat)
}

@Composable
fun VehicleLoad.label(): String = when (this) {
    VehicleLoad.QUIET -> stringResource(R.string.vehicle_load_quiet)
    VehicleLoad.STEADY -> stringResource(R.string.vehicle_load_steady)
    VehicleLoad.BUSY -> stringResource(R.string.vehicle_load_busy)
    VehicleLoad.FULL -> stringResource(R.string.vehicle_load_full)
}

/**
 * L'âge d'une position, dit comme on le dirait à voix haute.
 *
 * La seconde près n'a d'intérêt que la première minute : passé ce cap, ce
 * qu'on veut savoir n'est plus « à combien de secondes » mais « est-ce que ça
 * fait longtemps ».
 */
@Composable
fun positionAgeText(seconds: Long): String = when {
    seconds < FRESH_POSITION_SECONDS -> stringResource(R.string.vehicle_updated_now)
    seconds < 60L -> stringResource(R.string.vehicle_updated_seconds, seconds.toInt())
    else -> stringResource(R.string.vehicle_updated_minutes, (seconds / 60L).toInt())
}

/** En deçà, la position vient d'arriver : on ne compte pas les secondes. */
private const val FRESH_POSITION_SECONDS = 15L

@Composable
fun ManeuverKind.phrase(): String = when (this) {
    ManeuverKind.DEPART -> stringResource(R.string.maneuver_depart)
    ManeuverKind.STRAIGHT -> stringResource(R.string.maneuver_straight)
    ManeuverKind.SLIGHT_LEFT -> stringResource(R.string.maneuver_slight_left)
    ManeuverKind.LEFT -> stringResource(R.string.maneuver_left)
    ManeuverKind.SHARP_LEFT -> stringResource(R.string.maneuver_sharp_left)
    ManeuverKind.SLIGHT_RIGHT -> stringResource(R.string.maneuver_slight_right)
    ManeuverKind.RIGHT -> stringResource(R.string.maneuver_right)
    ManeuverKind.SHARP_RIGHT -> stringResource(R.string.maneuver_sharp_right)
    ManeuverKind.U_TURN -> stringResource(R.string.maneuver_uturn)
    ManeuverKind.ROUNDABOUT -> stringResource(R.string.maneuver_roundabout)
    ManeuverKind.FORK -> stringResource(R.string.maneuver_fork)
    ManeuverKind.MERGE -> stringResource(R.string.maneuver_merge)
    ManeuverKind.RAMP -> stringResource(R.string.maneuver_ramp)
    ManeuverKind.ARRIVE -> stringResource(R.string.maneuver_arrive)
    ManeuverKind.UNKNOWN -> stringResource(R.string.maneuver_unknown)
}

@Composable
fun NextAction.leadText(): String? {
    if (leadIsTransfer) return stringResource(R.string.nav_transfer)
    val stops = leadStops
    if (stops != null) {
        return if (stops <= 1) {
            stringResource(R.string.nav_stops_one)
        } else {
            stringResource(R.string.nav_stops_many, stops)
        }
    }
    val meters = leadMeters ?: return null
    return formatDistance(meters)
}

@Composable
fun NextAction.titleText(): String = when (kind) {
    NextActionKind.ARRIVE -> {
        val destination = destinationLabel
        if (destination.isNullOrBlank()) {
            stringResource(R.string.nav_arrive)
        } else {
            stringResource(R.string.nav_arrive_at, destination)
        }
    }
    NextActionKind.TRANSFER -> if (title.isBlank()) {
        stringResource(R.string.nav_transfer_generic)
    } else {
        stringResource(R.string.nav_transfer_join, title)
    }
    NextActionKind.ALIGHT -> if (title.isBlank()) {
        stringResource(R.string.nav_alight)
    } else {
        stringResource(R.string.nav_alight_at, title)
    }
    NextActionKind.BOARD -> if (title.isBlank()) {
        stringResource(R.string.nav_board)
    } else {
        stringResource(R.string.nav_board_line, title)
    }
    NextActionKind.MANEUVER -> title.ifBlank { maneuver?.phrase().orEmpty() }
    NextActionKind.FOLLOW -> title
}

@Composable
fun NextAction.detailText(): String? = when (kind) {
    NextActionKind.MANEUVER -> if (title.isNotBlank()) maneuver?.phrase() else null
    NextActionKind.BOARD -> detail
    NextActionKind.TRANSFER -> detail
    else -> null
}

@Composable
fun SummaryMetric.valueText(): String = when (kind) {
    SummaryMetricKind.DISTANCE -> formatDistance(meters ?: 0.0)
    SummaryMetricKind.STOPS -> if ((stops ?: 0) <= 1) {
        stringResource(R.string.nav_stops_one)
    } else {
        stringResource(R.string.nav_stops_many, stops ?: 0)
    }
    SummaryMetricKind.LEGS -> stringResource(R.string.nav_legs_many, legs ?: 0)
    SummaryMetricKind.TRANSFER_IN -> stringResource(R.string.nav_transfer_in, transferMinutes ?: 0)
}

@Composable
fun SummaryMetric.labelText(): String = when (kind) {
    SummaryMetricKind.DISTANCE -> stringResource(R.string.nav_metric_distance)
    SummaryMetricKind.STOPS -> stringResource(R.string.nav_metric_stops)
    SummaryMetricKind.LEGS -> stringResource(R.string.nav_metric_legs)
    SummaryMetricKind.TRANSFER_IN -> stringResource(R.string.nav_metric_transfer)
}

@Composable
private fun formatDistance(meters: Double): String =
    GeoMath.formatDistance(meters, DecimalFormatSymbols.getInstance().decimalSeparator)
