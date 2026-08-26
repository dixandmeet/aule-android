package io.aule.android.feature.map

import android.os.SystemClock
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.location.HeadingFusion
import io.aule.android.core.location.LocationProvider
import io.aule.android.core.map.MapController
import io.aule.android.core.map.MapZoom
import io.aule.android.core.map.camera.BuildingEmphasis
import io.aule.android.core.map.camera.CameraDynamics
import io.aule.android.core.map.camera.CameraInput
import io.aule.android.core.map.camera.CameraMode
import io.aule.android.core.map.camera.CRUISE_SPEED_MPS
import io.aule.android.core.map.camera.CameraProfile
import io.aule.android.core.map.camera.NavigationCamera
import io.aule.android.core.map.camera.TravelStyle
import io.aule.android.core.map.layer.UserPuckLayer
import io.aule.android.core.map.layer.VehiclesLayer
import io.aule.android.core.model.LegMode

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

    /**
     * La dernière pose sur laquelle la caméra s'est posée en suivant un véhicule.
     *
     * C'est la mémoire qui empêche le suivi de **retomber sur l'utilisateur**.
     * Un instantané peut ne pas contenir le véhicule qu'on suit — il sort du
     * rayon interrogé, le flux le saute une fois, le serveur bafouille — et le
     * cadrage n'a alors plus rien à viser. Sans cette mémoire, la caméra
     * repartait sur le puck : on suivait un bus, et sans rien avoir demandé on
     * se retrouvait à se regarder soi-même.
     */
    var lastVehiclePose: VehiclesLayer.Pose? = null

    /**
     * Comment on se déplace, quand un guidage tourne. `null` sinon.
     *
     * C'est **le** signal du guidage pour la caméra : sa présence dit qu'un
     * trajet est engagé — donc que les bâtiments reculent et que
     * l'orientation se reprend d'elle-même —, et sa valeur dit lequel des
     * trois cadres de navigation s'applique.
     */
    var travel: TravelStyle? = null

    /**
     * La distance à la prochaine manœuvre, en mètres. `null` quand il n'y en
     * a pas à portée — une jambe en transport, une ligne droite, hors guidage.
     */
    var maneuverMeters: Double? = null

    /**
     * L'allure et l'imminence, lissées. Voir [CameraDynamics].
     *
     * Elle vit ici et non dans le contrôleur parce qu'elle appartient à la
     * **session de suivi** : elle se remet à zéro avec elle, et le contrôleur
     * de carte n'a pas à connaître la notion de guidage.
     */
    val dynamics = CameraDynamics()

    /**
     * D'où l'utilisateur se tourne — la direction du cône du puck.
     *
     * Elle vit ici pour la même raison que [dynamics] : c'est un lissage, il
     * appartient à la **session de suivi** et se remet à zéro avec elle. Et
     * elle n'appartient pas au fournisseur de position, qui n'a pas à savoir
     * qu'une carte dessine un cône.
     */
    val facing = HeadingFusion()

    /** L'horloge du dernier battement, pour donner un pas de temps réel. */
    var lastTickElapsed: Long = 0L

    fun forgetVehicle() {
        selectedVehicleId = null
        selectedVehicleSpeed = 0.0
        lastVehiclePose = null
    }

    /**
     * Le guidage s'arrête : on rend le cadrage, sans traîner l'allure du
     * précédent dans le suivant.
     */
    fun forgetGuidance() {
        travel = null
        maneuverMeters = null
        routeBearingDegrees = null
        dynamics.reset()
    }
}

internal const val CAMERA_TICK_MS = 66L

/**
 * Le délai au bout duquel un guidage reprend l'orientation, après un geste.
 *
 * Regarder ailleurs pendant une navigation est légitime — vérifier la suite
 * du trajet, chercher une place — et rien ne doit l'interrompre. Mais
 * personne ne pense à rappuyer sur « Recentrer » au moment où la route
 * redevient intéressante, et une carte restée figée sur un carrefour qu'on
 * a dépassé depuis deux minutes est pire qu'une carte qui reprend la main.
 *
 * Huit secondes : assez pour lire, trop peu pour oublier. Le compteur
 * repart à chaque geste, donc consulter longuement ne déclenche rien tant
 * que le doigt travaille — et il part de la libération de la caméra, ce qui
 * laisse atterrir un vol qu'on vient de demander.
 *
 * ⚠️ **Seulement pendant un guidage.** En exploration, la carte reste où on
 * l'a laissée : on y consulte un arrêt, une ligne, un quartier, et se la
 * voir reprendre serait un vol pur et simple.
 */
