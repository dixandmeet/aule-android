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
 * Le zoom à partir duquel l'inclinaison est pleinement autorisée.
 *
 * Au-dessus on est à l'échelle de la rue : l'inclinaison donne de la
 * profondeur et montre ce qui vient. En dessous, elle n'apporte plus rien —
 * à hauteur de quartier, un plan incliné écrase le haut de l'écran, entasse
 * les arrêts lointains sur trois lignes de pixels et rend les distances
 * illisibles.
 *
 * Le couple avec [PITCH_FLAT_ZOOM] **encadre le seuil des bâtiments** : la
 * couche `building-3d` des deux styles a un `minzoom` de 15. Sous ce
 * niveau, il n'y a plus un seul volume à regarder ; l'inclinaison
 * n'incline plus qu'un plan.
 */
const val PITCH_FULL_ZOOM = 16.0

/**
 * Le zoom sous lequel la carte est franchement à plat.
 *
 * L'écart avec [PITCH_FULL_ZOOM] n'est pas un détail de réglage : c'est la
 * longueur de la rampe. Trop courte, la carte se redresse d'un coup au
 * milieu d'un pincement ; trop longue, on garde une inclinaison résiduelle
 * jusqu'à l'échelle de l'agglomération. Deux niveaux de zoom, c'est un
 * pincement franc : assez pour qu'on voie la carte se coucher, trop pour
 * qu'un ajustement au doigt la fasse basculer par accident.
 */
const val PITCH_FLAT_ZOOM = 14.0

/**
 * L'écart d'inclinaison sous lequel on laisse la carte tranquille.
 *
 * Sans cette marge, la rampe écrirait la caméra à chaque image d'un
 * pincement pour un quart de degré, et le rattrapage de fin de geste se
 * relancerait sur son propre résultat.
 */
const val PITCH_STEP_EPSILON = 0.25

/**
 * Ce que la caméra doit faire de son inclinaison, maintenant.
 *
 * [owedPitch] est le cœur du va-et-vient : l'inclinaison qu'on a retirée
 * en prenant de la hauteur et qu'on doit rendre en redescendant. Sans
 * cette mémoire, la carte saurait se coucher mais pas se relever — et
 * rezoomer laisserait un plan à plat à l'échelle de la rue.
 */
data class PitchDecision(
    /** L'inclinaison à écrire, ou `null` s'il n'y a rien à changer. */
    val pitch: Double?,
    /** Ce qu'il reste dû, ou `null` quand la dette est soldée. */
    val owedPitch: Double?,
)

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
        val pitch = min(
            profile.minPitch + (profile.maxPitch - profile.minPitch) * pace,
            maxPitchForZoom(zoom),
        )

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
     * L'inclinaison qu'on s'autorise à ce niveau de zoom.
     *
     * C'est la règle qui **redresse la carte quand on prend de la hauteur**.
     * Elle vaut pour les modes pilotés comme pour l'exploration libre : le
     * seul endroit où l'on décide de la 3D, c'est ici — le contrôleur ne
     * fait qu'appliquer.
     *
     * La rampe est linéaire et sans hystérésis : le doigt écarte, la carte
     * se couche ; le doigt pince, elle se relève, sans palier ni saut.
     */
    fun maxPitchForZoom(zoom: Double): Double {
        // Un zoom non fini ne devrait pas exister ; s'il arrive, la vue à
        // plat est le repli qui reste lisible.
        if (!zoom.isFinite()) return 0.0
        if (zoom >= PITCH_FULL_ZOOM) return MAX_PITCH
        if (zoom <= PITCH_FLAT_ZOOM) return 0.0
        val climb = (zoom - PITCH_FLAT_ZOOM) / (PITCH_FULL_ZOOM - PITCH_FLAT_ZOOM)
        return MAX_PITCH * climb
    }

    /**
     * L'inclinaison à écrire maintenant, et ce qu'il restera dû.
     *
     * Le contrat tient en une phrase : **on rend exactement ce qu'on a
     * pris**. En prenant de la hauteur, l'inclinaison passe sous le plafond
     * de [maxPitchForZoom] et la valeur retirée est mise de côté ; en
     * redescendant, elle revient au même rythme, jusqu'à solde.
     *
     * Ce n'est pas la même chose que « le zoom pilote l'inclinaison ». Une
     * carte posée à plat par une recherche d'adresse n'a rien retiré à
     * personne : [owedPitch] y vaut `null`, et zoomer dessus la laisse
     * plate. Sans cette distinction, un simple zoom relèverait une caméra
     * que le produit avait délibérément couchée.
     *
     * @param currentPitch l'inclinaison réelle de la caméra, en degrés.
     * @param owedPitch ce qui reste dû d'un redressement précédent.
     */
    fun pitchForZoom(currentPitch: Double, zoom: Double, owedPitch: Double?): PitchDecision {
        val ceiling = maxPitchForZoom(zoom)

        // On couche la carte : ce qu'on retire, on le note avant de le
        // retirer — c'est la seule occasion de le connaître.
        if (currentPitch > ceiling + PITCH_STEP_EPSILON) {
            return PitchDecision(pitch = ceiling, owedPitch = owedPitch ?: currentPitch)
        }

        val owed = owedPitch ?: return PitchDecision(pitch = null, owedPitch = null)
        val wanted = min(owed, ceiling)

        // Une dette soldée s'éteint. La garder ferait ressurgir, au premier
        // rezoom venu, une inclinaison prise dix minutes plus tôt.
        val settled = wanted >= owed - PITCH_STEP_EPSILON
        if (wanted <= currentPitch + PITCH_STEP_EPSILON) {
            val reached = currentPitch >= owed - PITCH_STEP_EPSILON
            return PitchDecision(pitch = null, owedPitch = if (reached) null else owed)
        }
        return PitchDecision(pitch = wanted, owedPitch = if (settled) null else owed)
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
