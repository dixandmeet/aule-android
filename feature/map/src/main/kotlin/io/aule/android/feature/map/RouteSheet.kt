package io.aule.android.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleButton
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
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
@Composable
internal fun RouteSheet(
    state: RouteUiState,
    onSelect: (String) -> Unit,
    onMode: (RouteMode) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AuleTheme.tokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.lg),
    ) {
        BasicText(
            text = stringResource(R.string.route_title),
            style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                .copy(color = tokens.onSurface.color),
            modifier = Modifier.semantics { heading() },
        )

        Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
            RouteEndpoint(label = stringResource(R.string.route_from), value = state.origin.label)
            RouteEndpoint(label = stringResource(R.string.route_to), value = state.destination.label)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
            ModeChip(
                label = stringResource(R.string.route_mode_transit),
                selected = state.mode == RouteMode.TRANSIT,
                onClick = { onMode(RouteMode.TRANSIT) },
            )
            ModeChip(
                label = stringResource(R.string.route_mode_car),
                selected = state.mode == RouteMode.CAR,
                onClick = { onMode(RouteMode.CAR) },
            )
        }

        when (state.status) {
            RouteLoadStatus.LOADING -> BasicText(
                text = stringResource(R.string.route_loading),
                style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
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
                    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
                        plan.alternatives.forEach { candidate ->
                            RouteCandidateRow(
                                candidate = candidate,
                                selected = candidate.id == state.selectedId,
                                onClick = { onSelect(candidate.id) },
                            )
                        }
                    }
                    if (state.selected != null) {
                        AuleButton(
                            title = stringResource(R.string.route_start),
                            onClick = onStart,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteEndpoint(label: String, value: String) {
    val tokens = AuleTheme.tokens
    Column {
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                .copy(color = tokens.onSurfaceMuted.color),
        )
        BasicText(
            text = value,
            style = auleTextStyle(AuleRole.BODY, FontWeight.Medium)
                .copy(color = tokens.onSurface.color),
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = AuleTheme.tokens
    val background = if (selected) tokens.accent.color else tokens.accent.color.copy(alpha = 0.12f)
    val foreground = if (selected) tokens.onAccent.color else tokens.accentOnSurface.color
    BasicText(
        text = label,
        style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold).copy(color = foreground),
        modifier = Modifier
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clip(RoundedCornerShape(AuleRadius.pill))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.sm)
            .semantics {
                role = Role.Button
                this.selected = selected
            },
    )
}

@Composable
private fun RouteCandidateRow(
    candidate: RouteCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = AuleTheme.tokens
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuleRadius.md))
            .background(if (selected) tokens.accent.color.copy(alpha = 0.12f) else tokens.surface.color)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = description
            }
            .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = duration,
                style = auleTextStyle(AuleRole.DATA, FontWeight.SemiBold)
                    .copy(color = tokens.onSurface.color),
            )
            BasicText(
                text = distance,
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
            )
        }
        if (departure != null) {
            BasicText(
                text = stringResource(R.string.route_departs, departure),
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
            )
        }
        if (profile != null) {
            BasicText(
                text = profile,
                style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                    .copy(color = tokens.accentOnSurface.color),
            )
        }
        if (reliability != null) {
            BasicText(
                text = reliability,
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
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
                BasicText(
                    text = step.label,
                    style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurface.color),
                    maxLines = 2,
                )
            }
        }
    }
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
