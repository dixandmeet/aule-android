package io.aule.android.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Combien de jours en avant et en arrière la grille se laisse feuilleter.
 *
 * Les bornes ne sont pas décoratives : au-delà, le GTFS publié n'a plus rien à
 * dire, et une flèche qui n'ouvre que des journées vides fait croire à une panne
 * plutôt qu'à une fin de référentiel. Trois semaines couvrent le changement de
 * grille d'un réseau — deux par an —, et la semaine en arrière sert à vérifier
 * un horaire qu'on a lu la veille.
 */
const val SCHEDULE_DAYS_AHEAD = 21
const val SCHEDULE_DAYS_BEHIND = 7

/**
 * Jusqu'où l'on cherche la reprise du service quand la journée est finie.
 *
 * Plusieurs jours, et non « demain » : une ligne peut ne pas circuler demain. Un
 * samedi soir sur une desserte scolaire reprend le lundi, et un « reprise
 * demain » y serait faux.
 */
const val RESUMPTION_LOOKAHEAD_DAYS = 7

/**
 * Un passage **théorique**, tel que le GTFS l'inscrit dans la grille d'un jour
 * de service.
 *
 * ## Pourquoi des secondes, et pas une heure
 *
 * Un jour de service dépasse minuit : le dernier tram part à 25 h 40, c'est-à-dire
 * 92 400 s après le minuit de *son* jour. Ramener cela à une heure murale
 * rangerait ce passage à 1 h 40 en tête de grille, avant le premier départ du
 * matin — et le lecteur croirait avoir le temps de le prendre le soir même. Les
 * secondes portent l'information entière ; l'heure qu'on écrit s'en déduit, elle
 * ne s'en remplace pas.
 *
 * ⚠️ **Le serveur rend aussi `time` et `dayOffset` ; on ne les lit pas.** Ce sont
 * deux dérivées de `seconds` calculées à l'autre bout — `serviceTime()` dans
 * `stop-day-schedule/route.ts` — et les relire reviendrait à tenir deux vérités
 * pour un seul fait.
 *
 * @param seconds secondes depuis le minuit du jour de service. Peut dépasser 86 400.
 * @param departureId la course que cet horaire désigne, quand le référentiel la
 *   nomme. C'est la clé qui ouvrira son plan le jour où la grille mènera à une course.
 */
data class ScheduledPassage(
    val seconds: Int,
    val departureId: String? = null,
    val mode: TransportMode? = null,
) {
    /**
     * L'identité porte **l'heure et la course** : deux courses de profils
     * différents peuvent partir à la même minute — le serveur ne dédoublonne que
     * par seconde —, et deux départs homonymes dans une même grille feraient
     * sauter la liste sous le doigt.
     */
    val id: String get() = "$seconds|${departureId ?: "-"}"

    /**
     * L'heure murale, de 0 à 23. Le lendemain matin d'un service de nuit y
     * revient à zéro : c'est [dayOffset] qui dit lequel des deux « 00 h » on lit.
     */
    val hour: Int get() = (seconds / 3_600) % 24
    val minute: Int get() = (seconds % 3_600) / 60

    /**
     * Zéro le jour même, un après minuit. Deux ne se voit pas sur ce réseau,
     * mais rien ici n'en dépend.
     */
    val dayOffset: Int get() = seconds / 86_400
}

/**
 * Ce que le référentiel a répondu.
 *
 * **Deux vides, et ils ne s'écrivent pas pareil.** Le réseau peut répondre « ce
 * jour-là, aucune course » — un dimanche sur une ligne scolaire, une grille de
 * vacances — et c'est une réponse. Il peut aussi répondre qu'il ne connaît ni
 * cet arrêt ni ce sens dans son référentiel, et c'est une lacune de la donnée,
 * pas une absence de service. Les confondre ferait annoncer « rien ne circule ce
 * jour-là » sur une desserte qui circule très bien.
 */
enum class DayScheduleOutcome {
    /**
     * Le référentiel a répondu pour cette desserte. Une liste vide veut alors
     * dire, à la lettre : **aucun départ ce jour-là**.
     */
    PUBLISHED,

    /**
     * 404 — le GTFS ne connaît pas cet arrêt sous ce nom, ou ce sens sur cette
     * ligne. On ne sait rien de ses horaires, ce qui n'est pas la même chose que
     * de savoir qu'il n'y en a pas.
     */
    UNKNOWN_DESSERTE,
}

