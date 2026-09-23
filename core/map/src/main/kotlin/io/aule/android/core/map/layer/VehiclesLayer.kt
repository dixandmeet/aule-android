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
import io.aule.android.core.map3d.VehicleLighting
import io.aule.android.core.map3d.VehicleScene
import io.aule.android.core.map3d.WebMercator
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
    /**
     * La scène 3D, si l'appareil a pu la monter.
     *
     * `null` — ou en échec — laisse **tout** le monde sur le volume extrudé,
     * c'est-à-dire exactement l'écran d'avant. Le repli n'est pas un mode
     * dégradé qu'on ajoute : c'est le chemin qui existait, qu'on n'enlève pas.
     */
    private val scene: VehicleScene? = null,
) : MapInteractiveLayer {

    override val id: String = ID
    override val isAnimated: Boolean = true

    private var source: GeoJsonSource? = null
    private var selectionSource: GeoJsonSource? = null
    private var bodySource: GeoJsonSource? = null
    private var map: MapLibreMap? = null
    private var selectedID: String? = null

    /**
     * La **course** suivie, par-dessus l'identifiant du moment.
     *
     * Voir [TransportVehicle.courseIdentity] : c'est elle qui permet de retrouver le véhicule
     * au sondage suivant, quand le flux temps réel lui a donné un autre nom.
     */
    private var selectedIdentity: String? = null

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

    /**
     * L'ambiance en cours, pour teinter les instances 3D.
     *
     * L'extrusion, elle, porte sa couleur dans une `Expression` que MapLibre
     * réévalue ; la scène reçoit une couleur déjà résolue et doit donc savoir.
     */
    private var night = false

    /**
     * Ce que le rendu natif a répondu à la dernière image.
     *
     * ⚠️ **Il se lit à chaque image, pas une fois au montage.** `initialize`
     * échoue sur le thread de rendu, longtemps après le montage ; un drapeau lu
     * une seule fois laisserait la couche croire la 3D disponible, cesser de
     * publier les volumes, et la flotte deviendrait **invisible** sans que rien
     * ne le dise.
     */
    private var sceneStatus = VehicleScene.SceneStatus.NEEDS_INIT

    data class Pose(val coordinate: Coordinate, val heading: Double)

    // ------------------------------------------------------------------ données

    fun apply(next: FleetSnapshot) {
        // ⚠️ **Avant d'écraser `byId`.** C'est le seul endroit où l'on sait encore à quelle
        // course appartenait chaque position déjà peinte : relu après l'affectation, il
        // répondrait sur le nouvel instantané, c'est-à-dire sur les véhicules qu'on cherche
        // justement à rattacher aux anciens.
        val previous = byId
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
        //
        // ⚠️ **L'héritage se fait par course, pas par identifiant.** Le flux temps réel
        // renomme ses véhicules d'un sondage à l'autre : la seule correspondance qui tienne
        // est [TransportVehicle.courseIdentity]. Par identifiant seul, le même bus repartait
        // de sa position brute toutes les quinze secondes, c'est-à-dire sautait.
        val heldByCourse = HashMap<String, Pose>(displayed.size)
        for ((identifier, pose) in displayed) {
            heldByCourse[previous[identifier]?.courseIdentity ?: identifier] = pose
        }
        val carried = HashMap<String, Pose>(next.vehicles.size)
        for (vehicle in next.vehicles) {
            val pose = displayed[vehicle.id]
                ?: vehicle.twinId?.let { displayed[it] }
                ?: heldByCourse[vehicle.courseIdentity]
                ?: Pose(vehicle.coordinate, vehicle.headingDegrees)
            carried[vehicle.id] = pose
        }
        displayed.clear()
        displayed.putAll(carried)

        val stillSelected = selectedID
        if (stillSelected != null && stillSelected !in byId) {
            // Le véhicule qu'on suivait revient sous un autre identifiant : soit le
            // théorique a été **remplacé par sa mesure**, soit la mesure elle-même a changé
            // de nom — le flux temps réel fait tourner ses identifiants d'un sondage à
            // l'autre. C'est la **course** qu'on suit, et elle, elle ne bouge pas : voir
            // [TransportVehicle.courseIdentity].
            //
            // ⚠️ **Et non `it.twinId == stillSelected`.** Ce repli-là ne rattrapait qu'un
            // seul saut, du théorique vers sa première mesure, puis lâchait : au sondage
            // suivant, l'identifiant retenu n'était plus le jumeau de personne. La sélection
            // se perdait, l'anneau disparaissait et la caméra suivait une pastille
            // invisible (recette du 18/09/2026, BUG-AND-008).
            val identity = selectedIdentity ?: previous[stillSelected]?.courseIdentity ?: stillSelected
            val heir = heirOf(identity, next.vehicles)
            // Sinon, le véhicule a quitté la zone : on lâche l'anneau plutôt
            // que de désigner un fantôme. La fiche, elle, garde ce qu'elle
            // savait — c'est le modèle d'écran qui décide de fermer.
            selectedID = heir?.id
            if (heir == null) selectedIdentity = null
        }

        redraw(progress = 0.0)
    }

    /**
     * @param identity la **course** suivie, quand l'appelant la connaît — voir
     *   [TransportVehicle.courseIdentity].
     *
     *   ⚠️ **À donner dès que le véhicule peut manquer de l'instantané.** Un sondage qui ne le
     *   rend pas laisse l'appelant avec un identifiant orphelin ; déduite de `byId`, l'identité
     *   vaudrait alors cet identifiant-là, et le sondage suivant — qui republie la course sous
     *   un troisième nom — ne s'y reconnaîtrait pas davantage. C'est la course qu'il faut
     *   retenir, elle ne bouge pas de la journée.
     */
    fun setSelected(id: String?, identity: String? = null) {
        selectedID = id
        // ⚠️ **L'identité se retient au moment où l'on choisit**, et pas au moment où l'on
        // cherche un héritier : à ce moment-là, le véhicule a déjà disparu de `byId` et son
        // `twinId` avec lui — il ne resterait qu'un identifiant orphelin à comparer.
        selectedIdentity = identity ?: id?.let { byId[it]?.courseIdentity ?: it }
        // Un redessin complet plutôt que le seul anneau : c'est la propriété
        // `selected` de chaque caisse qui décide de la couche translucide ou de
        // la pleine, et elle ne s'écrit qu'ici.
        redraw(slideProgress)
    }

    /** Le véhicule qu'on suit, tel que l'instantané courant le nomme. */
    private fun heirOf(identity: String, among: List<TransportVehicle>): TransportVehicle? =
        among.firstOrNull { it.courseIdentity == identity }

    fun vehicle(id: String): TransportVehicle? = byId[id]

    /**
     * La position **affichée** d'un véhicule — celle qu'il faut suivre avec
     * la caméra, pas celle du dernier sondage.
     */
    fun displayedCoordinate(id: String): Pose? = displayed[id]

    /**
     * La pose du véhicule choisi, telle qu'elle est peinte.
     *
     * ⚠️ **C'est par ici qu'une caméra de suivi doit passer, et pas par
     * l'identifiant qu'un écran a retenu.** Une position mesurée porte son propre
     * identifiant et **remplace** le théorique qu'on avait touché ; la couche
     * reporte la sélection sur l'héritier (voir [apply]), un appelant qui garde
     * l'ancien identifiant décrocherait au moment précis où la donnée s'améliore.
     */
    val selectedPose: Pose? get() = selectedID?.let { displayed[it] }

    /** Le véhicule choisi, héritage compris — sa vitesse commande le cadrage. */
    val selectedVehicle: TransportVehicle? get() = selectedID?.let { byId[it] }

    // ---------------------------------------------------------------- animation

    /**
     * Où en est la glisse depuis le dernier instantané.
     *
     * ⚠️ **Elle dépasse 1, mais l'avancement est borné à 1 par [interpolatedPose].**
     * Au-delà de l'horizon, le tracé ne dit plus rien : le véhicule attend la fin
     * de son tracé connu plutôt que d'inventer la suite. Le sondage est à 15 s
     * pour un horizon de 10 : ces cinq secondes d'attente ne se rattrapent pas ici,
     * elles se rattraperont le jour où l'un des deux rejoindra l'autre.
     */
    private val slideProgress: Double
        get() {
            val horizon = snapshot.horizonSeconds.coerceAtLeast(1.0)
            val age = (SystemClock.elapsedRealtime() - receivedAtMillis) / 1000.0
            return min(age / horizon, MAX_SLIDE)
        }

    /**
     * L'horodatage de la dernière image, pour en mesurer la durée.
     *
     * `onFrame` reçoit le temps écoulé **depuis le démarrage de l'horloge**, pas
     * l'écart entre deux images. C'est pourtant l'écart qui règle la rotation
     * des caisses : il se calcule donc ici, et se remet à zéro avec le style.
     */
    private var lastFrameSeconds = 0.0

    override fun onFrame(elapsedSeconds: Double) {
        val previousFrame = lastFrameSeconds
        // Retenu avant les sorties anticipées : sinon la première image après un
        // retour dans le cadre porterait tout le temps passé hors de lui.
        lastFrameSeconds = elapsedSeconds
        if (snapshot.vehicles.isEmpty()) return
        // Sous le seuil d'apparition, les couches sont invisibles : on calculait
        // jusqu'à 250 positions par image pour ne rien montrer.
        val zoom = map?.cameraPosition?.zoom ?: return
        if (zoom < MapZoom.VEHICLES_FROM) return
        // L'horloge peut repartir de zéro — une application reprise en pose un
        // nouveau départ : un écart négatif ne doit pas remonter le temps.
        val frame = (elapsedSeconds - previousFrame).coerceIn(0.0, VehicleGlide.MAX_FRAME_SECONDS)
        redraw(slideProgress, frame)
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

    /**
     * [dtSeconds] vaut zéro hors de la boucle d'image : voir [interpolatedPose].
     */
    private fun redraw(progress: Double, dtSeconds: Double = 0.0) {
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

        // L'ancre de la scène 3D : le centre de la caméra. Les poses sont des
        // mètres relatifs à ce point, ce qui garde les grands nombres hors du
        // `float` — voir `WebMercator` et `vehicle_layer.cpp`.
        val anchor = map?.cameraPosition?.target
        val anchorMercX = anchor?.let { WebMercator.x(it.longitude) } ?: 0.0
        val anchorMercY = anchor?.let { WebMercator.y(it.latitude) } ?: 0.0
        val anchorLat = anchor?.latitude ?: 0.0
        // La 3D ne prend la main que si le rendu natif a dit qu'il était prêt.
        val models = scene != null && anchor != null && volumes && sceneStatus.isReady
        val fade = VehicleBody.bodyFade(zoom, BODY_FADE, MapZoom.VEHICLE_BODIES_FROM)
        var poses = 0

        featureBuffer.clear()
        bodyBuffer.clear()
        for (vehicle in snapshot.vehicles) {
            val isSelected = vehicle.id == selectedID
            // Le véhicule suivi reste interpolé hors cadre : sinon la caméra
            // figerait au moment précis où l'on a le plus besoin d'elle.
            if (box != null && !vehicle.coordinate.isInside(box) && !isSelected) continue

            val pose = interpolatedPose(vehicle, progress, dtSeconds)
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
            if (!volumes) continue
            val mesh = if (models) meshIndex(vehicle.mode) else null
            if (mesh != null && (poses < VehicleScene.MAX_POSES || isSelected)) {
                // ⚠️ **Le véhicule choisi prend une place, quitte à la prendre à un autre.**
                //
                // Le plafond franchi, ce bloc ne faisait **rien** : la condition extérieure
                // laissait entrer le véhicule choisi grâce à `isSelected`, et la garde
                // intérieure lui refusait ensuite l'écriture. Il tombait donc entre les deux
                // branches — ni modèle, ni volume extrudé —, et comme le glyphe plat est éteint
                // au-dessus de [MapZoom.VEHICLE_BODIES_FROM], il devenait **invisible** alors
                // même que le commentaire ci-dessus promet qu'il passe toujours.
                //
                // ⚠️ **Entre 15,2 et 16,5 seulement**, et c'est ce qui rend le défaut rare : le
                // budget d'objets du voyageur tombe à trente au-delà (`FleetViewport.limitForZoom`),
                // donc les quarante-huit places ne peuvent plus se remplir. La bande étroite où
                // il se produit est aussi celle où la flotte est la plus dense à l'écran.
                //
                // Il reprend donc la dernière place écrite plutôt que d'être perdu. C'est le
                // bon échange : le plafond est là pour tenir le budget d'une image, et de tous
                // les véhicules à l'écran, celui qu'on regarde est le dernier qu'on accepte de
                // ne pas voir.
                val slot = if (poses < VehicleScene.MAX_POSES) poses++ else VehicleScene.MAX_POSES - 1
                writePose(slot, vehicle, pose, zoom, isSelected, fade,
                    anchorMercX, anchorMercY, anchorLat, mesh)
            } else if (bodyBuffer.size < MAX_BODIES || isSelected) {
                // Le navibus n'a pas de modèle dans le pack, et le repli non plus :
                // les deux passent par l'extrusion, qui reste donc **empruntée à
                // chaque session**. Un chemin de secours jamais parcouru est un
                // chemin cassé qu'on ignore.
                bodyBuffer += body(vehicle, pose, zoom, props)
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(featureBuffer))
        publishBodies()
        publishSelection()

        // Publier même à zéro : sans cela, la dernière flotte resterait peinte
        // après un dézoom sous le seuil.
        scene?.let {
            sceneStatus = it.commit(poses, anchorMercX, anchorMercY, anchorLat)
        }
    }

    /**
     * La teinte de carrosserie d'un modèle : **la livrée, et rien d'autre**.
     *
     * ⚠️ **Elle ne vient pas de `markerColor`, à la différence de l'extrusion.**
     * Un aplat plat peut être sombre sans rien perdre ; un modèle ne se lit que
     * par le contraste entre sa caisse et ses vitres, presque noires. Peint du
     * teal sombre de la pastille tram, il devient un bloc uniforme où ni baie ni
     * jupe n'apparaît — tout le détail qu'on est allé chercher disparaît. Voir
     * [VehicleScene.bodyColor].
     *
     * ⚠️ **Et le théorique n'y est plus mêlé, à la différence de l'extrusion.**
     * `GHOST_MIX` vaut 0,28 de **surface**, c'est-à-dire de blanc : sur un aplat
     * plat, c'est un retrait qui se lit ; sur un modèle éclairé, c'est un
     * délavage, et il se multiplie ensuite par l'ambiante. La livrée du tram
     * passait de `#2F9D80` à `#69B8A4` avant d'avoir reçu le moindre rayon — une
     * caisse qui a perdu sa teinte, sur une flotte du soir presque entièrement
     * théorique. La fraîcheur, elle, reste dite par le glyphe plat jusqu'au seuil
     * des volumes, par le registre de la pastille, et en toutes lettres par la
     * fiche.
     */
    private fun bodyPaint(mesh: Int): AuleRgba = AuleRgba(VehicleScene.bodyColor(mesh))

    /** Le maillage d'un mode, ou `null` s'il n'en a pas — le navibus. */
    private fun meshIndex(mode: TransportMode): Int? = when (mode) {
        TransportMode.BUS -> MESH_BUS
        TransportMode.TRAM -> MESH_TRAM
        TransportMode.BOAT -> null
    }

    /**
     * Écrit une instance dans le tampon natif, sans rien allouer.
     *
     * Écritures absolues plutôt que séquentielles : la position du tampon n'est
     * jamais touchée, donc deux images ne peuvent pas se marcher dessus, et il
     * n'y a rien à remettre à zéro entre elles.
     */
    private fun writePose(
        at: Int,
        vehicle: TransportVehicle,
        pose: Pose,
        zoom: Double,
        isSelected: Boolean,
        fade: Double,
        anchorMercX: Double,
        anchorMercY: Double,
        anchorLat: Double,
        mesh: Int,
    ) {
        val staging = scene?.staging ?: return
        val base = at * VehicleScene.POSE_BYTES

        val east = WebMercator.eastOffsetMeters(pose.coordinate.longitude, anchorMercX, anchorLat)
        val north = WebMercator.northOffsetMeters(pose.coordinate.latitude, anchorMercY, anchorLat)
        val scale = VehicleBody.emphasis(vehicle.mode, zoom, pose.coordinate.latitude).toFloat()

        val paint = bodyPaint(mesh)
        val opacity = (if (isSelected) SELECTED_OPACITY else FLEET_OPACITY) * fade

        staging.putFloat(base, east.toFloat())
        staging.putFloat(base + 4, north.toFloat())
        // Le natif attend des radians ; le cap du domaine est en degrés.
        staging.putFloat(base + 8, Math.toRadians(pose.heading).toFloat())
        staging.putFloat(base + 12, scale)
        staging.putFloat(base + 16, scale)
        staging.putFloat(base + 20, scale)
        staging.putFloat(base + 24, paint.red.toFloat())
        staging.putFloat(base + 28, paint.green.toFloat())
        staging.putFloat(base + 32, paint.blue.toFloat())
        staging.putFloat(base + 36, opacity.toFloat())
        staging.putInt(base + 40, mesh)
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
        val scale = VehicleBody.emphasis(vehicle.mode, zoom, pose.coordinate.latitude)
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

    /**
     * Où se trouve un véhicule à cet instant de la glisse, et vers où il regarde.
     *
     * [dtSeconds] est la durée de l'image — zéro pour un redessin qui n'en est
     * pas un : un sondage, un changement de sélection, un style remonté. Le cap
     * ne bouge alors pas, et c'est voulu. Ces redessins-là arrivent à des
     * cadences quelconques ; les laisser avancer le lissage rendrait la vitesse
     * de rotation d'un bus tributaire de celle du réseau.
     */
    private fun interpolatedPose(
        vehicle: TransportVehicle,
        progress: Double,
        dtSeconds: Double,
    ): Pose {
        val startHeading = displayed[vehicle.id]?.heading ?: vehicle.headingDegrees

        // ⚠️ **Le palier de `dwellSeconds` retenait toute la flotte, et pour rien.**
        // On tenait ici le marqueur immobile pendant les cinq premières secondes
        // de chaque horizon, au nom du « véhicule à quai qui ne glisse pas ». Mais
        // `dwellSeconds` vaut **5 pour tout le monde** — 72 véhicules sur 73 au
        // relevé du 18/09/2026 —, c'est une constante de la glisse et non
        // l'observation d'un bus arrêté ; [TransportVehicle.isStopped] le dit déjà
        // en toutes lettres. Et `stopProgress`, le seul champ qui saurait *où* le
        // quai tombe sur le tracé, est **nul pour les 73**.
        //
        // Ce que ça donnait à l'écran, mesuré sur le Samsung : sondage toutes les
        // 15 s, horizon de 10 s, donc **5 s figé, 5 s à vitesse double, 5 s figé**.
        // Un véhicule immobile deux tiers du temps, qui traverse son virage deux
        // fois trop vite, et qui **saute d'une vingtaine de mètres** en une image
        // au moment où le palier se lève — la position retenue est la fin du tracé
        // précédent, la suivante est le milieu du nouveau.
        //
        // Le web ne fait pas ça : sans `stationDwell`, `progressWithStationDwell`
        // rend l'avancement tel quel (`lib/carte-immersive/vehicle-motion.ts`). On
        // glisse donc à vitesse constante sur tout l'horizon, puis on attend le
        // sondage suivant — c'est tout ce que la donnée permet d'affirmer. Le jour
        // où le serveur publiera `stopProgress`, le palier se reposera **au quai**,
        // comme sur le web, et pas au début de la fenêtre.
        val fraction = progress.coerceIn(0.0, 1.0)

        // Le cap vient de **la voie sous la caisse**, lue sur la longueur de cette
        // caisse : c'est ce qui la fait pivoter pendant tout le virage, et se
        // poser dans l'axe dès que la voie est droite. [VehicleGlide] porte la
        // mesure de ce que l'ancienne dérivation — le déplacement entre deux
        // images — coûtait à l'écran.
        val path = paths[vehicle.id]
        if (path != null) {
            val point = PolylineProjection.pointAt(path, fraction)?.point
                ?: return Pose(vehicle.coordinate, startHeading)
            val aim = VehicleGlide.tangent(
                path = path,
                distanceMeters = fraction * path.length,
                spanMeters = VehicleBody.gauge(vehicle.mode).lengthMeters,
            )
            return Pose(point, VehicleGlide.heading(startHeading, aim, dtSeconds))
        }

        // Sans tracé, il ne reste que la corde vers le point d'arrivée annoncé.
        // Elle ne dit rien de la voirie : c'est le repli, pas le cas courant —
        // les 72 véhicules du relevé du 18/09/2026 portaient tous le leur.
        val ahead = vehicle.ahead
        val aim = if (ahead != null &&
            GeoMath.distance(vehicle.coordinate, ahead) >= VehicleGlide.MIN_CHORD_M
        ) {
            GeoMath.bearing(vehicle.coordinate, ahead)
        } else {
            // ⚠️ Surtout pas la corde : `atan2(0, 0)` vaut zéro, et un véhicule
            // arrêté pointerait alors plein nord. Le champ du serveur, lui, dit
            // le cap relevé.
            vehicle.headingDegrees
        }
        val point = ahead
            ?.let { GeoMath.interpolate(vehicle.coordinate, it, fraction) }
            ?: vehicle.coordinate
        return Pose(point, VehicleGlide.heading(startHeading, aim, dtSeconds))
    }

    // ------------------------------------------------------------------ montage

    override fun mount(style: Style, map: MapLibreMap) {
        this.map = map
        // La lumière du jour au montage ; la bascule d'ambiance la remplace.
        scene?.setLighting(VehicleLighting.of(night))

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
        // L'horloge d'images repart avec le style ; un horodatage gardé d'avant
        // rendrait la première durée d'image négative.
        lastFrameSeconds = 0.0
        // La carte n'appartient pas plus à la couche que le style : la garder
        // après un démontage, c'est retenir une `MapLibreMap` détruite pour lire
        // sa caméra. [mount] la rend.
        map = null
    }

    override fun onAmbianceChange(ambiance: MapAmbiance, style: Style) {
        val night = ambiance == MapAmbiance.DARK
        // La scène 3D reçoit une couleur déjà résolue : elle n'a pas
        // d'`Expression` à réévaluer, donc il faut la lui redire — et lui
        // donner la lumière de la nouvelle ambiance, celle du style qui arrive.
        this.night = night
        scene?.setLighting(VehicleLighting.of(night))
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
         * La flotte est **opaque**. Un véhicule ne se voit pas au travers.
         *
         * Elle était à 0,6 — « assez transparente pour qu'on lise la rue
         * dessous ». À z18 sur un tram de vingt-huit mètres, on voyait les rails
         * à travers la caisse : rien ne trahit plus vite un décor qu'un objet
         * qu'on traverse du regard. La rue sous un bus n'a rien à dire, et
         * l'ombre de contact dit déjà où il se pose (ADR-017).
         *
         * Le véhicule choisi ne se distingue donc plus par son opacité mais par
         * son anneau, qui est de toute façon ce qu'on regarde.
         */
        const val FLEET_OPACITY = 1.0

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
        /** L'ordre des maillages installés dans la scène native. */
        const val MESH_BUS = VehicleScene.MESH_BUS
        const val MESH_TRAM = VehicleScene.MESH_TRAM

        const val MAX_BODIES = 48

        const val PROPERTY_ALIGNMENT_MAP = "map"

        /**
         * Le plafond de l'avancement brut.
         *
         * ⚠️ **Il ne prolonge pas la glisse.** [interpolatedPose] borne la fraction
         * à 1 : passé l'horizon, le véhicule attend au bout de son tracé. Ce qui
         * dépasse ne sert donc qu'à empêcher un compteur de courir indéfiniment
         * quand un sondage tarde.
         */
        const val MAX_SLIDE = 1.35

        // Le cap n'a plus ni seuil de déplacement ni part fixe par image : il se
        // lit sur la voie, et se rejoint en un temps donné. Voir [VehicleGlide].

        /** Marge autour du cadre visible, en fraction de sa taille. */
        const val BOUNDS_MARGIN = 0.35
    }
}
