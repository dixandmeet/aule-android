package io.aule.android.core.map.layer

import io.aule.android.core.geo.GeoMath
import io.aule.android.core.map.MapScale
import io.aule.android.core.model.TransportMode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Le volume d'un véhicule : ses cotes réelles, et l'empreinte au sol qu'on extrude.
 *
 * **Ce fichier ne dessine plus la flotte, il la rattrape.** Bus et trams sont
 * désormais de vrais modèles, posés par la couche native (ADR-015). L'empreinte
 * extrudée reste pour deux cas, et ils comptent tous les deux :
 *
 * - **le navibus**, dont le pack ne contient aucun modèle. Une pastille plate
 *   seule au milieu de la Loire pendant que toute la ville prend du relief se
 *   lirait comme un oubli — c'est le choix qu'Android fait, là où le web et iOS
 *   le laissent en icône ;
 * - **le repli**, quand le rendu natif n'a pas pu démarrer. L'écran est alors
 *   exactement celui d'avant.
 *
 * Que le bateau emprunte le chemin de secours n'est pas un hasard heureux : c'est
 * ce qui le garde **parcouru à chaque session**. Un chemin de repli jamais
 * emprunté est un chemin cassé qu'on ignore.
 *
 * ⚠️ Une version de ce commentaire affirmait que le SDK Android « n'offre pas
 * cette porte ». C'était faux, et cela a failli coûter la fonctionnalité :
 * `CustomLayer` **est** la porte, elle réclame simplement un hôte C++. Voir
 * l'ADR-015.
 *
 * [gauge] est partagé avec la 3D — les deux rendus doivent montrer le même
 * réseau à la même échelle. [emphasis] et [footprint] ne servent plus qu'ici.
 *
 * Le fichier est du calcul pur — ni MapLibre, ni Android — pour rester vérifiable
 * sur la JVM.
 */
internal object VehicleBody {

    /**
     * Les cotes d'un véhicule, en mètres.
     *
     * Ce sont les vraies : elles viennent du pack de modèles du web
     * (`components/carte-immersive/next/layers/vehicles-3d.ts`), pour que les
     * deux cartes montrent le même réseau à la même échelle.
     */
    data class Gauge(
        val lengthMeters: Double,
        val widthMeters: Double,
        val heightMeters: Double,
    )

    /** Six sommets par empreinte : quatre coins, et un nez en deux points. */
    const val VERTICES = 6

    /**
     * Les cotes du mode.
     *
     * Le Navibus a droit à son volume, à la différence du web qui le laisse en
     * icône faute de modèle : ici une coque coûte le même polygone qu'une caisse
     * de bus, et une pastille plate seule au milieu de la Loire pendant que toute
     * la ville prend du relief se lirait comme un oubli.
     */
    fun gauge(mode: TransportMode): Gauge = when (mode) {
        TransportMode.BUS -> Gauge(lengthMeters = 11.0, widthMeters = 2.55, heightMeters = 3.2)
        TransportMode.TRAM -> Gauge(lengthMeters = 28.0, widthMeters = 2.65, heightMeters = 3.35)
        TransportMode.BOAT -> Gauge(lengthMeters = 19.0, widthMeters = 6.0, heightMeters = 3.6)
    }

    /**
     * Le grossissement appliqué aux cotes : de quoi tenir une **longueur à
     * l'écran**, jamais moins que la vraie.
     *
     * À l'échelle exacte, un bus de onze mètres fait une dizaine de points au
     * cadre du quartier : on le devine sans le lire. On l'agrandit donc jusqu'à
     * [floorPoints] points de long, et on rend les proportions vraies dès que la
     * carte descend assez pour qu'il les atteigne seul — vers z17,4 pour un bus,
     * z16,6 pour un tram. Même parti que le web, et que les figurants du décor.
     *
     * ⚠️ **Un plancher en points, pas un facteur par zoom.** L'ancienne rampe
     * plafonnait à ×1,6 à z15,5 : un bus y mesurait **quinze points**, et
     * comme le facteur ne suivait pas l'échelle, chaque cran de dézoom le
     * divisait par deux. Signalé à l'écran (« beaucoup trop petits ») le
     * 23/09/2026, au moment où l'ouverture reculait au quartier.
     *
     * Le tram, déjà long de vingt-huit mètres, a un plancher plus long mais un
     * grossissement moindre : au facteur d'un bus, il avalerait les carrefours.
     *
     * @param latitude celle du véhicule. Web Mercator étire les distances vers
     *   les pôles : le même zoom ne donne pas le même nombre de mètres par
     *   point à Nantes et à Lille.
     */
    fun emphasis(mode: TransportMode, zoom: Double, latitude: Double): Double {
        if (!zoom.isFinite() || !latitude.isFinite()) return 1.0
        val metersPerPoint = MapScale.metersPerPixel(latitude, zoom)
        val floorMeters = floorPoints(mode) * metersPerPoint
        return (floorMeters / gauge(mode).lengthMeters).coerceIn(1.0, MAX_EMPHASIS)
    }