/**
 * Pourquoi une journée s'affiche vide — **une fois le direct consulté**.
 *
 * [DayScheduleOutcome] dit ce que le référentiel a répondu ; il ne peut rien
 * savoir du réseau qui, lui, circule. Quand la grille rend « aucun départ » pour
 * le jour même et que des passages sont annoncés à la minute, les deux ne
 * peuvent pas être vrais ensemble.
 *
 * ⚠️ **Mesuré le 01/09/2026 côté iOS, et c'est ce qui a fait naître ce type.** La
 * grille publiée en production s'arrêtait au 30/08 : la route répondait 200 avec
 * zéro départ, et l'écran annonçait donc « cette desserte ne circule pas à cette
 * date » sur tout le réseau — pendant que la fiche d'arrêt, un cran plus haut,
 * comptait les trams en temps réel. Une grille périmée se lit comme une nuit
 * sans service, et rien ne les distingue côté client sinon le direct lui-même.
 */
enum class EmptyDayReason {
    /**
     * Le référentiel a répondu, rien ne le contredit : ce jour-là, cette
     * desserte ne part pas.
     */
    NO_SERVICE,

    /** 404 — ni cet arrêt sous ce nom, ni ce sens sur cette ligne. On ne sait rien. */
    UNKNOWN_DESSERTE,

    /**
     * La grille se tait là où le direct parle. On n'affirme pas *pourquoi* —
     * grille périmée, desserte entrée trop tard, service exceptionnel —,
     * seulement qu'on n'a pas d'horaire pour une journée où le réseau circule.
     */
    GRID_SILENT,
}

/**
 * La grille d'une desserte pour un jour de service : une ligne, un sens, une journée.
 *
 * ## Ce que la route ne sait pas faire
 *
 * `stop-day-schedule` cherche par **nom d'arrêt** — tous les quais du lieu — et
 * trie lui-même les sens sur la direction demandée. Il n'existe aucun paramètre
 * de quai, comme pour les passages en temps réel : c'est le sens demandé qui
 * fait le tri, et il le fait bien, une desserte ne partant que d'un côté de la rue.
 *
 * @param serviceDate le jour demandé — celui **du serveur**, jamais celui qu'on
 *   croit avoir demandé : c'est la réponse qui dit à quelle question elle répond.
 * @param times les départs de la journée, dans l'ordre.
 */
data class StopDaySchedule(
    val serviceDate: LocalDate,
    val line: String,
    val direction: String,
    val lineColor: String? = null,
    val times: List<ScheduledPassage> = emptyList(),
    val outcome: DayScheduleOutcome = DayScheduleOutcome.PUBLISHED,
) {
    /**
     * Le rang du prochain départ de cette journée — `null` quand il n'y en a plus.
     *
     * ⚠️ **La comparaison se fait en secondes de jour de service**, jamais en
     * heure murale : à 0 h 30, le tram de « 24 h 30 » appartient encore à la
     * grille de la veille. Voir [ServiceDay.elapsedSeconds], dont c'est toute la
     * raison d'être.
     */
    fun nextIndex(now: Instant): Int? {
        val elapsed = ServiceDay.elapsedSeconds(serviceDate, now)
        return times.indexOfFirst { it.seconds >= elapsed }.takeIf { it >= 0 }
    }

    /**
     * Vrai quand la journée **avait** des départs et qu'aucun n'est à venir.
     *
     * À distinguer d'une journée sans service : celle-là n'a jamais rien promis,
     * celle-ci est finie — et c'est seulement dans ce second cas qu'annoncer une
     * reprise a un sens.
     */
    fun isOver(now: Instant): Boolean = times.isNotEmpty() && nextIndex(now) == null

    /**
     * La grille par heures.
     *
     * **Une rangée par heure, et non une colonne de quatre-vingts horaires.** Lire
     * « le prochain 27 » demande alors de balayer une ligne, pas d'arpenter une
     * colonne — c'est la forme des fiches horaires posées sur les poteaux, pour
     * la même raison.
     *
     * Le regroupement porte sur l'heure **et** le décalage de jour : le « 00 h »
     * du lendemain matin ne se range pas avec le « 00 h » qui n'existe pas en
     * tête de grille.
     */
    val hourRows: List<ScheduleHourRow>
        get() = times
            .groupBy { it.dayOffset to it.hour }
            .map { (key, passages) ->
                ScheduleHourRow(hour = key.second, dayOffset = key.first, times = passages)
            }
            .sortedBy { it.times.first().seconds }

    /**
     * Pourquoi cette journée est vide, sachant ce que le direct annonce.
     *
     * ⚠️ **[liveContradicts] n'a de sens que sur la journée affichée.** Un tram
     * annoncé maintenant ne dit rien de mardi prochain : le passer vrai en
     * feuilletant ferait répondre « le réseau circule » d'une date dont on n'a
     * aucune nouvelle. C'est à l'appelant de n'y mettre le direct que quand la
     * grille lue est celle d'aujourd'hui.
     */
    fun emptyReason(liveContradicts: Boolean): EmptyDayReason = when (outcome) {
        DayScheduleOutcome.UNKNOWN_DESSERTE -> EmptyDayReason.UNKNOWN_DESSERTE
        DayScheduleOutcome.PUBLISHED ->
            if (liveContradicts) EmptyDayReason.GRID_SILENT else EmptyDayReason.NO_SERVICE
    }

    companion object {
        /**
         * La grille qu'on n'a pas : le référentiel ne connaît pas cette desserte
         * à cet arrêt.
         *
         * Elle existe parce que cette réponse-là se fabrique en trois endroits —
         * le 404 du dépôt réseau, la fixture, et les doublures des tests — et
         * qu'une journée « vide » y serait écrite `PUBLISHED` une fois sur trois,
         * c'est-à-dire annoncée comme une absence de service.
         */
        fun unknown(line: String, direction: String, serviceDate: LocalDate): StopDaySchedule =
            StopDaySchedule(
                serviceDate = serviceDate,
                line = line,
                direction = direction,
                lineColor = null,
                times = emptyList(),
                outcome = DayScheduleOutcome.UNKNOWN_DESSERTE,
            )
    }
}

