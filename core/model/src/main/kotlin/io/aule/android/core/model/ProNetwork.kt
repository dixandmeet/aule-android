package io.aule.android.core.model

import java.text.Normalizer

/**
 * Un réseau de transport auquel un compte Aule Pro se rattache.
 *
 * À ne pas confondre avec [TransitNetwork], qui dit sous quelle **marque** une
 * ligne circule — Naolib ou Aléop sur le même territoire. Ici, la question est
 * « pour qui travaillez-vous ? », et sa réponse est un employeur, pas un
 * cartouche de ligne.
 *
 * [name] et [territory] sont des **noms propres**, pas des formulations du
 * domaine : ADR-011 range dans la vue la façon dont l'application *parle*, pas
 * la façon dont le monde *s'appelle*. Naolib s'écrit Naolib en anglais, et le
 * catalogue GTFS pose déjà ses libellés de ligne dans le modèle
 * ([ServiceLine.label]).
 *
 * [searchTerms] porte ce qu'on tape et qui n'est écrit nulle part sur la carte
 * du réseau : l'exploitant, l'ancien nom, la ville seule, le département. Sans
 * eux, un conducteur nantais qui tape « tan » — le nom sous lequel il travaille
 * depuis quinze ans — ne trouve pas son propre réseau, et conclut qu'Aule ne le
 * couvre pas.
 */
data class ProNetwork(
    val key: String,
    val name: String,
    val territory: String,
    val searchTerms: List<String> = emptyList(),
) {
    /** L'initiale, pour l'emblème de repli quand on n'a pas le logo. */
    val initial: String
        get() = name.take(1).uppercase()

    /**
     * La requête est cherchée **dans** chaque terme et non en tête.
     *
     * On ne connaît pas le début d'un nom qu'on cherche justement parce qu'on
     * ne le connaît pas : « métropole » doit ramener « Nantes Métropole ».
     */
    fun matches(query: String): Boolean {
        val needle = query.fold()
        if (needle.isEmpty()) return true
        return (searchTerms + name + territory).any { needle in it.fold() }
    }
}

/**
 * Les réseaux qu'on peut choisir aujourd'hui.
 *
 * Un seul, et c'est une information en soi : voir [NETWORK_SEARCH_FROM].
 */
val SIGNUP_NETWORKS: List<ProNetwork> = listOf(
    ProNetwork(
        key = ProRegistrationDraft.NAOLIB_NETWORK_KEY,
        name = "Naolib",
        territory = "Nantes Métropole",
        searchTerms = listOf("Nantes", "TAN", "Semitan", "Loire-Atlantique", "44"),
    ),
)

/**
 * Le nombre de réseaux à partir duquel un champ de recherche a un sens.
 *
 * Chercher dans une liste d'un élément, c'est taper pour obtenir ce qui est
 * déjà à l'écran. Le champ ne faisait pas que ne servir à rien : il posait la
 * question « lequel ? » au-dessus d'une réponse unique, et repoussait cette
 * réponse d'une hauteur de champ vers le bas. Il revient tout seul le jour où
 * le catalogue s'ouvre — c'est le seuil, pas la main, qui décide.
 *
 * Six : la longueur au-delà de laquelle une liste cesse de se balayer d'un
 * coup d'œil et commence à se parcourir.
 */
const val NETWORK_SEARCH_FROM: Int = 6

/** Les réseaux que retient la requête, dans l'ordre du catalogue. */
fun signupNetworks(query: String): List<ProNetwork> =
    SIGNUP_NETWORKS.filter { it.matches(query) }

/**
 * Minuscules **et accents retirés**.
 *
 * Un clavier de téléphone tenu d'une main dans un dépôt ne met pas les
 * accents ; une recherche qui les exige rend le champ inutilisable sur la
 * moitié des noms de villes françaises. La décomposition NFD sépare la lettre
 * de son signe, il ne reste qu'à jeter le signe.
 */
private fun String.fold(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()

private val COMBINING_MARKS = Regex("\\p{Mn}+")
