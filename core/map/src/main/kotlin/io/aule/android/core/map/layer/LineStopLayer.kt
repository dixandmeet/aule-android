package io.aule.android.core.map.layer

import android.graphics.PointF
import android.graphics.RectF
import io.aule.android.core.designsystem.token.AuleTokens
import io.aule.android.core.designsystem.token.parseLineColor
import io.aule.android.core.map.MapAmbiance
import io.aule.android.core.map.MapInteractiveLayer
import io.aule.android.core.map.MapZoom
import io.aule.android.core.model.LineStopMarker
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Les arrêts du parcours qu'on consulte, posés sur son tracé.
 *
 * ## Ce qu'elle ajoute aux épingles du réseau
 *
 * [StopsLayer] répond à « qu'est-ce qu'il y a autour ? » : elle peint les 2 600
 * quais du catalogue, sans distinguer ce qui appartient à la ligne qu'on
 * regarde, et seulement à partir du seuil de quartier ([MapZoom.STOPS_FROM]).
 * Une fiche de ligne pose l'autre question — « où s'arrête **celle-ci** ? » —,
 * à un cadrage qui tient les quinze kilomètres du tracé.
 *
 * Les marqueurs d'ici sont donc **exactement** ceux de la liste du volet — même
 * source, même regroupement, voir `buildLineStopMarkers` — et ils portent la
 * couleur de la ligne. C'est ce qui permet de lire la desserte sur la carte
 * sans lire la liste, et l'inverse.
 *
 * ## Pourquoi un cercle et non l'épingle du catalogue
 *
 * ⚠️ **C'est la correction qui a motivé cette refonte.** La couche peignait
 * l'épingle de lieu du catalogue général — un jeton de 26 dp à pictogramme,
 * celui-là même qui répond à « qu'est-ce qu'il y a autour ». À l'échelle d'une
 * ligne entière, la C3 en alignait trente-quatre sur quinze kilomètres : à
 * zoom 10,6, deux arrêts voisins sont à une vingtaine de pixels et les jetons
 * font trois fois cette largeur. Le résultat mesuré sur le S21 le 05/09/2026 :
 * un chapelet continu de pastilles qui **recouvre le tracé qu'il précise**,
 * s'empile par trois aux terminus, et déborde assez de part et d'autre du trait
 * pour qu'on lise « des arrêts posés à côté de la ligne ». Capture à l'appui.
 *
 * Le dessin est donc celui d'iOS (`LineStopsLayer.swift`), repris tel quel :
 * cœur de la couleur du **fond**, contour de la couleur de la ligne, et un
 * rayon qui suit le zoom — 1,8 px à l'échelle du réseau, 6 px à celle de la
 * rue. À l'échelle de la ligne entière un arrêt est un point ; en approchant il
 * redevient une pastille, quand la place existe.
 *
 * Les deux bouts portent un anneau plus large : ce sont les deux rangs qu'on
 * cherche des yeux pour savoir dans quel sens la desserte se lit.
 *
 * ## Le nom attend, et n'a plus besoin de partager son symbole
 *
 * Les deux ne répondent pas à la même question. La pastille dit « il y a un
 * arrêt ici », et elle le dit juste dès le cadrage d'ensemble. Le nom dit
 * « **cet** arrêt s'appelle ainsi », et il ne peut le dire que s'il touche sa
 * pastille et aucune autre. À l'échelle d'une ligne entière il en est
 * incapable : il faudrait un kilomètre entre deux arrêts pour qu'un nom ne
 * penche pas vers son voisin. Le nom attend donc [MapZoom.STOP_LABELS_FROM],
 * où la mesure passe — cette constante porte le calcul, et le catalogue général
 * s'y tient déjà : fermer le volet d'une ligne ne fait ni apparaître ni sauter
 * un nom.
 *
 * ⚠️ **Un seul seuil pour tous les noms, terminus compris.** iOS écrit les
 * bouts deux paliers plus tôt ; on ne le suit pas, parce que la mesure de
 * [MapZoom.STOP_LABELS_FROM] vaut aussi pour eux — Armor a un voisin à cinq
 * cents mètres — et qu'un terminus mal rattaché est le nom qu'on croit le plus.
 *
 * Un seul seuil, c'est aussi **une seule couche de noms**, et cela écarte un
 * piège qui a déjà coûté une correction ici : mbgl place les symboles du dessus
 * vers le dessous, donc la couche la plus haute est servie la première, sur un
 * index de collision encore vide. Deux couches de noms sur la même source se
 * disputeraient la place au lieu de se la céder.
 *
 * ## Ce qui reste quand un arrêt est désigné
 *
 * Les autres passent au second plan ([DIMMED_OPACITY]) : c'est le seul moyen
 * d'établir une hiérarchie sans grossir le marqueur choisi jusqu'à couvrir ses
 * voisins — trente-quatre pastilles de même poids ne désignent rien, quelle que
 * soit celle qu'on entoure. L'arrêt désigné est peint **plein** et retiré des
 * couches ordinaires : laissé dedans, il y serait peint une seconde fois, et le
 * décalage des deux rayons se lirait comme un tremblement.
 */
