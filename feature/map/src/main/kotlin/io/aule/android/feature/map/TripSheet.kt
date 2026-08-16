package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleButton
import io.aule.android.core.designsystem.component.AuleButtonProminence
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.JourneyLeg
import io.aule.android.core.model.LegMode
import java.text.DecimalFormatSymbols

@Composable
internal fun TripSheet(
    state: NavigationUiState,
    onStop: () -> Unit,
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
            text = stringResource(R.string.nav_trip),
            style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                .copy(color = tokens.onSurface.color),
            modifier = Modifier.semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
            state.plan.legs.forEachIndexed { index, leg ->
                TripLegRow(leg, current = index == state.progress.legIndex)
            }
        }
        AuleButton(
            title = stringResource(R.string.nav_stop),
            onClick = onStop,
            prominence = AuleButtonProminence.TINTED,
        )
    }
}

@Composable
private fun TripLegRow(leg: JourneyLeg, current: Boolean) {
    val tokens = AuleTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
        val color = if (current) tokens.accentOnSurface.color else tokens.onSurface.color
        BasicText(
            text = leg.title,
            style = auleTextStyle(AuleRole.BODY, if (current) FontWeight.SemiBold else FontWeight.Medium)
                .copy(color = color),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leg.line?.let { line ->
                LineBadge(
                    line = line,
                    colorHex = leg.lineColor,
                    contentDescription = stringResource(R.string.line_badge, line),
                )
            }
            if (leg.mode != LegMode.TRANSIT) {
                BasicText(
                    text = GeoMath.formatDistance(
                        leg.distanceMeters,
                        DecimalFormatSymbols.getInstance().decimalSeparator,
                    ),
                    style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
                )
            }
        }
    }
}
