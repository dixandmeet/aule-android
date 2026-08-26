package io.aule.android.data.aule

import io.aule.android.core.model.normalizeStopName
import io.aule.android.data.dto.GtfsCalendarDateDto
import io.aule.android.data.dto.GtfsCalendarDto
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ce que deux lecteurs du catalogue GTFS ont en commun.
 *
 * La relève cherche la course qu'un collègue est en train de rouler ; la grille
 * horaire cherche tout ce qui passe à un arrêt dans une journée. Les deux
 * posent d'abord les mêmes questions — *quels services roulent ce jour-là*, et
 * *ce panneau de destination désigne-t-il la direction demandée* — et une
 * réponse recopiée finit toujours par diverger. Une grille horaire en désaccord
 * avec la course affichée serait le pire des deux mondes.
 *
 * Seules les décisions vivent ici : les requêtes restent chez chaque
 * repository, avec ses erreurs à lui.
 */

/**
 * Le fuseau du réseau, et non celui de l'appareil.
 *
 * GTFS compte ses secondes depuis le minuit **du service**, dans le fuseau de
 * l'exploitant. Un téléphone réglé sur un autre fuseau — cela arrive en
 * voyage, ou parce que quelqu'un l'a mis à la main — décalerait toute la
 * grille sans rien signaler.
 */
internal val NETWORK_ZONE: ZoneId = ZoneId.of("Europe/Paris")

/**
 * Les services qui roulent à cette date.
 *
 * Le calendrier régulier donne les jours de la semaine ; les exceptions
 * ajoutent (type 1) ou retirent (type 2) une date précise. Un jour férié se lit
 * dans les secondes, jamais dans le premier — et c'est exactement le jour où
 * une grille fausse se remarque.
 */
internal fun activeServiceIds(
    regular: List<GtfsCalendarDto>,
    exceptions: List<GtfsCalendarDateDto>,
    date: LocalDate,
): List<String> {
    val weekday = date.dayOfWeek.value
    val removed = exceptions.filter { it.exceptionType == 2 }.map { it.serviceId }.toSet()
    val added = exceptions.filter { it.exceptionType == 1 }.map { it.serviceId }.toSet()
    val active = mutableSetOf<String>()
    for (row in regular) {
        val runsToday = row.runsOn.size >= weekday && row.runsOn[weekday - 1]
        if (runsToday && row.serviceId !in removed) active += row.serviceId
    }
    active += added
    return active.toList()
}

/**
 * À quel point ce panneau de destination répond à la direction demandée.
 *
 * Zéro veut dire « rien à voir », et c'est le seul verdict qui compte vraiment :
 * le reste sert à départager quand plusieurs directions se ressemblent.
 * « Fac de Droit » et « Faculté de Droit » désignent le même terminus,
 * « Hermeland » et « Chantrerie » non.
 */
internal fun directionScore(headsign: String?, requested: String): Int {
    val actual = normalizeStopName(headsign.orEmpty())
    if (actual.isEmpty()) return 0
    val expected = normalizeStopName(requested)
    if (expected.isEmpty()) return 0
    if (actual == expected) return 100
    if (actual.contains(expected) || expected.contains(actual)) return 80
    val aliases = requested.split('/', '|')
        .map { normalizeStopName(it) }
        .filter { it.length >= 3 }
    for (alias in aliases) {
        if (actual.contains(alias) || alias.contains(actual)) return 70
    }
    return 0
}

/**
 * PostgREST plafonne ses réponses ; une desserte complète les dépasse.
 *
 * La dernière page est celle qui n'est pas pleine. Sans cette boucle, une ligne
 * à forte fréquence perdait ses derniers départs en silence — le pire genre de
 * perte, puisque la liste avait l'air complète.
 */
internal suspend fun <T> fetchAllPages(
    pageSize: Int = 1000,
    fetch: suspend (offset: Int, limit: Int) -> List<T>,
): List<T> {
    val all = mutableListOf<T>()
    var offset = 0
    while (true) {
        val page = fetch(offset, pageSize)
        all += page
        if (page.size < pageSize) break
        offset += pageSize
    }
    return all
}
