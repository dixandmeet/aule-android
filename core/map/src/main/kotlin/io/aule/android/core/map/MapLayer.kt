package io.aule.android.core.map

import android.graphics.PointF
import android.graphics.RectF
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Une couche de la carte : ce qu'on dessine par-dessus le fond.
 *
 * L'interface existe pour une raison précise : **un rechargement de style vide
 * toutes les sources et toutes les couches, en silence**. Sans un registre qui
 * sache tout remonter, passer du mode clair au mode sombre fait disparaître
 * arrêts, véhicules et puck sans la moindre erreur.
 */
interface MapLayer {

    /** Identifiant stable, pour le journal et pour éviter les doublons. */
    val id: String

    /**
     * Vrai si la couche a besoin d'être rappelée à chaque image.
     *
     * La boucle d'animation ne tourne que si **au moins une** couche le demande :
     * une carte immobile ne doit rien coûter.
     */
    val isAnimated: Boolean get() = false

    /**
     * Pose sources et couches sur un style fraîchement chargé, **et y republie sa
     * donnée**.
     *
     * Le second point n'est pas facultatif. La source qu'on vient de créer est
     * vide, et rien ne garantit qu'un autre chemin vienne la remplir : une couche
     * qui ne dessine qu'au changement de donnée reste invisible jusqu'au prochain
     * changement. Pour un puck à l'arrêt, « le prochain changement » veut dire
     * « plus jamais » — c'est exactement ainsi qu'il a disparu au premier passage
     * en mode sombre sur iOS.
     */
    fun mount(style: Style, map: MapLibreMap)

    /** Retire ce qu'on a posé. Appelé avant un changement de style volontaire. */
    fun unmount(style: Style)

    /** Une image d'animation. [elapsedSeconds] court depuis le démarrage de l'horloge. */
    fun onFrame(elapsedSeconds: Double) {}

    /**
     * L'ambiance a changé sans que le style soit rechargé — l'occasion de
     * repeindre les couleurs sans tout reconstruire.
     */
    fun onAmbianceChange(ambiance: MapAmbiance, style: Style) {}
}

/** Une couche qu'on peut toucher. */
interface MapInteractiveLayer : MapLayer {
    /**
     * L'action à exécuter si la couche a été touchée, `null` sinon.
     *
     * [rect] porte la tolérance tactile ; [point] est le doigt exact, pour
     * départager plusieurs candidats par la distance à l'écran plutôt que par
     * l'ordre de rendu — cet ordre n'a rien à voir avec ce que l'utilisateur
     * visait.
     */
    fun hitTest(map: MapLibreMap, rect: RectF, point: PointF): (() -> Unit)?
}

/**
 * Tient les couches et leur ordre.
 *
 * L'ordre d'enregistrement **est** l'ordre de superposition : la première
 * enregistrée est la plus basse. On ne s'en remet pas au hasard d'un dictionnaire.
 */
class MapLayerRegistry {

    private val _layers = mutableListOf<MapLayer>()
    val layers: List<MapLayer> get() = _layers

    private val mounted = mutableSetOf<String>()

    val hasAnimatedLayer: Boolean get() = _layers.any { it.isAnimated }

    /**
     * Ajoute une couche, ou **remplace** celle qui portait déjà cet id.
     *
     * Remplacer, et non ignorer. Le registre appartient au contrôleur, donc à
     * l'activité ; les couches, elles, naissent dans un `remember` de l'écran
     * carte. Une déconnexion sort cet écran de la composition et le ramène
     * ensuite : au retour, l'écran construit de **nouvelles** couches, et
     * c'est à elles que ses effets écrivent. Un registre qui garderait les
     * anciennes remonterait la donnée d'avant la déconnexion — arrêts, flotte,
     * puck — définitivement figée, et sans la moindre erreur.
     *
     * Le remplacement se fait **en place** : l'ordre d'enregistrement est
     * l'ordre de superposition, et une couche revenue par la fin de la liste
     * passerait au-dessus de toutes les autres — le puck sous le ruban
     * d'itinéraire, par exemple.
     */
    fun register(layer: MapLayer) {
        val existing = _layers.indexOfFirst { it.id == layer.id }
        if (existing < 0) {
            _layers += layer
            return
        }
        _layers[existing] = layer
        // La marque de montage désignait l'instance qu'on vient d'écarter :
        // sans cet oubli, `mountPending` sauterait la nouvelle et la couche
        // manquerait à la carte. Le chemin suppose un style déjà parti — c'est
        // le cas au retour de l'écran, où `detach` a tout démonté. Remplacer
        // une couche réellement posée laisserait ses objets natifs dans le
        // style, et la nouvelle buterait dessus en montant.
        mounted -= layer.id
    }

    fun layer(id: String): MapLayer? = _layers.firstOrNull { it.id == id }

    fun isMounted(id: String): Boolean = id in mounted

    /** Monte tout ce qui ne l'est pas encore, dans l'ordre d'enregistrement. */
    fun mountPending(style: Style, map: MapLibreMap) {
        for (layer in _layers) {
            if (layer.id in mounted) continue
            layer.mount(style, map)
            mounted += layer.id
        }
    }

    /**
     * Marque tout comme démonté **sans rien retirer**.
     *
     * C'est ce qu'il faut après un rechargement de style : les objets natifs ont
     * déjà disparu, tenter de les retirer échouerait, et il ne reste qu'à les
     * reposer.
     */
    fun markAllUnmounted() {
        mounted.clear()
    }

    fun unmountAll(style: Style) {
        for (layer in _layers.asReversed()) {
            if (layer.id in mounted) layer.unmount(style)
        }
        mounted.clear()
    }

    fun broadcastAmbiance(ambiance: MapAmbiance, style: Style) {
        for (layer in _layers) {
            if (layer.id in mounted) layer.onAmbianceChange(ambiance, style)
        }
    }

    fun broadcastFrame(elapsedSeconds: Double) {
        for (layer in _layers) {
            if (layer.isAnimated && layer.id in mounted) layer.onFrame(elapsedSeconds)
        }
    }
}
