package io.aule.android.core.model

/**
 * Le nuancier des lignes : quel numéro s'écrit dans quelle couleur.
 *
 * Une ligne se reconnaît à sa teinte avant qu'on ait lu son numéro — c'est vrai
 * du plan papier, des girouettes et des abribus. Les passages d'un arrêt
 * arrivent déjà coloriés par le BFF ; le flux de flotte, lui, n'annonce qu'un
 * `route_id`, et le badge d'un véhicule restait donc gris alors que la couleur
 * existe, une table plus loin.
 *
 * ## Pourquoi un index et pas une carte nue
 *
 * L'identifiant qui sert de clé ne vient pas de la même source que celui qui
 * sert de valeur : le flux de flotte écrit « C6 » là où le catalogue GTFS peut
 * écrire « c6 » ou l'entourer d'espaces. Une différence de casse ne doit pas
 * coûter une couleur, et c'est le genre de correspondance qu'on ne veut écrire
 * qu'une fois.
 */
data class LinePalette(val colors: Map<String, String> = emptyMap()) {

    /**
     * Les couleurs retenues, indexées sur une clé normalisée.
     *
     * Une couleur vide vaut une couleur absente : le badge sait déjà quoi faire
     * d'un identifiant sans teinte — il prend son gris de repli — alors qu'une
     * chaîne blanche lui donnerait un aplat noir.
     */
    private val index: Map<String, String> = buildMap {
        colors.forEach { (line, color) ->
            val key = normalize(line) ?: return@forEach
            val value = color.trim().takeIf { it.isNotEmpty() } ?: return@forEach
            put(key, value)
        }
    }

    /** La couleur d'une ligne, ou rien — jamais une couleur inventée. */
    fun colorOf(lineId: String?): String? = normalize(lineId)?.let { index[it] }

    val isEmpty: Boolean get() = index.isEmpty()

    private fun normalize(lineId: String?): String? =
        lineId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    companion object {
        /** Aucune couleur connue : les badges gardent leur gris. */
        val EMPTY = LinePalette()
    }
}
