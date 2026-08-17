package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.designsystem.token.AuleSpacing
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
                MapSearchBar(
                    search = state.search,
                    onQueryChange = onSearchQuery,
                    onActivate = onSearchActivate,
                    onCancel = onSearchCancel,
                    onSelectStop = onSelectSearchStop,
                    onSelectPlace = onSelectSearchPlace,
                    onOpenMenu = onOpenMenu,
                )
                if (!state.search.isActive) {
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

        if (navigating || !state.search.isActive) {
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

@Composable
private fun DiagnosticsPill(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FleetStatusPill(
    label: String,
    isLive: Boolean,
    onClick: () -> Unit,
) {
    val hint = stringResource(R.string.fleet_nearby_hint)
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            RealtimeDot(
                isLive = isLive,
                liveDescription = label,
                scheduledDescription = label,
            )
        },
        modifier = Modifier.semantics { contentDescription = "$label. $hint" },
    )
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
    val active = mode.followsSomething
    val label = stringResource(
        if (active) R.string.map_orient else R.string.map_recenter,
    )
    val colors = MaterialTheme.colorScheme
    val tokens = AuleTheme.tokens
    val fill = if (active) tokens.accent.color else colors.surfaceContainerHigh
    val glyph = if (active) tokens.onAccent.color else colors.onSurface

    SmallFloatingActionButton(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        modifier = modifier,
        containerColor = fill,
        contentColor = glyph,
    ) {
        Icon(
            imageVector = AuleGlyph.HEADING.asImageVector(filled = active),
            contentDescription = label,
        )
    }
}

/**
 * L'action principale de la carte : ouvrir le calcul d'itinéraire.
 *
 * Un FAB, pas une destination de barre : le guide réserve la barre aux
 * écrans, et le FAB à **l'action** qu'on veut voir en premier. Taille
 * ordinaire, couleurs du composant (`primaryContainer`), icône 24 dp,
 * description pour le lecteur d'écran. Rien d'autre — forme et ombre
 * viennent de Material.
 */
@Composable
internal fun RouteActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val label = stringResource(R.string.rail_route_a11y)
    FloatingActionButton(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        modifier = modifier,
        containerColor = AuleTheme.tokens.accent.color,
        contentColor = AuleTheme.tokens.onAccent.color,
    ) {
        Icon(
            imageVector = AuleGlyph.ROUTE.asImageVector(),
            contentDescription = label,
            modifier = Modifier.size(AuleControl.icon),
        )
    }
}

/**
 * La barre de navigation et les FAB, collés au bas de la fenêtre.
 *
 * Le FAB d'itinéraire est l'action principale : il reste **au-dessus**
 * de la barre, ancré à droite, à 16 dp du bord et de la barre — c'est
 * le placement Material 3, et il ne doit pas recouvrir la barre. Il ne
 * partage pas la ligne de l'attribution : un `Row` laissait le copyright
 * chasser les boutons hors de l'écran. Le cadrage, action secondaire,
 * s'empile au-dessus en small FAB.
 */
@Composable
internal fun MapActionChrome(
    cameraMode: CameraMode,
    items: List<MapActionItem>,
    onRecenter: () -> Unit,
    showAttribution: Boolean,
    onRoute: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val barVisible = items.isNotEmpty()
    Column(modifier = modifier.fillMaxWidth()) {
        MapActionOverlay(
            cameraMode = cameraMode,
            onRecenter = onRecenter,
            onRoute = onRoute,
            showAttribution = showAttribution,
            barVisible = barVisible,
        )
        MapActionBar(items = items)
    }
}

/**
 * Attribution à gauche, FAB à droite : deux calques, pas une ligne.
 * Sinon le copyright pousse les boutons hors de l'écran.
 */
@Composable
private fun MapActionOverlay(
    cameraMode: CameraMode,
    onRecenter: () -> Unit,
    onRoute: (() -> Unit)?,
    showAttribution: Boolean,
    barVisible: Boolean,
) {
    val reduceMotion = reduceMotionEnabled()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    if (barVisible) {
                        WindowInsetsSides.Horizontal
                    } else {
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    },
                ),
            )
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.lg),
    ) {
        AnimatedVisibility(
            visible = showAttribution,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(end = AuleControl.height + AuleSpacing.xl),
            enter = if (reduceMotion) {
                EnterTransition.None
            } else {
                fadeIn(tween(AuleMotion.POP_MS))
            },
            exit = if (reduceMotion) {
                ExitTransition.None
            } else {
                fadeOut(tween(AuleMotion.POP_MS))
            },
        ) {
            val attributionA11y = stringResource(R.string.map_attribution_a11y)
            AuleCappedFontScale {
                Text(
                    text = MAP_ATTRIBUTION,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = attributionA11y },
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.lg),
            horizontalAlignment = Alignment.End,
        ) {
            RecenterButton(mode = cameraMode, onClick = onRecenter)
            if (onRoute != null) {
                RouteActionButton(onClick = onRoute)
            }
        }
    }
}

private data class BannerIssue(
    val message: String,
    val action: String?,
    val onAction: (() -> Unit)?,
)
