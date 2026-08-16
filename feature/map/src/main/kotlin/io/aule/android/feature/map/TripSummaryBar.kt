package io.aule.android.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
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
    val tokens = AuleTheme.tokens
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
    val shape = RoundedCornerShape(topStart = AuleRadius.lg, topEnd = AuleRadius.lg)
    AuleCappedFontScale(maxScale = 1.3f) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .onSizeChanged { onHeightPx(it.height.toFloat()) }
                .auleShadow(AuleElevation.RESTING, shape)
                .clip(shape)
                .background(tokens.surfaceSolid.color)
                .clickable(onClick = onOpen)
                .defaultMinSize(minHeight = TripSummaryBarHeight)
                .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md)
                .semantics {
                    role = Role.Button
                    contentDescription = spoken
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryColumn(value = arrival, label = arrivalLabel)
            SummaryColumn(value = remaining, label = remainingLabel)
            SummaryColumn(value = thirdValue, label = thirdLabel)
        }
    }
}

@Composable
private fun SummaryColumn(value: String, label: String) {
    val tokens = AuleTheme.tokens
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text = value,
            style = auleTextStyle(AuleRole.DATA, FontWeight.SemiBold).copy(color = tokens.onSurface.color),
        )
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
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
