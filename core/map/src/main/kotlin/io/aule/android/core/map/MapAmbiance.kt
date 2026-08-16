package io.aule.android.core.map

/**
 * L'ambiance de la carte.
 *
 * Les deux styles sont **embarqués** dans les assets et jamais téléchargés : la
 * carte doit se peindre même sans réseau, et un style servi par un serveur plus
 * ancien repeindrait la carte conducteur avec les mauvaises couleurs. Ils sont
 * générés par `dashboard/lib/carte-immersive/style/build-style.ts` et copiés tels
 * quels depuis le proto iOS — on ne les retouche pas à la main.
 */
enum class MapAmbiance(val assetPath: String) {
    LIGHT("asset://map/style-light.json"),
    DARK("asset://map/style-dark.json");

    companion object {
        fun of(night: Boolean): MapAmbiance = if (night) DARK else LIGHT
    }
}

object MapStyleAnchors {
    /**
     * Les tracés s'insèrent **sous** les étiquettes : une ligne de bus peinte
     * par-dessus le nom des rues rend la carte illisible là où elle doit l'être
     * le plus.
     */
    const val BELOW_LABELS = "label-water"
}

/**
 * L'attribution, obligatoire.
 *
 * Le bouton natif de MapLibre est masqué pour que le HUD reste maître de sa mise
 * en page ; l'attribution est donc réaffichée par le HUD, et ce n'est pas
 * facultatif — c'est la condition d'usage des données.
 */
const val MAP_ATTRIBUTION = "© OpenStreetMap · OpenFreeMap · OpenMapTiles · Nantes Métropole"

/** Les seuils de zoom d'apparition, repris du proto iOS. */
object MapZoom {
    /** En dessous, les arrêts encombrent plus qu'ils n'informent. */
    const val STOPS_FROM = 13.0

    /** Les quais n'apparaissent qu'une fois qu'on est à l'échelle du trottoir. */
    const val QUAYS_FROM = 17.5

    const val VEHICLES_FROM = 12.0
    const val VEHICLE_ICONS_FROM = 14.0

    /** L'ouverture, juste sous le seuil des quais. */
    const val OPENING = 17.0
    const val OPENING_PITCH = 59.0
}
