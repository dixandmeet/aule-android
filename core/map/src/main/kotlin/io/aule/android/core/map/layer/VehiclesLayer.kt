package io.aule.android.core.map.layer

import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import com.google.gson.JsonObject
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleRgba
import io.aule.android.core.designsystem.token.AuleTokens
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.geo.PolylinePath
import io.aule.android.core.geo.PolylineProjection
import io.aule.android.core.map.MapAmbiance
import io.aule.android.core.map.MapIcons
import io.aule.android.core.map.MapInteractiveLayer
import io.aule.android.core.map.MapZoom
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import kotlin.math.min
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

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
 *
 * **Trois façons de montrer le même bus.** De loin un point, plus près un glyphe,
 * et une fois la ville en relief une caisse extrudée — voir [VehicleBody]. Les
 * deux dernières se croisent en fondu autour de [MapZoom.VEHICLE_BODIES_FROM] :
 * la carte ne bascule jamais d'un état à l'autre sous l'œil. Les caisses sont
 * translucides ; seule celle du véhicule choisi passe en couleur pleine, ce qui
 * fait de la sélection une réponse visible sans rien ajouter à l'écran.
 */
class VehiclesLayer(
    private val onSelect: (TransportVehicle) -> Unit,
) : MapInteractiveLayer {

    override val id: String = ID
    override val isAnimated: Boolean = true

    private var source: GeoJsonSource? = null
    private var selectionSource: GeoJsonSource? = null
    private var bodySource: GeoJsonSource? = null
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

    /** Idem pour les volumes, bien moins nombreux : ils ne sortent qu'en vue rapprochée. */
    private val bodyBuffer = ArrayList<Feature>(MAX_BODIES + 1)

    /** L'empreinte en cours de calcul, en paires `lon, lat`. Une seule pour toute la flotte. */
    private val ring = DoubleArray(VehicleBody.VERTICES * 2)

    /**
     * Vrai si la source des volumes contient encore quelque chose.
     *
     * Sans ce drapeau, on republierait une collection vide à chaque image dès
     * qu'on repasse sous le seuil — soixante fois par seconde pour ne rien
     * effacer de plus.
     */
    private var bodiesPublished = false

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
                addProperty(PROP_ICON, MapIcons.vehicleName(vehicle.mode, vehicle.isLive))
                addProperty(PROP_HEADING, vehicle.headingDegrees)
                // Un véhicule théorique s'affiche en retrait : il dit où le bus
                // *devrait* être, ce qui n'est pas la même promesse qu'une
                // position mesurée.
                //
                // Ce retrait ne sert plus qu'au **point de loin**, seul objet
                // trop petit pour porter une forme. Le glyphe, lui, le dit
                // désormais par sa silhouette — creuse pour l'horaire, pleine
                // pour la mesure — et reste à pleine opacité : à 0,55 sur une
                // flotte du soir presque entièrement théorique, la carte se
                // lisait délavée.
                addProperty(PROP_OPACITY, if (vehicle.isLive) 1.0 else 0.55)
                // La teinte du volume se choisit ici : l'opacité d'une couche
                // `fill-extrusion` ne peut pas varier d'un véhicule à l'autre,
                // donc le retrait du théorique passe par la couleur.
                addProperty(PROP_TINT, tint(vehicle))
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
            // Le théorique qu'on suivait vient peut-être d'être **remplacé par
            // sa mesure** : c'est le même bus, sous un autre identifiant. On
            // reporte l'anneau dessus, sinon la sélection se perdrait au moment
            // précis où la donnée s'améliore — et la caméra, qui suit cet
            // identifiant, décrocherait avec elle.
            val heir = next.vehicles.firstOrNull { it.twinId == stillSelected }
            // Sinon, le véhicule a quitté la zone : on lâche l'anneau plutôt
            // que de désigner un fantôme. La fiche, elle, garde ce qu'elle
            // savait — c'est le modèle d'écran qui décide de fermer.
            selectedID = heir?.id
        }

        redraw(progress = 0.0)
    }

    fun setSelected(id: String?) {
        selectedID = id
        // Un redessin complet plutôt que le seul anneau : c'est la propriété
        // `selected` de chaque caisse qui décide de la couche translucide ou de
        // la pleine, et elle ne s'écrit qu'ici.
        redraw(slideProgress)
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

        // Le volume ne se calcule qu'une fois la ville en relief. Plus bas, la
        // couche est éteinte : six sommets par véhicule et par image n'y
        // peindraient rien.
        val zoom = map?.cameraPosition?.zoom ?: 0.0
        val volumes = zoom >= MapZoom.VEHICLE_BODIES_FROM - BODY_FADE

        featureBuffer.clear()
        bodyBuffer.clear()
        for (vehicle in snapshot.vehicles) {
            val isSelected = vehicle.id == selectedID
            // Le véhicule suivi reste interpolé hors cadre : sinon la caméra
            // figerait au moment précis où l'on a le plus besoin d'elle.
            if (box != null && !vehicle.coordinate.isInside(box) && !isSelected) continue

            val pose = interpolatedPose(vehicle, progress)
            displayed[vehicle.id] = pose

            val props = properties[vehicle.id] ?: continue
            props.addProperty(PROP_HEADING, pose.heading)
            // C'est cette propriété qui répartit la flotte entre les deux
            // couches de volume : la translucide, et celle du véhicule choisi.
            props.addProperty(PROP_SELECTED, isSelected)

            featureBuffer += Feature.fromGeometry(
                Point.fromLngLat(pose.coordinate.longitude, pose.coordinate.latitude),
                props,
            )

            // Le plafond est une garde, pas un cadrage : au seuil des volumes,
            // l'écran couvre quelques centaines de mètres et n'en montre
            // qu'une poignée. Le véhicule choisi passe toujours — c'est celui
            // qu'on regarde.
            if (volumes && (bodyBuffer.size < MAX_BODIES || isSelected)) {
                bodyBuffer += body(vehicle, pose, zoom, props)
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(featureBuffer))
        publishBodies()
        publishSelection()
    }

    /**
     * La caisse d'un véhicule : son empreinte au sol, et la hauteur à extruder.
     *
     * La hauteur voyage dans les propriétés plutôt que dans la couche parce
     * qu'elle dépend du zoom **et** du mode : un tram ne se grossit pas comme un
     * bus, et une expression ne saurait pas mêler les deux aussi lisiblement.
     */
    private fun body(
        vehicle: TransportVehicle,
        pose: Pose,
        zoom: Double,
        props: JsonObject,
    ): Feature {
        val gauge = VehicleBody.gauge(vehicle.mode)
        val scale = VehicleBody.emphasis(vehicle.mode, zoom)
        VehicleBody.footprint(
            latitude = pose.coordinate.latitude,
            longitude = pose.coordinate.longitude,
            headingDegrees = pose.heading,
            gauge = gauge,
            scale = scale,
            out = ring,
        )
        props.addProperty(PROP_HEIGHT, gauge.heightMeters * scale)

        val corners = ArrayList<Point>(VehicleBody.VERTICES + 1)
        for (index in 0 until VehicleBody.VERTICES) {
            corners += Point.fromLngLat(ring[index * 2], ring[index * 2 + 1])
        }
        // GeoJSON veut un anneau fermé : sans le retour au premier point, le
        // polygone ne se triangule pas et la caisse ne se dessine pas.
        corners += corners[0]
        return Feature.fromGeometry(Polygon.fromLngLats(listOf(corners)), props)
    }

    private fun publishBodies() {
        val source = bodySource ?: return
        // Rien à dire quand il n'y a rien à effacer.
        if (bodyBuffer.isEmpty() && !bodiesPublished) return
        source.setGeoJson(FeatureCollection.fromFeatures(bodyBuffer))
        bodiesPublished = bodyBuffer.isNotEmpty()
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

        // Les caisses ont leur propre source : mêlées aux points, elles feraient
        // poser un second glyphe au centre de chaque polygone.
        bodySource = GeoJsonSource(
            BODY_SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withSynchronousUpdate(true).withBuffer(0).withTolerance(0f),
        ).also { style.addSource(it) }
        bodiesPublished = false

        // L'anneau passe sous les glyphes : au-dessus, il masquerait le
        // véhicule qu'il désigne.
        style.addLayer(
            SymbolLayer(SELECTION_LAYER, SELECTION_SOURCE).withProperties(
                PropertyFactory.iconImage(MapIcons.STOP_SELECTED),
                PropertyFactory.iconAllowOverlap(true),
            ),
        )

        // Les volumes, en deux couches jumelles.
        //
        // **Pourquoi deux.** `fill-extrusion-opacity` est une propriété de
        // couche : la spécification ne la laisse pas varier d'un véhicule à
        // l'autre. Or c'est exactement ce qu'on demande — la flotte translucide,
        // le véhicule choisi en couleur pleine. Un filtre sur `selected` répartit
        // donc la même source entre deux couches, chacune avec son opacité, et
        // aucune caisse n'est jamais dessinée deux fois.
        style.addLayer(
            FillExtrusionLayer(BODY_LAYER, BODY_SOURCE).withProperties(
                *bodyProperties(FLEET_OPACITY),
            ).also {
                it.minZoom = (MapZoom.VEHICLE_BODIES_FROM - BODY_FADE).toFloat()
                it.setFilter(Expression.not(Expression.toBool(Expression.get(PROP_SELECTED))))
            },
        )

        style.addLayer(
            FillExtrusionLayer(BODY_SELECTED_LAYER, BODY_SOURCE).withProperties(
                *bodyProperties(SELECTED_OPACITY),
            ).also {
                it.minZoom = (MapZoom.VEHICLE_BODIES_FROM - BODY_FADE).toFloat()
                it.setFilter(Expression.eq(Expression.get(PROP_SELECTED), Expression.literal(true)))
            },
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

        // Une seule couche pour le véhicule et son cap. Le chevron qu'elle
        // remplace était une couche à part, posée sur la même source : deux
        // symboles par véhicule, deux entrées dans l'index de collision, et un
        // cap peint à l'encre neutre qui se perdait sur la chaussée claire.
        // C'est la silhouette elle-même qui pointe, maintenant.
        style.addLayer(
            SymbolLayer(ICON_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                PropertyFactory.iconRotate(Expression.get(PROP_HEADING)),
                // Le cap est une direction **sur la carte** : alignée à l'écran,
                // la silhouette mentirait dès que la carte tourne.
                PropertyFactory.iconRotationAlignment(PROPERTY_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconOpacity(flatOpacity()),
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
        style.removeLayer(DOT_LAYER)
        style.removeLayer(BODY_SELECTED_LAYER)
        style.removeLayer(BODY_LAYER)
        style.removeLayer(SELECTION_LAYER)
        style.removeSource(SOURCE)
        style.removeSource(SELECTION_SOURCE)
        style.removeSource(BODY_SOURCE)
        forgetStyle()
    }

    /**
     * ⚠️ **La couche qui a rendu ce contrat obligatoire, côté iOS.** Elle y est
     * écrite depuis deux endroits : la boucle d'image du registre — qui, elle,
     * sait ne pas parler aux couches démontées — et le ticker du Guet, qui publie
     * la flotte sans rien demander à personne. Ici, c'est l'écran carte qui tient
     * ce rôle : il collecte les instantanés et appelle [apply] directement.
     *
     * L'instantané, les poses affichées et les trajectoires **restent** : [mount]
     * republie tout où il en était, sinon la flotte remonterait vide et le
     * resterait jusqu'au prochain sondage — quinze secondes de carte déserte
     * après un passage en mode sombre.
     */
    override fun forgetStyle() {
        source = null
        selectionSource = null
        bodySource = null
        bodiesPublished = false
        // La carte n'appartient pas plus à la couche que le style : la garder
        // après un démontage, c'est retenir une `MapLibreMap` détruite pour lire
        // sa caméra. [mount] la rend.
        map = null
    }

    override fun onAmbianceChange(ambiance: MapAmbiance, style: Style) {
        val night = ambiance == MapAmbiance.DARK
        val tokens = AuleTokens.of(night)
        (style.getLayer(DOT_LAYER) as? CircleLayer)?.setProperties(
            PropertyFactory.circleStrokeColor(tokens.surfaceSolid.argb),
            PropertyFactory.circleColor(tokens.accentOnSurface.argb),
        )
        val colour = PropertyFactory.fillExtrusionColor(bodyColor(night))
        (style.getLayer(BODY_LAYER) as? FillExtrusionLayer)?.setProperties(colour)
        (style.getLayer(BODY_SELECTED_LAYER) as? FillExtrusionLayer)?.setProperties(colour)
    }

    // ----------------------------------------------------------------- peinture

    /**
     * Ce qui définit une caisse, à l'opacité près — c'est elle qui distingue les
     * deux couches.
     */
    private fun bodyProperties(opacity: Double) = arrayOf(
        PropertyFactory.fillExtrusionColor(bodyColor(night = false)),
        PropertyFactory.fillExtrusionHeight(Expression.get(PROP_HEIGHT)),
        // Le dégradé vertical est ce qui fait lire un volume plutôt qu'une
        // tache : sans lui, toit et flancs ont la même couleur et la caisse
        // s'aplatit dès que la carte se redresse.
        PropertyFactory.fillExtrusionVerticalGradient(true),
        // Assez pour casser l'arête vive d'une boîte, pas assez pour arrondir un
        // bus en galet.
        PropertyFactory.fillExtrusionRoundedCornerDistance(BODY_CORNER_M),
        // Les volumes montent quand les icônes plates s'effacent : sur ces trois
        // dixièmes de zoom, l'un remplace l'autre sans que rien clignote.
        PropertyFactory.fillExtrusionOpacity(
            Expression.interpolate(
                Expression.linear(),
                Expression.zoom(),
                Expression.stop(MapZoom.VEHICLE_BODIES_FROM - BODY_FADE, 0.0),
                Expression.stop(MapZoom.VEHICLE_BODIES_FROM + BODY_FADE, opacity),
            ),
        ),
    )

    /**
     * Le fondu inverse : les glyphes plats s'éteignent là où les caisses montent.
     *
     * **`zoom` doit rester l'entrée du `interpolate` de tête.** La spécification
     * l'exige, et MapLibre rejette l'expression **en silence** si on l'enfouit
     * dans un produit : la couche retombe alors sur son opacité par défaut, et
     * les glyphes restent allumés par-dessus les caisses. Payé à l'écran avant
     * d'être compris.
     *
     * La borne basse vaut **un** et non l'opacité du véhicule : le théorique se
     * dit par la silhouette creuse, et n'a plus rien à retirer ici.
     */
    private fun flatOpacity(): Expression = Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        Expression.stop(MapZoom.VEHICLE_BODIES_FROM - BODY_FADE, 1.0),
        Expression.stop(MapZoom.VEHICLE_BODIES_FROM + BODY_FADE, 0.0),
    )

    /**
     * La couleur d'une caisse, par mode et par origine de la position.
     *
     * Les teintes sont celles des pastilles : la bascule du plat au volume ne doit
     * pas changer la couleur du réseau sous l'œil. Le théorique, lui, est mêlé à
     * la surface — c'est le seul retrait qui reste quand l'opacité appartient à la
     * couche et non au véhicule.
     */
    private fun bodyColor(night: Boolean): Expression {
        val surface = AuleTokens.of(night).surfaceSolid
        val stops = ArrayList<Expression.Stop>(TransportMode.entries.size * 2)
        for (mode in TransportMode.entries) {
            val paint = mode.markerColor(night)
            stops += Expression.stop(mode.tintKey, Expression.color(paint.argb))
            stops += Expression.stop(
                mode.tintKey + GHOST_SUFFIX,
                Expression.color(paint.mixedWith(surface, GHOST_MIX).argb),
            )
        }
        return Expression.match(
            Expression.get(PROP_TINT),
            Expression.color(AuleBrand.teal.argb),
            *stops.toTypedArray(),
        )
    }

    private fun tint(vehicle: TransportVehicle): String =
        if (vehicle.isLive) vehicle.mode.tintKey else vehicle.mode.tintKey + GHOST_SUFFIX

    private val TransportMode.tintKey: String get() = name.lowercase()

    private fun AuleRgba.mixedWith(other: AuleRgba, amount: Double): AuleRgba = AuleRgba(
        red = red + (other.red - red) * amount,
        green = green + (other.green - green) * amount,
        blue = blue + (other.blue - blue) * amount,
        alpha = alpha,
    )

    // -------------------------------------------------------------------- doigt

    override fun hitTest(map: MapLibreMap, rect: RectF, point: PointF): (() -> Unit)? {
        val zoom = map.cameraPosition.zoom
        if (zoom < MapZoom.VEHICLES_FROM) return null

        // En vue rapprochée, on vise la caisse : c'est elle qu'on voit, et son
        // empreinte est une bien plus grande cible qu'un glyphe de 22 dp. Le
        // glyphe reste de la partie — il est éteint, mais toujours posé, et fait
        // office de filet pendant le fondu.
        val layers = when {
            zoom >= MapZoom.VEHICLE_BODIES_FROM ->
                arrayOf(BODY_LAYER, BODY_SELECTED_LAYER, ICON_LAYER)
            zoom >= MapZoom.VEHICLE_ICONS_FROM -> arrayOf(ICON_LAYER)
            else -> arrayOf(DOT_LAYER)
        }
        val hits = map.queryRenderedFeatures(rect, *layers)
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
        const val BODY_SOURCE = "aule.vehicles.body.source"
        const val DOT_LAYER = "aule.vehicles.dot"
        const val ICON_LAYER = "aule.vehicles.icon"
        const val BODY_LAYER = "aule.vehicles.body"
        const val BODY_SELECTED_LAYER = "aule.vehicles.body.selected"
        const val SELECTION_LAYER = "aule.vehicles.selection.layer"

        const val PROP_ID = "id"
        const val PROP_ICON = "icon"
        const val PROP_HEADING = "heading"
        const val PROP_OPACITY = "opacity"
        const val PROP_SELECTED = "selected"
        const val PROP_HEIGHT = "height"
        const val PROP_TINT = "tint"

        /** Ce qui, dans une teinte, dit que la position est calculée et non mesurée. */
        const val GHOST_SUFFIX = ".ghost"

        /**
         * Part de surface mêlée à la teinte d'un véhicule théorique.
         *
         * Le retrait doit se voir sans effacer : à moitié blanchie, une caisse
         * translucide se confondait avec la chaussée — mesuré à l'écran sur la
         * flotte du soir, où presque tout est théorique.
         */
        const val GHOST_MIX = 0.28

        /**
         * La flotte au repos : assez présente pour se suivre du regard, assez
         * transparente pour qu'on lise la rue dessous — et pour que le véhicule
         * choisi, lui, se détache d'un coup.
         */
        const val FLEET_OPACITY = 0.6

        /** Le véhicule choisi, en couleur pleine : c'est la réponse à un doigt posé. */
        const val SELECTED_OPACITY = 1.0

        /** La demi-largeur du fondu entre les glyphes plats et les volumes, en zoom. */
        const val BODY_FADE = 0.3

        /** L'arrondi des arêtes de caisse, en mètres. */
        const val BODY_CORNER_M = 0.6f

        /**
         * Le plafond de caisses par image.
         *
         * Une garde contre un sondage anormalement dense, pas un cadrage : au
         * seuil des volumes, l'écran ne montre qu'une poignée de véhicules.
         */
        const val MAX_BODIES = 48

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
