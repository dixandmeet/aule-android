package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.drawAuleGlyph
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.location.LocationAuthorization
import io.aule.android.core.map.MAP_ATTRIBUTION
import io.aule.android.core.map.camera.CameraMode
import io.aule.android.core.model.Place
import io.aule.android.core.model.StopSearchHit

/**
 * Ce qui flotte au-dessus de la carte.
 *
 * Recherche et pastilles hors guidage ; bandeau et barre d'arrivée dès
 * qu'un trajet est engagé. Le bouton de cadrage reste : après un geste,
 * c'est lui qui rend la navigation.
 */
@Composable
internal fun MapHud(
    state: MapUiState,
    authorization: LocationAuthorization,
    lastLocationError: String?,
    showsDiagnostics: Boolean,
    diagnosticsLabel: String,
    onShowNearby: () -> Unit,
    onRetryStops: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPrecise: () -> Unit,
    onSearchQuery: (String) -> Unit,
    onSearchActivate: () -> Unit,
    onSearchCancel: () -> Unit,
    onSelectSearchStop: (StopSearchHit) -> Unit,
    onSelectSearchPlace: (Place) -> Unit,
    onOpenTrip: () -> Unit = {},
    onSummaryHeightPx: (Float) -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
    serviceBanner: String? = null,
    serviceBannerAction: String? = null,
    onServiceBannerAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val navigating = state.isNavigating
    val showingTrip = state.navigation?.showingTrip == true
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuleSpacing.lg)
                .padding(top = AuleSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            if (serviceBanner != null) {
                AuleBanner(
                    message = serviceBanner,
                    action = serviceBannerAction,
                    onAction = onServiceBannerAction,
                )
            }
            if (navigating) {
                val navigation = state.navigation
                if (navigation != null) {
                    GuidanceBanner(state = navigation)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                ) {
                    // Le menu s'efface pendant la recherche : la barre a besoin
                    // de toute la largeur, et sa flèche sert alors de sortie.
                    if (onOpenMenu != null && !state.search.isActive) {
                        MenuButton(onClick = onOpenMenu)
                    }
                    MapSearchBar(
                        search = state.search,
                        onQueryChange = onSearchQuery,
                        onActivate = onSearchActivate,
                        onCancel = onSearchCancel,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!state.search.showsResults) {
                    if (showsDiagnostics) {
                        DiagnosticsPill(diagnosticsLabel)
                    }
                    if (state.showsFleetStatus) {
                        FleetStatusPill(
                            label = state.fleetStatus.label(),
                            isLive = state.fleetStatus is io.aule.android.core.model.FleetStatus.LiveOnly ||
                                state.fleetStatus is io.aule.android.core.model.FleetStatus.Mixed,
                            onClick = onShowNearby,
                        )
                    }
                    IssueBanner(
                        state = state,
                        authorization = authorization,
                        lastLocationError = lastLocationError,
                        onRetryStops = onRetryStops,
                        onOpenSettings = onOpenSettings,
                        onRequestPrecise = onRequestPrecise,
                    )
                }
            }
        }

        if (!navigating && state.search.showsResults) {
            SearchResults(
                search = state.search,
                onSelectStop = onSelectSearchStop,
                onSelectPlace = onSelectSearchPlace,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.lg)
                    .padding(top = AuleSpacing.sm, bottom = AuleSpacing.md),
            )
        } else {
            Box(modifier = Modifier.weight(1f))

            val navigation = state.navigation
            if (navigating && !showingTrip && navigation != null) {
                TripSummaryBar(
                    summary = navigation.summary,
                    onOpen = onOpenTrip,
                    onHeightPx = onSummaryHeightPx,
                )
            }
        }
    }
}

/**
 * L'entrée du compte, à gauche de la recherche.
 *
 * Même hauteur et même ombre que la barre : les deux forment une seule ligne
 * de chrome, pas un bouton posé sur elle.
 */
@Composable
private fun MenuButton(onClick: () -> Unit) {
    val tokens = AuleTheme.tokens
    val view = LocalView.current
    val label = stringResource(R.string.menu_open)
    Box(
        modifier = Modifier
            .size(AuleControl.height)
            .auleShadow(AuleElevation.FLOATING, CircleShape)
            .clip(CircleShape)
            .background(tokens.surfaceSolid.color)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .drawBehind { drawAuleGlyph(AuleGlyph.MENU, tokens.onSurface.color) },
    )
}

@Composable
private fun DiagnosticsPill(label: String) {
    val tokens = AuleTheme.tokens
    BasicText(
        text = label,
        style = auleTextStyle(AuleRole.KICKER, androidx.compose.ui.text.font.FontWeight.Medium)
            .copy(color = tokens.onSurfaceMuted.color),
        modifier = Modifier
            .clip(RoundedCornerShape(AuleRadius.pill))
            .background(tokens.surface.color)
            .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.xs),
    )
}

