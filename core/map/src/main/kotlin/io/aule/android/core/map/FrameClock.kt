package io.aule.android.core.map

import android.view.Choreographer

/**
 * L'horloge d'animation, cadencée sur l'écran.
 *
 * Sur le S21 elle bat à 120 Hz. Elle sert à faire glisser les véhicules entre
 * deux sondages du serveur, qui parle toutes les quinze secondes.
 *
 * **Elle tourne sur le thread principal**, comme la recomposition Compose — c'est
 * imposé : `GeoJsonSource.setGeoJson` est annoté `@UiThread` et lève si on
 * l'appelle d'ailleurs. L'isolement contre les recompositions ne vient donc pas
 * d'un thread mais de l'**état** : la boucle n'écrit aucun `State` que Compose
 * lise, donc Compose ne se réveille pas. Ce qui part réellement sur un autre
 * dispatcher, c'est la construction de l'instantané de flotte, pas son affichage.
 */
internal class FrameClock(private val onFrame: (elapsedSeconds: Double) -> Unit) {

    private val choreographer: Choreographer = Choreographer.getInstance()
    private var startNanos = 0L
    private var running = false
    private var muted = false

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (!muted) onFrame((frameTimeNanos - startNanos) / 1_000_000_000.0)
            choreographer.postFrameCallback(this)
        }
    }

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        running = true
        startNanos = System.nanoTime()
        choreographer.postFrameCallback(callback)
    }

    fun stop() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(callback)
    }

    /**
     * Met en veille **sans réinitialiser l'horloge**.
     *
     * La différence compte : l'horloge situe les véhicules entre deux sondages.
     * La remettre à zéro au retour au premier plan les ferait tous sauter en
     * arrière, d'un coup.
     */
    fun setMuted(value: Boolean) {
        muted = value
    }
}
