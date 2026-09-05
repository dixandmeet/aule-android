package io.aule.android.core.geo

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Un tracé et ses longueurs cumulées, préparées une fois.
 *
 * C'est ce qui sort les haversine de la boucle d'image. Ces longueurs ne changent
 * pas entre deux sondages du serveur ; les recalculer par véhicule et par image
 * coûtait, sur iOS, de l'ordre de **600 000 haversine par seconde**. On les
 * calcule donc à la réception de l'instantané, pas à l'affichage.
 */
class PolylinePath(val points: List<Coordinate>) {

    val cumulative: DoubleArray = PolylineProjection.cumulativeLengths(points)

    val length: Double get() = cumulative.lastOrNull() ?: 0.0

    val isUsable: Boolean get() = points.size >= 2 && length > 0
}

/** Où l'on se trouve sur un tracé. */
data class PolylineMatch(
    /** Avancement le long du tracé, de 0 à 1. */
    val t: Double,
    /** Le point du tracé le plus proche de la position donnée. */
    val point: Coordinate,
    /**
     * Distance perpendiculaire au tracé, en mètres.
     * C'est elle qui dit si on a quitté l'itinéraire.
     */
    val deviationMeters: Double,
    /**
     * Cap du segment sous nos pieds. Utile quand le GPS n'en donne aucun —
     * c'est-à-dire à l'arrêt, précisément au moment où l'on démarre une navigation.
     */
    val bearing: Double,
    /** Index du segment atteint, pour reprendre la recherche à côté au coup suivant. */
    val segmentIndex: Int,
)

object PolylineProjection {

    /**
     * Fenêtre arrière, **en mètres**. Un bus ne recule pas.
     *
     * Port de `SAE/lib/navigation/route_progress.dart`, où elle valait 1,8 % du
     * tracé — voir [FORWARD_WINDOW_M] pour ce que la fraction coûtait.
     */
    const val BACK_WINDOW_M = 40.0

    /**
     * Fenêtre avant, **en mètres**. De quoi franchir un tunnel entre deux
     * positions.
     *
     * ## Pourquoi des mètres, et non un pourcentage
     *
     * Les deux fenêtres valaient une fraction du tracé : 1,8 % en arrière, 12 %
     * en avant. Une fraction ne mesure pourtant rien de ce qui compte ici — ce
     * qu'on veut borner, c'est **le chemin qu'on a pu parcourir depuis la
     * position précédente**, et cela se compte en mètres, pas en proportion d'un
     * trajet dont la longueur n'a rien à voir.
     *
     * La fraction se retournait aux deux bouts. Sur les 1 832 m du trajet de
     * recette, 12 % faisaient **220 m** de fenêtre avant : dans un rond-point,
     * la branche de sortie tombe dedans, la projection pouvait s'y poser alors
     * qu'on abordait à peine l'entrée, et l'avancement décrochait — la fenêtre
     * arrière de 33 m ne permettait plus de revenir. L'écran annonçait alors
     * « Vous avez quitté l'itinéraire » sur un trajet suivi au mètre près, et
     * un recalcul partait toutes les douze secondes. Mesuré le 28/08/2026 : deux
     * à quatre fois sur le tiers du trajet qui enchaîne huit ronds-points,
     * **zéro** sur la portion droite qui le suit.
     *
     * À l'autre bout, sur un trajet de 50 km, 12 % auraient fait **six
     * kilomètres** : la parade n'en était plus une.
     *
     * Cent cinquante mètres, c'est treize secondes à 40 km/h et quatre secondes
     * à 130 : de quoi absorber un trou de signal sans offrir à la projection la
     * moitié d'un giratoire.
     */
    const val FORWARD_WINDOW_M = 150.0

    /**
     * Écart au-delà duquel on soupçonne un décrochage plutôt qu'une sortie.
     *
     * La fenêtre protège du saut, mais quand elle a laissé passer un saut, elle
     * emprisonne : l'avancement est en avant, la fenêtre arrière est courte, et
     * plus rien ne ramène au bon brin. C'est ce que [RouteProgress] rattrape.
     *
     * Le seuil est sous les 32 m d'`OffRouteDetector` : on vérifie **avant** de
     * conclure à une sortie, jamais après.
     */
    const val RECOVERY_DEVIATION_M = 25.0