    /**
     * La longueur d'écran, en points, sous laquelle un véhicule ne descend pas.
     *
     * Celle d'un bus est celle d'un doigt posé : à peu près le bouton de
     * position, de quoi lire le sens de marche et viser la caisse. Le tram
     * garde sa silhouette de rame, nettement plus longue qu'un bus sans
     * l'être trois fois comme dans la rue.
     */
    fun floorPoints(mode: TransportMode): Double = when (mode) {
        TransportMode.BUS -> BUS_FLOOR_POINTS
        TransportMode.TRAM -> TRAM_FLOOR_POINTS
        TransportMode.BOAT -> BOAT_FLOOR_POINTS
    }

    /**
     * Le fondu du volume, entre le glyphe plat et le relief.
     *
     * ⚠️ **Cette fonction et l'expression de la couche doivent dire la même
     * chose.** L'extrusion interpole son opacité par une `Expression` que
     * MapLibre évalue, la scène 3D reçoit son alpha calculé ici : deux rampes
     * écrites séparément divergeraient, et le passage de l'une à l'autre se
     * verrait comme un ressaut. Les bornes vivent donc à un seul endroit.
     */
    fun bodyFade(zoom: Double, fade: Double, from: Double): Double = when {
        zoom <= from - fade -> 0.0
        zoom >= from + fade -> 1.0
        else -> (zoom - (from - fade)) / (2 * fade)
    }

    /**
     * Écrit l'empreinte au sol dans [out], par paires `lon, lat` — l'ordre GeoJSON.
     *
     * Un tableau plutôt qu'une liste de points : cette fonction tourne pour chaque
     * véhicule à l'image, et rendre six objets par appel à 120 Hz ferait travailler
     * le ramasse-miettes pendant que la carte glisse.
     *
     * La forme n'est pas un rectangle : l'avant est aminci en pointe. C'est le seul
     * indice de sens de marche qu'il reste une fois le chevron 2D éteint, et sans
     * lui un bus à l'arrêt ne dit plus de quel côté il repartira.
     *
     * La projection est locale et plate — à quinze mètres du centre, la courbure
     * de la Terre est très en dessous du pixel.
     */
    fun footprint(
        latitude: Double,
        longitude: Double,
        headingDegrees: Double,
        gauge: Gauge,
        scale: Double,
        out: DoubleArray,
    ) {
        val length = gauge.lengthMeters * scale
        val width = gauge.widthMeters * scale
        val halfLength = length / 2
        val halfWidth = width / 2
        val nose = min(NOSE_MAX_M * scale, length * NOSE_SHARE)
        val halfNose = halfWidth * NOSE_WIDTH_SHARE

        // Le cap compte les degrés depuis le nord dans le sens des aiguilles :
        // l'avant pointe donc vers (sin, cos) et non l'inverse.
        val heading = Math.toRadians(GeoMath.normalizeHeading(headingDegrees))
        val forwardEast = sin(heading)
        val forwardNorth = cos(heading)
        // Tribord, à quatre-vingt-dix degrés du cap.
        val rightEast = forwardNorth
        val rightNorth = -forwardEast

        val metresPerDegreeLat = Math.PI * GeoMath.EARTH_RADIUS_M / 180
        val metresPerDegreeLon = metresPerDegreeLat * cos(Math.toRadians(latitude))

        fun put(index: Int, alongMetres: Double, acrossMetres: Double) {
            val east = forwardEast * alongMetres + rightEast * acrossMetres
            val north = forwardNorth * alongMetres + rightNorth * acrossMetres
            out[index * 2] = longitude + east / metresPerDegreeLon
            out[index * 2 + 1] = latitude + north / metresPerDegreeLat
        }

        put(0, -halfLength, -halfWidth)
        put(1, -halfLength, halfWidth)
        put(2, halfLength - nose, halfWidth)
        put(3, halfLength, halfNose)
        put(4, halfLength, -halfNose)
        put(5, halfLength - nose, -halfWidth)
    }

    private const val BUS_FLOOR_POINTS = 36.0
    private const val TRAM_FLOOR_POINTS = 60.0
    private const val BOAT_FLOOR_POINTS = 44.0

    /**
     * Le plafond, pour les zooms où le volume ne se voit déjà plus : le fondu
     * éteint les caisses sous z14,9, mais la scène 3D les calcule encore, et un
     * bus de cent mètres de haut traversant le fondu se verrait.
     */
    private const val MAX_EMPHASIS = 6.0

    /** Longueur du nez : une part de la caisse, plafonnée pour les longs véhicules. */
    private const val NOSE_SHARE = 0.18
    private const val NOSE_MAX_M = 1.8

    /** Largeur du bout du nez, en part de la demi-largeur. */
    private const val NOSE_WIDTH_SHARE = 0.62
}
