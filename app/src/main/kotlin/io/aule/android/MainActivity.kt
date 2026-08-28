package io.aule.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.aule.android.core.map.MapController

/**
 * L'unique activité.
 *
 * Une seule, et qui le restera : la carte est le socle permanent de
 * l'application et tout se posera par-dessus. Le Flutter du dépôt a payé la leçon
 * inverse — un échange d'écran qui détruisait la carte à chaque fois.
 *
 * L'auth se pose **avant** la carte : sans session, MapLibre n'est pas monté.
 *
 * ## Pourquoi `FragmentActivity` et non `ComponentActivity`
 *
 * `BiometricPrompt` l'exige comme hôte : il s'accroche au gestionnaire de
 * fragments pour survivre à une rotation pendant que son dialogue est ouvert.
 * `FragmentActivity` **hérite** de `ComponentActivity` — `setContent`,
 * `enableEdgeToEdge` et `onNewIntent` continuent donc de fonctionner tels
 * quels, et rien d'autre ici ne change.
 *
 * Aucun fragment n'est créé pour autant, et il ne faut pas en déduire une
 * ouverture : l'application reste une activité unique et du Compose (ADR-001).
 * C'est une contrainte d'hébergement d'un dialogue système, pas un retour au
 * modèle des fragments.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Avant super.onCreate : la fenêtre doit être en bord-à-bord dès la
        // première image, sinon les barres système sautent au premier rendu.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val graph = (application as AuleApplication).graph
        graph.offerAuthCallback(intent.data)

        // Le contrôleur survit aux recompositions mais pas à l'activité : il
        // tient la `MapView`, qui est une ressource de fenêtre.
        val controller = MapController(
            logger = graph.logger,
            density = resources.displayMetrics.density,
        )

        setContent {
            AuleRoot(graph = graph, mapController = controller)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        (application as AuleApplication).graph.offerAuthCallback(intent.data)
    }
}
