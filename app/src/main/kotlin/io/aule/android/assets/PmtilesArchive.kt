package io.aule.android.assets

import android.content.Context
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import java.io.File
import java.io.IOException

/**
 * Une archive PMTiles embarquée, recopiée là où le lecteur natif sait la lire.
 *
 * Deux archives suivent ce chemin : les tracés du réseau (3,4 Mo) et le référentiel
 * de voirie de Nantes Métropole (2,1 Mo). Le mécanisme est le même au mot près —
 * d'où une classe paramétrée plutôt qu'une seconde copie de ces soixante lignes,
 * qui auraient divergé au premier correctif.
 *
 * ## Pourquoi une copie
 *
 * Le lecteur natif veut une URL `file://`. Les assets d'un APK ne sont pas des
 * fichiers : ils vivent compressés dans le paquet, sans chemin que le système
 * puisse ouvrir. La copie est donc le prix d'entrée — 3,4 Mo, une fois.
 *
 * ## Une fois, et pas à chaque lancement
 *
 * La taille sert de témoin : si le fichier recopié fait déjà la taille de
 * l'asset, il est le bon. C'est plus faible qu'une empreinte et bien moins cher
 * — lire 3,4 Mo pour en calculer le SHA à chaque démarrage coûterait plus que la
 * copie qu'on cherche à éviter. Une mise à jour de l'app change la taille de
 * l'archive quand son contenu change ; à taille égale et contenu différent, on
 * repeindrait l'ancien réseau, ce qui reste un réseau juste à un build près.
 *
 * ## Ce qui se passe en cas d'échec
 *
 * `null`, et **la carte se peint quand même** : les tracés sont un calque qu'on
 * demande, pas le fond. Lever ici empêcherait de démarrer pour une couche
 * facultative.
 */
class PmtilesArchive(
    private val context: Context,
    private val logger: AuleLogger,
    /** Le chemin de l'archive dans les assets — p. ex. `tiles/transit.pmtiles`. */
    private val assetPath: String,
    /** Le nom du fichier recopié dans `filesDir`. */
    private val cachedFileName: String,
    /** Ce que le journal appelle cette archive, à l'ablatif : « des tracés », « de voirie ». */
    private val label: String,
) {

    /**
     * Le fichier prêt à l'emploi, ou `null` si la copie a échoué.
     *
     * À appeler hors du fil principal : c'est une entrée-sortie disque.
     */
    fun ensureExtracted(): File? {
        val assets = context.applicationContext.assets
        val target = File(context.applicationContext.filesDir, cachedFileName)

        // `openFd` ne répond que sur un asset **non compressé** : c'est pour
        // cela que `noCompress += "pmtiles"` est posé dans `build.gradle.kts`.
        // S'il refusait quand même, on retomberait sur « le fichier existe » —
        // moins sûr, mais toujours mieux que recopier 3,4 Mo à chaque lancement.
        val expected = try {
            assets.openFd(assetPath).use { it.length }
        } catch (_: IOException) {
            logger.warn(
                LogDomain.MAP,
                "Archive $label compressée dans l'APK : la fraîcheur de la copie " +
                    "ne peut plus se vérifier par la taille.",
            )
            null
        }

        val fresh = if (expected != null) target.length() == expected else target.length() > 0
        if (target.isFile && fresh) return target

        return try {
            val temporary = File(target.parentFile, cachedFileName + ".part")
            assets.open(assetPath).use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            // Renommage atomique : une copie interrompue laisse un `.part`, pas
            // une archive tronquée que le lecteur prendrait pour valide.
            if (target.exists() && !target.delete()) {
                logger.warn(LogDomain.MAP, "Ancienne archive $label impossible à retirer.")
            }
            if (!temporary.renameTo(target)) {
                logger.warn(LogDomain.MAP, "Archive $label impossible à installer.")
                temporary.delete()
                return null
            }
            logger.info(LogDomain.MAP, "Archive $label prête (${target.length()} octets).")
            target
        } catch (failure: IOException) {
            logger.warn(LogDomain.MAP, "Archive $label illisible.", failure)
            null
        }
    }
}