/** Une heure de la grille, et ce qui part dedans. */
data class ScheduleHourRow(
    val hour: Int,
    val dayOffset: Int,
    val times: List<ScheduledPassage>,
) {
    val id: String get() = "$dayOffset-$hour"
}

/**
 * L'arithmétique des jours de service.
 *
 * Tout y est **parisien**, et non local à l'appareil : la fiche horaire dit
 * « mercredi 26 août », et ce mercredi-là reste parisien même si le téléphone
 * est à Tokyo.
 */
object ServiceDay {

    /** Le fuseau du réseau. */
    val ZONE: ZoneId = ZoneId.of("Europe/Paris")

    /**
     * Le jour **civil** parisien, celui que la grille appelle « aujourd'hui ».
     *
     * ⚠️ **Ce n'est pas toujours le jour de service en cours.** À 0 h 30, le tram
     * qui passe appartient encore à la grille de la veille — il y figure à
     * 24 h 30. La fiche l'assume : les prochains passages en temps réel, eux, le
     * montrent quoi qu'il arrive, et [StopDaySchedule.nextIndex] reste juste sur
     * la journée de la veille si on l'ouvre.
     */
    fun today(now: Instant = Instant.now()): LocalDate = now.atZone(ZONE).toLocalDate()

    /**
     * Les secondes écoulées depuis le minuit **de ce jour de service**.
     *
     * Négatives avant ce minuit, au-delà de 86 400 après le suivant : c'est
     * exactement ce qu'il faut pour comparer à un horaire GTFS, qui compte de la
     * même façon. Le calcul passe par le fuseau, donc les deux nuits de
     * changement d'heure comptent 23 et 25 heures — un décalage en secondes
     * fixes y placerait le dernier tram une heure à côté.
     */
    fun elapsedSeconds(serviceDate: LocalDate, now: Instant): Long =
        Duration.between(serviceDate.atStartOfDay(ZONE).toInstant(), now).seconds
}

/**
 * La desserte dont on veut les horaires : une ligne et un sens, à un arrêt.
 *
 * Les deux chemins qui y mènent n'ont pas la même source — un rang de passages
 * annonce une **girouette** (« Babinière »), un rang de lignes desservies annonce
 * un **sens** (« Beaujoire / Babinière ») — et le référentiel les reconnaît l'un
 * comme l'autre : c'est `directionScore`, côté serveur, qui rapproche les deux
 * libellés. On transporte donc le libellé tel qu'on l'a lu, sans tenter de le
 * normaliser en chemin.
 */
data class ScheduleTarget(
    val line: String,
    val direction: String,
    val lineColor: String? = null,
    val mode: TransportMode? = null,
) {
    val id: String get() = "$line|$direction"

    /**
     * Le tamis qui reconnaît, parmi les passages annoncés au lieu, ceux de cette
     * desserte.
     *
     * C'est [ServingLine.serves], et ce n'est pas un détour : la règle
     * d'appariement entre un libellé de sens et les deux libellés d'un passage
     * est écrite là, elle est testée là, et une seconde copie ici finirait par en
     * diverger.
     */
    val sieve: ServingLine
        get() = ServingLine(line = line, direction = direction, lineColor = lineColor, mode = mode)
}

/**
 * Vrai quand ce passage est **celui-ci**, annoncé depuis ce quai.
 *
 * L'appariement est celui que le serveur pratique déjà entre les deux bouts de
 * l'API (`naolib-realtime.ts`, `directionMatchScore`), réduit à ce dont la fiche
 * a besoin : même ligne, et un sens reconnaissable. On essaie les **deux**
 * libellés du passage parce qu'ils ne disent pas la même chose — mesuré le
 * 18/08/2026, la ligne 1 annonce `direction: "Beaujoire / Babinière"` et
 * `destination: "Babinière"`, quand le GTFS ne connaît que le premier.
 *
 * L'inclusion se fait **par mots entiers**, dans les deux sens : « Babinière »
 * est une branche de « Beaujoire / Babinière », et il faut les reconnaître l'un
 * dans l'autre. Comparer les chaînes brutes ferait de « Beau » un sens de
 * « Beaujoire ».
 *
 * En cas de doute, la règle **laisse passer** : un rang de trop se lit et se
 * corrige d'un coup d'œil, un tram effacé du tableau ne se voit pas.
 */
