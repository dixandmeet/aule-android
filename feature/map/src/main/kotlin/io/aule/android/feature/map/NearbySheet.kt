package io.aule.android.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.NearbyDigest
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportVehicle
import java.text.DecimalFormatSymbols

/**
 * « Autour de vous » — le chemin d'accès à la carte quand on ne la voit pas.
 *
 * La carte MapLibre est un tampon opaque : TalkBack n'y trouve rien, et la
 * sélection passe par un hit-test de 22 dp qui suppose qu'on sait déjà où
 * poser le doigt. Cette liste est la réponse, et elle répond à la vraie
 * question — « qu'est-ce qu'il y a autour de moi ? ».
 */
@Composable
internal fun NearbySheet(
    digest: NearbyDigest,
    onSelectStop: (TransitStop) -> Unit,
    onSelectVehicle: (TransportVehicle) -> Unit,
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
            text = stringResource(R.string.nearby_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = AuleSpacing.lg)
                .semantics { heading() },
        )

        if (digest.isEmpty) {
            AuleEmptyState(
                title = stringResource(R.string.nearby_empty_title),
                detail = stringResource(R.string.nearby_empty_detail),
                modifier = Modifier.padding(horizontal = AuleSpacing.lg),
            )
        }

        if (digest.stops.isNotEmpty()) {
            Column {
                Text(
                    text = stringResource(R.string.nearby_section_stops),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                )
                digest.stops.forEach { entry ->
                    val distance = GeoMath.formatDistance(
                        entry.distanceMeters,
                        DecimalFormatSymbols.getInstance().decimalSeparator,
                    )
                    val modeLabel = entry.stop.mode.label()
                    val atDistance = stringResource(R.string.nearby_at_distance, distance)
                    val wheelchair = stringResource(R.string.nearby_wheelchair)
                    val label = buildString {
                        append(entry.stop.departuresKey)
                        append(", ")
                        append(modeLabel)
                        append(", ")
                        append(atDistance)
                        if (entry.stop.isWheelchairAccessible) {
                            append(", ")
                            append(wheelchair)
                        }
                    }
                    ListItem(
                        headlineContent = { Text(entry.stop.departuresKey) },
                        supportingContent = { Text(modeLabel) },
                        trailingContent = {
                            Text(
                                text = distance,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier
                            .clickable(
                                onClickLabel = stringResource(R.string.nearby_hint_stop),
                                onClick = { onSelectStop(entry.stop) },
                            )
                            .semantics { contentDescription = label },
                    )
                }
            }
        }

        if (digest.vehicles.isNotEmpty()) {
            Column {
                Text(
                    text = stringResource(R.string.nearby_section_vehicles),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                )
                digest.vehicles.forEach { entry ->
                    val distance = GeoMath.formatDistance(
                        entry.distanceMeters,
                        DecimalFormatSymbols.getInstance().decimalSeparator,
                    )
                    val modeLabel = entry.vehicle.mode.label()
                    val towards = entry.vehicle.destination?.let {
                        stringResource(R.string.nearby_towards, it)
                    }
                    val atDistance = stringResource(R.string.nearby_at_distance, distance)
                    val feedLabel = stringResource(
                        if (entry.vehicle.isLive) R.string.nearby_live else R.string.nearby_estimated,
                    )
                    val label = buildString {
                        append(modeLabel)
                        append(" ")
                        append(entry.vehicle.lineName)
                        if (towards != null) {
                            append(", ")
                            append(towards)
                        }
                        append(", ")
                        append(atDistance)
                        append(", ")
                        append(feedLabel)
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                text = entry.vehicle.destination
                                    ?: stringResource(R.string.vehicle_unknown_destination),
                            )
                        },
                        supportingContent = {
                            // Le point seul ne dit rien : posé sous un nom de
                            // destination, il se lit comme une puce de liste.
                            // Le détail d'arrêt l'accompagne déjà de son
                            // libellé — c'est la même information, elle
                            // s'écrit pareil.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RealtimeDot(
                                    isLive = entry.vehicle.isLive,
                                    liveDescription = stringResource(R.string.vehicle_live),
                                    scheduledDescription = stringResource(
                                        R.string.vehicle_estimated,
                                    ),
                                )
                                Text(text = feedLabel)
                            }
                        },
                        leadingContent = {
                            LineBadge(
                                line = entry.vehicle.lineName,
                                colorHex = null,
                                contentDescription = stringResource(
                                    R.string.line_badge,
                                    entry.vehicle.lineName,
                                ),
                            )
                        },
                        trailingContent = {
                            Text(
                                text = distance,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier
                            .clickable(
                                onClickLabel = stringResource(R.string.nearby_hint_vehicle),
                                onClick = { onSelectVehicle(entry.vehicle) },
                            )
                            .semantics { contentDescription = label },
                    )
                }
            }
        }
    }
}
