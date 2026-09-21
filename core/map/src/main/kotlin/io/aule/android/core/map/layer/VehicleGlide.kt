package io.aule.android.core.map.layer

import io.aule.android.core.geo.GeoMath
import io.aule.android.core.geo.PolylinePath
import io.aule.android.core.geo.PolylineProjection
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Le cap d'un véhicule qui glisse : celui que lui donne **la voie sous sa
 * caisse**, et la façon dont il y vient.
 *
 * ## Ce que ce fichier répare
 *
 * Le cap se déduisait du déplacement entre deux images, sous un seuil de bruit
 * de 1,5 m. Or une image dure 1/60 s : un bus à 4 m/s y avance de **7 cm**. Le
 * seuil ne s'ouvrait donc jamais pendant la glisse — seulement à la trame qui
 * suit un sondage, quand la position est reposée sur le nouveau tracé — et il
 * n'accordait alors que 35 % de la correction. Autrement dit, la caisse gardait
 * pendant dix secondes le cap **mesuré au début de l'horizon**, quoi que fît la
 * voie.
 *
 * Ce que ça coûte, mesuré sur la flotte nantaise le 18/09/2026, 72 véhicules :
 * sur un seul horizon de dix secondes, le cap de la voie tourne de 5° en
 * médiane, **39° au neuvième décile, 79° au pire**, et onze véhicules sur
 * cinquante-sept dépassent 20°. C'est exactement la rotation qu'aucune caisse
 * n'appliquait : un bus posé de biais dans son virage, puis redressé d'un coup
 * au sondage suivant.
 *
 * ## Le cap est une propriété de la voie, pas du déplacement
 *
 * D'où la bascule : on ne regarde plus de combien le véhicule a bougé, on
 * regarde **où va la voie sous lui**. Le tracé est là — le serveur l'envoie avec
 * chaque position, il épouse la voirie —, il suffisait de le lire ailleurs qu'à
 * son premier sommet.
 *
 * Aucun seuil de bruit n'est alors nécessaire : une géométrie ne tremble pas. Un
 * véhicule à l'arrêt garde son cap parce que sa voie ne tourne pas sous lui, et
 * non parce qu'un seuil l'a retenu.
 *
 * Calcul pur — ni MapLibre, ni Android — pour rester vérifiable sur la JVM.
 */
internal object VehicleGlide {

    /**
     * Le cap que dessine la voie sous une caisse de [spanMeters] de long, posée
     * à [distanceMeters] du début de [path].
     *
     * ⚠️ **La corde couvre la caisse entière, pas le sommet le plus proche.**
     * Prendre le cap du seul segment sous le point d'ancrage donnait un escalier :
     * les sommets du tracé sont espacés de 23 m en médiane et 43 m au neuvième
     * décile (mesuré sur la flotte, 18/09/2026), donc le cap sautait d'un palier
     * à l'autre. Une corde tendue d'un bout à l'autre du véhicule, elle, varie de
     * façon **continue** avec l'avancement : elle pivote pendant toute la
     * traversée du virage au lieu de basculer à son entrée.
     *
     * Et elle dit le vrai d'un objet rigide : un tram de vingt-huit mètres qui
     * aborde une courbe **occupe** cette courbe, ses deux bouts posés sur deux
     * tangentes différentes. Son axe est la corde entre ces deux bouts, pas la
     * tangente de son milieu. Le rendu dessine une boîte ; la corde est
     * l'orientation de cette boîte.
     *
     * Rend `null` quand la voie ne peut rien affirmer — tracé inutilisable, ou
     * corde trop courte pour porter une direction. L'appelant garde alors le cap
     * qu'il avait, ce qui est toujours mieux que le nord d'un `atan2(0, 0)`.
     */
    fun tangent(path: PolylinePath, distanceMeters: Double, spanMeters: Double): Double? {
        if (!path.isUsable) return null
        val total = path.length
        // Une caisse plus longue que le tracé connu prend le tracé entier : c'est
        // la meilleure direction disponible, et elle reste juste.
        val span = min(max(spanMeters, MIN_SPAN_M), total)

        // ⚠️ **La fenêtre glisse aux extrémités, elle ne se raccourcit pas.** Un
        // tracé ne couvre que l'horizon annoncé — 41 m en médiane —, donc le
        // véhicule passe une bonne part de sa glisse près d'un bout. Une fenêtre
        // tronquée y rendrait une corde de quelques mètres, c'est-à-dire le
        // segment le plus proche : l'escalier qu'on vient d'écarter reviendrait
        // précisément là où il se voit le plus, au raccord entre deux sondages.
        var tail = distanceMeters.coerceIn(0.0, total) - span / 2
        if (tail < 0.0) tail = 0.0
        if (tail + span > total) tail = total - span
        val nose = tail + span

        val from = PolylineProjection.pointAt(path, tail / total)?.point ?: return null
        val to = PolylineProjection.pointAt(path, nose / total)?.point ?: return null
        // Un tracé dont tous les sommets se confondent — l'arrondi du serveur est
        // à cinq décimales, soit 1,1 m — ne porte aucune direction.
        if (GeoMath.distance(from, to) < MIN_CHORD_M) return null
        return GeoMath.bearing(from, to)
    }

