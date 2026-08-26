package io.aule.android.core.guet

import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.NearbyDigest
import io.aule.android.core.model.StopDeparture
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.normalizeStopName
import io.aule.android.core.model.walkSecondsOver
import kotlin.math.roundToInt

/**
 * De ce qu'on sait à ce qu'on annonce.
 *
 * Pur, sans état, sans horloge : `now` vit dans le contexte. Une décision qui se
 * prend ici s'exerce en une ligne ; une décision qui se prend dans un modèle
 * Android demande un appareil.
 *
 * Port de `Native/Aule/Core/Guet/GuetEngine.swift`.
 */
object GuetEngine {

    /** Combien de candidats on garde. Au-delà, on classe des passages que personne ne lira. */
    const val DEFAULT_LIMIT = 8

    /**
     * Au-delà de ce délai, un passage n'est pas encore l'affaire du Guet. Trente
     * minutes : c'est aussi l'horizon au-delà duquel une estimation temps réel
     * cesse d'être programmable — voir [GuetSchedule].
     */
    const val HORIZON = 30.0 * 60

    // ------------------------------------------------------------------ classer

    /**
     * Les passages atteignables autour de l'utilisateur, du plus pertinent au
     * moins.
     *
     * @param deduplicated **un couple ligne + destination n'est proposé qu'à un
     *   seul arrêt.** Vrai pour proposer, faux pour rattraper — voir [alternatives].
     */
    fun candidates(context: GuetContext, deduplicated: Boolean = true): List<GuetCandidate> {
        if (!context.preferences.isEnabled) return emptyList()

        val found = mutableListOf<GuetCandidate>()
        for (entry in context.nearby) {
            val place = entry.stop.departuresKey
            val announced = context.departures[place] ?: continue
            val walk = walkSeconds(place, entry, context)

            for (departure in announced.departures) {
                val candidate = candidate(departure, entry.stop, place, walk, context)
                if (candidate != null) found += candidate
            }
        }

        val ranked = found.sortedWith(
            compareByDescending<GuetCandidate> { it.score.total }
                // À score égal, le plus tôt : deux passages également notés ne se
                // départagent que par celui qu'on peut encore attraper.
                .thenBy { it.timing.expectedAt },
        )

        // ⚠️ **La déduplication vient après le classement, jamais avant.** Écarter
        // les arrêts en amont de la boucle reviendrait à trancher avant que
        // [candidate] n'ait appliqué le mode, l'atteignabilité et l'horizon : un
        // passage inatteignable à l'arrêt le plus proche aurait réservé le couple,
        // et le même bus, parfaitement attrapable trois minutes plus loin, aurait
        // disparu de la veille entière.
        //
        // Ici, l'arbitre est le classement — donc l'atteignabilité, l'affinité et
        // la fraîcheur, et pas seulement la distance, qui y pèse déjà par la
        // proximité.
        return (if (deduplicated) oneStopPerCouple(ranked) else ranked).take(DEFAULT_LIMIT)
    }

    /**
     * Le passage correspondant à une clé, **sans le filtre d'atteignabilité**.
     *
     * ## Pourquoi ce second chemin existe
     *
     * [candidates] écarte ce qu'on ne peut pas attraper : c'est juste pour
     * **proposer**. Mais pendant un accompagnement, un passage devenu hors
     * d'atteinte ne doit surtout pas disparaître — c'est exactement le moment où
     * l'écran doit dire « il faut presser le pas », puis « manqué », et où l'on
     * propose « Tenter quand même » ou « Prendre le suivant ».
     *
     * Le laisser tomber du classement figeait le volet sur son dernier niveau
     * confortable, pendant que l'utilisateur s'éloignait de son quai. Aucun écran
     * ne le disait.
     */
    fun passage(key: PassageKey, context: GuetContext): GuetCandidate? {
        for (entry in context.nearby) {
            val place = entry.stop.departuresKey
            val announced = context.departures[place] ?: continue
            val walk = walkSeconds(place, entry, context)
            for (departure in announced.departures) {
                val found = candidate(
                    departure, entry.stop, place, walk, context,
                    requireReachable = false,
                )
                if (found != null && found.key == key) return found
            }
        }
        return null
    }

    /**
     * Le candidat qui mérite de sonner, s'il y en a un.
     *
     * Trois conditions, et les trois sont nécessaires : c'est **l'heure** (la phase
     * le dit), c'est **atteignable** (la faisabilité le dit), et ça **vaut la
     * peine** (le score le dit). Un seuil de score sans condition de phase sonnerait
     * un quart d'heure trop tôt ; une condition de phase sans seuil sonnerait pour
     * n'importe quel bus.
     */
    fun alertWorthy(candidates: List<GuetCandidate>): GuetCandidate? = candidates.firstOrNull {
        (it.level.phase == GuetPhase.PREPARE || it.level.phase == GuetPhase.LEAVE_NOW) &&
            it.level.isReachable &&
            it.score.isAlertWorthy
    }