internal const val ORIENTATION_RESUME_MS = 8_000L

internal fun tickCamera(
    controller: MapController,
    puck: UserPuckLayer,
    vehicles: VehiclesLayer,
    location: LocationProvider,
    state: CameraFollowState,
) {
    val fix = location.lastFix.value
    // ⚠️ **Le pas de temps se prend en tête, une fois pour toutes.** Il était
    // lu plus bas, après trois `return` possibles : les battements qui
    // sortaient tôt — le cadrage d'ouverture, un véhicule suivi qu'un
    // instantané ne contient pas — ne remettaient pas l'horloge, et le
    // battement suivant héritait d'un pas plusieurs fois trop grand. Deux
    // lisseurs le lisent maintenant, ce qui rendait le défaut visible.
    val step = state.step()

    // **Trois consommateurs du cap, trois comportements.**
    //
    // Le **marqueur** dit « je vais par là » : sous 0,7 m/s le cap de route
    // est gelé, le dernier connu n'est plus une information mais un souvenir,
    // et la flèche retombe au disque.
    //
    // Le **cône** dit « je regarde par là », et c'est une autre question — on
    // se la pose surtout à l'arrêt. Il mélange donc la boussole au cap de
    // route, en donnant progressivement raison à la route à mesure qu'on
    // prend de la vitesse.
    //
    // La **caméra**, elle, conserve le dernier cap de route : le remettre à
    // zéro ferait pivoter la carte au nord à chaque feu rouge. Et elle ne
    // touche pas à la boussole — une carte qu'on ferait tourner sur place
    // parce qu'on a bougé le poignet est inutilisable.
    val movementHeading = if (fix == null || fix.isHeadingFrozen) null else fix.stabilizedHeading
    puck.update(
        fix = fix,
        movementHeading = movementHeading,
        facingHeading = state.facing.advance(
            compass = location.deviceHeadingDegrees,
            course = movementHeading,
            speedMps = fix?.speedMetersPerSecond ?: 0.0,
            stepSeconds = step,
        ),
    )

    // Le premier point GPS amène la carte sur l'utilisateur, une seule
    // fois. Le refaire à chaque position reprendrait la main sur quelqu'un
    // en train d'explorer.
    //
    // ⚠️ **Et le cadrage de suivi attend le battement suivant.** Écrit dans
    // la même trame que ce `moveTo`, il part en animation sur une caméra que
    // le moteur vient de déplacer sèchement : MapLibre annule l'animation, et
    // comme le cadrage se souvient de l'avoir demandée, aucun battement ne
    // réessaie — le sujet restait alors au centre de l'écran, sans décalage
    // avant, jusqu'au premier changement de mode. Soixante-six millisecondes
    // de retard suffisent à séparer les deux, et personne ne les voit.
    if (!state.hasCenteredOnUser && fix != null && fix.isUsable) {
        state.hasCenteredOnUser = true
        controller.moveTo(
            center = fix.coordinate,
            zoom = MapZoom.OPENING,
            pitch = MapZoom.PITCH_3D,
            bearing = 0.0,
        )
        return
    }

    val guiding = state.travel != null
    // Le geste rend la carte sans rien annuler ; le guidage, lui, la
    // reprend au bout d'un moment. Avant le cadrage, parce que reprendre
    // le mode change le cadre qu'on est sur le point de calculer.
    if (guiding &&
        controller.cameraMode.value == CameraMode.FREE_EXPLORE &&
        controller.elapsedSinceCameraFreedMs >= ORIENTATION_RESUME_MS
    ) {
        controller.setCameraMode(CameraMode.NAVIGATION)
    }

    val mode = controller.cameraMode.value
    val travel = state.travel ?: TravelStyle.DRIVE
    val center: Coordinate
    val headingForCamera: Double?
    val speed: Double

    if (mode == CameraMode.FOLLOW_VEHICLE) {
        val id = state.selectedVehicleId ?: return
        // Trois sources, de la plus vivante à la plus ancienne : la pose
        // interpolée qu'on dessine, la position brute du dernier instantané,
        // puis la dernière pose tenue. Le suivi ne lâche donc jamais le
        // véhicule ; au pire, il l'attend là où il l'a laissé.
        val pose = vehicles.displayedCoordinate(id)
            ?: vehicles.vehicle(id)?.let { VehiclesLayer.Pose(it.coordinate, it.headingDegrees) }
            ?: state.lastVehiclePose
            ?: return
        state.lastVehiclePose = pose
        center = pose.coordinate
        headingForCamera = pose.heading
        speed = state.selectedVehicleSpeed
    } else {
        center = puck.displayedCoordinate ?: return
        headingForCamera = fix?.stabilizedHeading
        speed = fix?.speedMetersPerSecond ?: 0.0
    }

    // Le profil du mode courant, ou celui de la navigation quand la caméra
    // est libre : il faut une vitesse de croisière de référence pour que
    // l'allure continue de se lisser pendant qu'on lit la carte, sans quoi
    // « Recentrer » repartirait d'une allure d'arrêt sur une voie rapide.
    val profile = CameraProfile.of(mode, travel)
        ?: CameraProfile.of(CameraMode.NAVIGATION, travel)
    val drive = state.dynamics.advance(
        speedMps = speed,
        // Le repli n'arrive pas — la navigation a toujours un profil — mais
        // une croisière nulle ferait lire toute vitesse comme « lancé », et
        // c'est le genre de repli qu'on ne veut pas découvrir sur la route.
        cruiseSpeedMps = profile?.cruiseSpeedMps ?: CRUISE_SPEED_MPS,
        // Une manœuvre ne cadre que ce qu'on est en train de conduire. En
        // suivi de véhicule ou en repérage, le carrefour du trajet n'a pas à
        // reprendre la caméra.
        maneuverMeters = state.maneuverMeters.takeIf { mode == CameraMode.NAVIGATION },
        travel = travel,
        stepSeconds = step,
    )

    // Les volumes reculent tant qu'un trajet est engagé — **geste ou pas** :
    // un doigt posé sur la carte ne suspend pas la navigation, et rendre la
    // ville pleine à cet instant masquerait la route qu'on vérifie.
    controller.setBuildingEmphasis(
        BuildingEmphasis.of(
            guiding = guiding,
            followingVehicle = mode == CameraMode.FOLLOW_VEHICLE,
            maneuverFocus = drive.maneuverFocus,
        ),
    )

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
            travel = travel,
            pace = drive.pace,
            maneuverFocus = drive.maneuverFocus,
        ),
    ) ?: return

    controller.applyCameraTarget(target)
}

/**
 * Le temps écoulé depuis le battement précédent, en secondes.
 *
 * Le lissage raisonne en temps et non en nombre d'images : c'est ce qui
 * fait que la caméra se comporte pareil quand le ticker prend du retard —
 * et il en prend, à chaque recomposition lourde.
 */
private fun CameraFollowState.step(): Double {
    val now = SystemClock.elapsedRealtime()
    val previous = lastTickElapsed
    lastTickElapsed = now
    if (previous == 0L) return 0.0
    return (now - previous) / 1_000.0
}

/**
 * Le style de déplacement d'une jambe d'itinéraire.
 *
 * C'est la seule traduction entre le vocabulaire du trajet — qui parle de
 * marche, de voiture et de transport — et celui de la caméra, qui parle de
 * distances et de vitesses. La faire ici plutôt que dans le module de
 * caméra garde ce dernier ignorant du domaine.
 */
internal fun travelStyleOf(mode: LegMode): TravelStyle = when (mode) {
    LegMode.WALK -> TravelStyle.WALK
    LegMode.CAR -> TravelStyle.DRIVE
    LegMode.TRANSIT -> TravelStyle.TRANSIT
}
