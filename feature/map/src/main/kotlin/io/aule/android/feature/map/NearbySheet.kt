package io.aule.android.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
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
            text = stringResource(R.string.nearby_title),
            style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                .copy(color = tokens.onSurface.color),
            modifier = Modifier.semantics { heading() },
        )

        if (digest.isEmpty) {
            AuleEmptyState(
                title = stringResource(R.string.nearby_empty_title),
                detail = stringResource(R.string.nearby_empty_detail),
            )
        }

        if (digest.stops.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
                BasicText(
                    text = stringResource(R.string.nearby_section_stops),
                    style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                        .copy(color = tokens.onSurfaceMuted.color),
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
                    NearbyRow(
                        title = entry.stop.departuresKey,
                        subtitle = modeLabel,
                        distance = distance,
                        contentDescription = label,
                        clickLabel = stringResource(R.string.nearby_hint_stop),
                        onClick = { onSelectStop(entry.stop) },
                    )
                }
            }
        }

        if (digest.vehicles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
                BasicText(
                    text = stringResource(R.string.nearby_section_vehicles),
                    style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                        .copy(color = tokens.onSurfaceMuted.color),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuleTouch.minimum)
                            .clickable(
                                onClickLabel = stringResource(R.string.nearby_hint_vehicle),
                                onClick = { onSelectVehicle(entry.vehicle) },
                            )
                            .semantics {
                                role = Role.Button
                                contentDescription = label
                            }
                            .padding(vertical = AuleSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LineBadge(
                            line = entry.vehicle.lineName,
                            colorHex = null,
                            contentDescription = stringResource(
                                R.string.line_badge,
                                entry.vehicle.lineName,
                            ),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = entry.vehicle.destination
                                    ?: stringResource(R.string.vehicle_unknown_destination),
                                style = auleTextStyle(AuleRole.BODY, FontWeight.Medium)
                                    .copy(color = tokens.onSurface.color),
                                maxLines = 1,
                            )
                            RealtimeDot(
                                isLive = entry.vehicle.isLive,
                                liveDescription = stringResource(R.string.vehicle_live),
                                scheduledDescription = stringResource(R.string.vehicle_estimated),
                            )
                        }
                        BasicText(
                            text = distance,
                            style = auleTextStyle(AuleRole.KICKER)
                                .copy(color = tokens.onSurfaceMuted.color),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyRow(
    title: String,
    subtitle: String,
    distance: String,
    contentDescription: String,
    clickLabel: String,
    onClick: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .padding(vertical = AuleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = auleTextStyle(AuleRole.BODY, FontWeight.Medium)
                    .copy(color = tokens.onSurface.color),
            )
            BasicText(
                text = subtitle,
                style = auleTextStyle(AuleRole.KICKER)
                    .copy(color = tokens.onSurfaceMuted.color),
            )
        }
        BasicText(
            text = distance,
            style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
        )
    }
}
