package io.aule.android.core.map.layer

import io.aule.android.core.map.MapLayer
import io.aule.android.core.map3d.VehicleScene
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Les véhicules en volume, dessinés par la couche native.
 *
 * Elle ne porte aucune donnée : [VehiclesLayer] reste le seul propriétaire des
 * positions, et publie les poses dans la scène à chaque image. Deux calculs
 * séparés laisseraient le modèle dériver de son ombre au sol.
 *
 * ⚠️ **L'hôte natif ne survit pas à son style.** `CustomLayer` adopte le
 * pointeur qu'on lui donne et le détruit avec elle ; un rechargement d'ambiance
 * emporte donc les deux. Réutiliser le pointeur au montage suivant écrirait dans
 * de la mémoire libérée — la version native du piège que [MapLayer.forgetStyle]
 * décrit pour les sources, en moins clément : ici il n'y a pas de garde du
 * moteur, juste un `SIGSEGV`. D'où un hôte **neuf à chaque montage**.
 *
 * La scène, elle, survit : les maillages téléversés et les poses en vol n'ont
 * aucune raison d'être relus à chaque bascule clair/sombre.
 */
class VehicleModelLayer(private val scene: VehicleScene) : MapLayer {

    override val id: String = ID

    private var mounted = false

    override fun mount(style: Style, map: MapLibreMap) {
        style.addLayer(scene.layer(LAYER))
        mounted = true
    }

    override fun unmount(style: Style) {
        if (!mounted) return
        style.removeLayer(LAYER)
        mounted = false
    }

    override fun forgetStyle() {
        // Rien à libérer : MapLibre a emporté la couche et l'hôte avec le style.
        mounted = false
    }

    private companion object {
        const val ID = "aule.vehicles.models"
        const val LAYER = "aule-vehicle-models"
    }
}