    // --------------------------------------------------------- proposer autre chose

    /**
     * Ce qu'on peut proposer quand celui-ci devient hors d'atteinte.
     *
     * L'ordre **est** la règle, et il va du moins dépaysant au plus : le même
     * véhicule plus tard, puis une autre ligne au même endroit, puis le même trajet
     * d'ailleurs. Quelqu'un qui vient de rater son tram veut d'abord le suivant,
     * pas un itinéraire différent.
     *
     * Aucun appel réseau : tout se calcule sur le contexte déjà en main. Les
     * alternatives refusées au registre sont écartées — proposer ce qu'on vient de
     * refuser est la façon la plus rapide de perdre la confiance qu'une alerte
     * demande.
     */
    fun alternatives(
        rejected: GuetCandidate,
        context: GuetContext,
        limit: Int = 3,
    ): List<GuetCandidate> {
        // Proposer ne doit montrer qu'un arrêt ; **rattraper un bus manqué doit
        // pouvoir en montrer un autre**, et c'est exactement le rang 2 ci-dessous.
        // La déduplication le rendrait mort.
        val pool = candidates(context, deduplicated = false).filter {
            it.key != rejected.key &&
                !context.ledger.isDeclined(it.key) &&
                it.level.isReachable
        }

        fun rank(candidate: GuetCandidate): Int {
            val sameLine = normalizeStopName(candidate.line) == normalizeStopName(rejected.line)
            val sameDestination =
                normalizeStopName(candidate.destination) == normalizeStopName(rejected.destination)
            val samePlace = normalizeStopName(candidate.place) == normalizeStopName(rejected.place)
            // 1. Le prochain passage de la même ligne vers la même destination.
            if (sameLine && sameDestination && samePlace) return 0
            // 2. Une autre ligne du même lieu, vers la même destination.
            if (sameDestination && samePlace) return 1
            // 3. La même ligne depuis un autre quai accessible.
            if (sameLine && sameDestination) return 2
            return 3
        }

        return pool
            .sortedWith(compareBy<GuetCandidate> { rank(it) }.thenBy { it.timing.expectedAt })
            .take(limit)
    }

    // ------------------------------------------------------------------ interne

    /**
     * Un couple ligne + destination n'est gardé qu'à **un seul arrêt** : celui que
     * le classement a placé en tête.
     *
     * ⚠️ **La clé est le couple, l'unité écartée est l'arrêt — pas le passage.**
     * Tous les passages successifs du couple à l'arrêt retenu sont conservés :
     * c'est précisément ce que [alternatives] classe en rang 0, « le prochain
     * passage de la même ligne vers la même destination ». Dédupliquer jusqu'au
     * passage rendrait ce rang inatteignable.
     *
     * Les deux sens d'une ligne restent distincts, chacun gagné par son propre
     * arrêt.
     */
    private fun oneStopPerCouple(ranked: List<GuetCandidate>): List<GuetCandidate> {
        val held = mutableMapOf<String, String>()
        return ranked.filter { candidate ->
            val couple =
                "${normalizeStopName(candidate.line)}|${normalizeStopName(candidate.destination)}"
            val place = normalizeStopName(candidate.place)
            val winner = held[couple]
            if (winner == null) {
                held[couple] = place
                true
            } else {
                winner == place
            }
        }
    }

