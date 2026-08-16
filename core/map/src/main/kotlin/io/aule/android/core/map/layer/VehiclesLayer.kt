package io.aule.android.core.map.layer

import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import com.google.gson.JsonObject
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleTokens
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.geo.PolylinePath
import io.aule.android.core.geo.PolylineProjection
import io.aule.android.core.map.MapAmbiance
import io.aule.android.core.map.MapIcons
import io.aule.android.core.map.MapInteractiveLayer
import io.aule.android.core.map.MapZoom
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.TransportVehicle
import kotlin.math.min
import org.maplibre.android.geometry.LatLng
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
 * Les véhicules en circulation, et leur glisse entre deux sondages.
 *
 * **C'est la couche qui décide de la comparaison avec Flutter.** Le serveur ne
 * parle que toutes les quinze secondes ; appliquer ses positions telles quelles
 * ferait sauter chaque bus de cent mètres, quatre fois par minute. On interpole
 * donc à la fréquence de l'écran — 120 Hz sur le S21.
 *
 * Deux règles tiennent la fluidité :
 *
 * **Rien ne remonte à Compose.** Les positions interpolées sont écrites
 * directement dans la source MapLibre. Les faire transiter par un `StateFlow`
 * déclencherait une recomposition par image, pour un contenu que Compose ne
 * dessine même pas. L'isolement vient de l'état, pas d'un thread : cette boucle
 * tourne sur le thread principal, comme la recomposition, parce que
 * `setGeoJson` l'exige.
 *
 * **On suit la trajectoire, pas la corde.** Le serveur envoie une courte
 * polyligne qui épouse la voie ; interpoler en ligne droite ferait couper les
 * trams à travers les immeubles à chaque virage.
 */
