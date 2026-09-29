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

    /**
     * La couleur d'une ligne dont on connaît le mode.
     *
     * ⚠️ **Un train n'emprunte pas la couleur du bus qui porte son numéro.** Le TER C2
     * et le Chronobus C2 s'écrivent pareil ; un numéro nu donne le Chronobus
     * (voir [of]), et c'est le mode qui fait chercher d'abord la clé `aleop:`.
     */
    fun colorOf(lineId: String?, mode: TransportMode?): String? {
        val id = lineId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (mode == TransportMode.TER && ':' !in id) {
            colorOf(transitLineKey(TransitNetwork.ALEOP, id))?.let { return it }
        }
        return colorOf(id)
    }

    val isEmpty: Boolean get() = index.isEmpty()

    private fun normalize(lineId: String?): String? =
        lineId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    companion object {
        /** Aucune couleur connue : les badges gardent leur gris. */
        val EMPTY = LinePalette()

        /**
         * Le nuancier de l'index des lignes, **indexé par clé** `réseau:MATCH`.
         *
         * ## ⚠️ Pourquoi pas `name to color`
         *
         * C2, C4, C6 et C7 sont à la fois des Chronobus et des TER, et l'index range
         * les trains **après** les bus : une table par indice, où la dernière entrée
         * l'emporte, peignait le Chronobus C2 du bleu TER. Chaque ligne est donc
         * rangée sous sa clé, sous ses `route_id` — ce que porte une position
         * théorique —, et sous son numéro nu **Naolib d'abord** : un numéro sans
         * réseau est celui du réseau urbain.
         */
        fun of(lines: List<TransitLine>): LinePalette {
            val colors = LinkedHashMap<String, String>()
            val seen = HashSet<String>()
            fun keep(id: String, color: String) {
                // La première couleur d'une clé l'emporte, sans casse : c'est la règle
                // de l'index, et `LinePalette` compare sans casse.
                if (seen.add(id.trim().lowercase())) colors[id] = color
            }
            lines.forEach { line -> line.colorHex?.let { keep(line.key, it) } }
            lines.forEach { line -> line.colorHex?.let { color -> line.routeIds.forEach { keep(it, color) } } }
            lines.sortedBy { if (it.network == TransitNetwork.NAOLIB) 0 else 1 }
                .forEach { line -> line.colorHex?.let { keep(canonicalLineName(line.name), it) } }
            return LinePalette(colors)
        }
    }
}
