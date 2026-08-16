package io.aule.android.core.location

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath

/**
 * Sous cette vitesse, le cap GPS n'est plus un cap : c'est du bruit.
 *
 * Le « heading » d'un GPS est un *course over ground* — il se déduit du
 * déplacement entre deux positions. À l'arrêt il n'y a pas de déplacement,
 * seulement l'erreur de mesure, et le cap se met à tourner tout seul.
 */
const val HEADING_MIN_SPEED_MPS = 0.7

/** En dessous de cette vitesse, on considère l'utilisateur immobile. */
const val ANCHOR_STATIONARY_SPEED_MPS = 0.5

/** Rayon dans lequel un tremblement GPS est absorbé plutôt que suivi. */
const val ANCHOR_RADIUS_METERS = 12.0

/**
 * Lisse le cap avant de le donner à la caméra.
 *
 * Port de `SAE/lib/navigation/heading_stabilizer.dart` et du proto iOS.
 * Sans lissage, un cap brut fait vibrer l'écran à l'arrêt et sursauter la
 * carte en virage. Deux règles :
 *
 * 1. **En dessous de [HEADING_MIN_SPEED_MPS], on gèle** le dernier cap
 *    valable plutôt que d'en adopter un faux. Une carte figée dans la
 *    mauvaise direction se corrige au premier pas ; une carte qui tourne
 *    sur place est inutilisable.
 * 2. **Au-dessus, on converge** vers le cap mesuré, par le plus court
 *    chemin. Le facteur est volontairement lent : la caméra doit suivre la
 *    route, pas les nids-de-poule.
 */
class HeadingStabilizer(
    var smoothing: Double = 0.25,
) {
    var stabilized: Double? = null
        private set

    var isFrozen: Boolean = false
        private set

    fun ingest(course: Double?, speed: Double) {
        if (course == null || course < 0 || speed < HEADING_MIN_SPEED_MPS) {
            isFrozen = true
            return
        }
        isFrozen = false

        val current = stabilized
        if (current == null) {
            stabilized = GeoMath.normalizeHeading(course)
            return
        }
        val delta = GeoMath.shortestHeadingDelta(current, course)
        stabilized = GeoMath.normalizeHeading(current + delta * smoothing)
    }

    fun reset() {
        stabilized = null
        isFrozen = false
    }
}

/**
 * Empêche le puck de danser quand l'utilisateur ne bouge pas.
 *
 * Port de `SAE/lib/navigation/motion_anchor.dart`. À l'arrêt, deux positions
 * successives diffèrent de quelques mètres sans que rien n'ait bougé ;
 * suivre chacune fait glisser le puck en permanence, et pire, fait
 * travailler la caméra pour rien.
 *
 * L'ancre tient donc une position de référence tant qu'on reste dedans, et
 * ne la lâche que lorsque le déplacement devient réel — soit parce qu'on
 * sort du rayon, soit parce que la vitesse dit qu'on marche.
 */
class MotionAnchor {

    private var anchor: Coordinate? = null

    /**
     * Rend la position à afficher : l'ancre tant qu'on n'a pas vraiment
     * bougé, la mesure sinon.
     */
    fun settle(position: Coordinate, speed: Double, accuracy: Double): Coordinate {
        // Une position trop imprécise ne doit pas déplacer l'ancre — c'est
        // exactement le cas en ville dense, entre deux immeubles, où
        // l'erreur atteint la centaine de mètres.
        val current = anchor
        if (accuracy > LocationFix.MAX_USABLE_ACCURACY_METERS && current != null) {
            return current
        }

        if (current == null) {
            anchor = position
            return position
        }

        val moving = speed >= ANCHOR_STATIONARY_SPEED_MPS
        val escaped = GeoMath.distance(current, position) > ANCHOR_RADIUS_METERS
        if (moving || escaped) {
            anchor = position
            return position
        }
        return current
    }

    fun reset() {
        anchor = null
    }
}
