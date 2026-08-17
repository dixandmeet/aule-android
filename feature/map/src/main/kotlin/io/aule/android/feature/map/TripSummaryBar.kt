package io.aule.android.feature.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.model.TripSummary
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Hauteur de la barre, port de `kSummaryBarHeightPx`. */
internal val TripSummaryBarHeight = 72.dp

@Composable
internal fun TripSummaryBar(
    summary: TripSummary,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onHeightPx: (Float) -> Unit = {},
) {
    val unknown = stringResource(R.string.value_unknown)
    val arrival = summary.arrivalAt?.let { clockFormatter.format(it) } ?: unknown
    val remaining = summary.remaining?.clockLabel() ?: unknown
    val arrivalLabel = if (summary.estimated) {
        stringResource(R.string.nav_arrival_estimated)
    } else {
        stringResource(R.string.nav_arrival)
    }
    val remainingLabel = stringResource(R.string.nav_remaining)
    val thirdValue = summary.third.valueText()
    val thirdLabel = summary.third.labelText()
    val hint = stringResource(R.string.nav_trip_hint)
    val spoken = "$arrivalLabel $arrival. $remainingLabel $remaining. $thirdLabel $thirdValue. $hint"
    AuleCappedFontScale(maxScale = 1.3f) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .onSizeChanged { onHeightPx(it.height.toFloat()) }
                .clickable(onClick = onOpen)
                .semantics {
                    role = Role.Button
                    contentDescription = spoken
                },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(AuleStroke.hairline, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = TripSummaryBarHeight)
                    .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SummaryColumn(value = arrival, label = arrivalLabel)
                SummaryColumn(value = remaining, label = remainingLabel)
                SummaryColumn(value = thirdValue, label = thirdLabel)
            }
        }
    }
}

@Composable
private fun SummaryColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Duration.clockLabel(): String {
    val minutes = toMinutes()
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return "${hours}h${rest.toString().padStart(2, '0')}"
}

private val clockFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
