package io.aule.android.feature.map

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.aule.android.core.map.MapAmbiance
import io.aule.android.core.map.MapController
import org.maplibre.android.maps.MapView

/**
 * L'hôte Compose de la carte.
 *
 * Volontairement mince : il crée la `MapView`, relaie son cycle de vie, et passe
 * la main au [MapController]. Tout le reste — couches, caméra, gestes, icônes —
 * vit dans `:core:map`, qui ne connaît pas Compose. C'est ce qui permet au moteur
 * cartographique d'évoluer sans toucher à l'interface.
 *
 * La `MapView` est `remember`ée et **jamais recréée** : elle porte le contexte de
 * rendu, le pont JNI et la boucle de gestes. Le Flutter du dépôt a payé la leçon
 * inverse — un échange d'écran qui détruisait la carte à chaque fois.
 */
@Composable
fun AuleMap(
    controller: MapController,
    ambiance: MapAmbiance,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            // MapLibre rend un tampon opaque : TalkBack n'y trouve rien, et
            // la sélection passe par un hit-test de 22 dp. Le nœud
            // d'accessibilité vit donc sur le modificateur Compose, pas
            // sur la vue native.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            contentDescription = null
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    controller.onResume()
                }
                // L'ordre s'inverse à la descente : le contrôleur met son horloge
                // en veille avant que la vue ne se retire.
                Lifecycle.Event.ON_PAUSE -> {
                    controller.onPause()
                    mapView.onPause()
                }
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }

        // Un observateur posé alors que le cycle de vie est déjà démarré ne
        // rejoue pas les événements passés : sans ce rattrapage, `onStart` et
        // `onResume` ne seraient jamais appelés et la carte resterait noire.
        val state = lifecycleOwner.lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (state.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
            controller.onResume()
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.getMapAsync { map -> controller.attach(mapView, map, ambiance) }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // `detach` **avant** `onDestroy` : après, le `Style` est invalide et
            // toute écriture sur une source lève.
            controller.detach()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { controller.setAmbiance(ambiance) },
    )
}
