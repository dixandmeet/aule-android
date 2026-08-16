package io.aule.android.feature.map

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.location.LocationProvider
import io.aule.android.core.map.MapController
import io.aule.android.core.map.MapZoom
import io.aule.android.core.map.camera.CameraInput
import io.aule.android.core.map.camera.CameraMode
import io.aule.android.core.map.camera.NavigationCamera
import io.aule.android.core.map.layer.UserPuckLayer
import io.aule.android.core.map.layer.VehiclesLayer

/**
 * Le ticker caméra, ~15 Hz.
 *
 * Volontairement plus lent que l'écran : la caméra suit une position GPS
 * qui arrive à 1 Hz, et la recalculer à 120 Hz ne ferait que consommer.
 * La fluidité vient de l'interpolation du puck, pas de la fréquence des
 * décisions.
 *
 * Compose n'est pas dans le chemin : on lit le dernier fix, on pousse le
 * puck, on demande un cadrage. Aucun `State` n'est écrit.
 */
internal class CameraFollowState {
    var hasCenteredOnUser = false
    var selectedVehicleId: String? = null
    var selectedVehicleSpeed: Double = 0.0
    /** Cap du tracé sous les pieds, seulement pendant un guidage. */
    var routeBearingDegrees: Double? = null
}

internal const val CAMERA_TICK_MS = 66L

internal fun tickCamera(
    controller: MapController,
    puck: UserPuckLayer,
    vehicles: VehiclesLayer,
    location: LocationProvider,
    state: CameraFollowState,
) {
    val fix = location.lastFix.value
    val stabilizerHeading = fix?.stabilizedHeading
    // **Deux consommateurs du cap, deux comportements.**
    //
    // Le cône du puck dit « vous regardez par là ». Sous 0,7 m/s le cap
    // est gelé : le dernier connu n'est plus une information, c'est un
    // souvenir. La caméra, elle, **conserve** ce dernier cap : le
    // remettre à zéro ferait pivoter la carte au nord à chaque feu rouge.
    puck.update(
        fix = fix,
        stabilizedHeading = if (fix == null || fix.isHeadingFrozen) null else stabilizerHeading,
    )

    // Le premier point GPS amène la carte sur l'utilisateur, une seule
    // fois. Le refaire à chaque position reprendrait la main sur quelqu'un
    // en train d'explorer.
    if (!state.hasCenteredOnUser && fix != null && fix.isUsable) {
        state.hasCenteredOnUser = true
        controller.moveTo(
            center = fix.coordinate,
            zoom = MapZoom.OPENING,
            pitch = MapZoom.OPENING_PITCH,
            bearing = 0.0,
        )
    }

    val mode = controller.cameraMode.value
    val center: Coordinate
    val headingForCamera: Double?
    val speed: Double

    if (mode == CameraMode.FOLLOW_VEHICLE) {
        val id = state.selectedVehicleId ?: return
        val pose = vehicles.displayedCoordinate(id) ?: return
        center = pose.coordinate
        headingForCamera = pose.heading
        speed = state.selectedVehicleSpeed
    } else {
        center = puck.displayedCoordinate ?: return
        headingForCamera = fix?.stabilizedHeading
        speed = fix?.speedMetersPerSecond ?: 0.0
    }

    val target = NavigationCamera.target(
        CameraInput(
            mode = mode,
            center = center,
            headingDegrees = headingForCamera,
            speedMps = speed,
            viewportHeight = controller.viewportHeightDp,
            sheetHeightPx = controller.sheetHeightDp,
            currentBearing = controller.currentBearing,
            routeBearingDegrees = state.routeBearingDegrees,
        ),
    ) ?: return

    controller.applyCameraTarget(target)
}
