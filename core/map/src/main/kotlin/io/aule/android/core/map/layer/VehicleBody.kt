package io.aule.android.core.map.layer

import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.TransportMode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Le volume d'un véhicule : ses cotes réelles, et l'empreinte au sol qu'on extrude.
 *
 * **Pourquoi une empreinte extrudée et pas un modèle.** Le web pose des `.glb`
 * dans une scène three.js greffée sur MapLibre ; le SDK Android n'offre pas cette
 * porte — il n'a pas de couche de modèles, et son `CustomLayer` réclame un hôte
 * C++, pas du Kotlin. Reste `fill-extrusion`, exactement la couche qui donne leur
 * relief aux bâtiments du style : une empreinte, une hauteur, et le moteur
 * s'occupe des faces, de l'ombrage et de la profondeur — un bus derrière un
 * immeuble passe derrière l'immeuble, sans qu'on ait un seul test à écrire.
 *
 * Ce qu'on y perd est la silhouette ; ce qu'on y gagne est un volume qui suit
 * l'inclinaison de la carte à 120 Hz sans peser plus qu'un polygone.
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
     * Le grossissement appliqué aux cotes, selon le zoom.
     *
     * À l'échelle exacte, un bus de onze mètres fait quelques pixels au seuil
     * d'apparition : on le devine sans le lire. On l'exagère donc là où la carte
     * est large, et on rend les proportions vraies dès qu'on descend dans la rue
     * — même parti que le web, et que les figurants du décor.
     *
     * Le tram, déjà long de vingt-huit mètres, est moins grossi : au même facteur
     * il avalerait les carrefours.
     */
    fun emphasis(mode: TransportMode, zoom: Double): Double {
        val base = min(MAX_EMPHASIS, MAX_EMPHASIS - (zoom - EMPHASIS_FROM) * EMPHASIS_DECAY)
            .coerceAtLeast(1.0)
        return if (mode == TransportMode.TRAM) 1 + (base - 1) * TRAM_EMPHASIS_SHARE else base
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

    /** Le seuil de zoom où le grossissement est à son maximum. */
    private const val EMPHASIS_FROM = 15.5

    private const val MAX_EMPHASIS = 1.6

    /** De sorte que les proportions redeviennent vraies vers z17,4. */
    private const val EMPHASIS_DECAY = 0.32

    private const val TRAM_EMPHASIS_SHARE = 0.55

    /** Longueur du nez : une part de la caisse, plafonnée pour les longs véhicules. */
    private const val NOSE_SHARE = 0.18
    private const val NOSE_MAX_M = 1.8

    /** Largeur du bout du nez, en part de la demi-largeur. */
    private const val NOSE_WIDTH_SHARE = 0.62
}
