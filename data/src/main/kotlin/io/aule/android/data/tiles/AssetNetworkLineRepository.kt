package io.aule.android.data.tiles

import io.aule.android.core.model.TransitLine
import io.aule.android.core.model.canonicalLineName
import io.aule.android.core.model.decodeTransitLineIndex
import io.aule.android.core.model.repository.AssetBytes
import io.aule.android.core.model.repository.NetworkLineRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * L'inventaire des lignes, lu dans les assets.
 *
 * ## Lu une fois, à la première question posée
 *
 * 23 Ko et 138 lignes, soit une lecture qui ne se voit pas. La faire au
 * lancement coûterait la même chose à un écran qui n'affiche peut-être aucune
 * pastille de ligne.
 *
 * ## L'index par indice, bâti une fois
 *
 * Une couleur de badge se demande à chaque véhicule peint, soit plusieurs
 * centaines de fois par instantané de flotte : un balayage du tableau à chaque
 * appel se paierait à l'image.
 *
 * Deux entrées pour un même indice ne devraient pas exister ; si l'index en
 * portait, **garder la première** évite qu'une couleur change d'un build à
 * l'autre au gré de l'ordre du fichier.
 *
 * Port de `Native/Aule/Core/Map/TransitLineIndex.swift`.
 */
class AssetNetworkLineRepository(
    private val assets: AssetBytes,
    private val path: String = TRANSIT_LINES_INDEX_ASSET,
) : NetworkLineRepository {

    private val mutex = Mutex()

    @Volatile private var catalogue: List<TransitLine>? = null
    @Volatile private var byName: Map<String, TransitLine> = emptyMap()

    override suspend fun allLines(): List<TransitLine> = loaded()

    override suspend fun line(named: String): TransitLine? {
        loaded()
        return byName[canonicalLineName(named)]
    }

    private suspend fun loaded(): List<TransitLine> {
        catalogue?.let { return it }
        return mutex.withLock {
            catalogue?.let { return@withLock it }
            // Un asset absent rend une liste vide plutôt que de lever : sans
            // index les badges restent gris et lisibles. C'est le test qui tient
            // la présence du fichier, pas l'exécution.
            val lines = decodeTransitLineIndex(assets.readText(path))
            // `putIfAbsent` et non `associateBy` : ce dernier garde la **dernière**
            // occurrence d'une clé en double, quand on veut la première.
            byName = buildMap { lines.forEach { putIfAbsent(it.match, it) } }
            catalogue = lines
            lines
        }
    }
}

/** Le chemin de l'asset — le nom de la source, sans le renommer au passage. */
const val TRANSIT_LINES_INDEX_ASSET = "tiles/transit-lines-index.json"
