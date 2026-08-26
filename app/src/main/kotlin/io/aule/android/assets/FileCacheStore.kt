package io.aule.android.assets

import android.content.Context
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import java.io.File
import java.io.IOException

/**
 * Le cache disque, dans `cacheDir`.
 *
 * `cacheDir` et non `filesDir` : le système peut le vider quand la place manque,
 * ce qui est exactement le bon endroit pour une donnée retéléchargeable. Un
 * catalogue effacé se redemande au lancement suivant ; un catalogue qui
 * empêcherait une mise à jour de s'installer serait un défaut.
 *
 * **Rien ne lève.** Un cache est une optimisation, et une optimisation qui fait
 * planter est un défaut net : un disque plein rend `null` et l'appelant repart
 * sur le réseau.
 */
class FileCacheStore(
    context: Context,
    private val logger: AuleLogger,
) : io.aule.android.core.model.repository.CacheStore {

    private val directory = File(context.applicationContext.cacheDir, DIRECTORY)

    override fun read(name: String): String? = try {
        val file = File(directory, name)
        if (file.isFile) file.readText() else null
    } catch (failure: IOException) {
        logger.warn(LogDomain.NET, "Cache « $name » illisible.", failure)
        null
    }

    override fun write(name: String, content: String) {
        try {
            directory.mkdirs()
            // Écriture puis renommage : une écriture interrompue laisse un
            // `.part`, pas un fichier tronqué que la relecture prendrait pour un
            // catalogue valide — et un catalogue à moitié lu est un écran de
            // carte à moitié vide, sans erreur pour le dire.
            val temporary = File(directory, "$name.part")
            temporary.writeText(content)
            val target = File(directory, name)
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.delete()
                logger.warn(LogDomain.NET, "Cache « $name » impossible à installer.")
            }
        } catch (failure: IOException) {
            logger.warn(LogDomain.NET, "Cache « $name » impossible à écrire.", failure)
        }
    }

    override fun clear(name: String) {
        runCatching { File(directory, name).delete() }
    }

    private companion object {
        const val DIRECTORY = "aule-data"
    }
}
