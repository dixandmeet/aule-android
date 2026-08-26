package io.aule.android.data.caching

import io.aule.android.core.model.TransitLine
import io.aule.android.core.model.canonicalLineName
import io.aule.android.core.model.repository.NetworkLineRepository

/**
 * L'inventaire des lignes, gardé en mémoire pour la durée du processus.
 *
 * ## Pourquoi il n'y a **pas** de cache disque ici
 *
 * Le pendant iOS s'appelle `CachedNetworkLineRepository` et met en cache une
 * réponse réseau. Android n'en a pas besoin : son inventaire vient déjà d'un
 * asset embarqué, donc d'un fichier local que rien ne peut rendre indisponible.
 * Le recopier dans `cacheDir` reviendrait à cacher un fichier avec un fichier.
 *
 * Ce décorateur ne fait donc qu'une chose — éviter de relire et redécoder 23 Ko
 * à chaque question — et il le dit dans son nom plutôt que de faire semblant
 * d'être son homonyme iOS.
 *
 * ⚠️ [AssetNetworkLineRepository][io.aule.android.data.tiles.AssetNetworkLineRepository]
 * garde **déjà** son catalogue. Ce décorateur n'a d'intérêt que devant une
 * implémentation qui, elle, irait chercher au loin — le jour où l'inventaire
 * viendrait du BFF plutôt que des assets. Il existe pour que ce jour-là la
 * couture soit prête, et il se compose ou non sans rien changer aux appelants.
 */
class CachedNetworkLineRepository(
    private val upstream: NetworkLineRepository,
) : NetworkLineRepository {

    @Volatile private var lines: List<TransitLine>? = null
    @Volatile private var byName: Map<String, TransitLine> = emptyMap()

    override suspend fun allLines(): List<TransitLine> = loaded()

    override suspend fun line(named: String): TransitLine? {
        loaded()
        return byName[canonicalLineName(named)]
    }

    private suspend fun loaded(): List<TransitLine> {
        lines?.let { return it }
        val fresh = upstream.allLines()
        // Un inventaire vide ne se garde pas : ce serait figer une panne pour
        // toute la durée du processus, là où un second essai peut réussir.
        if (fresh.isEmpty()) return fresh
        byName = buildMap { fresh.forEach { putIfAbsent(it.match, it) } }
        lines = fresh
        return fresh
    }
}
