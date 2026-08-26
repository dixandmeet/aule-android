package io.aule.android.core.map.layer

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.map.MapIcons
import io.aule.android.core.map.MapLayer
import io.aule.android.core.model.TransportMode
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
 * L'arrêt qu'on est allé voir depuis la desserte d'une ligne.
 *
 * ## Pourquoi une couche à elle
 *
 * La caméra sait déjà se poser sur un arrêt de la desserte. Ce qu'elle ne sait
 * pas, c'est **dire lequel** : elle centre une coordonnée, et l'écran montre un
 * carrefour. Quand le catalogue d'arrêts couvre par chance la zone, sa pastille
 * répond ; quand la ligne traverse une commune que l'on n'a jamais approchée, il
 * n'y a rien au centre, et le voyage se termine sur un lieu anonyme.
 *
 * Ce marqueur ne dépend d'aucun catalogue : il vient de la desserte elle-même,
 * qui porte la position de chacun de ses arrêts.
 *
 * ## Il parle la langue d'un arrêt choisi
 *
 * L'anneau est celui de [MapIcons.STOP_SELECTED] — le même que sous l'arrêt
 * qu'on touche sur la carte. C'est la même intention (« celui-ci »), donc la
 * même marque : inventer un second vocabulaire pour le même geste ferait deux
 * façons de désigner et aucune qui se reconnaisse.
 *
 * La pastille au centre est celle du mode de la ligne. Elle est **doublée** par
 * celle du catalogue quand il connaît l'arrêt : deux fois la même image au même
 * point, ce qui ne se voit pas. Sans mode connu, on ne dessine pas de pastille —
 * l'anneau seul marque l'endroit, et c'est mieux qu'une couleur inventée.
 *
 * ## Il ne porte pas de nom, et c'est voulu
 *
 * La première version en écrivait un. À l'essai sur le S21, la carte affichait
 * **deux** « Souillarderie » à trente mètres l'un de l'autre : la desserte GTFS
 * donne la position du **quai**, sur le rail, quand le catalogue donne celle du
 * **lieu**, qui agrège les quais. Deux points justes, deux étiquettes justes, et
 * un écran qui laisse croire à deux arrêts.
 *
 * L'index de collision ne départage pas ce cas — les deux boîtes ne se touchent
 * pas. Le nom reste donc là où il ne peut pas se dédoubler : dans le volet, sur
 * le rang que l'on vient de toucher, qui se teinte. À l'écran, l'anneau répond
 * à « où », et il n'avait jamais eu à répondre à « lequel ».
 */
class LineStopLayer : MapLayer {

    override val id: String = ID

    private var source: GeoJsonSource? = null
    private var coordinate: Coordinate? = null
    private var mode: TransportMode? = null

    /** Pose le marqueur, ou l'efface avec une coordonnée nulle. */
    fun setStop(coordinate: Coordinate?, mode: TransportMode? = null) {
        this.coordinate = coordinate
        this.mode = mode
        redraw()
    }

    override fun mount(style: Style, map: MapLibreMap) {
        source = GeoJsonSource(
            SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withBuffer(0).withTolerance(0f),
        ).also { style.addSource(it) }

        // L'anneau passe sous la pastille : au-dessus, il masquerait ce qu'il
        // désigne — la même raison que dans `StopsLayer`.
        style.addLayer(
            SymbolLayer(RING_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(MapIcons.STOP_SELECTED),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )

        style.addLayer(
            SymbolLayer(DOT_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )

        // Le montage se termine par une publication : la source qu'on vient de
        // créer est vide, et un arrêt déjà visé n'aurait plus rien pour se
        // redessiner après une bascule d'ambiance.
        redraw()
    }

    override fun unmount(style: Style) {
        style.removeLayer(DOT_LAYER)
        style.removeLayer(RING_LAYER)
        style.removeSource(SOURCE)
        forgetStyle()
    }

    /** L'arrêt visé reste su : c'est [mount] qui le republie. */
    override fun forgetStyle() {
        source = null
    }

    private fun redraw() {
        val source = source ?: return
        val coordinate = coordinate
        if (coordinate == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val feature = Feature.fromGeometry(
            Point.fromLngLat(coordinate.longitude, coordinate.latitude),
        ).apply {
            // Un nom d'image qu'aucun style n'enregistre plutôt qu'une chaîne
            // vide : `iconImage` sur une image absente ne dessine rien et ne dit
            // rien, ce qui est le comportement voulu pour un mode inconnu.
            addStringProperty(PROP_ICON, mode?.let { MapIcons.stopPlaceName(it) } ?: NO_ICON)
        }
        source.setGeoJson(feature)
    }

    private companion object {
        const val ID = "aule.line-stop"
        const val SOURCE = "aule.line-stop.source"
        const val RING_LAYER = "aule.line-stop.ring"
        const val DOT_LAYER = "aule.line-stop.dot"

        const val PROP_ICON = "icon"

        /** Un nom d'image qu'aucun style n'enregistre : rien ne se dessine. */
        const val NO_ICON = "aule.none"
    }
}
