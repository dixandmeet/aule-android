package io.aule.android

import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

/**
 * Le processus.
 *
 * Elle fait le strict minimum : construire le graphe et amorcer MapLibre. Tout ce
 * qui coûte plus que quelques millisecondes attend d'être demandé —
 * `Application.onCreate` est sur le chemin critique du démarrage à froid, et ce
 * qu'on y met retarde le premier pixel pour tout le monde.
 */
class AuleApplication : Application() {

    lateinit var graph: AuleGraph
        private set

    override fun onCreate() {
        super.onCreate()
                graph = AuleGraph.create(this)

        // MapLibre doit être amorcé avant toute création de `MapView`.
        MapLibre.getInstance(this)

        // Et il reçoit **notre** client : tuiles, glyphes et API partagent alors
        // un seul pool de connexions, un seul délai d'attente et un seul point de
        // journalisation. Sur un réseau mobile en mouvement, ça évite une poignée
        // de main TLS dupliquée par domaine.
        HttpRequestUtil.setOkHttpClient(graph.okHttp)
    }
}
