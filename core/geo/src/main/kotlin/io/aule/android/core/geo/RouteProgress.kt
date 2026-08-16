package io.aule.android.core.geo

/**
 * Où l'on en est le long d'un tracé — **et pourquoi ce n'est pas une simple
 * projection.**
 *
 * Port de `SAE/lib/navigation/route_progress.dart`.
 *
 * Projeter au plus proche saute d'un brin à l'autre sur un corridor emprunté
 * à l'aller et au retour. La parade est une **fenêtre** autour de là où l'on
 * était : [PolylineProjection.BACK_WINDOW] en arrière, [PolylineProjection.FORWARD_WINDOW]
 * en avant.
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
        val match = if (seeded) {
            PolylineProjection.project(position, onto = points, currentT = t)
        } else {
            PolylineProjection.project(position, onto = points)
        } ?: return null
        seeded = true
        t = match.t
        bearing = match.bearing
        deviationMeters = match.deviationMeters
        return match
    }

    /** Une autre course, ou la même reprise ailleurs. */
    fun reset(initial: Double? = null) {
        t = initial?.coerceIn(0.0, 1.0) ?: 0.0
        seeded = initial != null
        bearing = 0.0
        deviationMeters = 0.0
    }
}
