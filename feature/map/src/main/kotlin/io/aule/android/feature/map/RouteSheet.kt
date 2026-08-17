package io.aule.android.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.RouteCandidate
import io.aule.android.core.model.RouteMode
import io.aule.android.core.model.RouteProfile
import io.aule.android.core.model.RouteReliability
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Le panneau d'itinéraire.
 *
 * Il ne calcule rien : il lit l'état que le ViewModel tient et remonte des
 * intentions — choisir une variante, changer de mode, fermer. Un seul
 * écrivain, et ce n'est pas lui.
 *
 * « Démarrer » n'apparaît que sur un trajet retenu : un bouton offert
 * sans guidage derrière lui mentirait.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteSheet(
    state: RouteUiState,
    onSelect: (String) -> Unit,
    onMode: (RouteMode) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.lg),
    ) {
        Text(
            text = stringResource(R.string.route_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )

        Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
            RouteEndpoint(label = stringResource(R.string.route_from), value = state.origin.label)
            RouteEndpoint(label = stringResource(R.string.route_to), value = state.destination.label)
        }

        val modes = listOf(RouteMode.TRANSIT, RouteMode.CAR)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { onMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    label = {
                        Text(
                            stringResource(
                                if (mode == RouteMode.TRANSIT) {
                                    R.string.route_mode_transit
                                } else {
                                    R.string.route_mode_car
                                },
                            ),
                        )
                    },
                )
            }
        }

        when (state.status) {
            RouteLoadStatus.LOADING -> AuleLoadingState(
                label = stringResource(R.string.route_loading),
            )
            RouteLoadStatus.ERROR -> AuleEmptyState(
                title = stringResource(R.string.route_error_title),
                detail = state.error ?: stringResource(R.string.route_error_detail),
            )
            RouteLoadStatus.READY -> {
                val plan = state.plan
                if (plan == null || plan.alternatives.isEmpty()) {
                    AuleEmptyState(
                        title = stringResource(R.string.route_empty_title),
                        detail = stringResource(R.string.route_empty_detail),
                    )
                } else {
                    Column {
                        plan.alternatives.forEach { candidate ->
                            RouteCandidateRow(
                                candidate = candidate,
                                selected = candidate.id == state.selectedId,
                                onClick = { onSelect(candidate.id) },
                            )
                        }
                    }
                    if (state.selected != null) {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth(),
                            colors = auleAccentButtonColors(),
                        ) {
                            Text(stringResource(R.string.route_start))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteEndpoint(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RouteCandidateRow(
    candidate: RouteCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val duration = stringResource(R.string.route_duration, candidate.durationMinutes)
    val distance = GeoMath.formatDistance(
        candidate.distanceMeters.toDouble(),
        DecimalFormatSymbols.getInstance().decimalSeparator,
    )
    val profile = candidate.profiles.firstOrNull()?.label()
    val reliability = candidate.reliability?.label()
    val departure = candidate.departureAt?.toClock()
    val description = buildString {
        append(duration)
        append(", ")
        append(distance)
        if (profile != null) {
            append(", ")
            append(profile)
        }
        candidate.steps.forEach { step ->
            append(", ")
            append(step.label)
        }
    }
    val supporting = buildString {
        if (departure != null) append(stringResource(R.string.route_departs, departure))
        if (profile != null) {
            if (isNotEmpty()) append(" · ")
            append(profile)
        }
        if (reliability != null) {
            if (isNotEmpty()) append(" · ")
            append(reliability)
        }
    }
    ListItem(
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = duration, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = distance,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
                if (supporting.isNotEmpty()) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                val lines = candidate.lineIds()
                if (lines.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        lines.distinct().forEach { line ->
                            val color = candidate.segments.firstOrNull { it.routeId == line }?.color
                            LineBadge(
                                line = line,
                                colorHex = color,
                                contentDescription = stringResource(R.string.line_badge, line),
                            )
                        }
                    }
                } else {
                    candidate.steps.firstOrNull()?.let { step ->
                        Text(text = step.label, maxLines = 2)
                    }
                }
            }
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = description
            },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) colors.primaryContainer else colors.surface,
        ),
    )
}

@Composable
internal fun RouteProfile.label(): String = when (this) {
    RouteProfile.FASTEST -> stringResource(R.string.route_profile_fastest)
    RouteProfile.LEAST_WALK -> stringResource(R.string.route_profile_least_walk)
    RouteProfile.LEAST_TRANSFERS -> stringResource(R.string.route_profile_least_transfers)
    RouteProfile.MOST_RELIABLE -> stringResource(R.string.route_profile_most_reliable)
}

@Composable
internal fun RouteReliability.label(): String = when (this) {
    RouteReliability.COMFORTABLE -> stringResource(R.string.route_reliability_comfortable)
    RouteReliability.TIGHT -> stringResource(R.string.route_reliability_tight)
    RouteReliability.RISKY -> stringResource(R.string.route_reliability_risky)
}

private fun Instant.toClock(): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(this)

private fun RouteCandidate.lineIds(): List<String> =
    segments.mapNotNull { it.routeId?.takeIf { id -> id.isNotBlank() } }
