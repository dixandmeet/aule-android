package io.aule.android.core.map.layer

import com.google.gson.JsonObject
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.location.LocationFix
import io.aule.android.core.map.MapIcons
import io.aule.android.core.map.MapLayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * La position de l'utilisateur.
 *
 * On dessine notre propre puck plutôt que d'utiliser celui de MapLibre : le
 * brief exclut le bleu de Google Maps, et surtout le puck natif se recale
 * par sauts alors que celui-ci glisse.
 *
 * Trois éléments, du plus incertain au plus sûr : l'anneau de précision, le
 * cône de cap, le marqueur. Le cône disparaît dès que le cap est gelé —
 * afficher une direction qu'on ne connaît plus est pire que de n'en
 * afficher aucune —, et le marqueur change de forme avec lui : **disque à
 * l'arrêt, flèche en mouvement**. Le disque répond « je suis là », la
 * flèche répond « je suis là et je vais par là », qui est la question qu'on
 * se pose en marchant.
 */
class UserPuckLayer : MapLayer {

    override val id: String = ID
    override val isAnimated: Boolean = true

    private var source: GeoJsonSource? = null

    // Référence **forte** sur la couche du halo : c'est elle qu'on écrit à
    // chaque battement, et la retrouver par son identifiant trente fois par
    // seconde coûterait une recherche dans le style à chaque fois.
    private var halo: SymbolLayer? = null
    private var lastBreathAt = 0.0

    private var target: Target? = null
    private var displayed: Displayed? = null

    /**
     * La position affichée, qui court après la mesure. C'est elle que suit
     * la caméra : la faire suivre la mesure brute ferait sursauter l'écran
     * à chaque point GPS.
     */
    val displayedCoordinate: Coordinate? get() = displayed?.coordinate
    val displayedHeading: Double? get() = displayed?.heading

    fun update(fix: LocationFix?, stabilizedHeading: Double?) {
        if (fix == null) {
            target = null
            return
        }
        val hadHeading = target?.heading != null
        target = Target(fix.coordinate, stabilizedHeading, fix.accuracyMeters)
        if (displayed == null) {
            // Première position : on se pose dessus, sans glisser depuis nulle part.
            displayed = Displayed(fix.coordinate, stabilizedHeading ?: 0.0)
            redraw()
            return
        }
        // ⚠️ **Le gel du cap doit être republié tout de suite.** C'est lui qui
        // choisit entre le disque et la flèche, et il bascule précisément au
        // moment où plus rien ne bouge : [onFrame] sort alors sans dessiner —
        // le puck a rattrapé sa cible, le cap est figé — et la flèche resterait
        // à pointer une direction qu'on n'a plus. On ne republie qu'au
        // basculement, pas à chaque point : le reste du temps, l'interpolation
        // fait très bien le travail.
        if (hadHeading != (stabilizedHeading != null)) redraw()
    }

    override fun onFrame(elapsedSeconds: Double) {
        breathe(elapsedSeconds)

        val target = target ?: return
        val current = displayed ?: return

        val distance = GeoMath.distance(current.coordinate, target.coordinate)
        val headingDelta = abs(
            GeoMath.shortestHeadingDelta(current.heading, target.heading ?: current.heading),
        )
        if (distance <= 0.15 && headingDelta <= 0.3) return

        val factor = if (distance > 40) 0.5 else 0.12
        val nextCoordinate = GeoMath.interpolate(current.coordinate, target.coordinate, factor)
        val nextHeading = target.heading?.let { GeoMath.interpolateHeading(current.heading, it, 0.2) }
            ?: current.heading
        displayed = Displayed(nextCoordinate, nextHeading)
        redraw()
    }

