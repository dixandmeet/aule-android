package io.aule.android.core.map.layer

import android.os.SystemClock
import com.google.gson.JsonObject
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.map.MapIcons
import io.aule.android.core.map.MapLayer
import io.aule.android.core.model.HandoverFix
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Le véhicule du collègue, pendant une relève engagée, et le point de
 * relève une fois l'arrêt choisi.
 *
 * La flotte glisse sur dix secondes ; ici la fenêtre est de cinq, celle du
 * sondage `handover_track`. Reprendre la fenêtre de la flotte ferait courir
 * le véhicule relevé avec une image de retard permanente.
 *
 * L'arrêt ne glisse pas : c'est un rendez-vous, pas une position mesurée.
 * Il apparaît dès qu'il est enregistré, indépendamment du véhicule.
 */
class HandoverLayer : MapLayer {

    override val id: String = ID
    override val isAnimated: Boolean = true

    private var source: GeoJsonSource? = null
    private var stopSource: GeoJsonSource? = null
    private var target: Mark? = null
    private var from: Mark? = null
    private var startElapsedMs: Long = 0L
    private var emptied = true
    private var stopCoordinate: Coordinate? = null
    private var stopArrived = false

    /**
     * Dernière pose rendue. C'est elle que suivrait une caméra : suivre le
     * point reçu ferait sauter le cadrage toutes les cinq secondes.
     */
    var displayed: Coordinate? = null
        private set

    fun show(fix: HandoverFix?) {
        val next = fix?.toMark()
        val rendered = displayed
        from = when {
            next == null -> null
            rendered != null -> Mark(rendered, from?.heading ?: next.heading, next.reliable)
            else -> target ?: next
        }
        target = next
        startElapsedMs = SystemClock.elapsedRealtime()
        if (next == null) {
            displayed = null
            redraw(empty = true)
        }
    }

    fun showStop(coordinate: Coordinate?, arrived: Boolean = false) {
        stopCoordinate = coordinate
        stopArrived = arrived
        redrawStop()
    }

    override fun onFrame(elapsedSeconds: Double) {
        val target = target
        if (target == null) {
            if (!emptied) redraw(empty = true)
            return
        }
        val now = SystemClock.elapsedRealtime()
        val t = ((now - startElapsedMs).toDouble() / GLIDE_WINDOW_MS).coerceIn(0.0, 1.0)
        val origin = from ?: target
        displayed = GeoMath.interpolate(origin.coordinate, target.coordinate, t)
        val heading = GeoMath.interpolateHeading(origin.heading, target.heading, t)
        redraw(
            empty = false,
            coordinate = displayed!!,
            heading = heading,
            reliable = target.reliable,
        )
    }

    override fun mount(style: Style, map: MapLibreMap) {
        source = GeoJsonSource(
            SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withSynchronousUpdate(true).withBuffer(0).withTolerance(0f),
        ).also { style.addSource(it) }
        stopSource = GeoJsonSource(
            STOP_SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withBuffer(0).withTolerance(0f),
        ).also { style.addSource(it) }

        style.addLayer(
            SymbolLayer(STOP_LAYER, STOP_SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                PropertyFactory.iconAllowOverlap(true),
            ),
        )
        style.addLayer(
            SymbolLayer(HEADING_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(MapIcons.VEHICLE_HEADING),
                PropertyFactory.iconRotate(Expression.get(PROP_HEADING)),
                PropertyFactory.iconRotationAlignment(ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
            ),
        )
        style.addLayer(
            SymbolLayer(ICON_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                PropertyFactory.iconAllowOverlap(true),
            ),
        )
        val current = displayed
        val mark = target
        if (current != null && mark != null) {
            redraw(
                empty = false,
                coordinate = current,
                heading = mark.heading,
                reliable = mark.reliable,
            )
        } else {
            redraw(empty = true)
        }
        redrawStop()
    }

    override fun unmount(style: Style) {
        style.removeLayer(HEADING_LAYER)
        style.removeLayer(ICON_LAYER)
        style.removeLayer(STOP_LAYER)
        style.removeSource(SOURCE)
        style.removeSource(STOP_SOURCE)
        source = null
        stopSource = null
    }

    private fun redraw(
        empty: Boolean,
        coordinate: Coordinate? = null,
        heading: Double = 0.0,
        reliable: Boolean = true,
    ) {
        val source = source ?: return
        emptied = empty
        if (empty || coordinate == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val properties = JsonObject().apply {
            addProperty(PROP_HEADING, heading)
            addProperty(
                PROP_ICON,
                if (reliable) MapIcons.HANDOVER_VEHICLE else MapIcons.HANDOVER_VEHICLE_STALE,
            )
        }
        source.setGeoJson(
            Feature.fromGeometry(
                Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                properties,
            ),
        )
    }

    private fun redrawStop() {
        val source = stopSource ?: return
        val coordinate = stopCoordinate
        if (coordinate == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val properties = JsonObject().apply {
            addProperty(
                PROP_ICON,
                if (stopArrived) MapIcons.HANDOVER_STOP_ARRIVED else MapIcons.HANDOVER_STOP,
            )
        }
        source.setGeoJson(
            Feature.fromGeometry(
                Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                properties,
            ),
        )
    }

    private data class Mark(
        val coordinate: Coordinate,
        val heading: Double,
        val reliable: Boolean,
    )

    private companion object {
        const val ID = "aule.handover"
        const val SOURCE = "aule.handover.source"
        const val STOP_SOURCE = "aule.handover.stop.source"
        const val ICON_LAYER = "aule.handover.icon"
        const val HEADING_LAYER = "aule.handover.heading"
        const val STOP_LAYER = "aule.handover.stop"
        const val PROP_HEADING = "heading"
        const val PROP_ICON = "icon"
        const val ALIGNMENT_MAP = "map"
        const val GLIDE_WINDOW_MS = 5_000L

        fun HandoverFix.toMark(): Mark = Mark(
            coordinate = coordinate,
            heading = heading ?: 0.0,
            reliable = isReliable,
        )
    }
}
