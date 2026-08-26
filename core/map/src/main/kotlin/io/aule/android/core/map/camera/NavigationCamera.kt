package io.aule.android.core.map.camera

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.location.HEADING_MIN_SPEED_MPS
import kotlin.math.max
import kotlin.math.min

/**
 * Vitesse de croisière de référence, sur route : au-delà, le cadrage ne
 * change plus.
 *
 * 18 m/s ≈ 65 km/h. C'est la vitesse au-delà de laquelle un bus urbain ne
 * va plus vraiment plus vite, et reculer davantage ne montrerait que des
 * toits.
 *
 * ⚠️ **Ce n'est plus la seule.** Chaque profil porte la sienne
 * ([CameraProfile.cruiseSpeedMps]) : rapportée à 18 m/s, la marche vaut
 * huit pour cent d'allure et le cadrage piéton ne quittait jamais son cran
 * d'arrêt — la caméra ne savait littéralement pas qu'on marchait.
 */
const val CRUISE_SPEED_MPS = 18.0

/** L'allure de croisière d'un piéton : 2,2 m/s, soit un pas rapide. */
const val WALK_CRUISE_SPEED_MPS = 2.2

/** Celle d'un trajet en transport : 14 m/s ≈ 50 km/h, arrêts compris. */
const val TRANSIT_CRUISE_SPEED_MPS = 14.0

/**
 * Le plafond d'inclinaison qu'on s'autorise à demander.
 *
 * MapLibre Android plafonne à 60° dans son cœur
 * (`MapLibreConstants.MAXIMUM_PITCH`) et refuse sans lever. On demande
 * quand même 67° — la valeur iOS — et l'écrêtage se fait à l'application,
 * avec la valeur réellement obtenue.
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
 * couche `building-3d` des deux styles entre à 15 et devient pleine à 15,5.
 * On cale donc le plein droit à l'inclinaison sur le moment où les volumes
 * sont entiers, et non un niveau plus haut : depuis que l'exploration cadre
 * autour de 16,3, un plafond posé à 16 aurait rogné la 3D dès le premier
 * écart de doigt.
 */
const val PITCH_FULL_ZOOM = 15.5

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
const val PITCH_FLAT_ZOOM = 13.5

/**
 * L'écart d'inclinaison sous lequel on laisse la carte tranquille.
 *
 * Sans cette marge, la rampe écrirait la caméra à chaque image d'un
 * pincement pour un quart de degré, et le rattrapage de fin de geste se
 * relancerait sur son propre résultat.
 */
const val PITCH_STEP_EPSILON = 0.25

/**
 * De combien le sujet remonte vers le centre à l'approche d'une manœuvre.
 *
 * Un quart du décalage, pas davantage : le carrefour est **devant**, donc
 * il doit rester dans la moitié haute — mais collé au bord supérieur, on
 * ne voit plus par quelle branche on y entre.
 */
const val MANEUVER_OFFSET_RELIEF = 0.25

/**
 * Comment on se déplace, quand un guidage est engagé.
 *
 * Ce n'est pas une redite de [CameraMode] : le mode dit **ce que la caméra
 * fait**, le style de déplacement dit **à quelle vitesse et à quelle
 * distance le monde arrive**. Un piéton et une voiture partagent le même
 * mode de navigation et ne veulent pas le même cadre — c'est la distinction
 * qui manquait, et elle se voyait : la caméra de navigation était réglée
 * pour 65 km/h, y compris sur un trottoir.
 */
enum class TravelStyle {
    WALK,
    DRIVE,
    TRANSIT,
    ;

    /** La distance à laquelle une manœuvre occupe toute l'attention. */
    val maneuverNearMeters: Double
        get() = when (this) {
            WALK -> 20.0
            DRIVE -> 45.0
            TRANSIT -> 40.0
        }

    /** Celle au-delà de laquelle elle ne compte plus pour le cadrage. */
    val maneuverFarMeters: Double
        get() = when (this) {
            WALK -> 60.0
            DRIVE -> 170.0
            TRANSIT -> 140.0
        }
}

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

    /**
     * L'exploration suivie : on suit l'utilisateur, carte au nord.
     *
     * C'est le mode par défaut, celui du lancement — un état de repérage,
     * pas de navigation. Il cadre donc **large** : plusieurs rues, les
     * carrefours d'à côté, les arrêts, la forme du quartier.
     */
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

