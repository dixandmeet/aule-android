package io.aule.android.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * La grille théorique d'une ligne à un arrêt, pour une date.
 *
 * C'est l'autre moitié de ce qu'un arrêt sait dire. [StopDepartures] annonce ce
 * qui **arrive** — mesuré, court, périssable, et muet passé trois heures.
 * La grille annonce ce qui est **prévu** : la journée entière, y compris ce qui
 * est déjà passé, et y compris un jour qui n'est pas encore arrivé. On ne s'en
 * sert pas pour la même chose — l'une répond à « je pars maintenant », l'autre
 * à « à quelle heure faut-il que je parte ».
 *
 * Les deux ne se fondent pas en une seule liste, et c'est délibéré : rien ne
 * permet d'affirmer, de l'extérieur, quel passage théorique correspond à quel
 * passage mesuré. Les rapprocher à l'heure la plus proche donnerait une liste
 * d'apparence exacte dont une entrée sur dix mentirait.
 *
 * @param passages les heures de passage, dans l'ordre. Un service qui déborde
 *   après minuit tombe le lendemain — GTFS compte les secondes depuis le minuit
 *   du service, et « 25:10 » est une heure valide qui veut dire 1 h 10.
 */
data class Timetable(
    val date: LocalDate,
    val line: String,
    val destination: String,
    val stopName: String,
    val passages: List<Instant> = emptyList(),
) {
    val isEmpty: Boolean get() = passages.isEmpty()

    /** Le prochain passage prévu à partir de [from], s'il en reste un ce jour-là. */
    fun next(from: Instant): Instant? = passages.firstOrNull { !it.isBefore(from) }
}

/**
 * Pourquoi une grille n'a pas pu être rendue.
 *
 * Trois causes, trois écrans différents : une session manquante se règle en se
 * reconnectant, une ligne introuvable au catalogue est une donnée absente
 * qu'aucune attente ne corrigera, et une panne réseau s'attend. Les confondre
 * ferait proposer « réessayez » à quelqu'un dont le problème est ailleurs.
 */
enum class TimetableFailureKind {
    /** Pas de session, ou une session que la base refuse. */
    NOT_SIGNED_IN,

    /** Supabase n'est pas configuré dans cette variante de l'application. */
    NOT_CONFIGURED,

    /** La ligne ou l'arrêt n'existe pas dans le catalogue publié. */
    NOT_IN_CATALOG,

    /** Le réseau, ou la base, n'a pas répondu. */
    UNAVAILABLE,
}

class TimetableException(
    val kind: TimetableFailureKind,
    cause: Throwable? = null,
) : Exception(kind.name, cause)