    /**
     * Jusqu'où le rattrapage a le droit de déplacer l'avancement.
     *
     * Sans cette borne, le rattrapage **annulerait la fenêtre** : les deux
     * situations se ressemblent trait pour trait — dans les deux cas la position
     * est sur le tracé et la fenêtre ne l'y trouve pas. Ce qui les sépare, c'est
     * la distance. Un décrochage de giratoire se compte en dizaines de mètres ;
     * un brin parallèle pris pour l'autre, ou une position aberrante, envoie à
     * des kilomètres.
     *
     * On rattrape donc un décrochage **local**, jamais une téléportation.
     */
    const val RECOVERY_SPAN_M = 400.0

    /**
     * Projette une position sur un tracé, en ne cherchant que dans une fenêtre
     * autour de l'avancement connu.
     *
     * La fenêtre est tout l'intérêt : une projection au plus proche saute d'un
     * brin à l'autre sur un corridor emprunté à l'aller et au retour, et
     * l'avancement fait des bonds de plusieurs kilomètres. Les bornes sont
     * dissymétriques — on avance beaucoup plus souvent qu'on ne recule.
     *
     * Les bornes sont données **en mètres** et converties en fraction pour ce
     * tracé-ci : c'est la longueur parcourue qu'on borne, pas une proportion du
     * trajet — voir [FORWARD_WINDOW_M].
     *
     * @param currentT avancement connu, ou `null` pour chercher sur tout le tracé
     *   (premier appel).
     */
    fun project(
        position: Coordinate,
        onto: List<Coordinate>,
        currentT: Double? = null,
        backWindowMeters: Double = BACK_WINDOW_M,
        forwardWindowMeters: Double = FORWARD_WINDOW_M,
    ): PolylineMatch? {
        if (currentT == null) return projectWithin(position, onto)
        // Le second parcours des longueurs cumulées est dans `projectWithin`.
        // Une fois par seconde sur quelques centaines de points, il ne pèse
        // rien — contrairement à la boucle d'image des véhicules, qui passe
        // par `PolylinePath` justement pour ne pas le refaire.
        val total = length(onto)
        if (total <= 0) return null
        val minT = max(0.0, currentT - backWindowMeters / total)
        val maxT = min(1.0, currentT + forwardWindowMeters / total)
        return projectWithin(position, onto, minT = minT, maxT = maxT)
    }

    /**
     * Projette dans un intervalle `[minT, maxT]`, sans fenêtre autour d'un `t`.
     *
     * C'est ce dont l'agrafage des manœuvres a besoin : une jambe, pas un
     * voisinage. Réutiliser [project] avec une fenêtre de 12 % ouvrirait la
     * recherche trop large, et une manœuvre de la marche finale pourrait
     * retomber sur la marche initiale.
     */
    fun projectWithin(
        position: Coordinate,
        onto: List<Coordinate>,
        minT: Double = 0.0,
        maxT: Double = 1.0,
    ): PolylineMatch? {
        if (onto.size < 2) return null
        val cumulative = cumulativeLengths(onto)
        val total = cumulative.last()
        if (total <= 0) return null
        val lowerT = minT.coerceIn(0.0, 1.0)
        val upperT = maxT.coerceIn(lowerT, 1.0)

        var best: PolylineMatch? = null
        for (index in 0 until onto.size - 1) {
            val segmentStartT = cumulative[index] / total
            val segmentEndT = cumulative[index + 1] / total
            if (segmentEndT < lowerT || segmentStartT > upperT) continue

            val a = onto[index]
            val b = onto[index + 1]
            var fraction = closestFractionOnSegment(position, a, b)

            // La fenêtre borne l'avancement **à l'intérieur** du segment, pas
            // seulement le choix des segments. Sans ce recadrage, un segment qui
            // n'entre dans la fenêtre que par son début laisse quand même le
            // résultat filer jusqu'à sa fin — et sur un tracé grossier, cela
            // suffit à retrouver le saut que la fenêtre existe pour empêcher.
            val span = segmentEndT - segmentStartT
            if (span > 0) {
                val lowestFraction = max(0.0, (lowerT - segmentStartT) / span)
                val highestFraction = min(1.0, (upperT - segmentStartT) / span)
                if (lowestFraction > highestFraction) continue
                fraction = fraction.coerceIn(lowestFraction, highestFraction)
            }

            val point = GeoMath.interpolate(a, b, fraction)
            val deviation = GeoMath.distance(position, point)

            if (best == null || deviation < best.deviationMeters) {
                best = PolylineMatch(
                    t = (segmentStartT + span * fraction).coerceIn(0.0, 1.0),
                    point = point,
                    deviationMeters = deviation,
                    bearing = GeoMath.bearing(a, b),
                    segmentIndex = index,
                )
            }
        }
        return best
    }

