package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.LineJourneyStop
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.canonicalLineName
import io.aule.android.core.model.repository.DriverServiceRepository
import io.aule.android.core.model.repository.StopRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Une desserte : un sens de la ligne, et les arrêts dans l'ordre.
 *
 * @param terminus ce que le référentiel annonce au bout — « Beaujoire ». C'est
 *   ce qui nomme l'onglet, et non « sens 0 », qui ne dit rien à personne.
 */
internal data class LineDesserte(
    val directionId: Int,
    val terminus: String,
    val stops: List<LineJourneyStop>,
)

internal data class LineStopsUiState(
    /** L'indice canonique de la ligne chargée, ou `null` avant tout chargement. */
    val line: String? = null,
    val dessertes: List<LineDesserte> = emptyList(),
    val selectedDirection: Int? = null,
    /**
     * Les lignes qui desservent chaque arrêt, par **nom d'arrêt**.
     *
     * Un nom et non un identifiant : le référentiel des passages travaille par
     * lieu — « Commerce » —, là où la desserte GTFS numérote des quais. C'est
     * aussi ce qui fait que les deux sens d'une ligne partagent leurs réponses,
     * et qu'un arrêt vu sur la 1 est déjà connu quand on ouvre la 2.
     *
     * Une entrée absente veut dire « pas encore lu » ; une entrée vide veut dire
     * « lu, et il n'y a rien ». Le rang ne montre donc rien dans les deux cas,
     * mais le modèle ne redemande pas ce qui a déjà répondu.
     */
    val connections: Map<String, List<ServingLine>> = emptyMap(),
    /**
     * L'arrêt sur lequel la caméra s'est posée, ou `null` quand elle tient la
     * ligne entière. Un **identifiant** et non l'arrêt lui-même : changer de
     * sens reconstruit la liste, et un objet retenu là désignerait un arrêt qui
     * n'est plus dans la desserte affichée.
     */
    val focusedStopId: String? = null,
    val isLoading: Boolean = false,
    /**
     * Pourquoi la desserte n'a pas pu être lue. **Dit à l'écran** : contrairement
     * au plan d'un véhicule, il n'y a rien d'autre sur cette fiche — se taire
     * laisserait un volet vide qui se lit « cette ligne ne dessert rien ».
     */
    val failure: LineStopsFailure? = null,
) {
    val selected: LineDesserte?
        get() = dessertes.firstOrNull { it.directionId == selectedDirection } ?: dessertes.firstOrNull()

    /** L'arrêt sous la caméra, s'il est encore dans la desserte affichée. */
    val focusedStop: LineJourneyStop?
        get() = selected?.stops?.firstOrNull { it.id == focusedStopId }

    /**
     * Ce qu'on peut prendre d'autre à cet arrêt.
     *
     * La ligne qu'on consulte est **retirée** : elle n'est pas une
     * correspondance, c'est celle sur laquelle on est déjà. La garder ferait
     * porter à chaque rang un badge qui redit le titre du volet.
     *
     * Dédoublonné par indice : le référentiel répond une entrée **par sens**,
     * et la 2 desservant l'arrêt dans les deux sens y figure deux fois.
     */
    fun connectionsAt(stopName: String, excluding: String): List<ServingLine> {
        val self = canonicalLineName(excluding)
        return connections[stopName].orEmpty()
            .filterNot { canonicalLineName(it.line) == self }
            .distinctBy { canonicalLineName(it.line) }
    }

    /**
     * Vrai quand la ligne a plus d'une desserte à proposer — donc quand il y a un
     * choix à offrir. Une ligne à sens unique n'a pas besoin d'un sélecteur qui
     * ne sélectionne rien.
     */
    val hasChoice: Boolean get() = dessertes.size > 1
}

/**
 * Pourquoi la fiche est vide, et ce qu'on peut y faire.
 *
 * Les trois cas mènent au même écran vide mais **n'appellent pas la même
 * réaction** : se reconnecter, réessayer, ou accepter que le référentiel ne
 * connaisse pas cette ligne. Un seul message pour les trois enverrait deux
 * personnes sur trois au mauvais geste.
 */
internal enum class LineStopsFailure {
    /** Les tables GTFS exigent une session, comme la grille horaire. */
    NOT_SIGNED_IN,

    /**
     * La ligne existe dans l'index embarqué mais pas dans le référentiel des
     * services. C'est le cas de tous les cars Aléop : l'app ne suit ni leur
     * flotte ni leurs horaires, seuls leurs tracés sont dans les tuiles.
     */
    UNKNOWN_LINE,

    /** Le réseau n'a pas répondu. */
    NETWORK,
}

