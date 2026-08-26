package io.aule.android.core.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath

/**
 * La boussole : vers où le **téléphone** regarde.
 *
 * Elle répond à une question que le GPS ne sait pas poser. Le cap d'un GPS
 * est un *course over ground* — il se déduit d'un déplacement, et à l'arrêt
 * il n'existe pas ([HeadingStabilizer] le gèle sous
 * [HEADING_MIN_SPEED_MPS]). Or c'est précisément à l'arrêt qu'on cherche sa
 * direction : on sort du métro, on lève les yeux, et la seule chose qu'on
 * veut savoir est de quel côté partir.
 *
 * [Sensor.TYPE_ROTATION_VECTOR] et non le magnétomètre nu : le vecteur de
 * rotation est déjà la fusion accéléromètre + gyroscope + magnétomètre que
 * le système entretient, et il ne dérive ni ne tremble comme un cap tiré du
 * champ magnétique seul. Ce qu'on ajoute par-dessus, c'est ce que le
 * système ne peut pas savoir : où est l'écran, et où est le nord **vrai**.
 *
 * ⚠️ **Hors flux observable.** Le cap arrive à la cadence du capteur —
 * plusieurs dizaines de fois par seconde. Il est lu par le ticker caméra,
 * jamais publié dans un `StateFlow` : le faire recomposerait l'arbre Compose
 * à chaque frémissement du poignet (ADR-006).
 */
class DeviceCompass(context: Context) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensors = appContext.getSystemService(SensorManager::class.java)
    private val rotationVector: Sensor? =
        sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    /**
     * ⚠️ **Écrit sur le thread capteur, lu sur le thread principal.** Sans
     * `@Volatile`, le ticker caméra peut lire indéfiniment une valeur mise en
     * cache dans un registre — un cône figé sur le premier cap mesuré, ce qui
     * est exactement le défaut qu'on vient corriger.
     */
    @Volatile
    private var azimuthDegrees: Double? = null

    @Volatile
    private var declinationDegrees: Float = 0f

    private var declinationReference: Coordinate? = null

    private var listening = false

    /** Vrai quand l'appareil a de quoi répondre. Un émulateur, souvent non. */
    val isAvailable: Boolean get() = rotationVector != null

    /**
     * Le dernier cap connu, en degrés depuis le nord **vrai**, dans `[0, 360[`.
     *
     * `null` tant qu'aucune mesure n'est arrivée, et de nouveau `null` dès que
     * le système déclare son capteur inexploitable — près d'une portière, d'un
     * aimant de coque, d'un tableau de bord. Un cap faux de quatre-vingts
     * degrés est pire qu'une absence de cap : il envoie marcher dans la
     * mauvaise rue avec l'assurance d'un instrument.
     */
    val heading: Double? get() = azimuthDegrees

    fun start() {
        val sensor = rotationVector ?: return
        if (listening) return
        listening = true
        // `SENSOR_DELAY_UI`, ~60 ms : le ticker caméra en consomme un toutes
        // les 66 ms et le cône est lissé par-dessus. Descendre à `GAME`
        // quadruplerait les réveils pour des valeurs que personne ne lit.
        sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!listening) return
        listening = false
        sensors?.unregisterListener(this)
        // Le cap ne survit pas à l'arrêt : au retour, il aurait vieilli du
        // temps passé ailleurs, et le premier échantillon arrive en quelques
        // dizaines de millisecondes.
        azimuthDegrees = null
    }

    /**
     * Donne à la boussole la position où elle mesure.
     *
     * Le vecteur de rotation vise le nord **magnétique** ; le cap d'un GPS,
     * lui, vise le nord **vrai**. Les mêler sans corriger, c'est faire tourner
     * le cône d'un degré et demi au moment précis où l'on se met à marcher, à
     * Nantes — et de vingt degrés au Canada, où l'app finira peut-être par ne
     * pas aller, mais où le défaut serait imputé à l'app et non au modèle
     * géomagnétique.
     *
     * Recalculée seulement quand on a franchi [DECLINATION_REFRESH_METERS] :
     * la déclinaison varie de l'ordre du degré par centaine de kilomètres, et
     * le modèle WMM n'est pas un calcul à faire une fois par seconde.
     */
    fun setReference(coordinate: Coordinate) {
        val previous = declinationReference
        if (previous != null &&
            GeoMath.distance(previous, coordinate) < DECLINATION_REFRESH_METERS
        ) {
            return
        }
        declinationReference = coordinate
        declinationDegrees = GeomagneticField(
            coordinate.latitude.toFloat(),
            coordinate.longitude.toFloat(),
            0f,
            System.currentTimeMillis(),
        ).declination
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val (axisX, axisY) = screenAxes()
        if (!SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)) return
        SensorManager.getOrientation(remapped, orientation)

        val magnetic = Math.toDegrees(orientation[0].toDouble())
        azimuthDegrees = GeoMath.normalizeHeading(magnetic + declinationDegrees)
    }

    /**
     * Le système ne prévient que d'un défaut de calibration.
     *
     * On ne rejette que l'inexploitable. `ACCURACY_LOW` est l'état ordinaire
     * d'un téléphone qu'on n'a jamais fait tourner en huit : le cap y est bon
     * à une trentaine de degrés, ce que l'ouverture du cône absorbe. Exiger
     * `HIGH` laisserait la plupart des utilisateurs sans direction du tout.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            azimuthDegrees = null
        }
    }

    /**
     * Les axes à remettre d'aplomb selon l'orientation de l'affichage.
     *
     * L'activité est verrouillée en portrait par le manifeste, donc ce calcul
     * rend aujourd'hui l'identité. Il est ici parce que le verrou vit dans
     * `:app` et non dans ce module : le jour où une tablette ou un mode
     * bureau le lève, un cap silencieusement pivoté de quatre-vingt-dix
     * degrés est un défaut que personne ne rattachera à cette ligne.
     */
    private fun screenAxes(): Pair<Int, Int> = when (currentRotation()) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }

    /**
     * `DisplayManager` plutôt que `Context.getDisplay` : ce dernier exige un
     * contexte visuel, et celui qu'on tient ici est celui de l'application.
     */
    private fun currentRotation(): Int {
        val displays = appContext.getSystemService(DisplayManager::class.java)
        return displays?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
    }

    private companion object {
        const val DECLINATION_REFRESH_METERS = 20_000.0
    }
}
