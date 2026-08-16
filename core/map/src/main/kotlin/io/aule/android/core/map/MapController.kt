package io.aule.android.core.map

import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.map.camera.CameraMode
import io.aule.android.core.map.camera.CameraTarget
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

    /** Hauteur masquée par le volet, en pixels. Entre dans le cadrage de la caméra. */
    var sheetHeightPx: Float = 0f

    var onTapMap: ((Coordinate) -> Unit)? = null
    var onUserTookControl: (() -> Unit)? = null
    var onRegionSettled: ((center: Coordinate, zoom: Double, radiusMeters: Double) -> Unit)? = null
    var onMapLoadFailure: ((String) -> Unit)? = null

    /** Vrai pendant nos propres animations, pour ne pas les prendre pour un geste. */
    private var suppressGestureDetection = false

    private var lastAppliedTarget: CameraTarget? = null
    private var isCameraCallInFlight = false
    private var lastRegionEmitElapsed = 0L

    // ------------------------------------------------------------------ montage

    fun attach(mapView: MapView, map: MapLibreMap, ambiance: MapAmbiance) {
        this.mapView = mapView
        this.map = map
        _ambiance.value = ambiance

        map.uiSettings.apply {
            isLogoEnabled = false
            isAttributionEnabled = false
            isCompassEnabled = false
            // Le HUD réaffiche l'attribution : c'est une obligation de licence,
            // pas une option. Masquer le bouton sans la réafficher serait une faute.
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
        // Avant `MapView.onDestroy` : après, le `Style` est invalide et toute
        // écriture sur une source lève.
        frameClock.stop()
        style?.let { registry.unmountAll(it) }
        registry.markAllUnmounted()
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
        // silence. On marque tout démonté avant, sinon `mountPending` croirait
        // n'avoir rien à faire et la carte se viderait sans erreur.
        registry.markAllUnmounted()
        _isStyleLoaded.value = false
        style = null

        map.setStyle(Style.Builder().fromUri(ambiance.assetPath)) { loaded ->
            onStyleLoaded(loaded, ambiance)
        }
    }

    /** L'ordre de ces quatre appels n'est pas commutatif. */
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

    // ------------------------------------------------------------------- caméra

    /**
     * Pose la caméra **après** création de la vue, jamais via une caméra initiale.
     *
     * À la construction, la vue n'a pas encore de taille et le cadrage calculé
     * sur elle est faux — un décalage qu'on ne voit qu'au premier lancement.
     */
    /**
     * Cadre un lieu qu'on vient de nommer — un arrêt cherché, une adresse.
     *
     * Distinct de [moveTo] : on **anime**, et on passe en exploration libre
     * pour que le suivi ne ramène pas la caméra sur le puck pendant le vol.
     */
    fun flyTo(center: Coordinate, zoom: Double = MapZoom.OPENING) {
        val map = map ?: return
        val view = mapView ?: return
        if (view.width == 0 || view.height == 0) {
            view.post { flyTo(center, zoom) }
            return
        }
        setCameraMode(CameraMode.FREE_EXPLORE)
        val position = cameraPosition(center, zoom, pitch = 0.0, bearing = 0.0, topPaddingPx = 0.0)
        suppressGestureDetection = true
        isCameraCallInFlight = true
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(position),
            AuleMotion.CAMERA_ENTRY_MS,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() = releaseCameraCall()
                override fun onCancel() = releaseCameraCall()
            },
        )
        lastAppliedTarget = null
    }

    /**
     * Cadre un tracé pour qu'il tienne dans la bande visible, volet compris.
     *
     * Une seule coordonnée non finie étirerait la boîte jusqu'à l'infini, et
     * le cadrage montrerait la planète entière — une panne spectaculaire pour
     * une donnée manquante.
     */
    fun frame(coordinates: List<Coordinate>) {
        val map = map ?: return
        val view = mapView ?: return
        if (view.width == 0 || view.height == 0) {
            view.post { frame(coordinates) }
            return
        }
        val usable = coordinates.filter { it.latitude.isFinite() && it.longitude.isFinite() }
        if (usable.isEmpty()) return
        if (usable.size < 2) {
            flyTo(usable.first())
            return
        }
        setCameraMode(CameraMode.FREE_EXPLORE)
        val builder = org.maplibre.android.geometry.LatLngBounds.Builder()
        usable.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
        val pad = (48f * density).toInt().coerceAtLeast(32)
        val bottom = pad + sheetHeightPx.toInt()
        suppressGestureDetection = true
        isCameraCallInFlight = true
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(builder.build(), pad, pad, pad, bottom),
            AuleMotion.CAMERA_ENTRY_MS,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() = releaseCameraCall()
                override fun onCancel() = releaseCameraCall()
            },
        )
        lastAppliedTarget = null
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
        if (!mode.followsSomething) {
            applyPadding(topPx = 0.0)
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
        if (!animated && last != null && !isSignificant(target, last)) return false

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
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(position),
                AuleMotion.CAMERA_ENTRY_MS,
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
            override fun onMoveBegin(detector: MoveGestureDetector) = handleUserGesture()
            override fun onMove(detector: MoveGestureDetector) = Unit
            override fun onMoveEnd(detector: MoveGestureDetector) = settleRegion()
        })
        map.addOnRotateListener(object : MapLibreMap.OnRotateListener {
            override fun onRotateBegin(detector: RotateGestureDetector) = handleUserGesture()
            override fun onRotate(detector: RotateGestureDetector) = Unit
            override fun onRotateEnd(detector: RotateGestureDetector) = Unit
        })
        map.addOnScaleListener(object : MapLibreMap.OnScaleListener {
            override fun onScaleBegin(detector: StandardScaleGestureDetector) = handleUserGesture()
            override fun onScale(detector: StandardScaleGestureDetector) = Unit
            override fun onScaleEnd(detector: StandardScaleGestureDetector) = settleRegion()
        })
        map.addOnShoveListener(object : MapLibreMap.OnShoveListener {
            override fun onShoveBegin(detector: ShoveGestureDetector) = handleUserGesture()
            override fun onShove(detector: ShoveGestureDetector) = Unit
            override fun onShoveEnd(detector: ShoveGestureDetector) = Unit
        })

        map.addOnMapClickListener { latLng -> handleTap(map, latLng) }
    }

    /**
     * Un doigt sur la carte rend la main à l'utilisateur — **sans rien annuler**.
     *
     * Regarder ailleurs ne vaut pas renoncement : le guidage tient, et
     * « Recentrer » reprend là où on en était.
     */
    private fun handleUserGesture() {
        if (suppressGestureDetection) return
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

        /** Plafond d'émission de la région en suivi : la caméra ne se repose jamais. */
        const val REGION_EMIT_MAX_MS = 1_000L
    }
}