class LineStopLayer(
    private val onSelectStop: ((LineStopMarker) -> Unit)? = null,
) : MapInteractiveLayer {

    override val id: String = ID

    private var stopsSource: GeoJsonSource? = null
    private var selectionSource: GeoJsonSource? = null

    /**
     * Les cercles posés, retenus pour les assourdir et les filtrer sans relire
     * le style.
     *
     * La référence est directe pour la raison de [TransitLinesLayer] : ces deux
     * gestes n'arrivent pas dans un rappel du style, et lui redemander ses
     * couches supposerait un style non nul au moment précis où il peut l'être —
     * pendant un rechargement, c'est-à-dire quand la desserte se repose.
     */
    private var dots: CircleLayer? = null
    private var ends: CircleLayer? = null
    private var labels: SymbolLayer? = null

    private var markers: List<LineStopMarker> = emptyList()
    private var byId: Map<String, LineStopMarker> = emptyMap()
    private var focusedStopId: String? = null
    private var ambiance: MapAmbiance = MapAmbiance.LIGHT

    /**
     * La couleur de la ligne consultée, telle que le GTFS l'écrit. `null` sur
     * une ligne dont l'index ne connaît pas la teinte : les pastilles prennent
     * alors le gris de repli, qui ne prétend pas être une couleur de ligne.
     */
    private var lineColorHex: String? = null

    /**
     * Met à jour les arrêts du parcours affiché.
     *
     * @param markers les arrêts dans l'ordre du parcours — ceux-là mêmes que la
     *   liste du volet montre.
     * @param colorHex la couleur GTFS de la ligne, « #RRGGBB ».
     * @param focusedStopId l'identifiant du marqueur visé, ou `null`.
     */
    fun setStops(
        markers: List<LineStopMarker>,
        colorHex: String? = null,
        focusedStopId: String? = null,
    ) {
        this.markers = markers
        this.byId = markers.associateBy { it.id }
        this.lineColorHex = colorHex
        // Un arrêt désigné dans le parcours précédent n'existe plus dans
        // celui-ci : le garder laisserait la pastille pleine posée sur un point
        // que plus rien n'occupe, et le reste de la desserte assourdi pour
        // personne.
        this.focusedStopId = focusedStopId?.takeIf { it in byId }
        redraw()
    }

    override fun mount(style: Style, map: MapLibreMap) {
        val options = GeoJsonOptions().withBuffer(0).withTolerance(0.375f)

        stopsSource = GeoJsonSource(
            STOPS_SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            options,
        ).also { style.addSource(it) }

        selectionSource = GeoJsonSource(
            SELECTION_SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            options,
        ).also { style.addSource(it) }

        // Le halo passe **sous** les pastilles : posé au-dessus, il voilerait
        // l'arrêt qu'il est censé désigner.
        style.addLayer(
            CircleLayer(HALO_LAYER, SELECTION_SOURCE).withProperties(
                PropertyFactory.circleRadius(haloRadius()),
                PropertyFactory.circleColor(Expression.get(PROP_COLOR)),
                PropertyFactory.circleOpacity(HALO_OPACITY),
                PropertyFactory.circleBlur(HALO_BLUR),
            ),
        )

        dots = CircleLayer(DOT_LAYER, STOPS_SOURCE).withProperties(
            PropertyFactory.circleRadius(stopRadius()),
            PropertyFactory.circleColor(casingColor()),
            PropertyFactory.circleStrokeWidth(stopStrokeWidth()),
            PropertyFactory.circleStrokeColor(Expression.get(PROP_COLOR)),
        ).also {
            fade(it)
            style.addLayer(it)
        }

        ends = CircleLayer(END_LAYER, STOPS_SOURCE).withProperties(
            PropertyFactory.circleRadius(endRadius()),
            PropertyFactory.circleColor(casingColor()),
            PropertyFactory.circleStrokeWidth(endStrokeWidth()),
            PropertyFactory.circleStrokeColor(Expression.get(PROP_COLOR)),
        ).also {
            fade(it)
            style.addLayer(it)
        }

        // L'arrêt désigné est peint **plein**, et non entouré d'un anneau de
        // plus : sur une ligne qui en compte trente-quatre, l'inversion du
        // contraste se repère d'un coup d'œil là où un cerclage supplémentaire
        // demande de comparer.
        style.addLayer(
            CircleLayer(SELECTION_LAYER, SELECTION_SOURCE).withProperties(
                PropertyFactory.circleRadius(selectionRadius()),
                PropertyFactory.circleColor(Expression.get(PROP_COLOR)),
                PropertyFactory.circleStrokeWidth(SELECTION_STROKE),
                PropertyFactory.circleStrokeColor(casingColor()),
            ),
        )

        val tokens = AuleTokens.of(ambiance == MapAmbiance.DARK)
        labels = SymbolLayer(LABEL_LAYER, STOPS_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get(PROP_NAME)),
            // Le fontstack doit exister côté serveur de glyphes : le style n'en
            // référence que deux, et en demander un troisième ne dessine
            // simplement aucune étiquette.
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(labelSize()),
            PropertyFactory.textColor(tokens.onSurface.argb),
            PropertyFactory.textHaloColor(tokens.surfaceSolid.argb),
            PropertyFactory.textHaloWidth(HALO_WIDTH),
            // ## Dessus ou dessous — et **jamais** sur les côtés
            //
            // Deux places valent mieux qu'une le long d'un tracé en diagonale,
            // mais les quatre côtés ne se valent pas. `textRadialOffset` écarte
            // le **bord** du texte, pas son milieu : posé à droite, « Sophie
            // Trébuchet » déroule deux cents pixels vers l'est et son centre
            // finit plus près de la pastille suivante que de la sienne. On ne
            // lit pas une étiquette au bord dont elle part, on la lit là où elle
            // pèse.
            //
            // ⚠️ Les ancres variables **ignorent** `textOffset` : l'écart se dit
            // en `textRadialOffset`, et `textJustify("auto")` est exigé avec
            // elles.
            PropertyFactory.textVariableAnchor(arrayOf("top", "bottom")),
            PropertyFactory.textRadialOffset(LABEL_OFFSET_EM),
            PropertyFactory.textJustify("auto"),
            PropertyFactory.textMaxWidth(LABEL_MAX_WIDTH),
            // Le nom cède à la collision, jamais la pastille : un nom masqué se
            // répare en zoomant, un arrêt absent ne se répare pas.
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textOptional(true),
        ).also {
            it.minZoom = LABELS_FROM
            it.textOpacityTransition = TransitionOptions(SELECTION_FADE_MS, 0)
            style.addLayer(it)
        }

        // Le nom de l'arrêt désigné, **à part et par-dessus tout**.
        //
        // Il ne peut pas venir de la couche ci-dessus : elle s'assourdit dès
        // qu'une sélection existe, et le nom de l'arrêt qu'on vient d'ouvrir
        // passerait au gris avec les trente-trois autres — l'inverse exact de ce
        // qu'on cherche. Il ignore aussi la collision et le seuil de zoom :
        // c'est le seul nom de la desserte qui doive s'écrire quoi qu'il arrive.
        style.addLayer(
            SymbolLayer(SELECTION_LABEL_LAYER, SELECTION_SOURCE).withProperties(
                PropertyFactory.textField(Expression.get(PROP_NAME)),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(SELECTION_LABEL_SIZE),
                PropertyFactory.textColor(tokens.onSurface.argb),
                PropertyFactory.textHaloColor(tokens.surfaceSolid.argb),
                PropertyFactory.textHaloWidth(HALO_WIDTH),
                PropertyFactory.textVariableAnchor(arrayOf("top", "bottom")),
                PropertyFactory.textRadialOffset(LABEL_OFFSET_EM),
                PropertyFactory.textJustify("auto"),
                PropertyFactory.textMaxWidth(LABEL_MAX_WIDTH),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textOptional(false),
            ),
        )

        // Le montage se termine par une publication de l'état courant : les
        // sources qu'on vient de créer sont vides, et une desserte posée avant
        // un rechargement de style — passer en mode sombre suffit — n'a aucune
        // raison d'être reposée.
        redraw()
    }

    override fun unmount(style: Style) {
        for (layer in PAINTED) style.removeLayer(layer)
        style.removeLayer(SELECTION_LABEL_LAYER)
        style.removeLayer(SELECTION_LAYER)
        style.removeLayer(HALO_LAYER)
        style.removeSource(STOPS_SOURCE)
        style.removeSource(SELECTION_SOURCE)
        forgetStyle()
    }

    override fun forgetStyle() {
        stopsSource = null
        selectionSource = null
        dots = null
        ends = null
        labels = null
    }

    override fun onAmbianceChange(ambiance: MapAmbiance, style: Style) {
        this.ambiance = ambiance
        val tokens = AuleTokens.of(ambiance == MapAmbiance.DARK)
        for (layer in listOf(DOT_LAYER, END_LAYER)) {
            (style.getLayer(layer) as? CircleLayer)?.setProperties(
                PropertyFactory.circleColor(casingColor()),
            )
        }
        (style.getLayer(SELECTION_LAYER) as? CircleLayer)?.setProperties(
            PropertyFactory.circleStrokeColor(casingColor()),
        )
        for (layer in listOf(LABEL_LAYER, SELECTION_LABEL_LAYER)) {
            (style.getLayer(layer) as? SymbolLayer)?.setProperties(
                PropertyFactory.textColor(tokens.onSurface.argb),
                PropertyFactory.textHaloColor(tokens.surfaceSolid.argb),
            )
        }
    }

    private fun redraw() {
        val stopsSource = stopsSource ?: return
        val selectionSource = selectionSource ?: return

        val placed = markers.filter { it.coordinate != null }
        stopsSource.setGeoJson(FeatureCollection.fromFeatures(placed.map(::feature)))

        val focused = placed.firstOrNull { it.id == focusedStopId }
        selectionSource.setGeoJson(
            if (focused == null) {
                FeatureCollection.fromFeatures(emptyList())
            } else {
                FeatureCollection.fromFeatures(listOf(feature(focused)))
            },
        )

        applyDimming()
        applyFilters()
    }

    /**
     * Assourdit la desserte quand un arrêt est désigné, la rend quand plus rien
     * ne l'est.
     *
     * L'opacité passe par une **transition** et non par un saut : c'est le fondu
     * d'une ligne mise en avant sur le réseau, et c'est lui qui fait comprendre
     * que les autres arrêts sont mis de côté plutôt qu'effacés.
     *
     * ⚠️ **Une opacité de couche, jamais une propriété de la donnée.** MapLibre
     * n'anime pas ce qui est piloté par la donnée : mise dans la feature,
     * l'opacité sauterait, et le saut se lit comme un rafraîchissement.
     */
    private fun applyDimming() {
        val opacity = if (focusedStopId == null) 1f else DIMMED_OPACITY
        for (circle in listOfNotNull(dots, ends)) {
            circle.setProperties(
                PropertyFactory.circleOpacity(opacity),
                PropertyFactory.circleStrokeOpacity(opacity),
            )
        }
        labels?.setProperties(PropertyFactory.textOpacity(opacity))
    }

    /**
     * Répartit les marqueurs entre les deux couches de cercles, **et retire des
     * trois l'arrêt désigné**.
     *
     * Le second point n'est pas une optimisation : la couche de sélection le
     * peint plein, par-dessus, avec son nom. Laissé dans les couches
     * ordinaires, il y serait peint une seconde fois — pastille assourdie sous
     * pastille pleine, nom gris sous nom noir —, et le décalage de leurs deux
     * rayons se lirait comme un tremblement.
     */
    private fun applyFilters() {
        dots?.setFilter(filter(ends = false))
        ends?.setFilter(filter(ends = true))
        labels?.setFilter(notFocused() ?: Expression.literal(true))
    }

    /** Le filtre d'une couche de cercles : son côté de la desserte, moins l'arrêt désigné. */
    private fun filter(ends: Boolean): Expression {
        val side = Expression.eq(Expression.get(PROP_IS_END), Expression.literal(ends))
        val rest = notFocused() ?: return side
        return Expression.all(side, rest)
    }

    private fun notFocused(): Expression? = focusedStopId?.let {
        Expression.neq(Expression.get(PROP_ID), Expression.literal(it))
    }

    private fun feature(marker: LineStopMarker): Feature {
        val coordinate = marker.coordinate!!
        return Feature.fromGeometry(
            Point.fromLngLat(coordinate.longitude, coordinate.latitude),
        ).apply {
            addStringProperty(PROP_ID, marker.id)
            addStringProperty(PROP_NAME, marker.name)
            addBooleanProperty(PROP_IS_END, marker.role.isEnd)
            // La couleur voyage **dans la donnée** plutôt que dans la couche :
            // c'est ce qui permet de la reprendre sans reconstruire les couches,
            // et c'est déjà ce que fait le tracé du réseau.
            addStringProperty(PROP_COLOR, strokeColorHex())
        }
    }

    /**
     * La couleur de la ligne, ou le gris de repli.
     *
     * Le repli n'est pas un détail : une ligne dont l'index ignore la teinte
     * doit quand même montrer sa desserte, et un contour transparent ferait
     * disparaître trente-quatre arrêts sans rien signaler.
     */
    private fun strokeColorHex(): String {
        val parsed = parseLineColor(lineColorHex)
        val red = (parsed.red * 255).toInt().coerceIn(0, 255)
        val green = (parsed.green * 255).toInt().coerceIn(0, 255)
        val blue = (parsed.blue * 255).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", red, green, blue)
    }

    /**
     * Blanc de jour, presque noir de nuit — le cœur de la pastille prend la
     * couleur du **fond**, comme le liseré du tracé. Un blanc figé ferait des
     * confettis clairs sur une carte de nuit.
     */
    private fun casingColor(): Int = AuleTokens.of(ambiance == MapAmbiance.DARK).surfaceSolid.argb

    override fun hitTest(map: MapLibreMap, rect: RectF, point: PointF): (() -> Unit)? {
        if (markers.isEmpty()) return null
        val hits = map.queryRenderedFeatures(rect, DOT_LAYER, END_LAYER, SELECTION_LAYER)
        if (hits.isEmpty()) return null

        // On départage par la distance **à l'écran** au doigt, et non par l'ordre
        // que MapLibre renvoie : cet ordre suit le rendu, qui n'a rien à voir
        // avec ce que l'utilisateur visait.
        val closest = hits.minByOrNull { feature ->
            val geometry = feature.geometry() as? Point ?: return@minByOrNull Float.MAX_VALUE
            val screen = map.projection.toScreenLocation(
                org.maplibre.android.geometry.LatLng(geometry.latitude(), geometry.longitude()),
            )
            val dx = screen.x - point.x
            val dy = screen.y - point.y
            dx * dx + dy * dy
        } ?: return null

        val marker = closest.getStringProperty(PROP_ID)?.let { byId[it] } ?: return null
        return { onSelectStop?.invoke(marker) }
    }

    /**
     * Le fondu d'entrée et de sortie de l'assourdissement. Posé au montage :
     * sans lui, la mise au second plan serait un à-coup, qu'on lit comme un
     * rafraîchissement.
     */
    private fun fade(layer: CircleLayer) {
        layer.circleOpacityTransition = TransitionOptions(SELECTION_FADE_MS, 0)
        layer.circleStrokeOpacityTransition = TransitionOptions(SELECTION_FADE_MS, 0)
    }

    private companion object {
        const val ID = "aule.line-stop"
        const val STOPS_SOURCE = "aule.line-stop.stops.source"
        const val SELECTION_SOURCE = "aule.line-stop.selection.source"

        const val HALO_LAYER = "aule.line-stop.halo"

        /** Les arrêts intermédiaires : un point, puis une pastille en approchant. */
        const val DOT_LAYER = "aule.line-stop.dot"

        /** Les deux bouts, qui disent dans quel sens la desserte se lit. */
        const val END_LAYER = "aule.line-stop.end"
        const val SELECTION_LAYER = "aule.line-stop.selection"
        const val LABEL_LAYER = "aule.line-stop.label"
        const val SELECTION_LABEL_LAYER = "aule.line-stop.selection-label"

        /** Les couches qui portent la desserte, dans l'ordre où on les retire. */
        val PAINTED = listOf(LABEL_LAYER, END_LAYER, DOT_LAYER)

        const val PROP_ID = "id"
        const val PROP_NAME = "name"
        const val PROP_COLOR = "color"
        const val PROP_IS_END = "isEnd"

        /** Le seuil est commun aux deux couches d'arrêts — il porte sa mesure. */
        val LABELS_FROM = MapZoom.STOP_LABELS_FROM.toFloat()

        /**
         * Ce que gardent les arrêts qu'on n'a pas désignés.
         *
         * Assez pour qu'on lise encore le tracé de la ligne — c'est le contexte
         * de l'arrêt qu'on regarde —, assez peu pour que la hiérarchie soit
         * immédiate.
         */
        const val DIMMED_OPACITY = 0.32f

        const val HALO_OPACITY = 0.22f
        const val HALO_BLUR = 0.5f
        const val HALO_WIDTH = 1.4f
        const val SELECTION_STROKE = 3f
        const val SELECTION_LABEL_SIZE = 13f

        /** 1,25 **em**, donc relatif au corps : l'écart grandit avec le texte. */
        const val LABEL_OFFSET_EM = 1.25f
        const val LABEL_MAX_WIDTH = 8f

        /**
         * Le fondu de la mise au second plan. Celui d'une ligne désignée sur le
         * réseau : les deux gestes disent la même chose et doivent durer pareil.
         */
        const val SELECTION_FADE_MS = 200L

        /**
         * Un point à l'échelle du réseau, une pastille à celle de la rue.
         *
         * Les quatre paliers viennent d'iOS (`LineStopsLayer.stopRadius`), où ils
         * ont été réglés contre le même moteur. Le premier est celui du réseau
         * lisible — au-dessous, on regarde le pays, pas une ligne.
         */
        fun stopRadius(): Expression = interpolate(1.8, 2.8, 4.4, 6.0)

        fun stopStrokeWidth(): Expression = interpolate(0.8, 1.2, 2.0, 2.6)

        fun endRadius(): Expression = interpolate(2.8, 4.0, 6.0, 8.0)

        fun endStrokeWidth(): Expression = interpolate(1.4, 2.0, 3.0, 3.6)

        fun selectionRadius(): Expression = interpolate(3.4, 5.0, 7.0, 9.0)

        /**
         * Le halo est large **en pixels d'écran**, donc plus large que la
         * pastille à tous les zooms : c'est lui qui fait sortir l'arrêt désigné
         * d'un quartier dense.
         */
        fun haloRadius(): Expression = Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(NETWORK_LEGIBLE, 10.0),
            Expression.stop(15.0, 20.0),
            Expression.stop(STREET_SCALE, 28.0),
        )

        /**
         * Le corps du nom, au **cran du catalogue général**.
         *
         * Les deux couches ne sont jamais peintes ensemble, mais on passe de
         * l'une à l'autre en fermant un volet : deux échelles feraient sauter la
         * taille de tous les noms sur ce seul geste.
         */
        fun labelSize(): Expression = Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(MapZoom.STOP_LABELS_FROM, 11.0),
            Expression.stop(18.0, 13.0),
        )

        /** Le zoom sous lequel une ligne n'est plus lisible — vue nationale. */
        const val NETWORK_LEGIBLE = 10.0

        /** Le zoom du trottoir, où la pastille prend sa pleine taille. */
        const val STREET_SCALE = 17.0

        private fun interpolate(
            atNetwork: Double,
            atDistrict: Double,
            atNeighbourhood: Double,
            atStreet: Double,
        ): Expression = Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(NETWORK_LEGIBLE, atNetwork),
            Expression.stop(13.0, atDistrict),
            Expression.stop(15.0, atNeighbourhood),
            Expression.stop(STREET_SCALE, atStreet),
        )
    }
}