    override fun mount(style: Style, map: MapLibreMap) {
        source = GeoJsonSource(
            SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withSynchronousUpdate(true).withBuffer(0).withTolerance(0f),
        ).also { style.addSource(it) }

        // L'anneau d'incertitude, en mètres visuels : il doit grandir quand
        // on dézoome, sinon il annonce une précision qu'on n'a pas. Les
        // stops [12 → 0,5] / [22 → 512] sont ceux du proto iOS.
        style.addLayer(
            CircleLayer(ACCURACY_LAYER, SOURCE).withProperties(
                PropertyFactory.circleRadius(
                    Expression.interpolate(
                        Expression.exponential(2f),
                        Expression.zoom(),
                        Expression.stop(12, 0.5f),
                        Expression.stop(22, 512f),
                    ),
                ),
                PropertyFactory.circleColor(AuleBrand.teal.argb),
                PropertyFactory.circleOpacity(0.12f),
                PropertyFactory.circleStrokeWidth(1f),
                PropertyFactory.circleStrokeColor(AuleBrand.teal.argb),
                PropertyFactory.circleStrokeOpacity(0.25f),
                PropertyFactory.circlePitchAlignment(ALIGNMENT_MAP),
            ),
        )

        // Le halo, **sous** tout le reste : il entoure le puck, il ne le voile
        // pas. Face à l'écran comme le puck lui-même — couché au sol par
        // l'inclinaison de la navigation, il deviendrait une ellipse traînant
        // devant soi.
        halo = SymbolLayer(HALO_LAYER, SOURCE).withProperties(
            PropertyFactory.iconImage(MapIcons.PUCK_HALO),
            PropertyFactory.iconSize(HALO_REST),
            PropertyFactory.iconOpacity(HALO_OPACITY_REST),
            PropertyFactory.iconPitchAlignment(ALIGNMENT_VIEWPORT),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ).also { style.addLayer(it) }

        style.addLayer(
            SymbolLayer(CONE_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(MapIcons.PUCK_HEADING),
                PropertyFactory.iconRotate(Expression.get(PROP_HEADING)),
                PropertyFactory.iconRotationAlignment(ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
            ).also {
                it.setFilter(Expression.eq(Expression.get(PROP_HAS_HEADING), Expression.literal(1)))
            },
        )

        // Le marqueur lui-même : disque à l'arrêt, **flèche en mouvement**.
        //
        // C'est la même couche et la même source dans les deux cas — une
        // seconde couche ferait apparaître les deux marqueurs le temps d'une
        // image, à chaque démarrage et à chaque feu rouge. L'image est donc
        // choisie par expression, sur la propriété qui dit déjà si le cap
        // vaut quelque chose.
        //
        // Deux alignements, et ils ne disent pas la même chose : la flèche
        // **tourne avec la carte** — c'est une direction géographique — mais
        // reste **face à l'écran**. Couchée au sol par l'inclinaison de la
        // navigation, elle s'écraserait jusqu'à ne plus désigner que du vide.
        //
        // `ignorePlacement` la met hors du jeu d'évitement des étiquettes :
        // sa position n'est pas négociable, et un nom d'arrêt n'a pas à la
        // faire disparaître.
        style.addLayer(
            SymbolLayer(DOT_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(
                    Expression.switchCase(
                        Expression.eq(Expression.get(PROP_HAS_HEADING), Expression.literal(1)),
                        Expression.literal(MapIcons.PUCK_MOVING),
                        Expression.literal(MapIcons.PUCK),
                    ),
                ),
                PropertyFactory.iconRotate(Expression.get(PROP_HEADING)),
                PropertyFactory.iconRotationAlignment(ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )

        // ⚠️ La source qu'on vient de poser est vide, et **personne ne
        // viendra la remplir**. `update` ne dessine qu'à la première
        // position, et `onFrame` sort avant de dessiner dès que le puck a
        // rattrapé sa cible. À l'arrêt, les deux se taisent : sans cette
        // ligne, le puck disparaît au premier rechargement de style — donc
        // au premier passage en mode sombre — et ne revient qu'au prochain
        // déplacement réel.
        redraw()
    }

    override fun unmount(style: Style) {
        style.removeLayer(DOT_LAYER)
        style.removeLayer(CONE_LAYER)
        style.removeLayer(HALO_LAYER)
        style.removeLayer(ACCURACY_LAYER)
        style.removeSource(SOURCE)
        forgetStyle()
    }

    /**
     * ⚠️ **La couche la plus exposée du lot.** Le ticker caméra l'écrit toutes les
     * 66 ms par `update`, sans rien demander au registre : entre le moment où un
     * style meurt et celui où le suivant est chargé, une dizaine de battements
     * tombent sur une source qui n'est plus posée nulle part.
     *
     * La position visée et la position affichée, elles, restent : [mount] les
     * republie, et le puck ne disparaît pas le temps d'un rechargement.
     */
    override fun forgetStyle() {
        source = null
        halo = null
    }

    /**
     * Le battement du halo, repris du web : deux secondes et demie, de la taille
     * du puck à un tiers de plus, en s'effaçant à mesure qu'il s'élargit.
     *
     * **Trente fois par seconde, et non cent vingt.** L'écran du S21 en offre
     * cent vingt, mais une courbe qui met deux secondes et demie à faire son
     * tour ne se raconte pas plus finement en quatre-vingt-dix pas de plus :
     * c'est trois écritures de propriété sur quatre en moins, sur la seule
     * couche que rien n'oblige à se redessiner.
     *
     * Une sinusoïde plutôt qu'un aller-retour linéaire — c'est l'`ease-in-out`
     * du web, et c'est ce qui sépare une respiration d'un clignotant.
     */
    private fun breathe(elapsedSeconds: Double) {
        val halo = halo ?: return
        if (elapsedSeconds - lastBreathAt < BREATH_STEP) return
        lastBreathAt = elapsedSeconds

        val phase = (elapsedSeconds % HALO_PERIOD) / HALO_PERIOD
        val wave = (1.0 - cos(2.0 * PI * phase)) / 2.0
        halo.setProperties(
            PropertyFactory.iconSize((HALO_REST + (1f - HALO_REST) * wave.toFloat())),
            PropertyFactory.iconOpacity(
                HALO_OPACITY_REST + (HALO_OPACITY_WIDE - HALO_OPACITY_REST) * wave.toFloat(),
            ),
        )
    }

    private fun redraw() {
        val source = source ?: return
        val displayed = displayed ?: return
        val target = target ?: return

        val props = JsonObject().apply {
            addProperty(PROP_HEADING, displayed.heading)
            addProperty(PROP_HAS_HEADING, if (target.heading != null) 1 else 0)
            addProperty(PROP_ACCURACY, max(target.accuracy, 5.0))
        }
        source.setGeoJson(
            Feature.fromGeometry(
                Point.fromLngLat(displayed.coordinate.longitude, displayed.coordinate.latitude),
                props,
            ),
        )
    }

    private data class Target(
        val coordinate: Coordinate,
        val heading: Double?,
        val accuracy: Double,
    )

    private data class Displayed(
        val coordinate: Coordinate,
        val heading: Double,
    )

    private companion object {
        const val ID = "aule.puck"
        const val SOURCE = "aule.puck.source"
        const val ACCURACY_LAYER = "aule.puck.accuracy"
        const val CONE_LAYER = "aule.puck.cone"
        const val HALO_LAYER = "aule.puck.halo"
        const val DOT_LAYER = "aule.puck.dot"

        const val PROP_HEADING = "heading"
        const val PROP_HAS_HEADING = "hasHeading"
        const val PROP_ACCURACY = "accuracy"

        /**
         * La respiration du halo, et son pas.
         *
         * L'image est peinte à son ampleur maximale : [HALO_REST] est donc la
         * part qu'elle occupe au repos, et le sommet vaut un. Les valeurs
         * viennent du web — un tiers de plus en largeur, et l'opacité qui
         * tombe des trois quarts pendant qu'il s'étale.
         */
        const val HALO_PERIOD = 2.6
        const val BREATH_STEP = 1.0 / 30.0
        const val HALO_REST = 0.62f
        const val HALO_OPACITY_REST = 0.70f
        const val HALO_OPACITY_WIDE = 0.18f

        const val ALIGNMENT_MAP = "map"

        /** Face à l'écran, quelle que soit l'inclinaison de la caméra. */
        const val ALIGNMENT_VIEWPORT = "viewport"
    }
}
