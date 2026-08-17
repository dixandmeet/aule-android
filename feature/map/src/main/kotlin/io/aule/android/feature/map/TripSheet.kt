package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.component.LineBadge
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.lg),
    ) {
        Text(
            text = stringResource(R.string.nav_trip),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = AuleSpacing.lg)
                .semantics { heading() },
        )
        Column {
            state.plan.legs.forEachIndexed { index, leg ->
                TripLegRow(leg, current = index == state.progress.legIndex)
            }
        }
        FilledTonalButton(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuleSpacing.lg),
        ) {
            Text(stringResource(R.string.nav_stop))
        }
    }
}

@Composable
private fun TripLegRow(leg: JourneyLeg, current: Boolean) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = {
            Text(
                text = leg.title,
                color = if (current) colors.primary else colors.onSurface,
            )
        },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
                leg.line?.let { line ->
                    LineBadge(
                        line = line,
                        colorHex = leg.lineColor,
                        contentDescription = stringResource(R.string.line_badge, line),
                    )
                }
                if (leg.mode != LegMode.TRANSIT) {
                    Text(
                        text = GeoMath.formatDistance(
                            leg.distanceMeters,
                            DecimalFormatSymbols.getInstance().decimalSeparator,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (current) colors.primaryContainer else colors.surface,
        ),
    )
}
