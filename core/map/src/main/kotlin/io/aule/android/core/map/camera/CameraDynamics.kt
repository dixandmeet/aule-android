package io.aule.android.core.map.camera

import kotlin.math.exp

/**
 * Les quatre allures que la caméra sait distinguer.
 *
 * Ce ne sont pas des vitesses mais des **intentions de cadrage** : arrêté,
 * on regarde autour de soi ; lancé, on regarde devant. Entre les deux, deux
 * crans suffisent — un troisième ne se verrait pas, et chaque cran de plus
 * est une occasion de plus pour la carte de bouger sans qu'on l'ait
 * demandé.
 *
 * Le rapport à la vitesse est **relatif** à la croisière du profil
 * ([CameraProfile.cruiseSpeedMps]) : 4 km/h est une course à pied et un
 * embouteillage, et les deux ne demandent pas le même cadre.
 */
enum class SpeedGear(val pace: Double) {
    /** Immobile, ou presque : la caméra prend de la hauteur. */
    STILL(0.0),

    /** On avance. Léger rapprochement. */
    SLOW(0.34),

    /** Allure normale : le cadre intermédiaire. */
    CRUISE(0.68),

    /** Lancé : on recule pour montrer ce qui arrive. */
    FAST(1.0),
}

/**
 * Les seuils de montée, en fraction de la vitesse de croisière.
 *
 * Un pour chaque passage : `STILL → SLOW`, `SLOW → CRUISE`,
 * `CRUISE → FAST`.
 */
private val GEAR_RISE = doubleArrayOf(0.10, 0.40, 0.80)

/**
 * Ceux de descente, **volontairement plus bas**.
 *
 * C'est toute l'hystérésis : sans elle, une vitesse qui oscille autour d'un
 * seuil — un GPS urbain le fait en permanence — ferait osciller le cadre
 * avec elle. Il faut donc ralentir franchement pour reprendre le cran du
 * dessous, pas seulement repasser sous la barre qu'on vient de franchir.
 */
private val GEAR_FALL = doubleArrayOf(0.05, 0.30, 0.65)

/**
 * Le temps que met l'allure à rejoindre son palier, en secondes.
 *
 * Les paliers, seuls, feraient sauter le zoom d'un cran entier au moment du
 * franchissement. La constante de temps étale ce saut sur environ une
 * seconde : on voit la carte reculer, on ne la voit pas changer d'échelle.
 */
private const val PACE_TAU_SECONDS = 0.9

/** Le temps de montée du cadrage de carrefour : court, l'intersection arrive. */
private const val FOCUS_RISE_TAU_SECONDS = 0.5

/**
 * Celui de la descente, **deux fois plus long**.
 *
 * Passé le carrefour, la manœuvre suivante est souvent à trois cents
 * mètres : l'imminence retombe d'un coup à zéro. Rendre le cadre au même
 * rythme qu'on l'a pris ferait un dézoom sec juste après le virage —
 * exactement au moment où l'on cherche à se resituer.
 */
private const val FOCUS_FALL_TAU_SECONDS = 1.2

/** Le pas de temps le plus long qu'on accepte d'intégrer d'un coup. */
private const val MAX_STEP_SECONDS = 0.5

/** Ce que les dynamiques rendent au cadrage : deux nombres, tous deux lissés. */
data class CameraDrive(
    /** L'allure, de 0 (arrêté) à 1 (lancé). */
    val pace: Double,
    /** L'imminence de la prochaine manœuvre, de 0 (loin) à 1 (au carrefour). */
    val maneuverFocus: Double,
    /** Le cran d'allure retenu — utile au journal, et aux tests. */
    val gear: SpeedGear,
)

/**
 * La mémoire de la caméra : ce qu'une fonction pure ne peut pas tenir.
 *
 * [NavigationCamera] décide d'un cadre à partir de nombres ; encore
 * faut-il que ces nombres ne sautent pas. C'est le travail d'ici, et il
 * tient en deux idées :
 *
 * - **des paliers plutôt qu'une rampe** : le zoom ne suit pas la vitesse
 *   instantanée, il suit un cran d'allure qui, lui, ne change que quand on
 *   change vraiment d'allure ;
 * - **de l'hystérésis à chaque palier** : on ne redescend pas au seuil où
 *   l'on est monté, sinon un GPS qui hésite fait respirer la carte.
 *
 * L'objet est **délibérément hors de MapLibre** : il n'a ni carte, ni
 * horloge, ni contexte Android. Le pas de temps lui est donné, ce qui rend
 * une seconde de conduite reproductible dans un test.
 */
class CameraDynamics {

    var gear: SpeedGear = SpeedGear.STILL
        private set

    var pace: Double = 0.0
        private set

