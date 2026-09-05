package io.aule.android.data.tiles

import io.aule.android.core.model.SpeedLimitTable
import io.aule.android.core.model.decodeSpeedLimits
import io.aule.android.core.model.repository.AssetBytes

/** Le chemin de l'asset, copié de `dashboard/public/tiles/`. */
const val LINE_SPEED_LIMITS_ASSET = "tiles/line-speed-limits.json"

/**
 * Les limitations de vitesse, lues dans les assets.
 *
 * ## Pourquoi embarquées, et non servies
 *
 * Même raison que l'inventaire des lignes, en plus impérieuse : un panneau
 * réglementaire qui s'éteindrait dans un tunnel, sous un pont ou dans une zone
 * blanche n'aurait aucune valeur — c'est exactement là qu'un aller-retour réseau
 * échoue, et exactement là qu'on roule. Un kilo-octet dans l'APK vaut mieux
 * qu'une requête qui peut ne pas aboutir.
 *
 * C'est une **copie** de `dashboard/public/tiles/line-speed-limits.json`,
 * produite par `tool/build_line_speed_limits.py`.
 *
 * ## Un asset absent n'est pas une panne
 *
 * Rien à afficher, et c'est un état normal : le fichier ne couvre que les
 * tronçons qu'une note de service décrit. Une application sans asset et un réseau
 * sans note se ressemblent à l'écran, et c'est délibéré — dans les deux cas le
 * panneau reste éteint plutôt que d'inventer.
 */
fun loadSpeedLimits(
    assets: AssetBytes,
    path: String = LINE_SPEED_LIMITS_ASSET,
): SpeedLimitTable {
    val text = assets.readText(path) ?: return SpeedLimitTable.EMPTY
    return decodeSpeedLimits(text)
}
