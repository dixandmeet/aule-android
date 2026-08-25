package io.aule.android.core.map

import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.geo.SolarPosition
import io.aule.android.core.map.camera.BuildingEmphasis
import io.aule.android.core.map.camera.CameraMode
import io.aule.android.core.map.camera.CameraTarget
import io.aule.android.core.map.camera.NavigationCamera
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.gestures.RotateGestureDetector
import org.maplibre.android.gestures.ShoveGestureDetector
import org.maplibre.android.gestures.StandardScaleGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.light.Position

/**
 * Le propriétaire unique de la carte.
 *
 * Personne d'autre ne détient la `MapView`, ne déplace la caméra ni ne pose de
 * couche. C'est ce qui permet au moteur d'évoluer sans réécrire l'interface — et
 * ce qui évite qu'un écran et une couche se disputent la caméra sans que rien ne
 * le signale.
 *
 * **Rien ici n'est observable par image.** Ce qui l'est change quelques fois par
 * minute : l'ambiance, le chargement du style, le mode de caméra. Les positions
 * de véhicules passent par les couches et les sources MapLibre, jamais par un
 * `StateFlow`.
 */
class MapController(
    private val logger: AuleLogger,
    private val density: Float,
) {

    // --- État observable : quelques changements par minute, jamais par image ---

    private val _isStyleLoaded = MutableStateFlow(false)
    val isStyleLoaded: StateFlow<Boolean> = _isStyleLoaded.asStateFlow()

    private val _ambiance = MutableStateFlow(MapAmbiance.LIGHT)
    val ambiance: StateFlow<MapAmbiance> = _ambiance.asStateFlow()

    private val _cameraMode = MutableStateFlow(CameraMode.FOLLOW)
    val cameraMode: StateFlow<CameraMode> = _cameraMode.asStateFlow()

    // --- Interne : jamais observable ---

    val registry = MapLayerRegistry()

    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null

    private val frameClock = FrameClock(::onFrame)

    /** Ce que le moteur a réellement accepté comme inclinaison maximale. */
    var measuredMaxPitch: Double = MAX_PITCH_REQUESTED
        private set

    /**
     * Hauteur masquée par le volet, en pixels. Entre dans le cadrage de la caméra.
     *
     * L'écrire **réécrit le cadre en cours** : un tracé cadré pendant que le
     * volet montait finirait pour partie dessous, et c'est exactement ce qui se
     * passe à l'ouverture d'une fiche — le cadre est demandé au changement de
     * sélection, le volet, lui, prend sa hauteur une poignée d'images plus tard.
     */
    var sheetHeightPx: Float = 0f
        set(value) {
            if (value == field) return
            field = value
            reframeForSheet()
        }

    var onTapMap: ((Coordinate) -> Unit)? = null
    var onUserTookControl: (() -> Unit)? = null
    var onRegionSettled: ((center: Coordinate, zoom: Double, radiusMeters: Double) -> Unit)? = null
    var onMapLoadFailure: ((String) -> Unit)? = null

    /** Vrai pendant nos propres animations, pour ne pas les prendre pour un geste. */
    private var suppressGestureDetection = false

    /** Combien de gestes sont en cours. Vrai tant qu'un doigt pilote la carte. */
    private var activeGestureCount = 0
    private val isGestureActive: Boolean get() = activeGestureCount > 0

    /** Vrai pendant notre propre réglage d'inclinaison, pour ne pas le déclencher sur lui-même. */
    private var isAdjustingPitch = false

    /**
     * L'inclinaison retirée en prenant de la hauteur, à rendre en
     * redescendant. `null` tant qu'on n'a rien pris : on ne relève que ce
     * qu'on a couché, jamais une carte que le produit voulait à plat.
     */
    private var owedPitch: Double? = null

    private var lastAppliedTarget: CameraTarget? = null

    /**
     * La hauteur de volet avec laquelle le dernier cadrage a été écrit.
     *
     * Le volet fait partie du cadrage : c'est lui qui décide de la bande
     * réellement visible, donc de l'endroit où le sujet doit tomber. Un
     * véhicule **à quai** ne bouge pas d'un mètre pendant qu'on déplie le
     * volet sur lui : sans cette mémoire, le seuil de delta considère qu'il
     * n'y a rien à réécrire, et le sujet disparaît sous le volet qui monte.
     */
    private var lastAppliedSheetPx: Float = 0f

    /**
     * Le tracé que la caméra encadre, **tant que le cadre est encore le nôtre**.
     *
     * Il sert à réécrire le cadre quand le volet change de hauteur : une ligne
     * désignée doit tenir dans la bande visible, et cette bande se rétrécit dès
     * qu'un volet monte dessus. On l'oublie au premier geste et à tout autre
     * mouvement de caméra — reprendre le cadre après coup volerait la carte à
     * quelqu'un qui est en train de la lire.
     */
    private var framedCoordinates: List<Coordinate>? = null

    /** La hauteur de volet avec laquelle [framedCoordinates] a été cadré. */
    private var framedSheetPx: Float = 0f
    private var isCameraCallInFlight = false
    private var lastRegionEmitElapsed = 0L

    /**
     * Ce que les bâtiments valent en ce moment, de 0 à 1.
     *
     * Le champ existe pour deux raisons, et la seconde n'est pas évidente :
     * il évite de repeindre toutes les extrusions de l'écran quinze fois par
     * seconde, et surtout il **survit au rechargement du style**. Passer en
     * mode sombre reconstruit la couche `building-3d` avec l'opacité écrite
     * dans le JSON : sans cette mémoire, la ville reviendrait pleine au
     * milieu d'un guidage, à la seule occasion d'un coucher de soleil.
     */
    private var buildingEmphasis: Double = BuildingEmphasis.FULL

    /**
     * La dernière lumière écrite, pour ne pas réécrire la même.
     *
     * L'égalité est **exacte**, et c'est voulu : de jour le soleil bouge à
     * chaque battement, donc on écrit à chaque battement ; de nuit
     * [MapSunlight] rend des constantes écrêtées, rigoureusement les mêmes
     * d'une fois sur l'autre, et il n'y a alors rien à repeindre jusqu'au
     * matin.
     *
     * ⚠️ **À oublier au rechargement du style**, comme [buildingEmphasis] :
     * le style neuf arrive avec la lumière figée de son JSON, et un contrôleur
     * qui croit l'avoir déjà écrite laisserait la carte éclairée à l'heure du
     * générateur.
     */
    private var lastSunlight: SunlightSetting? = null

    /**
     * Depuis quand la caméra n'appartient plus à personne.
     *
     * Zéro tant qu'elle n'a jamais été libérée. C'est ce que lit le guidage
     * pour savoir s'il a le droit de reprendre le cadrage : regarder ailleurs
     * pendant une navigation est légitime, mais l'oublier l'est aussi — et
     * personne ne pense à rappuyer sur « Recentrer » au moment où la route
     * redevient intéressante.
     *
     * ⚠️ **C'est la libération qu'on date, pas le geste.** Un doigt n'est pas
     * le seul chemin vers l'exploration libre : un vol vers une relève, un
     * tracé qu'on cadre y passent aussi, et sans geste. Daté sur le dernier
     * geste, le compteur d'un utilisateur qui n'a pas touché la carte depuis
     * dix minutes vaut « il y a longtemps » — et le guidage reprendrait la
     * caméra dans la trame **suivant** le décollage, tuant le vol qu'on
     * venait de demander. Chaque geste repousse l'échéance, ce qui couvre
     * aussi le cas d'un doigt qui travaille sur une carte déjà libre.
     */
    private var cameraFreedElapsed = 0L

    // ------------------------------------------------------------------ montage

    fun attach(mapView: MapView, map: MapLibreMap, ambiance: MapAmbiance) {
        this.mapView = mapView
        this.map = map
        _ambiance.value = ambiance

        map.uiSettings.apply {
            isLogoEnabled = false
            isAttributionEnabled = false
            isCompassEnabled = false
            // Rien du chrome MapLibre ne survit : le HUD tient seul le bas
            // de l'écran, et l'attribution n'est plus affichée.
        }

        // On demande la limite documentée de MapLibre Android et on **relève ce
        // qu'on obtient** plutôt que de le supposer. Une version native peut
        // encore appliquer un plafond plus bas sans lever d'exception.
        map.setMaxPitchPreference(MAX_PITCH_REQUESTED)
        measuredMaxPitch = map.maxPitch
        if (measuredMaxPitch < MAX_PITCH_REQUESTED) {
            logger.info(
                LogDomain.MAP,
                "Inclinaison plafonnée par le moteur : $measuredMaxPitch° " +
                    "(demandé $MAX_PITCH_REQUESTED°). Le cadrage de navigation " +
                    "reposera sur le zoom.",
            )
        }

        map.setOnFpsChangedListener { fps -> renderedFps = fps.toInt() }
        mapView.addOnDidFailLoadingMapListener { error ->
            logger.error(LogDomain.MAP, "Fond de carte en échec : $error")
            onMapLoadFailure?.invoke(error)
        }

        installGestureListeners(map)
        loadStyle(ambiance)
    }

    fun detach() {
        // Avant `MapView.onDestroy` : après, le `Style` est invalide — en retirer
        // une couche lève `IllegalStateException`, et ce qu'on écrit dans une
        // source part dans le vide sans un mot.
        frameClock.stop()
        forgetFrame()
        style?.let { registry.unmountAll(it) }
        // Et non « marquer démonté » : les couches doivent **lâcher** ce qu'elles
        // tiennent du style. Les appelants qui écrivent hors du registre — le
        // ticker caméra pour le puck, l'écran carte pour la flotte — ne savent pas
        // que la carte vient de disparaître sous eux.
        registry.styleWasDiscarded()
        _isStyleLoaded.value = false
        style = null
        map = null
        mapView = null
    }

    fun onResume() {
        frameClock.setMuted(false)
        startFrameLoopIfNeeded()
    }

    fun onPause() {
        // En veille, pas à l'arrêt : l'horloge continue de courir. La remettre à
        // zéro ferait sauter toute la flotte en arrière au retour.
        frameClock.setMuted(true)
    }

    // -------------------------------------------------------------------- style

    fun setAmbiance(next: MapAmbiance) {
        if (next == _ambiance.value) return
        _ambiance.value = next
        loadStyle(next)
    }

    private fun loadStyle(ambiance: MapAmbiance) {
        val map = map ?: return
        // Un rechargement de style vide sources, couches **et images**, en
        // silence. On prévient les couches avant, sinon `mountPending` croirait
        // n'avoir rien à faire et la carte se viderait sans erreur — et celles qui
        // sont écrites hors du registre continueraient de publier dans les sources
        // du style mort, le temps que le suivant se charge.
        registry.styleWasDiscarded()
        _isStyleLoaded.value = false
        style = null
        // Le prochain style arrivera avec la lumière de son JSON : ce qu'on a
        // écrit dans celui-ci ne dit plus rien de ce qu'il porte.
        lastSunlight = null

        map.setStyle(Style.Builder().fromUri(ambiance.assetPath)) { loaded ->
            onStyleLoaded(loaded, ambiance)
        }
    }

    /** L'ordre de ces appels n'est pas commutatif. */
    private fun onStyleLoaded(loaded: Style, ambiance: MapAmbiance) {
        val map = map ?: return
        style = loaded

        MapIcons.register(loaded, night = ambiance == MapAmbiance.DARK)
        // Une image absente ne fait pas d'erreur : `iconImage` ne dessine
        // simplement rien. On vérifie donc que le registre a bien pris.
        val sample = MapIcons.stopPlaceName(io.aule.android.core.model.TransportMode.TRAM)
        if (loaded.getImage(sample) == null) {
            logger.error(LogDomain.MAP, "Icône « $sample » absente du style après enregistrement.")
        }
        registry.mountPending(loaded, map)
        registry.broadcastAmbiance(ambiance, loaded)
        // Le style neuf porte l'opacité de son JSON : on lui réimpose celle
        // que la situation demande, sinon la ville revient pleine au premier
        // changement d'ambiance — en pleine navigation, sans un mot.
        applyBuildingEmphasis(animated = false)
        // Pour la même raison : le JSON porte la lumière d'un instant choisi
        // par le générateur, pas celle de maintenant.
        refreshSunlight()
        _isStyleLoaded.value = true

        logger.info(
            LogDomain.MAP,
            "Style ${ambiance.name.lowercase()} chargé, ${registry.layers.size} couche(s) posée(s).",
        )
        startFrameLoopIfNeeded()
    }

    private fun startFrameLoopIfNeeded() {
        if (_isStyleLoaded.value && registry.hasAnimatedLayer) frameClock.start()
    }

    private var ticksSinceReport = 0
    private var lastReportSeconds = 0.0

    private var frameCostNanos = 0L
    private var worstFrameNanos = 0L

    private fun onFrame(elapsedSeconds: Double) {
        val started = System.nanoTime()
        registry.broadcastFrame(elapsedSeconds)
        val cost = System.nanoTime() - started
        frameCostNanos += cost
        if (cost > worstFrameNanos) worstFrameNanos = cost

        // La cadence d'interpolation, une fois par seconde et seulement en DEBUG.
        // C'est la seule façon de la constater : MapLibre rend dans sa propre
        // surface, hors du pipeline d'images de l'application, et `dumpsys
        // gfxinfo` rapporte donc zéro image — ce qui prouve l'absence de
        // recomposition, mais ne dit rien de la carte.
        ticksSinceReport++
        if (elapsedSeconds - lastReportSeconds >= 1.0) {
            val hz = ticksSinceReport / (elapsedSeconds - lastReportSeconds)
            val meanMicros = if (ticksSinceReport > 0) frameCostNanos / ticksSinceReport / 1000 else 0
            logger.debug(
                LogDomain.MAP,
                "Interpolation : ${hz.toInt()} Hz · rendu : $renderedFps ips · " +
                    "coût moyen ${meanMicros} µs, pire ${worstFrameNanos / 1000} µs " +
                    "(budget 8333 µs à 120 Hz)",
            )
            ticksSinceReport = 0
            lastReportSeconds = elapsedSeconds
            frameCostNanos = 0
            worstFrameNanos = 0
        }
    }

    /** Images par seconde réellement produites par MapLibre. */
    private var renderedFps: Int = 0

    // -------------------------------------------------------------- lisibilité

    /**
     * Dose la présence des bâtiments, de 0 (invisibles) à 1 (pleins).
     *
     * ⚠️ **Ce n'est pas un réglage d'ambiance, c'est un réglage de lecture.**
     * Les volumes restent allumés en toute circonstance — ce sont eux qui
     * font reconnaître un endroit —, mais ils sont seconds : à l'inclinaison
     * de la navigation, une façade proche prend le quart de l'écran et masque
     * précisément la rue qu'on suit. On la fait donc reculer pendant qu'on
     * suit un trajet, et davantage encore à l'approche d'un carrefour.
     *
     * L'écriture est **quantifiée** : le niveau vient d'une imminence
     * continue, réévaluée quinze fois par seconde, et repeindre toutes les
     * extrusions à ce rythme coûterait plus cher que tout le reste de la
     * carte. On n'écrit qu'au-delà de [BuildingEmphasis.STEP], et c'est le
     * moteur qui interpole entre deux valeurs écrites.
     */
    fun setBuildingEmphasis(level: Double) {
        val wanted = level.coerceIn(0.0, 1.0)
        if (abs(wanted - buildingEmphasis) < BuildingEmphasis.STEP) return
        buildingEmphasis = wanted
        applyBuildingEmphasis(animated = true)
    }

    /**
     * Écrit l'opacité des volumes, **fondu d'apparition compris**.
     *
     * On ne pose pas une constante : le style fait entrer les extrusions
     * entre [BUILDINGS_FADE_FROM] et [BUILDINGS_FADE_TO], et écraser
     * l'expression par un nombre ferait surgir la ville d'un bloc au
     * franchissement du seuil. On réécrit donc la même rampe, avec un
     * plafond différent.
     *
     * L'animation est laissée au moteur ([TransitionOptions]) : il interpole
     * une propriété de peinture bien mieux qu'une boucle de notre côté, et
     * sans réveiller la caméra.
     */
    private fun applyBuildingEmphasis(animated: Boolean) {
        val style = style ?: return
        // Le style peut ne pas porter la couche — un fond de secours, un
        // style futur. C'est un cas normal, pas une panne : la carte reste
        // lisible, simplement sans volumes à doser.
        val layer = style.getLayer(BUILDINGS_3D_LAYER) as? FillExtrusionLayer ?: return
        layer.fillExtrusionOpacityTransition = TransitionOptions(
            if (animated) AuleMotion.CAMERA_NUDGE_MS.toLong() else 0L,
            0L,
        )
        layer.setProperties(
            PropertyFactory.fillExtrusionOpacity(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(BUILDINGS_FADE_FROM, 0f),
                    Expression.stop(BUILDINGS_FADE_TO, buildingEmphasis.toFloat()),
                ),
            ),
        )
    }

    // ------------------------------------------------------------------- soleil

    /**
     * Réécrit la lumière du style avec la position réelle du soleil.
     *
     * Le bloc `light` des deux styles est figé à l'instant où le générateur les
     * a produits : la ville y est éclairée à la même heure toute l'année. Ce
     * qu'on rend ici, c'est le **volume** — une façade tournée vers le soleil
     * s'éclaire, celle d'en face s'éteint, et la même rue ne se lit pas pareil
     * à huit heures et à midi. Il n'y a toujours pas d'ombre portée : MapLibre
     * ombre chaque face selon son orientation, il ne projette rien sur le
     * trottoir ([MapSunlight] le détaille).
     *
     * ⚠️ **L'heure vient de la montre, pas du chronomètre.** Le reste du
     * fichier date ses délais avec `SystemClock.elapsedRealtime`, qui compte
     * depuis le démarrage de l'appareil et ne sait rien du calendrier. Le
     * soleil, lui, se calcule sur un instant absolu — d'où
     * [System.currentTimeMillis].
     *
     * Sans transition : appelé à la minute, l'écart d'un battement vaut un
     * quart de degré d'azimut, soit moins que ce qu'un écran peut montrer. Une
     * transition n'y gagnerait rien et coûterait un piège — l'azimut est un
     * cap, et l'interpoler à la traversée du nord ferait faire un tour complet
     * à la lumière.
     */
    fun refreshSunlight(nowMillis: Long = System.currentTimeMillis()) {
        val style = style ?: return
        val light = style.light ?: return

        // Le centre de la caméra, et non la position GPS : il ne demande aucune
        // autorisation, et à l'échelle d'une agglomération les deux donnent le
        // même soleil — un degré de longitude ne pèse que quatre minutes de
        // temps solaire, et Nantes en fait moins d'un tiers d'un bout à l'autre.
        //
        // ⚠️ **Le repli ne se joue pas sur la nullité.** Tant que la caméra n'a
        // pas été posée, MapLibre ne rend pas `null` mais le point (0, 0) — le
        // golfe de Guinée, à quatre mille kilomètres et sous un autre soleil.
        // Le premier lancement sur le S21 ouvrait ainsi la ville éclairée
        // d'aplomb un matin d'août. C'est le cas que [Coordinate.isValid]
        // existe pour attraper, et il faut le lui demander.
        val over = cameraCenter?.takeIf { it.isValid } ?: Coordinate.NANTES
        val setting = MapSunlight.of(SolarPosition.at(over, nowMillis), _ambiance.value)
        if (setting == lastSunlight) return
        val isFirstOfStyle = lastSunlight == null
        lastSunlight = setting

        light.position = Position(
            SUN_DISTANCE,
            setting.azimuthDegrees.toFloat(),
            setting.polarDegrees.toFloat(),
        )
        light.setColor(setting.color.argb)
        light.intensity = setting.intensity.toFloat()

        // Une ligne par style chargé, pas une par battement : c'est la seule
        // trace écrite d'un réglage qu'on ne peut que constater à l'œil.
        if (isFirstOfStyle) {
            logger.debug(
                LogDomain.MAP,
                "Soleil sur $over : azimut ${setting.azimuthDegrees.toInt()}°, " +
                    "polaire ${setting.polarDegrees.toInt()}°, force ${setting.intensity}.",
            )
        }
    }

    // ------------------------------------------------------------------- caméra
    //
    // La caméra se pose **après** création de la vue, jamais via une caméra
    // initiale : à la construction, la vue n'a pas encore de taille et le
    // cadrage calculé sur elle est faux — un décalage qu'on ne voit qu'au
    // premier lancement. C'est pourquoi chaque entrée d'ici vérifie que la
    // vue est mesurée, et se rejoue au passage suivant sinon.

    /**
     * Cadre un lieu qu'on vient de nommer — un arrêt cherché, une adresse.
     *
     * Distinct de [moveTo] : on **anime**, et on passe en exploration libre
     * pour que le suivi ne ramène pas la caméra sur le puck pendant le vol.
     *
     * @param pitch l'inclinaison d'arrivée. **En volume par défaut** : la carte
     *   d'Aule se regarde inclinée, et une arrivée à plat sur un écran qui
     *   s'incline partout ailleurs se lit comme une autre application. C'est
     *   aussi le volume des bâtiments qui fait reconnaître un endroit où l'on
     *   est déjà passé. Un appelant qui veut un plan le demande explicitement.
     */
    fun flyTo(
        center: Coordinate,
        zoom: Double = MapZoom.OPENING,
        pitch: Double = MapZoom.PITCH_3D,
    ) {
        val map = map ?: return
        val view = mapView ?: return
        if (view.width == 0 || view.height == 0) {
            view.post { flyTo(center, zoom, pitch) }
            return
        }
        setCameraMode(CameraMode.FREE_EXPLORE)
        forgetOwedPitch()
        forgetFrame()
        val position = cameraPosition(center, zoom, pitch = pitch, bearing = 0.0, topPaddingPx = 0.0)
        suppressGestureDetection = true
        isCameraCallInFlight = true
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(position),
            AuleMotion.CAMERA_FLY_MS,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() = releaseCameraCall()
                override fun onCancel() = releaseCameraCall()
            },
        )
        lastAppliedTarget = null
    }

    /**
     * Se **rapproche** d'un lieu désigné, sans lui plonger dessus.
     *
     * C'est le geste d'une sélection — un arrêt touché dans la recherche, un
     * quai choisi dans une fiche de ligne —, et il diffère de [flyTo] sur le
     * seul point qui compte : le zoom d'arrivée n'est pas une valeur fixe,
     * c'est **celui qu'on a déjà**, ramené dans des bornes raisonnables.
     *
     * Un zoom fixe fait l'un ou l'autre des deux défauts, selon d'où l'on
     * part : venu de l'échelle de la ville, il se pose si près qu'on ne sait
     * toujours pas où l'on est ; venu d'une rue, il recule sans qu'on l'ait
     * demandé. La borne, elle, ne corrige que ce qui est vraiment trop loin
     * ou vraiment trop près — et l'objet sélectionné garde son quartier
     * autour de lui.
     */
    fun focusOn(center: Coordinate, pitch: Double = MapZoom.PITCH_3D) {
        flyTo(
            center = center,
            zoom = NavigationCamera.selectionZoom(
                currentZoom = cameraZoom,
                minZoom = MapZoom.SELECTION_MIN,
                maxZoom = MapZoom.SELECTION_MAX,
            ),
            pitch = pitch,
        )
    }

    /**
     * Cadre un tracé pour qu'il tienne dans la bande visible, volet compris.
     *
     * Une seule coordonnée non finie étirerait la boîte jusqu'à l'infini, et
     * le cadrage montrerait la planète entière — une panne spectaculaire pour
     * une donnée manquante.
     */
    fun frame(coordinates: List<Coordinate>) {
        val usable = coordinates.filter { it.latitude.isFinite() && it.longitude.isFinite() }
        if (usable.isEmpty()) return
        if (usable.size < 2) {
            flyTo(usable.first())
            return
        }
        setCameraMode(CameraMode.FREE_EXPLORE)
        forgetOwedPitch()
        // Après [setCameraMode], qui oublie le cadre précédent.
        framedCoordinates = usable
        applyFrame(usable, animated = true)
    }

    /**
     * Rend le cadre : plus rien à tenir à l'écran.
     *
     * À appeler quand le tracé cadré s'efface — une ligne qu'on ne désigne
     * plus, un volet qui se referme. Sans ça, le volet qui redescend
     * ramènerait la caméra sur une ligne que la carte ne peint plus.
     */
    fun releaseFrame() {
        forgetFrame()
    }

    /**
     * Écrit le cadre. Animé à l'entrée, sec quand c'est le volet qui bouge :
     * on suit alors un doigt, et une animation par image flotterait derrière lui.
     */
    private fun applyFrame(coordinates: List<Coordinate>, animated: Boolean) {
        val map = map ?: return
        val view = mapView ?: return
        if (view.width == 0 || view.height == 0) {
            // La vue n'a pas encore de taille : le cadre calculé sur elle serait
            // faux. On rejoue au prochain passage, sauf si la sélection a changé
            // entre-temps.
            view.post { if (framedCoordinates === coordinates) applyFrame(coordinates, animated) }
            return
        }
        val builder = org.maplibre.android.geometry.LatLngBounds.Builder()
        coordinates.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
        val pad = (FRAME_MARGIN_DP * density).toInt().coerceAtLeast(32)
        // Le volet peut couvrir l'écran entier — une fiche de ligne s'ouvre
        // ainsi. La marge basse demandée dépasserait alors la hauteur de la
        // vue, et le moteur cadrerait sur une bande de hauteur négative. On
        // garde donc toujours une bande à lire : le tracé y tient en petit,
        // et il retrouve sa taille dès que le volet redescend.
        val room = view.height - pad - (MIN_FRAME_BAND_DP * density).toInt()
        val bottom = (pad + sheetHeightPx.toInt()).coerceAtMost(room.coerceAtLeast(pad))
        // **Cadrer à plat et au nord.** Sans cap ni inclinaison donnés, MapLibre
        // reprend ceux de la caméra (`getCameraForLatLngBounds(bounds, padding)`
        // lit `Transform`), et un cadre calculé sur une vue inclinée laisse le
        // tracé déborder de l'écran — la carte s'incline toute seule au zoom de
        // la rue, donc le cas est la règle, pas l'exception.
        val position = map.getCameraForLatLngBounds(
            builder.build(),
            intArrayOf(pad, pad, pad, bottom),
            0.0,
            0.0,
        ) ?: return
        framedSheetPx = sheetHeightPx
        lastAppliedTarget = null
        // À l'entrée seulement : le suivi du volet réécrit le cadre des dizaines
        // de fois par glissement, et autant de lignes noieraient le journal.
        if (animated) {
            logger.debug(
                LogDomain.MAP,
                "Cadrage : vue ${view.width}×${view.height}, marges $pad/$bottom, " +
                    "zoom ${position.zoom}, cible ${position.target}.",
            )
        }
        suppressGestureDetection = true
        if (animated) {
            isCameraCallInFlight = true
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(position),
                AuleMotion.CAMERA_FLY_MS,
                object : MapLibreMap.CancelableCallback {
                    override fun onFinish() = finishFrame()
                    override fun onCancel() = finishFrame()
                },
            )
        } else {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
            suppressGestureDetection = false
        }
    }

    /**
     * Le volet a bougé : le cadre se réécrit sur la bande qui reste.
     *
     * Rien pendant notre propre animation — un `moveCamera` l'annulerait à
     * mi-course. [finishFrame] rattrape ce que le volet a fait pendant le vol.
     */
    private fun reframeForSheet() {
        val coordinates = framedCoordinates ?: return
        if (isCameraCallInFlight) return
        if (abs(sheetHeightPx - framedSheetPx) <= SHEET_STEP_PX) return
        applyFrame(coordinates, animated = false)
    }

    private fun finishFrame() {
        releaseCameraCall()
        reframeForSheet()
    }

    fun moveTo(center: Coordinate, zoom: Double, pitch: Double = 0.0, bearing: Double = 0.0) {
        val view = mapView ?: return

        // La vue doit avoir une taille. `onStyleLoaded` arrive régulièrement avant
        // la première mise en page, et MapLibre cadre alors sur un viewport de
        // 0 × 0 : la caméra part à l'échelle du monde, et les couches à seuil de
        // zoom — les arrêts en premier — ne dessinent rien. Le défaut est
        // intermittent, ce qui le rend pénible à attribuer.
        if (view.width == 0 || view.height == 0) {
            view.post { moveTo(center, zoom, pitch, bearing) }
            return
        }
        applyCamera(center, zoom, pitch, bearing, topPaddingPx = 0.0)
    }

    fun setCameraMode(mode: CameraMode) {
        if (mode == _cameraMode.value) return
        _cameraMode.value = mode
        lastAppliedTarget = null
        forgetOwedPitch()
        forgetFrame()
        if (!mode.followsSomething) {
            applyPadding(topPx = 0.0)
            // La caméra vient d'être rendue : c'est de cet instant que le
            // guidage compte avant d'oser la reprendre.
            cameraFreedElapsed = SystemClock.elapsedRealtime()
        }
        logger.info(LogDomain.MAP, "Mode caméra : $mode")
    }

    /**
     * Le bouton unique : recentrer si l'on s'est éloigné, sinon basculer
     * l'orientation.
     */
    fun toggleFollow() {
        setCameraMode(
            when (_cameraMode.value) {
                CameraMode.FREE_EXPLORE, CameraMode.OVERVIEW -> CameraMode.FOLLOW
                CameraMode.FOLLOW -> CameraMode.NAVIGATION
                CameraMode.NAVIGATION, CameraMode.FOLLOW_VEHICLE -> CameraMode.FOLLOW
            },
        )
    }

    /**
     * Applique un cadrage. Appelé au rythme des positions GPS, pas à chaque image.
     *
     * Trois garde-fous, tous appris du portage Flutter :
     * - **un seul appel en vol** — MapLibre ignore un `moveCamera` reçu pendant
     *   une animation, et l'app croirait alors avoir bougé ;
     * - **un seuil de delta** — écrire pour trois centimètres réveille le
     *   moteur de rendu sans rien changer à l'écran ;
     * - **pas d'animation au suivi** — on anime seulement à l'entrée dans un
     *   mode, sinon deux animations se chevauchent et la carte flotte.
     */
    fun applyCameraTarget(target: CameraTarget): Boolean {
        val map = map ?: return false
        if (isCameraCallInFlight) return false

        val animated = lastAppliedTarget == null && _cameraMode.value.followsSomething
        val last = lastAppliedTarget
        // Le volet a bougé : le cadrage se réécrit même si le sujet, lui, est
        // resté sur place. Sans animation — on suit un doigt qui fait glisser
        // le volet, et une animation par image flotterait derrière lui.
        val sheetMoved = abs(sheetHeightPx - lastAppliedSheetPx) > SHEET_STEP_PX
        if (!animated && last != null && !sheetMoved && !isSignificant(target, last)) return false

        // Un mode qui pilote décide seul de son inclinaison, et du cadre : la
        // dette d'un dézoom précédent comme le tracé encadré n'ont plus d'objet.
        forgetOwedPitch()
        forgetFrame()

        val topPx = target.forwardOffsetPx * 2.0 * density
        val position = cameraPosition(
            center = target.center,
            zoom = target.zoom,
            pitch = target.pitch,
            bearing = target.bearing,
            topPaddingPx = topPx,
        )

        suppressGestureDetection = true
        if (animated) {
            isCameraCallInFlight = true
            // Zoom, cap et inclinaison changent ensemble : c'est le mouvement
            // le plus long du lot, et il doit l'être — joué court, il se lit
            // comme une bascule plutôt que comme un décollage.
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(position),
                AuleMotion.CAMERA_MODE_MS,
                object : MapLibreMap.CancelableCallback {
                    override fun onFinish() = releaseCameraCall()
                    override fun onCancel() = releaseCameraCall()
                },
            )
        } else {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
            suppressGestureDetection = false
        }
        lastAppliedTarget = target
        lastAppliedSheetPx = sheetHeightPx
        maybeEmitRegionFromFollow()
        return true
    }

    private fun releaseCameraCall() {
        isCameraCallInFlight = false
        suppressGestureDetection = false
    }

    /** Un mouvement mérite-t-il d'être écrit ? */
    private fun isSignificant(target: CameraTarget, last: CameraTarget): Boolean =
        GeoMath.distance(target.center, last.center) > 0.3 ||
            abs(GeoMath.shortestHeadingDelta(last.bearing, target.bearing)) > 0.5 ||
            abs(target.zoom - last.zoom) > 0.01 ||
            abs(target.pitch - last.pitch) > 0.25

    private fun applyCamera(
        center: Coordinate,
        zoom: Double,
        pitch: Double,
        bearing: Double,
        topPaddingPx: Double,
    ) {
        val map = map ?: return
        forgetOwedPitch()
        forgetFrame()
        val position = cameraPosition(center, zoom, pitch, bearing, topPaddingPx)
        suppressGestureDetection = true
        map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
        suppressGestureDetection = false

        logger.debug(
            LogDomain.MAP,
            "Caméra posée : zoom demandé $zoom, obtenu ${map.cameraPosition.zoom}.",
        )
    }

    private fun applyPadding(topPx: Double) {
        val map = map ?: return
        val current = map.cameraPosition
        val position = CameraPosition.Builder(current)
            .padding(0.0, topPx, 0.0, sheetHeightPx.toDouble())
            .build()
        suppressGestureDetection = true
        map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
        suppressGestureDetection = false
    }

    private fun cameraPosition(
        center: Coordinate,
        zoom: Double,
        pitch: Double,
        bearing: Double,
        topPaddingPx: Double,
    ): CameraPosition = CameraPosition.Builder()
        .target(LatLng(center.latitude, center.longitude))
        .zoom(zoom)
        .tilt(pitch.coerceAtMost(measuredMaxPitch))
        .bearing(bearing)
        // Le padding voyage **dans** la position : le décalage avant (haut × 2,
        // pour descendre le sujet d'autant) et le volet sont donc appliqués
        // dans la même trame. Sur iOS le `contentInset` s'écrivait séparément,
        // ce qui laissait une image où l'un était posé sans l'autre.
        .padding(0.0, topPaddingPx, 0.0, sheetHeightPx.toDouble())
        .build()

    val cameraCenter: Coordinate?
        get() = map?.cameraPosition?.target?.let { Coordinate(it.latitude, it.longitude) }

    val cameraZoom: Double get() = map?.cameraPosition?.zoom ?: MapZoom.OPENING

    val currentBearing: Double
        get() = map?.cameraPosition?.bearing ?: 0.0

    /**
     * Depuis combien de temps la caméra est libre, en millisecondes.
     *
     * Zéro tant qu'elle ne l'a jamais été — et zéro veut dire « pas encore »,
     * donc pas de reprise. Le compteur repart à **chaque** geste et à chaque
     * entrée en mode libre : quelqu'un qui fait glisser la carte pendant dix
     * secondes ne doit pas se la voir reprendre entre deux doigts posés, et
     * un vol qui vient de décoller doit avoir le temps d'atterrir.
     */
    val elapsedSinceCameraFreedMs: Long
        get() = if (cameraFreedElapsed == 0L) {
            // Jamais libérée : « à l'instant » est la réponse prudente. Elle
            // interdit toute reprise, ce qui est le bon défaut — on ne
            // reprend que ce qu'on a laissé.
            0L
        } else {
            SystemClock.elapsedRealtime() - cameraFreedElapsed
        }

    /** Hauteur de la carte, en dp — l'unité dans laquelle [CameraTarget] s'exprime. */
    val viewportHeightDp: Double
        get() {
            val view = mapView ?: return 0.0
            return if (density > 0f) view.height / density.toDouble() else 0.0
        }

    val sheetHeightDp: Double
        get() = if (density > 0f) (sheetHeightPx / density).toDouble() else 0.0

    // ------------------------------------------------------------------- gestes

    private fun installGestureListeners(map: MapLibreMap) {
        map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) = beginGesture()
            override fun onMove(detector: MoveGestureDetector) = Unit
            override fun onMoveEnd(detector: MoveGestureDetector) {
                endGesture()
                settleRegion()
            }
        })
        map.addOnRotateListener(object : MapLibreMap.OnRotateListener {
            override fun onRotateBegin(detector: RotateGestureDetector) = beginGesture()
            override fun onRotate(detector: RotateGestureDetector) = Unit
            override fun onRotateEnd(detector: RotateGestureDetector) = endGesture()
        })
        map.addOnScaleListener(object : MapLibreMap.OnScaleListener {
            override fun onScaleBegin(detector: StandardScaleGestureDetector) = beginGesture()
            override fun onScale(detector: StandardScaleGestureDetector) = Unit
            override fun onScaleEnd(detector: StandardScaleGestureDetector) {
                endGesture()
                settleRegion()
            }
        })
        map.addOnShoveListener(object : MapLibreMap.OnShoveListener {
            override fun onShoveBegin(detector: ShoveGestureDetector) = beginGesture()
            override fun onShove(detector: ShoveGestureDetector) = Unit
            override fun onShoveEnd(detector: ShoveGestureDetector) = endGesture()
        })

        // **L'inclinaison suit le doigt, jamais une animation.** Pendant un
        // geste, personne d'autre n'écrit la caméra : un `moveCamera` y est
        // sans risque et l'inclinaison se règle au rythme du pincement. Hors
        // geste, ce même appel annulerait l'animation en cours — l'inertie
        // du zoom, un double-tap, un `flyTo` — et le mouvement s'arrêterait
        // net à mi-course. On attend donc que la caméra se pose.
        map.addOnCameraMoveListener { if (isGestureActive) applyPitchForZoom(animated = false) }
        map.addOnCameraIdleListener { applyPitchForZoom(animated = true) }

        map.addOnMapClickListener { latLng -> handleTap(map, latLng) }
    }

    private fun beginGesture() {
        // Un compteur, pas un booléen : pincer en tournant ouvre deux gestes,
        // et la fin du premier ne signifie pas que les doigts ont quitté
        // l'écran.
        activeGestureCount++
        handleUserGesture()
    }

    private fun endGesture() {
        activeGestureCount = (activeGestureCount - 1).coerceAtLeast(0)
    }

    /**
     * Couche la carte, ou la relève, selon la hauteur à laquelle on est.
     *
     * En dézoomant on quitte l'échelle de la rue, celle où l'inclinaison
     * sert à quelque chose : passé le seuil elle ne fait plus qu'écraser le
     * lointain, et la carte revient à plat. En rezoomant, elle se relève de
     * la même quantité et par la même rampe. Le va-et-vient est symétrique
     * parce que [owedPitch] retient ce qui a été retiré — la décision, elle,
     * est prise ailleurs et sans carte, dans
     * [NavigationCamera.pitchForZoom].
     */
    private fun applyPitchForZoom(animated: Boolean) {
        val map = map ?: return
        if (isAdjustingPitch || isCameraCallInFlight) return

        val current = map.cameraPosition
        val decision = NavigationCamera.pitchForZoom(current.tilt, current.zoom, owedPitch)
        owedPitch = decision.owedPitch
        val pitch = decision.pitch ?: return

        val position = CameraPosition.Builder(current).tilt(pitch).build()
        isAdjustingPitch = true
        if (animated) {
            suppressGestureDetection = true
            isCameraCallInFlight = true
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(position),
                AuleMotion.CAMERA_NUDGE_MS,
                object : MapLibreMap.CancelableCallback {
                    override fun onFinish() = finishPitchAdjust()
                    override fun onCancel() = finishPitchAdjust()
                },
            )
        } else {
            // En plein geste : on ne touche pas à `suppressGestureDetection`,
            // sinon on masquerait le geste qui est en train d'avoir lieu.
            map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
            isAdjustingPitch = false
        }
    }

    private fun finishPitchAdjust() {
        isAdjustingPitch = false
        releaseCameraCall()
    }

    /**
     * Efface la dette d'inclinaison : quelqu'un d'autre vient de décider du
     * cadrage.
     *
     * Un mode de caméra, un vol vers une adresse, un tracé à cadrer posent
     * l'inclinaison qu'ils veulent — souvent zéro. La rendre au prochain
     * zoom relèverait une carte que le produit avait couchée exprès.
     */
    private fun forgetOwedPitch() {
        owedPitch = null
    }

    /**
     * Oublie le tracé encadré : la caméra vient de partir ailleurs.
     *
     * Sans cet oubli, le prochain mouvement de volet ramènerait la carte sur
     * une ligne que plus personne ne regarde.
     */
    private fun forgetFrame() {
        framedCoordinates = null
    }

    /**
     * Un doigt sur la carte rend la main à l'utilisateur — **sans rien annuler**.
     *
     * Regarder ailleurs ne vaut pas renoncement : le guidage tient, et
     * « Recentrer » reprend là où on en était.
     */
    private fun handleUserGesture() {
        if (suppressGestureDetection) return
        cameraFreedElapsed = SystemClock.elapsedRealtime()
        // Le cadre cesse d'être le nôtre : le volet ne le réécrira plus.
        forgetFrame()
        if (_cameraMode.value.followsSomething) {
            setCameraMode(CameraMode.FREE_EXPLORE)
        }
        onUserTookControl?.invoke()
    }

    /**
     * En suivi la caméra **ne se repose jamais**. Un anti-rebond seul
     * laisserait la fenêtre d'interrogation figée ; on émet donc au plus
     * tard toutes les secondes tant que ça bouge.
     */
    private fun maybeEmitRegionFromFollow() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRegionEmitElapsed >= REGION_EMIT_MAX_MS) {
            settleRegion()
        }
    }

    private fun settleRegion() {
        val map = map ?: return
        lastRegionEmitElapsed = SystemClock.elapsedRealtime()
        val center = map.cameraPosition.target ?: return
        val bounds = map.projection.visibleRegion.latLngBounds
        val radius = GeoMath.distance(
            Coordinate(center.latitude, center.longitude),
            Coordinate(bounds.latitudeNorth, bounds.longitudeEast),
        )
        onRegionSettled?.invoke(
            Coordinate(center.latitude, center.longitude),
            map.cameraPosition.zoom,
            radius,
        )
    }

    /**
     * Le hit-test, à la main.
     *
     * La tolérance est de 22 **dp**, convertie ici en pixels : `RectF` et
     * `queryRenderedFeatures` travaillent en pixels physiques, et confondre les
     * deux donne une zone quatre fois trop petite sur un écran dense.
     *
     * Les couches sont interrogées de la plus haute à la plus basse, et chacune
     * départage ses candidats par la distance au doigt.
     */
    private fun handleTap(map: MapLibreMap, latLng: LatLng): Boolean {
        val point = map.projection.toScreenLocation(latLng)
        val tolerance = HIT_TEST_TOLERANCE_DP * density
        val rect = RectF(
            point.x - tolerance,
            point.y - tolerance,
            point.x + tolerance,
            point.y + tolerance,
        )

        val action = registry.layers.asReversed()
            .filterIsInstance<MapInteractiveLayer>()
            .firstNotNullOfOrNull { it.hitTest(map, rect, PointF(point.x, point.y)) }

        if (action != null) {
            action()
        } else {
            onTapMap?.invoke(Coordinate(latLng.latitude, latLng.longitude))
        }
        return true
    }

    companion object {
        /**
         * L'inclinaison que demande le produit, reprise du proto iOS.
         *
         * MapLibre Android la refuse : `MapLibreConstants.MAXIMUM_PITCH` vaut
         * 60°, dans le cœur du moteur. On la demande quand même et on journalise
         * ce qu'on obtient, pour que l'écart soit une donnée mesurée et non une
         * supposition — et pour qu'une version future qui relèverait le plafond
         * en profite sans qu'on touche à ce code.
         */
        const val MAX_PITCH_REQUESTED = 60.0

        const val HIT_TEST_TOLERANCE_DP = 22f

        /**
         * La marge autour d'un tracé cadré, en dp.
         *
         * Elle vaut pour les quatre bords ; en bas, la hauteur du volet s'y
         * ajoute. Sans elle, le tracé viendrait mourir sur le bord de l'écran,
         * où l'on ne voit plus s'il continue.
         */
        const val FRAME_MARGIN_DP = 48f

        /**
         * La bande de carte qu'un cadrage garde toujours, en dp.
         *
         * Elle vaut pour le volet ouvert en grand : cadrer dans ce qui reste
         * — parfois rien — n'a pas de sens, et le moteur rendrait alors une
         * position aberrante plutôt qu'une erreur.
         */
        const val MIN_FRAME_BAND_DP = 160f

        /**
         * De combien le volet doit avoir bougé pour valoir un cadrage réécrit.
         *
         * Huit pixels physiques, soit moins de trois points sur le S21 : le
         * volet suit un doigt, donc il faut réécrire souvent, mais pas pour un
         * pixel d'arrondi entre deux images.
         */
        const val SHEET_STEP_PX = 8f

        /** Plafond d'émission de la région en suivi : la caméra ne se repose jamais. */
        const val REGION_EMIT_MAX_MS = 1_000L

        /** La couche de volumes des deux styles, celle dont on dose la présence. */
        const val BUILDINGS_3D_LAYER = "building-3d"

        /**
         * Les deux zooms entre lesquels les volumes entrent, repris du style.
         *
         * Ils y sont écrits une fois pour toutes ; les redire ici est le prix
         * à payer pour pouvoir réécrire l'expression sans la relire — MapLibre
         * ne rend pas une propriété de peinture sous forme d'expression.
         */
        const val BUILDINGS_FADE_FROM = 15.0f
        const val BUILDINGS_FADE_TO = 15.5f

        /**
         * La distance de la source lumineuse, reprise telle quelle des styles.
         *
         * Le nuanceur ne normalise pas le vecteur reçu : au-delà de un, la
         * saturation gagne les faces obliques et la ville durcit. Les deux
         * styles ont été réglés à 1,15 et il n'y a aucune raison que le soleil
         * change ça — lui ne décide que de la direction.
         */
        const val SUN_DISTANCE = 1.15f
    }
}
