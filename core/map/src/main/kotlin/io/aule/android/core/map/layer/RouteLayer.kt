package io.aule.android.core.map.layer

import com.google.gson.JsonObject
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.map.MapLayer
import io.aule.android.core.model.ROUTE_FALLBACK_COLOR
import io.aule.android.core.model.RouteCandidate
import io.aule.android.core.model.RoutePlace
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Le tracé de l'itinéraire planifié.
 *
 * Port de `SAE/lib/carte_immersive/layers/route_layer.dart`.
 *
 * Quatre couches et non une : les tronçons transit en ruban plein **avec
 * liseré** — la couleur de la ligne fait foi ; les tronçons de marche en
 * pointillé. On ne peint pas une correspondance à pied comme un bus qu'on
 * suit. Les extrémités, enfin, disent où l'on part et où l'on va.
 */
class RouteLayer : MapLayer {

    override val id: String = ID

    private var lineSource: GeoJsonSource? = null
    private var endpointsSource: GeoJsonSource? = null

    private var candidate: RouteCandidate? = null
    private var origin: RoutePlace? = null
    private var destination: RoutePlace? = null

    fun setTrace(candidate: RouteCandidate?, origin: RoutePlace?, destination: RoutePlace?) {
        this.candidate = candidate
        this.origin = origin
        this.destination = destination
        redraw()
    }

    override fun mount(style: Style, map: MapLibreMap) {
        val options = GeoJsonOptions().withBuffer(0).withTolerance(0.375f)
        lineSource = GeoJsonSource(LINE_SOURCE, FeatureCollection.fromFeatures(emptyList()), options)
            .also { style.addSource(it) }
        endpointsSource = GeoJsonSource(ENDPOINTS_SOURCE, FeatureCollection.fromFeatures(emptyList()), options)
            .also { style.addSource(it) }

        style.addLayer(
            LineLayer(CASING_LAYER, LINE_SOURCE).withProperties(
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
                PropertyFactory.lineColor("#1F2933"),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(12, 6f),
                        Expression.stop(16, 10f),
                    ),
                ),
                PropertyFactory.lineOpacity(0.55f),
            ).also { it.setFilter(Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_TRANSIT))) },
        )

        style.addLayer(
            LineLayer(LINE_LAYER, LINE_SOURCE).withProperties(
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
                PropertyFactory.lineColor(
                    Expression.coalesce(Expression.get(PROP_COLOR), Expression.literal(ROUTE_FALLBACK_COLOR)),
                ),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(12, 4f),
                        Expression.stop(16, 7f),
                    ),
                ),
            ).also { it.setFilter(Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_TRANSIT))) },
        )

        style.addLayer(
            LineLayer(WALK_LAYER, LINE_SOURCE).withProperties(
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
                PropertyFactory.lineColor("#5A6B7A"),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(12, 3f),
                        Expression.stop(16, 5f),
                    ),
                ),
                PropertyFactory.lineDasharray(arrayOf(0.1f, 1.8f)),
            ).also { it.setFilter(Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_WALK))) },
        )

        style.addLayer(
            CircleLayer(ENDPOINTS_LAYER, ENDPOINTS_SOURCE).withProperties(
                PropertyFactory.circleRadius(
                    Expression.switchCase(
                        Expression.eq(Expression.get(PROP_ROLE), Expression.literal(ROLE_DESTINATION)),
                        Expression.literal(8f),
                        Expression.literal(6f),
                    ),
                ),
                PropertyFactory.circleColor(
                    Expression.switchCase(
                        Expression.eq(Expression.get(PROP_ROLE), Expression.literal(ROLE_DESTINATION)),
                        Expression.literal("#E4573D"),
                        Expression.literal("#FFFFFF"),
                    ),
                ),
                PropertyFactory.circleStrokeColor("#1F2933"),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )

        redraw()
    }

    override fun unmount(style: Style) {
        style.removeLayer(ENDPOINTS_LAYER)
        style.removeLayer(WALK_LAYER)
        style.removeLayer(LINE_LAYER)
        style.removeLayer(CASING_LAYER)
        style.removeSource(ENDPOINTS_SOURCE)
        style.removeSource(LINE_SOURCE)
        forgetStyle()
    }

    /** Le trajet posé reste su : c'est [mount] qui le republie. */
    override fun forgetStyle() {
        lineSource = null
        endpointsSource = null
    }

    private fun redraw() {
        val lineSource = lineSource ?: return
        val candidate = candidate
        if (candidate == null) {
            lineSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            endpointsSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }

        val segments = candidate.segments.ifEmpty {
            if (candidate.coordinates.size >= 2) {
                listOf(
                    io.aule.android.core.model.RouteSegment(
                        coordinates = candidate.coordinates,
                        color = ROUTE_FALLBACK_COLOR,
                        walk = true,
                    ),
                )
            } else {
                emptyList()
            }
        }

        val features = segments.mapNotNull { segment ->
            if (segment.coordinates.size < 2) return@mapNotNull null
            val line = LineString.fromLngLats(
                segment.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) },
            )
            val props = JsonObject().apply {
                addProperty(PROP_KIND, if (segment.walk) KIND_WALK else KIND_TRANSIT)
                addProperty(PROP_COLOR, segment.color)
            }
            Feature.fromGeometry(line, props)
        }
        lineSource.setGeoJson(FeatureCollection.fromFeatures(features))

        val endpoints = mutableListOf<Feature>()
        origin?.coordinate?.let { coordinate ->
            endpoints += Feature.fromGeometry(
                Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                JsonObject().apply { addProperty(PROP_ROLE, ROLE_ORIGIN) },
            )
        }
        destination?.coordinate?.let { coordinate ->
            endpoints += Feature.fromGeometry(
                Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                JsonObject().apply { addProperty(PROP_ROLE, ROLE_DESTINATION) },
            )
        }
        endpointsSource?.setGeoJson(FeatureCollection.fromFeatures(endpoints))
    }

    private companion object {
        const val ID = "aule.route"
        const val LINE_SOURCE = "aule.route.line"
        const val ENDPOINTS_SOURCE = "aule.route.endpoints"
        const val CASING_LAYER = "aule.route.casing"
        const val LINE_LAYER = "aule.route.line.layer"
        const val WALK_LAYER = "aule.route.walk"
        const val ENDPOINTS_LAYER = "aule.route.endpoints.layer"
        const val PROP_KIND = "kind"
        const val PROP_COLOR = "color"
        const val PROP_ROLE = "role"
        const val KIND_WALK = "walk"
        const val KIND_TRANSIT = "transit"
        const val ROLE_ORIGIN = "origin"
        const val ROLE_DESTINATION = "destination"
    }
}