    /**
     * Le point d'un tracé à l'avancement [t], et le cap qu'on y suit.
     *
     * Recalcule les longueurs cumulées à chaque appel : réservé aux usages ponctuels.
     * Dans une boucle d'image, passer par [PolylinePath].
     */
    fun pointAt(points: List<Coordinate>, t: Double): PointOnLine? =
        pointAt(PolylinePath(points), t)

    /** La même chose, sur un tracé dont les longueurs sont déjà connues. */
    fun pointAt(path: PolylinePath, t: Double): PointOnLine? {
        val points = path.points
        if (points.isEmpty()) return null
        if (points.size < 2) return PointOnLine(points[0], 0.0)

        val cumulative = path.cumulative
        val total = path.length
        if (total <= 0) return null

        val target = t.coerceIn(0.0, 1.0) * total
        for (index in 0 until points.size - 1) {
            if (cumulative[index + 1] < target) continue
            val segmentLength = cumulative[index + 1] - cumulative[index]
            val fraction = if (segmentLength > 0) (target - cumulative[index]) / segmentLength else 0.0
            return PointOnLine(
                point = GeoMath.interpolate(points[index], points[index + 1], fraction),
                bearing = GeoMath.bearing(points[index], points[index + 1]),
            )
        }
        val last = points.size - 1
        return PointOnLine(points[last], GeoMath.bearing(points[last - 1], points[last]))
    }

    /**
     * La fraction du tracé atteinte au sommet [index].
     *
     * Les frontières de jambes sont des sommets connus. Tout le reste de la
     * navigation raisonne en `t` : cette conversion doit se faire **dans la
     * même métrique** que [project], sinon les jambes ne tombent pas où la
     * progression les attend.
     */
    fun tAtIndex(points: List<Coordinate>, index: Int): Double {
        if (points.size < 2) return 0.0
        val cumulative = cumulativeLengths(points)
        val total = cumulative.last()
        if (total <= 0) return 0.0
        if (index <= 0) return 0.0
        if (index >= points.lastIndex) return 1.0
        return (cumulative[index] / total).coerceIn(0.0, 1.0)
    }

    /** Longueur totale d'un tracé, en mètres. */
    fun length(points: List<Coordinate>): Double =
        if (points.isEmpty()) 0.0 else cumulativeLengths(points).last()

    fun cumulativeLengths(points: List<Coordinate>): DoubleArray {
        if (points.isEmpty()) return doubleArrayOf(0.0)
        val result = DoubleArray(points.size)
        for (index in 1 until points.size) {
            result[index] = result[index - 1] + GeoMath.distance(points[index - 1], points[index])
        }
        return result
    }

    /**
     * Projection plane locale : à l'échelle d'un segment de rue, l'erreur est
     * négligeable devant la précision du GPS, et la trigonométrie sphérique
     * coûterait à chaque image.
     */
    private fun closestFractionOnSegment(p: Coordinate, a: Coordinate, b: Coordinate): Double {
        val latScale = cos(Math.toRadians(p.latitude))
        val ax = a.longitude * latScale
        val ay = a.latitude
        val bx = b.longitude * latScale
        val by = b.latitude
        val px = p.longitude * latScale
        val py = p.latitude

        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0) return 0.0

        return (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    }

    data class PointOnLine(val point: Coordinate, val bearing: Double)
}
