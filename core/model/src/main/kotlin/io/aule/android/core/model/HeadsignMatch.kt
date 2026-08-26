package io.aule.android.core.model

/**
 * Reconnaître qu'une course et un passage parlent du même véhicule.
 *
 * Le temps réel ne porte **aucun identifiant de course**, et les deux bouts de
 * l'API ne nomment pas les mêmes objets : les passages désignent une course par
 * `ligne-sens-heure-rang`, le radar désigne une position par son propre
 * identifiant. Deux espaces disjoints. Il ne reste que l'indice de ligne, les
 * libellés de terminus et l'heure.
 *
 * Ces trois fonctions sont donc **le** vocabulaire de l'appariement, et elles
 * vivent ici plutôt que dans `:core:guet` : la relève, le plan de ligne d'un
 * véhicule et le Guet posent la même question, et trois écritures de la même
 * règle divergeraient au premier ajustement.
 *
 * Port de `Native/Aule/Models/HeadsignMatch.swift`.
 */
object HeadsignMatch {

    /**
     * Comparaison insensible à la casse, aux accents et aux espaces de bord.
     *
     * « Hôtel Dieu » et « HOTEL DIEU » désignent le même terminus, et les deux
     * formes circulent — la première dans le GTFS, la seconde dans certains flux
     * temps réel.
     *
     * C'est [normalizeStopName] qui replie, et non une seconde règle écrite ici :
     * deux replis différents dans la même application finiraient par rendre des
     * réponses différentes à la même question.
     */
    fun folded(text: String): String = normalizeStopName(text)

    /**
     * La clé de ligne comparable : **ce qui suit le dernier `:`**, puis normalisé.
     *
     * ⚠️ Le théorique porte `ALEOP:300`, le suivi porte `300` — comparer les
     * identifiants bruts n'apparie **jamais** rien, et l'écran reste vide sans
     * qu'aucune erreur ne soit levée. C'est écrit noir sur blanc dans le contrat
     * BFF (§2), et c'est le genre de piège qu'on ne trouve qu'en le cherchant.
     */
    fun routeKey(line: String): String = folded(line.substringAfterLast(':'))

    /**
     * Les deux libellés désignent-ils le même terminus.
     *
     * @param mine le libellé de la course — le terminus d'un profil, la
     *   destination d'un véhicule.
     * @param theirs les libellés du passage, du plus précis au plus large :
     *   destination puis libellé de direction. Il en faut **deux** : mesuré côté
     *   iOS le 18/08/2026, la ligne 1 à Bouffay annonce
     *   `direction: "Beaujoire / Babinière"` et `destination: "Babinière"`. Le
     *   second nomme la branche, le premier les réunit ; n'en garder qu'un rendait
     *   la moitié des appariements impossibles.
     */
    fun headsign(mine: String?, matchesAnyOf: List<String>): Boolean {
        val folded = folded(mine.orEmpty())
        if (folded.isEmpty()) return false
        return matchesAnyOf.any { candidate ->
            val other = folded(candidate)
            other.isNotEmpty() &&
                (folded == other || folded.contains(other) || other.contains(folded))
        }
    }

    /** Ligne **et** destination, la règle complète. */
    fun course(
        line: String,
        headsign: String?,
        matchesLine: String,
        destinations: List<String>,
    ): Boolean {
        if (routeKey(line) != routeKey(matchesLine)) return false
        return headsign(headsign, destinations)
    }
}