/**
 * La desserte d'une ligne : ses arrêts, dans l'ordre, par sens.
 *
 * ## Pourquoi ce n'est pas « la liste des arrêts de la ligne »
 *
 * « La séquence d'arrêts de la ligne 1 » n'existe pas : dans un même sens, elle
 * dessert Beaujoire **ou** Babinière. Le modèle porte donc **tous** les sens
 * annoncés et laisse choisir — c'est la seule façon de ne pas répondre faux à
 * « où va-t-elle ? ».
 *
 * ## Deux requêtes, et un catalogue relu une fois
 *
 * Le référentiel des services (`fetchLines`) donne l'identifiant GTFS de la
 * ligne et ses sens ; `fetchJourney` donne les arrêts d'un sens. Le premier est
 * relu **une seule fois** par processus : il change à la fréquence d'un dépôt
 * GTFS, et le redemander à chaque fiche ouverte paierait un catalogue entier
 * pour un identifiant.
 *
 * Les sens partent **ensemble** : ce sont deux requêtes indépendantes, et les
 * enchaîner doublerait l'attente devant un volet qui n'affiche rien tant que le
 * premier n'a pas répondu.
 *
 * ## Rien de la ligne précédente ne survit
 *
 * ⚠️ Sans effacement à l'ouverture, la desserte du C6 resterait affichée sous le
 * badge de la 1 pendant toute la requête — des arrêts justes attribués à la
 * mauvaise ligne, ce qui est pire qu'un volet en attente.
 *
 * Port de `Native/Aule/Features/Lines/LineStopsModel.swift`.
 */
