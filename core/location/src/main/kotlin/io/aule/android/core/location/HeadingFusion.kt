package io.aule.android.core.location

import io.aule.android.core.geo.GeoMath
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/**
 * En dessous, le cap de route ne pèse rien : c'est le seuil de
 * [HeadingStabilizer], et sous lui il n'y a pas de route, seulement du bruit.
 */
const val FACING_COURSE_MIN_SPEED_MPS = HEADING_MIN_SPEED_MPS

/**
 * Au-dessus, le cap de route emporte tout.
 *
 * 2,2 m/s, soit huit kilomètres-heure : on ne marche plus, on roule ou on
 * court. C'est aussi le régime où la boussole devient la moins fiable —
 * carrosserie, guidon, bras qui balance — pendant que le cap de route, lui,
 * devient franchement bon.
 */
const val FACING_COURSE_FULL_SPEED_MPS = 2.2

/**
 * Le tremblement qu'on absorbe plutôt que de le suivre, à l'arrêt.
 *
 * Posé sur une table, un téléphone rend un cap qui oscille de deux ou trois
 * degrés — assez pour qu'un cône de quarante-six degrés d'ouverture se mette
 * visiblement à frémir. On ne poursuit donc que ce qui **dépasse** ce seuil,
 * et le cône garde au plus trois degrés de retard : invisible sur cette
 * ouverture, et strictement immobile tant que rien ne tourne.
 *
 * Le seuil s'efface à mesure que le cap de route prend la main : en
 * mouvement, il n'y a plus de tremblement à absorber, et un cap qu'on suit
 * au degré près vaut mieux qu'un cap en retard dans un virage.
 */
const val FACING_DEAD_ZONE_DEGREES = 3.0

/**
 * Le temps que met le cône à faire les deux tiers du chemin vers une
 * nouvelle direction.
 *
 * Un tiers de seconde : assez lent pour qu'un demi-tour se lise comme un
 * pivot et non comme un saut, assez rapide pour que tourner au coin d'une
 * rue soit dit pendant qu'on tourne. Le lissage raisonne en **temps** et non
 * en nombre d'échantillons : la boussole arrive à cinquante hertz, le GPS à
 * un, et un facteur par échantillon donnerait deux vitesses de convergence
 * selon la source.
 */
const val FACING_TIME_CONSTANT_SECONDS = 0.35

/**
 * D'où l'utilisateur se tourne — la direction que montre le cône du puck.
 *
 * Deux sources répondent à la question, et aucune ne suffit seule :
 *
 * - la **boussole** ([DeviceCompass]) sait où pointe le téléphone, y compris
 *   immobile ; mais elle décrit un boîtier, pas une trajectoire, et se laisse
 *   fausser par tout ce qui est métallique ;
 * - le **cap de route** sait où l'on va pour de bon ; mais il n'existe qu'en
 *   mouvement, et il est gelé le reste du temps.
 *
 * On les mélange donc par la vitesse, et non par un basculement : entre
 * [FACING_COURSE_MIN_SPEED_MPS] et [FACING_COURSE_FULL_SPEED_MPS], le poids
 * du cap de route monte le long d'une courbe en S. Un basculement franc ferait
 * sauter le cône d'un coup au premier pas — et ressauter en arrière au feu
 * rouge suivant, puisque marcher, c'est franchir un seuil de vitesse plusieurs
 * fois par minute.
 *
 * Classe **pure** : pas de capteur, pas d'horloge. Le pas de temps est un
 * paramètre, ce qui la rend vérifiable sur la JVM et la fait se comporter
 * pareil quand le ticker prend du retard.
 */
class HeadingFusion {

    /** Le cap affiché, lissé. `null` quand aucune source ne répond. */
    var facing: Double? = null
        private set

    /**
     * Avance d'un pas et rend le cap à montrer.
     *
     * [course] est le cap de route **vivant** : `null` quand il est gelé.
     * C'est ce qui garantit que, sur un appareil sans boussole, on retombe
     * exactement sur l'ancien comportement — pas de cône à l'arrêt plutôt
     * qu'un cône qui pointe là où l'on allait il y a une minute.
     */
    fun advance(
        compass: Double?,
        course: Double?,
        speedMps: Double,
        stepSeconds: Double,
    ): Double? {
        val target = blend(compass, course, speedMps)
        if (target == null) {
            facing = null
            return null
        }

        val current = facing
        if (current == null || stepSeconds <= 0.0) {
            // Première direction connue : on se pose dessus. Converger depuis
            // le nord ferait pivoter le cône au démarrage, sur une valeur
            // qu'on n'a jamais mesurée.
            facing = target
            return target
        }

        val delta = GeoMath.shortestHeadingDelta(current, target)
        val deadZone = FACING_DEAD_ZONE_DEGREES * (1.0 - courseTrust(speedMps))
        val excess = abs(delta) - deadZone
        if (excess <= 0.0) return current

        val alpha = 1.0 - exp(-stepSeconds / FACING_TIME_CONSTANT_SECONDS)
        val next = GeoMath.normalizeHeading(current + sign(delta) * excess * alpha)
        facing = next
        return next
    }

    fun reset() {
        facing = null
    }

    private fun blend(compass: Double?, course: Double?, speedMps: Double): Double? {
        if (compass == null) return course
        if (course == null) return compass
        // Par le plus court chemin : un mélange arithmétique de 350° et 10°
        // donnerait 180°, soit très exactement le dos de la direction réelle.
        val trust = courseTrust(speedMps)
        return GeoMath.normalizeHeading(
            compass + GeoMath.shortestHeadingDelta(compass, course) * trust,
        )
    }

    companion object {
        /**
         * La part du cap de route dans le mélange, de 0 à 1.
         *
         * Une courbe en S (`3t² − 2t³`) et non une rampe : la rampe a une
         * dérivée qui saute à ses deux extrémités, et ce saut se voit — le
         * cône accélère d'un coup au moment où l'on atteint le seuil, puis
         * s'arrête net à l'autre bout.
         */
        fun courseTrust(speedMps: Double): Double {
            val span = FACING_COURSE_FULL_SPEED_MPS - FACING_COURSE_MIN_SPEED_MPS
            val t = ((speedMps - FACING_COURSE_MIN_SPEED_MPS) / span).coerceIn(0.0, 1.0)
            return t * t * (3.0 - 2.0 * t)
        }
    }
}