    private fun candidate(
        departure: StopDeparture,
        stop: TransitStop,
        place: String,
        walkSeconds: Int,
        context: GuetContext,
        // Écarter ce qu'on ne peut pas attraper. Vrai pour **proposer**, faux pour **suivre**.
        requireReachable: Boolean = true,
    ): GuetCandidate? {
        // Un mode que l'utilisateur a décoché n'est pas un mauvais candidat, il
        // n'en est pas un. Le mode absent, lui, laisse passer : « on ne sait pas »
        // n'est pas « non ».
        val mode = departure.mode
        if (mode != null && mode !in context.preferences.modes) return null

        val timing = GuetTiming.of(
            expectedAt = departure.expectedAt,
            walkSeconds = walkSeconds,
            platformSeconds = context.preferences.platformSeconds,
            preparationSeconds = context.preferences.preparationSeconds,
        )
        val level = GuetLevel.of(timing, context.now)

        // Hors d'atteinte, ou trop loin pour qu'on ait quoi que ce soit à en dire.
        if (requireReachable) {
            if (!level.isReachable) return null
            if (timing.vehicleSeconds(context.now) > HORIZON) return null
        }

        val key = context.ledger.resolve(
            place = place,
            line = departure.line,
            destination = departure.destination,
            expectedAt = departure.expectedAt,
        )

        val score = GuetScore(
            mapOf(
                GuetScoring.Criterion.REACH to GuetScoring.reach(timing.slack(context.now)),
                GuetScoring.Criterion.PROXIMITY to GuetScoring.proximity(walkSeconds),
                GuetScoring.Criterion.AFFINITY to GuetScoring.affinity(
                    isFollowed = context.preferences.followedLines.any {
                        normalizeStopName(it) == normalizeStopName(departure.line)
                    },
                    habit = context.habits.affinity(
                        line = departure.line,
                        place = place,
                        at = context.now,
                        zone = context.zone,
                    ),
                ),
                GuetScoring.Criterion.DIRECTION to GuetScoring.direction(
                    directionMatch(departure, context),
                ),
                GuetScoring.Criterion.SILENCE to GuetScoring.silence(context.ledger.status(key)),
                GuetScoring.Criterion.FRESHNESS to GuetScoring.freshness(
                    isRealtime = departure.isRealtime,
                    isFleetStale = context.isFleetStale,
                ),
            ),
        )

        return GuetCandidate(
            key = key,
            place = place,
            stop = stop,
            line = departure.line,
            lineColor = departure.lineColor,
            destination = departure.destination,
            mode = departure.mode,
            isRealtime = departure.isRealtime,
            timing = timing,
            level = level,
            score = score,
        )
    }

    /**
     * La marche à retenir, **recalculée à chaque fois depuis la position du
     * moment**.
     *
     * ## Le défaut que ce calcul empêche
     *
     * La première écriture iOS prenait la marche mesurée telle quelle, et
     * l'estimation géométrique de l'inventaire sinon. Les deux sont datées : la
     * mesure vient du dernier aller-retour réseau, l'estimation du dernier
     * assemblage. Autrement dit **la marche ne bougeait pas pendant qu'on
     * marche** — et pendant un accompagnement, c'est le seul moment où quelqu'un
     * se rapproche vraiment de son quai. La marge affichée ne descendait que parce
     * que le véhicule approchait, jamais parce qu'on avançait ; et partir dans la
     * mauvaise direction ne changeait rien à l'écran.
     *
     * ## Ce qu'on garde de la mesure
     *
     * Sa **valeur relative**. Un itinéraire réel n'est pas un vol d'oiseau : il
     * contourne, il traverse, il attend un feu. Le rapport entre ce que le
     * calculateur a rendu et ce que la géométrie disait **au même endroit** capture
     * ce détour ; on l'applique à l'estimation fraîche. La distance redevient
     * vivante, le détour reste appris.
     */
    private fun walkSeconds(
        place: String,
        entry: NearbyDigest.StopEntry,
        context: GuetContext,
    ): Int {
        val fresh = walkSecondsOver(GeoMath.distance(context.position, entry.stop.coordinate))
        val factor = context.preferences.pace.factor(context.habits.paceFactor)

        val measured = context.walkSeconds[place]
            ?: return (fresh * factor).roundToInt()

        val atMeasure = walkSecondsOver(GeoMath.distance(measured.from, entry.stop.coordinate))
        // Sous quelques secondes, le rapport n'a plus de sens — on était déjà au quai.
        if (atMeasure <= 5) return (fresh * factor).roundToInt()

        val detour = measured.seconds.toDouble() / atMeasure.toDouble()
        return (fresh * detour * factor).roundToInt()
    }

    /**
     * Est-ce qu'on va par là.
     *
     * @return `null` quand ni trajet actif ni habitude ne permettent d'en juger —
     *   et c'est le cas courant, pas un cas limite. Rendre 0 y ferait pénaliser
     *   toutes les destinations d'un utilisateur qui n'a rien planifié.
     */
    private fun directionMatch(departure: StopDeparture, context: GuetContext): Double? {
        val wanted = context.activeDestination?.takeIf { it.isNotBlank() } ?: return null
        val target = normalizeStopName(wanted)
        val candidate = normalizeStopName(departure.destination)
        if (candidate.isEmpty()) return 0.0
        if (candidate == target) return 1.0
        val mine = candidate.split(" ").toSet()
        val theirs = target.split(" ").toSet()
        if (mine.isNotEmpty() && theirs.isNotEmpty() &&
            (mine.containsAll(theirs) || theirs.containsAll(mine))
        ) {
            return 1.0
        }
        return 0.0
    }
}
