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

    /**
     * Lâche tout ce qui vient du style : sources, couches, images posées.
     *
     * ⚠️ **Ce n'est pas [unmount], et l'un ne remplace pas l'autre.** Celui-là
     * retire d'un style **vivant** ; celle-ci est appelée quand le style **a déjà
     * disparu** — rechargé sous la carte, ou emporté avec elle. Il n'y a alors
     * plus rien à retirer, et il reste tout à oublier.
     *
     * ⚠️ **Elle n'est pas facultative.** Une `GeoJsonSource` gardée après la mort
     * de son style reste un objet JVM parfaitement vivant : rien ne devient
     * `null`, aucun `?: return` ne se déclenche, et la couche continue d'écrire
     * dans une source qui n'est plus posée nulle part. Sur iOS, la même faute
     * lève `MLNInvalidStyleSourceException` — donc `SIGABRT` — dès la première
     * forme publiée. Ici, MapLibre marque ses objets `detached` au `Style.clear()`
     * et ignore l'écriture sans rien dire : le plantage n'a pas lieu, mais c'est
     * une garde du moteur, pas une garantie du contrat. Une couche doit savoir
     * elle-même qu'elle n'a plus de style.
     *
     * La donnée métier, elle, se garde : c'est [mount] qui la republie.
     */
    fun forgetStyle()

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

    fun register(layer: MapLayer) {
        if (_layers.any { it.id == layer.id }) return
        _layers += layer
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
     * Le style a disparu : rechargé sous la carte, ou emporté avec elle.
     *
     * Les objets natifs n'existent plus — tenter de les retirer échouerait, et il
     * ne reste qu'à les oublier avant de les reposer.
     *
     * ⚠️ **Oublier n'est pas un drapeau.** Cette méthode s'est appelée
     * `markAllUnmounted()` et ne faisait rien de plus que son nom : vider
     * `mounted`. Le registre se taisait alors sur ce qui compte — les couches
     * gardaient leurs sources — et les appelants qui écrivent **hors du registre**,
     * à leur propre rythme, continuaient de leur parler : le ticker caméra pousse
     * le puck toutes les 66 ms, la flotte arrive à chaque sondage. Voir
     * [MapLayer.forgetStyle].
     *
     * ⚠️ **Toutes les couches, pas seulement celles que `mounted` connaît.**
     * Oublier deux fois ne coûte rien ; sauter une couche que le registre croit
     * démontée à tort, c'est la laisser écrire dans le vide.
     */
    fun styleWasDiscarded() {
        for (layer in _layers) layer.forgetStyle()
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
