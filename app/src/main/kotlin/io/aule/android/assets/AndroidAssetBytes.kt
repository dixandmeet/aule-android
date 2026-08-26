package io.aule.android.assets

import android.content.Context
import io.aule.android.core.model.repository.AssetBytes
import java.io.IOException

/**
 * Les assets de l'APK, derrière le contrat que `:data` sait lire.
 *
 * Toute la classe tient en une ligne utile, et c'est le but : c'est le seul
 * endroit du lot qui ait besoin d'un `Context`, et l'isoler ici laisse le décodage
 * de l'index vérifiable sans émulateur.
 */
class AndroidAssetBytes(
    context: Context,
) : AssetBytes {

    private val assets = context.applicationContext.assets

    /**
     * `null` quand l'asset manque. Un fichier absent du paquet est une erreur de
     * build, pas une panne d'exécution : on ne peut rien y faire à chaud, et
     * lever ici ne ferait que déplacer le plantage loin de sa cause.
     */
    override fun readText(path: String): String? = try {
        assets.open(path).use { it.readBytes().decodeToString() }
    } catch (_: IOException) {
        null
    }
}