@Composable
private fun FleetStatusPill(
    label: String,
    isLive: Boolean,
    onClick: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val hint = stringResource(R.string.fleet_nearby_hint)
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clip(RoundedCornerShape(AuleRadius.pill))
            .background(tokens.surface.color)
            .clickable(onClickLabel = hint, onClick = onClick)
            .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.sm)
            .semantics {
                role = Role.Button
                contentDescription = "$label. $hint"
            },
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RealtimeDot(
            isLive = isLive,
            liveDescription = label,
            scheduledDescription = label,
        )
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.KICKER, androidx.compose.ui.text.font.FontWeight.Medium)
                .copy(color = tokens.onSurface.color),
        )
    }
}

/**
 * Ce qui ne va pas, et ce qu'on peut y faire.
 *
 * Un seul incident à la fois, le plus grave : trois bandeaux empilés ne se
 * lisent pas. Par ordre : sans fond de carte rien n'est lisible, sans arrêts
 * la carte ment par omission, sans position elle reste utilisable mais ne
 * suit plus.
 */
@Composable
private fun IssueBanner(
    state: MapUiState,
    authorization: LocationAuthorization,
    lastLocationError: String?,
    onRetryStops: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPrecise: () -> Unit,
) {
    val issue = when {
        state.mapError != null -> BannerIssue(
            message = stringResource(R.string.issue_map, state.mapError),
            action = null,
            onAction = null,
        )
        state.stopsFailure != null -> BannerIssue(
            message = stringResource(R.string.issue_stops, state.stopsFailure),
            action = stringResource(
                if (state.isLoadingStops) R.string.issue_retrying else R.string.issue_retry,
            ),
            onAction = if (state.isLoadingStops) null else onRetryStops,
        )
        authorization == LocationAuthorization.DENIED ||
            authorization == LocationAuthorization.SERVICES_DISABLED -> BannerIssue(
            message = stringResource(R.string.map_location_denied),
            action = stringResource(R.string.map_location_settings),
            onAction = onOpenSettings,
        )
        authorization == LocationAuthorization.REDUCED_ACCURACY -> BannerIssue(
            message = stringResource(R.string.map_location_reduced),
            action = stringResource(R.string.map_location_allow),
            onAction = onRequestPrecise,
        )
        lastLocationError != null -> BannerIssue(
            message = lastLocationError,
            action = null,
            onAction = null,
        )
        else -> null
    } ?: return

    AuleBanner(
        message = issue.message,
        action = issue.action,
        onAction = issue.onAction,
    )
}

@Composable
internal fun RecenterButton(
    mode: CameraMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val tokens = AuleTheme.tokens
    val active = mode.followsSomething
    val label = stringResource(
        if (active) R.string.map_orient else R.string.map_recenter,
    )
    val fill = if (active) tokens.accent.color else tokens.surface.color
    val glyph = if (active) tokens.onAccent.color else tokens.onSurface.color

    Box(
        modifier = modifier
            .size(AuleTouch.minimum)
            .auleShadow(AuleElevation.FLOATING, CircleShape)
            .clip(CircleShape)
            .background(fill)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .drawBehind { drawAuleGlyph(AuleGlyph.HEADING, glyph, filled = active) },
        contentAlignment = Alignment.Center,
    ) {}
}

/**
 * Les rails et le cadrage, posés **au-dessus** des volets.
 *
 * À gauche, ce qui commence quelque chose ; à droite, ce qu'on demande à
 * la carte ; au centre, le retour au suivi. Les rails se réduisent quand
 * un volet monte — ils ne disparaissent pas.
 */
@Composable
internal fun MapActionChrome(
    cameraMode: CameraMode,
    compact: Boolean,
    leftItems: List<MapActionItem>,
    rightItems: List<MapActionItem>,
    onRecenter: () -> Unit,
    showAttribution: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MapActionRail(items = leftItems, compact = compact)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AuleSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            RecenterButton(mode = cameraMode, onClick = onRecenter)
            if (showAttribution) {
                val attributionA11y = stringResource(R.string.map_attribution_a11y)
                AuleCappedFontScale {
                    BasicText(
                        text = MAP_ATTRIBUTION,
                        style = auleTextStyle(AuleRole.KICKER).copy(
                            color = AuleTheme.tokens.onSurfaceMuted.color,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(AuleRadius.pill))
                            .background(
                                AuleTheme.tokens.surface.color.copy(alpha = AuleAlpha.VEIL),
                            )
                            .padding(horizontal = AuleSpacing.sm, vertical = AuleSpacing.xs)
                            .semantics { contentDescription = attributionA11y },
                    )
                }
            }
        }
        MapActionRail(items = rightItems, compact = compact)
    }
}

private data class BannerIssue(
    val message: String,
    val action: String?,
    val onAction: (() -> Unit)?,
)