    /**
     * Le cap de cette image : [previous], amené vers [aim] pendant [dtSeconds].
     *
     * Un filtre exponentiel, et non une part fixe par image. La part fixe des
     * 35 % d'avant liait la vitesse de rotation à la **cadence d'affichage** :
     * le même virage se prenait deux fois plus vite à 120 Hz qu'à 60, et une
     * saccade de rendu figeait la caisse. Ici la constante de temps est en
     * secondes, donc l'écran peut battre comme il veut.
     *
     * À [TAU_SECONDS], le cap rejoint la voie à 95 % en moins d'une seconde, et
     * le retard en régime — le produit de la constante par la vitesse de
     * rotation — vaut 4 à 5° dans un virage de bus pris en six secondes. Assez
     * rapide pour qu'on ne lise aucun décalage, assez lent pour que les paliers
     * du tracé ne se voient pas passer.
     */
    fun heading(previous: Double, aim: Double?, dtSeconds: Double): Double {
        if (aim == null) return GeoMath.normalizeHeading(previous)
        // ⚠️ **Un demi-tour n'est pas un virage : il se pose, il ne se joue pas.**
        // Au terminus, la course repart en sens inverse et la caisse hérite de la
        // pose de l'aller (voir `twinId`). Faire tourner ça donnerait une pirouette
        // d'une seconde et demie au milieu du quai. Au-delà de ce palier, ce n'est
        // plus une voie qui tourne, c'est une donnée qui change : on suit.
        if (abs(GeoMath.shortestHeadingDelta(previous, aim)) >= SNAP_ABOVE_DEGREES) {
            return GeoMath.normalizeHeading(aim)
        }
        return GeoMath.interpolateHeading(previous, aim, smoothing(dtSeconds))
    }

    /** La part du chemin restant parcourue en [dtSeconds]. */
    fun smoothing(dtSeconds: Double): Double {
        if (!dtSeconds.isFinite() || dtSeconds <= 0.0) return 0.0
        return 1 - exp(-min(dtSeconds, MAX_FRAME_SECONDS) / TAU_SECONDS)
    }

    /**
     * La plus longue image dont on tienne compte.
     *
     * Une trame perdue, une reprise d'application, une horloge repartie de zéro :
     * l'écart entre deux images peut valoir des secondes. Sans ce plafond, la
     * caisse rattraperait tout son retard d'un bloc — exactement le ressaut qu'on
     * cherche à supprimer.
     */
    const val MAX_FRAME_SECONDS = 0.1

    /** La constante de temps du rattrapage de cap, en secondes. */
    const val TAU_SECONDS = 0.30

    /** Au-delà, la voie n'a pas tourné : la donnée a changé. */
    const val SNAP_ABOVE_DEGREES = 120.0

    /** En dessous, une corde ne porte pas de direction fiable. */
    const val MIN_CHORD_M = 1.0

    /**
     * Le plus court véhicule qu'on oriente.
     *
     * Le gabarit du mode donne la corde — onze mètres pour un bus, vingt-huit
     * pour un tram —, mais un appelant qui n'en aurait pas doit quand même
     * obtenir une corde assez longue pour couvrir l'arrondi du serveur.
     */
    const val MIN_SPAN_M = 8.0
}
