package io.aule.android.core.map.camera

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.location.HEADING_MIN_SPEED_MPS
import kotlin.math.max
import kotlin.math.min

/**
 * Vitesse de croisière de référence : au-delà, le cadrage ne change plus.
 *
 * 18 m/s ≈ 65 km/h. C'est la vitesse au-delà de laquelle un bus urbain ne
 * va plus vraiment plus vite, et reculer davantage ne montrerait que des
 * toits.
 */
const val CRUISE_SPEED_MPS = 18.0

/**
 * Le plafond d'inclinaison qu'on s'autorise à demander.
 *
 * MapLibre Android plafonne à 60° dans son cœur
 * (`MapLibreConstants.MAXIMUM_PITCH`) et refuse sans lever. On demande
 * quand même 67° — la valeur iOS — et l'écrêtage se fait à l'application,
 * avec la valeur réellement obtenue. Conséquence produit : le profil
 * `navigation` glisse de 58,5° à 60° au lieu de 58,5° → 67°, soit une
 * rampe presque plate ; le sentiment de vitesse repose donc sur le zoom.
 */
const val MAX_PITCH = 67.0

/**
 * Ce que la caméra est en train de faire.
 *
 * Ces cinq modes sont le **seul** endroit où se règle le cadrage. Un
 * sixième se sert en ajoutant une entrée, pas en touchant à la fonction.
 */
enum class CameraMode {
    /** Le trajet entier tient à l'écran. Aucun suivi. */
    OVERVIEW,

    /** On suit l'utilisateur, carte au nord. Un état de repérage, pas de navigation. */
    FOLLOW,

    /** On suit l'utilisateur, carte orientée dans le sens de la marche, inclinée. */
    NAVIGATION,

    /** L'utilisateur a pris la carte en main. Rien n'est annulé — on attend « Recentrer ». */
    FREE_EXPLORE,

    /** On suit un véhicule qu'on a touché du doigt. */
    FOLLOW_VEHICLE,
    ;

    val followsSomething: Boolean
        get() = this == FOLLOW || this == NAVIGATION || this == FOLLOW_VEHICLE

    /**
     * Vrai quand la carte tourne avec le déplacement. [FOLLOW] y échappe
     * volontairement : on cherche à se situer, et une carte au nord se lit
     * mieux pour ça.
     */
    val orientsToHeading: Boolean
        get() = this == NAVIGATION || this == FOLLOW_VEHICLE
}

/**
 * Le cadrage à demander : ce que la caméra doit valoir maintenant.
 *
 * Les longueurs ([forwardOffsetPx]) sont en **dp**, l'équivalent des
 * points iOS. Le contrôleur les convertit en pixels physiques au moment
 * d'écrire dans MapLibre.
 */
data class CameraTarget(
    val center: Coordinate,
    val bearing: Double,
    val pitch: Double,
    val zoom: Double,
    /**
     * De combien de dp le sujet descend sous le centre de la bande visible.
     *
     * MapLibre n'a pas d'`offset` de caméra ; le driver le traduit en
     * marge de contenu.
     */
    val forwardOffsetPx: Double,
)

/** Ce qu'un mode demande à la caméra. */
data class CameraProfile(
    val minZoom: Double,
    val maxZoom: Double,
    val minPitch: Double,
    val maxPitch: Double,
    val forwardOffsetRatio: Double,
    val minOffsetPx: Double,
    val maxOffsetPx: Double,
    val orientToHeading: Boolean,
) {
    companion object {
        fun of(mode: CameraMode): CameraProfile? = when (mode) {
            CameraMode.FREE_EXPLORE, CameraMode.OVERVIEW -> null
            CameraMode.FOLLOW -> CameraProfile(
                minZoom = 16.8, maxZoom = 17.4,
                minPitch = 55.0, maxPitch = 55.0,
                forwardOffsetRatio = 0.10, minOffsetPx = 48.0, maxOffsetPx = 100.0,
                orientToHeading = false,
            )
            CameraMode.NAVIGATION -> CameraProfile(
                minZoom = 17.3, maxZoom = 18.0,
                minPitch = 58.5, maxPitch = MAX_PITCH,
                forwardOffsetRatio = 0.30, minOffsetPx = 72.0, maxOffsetPx = 260.0,
                orientToHeading = true,
            )
            CameraMode.FOLLOW_VEHICLE -> CameraProfile(
                minZoom = 17.9, maxZoom = 18.45,
                minPitch = 58.5, maxPitch = MAX_PITCH,
                forwardOffsetRatio = 0.12, minOffsetPx = 56.0, maxOffsetPx = 120.0,
                orientToHeading = true,
            )
        }
    }
}

