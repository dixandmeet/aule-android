package io.aule.android.data.tiles

import io.aule.android.core.model.TransitLine
import io.aule.android.core.model.TransitLineLookup
import io.aule.android.core.model.TransportMode
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
 * ## L'index par clé, bâti une fois
 *
 * Une couleur de badge se demande à chaque véhicule peint, soit plusieurs
 * centaines de fois par instantané de flotte : un balayage du tableau à chaque
 * appel se paierait à l'image.
 *
 * ⚠️ **Par clé `réseau:MATCH`, plus par indice.** Deux entrées pour un même
 * indice existent désormais : C2, C4, C6 et C7 sont des Chronobus **et** des
 * TER, et l'index par nom rendait l'un ou l'autre selon l'ordre du fichier. La
 * résolution — `route_id`, clé qualifiée, puis numéro nu Naolib d'abord — vit
 * dans [TransitLineLookup], partagée avec le décorateur
 * [io.aule.android.data.caching.CachedNetworkLineRepository].
 *
 * Port de `Native/Aule/Core/Map/TransitLineIndex.swift`.
 */
class AssetNetworkLineRepository(
    private val assets: AssetBytes,
    private val path: String = TRANSIT_LINES_INDEX_ASSET,
) : NetworkLineRepository {

    private val mutex = Mutex()

    @Volatile private var catalogue: List<TransitLine>? = null

    /**
     * L'index de résolution : clés, numéros nus et identifiants GTFS.
     *
     * ⚠️ **Les `route_id` sont le seul chemin depuis une position théorique.** Elle porte le
     * `route_id` brut — `ALEOP:309` —, que rien ne rattache à « E309 » par une règle
     * d'écriture : il faut la table. Voir [TransitLine.routeIds].
     */
    @Volatile private var lookup: TransitLineLookup = TransitLineLookup.EMPTY

    override suspend fun allLines(): List<TransitLine> = loaded()

    override suspend fun line(named: String): TransitLine? = line(named, mode = null)

    override suspend fun line(named: String, mode: TransportMode?): TransitLine? {
        loaded()
        return lookup.resolve(named, mode)
    }

    private suspend fun loaded(): List<TransitLine> {
        catalogue?.let { return it }
        return mutex.withLock {
            catalogue?.let { return@withLock it }
            // Un asset absent rend une liste vide plutôt que de lever : sans
            // index les badges restent gris et lisibles. C'est le test qui tient
            // la présence du fichier, pas l'exécution.
            val lines = decodeTransitLineIndex(assets.readText(path))
            // L'index avant le catalogue : un lecteur qui voit le catalogue posé doit
            // trouver l'index qui va avec.
            lookup = TransitLineLookup(lines)
            catalogue = lines
            lines
        }
    }
}

/** Le chemin de l'asset — le nom de la source, sans le renommer au passage. */
const val TRANSIT_LINES_INDEX_ASSET = "tiles/transit-lines-index.json"