    var maneuverFocus: Double = 0.0
        private set

    /**
     * Avance d'un pas de temps et rend le cadrage dynamique.
     *
     * @param speedMps la vitesse mesurée, en m/s.
     * @param cruiseSpeedMps celle à laquelle ce profil considère qu'on est lancé.
     * @param maneuverMeters la distance à la prochaine manœuvre, `null` s'il
     *   n'y en a pas — hors guidage, ou sur une jambe en transport.
     * @param travel le style de déplacement, qui fixe la portée d'un carrefour.
     * @param stepSeconds le temps écoulé depuis le dernier appel.
     */
    fun advance(
        speedMps: Double,
        cruiseSpeedMps: Double,
        maneuverMeters: Double?,
        travel: TravelStyle,
        stepSeconds: Double,
    ): CameraDrive {
        val step = when {
            !stepSeconds.isFinite() || stepSeconds <= 0.0 -> 0.0
            // Un écran qui revient de veille, un guidage repris : le pas de
            // temps peut valoir des minutes. L'intégrer tel quel téléporterait
            // le cadre — ce qui est le seul saut visuel qu'on s'interdit.
            else -> stepSeconds.coerceAtMost(MAX_STEP_SECONDS)
        }

        gear = gearFor(gear, ratio(speedMps, cruiseSpeedMps))
        pace = ease(pace, gear.pace, step, PACE_TAU_SECONDS)

        val aim = maneuverAim(maneuverMeters, travel)
        val tau = if (aim > maneuverFocus) FOCUS_RISE_TAU_SECONDS else FOCUS_FALL_TAU_SECONDS
        maneuverFocus = ease(maneuverFocus, aim, step, tau)

        return CameraDrive(pace = pace, maneuverFocus = maneuverFocus, gear = gear)
    }

    /**
     * Repart de l'arrêt, sans lissage.
     *
     * À appeler quand la situation cesse d'être comparable à la précédente —
     * un guidage qui s'arrête, un véhicule qu'on lâche. Continuer à lisser
     * ferait traîner l'allure d'un bus dans le cadrage d'un piéton.
     */
    fun reset() {
        gear = SpeedGear.STILL
        pace = 0.0
        maneuverFocus = 0.0
    }

    private companion object {

        fun ratio(speedMps: Double, cruiseSpeedMps: Double): Double {
            if (!speedMps.isFinite() || speedMps <= 0.0) return 0.0
            if (!cruiseSpeedMps.isFinite() || cruiseSpeedMps <= 0.0) return 1.0
            return speedMps / cruiseSpeedMps
        }

        /**
         * Le cran d'allure, sachant celui d'où l'on vient.
         *
         * On monte tant que le seuil de montée est franchi, on descend tant
         * que celui de descente ne l'est plus : deux boucles, et non un
         * `when` sur la vitesse. C'est ce qui permet de sauter deux crans
         * d'un coup — un premier point GPS à 50 km/h après un arrêt — sans
         * jamais osciller entre deux voisins.
         */
        fun gearFor(current: SpeedGear, ratio: Double): SpeedGear {
            var index = current.ordinal
            while (index < GEAR_RISE.size && ratio >= GEAR_RISE[index]) index++
            while (index > 0 && ratio < GEAR_FALL[index - 1]) index--
            return SpeedGear.entries[index]
        }

        /**
         * L'imminence d'une manœuvre : 1 au carrefour, 0 quand il est loin.
         *
         * La courbe est un `smoothstep` et non une droite : une rampe
         * linéaire démarre et s'arrête net, et ces deux coudes se voient
         * précisément là où la caméra est en train de bouger.
         */
        fun maneuverAim(distanceMeters: Double?, travel: TravelStyle): Double {
            val distance = distanceMeters ?: return 0.0
            if (!distance.isFinite() || distance < 0.0) return 0.0
            val near = travel.maneuverNearMeters
            val far = travel.maneuverFarMeters
            if (distance <= near) return 1.0
            if (distance >= far) return 0.0
            val t = (far - distance) / (far - near)
            return t * t * (3.0 - 2.0 * t)
        }

        /**
         * Un pas de filtre exponentiel, exprimé en **temps** et non en
         * fraction par image.
         *
         * Une fraction fixe par battement lierait la vitesse de la caméra à
         * la cadence du ticker : le même réglage donnerait deux
         * comportements selon qu'on bat à 15 ou à 60 Hz.
         */
        fun ease(current: Double, target: Double, stepSeconds: Double, tauSeconds: Double): Double {
            if (stepSeconds <= 0.0) return current
            val factor = 1.0 - exp(-stepSeconds / tauSeconds)
            return current + (target - current) * factor
        }
    }
}
