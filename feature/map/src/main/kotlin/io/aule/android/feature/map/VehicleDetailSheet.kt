package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.model.TransportVehicle

/**
 * Le panneau d'un véhicule : sa ligne, où il va, ce qu'il dessert ensuite.
 *
 * Court par construction. Quand on touche un bus sur la carte, on veut
 * savoir en trois secondes si c'est le bon — pas ouvrir un dossier.
 */
@Composable
internal fun VehicleDetailSheet(
    vehicle: TransportVehicle,
    isFollowing: Boolean,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.xl)
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.lg),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            LineBadge(
                line = vehicle.lineName,
                colorHex = null,
                contentDescription = stringResource(R.string.line_badge, vehicle.lineName),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vehicle.destination
                        ?: stringResource(R.string.vehicle_unknown_destination),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    modifier = Modifier.semantics { heading() },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RealtimeDot(
                        isLive = vehicle.isLive,
                        liveDescription = stringResource(R.string.vehicle_live),
                        scheduledDescription = stringResource(R.string.vehicle_estimated),
                    )
                    Text(
                        text = stringResource(
                            if (vehicle.isLive) R.string.vehicle_live else R.string.vehicle_estimated,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        if (vehicle.nextStop != null || vehicle.etaSeconds != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vehicle_next_stop),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = vehicle.nextStop ?: stringResource(R.string.value_unknown),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
                vehicle.etaSeconds?.let { eta ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(
                                if (eta < 60) {
                                    R.string.vehicle_eta_arrival
                                } else {
                                    R.string.vehicle_eta_in
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = if (eta < 60) {
                                stringResource(R.string.vehicle_eta_arriving)
                            } else {
                                stringResource(R.string.vehicle_eta_minutes, (eta / 60).toInt())
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (vehicle.isLive) {
                                realtimeInk()
                            } else {
                                colors.onSurface
                            },
                        )
                    }
                }
            }
        }

        if (isFollowing) {
            FilledTonalButton(onClick = onFollow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vehicle_unfollow))
            }
        } else {
            Button(
                onClick = onFollow,
                modifier = Modifier.fillMaxWidth(),
                colors = auleAccentButtonColors(),
            ) {
                Text(stringResource(R.string.vehicle_follow))
            }
        }
    }
}
