package io.aule.android.core.map.layer

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.map.MapIcons
import io.aule.android.core.map.MapLayer
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * L'épingle d'une adresse choisie dans la recherche.
 *
 * Un arrêt a déjà son anneau de sélection ; une adresse n'a rien sur la
 * carte tant qu'on ne pose pas ce marqueur. Sans lui, cadrer sur un point
 * sans pastille laisserait croire que la recherche n'a rien fait.
 */
class DestinationLayer : MapLayer {

    override val id: String = ID

    private var source: GeoJsonSource? = null
    private var coordinate: Coordinate? = null

    fun setCoordinate(next: Coordinate?) {
        coordinate = next
        redraw()
    }

    override fun mount(style: Style, map: MapLibreMap) {
        source = GeoJsonSource(
            SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withBuffer(0).withTolerance(0f),
        ).also { style.addSource(it) }

        style.addLayer(
            SymbolLayer(LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(MapIcons.DESTINATION),
                // La pointe de l'épingle, pas le centre du bitmap.
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true),
            ),
        )
        redraw()
    }

    override fun unmount(style: Style) {
        style.removeLayer(LAYER)
        style.removeSource(SOURCE)
        source = null
    }

    private fun redraw() {
        val source = source ?: return
        val coordinate = coordinate
        if (coordinate == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            source.setGeoJson(
                Feature.fromGeometry(
                    Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                ),
            )
        }
    }

    private companion object {
        const val ID = "aule.destination"
        const val SOURCE = "aule.destination.source"
        const val LAYER = "aule.destination.layer"
    }
}