/** Ce que la caméra sait de la situation au moment de décider. */
data class CameraInput(
    val mode: CameraMode,
    val center: Coordinate,
    /** Cap mesuré, déjà lissé. `null` à l'arrêt — le GPS n'en donne pas sous ~0,7 m/s. */
    val headingDegrees: Double?,
    val speedMps: Double,
    /** Hauteur totale de la carte, en dp. */
    val viewportHeight: Double,
    /** Hauteur du volet qui masque le bas, en dp. */
    val sheetHeightPx: Double,
    /** Cap de la carte en ce moment, pour ne pas repartir de zéro. */
    val currentBearing: Double,
    /** Cap du segment d'itinéraire sous les pieds, quand une navigation est engagée. */
    val routeBearingDegrees: Double? = null,
)

/**
 * Le cadrage à demander, ou `null` si le mode ne pilote pas la caméra.
 *
 * Fonction **pure** : aucune carte, aucune plateforme. C'est précisément
 * ce qui permet de vérifier un contrat visuel que personne ne saurait
 * juger en relisant du code — une inclinaison ou un zoom ne se relisent
 * pas, ils se mesurent.
 *
 * Les trois décisions, et pourquoi elles ne dépendent pas du mode :
 *
 * **Le cap est celui de la marche.** On regarde dans le sens du
 * déplacement, comme un GPS. Une carte au nord obligerait à retourner
 * mentalement la rue à chaque virage.
 *
 * **Le zoom recule avec la vitesse.** À l'arrêt on veut le quai, lancé
 * on veut la suite de l'itinéraire. L'écart reste petit : au-delà, la
 * carte respirerait à chaque feu rouge.
 *
 * **Le sujet n'est pas au centre.** Il descend dans le tiers bas, ce qui
 * laisse la route à venir occuper le haut de l'écran. Le décalage se
 * calcule sur la **bande réellement visible**, volet déduit : sinon un
 * volet à mi-hauteur repousse le puck derrière lui.
 */
object NavigationCamera {

    fun target(input: CameraInput): CameraTarget? {
        val profile = CameraProfile.of(input.mode) ?: return null

        // 0 à l'arrêt, 1 à vitesse de croisière. Tout le reste en découle.
        val pace = min(max(input.speedMps, 0.0) / CRUISE_SPEED_MPS, 1.0)

        val zoom = profile.maxZoom + (profile.minZoom - profile.maxZoom) * pace
        val pitch = min(profile.minPitch + (profile.maxPitch - profile.minPitch) * pace, MAX_PITCH)

        val visibleHeight = max(input.viewportHeight - input.sheetHeightPx, 0.0)
        val offset = min(
            max(visibleHeight * profile.forwardOffsetRatio, profile.minOffsetPx),
            profile.maxOffsetPx,
        )

        return CameraTarget(
            center = input.center,
            bearing = if (profile.orientToHeading) bearing(input) else 0.0,
            pitch = pitch,
            zoom = zoom,
            forwardOffsetPx = offset,
        )
    }

    /**
     * Le cap à adopter, par ordre de fiabilité décroissante.
     *
     * Le repli sur le cap de l'itinéraire n'est pas théorique : on démarre
     * une navigation **à l'arrêt**, là où le GPS ne donne aucun cap. Sans
     * lui, la première seconde de guidage se joue dans une direction
     * arbitraire.
     */
    fun bearing(input: CameraInput): Double {
        val heading = input.headingDegrees
        if (heading != null && input.speedMps >= HEADING_MIN_SPEED_MPS) {
            return GeoMath.normalizeHeading(heading)
        }
        val routeBearing = input.routeBearingDegrees
        if (routeBearing != null) {
            return GeoMath.normalizeHeading(routeBearing)
        }
        return GeoMath.normalizeHeading(input.currentBearing)
    }
}
