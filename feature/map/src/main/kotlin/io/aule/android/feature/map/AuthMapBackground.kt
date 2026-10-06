package io.aule.android.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.foundation.AuleOpacity
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.map.MapAmbiance
import io.aule.android.core.map.MapController
import io.aule.android.core.map.MapZoom
import io.aule.android.core.map.camera.CameraMode
import io.aule.android.core.map.layer.StationMarkerLayer
import io.aule.android.core.map.layer.TransitLinesLayer

/** Carte publique de la station : aucun suivi GPS, flotte ou accès au compte avant connexion. */
@Composable
fun AuthMapBackground(
    logger: AuleLogger,
    transitArchiveUrl: String?,
    quiet: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val localDensity = LocalDensity.current
    val night = AuleTheme.night
    val reduced = reduceMotionEnabled()
    var mapFailed by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    val controller = remember(logger, density) {
        MapController(logger, density).apply {
            setGesturesEnabled(false)
            setCameraMode(CameraMode.FREE_EXPLORE)
            onMapLoadFailure = { mapFailed = true }
            registry.register(StationMarkerLayer(AUTH_STATION, "Hôpital Bellier"))
        }
    }
    LaunchedEffect(controller, transitArchiveUrl) {
        transitArchiveUrl?.let { url ->
            TransitLinesLayer(url, logger).also {
                it.setVisible(true)
                controller.registerLayer(it)
            }
        }
    }
    val loaded by controller.isStyleLoaded.collectAsStateWithLifecycle()
    LaunchedEffect(loaded, height) {
        if (!loaded || height == 0) return@LaunchedEffect
        mapFailed = false
        // La station se pose au milieu de la bande laissée libre par la carte de connexion.
        controller.sheetHeightPx = height * FORM_COVERAGE
        controller.moveTo(AUTH_STATION, MapZoom.OPENING, MapZoom.PITCH_3D, AUTH_BEARING)
        if (!reduced && !quiet) {
            controller.glide(AUTH_STATION, MapZoom.OPENING - INTRO_ZOOM_DELTA, MapZoom.PITCH_3D, AUTH_BEARING, INTRO_DURATION_MS)
        }
    }
    Box(modifier = modifier.fillMaxSize().onSizeChanged { height = it.height }) {
        AuleMap(controller, MapAmbiance.of(night), Modifier.fillMaxSize())
        if (mapFailed) {
            Text(
                text = stringResource(R.string.auth_map_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = with(localDensity) { (height * ERROR_POSITION).toDp() })
                    .padding(horizontal = AuleSpacing.xl)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = AuleOpacity.GLASS), MaterialTheme.shapes.medium)
                    .padding(AuleSpacing.sm),
            )
        }
    }
}

@Composable
fun AuthMapCredits(modifier: Modifier = Modifier) {
    var showingCredits by remember { mutableStateOf(false) }
    IconButton(
        onClick = { showingCredits = true },
        modifier = modifier.testTag("auth-map-credits")
            .background(MaterialTheme.colorScheme.surface.copy(alpha = AuleOpacity.GLASS), CircleShape),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.legal_open), tint = MaterialTheme.colorScheme.onSurface)
    }
    if (showingCredits) LegalNoticeSheet(onClose = { showingCredits = false })
}

/** Index public du réseau : dashboard/public/tiles/transit-stops-index.json, entrée tram. */
internal val AUTH_STATION = Coordinate(latitude = 47.22119, longitude = -1.52451)
private const val AUTH_BEARING = 32.0
private const val FORM_COVERAGE = 0.56f
private const val ERROR_POSITION = 0.20f
private const val INTRO_ZOOM_DELTA = 0.12
private const val INTRO_DURATION_MS = 650