internal class LineStopsModel(
    private val repository: DriverServiceRepository?,
    private val session: () -> AuthSession?,
    private val dispatchers: AuleDispatchers,
    private val scope: CoroutineScope,
    private val logger: AuleLogger,
    /**
     * D'où viennent les correspondances. `null` les désactive — c'est ce que
     * font les tests qui n'ont rien à en dire.
     */
    private val stops: StopRepository? = null,
) {
    private val _state = MutableStateFlow(LineStopsUiState())
    val state: StateFlow<LineStopsUiState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Le référentiel des services, relu une fois. `null` tant qu'on n'a pas
     * essayé — à distinguer d'une liste vide, qui est une réponse.
     */
    private var serviceLines: List<io.aule.android.core.model.ServiceLine>? = null

    /**
     * Les correspondances déjà lues, pour la vie du processus.
     *
     * Elles ne changent pas à l'échelle d'une session : quelles lignes
     * desservent un arrêt est un fait de réseau, pas un horaire. Refermer une
     * fiche et la rouvrir ne redemande donc rien, et les arrêts partagés entre
     * deux lignes ne sont payés qu'une fois.
     */
    private val servingCache = mutableMapOf<String, List<ServingLine>>()

    /**
     * Ouvre la fiche d'une ligne.
     *
     * Redemander la ligne déjà affichée ne fait rien : c'est ce qui arrive quand
     * la vue se recompose, et recharger à chaque recomposition ferait clignoter
     * la fiche.
     */
    fun open(line: String) {
        val canonical = canonicalLineName(line)
        if (_state.value.line == canonical) return
        job?.cancel()
        _state.value = LineStopsUiState(line = canonical, isLoading = true)

        val current = session()
        if (current == null || repository == null) {
            _state.value = _state.value.copy(
                isLoading = false,
                failure = LineStopsFailure.NOT_SIGNED_IN,
            )
            return
        }

        job = scope.launch {
            try {
                val dessertes = withContext(dispatchers.io) { load(current, canonical) }
                if (_state.value.line != canonical) return@launch
                _state.value = _state.value.copy(
                    isLoading = false,
                    dessertes = dessertes,
                    selectedDirection = dessertes.firstOrNull()?.directionId,
                    failure = if (dessertes.isEmpty()) LineStopsFailure.UNKNOWN_LINE else null,
                    // Ce que le cache sait déjà se pose tout de suite : sur une
                    // ligne rouverte, les badges sont là avant le premier rang.
                    connections = servingCache.filterKeys { name ->
                        dessertes.any { desserte -> desserte.stops.any { it.name == name } }
                    },
                )
                // Les deux sens d'un coup, et pas seulement celui qui s'affiche :
                // ils partagent presque tous leurs arrêts, et basculer d'un
                // onglet à l'autre serait sinon une seconde attente pour la même
                // réponse.
                loadConnections(canonical, dessertes.flatMap { it.stops }.map { it.name })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unknown: UnknownLine) {
                if (_state.value.line != canonical) return@launch
                logger.info(LogDomain.NET, "Ligne $canonical absente du référentiel des services.")
                _state.value = _state.value.copy(
                    isLoading = false,
                    failure = LineStopsFailure.UNKNOWN_LINE,
                )
            } catch (failure: Throwable) {
                if (_state.value.line != canonical) return@launch
                logger.warn(LogDomain.NET, "Desserte indisponible pour $canonical.", failure)
                _state.value = _state.value.copy(
                    isLoading = false,
                    failure = LineStopsFailure.NETWORK,
                )
            }
        }
    }

    /** Repart tout de suite, sans attendre qu'on referme et rouvre la fiche. */
    fun retry() {
        val line = _state.value.line ?: return
        _state.value = LineStopsUiState()
        open(line)
    }

    fun selectDirection(directionId: Int) {
        if (_state.value.selectedDirection == directionId) return
        // Changer de sens **rend la caméra** : l'arrêt visé appartenait à
        // l'autre desserte, et la garder posée dessus laisserait la carte sur un
        // point que la liste ne montre plus.
        _state.value = _state.value.copy(selectedDirection = directionId, focusedStopId = null)
    }

    /**
     * Pose la caméra sur un arrêt, ou la rend à la ligne entière avec `null`.
     *
     * Retoucher l'arrêt déjà visé la rend aussi : c'est le geste qu'on fait sans
     * réfléchir pour revenir en arrière, le même que sur l'inventaire des
     * lignes.
     */
    fun focusStop(stopId: String?) {
        val next = if (stopId != null && stopId == _state.value.focusedStopId) null else stopId
        if (next == _state.value.focusedStopId) return
        _state.value = _state.value.copy(focusedStopId = next)
    }

    fun close() {
        job?.cancel()
        job = null
        _state.value = LineStopsUiState()
    }

    /**
     * Lit les correspondances de tous les arrêts de la ligne, par vagues.
     *
     * ## Une requête par arrêt, et pas d'autre choix
     *
     * Le BFF n'expose `stop-serving-lines` qu'un lieu à la fois. Une desserte de
     * quarante arrêts vaut donc quarante requêtes — c'est cher, et c'est la
     * raison pour laquelle le volet « autour de vous » se limite à trois. Le
     * calcul n'est pas le même ici : là-bas les passages se **rafraîchissent**
     * toutes les trente secondes, ici les correspondances sont un fait de
     * réseau, lu **une fois** et gardé (voir [servingCache]).
     *
     * ## Par vagues, et publiées au fur et à mesure
     *
     * [CONNECTION_WINDOW] à la fois : d'un coup, quarante requêtes ouvriraient
     * quarante sockets et prendraient la bande passante à la flotte, qui elle
     * est temps réel. Chaque vague publie ce qu'elle a obtenu, donc la liste se
     * garnit de haut en bas au lieu d'attendre la dernière réponse.
     *
     * ## Un arrêt qui échoue est muet
     *
     * Le rang garde son nom, sa position, son geste vers la carte. Un bandeau
     * d'erreur par arrêt ferait quarante bandeaux pour une seule coupure, et la
     * correspondance est un complément — pas ce qu'on est venu chercher.
     */
    private suspend fun loadConnections(canonical: String, names: List<String>) {
        val repository = stops ?: return
        val wanted = names.filter { it.isNotBlank() }.distinct()
        val pending = wanted.filterNot { it in servingCache }
        for (wave in pending.chunked(CONNECTION_WINDOW)) {
            if (_state.value.line != canonical) return
            val resolved = coroutineScope {
                wave.map { name ->
                    async(dispatchers.io) {
                        name to runCatching { repository.servingLines(name) }
                            .getOrElse { failure ->
                                if (failure is CancellationException) throw failure
                                logger.debug(
                                    LogDomain.NET,
                                    "Correspondances illisibles à « $name ».",
                                )
                                emptyList()
                            }
                    }
                }.awaitAll()
            }
            servingCache.putAll(resolved)
            if (_state.value.line != canonical) return
            _state.value = _state.value.copy(
                connections = _state.value.connections + resolved,
            )
        }
    }

    private suspend fun load(
        current: AuthSession,
        canonical: String,
    ): List<LineDesserte> {
        val repository = this.repository ?: throw UnknownLine()
        val catalog = serviceLines ?: repository.fetchLines(current).also { serviceLines = it }
        val line = catalog.firstOrNull { canonicalLineName(it.label) == canonical }
            ?: throw UnknownLine()

        // Les deux sens partent ensemble : les enchaîner doublerait l'attente
        // devant un volet qui n'affiche rien avant la première réponse.
        return coroutineScope {
            line.directions.map { direction ->
                async {
                    // Un sens qui échoue ne fait pas tomber l'autre : une fiche à
                    // un sens vaut mieux qu'une fiche vide.
                    val stops = runCatching {
                        repository.fetchJourney(current, line.id, direction.id).stops
                    }.getOrElse { failure ->
                        if (failure is CancellationException) throw failure
                        logger.warn(
                            LogDomain.NET,
                            "Sens ${direction.id} de $canonical illisible.",
                            failure,
                        )
                        emptyList()
                    }
                    LineDesserte(
                        directionId = direction.id,
                        terminus = direction.terminus,
                        stops = stops,
                    )
                }
            }.map { it.await() }.filter { it.stops.isNotEmpty() }
        }
    }

    /** La ligne n'est pas dans le référentiel des services — voir [LineStopsFailure]. */
    private class UnknownLine : Exception()

    private companion object {
        /**
         * Combien d'arrêts interrogent le référentiel en même temps.
         *
         * Six : de quoi garnir une desserte de tram en deux vagues, sans
         * disputer la bande passante à la flotte, qui elle se rafraîchit en
         * continu.
         */
        const val CONNECTION_WINDOW = 6
    }
}
