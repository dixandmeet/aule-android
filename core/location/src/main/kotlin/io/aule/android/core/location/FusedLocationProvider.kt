package io.aule.android.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.geo.Coordinate
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [LocationProvider] sur `FusedLocationProviderClient`.
 *
 * La fusion capteurs (GPS + réseau + IMU) et les profils de priorité sont
 * exactement ce qu'on veut, et le S21 a les Play Services. L'interface
 * garde une implémentation `LocationManager` possible sans toucher un
 * appelant.
 *
 * Le dialogue d'autorisation n'est **pas** ici : il a besoin d'une Activity.
 * L'écran le pose, puis rappelle [refreshAuthorization] et [start].
 */
class FusedLocationProvider(
    context: Context,
    private val logger: AuleLogger,
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context),
) : LocationProvider {

    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val headingStabilizer = HeadingStabilizer()
    private val motionAnchor = MotionAnchor()

    /**
     * La boussole suit le flux : elle démarre et s'arrête avec lui.
     *
     * Elle n'a besoin d'aucune autorisation, mais elle n'a de sens que quand
     * une carte regarde — un capteur qui tourne pour personne est une part de
     * batterie prise à quelqu'un qui consulte un horaire.
     */
    private val compass = DeviceCompass(context)

    private val _authorization = MutableStateFlow(readAuthorization())
    override val authorization: StateFlow<LocationAuthorization> = _authorization.asStateFlow()

    private val _lastFix = MutableStateFlow<LocationFix?>(null)
    override val lastFix: StateFlow<LocationFix?> = _lastFix.asStateFlow()

    private val _isAcquiring = MutableStateFlow(false)
    override val isAcquiring: StateFlow<Boolean> = _isAcquiring.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override val deviceHeadingDegrees: Double? get() = compass.heading

    private var purpose: LocationPurpose = LocationPurpose.READY
    private var isUpdating = false
    private var foregroundActive = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            ingest(location, maxAgeMs = LIVE_MAX_AGE_MS)
        }
    }

    init {
        logger.info(LogDomain.GPS, "Autorisation : ${_authorization.value}")
    }

    override fun start(purpose: LocationPurpose) {
        this.purpose = purpose
        refreshAuthorization()
        startIfNeeded()
        syncForegroundService()
    }

    override fun stop() {
        stopForegroundService()
        if (!isUpdating) return
        isUpdating = false
        _isAcquiring.value = false
        compass.stop()
        client.removeLocationUpdates(callback)
        logger.info(LogDomain.GPS, "Flux arrêté.")
    }

    override fun setPurpose(purpose: LocationPurpose) {
        if (purpose == this.purpose) return
        this.purpose = purpose
        logger.info(LogDomain.GPS, "Profil de localisation : $purpose")
        if (isUpdating) {
            // Reconstruire la requête : la priorité et l'intervalle vivent
            // dedans, et `requestLocationUpdates` remplace la précédente pour
            // le même callback.
            requestUpdates()
        }
        syncForegroundService()
    }

    override fun refreshAuthorization() {
        val resolved = readAuthorization()
        if (resolved != _authorization.value) {
            logger.info(LogDomain.GPS, "Autorisation : $resolved")
        }
        _authorization.value = resolved
        if (resolved.allowsUpdates) {
            startIfNeeded()
            syncForegroundService()
        } else {
            stop()
        }
    }

    override fun markPermissionRequested() {
        prefs.edit { putBoolean(KEY_HAS_REQUESTED, true) }
        refreshAuthorization()
    }

    override fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", appContext.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    // ------------------------------------------------------------------ interne

    private fun startIfNeeded() {
        if (isUpdating) return
        if (!_authorization.value.allowsUpdates) return
        isUpdating = true
        _isAcquiring.value = _lastFix.value == null
        compass.start()
        requestUpdates()
        requestLastKnown()
        logger.info(LogDomain.GPS, "Flux démarré (${purpose.name.lowercase()}).")
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        if (!hasLocationPermission()) {
            stop()
            return
        }
        try {
            client.requestLocationUpdates(
                locationRequest(purpose),
                callback,
                Looper.getMainLooper(),
            )
        } catch (failure: SecurityException) {
            logger.warn(LogDomain.GPS, "Permission révoquée pendant le flux.", failure)
            stop()
            refreshAuthorization()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLastKnown() {
        if (!hasLocationPermission()) return
        client.lastLocation.addOnSuccessListener { location ->
            location?.let { ingest(it, maxAgeMs = LAST_KNOWN_MAX_AGE_MS) }
        }
    }

    private fun ingest(location: Location, maxAgeMs: Long) {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
        if (ageMs < 0 || ageMs > maxAgeMs) return

        val coordinate = Coordinate(location.latitude, location.longitude)
        if (!coordinate.isValid) return

        val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.POSITIVE_INFINITY
        if (accuracy <= 0.0) return

        val speed = if (location.hasSpeed()) max(location.speed.toDouble(), 0.0) else 0.0
        val course = if (location.hasBearing() && location.bearing >= 0f) {
            location.bearing.toDouble()
        } else {
            null
        }

        headingStabilizer.ingest(course, speed)
        val settled = motionAnchor.settle(coordinate, speed, accuracy)
        // La position brute, pas l'ancrée : la déclinaison ne se joue pas à
        // douze mètres, et l'ancre peut tenir sur un point vieux d'une minute.
        compass.setReference(coordinate)

        _lastError.value = null
        _isAcquiring.value = false
        _lastFix.value = LocationFix(
            coordinate = settled,
            accuracyMeters = accuracy,
            courseDegrees = course,
            speedMetersPerSecond = speed,
            timestampMillis = location.time,
            stabilizedHeading = headingStabilizer.stabilized,
            isHeadingFrozen = headingStabilizer.isFrozen,
            isMocked = location.isMockedFix(),
        )
    }

    private fun readAuthorization(): LocationAuthorization {
        if (!isLocationEnabled()) return LocationAuthorization.SERVICES_DISABLED

        val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        return when {
            fine -> LocationAuthorization.GRANTED
            coarse -> LocationAuthorization.REDUCED_ACCURACY
            !prefs.getBoolean(KEY_HAS_REQUESTED, false) -> LocationAuthorization.UNKNOWN
            else -> LocationAuthorization.DENIED
        }
    }

    private fun isLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun hasLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Démarre ou arrête le service de premier plan selon le palier.
     *
     * Un échec (refus de notification, démarrage hors premier plan) n'est
     * **pas** fatal : le flux continue au premier plan, et le journal le dit.
     * Couper le guidage pour une notification manquante serait pire.
     */
    private fun syncForegroundService() {
        val wants = purpose.allowsBackground && isUpdating && _authorization.value.allowsUpdates
        if (wants) {
            startForegroundService()
        } else {
            stopForegroundService()
        }
    }

    private fun startForegroundService() {
        try {
            NavigatingForegroundService.start(
                appContext,
                onDuty = purpose == LocationPurpose.ON_DUTY,
            )
            foregroundActive = true
            logger.info(LogDomain.GPS, "Service de premier plan démarré.")
        } catch (failure: Throwable) {
            foregroundActive = false
            logger.warn(LogDomain.GPS, "Service de premier plan impossible.", failure)
        }
    }

    private fun stopForegroundService() {
        if (!foregroundActive) {
            // Arrêter quand même : un processus tué puis relancé peut laisser
            // le service orphelin avec notre drapeau à false.
            runCatching { NavigatingForegroundService.stop(appContext) }
            return
        }
        runCatching { NavigatingForegroundService.stop(appContext) }
        foregroundActive = false
        logger.info(LogDomain.GPS, "Service de premier plan arrêté.")
    }

    @Suppress("DEPRECATION")
    private fun Location.isMockedFix(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider

    private companion object {
        const val PREFS = "io.aule.android.location"
        const val KEY_HAS_REQUESTED = "has_requested"

        /** Une position live plus vieille que ça n'est plus une position. */
        const val LIVE_MAX_AGE_MS = 30_000L

        /** Le dernier connu a le droit d'être un peu plus ancien : il sert à poser le puck tout de suite. */
        const val LAST_KNOWN_MAX_AGE_MS = 120_000L

        fun locationRequest(purpose: LocationPurpose): LocationRequest {
            val priority = when (purpose) {
                LocationPurpose.GLANCE -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
                LocationPurpose.READY, LocationPurpose.ON_DUTY, LocationPurpose.NAVIGATING ->
                    Priority.PRIORITY_HIGH_ACCURACY
            }
            return LocationRequest.Builder(priority, purpose.intervalMillis)
                .setMinUpdateIntervalMillis(purpose.intervalMillis / 2)
                // Zéro, pas une omission : un filtre de distance rendrait le
                // flux muet à l'arrêt, et on ne saurait plus si l'usager
                // attend un bus ou si le signal est perdu.
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()
        }
    }
}
