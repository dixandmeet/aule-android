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
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.token.AuleRole
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
    val tokens = AuleTheme.tokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.xl),
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
                BasicText(
                    text = vehicle.destination
                        ?: stringResource(R.string.vehicle_unknown_destination),
                    style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                        .copy(color = tokens.onSurface.color),
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
                    BasicText(
                        text = stringResource(
                            if (vehicle.isLive) R.string.vehicle_live else R.string.vehicle_estimated,
                        ),
                        style = auleTextStyle(AuleRole.KICKER)
                            .copy(color = tokens.onSurfaceMuted.color),
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
                    BasicText(
                        text = stringResource(R.string.vehicle_next_stop),
                        style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                            .copy(color = tokens.onSurfaceMuted.color),
                    )
                    BasicText(
                        text = vehicle.nextStop ?: stringResource(R.string.value_unknown),
                        style = auleTextStyle(AuleRole.TITLE, FontWeight.Medium)
                            .copy(color = tokens.onSurface.color),
                        maxLines = 1,
                    )
                }
                vehicle.etaSeconds?.let { eta ->
                    Column(horizontalAlignment = Alignment.End) {
                        BasicText(
                            text = stringResource(
                                if (eta < 60) {
                                    R.string.vehicle_eta_arrival
                                } else {
                                    R.string.vehicle_eta_in
                                },
                            ),
                            style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                                .copy(color = tokens.onSurfaceMuted.color),
                        )
                        BasicText(
                            text = if (eta < 60) {
                                stringResource(R.string.vehicle_eta_arriving)
                            } else {
                                stringResource(R.string.vehicle_eta_minutes, (eta / 60).toInt())
                            },
                            style = auleTextStyle(AuleRole.DATA, FontWeight.SemiBold).copy(
                                color = if (vehicle.isLive) {
                                    tokens.realtime.color
                                } else {
                                    tokens.onSurface.color
                                },
                            ),
                        )
                    }
                }
            }
        }

        AuleButton(
            title = stringResource(
                if (isFollowing) R.string.vehicle_unfollow else R.string.vehicle_follow,
            ),
            onClick = onFollow,
            prominence = if (isFollowing) {
                AuleButtonProminence.TINTED
            } else {
                AuleButtonProminence.FILLED
            },
        )
    }
}
