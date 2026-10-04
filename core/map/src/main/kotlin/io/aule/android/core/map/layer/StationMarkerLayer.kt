package io.aule.android.core.map.layer

import io.aule.android.core.designsystem.token.AuleTokens
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.map.MapAmbiance
import io.aule.android.core.map.MapIcons
import io.aule.android.core.map.MapLayer
import io.aule.android.core.model.TransportMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

/** Un repère géographique réel, indépendant des données opérationnelles du compte. */
class StationMarkerLayer(
    private val coordinate: Coordinate,
    private val name: String,
) : MapLayer {
    override val id = "aule.auth-station"

    override fun mount(style: Style, map: MapLibreMap) {
        val station = Feature.fromGeometry(Point.fromLngLat(coordinate.longitude, coordinate.latitude))
        style.addSource(GeoJsonSource(SOURCE, station))
        val tokens = AuleTokens.of(map.style?.uri?.contains("dark") == true)
        style.addLayer(SymbolLayer(LAYER, SOURCE).withProperties(
            PropertyFactory.iconImage(MapIcons.stopPlaceName(TransportMode.TRAM)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.textField(name),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(LABEL_SIZE),
            PropertyFactory.textAnchor("top"),
            PropertyFactory.textOffset(arrayOf(0f, 1.25f)),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textHaloWidth(LABEL_HALO),
            PropertyFactory.textColor(tokens.onSurface.argb),
            PropertyFactory.textHaloColor(tokens.surfaceSolid.argb),
        ))
    }

    override fun onAmbianceChange(ambiance: MapAmbiance, style: Style) {
        val tokens = AuleTokens.of(ambiance == MapAmbiance.DARK)
        (style.getLayer(LAYER) as? SymbolLayer)?.setProperties(
            PropertyFactory.textColor(tokens.onSurface.argb),
            PropertyFactory.textHaloColor(tokens.surfaceSolid.argb),
        )
    }

    override fun unmount(style: Style) {
        style.removeLayer(LAYER)
        style.removeSource(SOURCE)
    }

    override fun forgetStyle() = Unit

    private companion object {
        const val SOURCE = "aule.auth-station.source"
        const val LAYER = "aule.auth-station.label"
        const val LABEL_SIZE = 14f
        const val LABEL_HALO = 1.4f
    }
}
