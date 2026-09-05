package io.aule.android.core.geo

/**
 * Où l'on en est le long d'un tracé — **et pourquoi ce n'est pas une simple
 * projection.**
 *
 * Port de `SAE/lib/navigation/route_progress.dart`.
 *
 * Projeter au plus proche saute d'un brin à l'autre sur un corridor emprunté
 * à l'aller et au retour. La parade est une **fenêtre** autour de là où l'on
 * était : [PolylineProjection.BACK_WINDOW_M] en arrière,
 * [PolylineProjection.FORWARD_WINDOW_M] en avant — des mètres, parce que c'est
 * un chemin parcouru qu'on borne, non une proportion du trajet.
 *
 * Et parce qu'une fenêtre qui a laissé passer un saut enferme ensuite, elle est
 * doublée d'un rattrapage — voir `recovered`.
 *
 * Un objet et non une fonction : la fenêtre a besoin de savoir d'où l'on vient.
 */
class RouteProgress(initial: Double? = null) {

    var t: Double = initial?.coerceIn(0.0, 1.0) ?: 0.0
        private set

    /** Cap du **tracé**, pas du GPS — utile à l'arrêt, au démarrage d'un guidage. */
    var bearing: Double = 0.0
        private set

    var deviationMeters: Double = 0.0
        private set

    /** Vrai dès qu'un premier point a posé la progression. */
    var seeded: Boolean = initial != null
        private set

    /**
     * Avance avec un nouveau point.
     *
     * Le premier appel cherche sur tout le tracé, les suivants dans la fenêtre.
     * Un tracé inexploitable rend `null` et **laisse** la progression telle
     * quelle : un vide momentané ne veut pas dire qu'on est revenu au départ.
     */
    fun advance(points: List<Coordinate>, position: Coordinate): PolylineMatch? {
        if (points.size < 2) return null
        val windowed = if (seeded) {
            PolylineProjection.project(position, onto = points, currentT = t)
        } else {
            PolylineProjection.project(position, onto = points)
        } ?: return null
        val match = if (seeded) recovered(points, position, windowed) else windowed
        seeded = true
        t = match.t
        bearing = match.bearing
        deviationMeters = match.deviationMeters
        return match
    }

    /**
     * Rattrape un décrochage, sans rouvrir la porte aux sauts.
     *
     * La fenêtre est une protection à sens unique : elle empêche l'avancement de
     * bondir, mais si un bond a quand même eu lieu — une branche de giratoire
     * prise pour une autre —, elle **enferme**. L'avancement est trop en avant,
     * la fenêtre arrière ne fait que quarante mètres, et plus rien ne ramène au
     * brin qu'on suit vraiment : l'écart reste grand, et le guidage finit par
     * annoncer une sortie d'itinéraire à quelqu'un qui n'a pas quitté sa route.
     *
     * D'où cette seconde chance, et ses **trois** conditions. On ne consulte le
     * tracé entier que lorsque la fenêtre ne trouve plus la route sous nos
     * pieds ; on ne retient sa réponse que si elle, la trouve ; et on ne la
     * retient que si elle est **proche** — [PolylineProjection.RECOVERY_SPAN_M].
     *
     * La troisième est la plus importante, et la moins évidente : sans elle, le
     * rattrapage annulerait la fenêtre qu'il complète. Un brin parallèle pris
     * pour l'autre présente exactement les mêmes symptômes qu'un décrochage —
     * position sur le tracé, fenêtre qui ne l'y trouve pas. Seule la distance
     * les sépare.
     *
     * Un véhicule réellement sorti, lui, reste loin du tracé des deux façons de
     * le mesurer : sa sortie est détectée comme avant.
     */
    private fun recovered(
        points: List<Coordinate>,
        position: Coordinate,
        windowed: PolylineMatch,
    ): PolylineMatch {
        if (windowed.deviationMeters <= PolylineProjection.RECOVERY_DEVIATION_M) return windowed
        val global = PolylineProjection.project(position, onto = points) ?: return windowed
        if (global.deviationMeters > PolylineProjection.RECOVERY_DEVIATION_M) return windowed
        val total = PolylineProjection.length(points)
        val bond = kotlin.math.abs(global.t - t) * total
        return if (bond <= PolylineProjection.RECOVERY_SPAN_M) global else windowed
    }

    /** Une autre course, ou la même reprise ailleurs. */
    fun reset(initial: Double? = null) {
        t = initial?.coerceIn(0.0, 1.0) ?: 0.0
        seeded = initial != null
        bearing = 0.0
        deviationMeters = 0.0
    }
}
