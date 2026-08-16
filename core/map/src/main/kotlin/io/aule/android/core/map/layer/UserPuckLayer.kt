package io.aule.android.core.map.layer

import com.google.gson.JsonObject
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.location.LocationFix
import io.aule.android.core.map.MapIcons
import io.aule.android.core.map.MapLayer
import kotlin.math.abs
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
 * cône de cap, le disque. Le cône disparaît dès que le cap est gelé —
 * afficher une direction qu'on ne connaît plus est pire que de n'en
 * afficher aucune.
 */
class UserPuckLayer : MapLayer {

    override val id: String = ID
    override val isAnimated: Boolean = true

    private var source: GeoJsonSource? = null

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
        target = Target(fix.coordinate, stabilizedHeading, fix.accuracyMeters)
        if (displayed == null) {
            // Première position : on se pose dessus, sans glisser depuis nulle part.
            displayed = Displayed(fix.coordinate, stabilizedHeading ?: 0.0)
            redraw()
        }
    }

    override fun onFrame(elapsedSeconds: Double) {
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

        style.addLayer(
            SymbolLayer(DOT_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(MapIcons.PUCK),
                PropertyFactory.iconAllowOverlap(true),
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
        style.removeLayer(ACCURACY_LAYER)
        style.removeSource(SOURCE)
        source = null
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
        const val DOT_LAYER = "aule.puck.dot"

        const val PROP_HEADING = "heading"
        const val PROP_HAS_HEADING = "hasHeading"
        const val PROP_ACCURACY = "accuracy"

        const val ALIGNMENT_MAP = "map"
    }
}
