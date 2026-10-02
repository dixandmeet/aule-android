package io.aule.android.core.map.layer

/**
 * Comment la couche des véhicules les peint.
 *
 * Le socle est partagé par deux applications qui n'ont pas la même carte : Aule Pro garde la
 * livrée d'origine, le Voyageur adopte la carrosserie neutre. Un paramètre de rendu — et non une
 * seconde couche — pour que tout le reste (glisse, sélection, volumes, toucher) reste **un seul
 * code** : deux couches de véhicules finiraient par ne plus se comporter pareil.
 */
enum class FleetLook {
    /**
     * **Le défaut, et l'écran d'avant au pixel près.** La carrosserie prend la teinte du mode
     * (`markerColor`), le véhicule choisi se dit par un anneau et un volume en couleur pleine.
     * Aule Pro et la SAE n'ont rien à poser : ils n'ont pas à connaître ce réglage.
     */
    CLASSIC,

    /**
     * La carrosserie est **neutre** — blanc cassé de jour, anthracite de nuit —, la couleur de la
     * ligne n'est plus qu'un **accent** (bande de toit, bas de caisse), et l'état — choisi, suivi —
     * se dit par un contour et un halo turquoise posé au sol. Les autres véhicules reculent.
     *
     * La spec commune aux trois plateformes est `voyageur/docs/vehicules-livree-neutre-accent.md` :
     * ses valeurs sont reprises telles quelles dans [VehicleLivery] et [VehicleHalo].
     */
    NEUTRAL_ACCENT,
}

/**
 * Le réglage de rendu de la flotte — jumeau de `FleetRendering` côté iOS.
 *
 * À poser **avant** le montage de la couche : ce sont ses couches de style qui en dépendent, et
 * elles ne se refont qu'au prochain chargement de style.
 */
data class FleetRendering(
    val look: FleetLook = FleetLook.CLASSIC,
) {
    /** Vrai pour la livrée neutre à accent. */
    val isNeutralAccent: Boolean get() = look == FleetLook.NEUTRAL_ACCENT

    companion object {
        /** Ce que le Voyageur active. */
        val VOYAGEUR = FleetRendering(look = FleetLook.NEUTRAL_ACCENT)
    }
}
