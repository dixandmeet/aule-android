package io.aule.android.core.map.layer

import com.google.gson.JsonObject
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.geo.PolylinePath
import io.aule.android.core.map.MapLayer
import io.aule.android.core.model.LegMode
import io.aule.android.core.model.ROUTE_FALLBACK_COLOR
import io.aule.android.core.model.RouteCandidate
import io.aule.android.core.model.RouteMode
import io.aule.android.core.model.RoutePlace
import io.aule.android.core.model.doorToDoorLegMode
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
    private var mode: RouteMode = RouteMode.TRANSIT
    private var origin: RoutePlace? = null
    private var destination: RoutePlace? = null

    /**
     * Où l'on en est, de 0 à 1 — ou `null` tant qu'on n'a pas démarré.
     *
     * ⚠️ **C'est ce qui sépare un trajet qu'on regarde d'un trajet qu'on fait.** Un plan se
     * peint en entier : on le lit avant de partir. Une navigation ne peint que ce qui reste —
     * sinon, à l'arrivée, la carte porte encore la totalité du chemin parcouru, et le trait
     * sous les pieds ne dit plus rien (recette du 18/09/2026, BUG-AND-022).
     */
    private var progress: Double? = null

    /**
     * [mode] est le mode **demandé**. Sans lui, un porte-à-porte se peindrait
     * toujours en pointillé : le moteur ne rend aucun `segments` sur ce chemin,
     * et rien dans sa réponse ne dit qu'on roule — voir [redraw].
     */
    fun setTrace(
        candidate: RouteCandidate?,
        mode: RouteMode,
        origin: RoutePlace?,
        destination: RoutePlace?,
    ) {
        this.candidate = candidate
        this.mode = mode
        this.origin = origin
        this.destination = destination
        redraw()
    }

    /**
     * L'avancement du guidage, le long de [RouteCandidate.paintedCoordinates].
     *
     * `null` arrête la navigation et rend le tracé entier : c'est ce que fait la fin d'un
     * guidage, et c'est aussi l'état d'un trajet qu'on n'a pas encore commencé.
     *
     * ⚠️ **La même polyligne que `JourneyPlan.points`**, et c'est ce qui rend la fraction
     * transposable : les deux concatènent les tronçons dans le même ordre.
     */
    fun setProgress(t: Double?) {
        val next = t?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
        if (next == progress) return
        progress = next
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
            ).also {
                it.setFilter(
                    Expression.any(
                        Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_TRANSIT)),
                        // Le ruban de navigation prend le même liseré : c'est lui qui le
                        // détache de la chaussée, et sans lui un trait épais se confond avec
                        // la voie qu'il suit.
                        Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_WALK_NAV)),
                    ),
                )
            },
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

        // ⚠️ **Le pointillé de la marche n'est pas un tracé de navigation.** Il dit « vous
        // ferez ce bout à pied » sur un plan qu'on lit ; sous les pieds de quelqu'un qui
        // marche, il ne tient pas la chaussée et ne se voit pas du coin de l'œil. Un guidage
        // demande une épaisseur, et un trait plein (recette du 18/09/2026, BUG-AND-022).
        //
        // ⚠️ **Deux couches, et non une propriété.** `line-dasharray` n'est pas pilotable par
        // les données dans MapLibre : le pointillé et le plein ne peuvent pas cohabiter dans
        // la même couche, quelle que soit l'expression qu'on y mettrait.
        style.addLayer(
            LineLayer(WALK_NAV_LAYER, LINE_SOURCE).withProperties(
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
                // ⚠️ **L'encre de la navigation, pas celle du tronçon.** Le moteur donne aux
                // jambes de marche un gris de **plan** — juste quand on lit un trajet avant de
                // partir, terne sous les pieds de quelqu'un qui marche. Le ruban qu'on suit
                // porte donc sa propre couleur, la même quel que soit le tronçon.
                PropertyFactory.lineColor(WALK_NAV_COLOR),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(12, 5f),
                        Expression.stop(16, 9f),
                        Expression.stop(18, 13f),
                    ),
                ),
            ).also { it.setFilter(Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_WALK_NAV))) },
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
        style.removeLayer(WALK_NAV_LAYER)
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

        // Le porte-à-porte arrive sans `segments` : `mode=foot` et `mode=car`
        // rendent la même forme, une géométrie seule. Le pointillé ne se pose
        // donc pas sur la réponse mais sur la **demande**, par la règle du
        // domaine — sinon un trajet en voiture se peint comme une marche.
        val segments = candidate.segments.ifEmpty {
            if (candidate.coordinates.size >= 2) {
                listOf(
                    io.aule.android.core.model.RouteSegment(
                        coordinates = candidate.coordinates,
                        color = ROUTE_FALLBACK_COLOR,
                        walk = mode.doorToDoorLegMode(candidate.steps) != LegMode.CAR,
                    ),
                )
            } else {
                emptyList()
            }
        }

        // Ce qui reste à faire, quand on est en train de le faire. Voir [remainingSegments].
        val painted = progress?.let { remainingSegments(segments, it) } ?: segments
        val guiding = progress != null

        val features = painted.mapNotNull { segment ->
            if (segment.coordinates.size < 2) return@mapNotNull null
            val line = LineString.fromLngLats(
                segment.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) },
            )
            val props = JsonObject().apply {
                addProperty(
                    PROP_KIND,
                    when {
                        !segment.walk -> KIND_TRANSIT
                        guiding -> KIND_WALK_NAV
                        else -> KIND_WALK
                    },
                )
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

    /**
     * Les tronçons amputés de ce qui est déjà parcouru.
     *
     * ⚠️ **La fraction porte sur la concaténation des tronçons**, pas sur chacun d'eux : c'est
     * la même polyligne que `JourneyPlan.points`, et c'est de là que vient le `t` du guidage.
     * On coupe donc à une distance cumulée, et le tronçon qui contient la coupure garde un
     * sommet interpolé — sans lui, le ruban repartirait du sommet suivant, jusqu'à quelques
     * dizaines de mètres devant les pieds dans un virage large.
     */
    private fun remainingSegments(
        segments: List<io.aule.android.core.model.RouteSegment>,
        t: Double,
    ): List<io.aule.android.core.model.RouteSegment> {
        val total = segments.sumOf { PolylinePath(it.coordinates).length }
        if (total <= 0.0) return segments
        var cut = t * total
        val kept = mutableListOf<io.aule.android.core.model.RouteSegment>()
        for (segment in segments) {
            val path = PolylinePath(segment.coordinates)
            val length = path.length
            if (cut >= length) {
                // Tronçon entièrement derrière : il ne reste rien à en peindre.
                cut -= length
                continue
            }
            kept += if (cut <= 0.0) segment else segment.copy(coordinates = clipped(path, cut))
            cut = 0.0
        }
        // Tout est derrière : on ne rend pas la liste entière, on ne rend rien. Un dernier
        // mètre repeint en entier se lirait comme un trajet qui n'a pas commencé.
        return kept
    }

    /** Le tracé à partir d'une distance donnée, sommet interpolé compris. */
    private fun clipped(path: PolylinePath, from: Double): List<Coordinate> {
        val end = path.cumulative.indexOfFirst { it > from }.takeIf { it > 0 } ?: return emptyList()
        val start = end - 1
        val span = path.cumulative[end] - path.cumulative[start]
        val fraction = if (span <= 0.0) 0.0 else (from - path.cumulative[start]) / span
        return buildList {
            add(GeoMath.interpolate(path.points[start], path.points[end], fraction))
            addAll(path.points.subList(end, path.points.size))
        }
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
        const val WALK_NAV_LAYER = "aule.route.walk.nav"
        const val KIND_WALK = "walk"
        const val KIND_WALK_NAV = "walk-nav"
        const val KIND_TRANSIT = "transit"

        /**
         * L'encre du ruban qu'on suit à pied.
         *
         * Le teal clair de la marque — assez saturé pour se détacher du fond de carte en
         * plein jour comme de nuit, et assez sombre pour porter le blanc de son liseré.
         */
        const val WALK_NAV_COLOR = "#137B7F"
        const val ROLE_ORIGIN = "origin"
        const val ROLE_DESTINATION = "destination"
    }
}
