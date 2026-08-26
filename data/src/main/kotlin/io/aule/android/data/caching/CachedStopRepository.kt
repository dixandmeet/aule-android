package io.aule.android.data.caching

import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.decodeStopCatalog
import io.aule.android.core.model.encodeCatalog
import io.aule.android.core.model.repository.CacheStore
import io.aule.android.core.model.repository.StopRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Le catalogue d'arrêts, servi depuis le disque.
 *
 * **Un décorateur, pas une modification.** [StopRepository] ne change pas de
 * signature : le cache se compose dans `AuleGraph.create` et rien ne remonte à
 * l'interface. C'est la couture qui existait déjà ; il suffisait de s'y brancher.
 *
 * ## Pourquoi un fichier, et pas le cache HTTP d'OkHttp
 *
 * Le BFF sert le catalogue avec `Cache-Control: public, s-maxage=300,
 * stale-while-revalidate=3600`, **sans `ETag`**. Or `s-maxage` ne s'adresse
 * qu'aux caches partagés : un cache privé l'ignore et retombe sur une fraîcheur
 * heuristique. Un lancement dans le métro resterait donc un lancement sans
 * arrêts.
 *
 * ## La politique de fraîcheur, en un seul endroit
 *
 * On sert le disque **immédiatement** s'il est lisible, et on programme une
 * revalidation en fond qui réécrit le fichier pour le lancement suivant. Le
 * catalogue est donc au pire un lancement en retard, ce qui est acceptable pour
 * une donnée qui change deux fois par an — et le contrat reste intact. Publier un
 * flux « disque puis réseau » obligerait chaque appelant et chaque test à
 * manipuler une séquence pour une valeur qui ne bouge jamais.
 *
 * Pas de marqueur « périmé » : un arrêt déplacé est une erreur d'arrondi, un
 * arrêt manquant est un bug. L'écran n'a besoin de savoir qu'une chose — s'il a
 * des arrêts.
 *
 * Port de `Native/Aule/Services/Caching/CachedStopRepository.swift`.
 *
 * @param scope où tourne la revalidation. Elle survit à l'appelant : c'est le
 *   propre d'un travail de fond, et le lier à l'écran qui a demandé les arrêts
 *   l'annulerait au premier changement de volet.
 */
class CachedStopRepository(
    private val upstream: StopRepository,
    private val cache: CacheStore,
    private val scope: CoroutineScope,
    private val logger: AuleLogger,
) : StopRepository {

    override suspend fun allStops(): List<TransitStop> {
        val cached = decodeStopCatalog(cache.read(CATALOG_FILE))
        if (cached.isNotEmpty()) {
            logger.info(LogDomain.NET, "${cached.size} arrêts servis depuis le disque.")
            scope.launch { revalidateQuietly() }
            return cached
        }
        return fetchAndStore()
    }

    // Les passages et les lignes desservies ne se mettent pas en cache : ils
    // changent à la minute. On délègue sans rien ajouter.

    override suspend fun departures(atStopNamed: String): StopDepartures =
        upstream.departures(atStopNamed)

    override suspend fun servingLines(atStopNamed: String): List<ServingLine> =
        upstream.servingLines(atStopNamed)

    private suspend fun fetchAndStore(): List<TransitStop> {
        val fresh = upstream.allStops()

        // **Ne jamais écrire un catalogue vide.** Au lancement suivant il
        // passerait pour un cache valide, et l'application n'aurait plus un seul
        // arrêt sans qu'aucune erreur ne le dise — le pire des deux mondes, un
        // défaut silencieux qui survit aux relances.
        if (fresh.isEmpty()) return fresh

        cache.write(CATALOG_FILE, fresh.encodeCatalog())
        return fresh
    }

    /**
     * La revalidation d'arrière-plan : l'utilisateur a déjà ses arrêts, son échec
     * n'est pas un incident pour lui. On le journalise sans le remonter.
     */
    private suspend fun revalidateQuietly() {
        try {
            val fresh = fetchAndStore()
            logger.info(LogDomain.NET, "Catalogue revalidé : ${fresh.size} arrêts.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logger.warn(LogDomain.NET, "Revalidation du catalogue impossible.", failure)
        }
    }

    private companion object {
        const val CATALOG_FILE = "stops-catalog-v1.json"
    }
}