class VehiclesLayer(
    private val onSelect: (TransportVehicle) -> Unit,
) : MapInteractiveLayer {

    override val id: String = ID
    override val isAnimated: Boolean = true

    private var source: GeoJsonSource? = null
    private var selectionSource: GeoJsonSource? = null
    private var map: MapLibreMap? = null
    private var selectedID: String? = null

    private var snapshot: FleetSnapshot = FleetSnapshot.EMPTY
    private var byId: Map<String, TransportVehicle> = emptyMap()

    /**
     * Instant local de réception du dernier instantané.
     *
     * C'est l'origine de la glisse — **pas** `generatedAt`, dont l'horloge est
     * celle du serveur et peut dériver de plusieurs secondes.
     */
    private var receivedAtMillis: Long = SystemClock.elapsedRealtime()

    /**
     * Position d'où chaque véhicule est parti, pour qu'un nouvel instantané ne le
     * téléporte pas : il repart d'où il était **affiché**.
     */
    private val displayed = HashMap<String, Pose>()

    /** Les trajectoires, longueurs cumulées comprises, préparées à la réception. */
    private val paths = HashMap<String, PolylinePath>()

    /**
     * Un `JsonObject` par véhicule, alloué à la réception et muté en place.
     *
     * Seuls le cap et la position changent par image ; réallouer les propriétés
     * de 250 véhicules à 120 Hz produirait trente mille objets par seconde pour
     * un contenu presque identique.
     */
    private val properties = HashMap<String, JsonObject>()

    /** Réutilisée d'une image à l'autre plutôt que réallouée. */
    private val featureBuffer = ArrayList<Feature>(256)

    data class Pose(val coordinate: Coordinate, val heading: Double)

    // ------------------------------------------------------------------ données

    fun apply(next: FleetSnapshot) {
        snapshot = next
        byId = next.vehicles.associateBy { it.id }
        receivedAtMillis = SystemClock.elapsedRealtime()

        // La géométrie se prépare quand elle arrive, pas quand on la dessine.
        paths.clear()
        properties.clear()
        for (vehicle in next.vehicles) {
            if (vehicle.trajectory.size >= 2) {
                val path = PolylinePath(vehicle.trajectory)
                if (path.isUsable) paths[vehicle.id] = path
            }
            properties[vehicle.id] = JsonObject().apply {
                addProperty(PROP_ID, vehicle.id)
                addProperty(PROP_ICON, MapIcons.vehicleName(vehicle.mode))
                addProperty(PROP_HEADING, vehicle.headingDegrees)
                // Un véhicule théorique s'affiche en retrait : il dit où le bus
                // *devrait* être, ce qui n'est pas la même promesse qu'une
                // position mesurée.
                addProperty(PROP_OPACITY, if (vehicle.isLive) 1.0 else 0.55)
            }
        }

        // On conserve la position affichée des véhicules déjà connus. Un véhicule
        // mesuré qui remplace son jumeau théorique hérite de la position de ce
        // dernier — sinon la carte clignote au moment précis où la donnée
        // s'améliore.
        val carried = HashMap<String, Pose>(next.vehicles.size)
        for (vehicle in next.vehicles) {
            val pose = displayed[vehicle.id]
                ?: vehicle.twinId?.let { displayed[it] }
                ?: Pose(vehicle.coordinate, vehicle.headingDegrees)
            carried[vehicle.id] = pose
        }
        displayed.clear()
        displayed.putAll(carried)

        val stillSelected = selectedID
        if (stillSelected != null && stillSelected !in byId) {
            // Le véhicule suivi a quitté la zone : on lâche l'anneau plutôt
            // que de désigner un fantôme. La fiche, elle, garde ce qu'elle
            // savait — c'est le modèle d'écran qui décide de fermer.
            selectedID = null
        }

        redraw(progress = 0.0)
    }

    fun setSelected(id: String?) {
        selectedID = id
        publishSelection()
    }

    fun vehicle(id: String): TransportVehicle? = byId[id]

    /**
     * La position **affichée** d'un véhicule — celle qu'il faut suivre avec
     * la caméra, pas celle du dernier sondage.
     */
    fun displayedCoordinate(id: String): Pose? = displayed[id]

    // ---------------------------------------------------------------- animation

    /**
     * Où en est la glisse depuis le dernier instantané.
     *
     * On laisse un peu dépasser l'horizon plutôt que de figer net : un sondage en
     * retard fige alors la flotte progressivement au lieu de l'arrêter d'un coup.
     */
    private val slideProgress: Double
        get() {
            val horizon = snapshot.horizonSeconds.coerceAtLeast(1.0)
            val age = (SystemClock.elapsedRealtime() - receivedAtMillis) / 1000.0
            return min(age / horizon, MAX_SLIDE)
        }

    override fun onFrame(elapsedSeconds: Double) {
        if (snapshot.vehicles.isEmpty()) return
        // Sous le seuil d'apparition, les couches sont invisibles : on calculait
        // jusqu'à 250 positions par image pour ne rien montrer.
        val zoom = map?.cameraPosition?.zoom ?: return
        if (zoom < MapZoom.VEHICLES_FROM) return
        redraw(slideProgress)
    }

    /**
     * Les bornes visibles, élargies d'une marge, mises en cache.
     *
     * `projection.visibleRegion` traverse le pont JNI ; l'appeler à chaque image
     * coûterait plus cher que ce qu'on cherche à économiser. Elles ne changent
     * qu'au mouvement de la caméra, donc on les recalcule à ce moment-là.
     */
    private var cachedBounds: DoubleArray? = null
    private var cachedCameraSignature: Long = Long.MIN_VALUE

    private fun visibleBox(map: MapLibreMap): DoubleArray {
        val camera = map.cameraPosition
        val target = camera.target
        val signature = if (target == null) {
            0L
        } else {
            (target.latitude * 1e6).toLong() * 31 +
                (target.longitude * 1e6).toLong() * 17 +
                (camera.zoom * 1e3).toLong() * 7 +
                (camera.bearing * 1e2).toLong()
        }

        val cached = cachedBounds
        if (cached != null && signature == cachedCameraSignature) return cached

        val bounds = map.projection.visibleRegion.latLngBounds
        // Une marge de sécurité pour que les véhicules soient déjà en place quand
        // ils entrent dans le cadre, plutôt que d'y apparaître.
        val latMargin = (bounds.latitudeNorth - bounds.latitudeSouth) * BOUNDS_MARGIN
        val lonMargin = (bounds.longitudeEast - bounds.longitudeWest) * BOUNDS_MARGIN
        val box = doubleArrayOf(
            bounds.latitudeSouth - latMargin,
            bounds.latitudeNorth + latMargin,
            bounds.longitudeWest - lonMargin,
            bounds.longitudeEast + lonMargin,
        )
        cachedBounds = box
        cachedCameraSignature = signature
        return box
    }

    private fun redraw(progress: Double) {
        val source = source ?: return
        val map = map

        // On n'interpole que ce qu'on montre.
        //
        // Mesuré sur le S21 le 16/08 : 1 275 µs par image pour 27 véhicules, soit
        // ~47 µs pièce, pour un budget de 8 333 µs à 120 Hz. À 250 véhicules —
        // la limite que demande le sondage — le calcul complet dépasserait le
        // budget. Or le rayon interrogé fait 2,5 km quand l'écran en montre 300 m :
        // l'écrasante majorité de la flotte n'est pas à l'image.
        val box = map?.let { visibleBox(it) }

        featureBuffer.clear()
        for (vehicle in snapshot.vehicles) {
            val isSelected = vehicle.id == selectedID
            // Le véhicule suivi reste interpolé hors cadre : sinon la caméra
            // figerait au moment précis où l'on a le plus besoin d'elle.
            if (box != null && !vehicle.coordinate.isInside(box) && !isSelected) continue

            val pose = interpolatedPose(vehicle, progress)
            displayed[vehicle.id] = pose

            val props = properties[vehicle.id] ?: continue
            props.addProperty(PROP_HEADING, pose.heading)

            featureBuffer += Feature.fromGeometry(
                Point.fromLngLat(pose.coordinate.longitude, pose.coordinate.latitude),
                props,
            )
        }
        source.setGeoJson(FeatureCollection.fromFeatures(featureBuffer))
        publishSelection()
    }

    private fun publishSelection() {
        val source = selectionSource ?: return
        val pose = selectedID?.let { displayed[it] }
        if (pose == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            source.setGeoJson(
                Feature.fromGeometry(
                    Point.fromLngLat(pose.coordinate.longitude, pose.coordinate.latitude),
                ),
            )
        }
    }

    private fun Coordinate.isInside(box: DoubleArray): Boolean =
        latitude >= box[0] && latitude <= box[1] && longitude >= box[2] && longitude <= box[3]

    /** Où se trouve un véhicule à cet instant de la glisse. */
    private fun interpolatedPose(vehicle: TransportVehicle, progress: Double): Pose {
        val previous = displayed[vehicle.id]
        val start = previous?.coordinate ?: vehicle.coordinate
        val startHeading = previous?.heading ?: vehicle.headingDegrees

        // Un véhicule à quai ne glisse pas : il attend, portes ouvertes. Le faire
        // avancer quand même donnerait un tram qui traverse lentement sa propre
        // station.
        if (vehicle.dwellSeconds > 0 && progress * snapshot.horizonSeconds < vehicle.dwellSeconds) {
            return Pose(start, startHeading)
        }

        val fraction = progress.coerceIn(0.0, 1.0)
        val target = positionAlongPath(vehicle, fraction)
            ?: return Pose(vehicle.coordinate, vehicle.headingDegrees)

        // Le cap se déduit du déplacement **réel** plutôt que du champ `heading` :
        // c'est lui qui fait tourner la flèche exactement quand le véhicule tourne.
        val travelled = GeoMath.distance(start, target.point)
        val heading = if (travelled > HEADING_MIN_TRAVEL_M) {
            GeoMath.interpolateHeading(startHeading, target.bearing, HEADING_SMOOTHING)
        } else {
            startHeading
        }
        return Pose(target.point, heading)
    }

    private fun positionAlongPath(
        vehicle: TransportVehicle,
        fraction: Double,
    ): PolylineProjection.PointOnLine? {
        paths[vehicle.id]?.let { return PolylineProjection.pointAt(it, fraction) }

        val ahead = vehicle.ahead ?: return PolylineProjection.PointOnLine(
            vehicle.coordinate,
            vehicle.headingDegrees,
        )
        return PolylineProjection.PointOnLine(
            GeoMath.interpolate(vehicle.coordinate, ahead, fraction),
            GeoMath.bearing(vehicle.coordinate, ahead),
        )
    }

    // ------------------------------------------------------------------ montage

    override fun mount(style: Style, map: MapLibreMap) {
        this.map = map

        source = GeoJsonSource(
            SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            // Sans mise à jour synchrone, chaque publication est différée sur un
            // worker MapLibre : la position affichée retarde alors d'une trame
            // variable sur la caméra, et c'est exactement ce qui donne à une
            // carte native un air de portage.
            GeoJsonOptions().withSynchronousUpdate(true).withBuffer(0).withTolerance(0f),
        ).also { style.addSource(it) }

        selectionSource = GeoJsonSource(
            SELECTION_SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withSynchronousUpdate(true).withBuffer(0).withTolerance(0f),
        ).also { style.addSource(it) }

        // L'anneau passe sous les glyphes : au-dessus, il masquerait le
        // véhicule qu'il désigne.
        style.addLayer(
            SymbolLayer(SELECTION_LAYER, SELECTION_SOURCE).withProperties(
                PropertyFactory.iconImage(MapIcons.STOP_SELECTED),
                PropertyFactory.iconAllowOverlap(true),
            ),
        )

        // De loin, un point suffit : cent glyphes de bus à l'échelle de
        // l'agglomération ne se distinguent plus les uns des autres.
        style.addLayer(
            CircleLayer(DOT_LAYER, SOURCE).withProperties(
                PropertyFactory.circleRadius(3.5f),
                PropertyFactory.circleColor(AuleBrand.teal.argb),
                PropertyFactory.circleStrokeWidth(1.2f),
                PropertyFactory.circleStrokeColor(AuleTokens.day.surfaceSolid.argb),
                PropertyFactory.circleOpacity(Expression.get(PROP_OPACITY)),
            ).also {
                it.minZoom = MapZoom.VEHICLES_FROM.toFloat()
                it.maxZoom = MapZoom.VEHICLE_ICONS_FROM.toFloat()
            },
        )

        style.addLayer(
            SymbolLayer(HEADING_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(MapIcons.VEHICLE_HEADING),
                PropertyFactory.iconRotate(Expression.get(PROP_HEADING)),
                // Le cap est une direction **sur la carte** : alignée à l'écran,
                // la flèche mentirait dès que la carte tourne.
                PropertyFactory.iconRotationAlignment(PROPERTY_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconOpacity(Expression.get(PROP_OPACITY)),
            ).also { it.minZoom = MapZoom.VEHICLE_ICONS_FROM.toFloat() },
        )

        style.addLayer(
            SymbolLayer(ICON_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconOpacity(Expression.get(PROP_OPACITY)),
            ).also { it.minZoom = MapZoom.VEHICLE_ICONS_FROM.toFloat() },
        )

        // La source posée est vide : sans republication, la flotte reste absente
        // jusqu'au prochain sondage — quinze secondes de carte déserte après un
        // passage en mode sombre. On reprend la glisse **où elle en était**, pas à
        // zéro, sinon les véhicules reculent.
        redraw(slideProgress)
    }

    override fun unmount(style: Style) {
        style.removeLayer(ICON_LAYER)
        style.removeLayer(HEADING_LAYER)
        style.removeLayer(DOT_LAYER)
        style.removeLayer(SELECTION_LAYER)
        style.removeSource(SOURCE)
        style.removeSource(SELECTION_SOURCE)
        source = null
        selectionSource = null
    }

    override fun onAmbianceChange(ambiance: MapAmbiance, style: Style) {
        val tokens = AuleTokens.of(ambiance == MapAmbiance.DARK)
        (style.getLayer(DOT_LAYER) as? CircleLayer)?.setProperties(
            PropertyFactory.circleStrokeColor(tokens.surfaceSolid.argb),
            PropertyFactory.circleColor(tokens.accentOnSurface.argb),
        )
    }

    // -------------------------------------------------------------------- doigt

    override fun hitTest(map: MapLibreMap, rect: RectF, point: PointF): (() -> Unit)? {
        val zoom = map.cameraPosition.zoom
        if (zoom < MapZoom.VEHICLES_FROM) return null

        val layer = if (zoom >= MapZoom.VEHICLE_ICONS_FROM) ICON_LAYER else DOT_LAYER
        val hits = map.queryRenderedFeatures(rect, layer)
        if (hits.isEmpty()) return null

        val closest = hits.mapNotNull { feature ->
            val identifier = feature.getStringProperty(PROP_ID) ?: return@mapNotNull null
            val vehicle = byId[identifier] ?: return@mapNotNull null
            val pose = displayed[identifier] ?: return@mapNotNull null
            val screen = map.projection.toScreenLocation(
                LatLng(pose.coordinate.latitude, pose.coordinate.longitude),
            )
            val dx = screen.x - point.x
            val dy = screen.y - point.y
            vehicle to (dx * dx + dy * dy)
        }.minByOrNull { it.second }?.first ?: return null

        return { onSelect(closest) }
    }

    private companion object {
        const val ID = "aule.vehicles"

        const val SOURCE = "aule.vehicles.source"
        const val SELECTION_SOURCE = "aule.vehicles.selection"
        const val DOT_LAYER = "aule.vehicles.dot"
        const val HEADING_LAYER = "aule.vehicles.heading"
        const val ICON_LAYER = "aule.vehicles.icon"
        const val SELECTION_LAYER = "aule.vehicles.selection.layer"

        const val PROP_ID = "id"
        const val PROP_ICON = "icon"
        const val PROP_HEADING = "heading"
        const val PROP_OPACITY = "opacity"

        const val PROPERTY_ALIGNMENT_MAP = "map"

        /** On laisse la glisse dépasser l'horizon de 35 % avant de figer. */
        const val MAX_SLIDE = 1.35

        /** En dessous, le déplacement est du bruit et ne doit pas faire tourner la flèche. */
        const val HEADING_MIN_TRAVEL_M = 1.5

        const val HEADING_SMOOTHING = 0.35

        /** Marge autour du cadre visible, en fraction de sa taille. */
        const val BOUNDS_MARGIN = 0.35
    }
}
