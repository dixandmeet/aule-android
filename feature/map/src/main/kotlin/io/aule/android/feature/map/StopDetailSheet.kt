package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.component.TransportBadge
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.model.DepartureRow
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.repository.StopRepository
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Le panneau d'un arrêt : ce qui passe, et quand.
 *
 * L'horloge du panneau bat toutes les dix secondes. Sans elle, « 3 min »
 * resterait « 3 min » pendant dix minutes.
 */
@Composable
internal fun StopDetailSheet(
    stop: TransitStop,
    repository: StopRepository,
    dispatchers: AuleDispatchers,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = remember(stop.id) { StopDetailModel(repository, dispatchers) }
    DisposableEffect(model) {
        model.load(stop.departuresKey)
        onDispose { model.close() }
    }

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(stop.id) {
        while (isActive) {
            delay(10_000)
            now = Instant.now()
        }
    }

    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
            Text(
                text = stop.departuresKey,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportBadge(
                    mode = stop.mode,
                    label = stop.mode.label(),
                    tint = stop.mode.markerColor(AuleTheme.night).color,
                )
                if (stop.isWheelchairAccessible) {
                    Text(
                        text = stringResource(R.string.stop_accessible),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                stop.code?.let { code ->
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        Button(
            onClick = onRoute,
            modifier = Modifier.fillMaxWidth(),
            colors = auleAccentButtonColors(),
        ) {
            Text(stringResource(R.string.route_go))
        }

        when {
            model.isLoading && model.departures == null -> {
                AuleLoadingState(
                    label = stringResource(R.string.stop_loading),
                )
            }
            model.departures != null -> {
                DeparturesSection(model.departures!!, now)
            }
            model.error != null -> {
                AuleEmptyState(
                    title = stringResource(R.string.stop_unavailable_title),
                    detail = model.error,
                )
            }
        }

        if (model.servingLines.isNotEmpty()) {
            ServingSection(model.servingLines)
        }
    }
}

@Composable
private fun DeparturesSection(departures: StopDepartures, now: Instant) {
    val rows = departures.grouped(from = now)
    if (rows.isEmpty()) {
        AuleEmptyState(
            title = departures.outcome.title(),
            detail = departures.outcome.detail(),
        )
        return
    }
    Column {
        Text(
            text = stringResource(R.string.stop_next_departures),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = AuleSpacing.sm),
        )
        rows.forEachIndexed { index, row ->
            DepartureRowItem(row)
            if (index < rows.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = DIVIDER_INSET),
                    thickness = AuleStroke.hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun DepartureRowItem(row: DepartureRow) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = {
            Text(
                text = row.destination,
                maxLines = 2,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                ),
            )
        },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = META_TOP_PADDING),
            ) {
                RealtimeDot(
                    isLive = row.isRealtime,
                    liveDescription = stringResource(R.string.stop_realtime),
                    scheduledDescription = stringResource(R.string.stop_scheduled),
                )
                Text(
                    text = stringResource(
                        if (row.isRealtime) R.string.stop_realtime else R.string.stop_scheduled,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .padding(top = AuleSpacing.xs)
                    .size(width = BADGE_SLOT_WIDTH, height = BADGE_SLOT_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                LineBadge(
                    line = row.line,
                    colorHex = row.lineColor,
                    contentDescription = stringResource(R.string.line_badge, row.line),
                )
            }
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                val next = row.nextWait
                if (next != null) {
                    Text(
                        text = next.label(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = if (row.isRealtime) realtimeInk() else colors.onSurface,
                    )
                }
                val rest = row.followingWaits.joinToString(" · ")
                if (rest.isNotEmpty()) {
                    Text(
                        text = rest,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = META_TOP_PADDING),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(vertical = META_TOP_PADDING),
    )
}

@Composable
private fun ServingSection(lines: List<ServingLine>) {
    Column {
        Text(
            text = stringResource(R.string.stop_serving_lines),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = AuleSpacing.sm, top = AuleSpacing.md),
        )
        lines.forEachIndexed { index, line ->
            ListItem(
                headlineContent = {
                    Text(
                        text = line.direction,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier.size(width = BADGE_SLOT_WIDTH, height = BADGE_SLOT_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) {
                        LineBadge(
                            line = line.line,
                            colorHex = line.lineColor,
                            contentDescription = stringResource(R.string.line_badge, line.line),
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (index < lines.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = DIVIDER_INSET),
                    thickness = AuleStroke.hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

/** L'alinéa du filet sous le badge : largeur du cran leading Material. */
private val DIVIDER_INSET = 56.dp

/** Le cran d'air au-dessus des métadonnées d'un passage. */
private val META_TOP_PADDING = 2.dp

/** L'emplacement du badge de ligne dans la rangée. */
private val BADGE_SLOT_WIDTH = 40.dp
private val BADGE_SLOT_HEIGHT = 24.dp