fun ServingLine.serves(departure: StopDeparture): Boolean {
    if (normalizeStopName(line) != normalizeStopName(departure.line)) return false
    val mine = normalizeStopName(direction).split(' ').filter { it.isNotEmpty() }.toSet()
    if (mine.isEmpty()) return false

    return listOfNotNull(departure.directionLabel, departure.destination).any { label ->
        val theirs = normalizeStopName(label).split(' ').filter { it.isNotEmpty() }.toSet()
        theirs.isNotEmpty() && (theirs.containsAll(mine) || mine.containsAll(theirs))
    }
}

/**
 * Les passages annoncés pour **cette desserte-là**, dans l'ordre.
 *
 * Un sens qui ne reconnaît rien rend une liste vide, et c'est une réponse : la
 * nuit, une desserte n'a aucun passage annoncé et garde toute sa grille théorique.
 */
fun StopDepartures.matching(target: ScheduleTarget): List<StopDeparture> {
    val sieve = target.sieve
    return departures.filter { sieve.serves(it) }.sortedBy { it.expectedAt }
}

/**
 * Toutes les graphies sous lesquelles le référentiel écrit ce lieu, [named] en tête.
 *
 * ⚠️ **Deux pôles majeurs y sont écrits de deux façons** — `Haluchère - Batignolles`
 * et `Haluchère-Batignolles`, `Foch - Cathédrale` et `Foch-Cathédrale`. Le serveur
 * cherche `gtfs_stops` sur l'union des noms qu'on lui donne : n'en envoyer qu'un
 * perd la moitié des quais du pôle, et la grille s'affiche à moitié vide sans que
 * rien ne dise pourquoi.
 *
 * Le nom demandé vient **en premier** et n'est jamais écarté, même absent du
 * catalogue : c'est celui qui intitule la réponse du serveur.
 */
fun List<TransitStop>.spellingsOfPlace(named: String): List<String> {
    val wanted = normalizeStopName(named)
    val seen = mutableSetOf(named)
    val found = mutableListOf(named)
    for (stop in this) {
        val candidate = stop.departuresKey
        if (normalizeStopName(candidate) == wanted && seen.add(candidate)) found += candidate
    }
    return found
}

/**
 * Une ligne, et tous les sens qu'elle prend depuis cet arrêt.
 *
 * Le serveur annonce **une entrée par direction** : « 75 vers Charbonneau » et
 * « 75 vers Facultés » y sont deux lignes distinctes. Rendues telles quelles,
 * quatre lignes en occupent sept, chaque badge paraît deux fois, et il faut
 * relire la colonne entière pour répondre à la seule question qu'on se pose
 * devant un poteau — *qu'est-ce qui passe ici ?*
 */
data class ServingLineGroup(
    val line: String,
    val lineColor: String? = null,
    val mode: TransportMode? = null,
    val directions: List<String> = emptyList(),
) {
    val id: String get() = line
}

/**
 * Regroupe les sens sous leur ligne, **en gardant l'ordre du serveur**.
 *
 * Il classe déjà par mode puis par indice de ligne ; retrier ici ferait passer
 * « 10 » avant « 2 » à la première comparaison de chaînes, et personne ne
 * cherche sa ligne dans un ordre qui n'est celui de rien.
 */
fun List<ServingLine>.groupedByLine(): List<ServingLineGroup> {
    val order = mutableListOf<String>()
    val groups = mutableMapOf<String, ServingLineGroup>()
    for (serving in this) {
        val key = normalizeStopName(serving.line)
        val existing = groups[key]
        if (existing == null) {
            order += key
            groups[key] = ServingLineGroup(
                line = serving.line,
                lineColor = serving.lineColor,
                mode = serving.mode,
                directions = listOfNotNull(serving.direction.takeIf { it.isNotBlank() }),
            )
        } else if (serving.direction.isNotBlank() &&
            existing.directions.none { normalizeStopName(it) == normalizeStopName(serving.direction) }
        ) {
            groups[key] = existing.copy(
                // La couleur manque parfois sur une entrée et pas sur l'autre :
                // la première renseignée vaut mieux qu'un badge gris.
                lineColor = existing.lineColor ?: serving.lineColor,
                mode = existing.mode ?: serving.mode,
                directions = existing.directions + serving.direction,
            )
        }
    }
    return order.mapNotNull { groups[it] }
}