/**
 * Comment le cadre suit l'allure — et pourquoi ce n'est pas une pente.
 *
 * On croit d'abord que « plus vite = plus loin » suffit. C'est faux au
 * premier cran : **à l'arrêt, on ne regarde pas ses pieds, on se
 * repère**. Quelqu'un qui s'arrête cherche la rue, le carrefour, l'arrêt
 * d'en face — il lui faut de la largeur. C'est en se mettant à marcher
 * qu'il veut le détail immédiat : le trottoir, l'entrée, le passage
 * piéton. Puis, à mesure qu'il accélère, ce qui compte repasse devant lui
 * et le cadre recule pour de bon.
 *
 * La courbe est donc en U : large au repos, resserrée à allure lente,
 * puis de plus en plus large. Les quatre ancres sont celles de
 * [SpeedGear] — c'est le même vocabulaire des deux côtés, et une allure
 * lissée qui passe entre deux crans donne un zoom qui passe entre deux
 * ancres.
 */
data class ZoomCurve(
    /** Immobile : le cadre de repérage, celui du lancement. */
    val rest: Double,
    /** Au pas : le seul moment où l'on se rapproche. */
    val close: Double,
    /** Allure courante : le cadre intermédiaire. */
    val cruise: Double,
    /** Lancé : on montre ce qui arrive, pas ce qu'on quitte. */
    val far: Double,
) {
    val widest: Double get() = minOf(rest, close, cruise, far)
    val tightest: Double get() = maxOf(rest, close, cruise, far)

    /** Le zoom d'une allure, interpolé entre les deux crans qui l'encadrent. */
    fun at(pace: Double): Double {
        val p = pace.coerceIn(0.0, 1.0)
        val gears = SpeedGear.entries
        for (index in 0 until gears.size - 1) {
            val low = gears[index]
            val high = gears[index + 1]
            if (p > high.pace) continue
            val span = high.pace - low.pace
            val t = if (span <= 0.0) 0.0 else (p - low.pace) / span
            return of(low) + (of(high) - of(low)) * t
        }
        return far
    }

    private fun of(gear: SpeedGear): Double = when (gear) {
        SpeedGear.STILL -> rest
        SpeedGear.SLOW -> close
        SpeedGear.CRUISE -> cruise
        SpeedGear.FAST -> far
    }
}

/**
 * Ce qu'un mode demande à la caméra.
 *
 * Le zoom vient de [zoom], une courbe et non deux bornes : le cadre le
 * plus serré n'est ni à l'arrêt ni à pleine vitesse, mais au pas. Voir
 * [ZoomCurve].
 */
