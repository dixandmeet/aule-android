package io.aule.android.core.map.layer

import android.graphics.PointF
import android.graphics.RectF
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.designsystem.token.AuleTokens
import io.aule.android.core.map.MapAmbiance
import io.aule.android.core.map.MapIcons
import io.aule.android.core.map.MapInteractiveLayer
import io.aule.android.core.map.MapZoom
import io.aule.android.core.model.TransitStop
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
 * Les arrêts du réseau.
 *
 * Deux sources plutôt qu'un filtre : les **lieux** (« Commerce », qu'on donne en
 * rendez-vous) et les **quais** (« Commerce, quai B », où l'on monte). Un pôle
 * d'échange resterait illisible si ses huit quais s'affichaient de loin, d'où
 * deux seuils de zoom distincts.
 *
 * Les lieux sont dédupliqués par [TransitStop.departuresKey] : plusieurs quais
 * appartiennent au même lieu, et l'afficher huit fois n'apprend rien.
 */
class StopsLayer(
    private val onSelect: (TransitStop) -> Unit,
    private val logger: AuleLogger? = null,
) : MapInteractiveLayer {

    override val id: String = ID

    // Références **fortes** sur les sources. Sur iOS, l'enveloppe Objective-C
    // n'était pas retenue et la source devenait nulle à la fin du montage : les
    // 2 600 arrêts se chargeaient, ne s'affichaient pas, et rien n'était
    // journalisé. La cause diffère sur Android, la précaution reste la même.
    private var placesSource: GeoJsonSource? = null
    private var quaysSource: GeoJsonSource? = null

    private var stops: List<TransitStop> = emptyList()
    private var byId: Map<String, TransitStop> = emptyMap()
    private var selected: TransitStop? = null
    private var selectionSource: GeoJsonSource? = null

    /**
     * Remplace le catalogue.
     *
     * Republie immédiatement si la couche est déjà posée — sinon la donnée
     * attendrait le prochain montage, c'est-à-dire, pour un catalogue chargé une
     * seule fois, jamais.
     */
    fun setStops(next: List<TransitStop>) {
        stops = next
        byId = next.associateBy { it.id }
        redraw()
    }

    /**
     * L'anneau sous l'arrêt choisi. `null` l'efface.
     *
     * Posé dans une source à part, sous les pastilles : au-dessus, il
     * masquerait l'épingle qu'il est censé désigner.
     */
    fun setSelected(stop: TransitStop?) {
        selected = stop
        publishSelection()
    }

    override fun mount(style: Style, map: MapLibreMap) {
        val options = GeoJsonOptions().withBuffer(0).withTolerance(0.375f)

        placesSource = GeoJsonSource(PLACES_SOURCE, FeatureCollection.fromFeatures(emptyList()), options)
            .also { style.addSource(it) }
        quaysSource = GeoJsonSource(QUAYS_SOURCE, FeatureCollection.fromFeatures(emptyList()), options)
            .also { style.addSource(it) }
        selectionSource = GeoJsonSource(SELECTION_SOURCE, FeatureCollection.fromFeatures(emptyList()), options)
            .also { style.addSource(it) }

        // L'anneau passe **sous** les arrêts : posé au-dessus, il masquerait
        // l'épingle qu'il est censé désigner.
        style.addLayer(
            SymbolLayer(SELECTION_LAYER, SELECTION_SOURCE).withProperties(
                PropertyFactory.iconImage(MapIcons.STOP_SELECTED),
                PropertyFactory.iconAllowOverlap(true),
            ),
        )

        style.addLayer(
            SymbolLayer(QUAY_LAYER, QUAYS_SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                // `allowOverlap` **oui**, `ignorePlacement` **non** — les deux ne
                // font pas la même chose et un seul est un piège.
                //
                // `allowOverlap` dessine la pastille même si elle chevauche autre
                // chose : sans lui, un arrêt disparaît dès qu'un nom de rue passe
                // dessous, et un arrêt invisible rend la carte inutile. Relevé sur
                // le S21 le 16/08 — « Hôtel de Ville » s'effaçait derrière son
                // propre nom de rue.
                //
                // `ignorePlacement` sortirait la pastille de l'index de
                // collision, donc des requêtes de features : visible et
                // intouchable, le pire des deux.
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconSize(quayScale()),
            ).also { it.minZoom = MapZoom.QUAYS_FROM.toFloat() },
        )

        style.addLayer(
            SymbolLayer(PLACE_LAYER, PLACES_SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconSize(placeScale()),
            ).also {
                it.minZoom = MapZoom.STOPS_FROM.toFloat()
                // Le lieu **s'efface** là où ses quais prennent la main.
                //
                // Sans ce plafond, les deux couches se peignaient l'une sur
                // l'autre au-dessus du palier : le lieu porte la coordonnée de
                // son premier quai, donc chaque pôle payait deux symboles
                // exactement superposés — deux entrées dans l'index de
                // collision, deux réponses au hit-test, et l'ombre du plus
                // petit qui bavait sous le plus grand.
                it.maxZoom = MapZoom.QUAYS_FROM.toFloat()
            },
        )

        style.addLayer(
            SymbolLayer(PLACE_LABEL_LAYER, PLACES_SOURCE).withProperties(
                PropertyFactory.textField(Expression.get(PROP_NAME)),
                // Le fontstack doit exister côté serveur de glyphes : le style
                // n'en référence que deux, et en demander un troisième ne
                // dessine simplement aucune étiquette.
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(labelScale()),
                PropertyFactory.textAnchor("top"),
                // 1,25 **em**, donc relatif au corps : l'écart au jeton grandit
                // avec le texte, et l'étiquette ne vient jamais mordre l'anneau
                // quand les deux grossissent ensemble. À 0,9 elle chevauchait le
                // bas de la pastille dès que celle-ci a pris son pictogramme.
                PropertyFactory.textOffset(arrayOf(0f, 1.25f)),
                PropertyFactory.textMaxWidth(8f),
                PropertyFactory.textHaloWidth(1.4f),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textOptional(true),
            ).also { it.minZoom = LABELS_FROM },
        )

        // Le montage se **termine** par une publication de la donnée courante :
        // sans cela, un catalogue déjà chargé resterait invisible après une
        // bascule d'ambiance.
        redraw()
        publishSelection()
    }

    override fun unmount(style: Style) {
        style.removeLayer(PLACE_LABEL_LAYER)
        style.removeLayer(PLACE_LAYER)
        style.removeLayer(QUAY_LAYER)
        style.removeLayer(SELECTION_LAYER)
        style.removeSource(PLACES_SOURCE)
        style.removeSource(QUAYS_SOURCE)
        style.removeSource(SELECTION_SOURCE)
        forgetStyle()
    }

    /**
     * Le catalogue et la sélection restent en mémoire — les recharger coûterait
     * un aller-retour de dépôt pour une donnée qu'on a déjà. [mount] les republie.
     */
    override fun forgetStyle() {
        placesSource = null
        quaysSource = null
        selectionSource = null
    }

    override fun onAmbianceChange(ambiance: MapAmbiance, style: Style) {
        val tokens = AuleTokens.of(ambiance == MapAmbiance.DARK)
        (style.getLayer(PLACE_LABEL_LAYER) as? SymbolLayer)?.setProperties(
            PropertyFactory.textColor(tokens.onSurface.argb),
            PropertyFactory.textHaloColor(tokens.surfaceSolid.argb),
        )
        // Pas de `redraw()` ici, et c'est délibéré. Les icônes sont ré-enregistrées
        // par le contrôleur **sous les mêmes noms** : les features pointent déjà
        // sur les bonnes images, republier 2 635 objets ne changerait rien à
        // l'écran et coûterait une sérialisation complète à chaque bascule.
    }

    private fun redraw() {
        val places = LinkedHashMap<String, TransitStop>()
        val quays = mutableListOf<TransitStop>()

        for (stop in stops) {
            quays += stop
            places.putIfAbsent(stop.departuresKey, stop)
        }

        val source = placesSource
        if (source == null) {
            // Publier dans le vide est le mode d'échec de cette couche : la donnée
            // arrive, la source n'existe pas encore, et plus rien ne la redemande.
            // On le dit — mais seulement quand il y avait quelque chose à perdre :
            // un avertissement qui se déclenche à chaque lancement apprend à ne
            // plus lire les avertissements.
            if (stops.isNotEmpty()) {
                logger?.warn(
                    LogDomain.MAP,
                    "Arrêts reçus (${stops.size}) avant le montage de la couche.",
                )
            }
            return
        }

        source.setGeoJson(
            FeatureCollection.fromFeatures(places.values.map { it.toFeature(withName = true) }),
        )
        quaysSource?.setGeoJson(
            FeatureCollection.fromFeatures(quays.map { it.toFeature(withName = false) }),
        )
        logger?.info(
            LogDomain.MAP,
            "Arrêts publiés : ${places.size} lieu(x), ${quays.size} quai(s).",
        )
    }

    private fun publishSelection() {
        val source = selectionSource ?: return
        val stop = selected
        if (stop == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            source.setGeoJson(
                Feature.fromGeometry(
                    Point.fromLngLat(stop.coordinate.longitude, stop.coordinate.latitude),
                ),
            )
        }
    }

    private fun TransitStop.toFeature(withName: Boolean): Feature =
        Feature.fromGeometry(Point.fromLngLat(coordinate.longitude, coordinate.latitude)).apply {
            addStringProperty(PROP_ID, id)
            addStringProperty(
                PROP_ICON,
                if (withName) MapIcons.stopPlaceName(mode) else MapIcons.stopQuayName(mode),
            )
            if (withName) addStringProperty(PROP_NAME, departuresKey)
        }

    /**
     * L'échelle du jeton d'un lieu, du premier palier où les arrêts apparaissent
     * jusqu'à celui où les quais les remplacent.
     *
     * Une taille fixe force un compromis qui ne va à aucun des deux bouts : ce
     * qui se lit à l'échelle du trottoir fait tapis à l'échelle du quartier, et
     * l'inverse donne des confettis. Le jeton part donc à un peu plus de la
     * moitié, où seule sa silhouette compte, et arrive à taille pleine au
     * moment où son pictogramme devient lisible.
     */
    private fun placeScale(): Expression = Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        Expression.stop(MapZoom.STOPS_FROM, 0.55),
        Expression.stop(15.5, 0.80),
        Expression.stop(MapZoom.QUAYS_FROM, 0.95),
    )

    /**
     * Celle du quai. Les deux se rejoignent au palier de bascule — 20,9 dp d'un
     * côté, 22,1 de l'autre : l'écart passe inaperçu, ce qui est tout ce qu'on
     * demande à une relève.
     */
    private fun quayScale(): Expression = Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        Expression.stop(MapZoom.QUAYS_FROM, 0.85),
        Expression.stop(19.0, 1.0),
    )

    /** Le corps de l'étiquette suit le jeton, sans quoi il rétrécirait tout seul. */
    private fun labelScale(): Expression = Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        Expression.stop(LABELS_FROM.toDouble(), 11.0),
        Expression.stop(18.0, 13.0),
    )

    override fun hitTest(map: MapLibreMap, rect: RectF, point: PointF): (() -> Unit)? {
        val hits = map.queryRenderedFeatures(rect, PLACE_LAYER, QUAY_LAYER)
        if (hits.isEmpty()) return null

        // On départage par la distance **à l'écran** au doigt, et non par l'ordre
        // que MapLibre renvoie : cet ordre suit le rendu, qui n'a rien à voir avec
        // ce que l'utilisateur visait.
        val closest = hits.minByOrNull { feature ->
            val geometry = feature.geometry() as? Point ?: return@minByOrNull Float.MAX_VALUE
            val screen = map.projection.toScreenLocation(
                org.maplibre.android.geometry.LatLng(geometry.latitude(), geometry.longitude()),
            )
            val dx = screen.x - point.x
            val dy = screen.y - point.y
            dx * dx + dy * dy
        } ?: return null

        val stop = closest.getStringProperty(PROP_ID)?.let { byId[it] } ?: return null
        return { onSelect(stop) }
    }

    private companion object {
        const val ID = "aule.stops"

        const val PLACES_SOURCE = "aule.stops.places"
        const val QUAYS_SOURCE = "aule.stops.quays"

        const val PLACE_LAYER = "aule.stops.place"
        const val PLACE_LABEL_LAYER = "aule.stops.place-label"
        const val QUAY_LAYER = "aule.stops.quay"
        const val SELECTION_SOURCE = "aule.stops.selection"
        const val SELECTION_LAYER = "aule.stops.selection.layer"

        const val PROP_ID = "id"
        const val PROP_ICON = "icon"
        const val PROP_NAME = "name"

        /** Les étiquettes n'apparaissent qu'une fois les pastilles bien séparées. */
        const val LABELS_FROM = 14.5f
    }
}
