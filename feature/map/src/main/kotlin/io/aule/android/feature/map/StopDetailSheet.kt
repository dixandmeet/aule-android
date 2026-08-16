package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleButton
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.TransportBadge
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
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

    val tokens = AuleTheme.tokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
            BasicText(
                text = stop.departuresKey,
                style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                    .copy(color = tokens.onSurface.color),
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
                    BasicText(
                        text = stringResource(R.string.stop_accessible),
                        style = auleTextStyle(AuleRole.KICKER)
                            .copy(color = tokens.onSurfaceMuted.color),
                    )
                }
                stop.code?.let { code ->
                    BasicText(
                        text = code,
                        style = auleTextStyle(AuleRole.KICKER)
                            .copy(color = tokens.onSurfaceMuted.color),
                    )
                }
            }
        }

        AuleButton(
            title = stringResource(R.string.route_go),
            onClick = onRoute,
        )

        when {
            model.isLoading && model.departures == null -> {
                BasicText(
                    text = stringResource(R.string.stop_loading),
                    style = auleTextStyle(AuleRole.BODY)
                        .copy(color = tokens.onSurfaceMuted.color),
                    modifier = Modifier.padding(vertical = AuleSpacing.lg),
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
    val tokens = AuleTheme.tokens
    val rows = departures.grouped(from = now)
    if (rows.isEmpty()) {
        AuleEmptyState(
            title = departures.outcome.title(),
            detail = departures.outcome.detail(),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
        BasicText(
            text = stringResource(R.string.stop_next_departures),
            style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                .copy(color = tokens.onSurfaceMuted.color),
        )
        rows.forEach { row -> DepartureRowItem(row) }
    }
}

@Composable
private fun DepartureRowItem(row: DepartureRow) {
    val tokens = AuleTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(
            line = row.line,
            colorHex = row.lineColor,
            contentDescription = stringResource(R.string.line_badge, row.line),
        )
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = row.destination,
                style = auleTextStyle(AuleRole.BODY, FontWeight.Medium)
                    .copy(color = tokens.onSurface.color),
                maxLines = 1,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RealtimeDot(
                    isLive = row.isRealtime,
                    liveDescription = stringResource(R.string.stop_realtime),
                    scheduledDescription = stringResource(R.string.stop_scheduled),
                )
                BasicText(
                    text = stringResource(
                        if (row.isRealtime) R.string.stop_realtime else R.string.stop_scheduled,
                    ),
                    style = auleTextStyle(AuleRole.KICKER)
                        .copy(color = tokens.onSurfaceMuted.color),
                )
            }
        }
        BasicText(
            text = row.waitsText(),
            style = auleTextStyle(AuleRole.DATA, FontWeight.SemiBold).copy(
                color = if (row.isRealtime) tokens.realtime.color else tokens.onSurface.color,
            ),
        )
    }
}

@Composable
private fun ServingSection(lines: List<ServingLine>) {
    val tokens = AuleTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
        BasicText(
            text = stringResource(R.string.stop_serving_lines),
            style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                .copy(color = tokens.onSurfaceMuted.color),
        )
        lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineBadge(
                    line = line.line,
                    colorHex = line.lineColor,
                    contentDescription = stringResource(R.string.line_badge, line.line),
                )
                BasicText(
                    text = line.direction,
                    style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurface.color),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