data class CameraProfile(
    val zoom: ZoomCurve,
    val minPitch: Double,
    val maxPitch: Double,
    val forwardOffsetRatio: Double,
    val minOffsetPx: Double,
    val maxOffsetPx: Double,
    val orientToHeading: Boolean,
    /** La vitesse à laquelle ce profil considère qu'on est lancé. */
    val cruiseSpeedMps: Double = CRUISE_SPEED_MPS,
    /** Ce que le cadre gagne en zoom quand une manœuvre est imminente. */
    val maneuverZoomBoost: Double = 0.0,
    /** Ce qu'il rend en inclinaison au même moment, pour lire le carrefour. */
    val maneuverPitchRelief: Double = 0.0,
) {

    /** Le cadre le plus large que ce profil demande, manœuvre non comprise. */
    val minZoom: Double get() = zoom.widest

    /** Le plus serré, aux mêmes conditions. */
    val maxZoom: Double get() = zoom.tightest

    companion object {

        /**
         * Le profil d'un mode, affiné par le style de déplacement.
         *
         * Les modes libres n'en ont pas : personne ne pilote la caméra, et
         * rendre un profil reviendrait à la reprendre à l'utilisateur.
         *
         * Le [travel] ne compte **que** pour la navigation. Ailleurs il est
         * ignoré : on ne suit pas un bus différemment selon qu'on l'attend
         * à pied ou en voiture.
         */
        fun of(mode: CameraMode, travel: TravelStyle = TravelStyle.DRIVE): CameraProfile? =
            when (mode) {
                CameraMode.FREE_EXPLORE, CameraMode.OVERVIEW -> null

                // Exploration : le cadre du lancement. Il recule d'un demi-niveau
                // sur ce qu'il valait — la zone visible gagne environ 40 % de
                // large — et il se couche moins : à 55° les façades proches
                // faisaient des murs, et la rue d'à côté passait derrière.
                //
                // Sa croisière est celle d'un transport et non d'une voiture :
                // on explore la carte à pied ou dans un bus, et rapportée à
                // 65 km/h la marche ne se distinguait pas de l'arrêt.
                CameraMode.FOLLOW -> CameraProfile(
                    zoom = ZoomCurve(rest = 16.5, close = 16.8, cruise = 16.4, far = 16.0),
                    minPitch = 48.0, maxPitch = 52.0,
                    forwardOffsetRatio = 0.12, minOffsetPx = 40.0, maxOffsetPx = 130.0,
                    orientToHeading = false,
                    cruiseSpeedMps = TRANSIT_CRUISE_SPEED_MPS,
                )

                CameraMode.NAVIGATION -> when (travel) {
                    // À pied on avance à 1,4 m/s : ce qui compte tient dans les
                    // cinquante mètres, et le prochain angle de rue est une
                    // information, pas un détail. On reste donc plus près, et
                    // moins couché — un piéton lit un plan, il ne conduit pas.
                    TravelStyle.WALK -> CameraProfile(
                        zoom = ZoomCurve(rest = 17.0, close = 17.3, cruise = 17.1, far = 16.9),
                        minPitch = 46.0, maxPitch = 52.0,
                        forwardOffsetRatio = 0.15, minOffsetPx = 56.0, maxOffsetPx = 150.0,
                        orientToHeading = true,
                        cruiseSpeedMps = WALK_CRUISE_SPEED_MPS,
                        maneuverZoomBoost = 0.7,
                        maneuverPitchRelief = 8.0,
                    )

                    // En voiture, la caméra descend : vue rasante, sujet dans le
                    // tiers bas, route devant. C'est le cadrage d'un GPS, et il
                    // n'est bon que là — partout ailleurs il coupe l'horizon.
                    TravelStyle.DRIVE -> CameraProfile(
                        zoom = ZoomCurve(rest = 16.9, close = 17.1, cruise = 16.6, far = 16.2),
                        minPitch = 55.0, maxPitch = MAX_PITCH,
                        forwardOffsetRatio = 0.20, minOffsetPx = 72.0, maxOffsetPx = 210.0,
                        orientToHeading = true,
                        maneuverZoomBoost = 0.8,
                        maneuverPitchRelief = 10.0,
                    )

                    // En transport on est passager : on ne conduit rien, on
                    // surveille les arrêts qui viennent. Le cadre est celui de
                    // la voiture, relevé et reculé.
                    TravelStyle.TRANSIT -> CameraProfile(
                        zoom = ZoomCurve(rest = 16.7, close = 16.9, cruise = 16.5, far = 16.1),
                        minPitch = 50.0, maxPitch = 56.0,
                        forwardOffsetRatio = 0.15, minOffsetPx = 56.0, maxOffsetPx = 160.0,
                        orientToHeading = true,
                        cruiseSpeedMps = TRANSIT_CRUISE_SPEED_MPS,
                        maneuverZoomBoost = 0.5,
                        maneuverPitchRelief = 6.0,
                    )
                }

                // Suivre un véhicule, c'est le regarder rouler : on reste
                // au-dessus de lui, sans le coller. Reculé d'un niveau entier
                // sur ce qu'il valait — à 18,45 on ne voyait plus que le bus et
                // le toit d'en face, jamais la rue qu'il prenait.
                CameraMode.FOLLOW_VEHICLE -> CameraProfile(
                    zoom = ZoomCurve(rest = 17.1, close = 17.4, cruise = 17.1, far = 16.9),
                    minPitch = 52.0, maxPitch = 58.0,
                    forwardOffsetRatio = 0.12, minOffsetPx = 48.0, maxOffsetPx = 120.0,
                    orientToHeading = true,
                    cruiseSpeedMps = TRANSIT_CRUISE_SPEED_MPS,
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
    /** Comment on se déplace : cela change le cadre de la navigation. */
    val travel: TravelStyle = TravelStyle.DRIVE,
    /**
     * L'allure, de 0 (arrêté) à 1 (lancé), **déjà quantifiée et lissée**.
     *
     * Elle ne se déduit pas de [speedMps] ici : une allure brute ferait
     * respirer le zoom à chaque oscillation du GPS. Le travail est fait par
     * [CameraDynamics], qui a la mémoire qu'une fonction pure n'a pas.
     */
    val pace: Double = 0.0,
    /** L'imminence de la prochaine manœuvre, de 0 (loin) à 1 (au carrefour). */
    val maneuverFocus: Double = 0.0,
)

/**
 * Le cadrage à demander, ou `null` si le mode ne pilote pas la caméra.
 *
 * Fonction **pure** : aucune carte, aucune plateforme. C'est précisément
 * ce qui permet de vérifier un contrat visuel que personne ne saurait
 * juger en relisant du code — une inclinaison ou un zoom ne se relisent
 * pas, ils se mesurent.
 *
 * Les quatre décisions, et pourquoi elles ne dépendent pas du mode :
 *
 * **Le cap est celui de la marche.** On regarde dans le sens du
 * déplacement, comme un GPS. Une carte au nord obligerait à retourner
 * mentalement la rue à chaque virage — sauf en repérage, où c'est
 * justement le nord qui sert de repère.
 *
 * **Le zoom suit l'allure, en U.** Arrêté on se repère et il faut de la
 * largeur ; au pas on veut le trottoir, donc on se rapproche ; lancé on
 * veut la suite de l'itinéraire et le cadre recule pour de bon. L'allure
 * est quantifiée en paliers en amont : sans cela, la carte respirerait à
 * chaque feu rouge.
 *
 * **Le carrefour reprend le cadre.** À l'approche d'une manœuvre, on se
 * rapproche et on se redresse : c'est le seul moment où la géométrie de
 * l'intersection compte plus que la route qui vient après.
 *
 * **Le sujet n'est pas au centre.** Il descend sous le milieu, ce qui
 * laisse la route à venir occuper le haut de l'écran. Le décalage se
 * calcule sur la **bande réellement visible**, volet déduit : sinon un
 * volet à mi-hauteur repousse le puck derrière lui.
 */
object NavigationCamera {

    fun target(input: CameraInput): CameraTarget? {
        val profile = CameraProfile.of(input.mode, input.travel) ?: return null

        val pace = input.pace.coerceIn(0.0, 1.0)
        val focus = input.maneuverFocus.coerceIn(0.0, 1.0)

        val zoom = profile.zoom.at(pace) + profile.maneuverZoomBoost * focus
        val pitch = min(
            profile.minPitch + (profile.maxPitch - profile.minPitch) * pace -
                profile.maneuverPitchRelief * focus,
            maxPitchForZoom(zoom),
        ).coerceAtLeast(0.0)

        val visibleHeight = max(input.viewportHeight - input.sheetHeightPx, 0.0)
        val offset = min(
            max(visibleHeight * profile.forwardOffsetRatio, profile.minOffsetPx),
            profile.maxOffsetPx,
        ) * (1.0 - MANEUVER_OFFSET_RELIEF * focus)

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
     * Le zoom d'un rapprochement **léger** sur un lieu qu'on vient de désigner.
     *
     * Se poser sur un arrêt ne veut pas dire s'y coller : au cran du
     * trottoir, le carrefour remplit l'écran et l'arrêt reste aussi anonyme
     * qu'avant le vol. On garde donc le cadre courant tant qu'il est
     * raisonnable, et on ne le corrige que s'il l'est trop peu — venu de
     * l'échelle de la ville, ou déjà collé au sol.
     */
    fun selectionZoom(currentZoom: Double, minZoom: Double, maxZoom: Double): Double {
        if (!currentZoom.isFinite()) return minZoom
        return currentZoom.coerceIn(minZoom, maxZoom)
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
