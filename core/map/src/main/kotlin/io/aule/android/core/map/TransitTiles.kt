package io.aule.android.core.map

import java.io.File

/**
 * D'où viennent les tracés du réseau, et comment ils s'appellent dans l'archive.
 *
 * **Une archive embarquée, pas une requête.** Les tronçons du réseau tiennent
 * dans 3,4 Mo de tuiles vectorielles produites une fois pour toutes
 * (`dashboard/tools/tiles/build-transit.sh`) : géométrie, couleur et rang de
 * densité y sont calculés au build, jamais à l'exécution. C'est le même fichier
 * que la carte web et l'application iOS, **au bit près** — voir
 * `app/src/main/assets/tiles/README.md`.
 *
 * L'embarquer plutôt que d'aller le chercher tient la même promesse que les deux
 * styles : le réseau s'affiche dans un tunnel comme en zone blanche, et un
 * premier affichage ne commence pas par attendre 3,4 Mo.
 */
object TransitTiles {

    /**
     * Le nom de la couche des tracés dans l'archive (`--named-layer` de
     * tippecanoe). Il est partagé avec le web (`TRANSIT_SOURCE_LAYER` de
     * `style/tile-source.ts`) et l'iOS : il change des deux côtés ou d'aucun.
     */
    const val LINES_SOURCE_LAYER = "transit_lines"

    /** Le chemin de l'archive dans les assets. */
    const val ASSET_PATH = "tiles/transit.pmtiles"

    /** Le nom sous lequel elle est recopiée — le même, pour qu'on la reconnaisse. */
    const val CACHED_FILE_NAME = "transit.pmtiles"

    /**
     * L'URL que MapLibre attend — et **la seule qui peigne quoi que ce soit**.
     *
     * ⚠️ La forme nue, `pmtiles:///chemin/vers/archive.pmtiles`, est acceptée
     * sans erreur et reste muette. Seul marche `pmtiles://` suivi d'une URL
     * `file://` dont le chemin est **percent-encodé**. Le piège a déjà été payé
     * deux fois — côté Flutter, où le dossier « Application Support » contient
     * une espace, puis côté iOS. Sur Android, `filesDir` d'une application
     * d'entreprise peut vivre sous un profil professionnel dont le chemin
     * contient une espace ou un accent, et le défaut serait le même : une carte
     * nue, sans erreur, sans tuile manquante.
     *
     * ## Pourquoi un fichier recopié plutôt que l'asset directement
     *
     * `pmtiles://asset://tiles/transit.pmtiles` serait plus court et éviterait
     * la copie. On ne sait **pas** si le lecteur PMTiles du binaire natif
     * délègue son URL interne au lecteur d'assets : le `.so` embarque bien
     * `PMTilesFileSource` et `AssetManagerFileSource`, mais rien dans ses
     * symboles ne dit que le premier passe par le second. La forme `file://`,
     * elle, est celle que l'iOS a prouvée et que le lecteur de fichiers local
     * garantit. La copie coûte 3,4 Mo une fois, au premier lancement.
     *
     * C'est une optimisation à reprendre le jour où quelqu'un peut essayer
     * l'autre forme sur un appareil — voir le § Lot 2 du plan de rattrapage.
     */
    fun pmtilesUrl(file: File): String = "pmtiles://" + fileUrl(file.absolutePath)

    /**
     * `file://` + chemin percent-encodé, à la façon d'`URL.absoluteString`.
     *
     * Écrit à la main plutôt que par `File.toURI()` : ce dernier laisse passer
     * l'espace en `%20` mais **pas** les autres caractères réservés de la même
     * façon, et surtout il n'échappe pas ce que le lecteur natif attend. La
     * fonction est pure, donc vérifiable — c'est le seul endroit du lot où une
     * faute ne produit aucun message.
     */
    internal fun fileUrl(path: String): String {
        val encoded = buildString(path.length) {
            for (byte in path.toByteArray(Charsets.UTF_8)) {
                val value = byte.toInt() and 0xFF
                val ch = value.toChar()
                if (ch.isUnreservedInPath()) {
                    append(ch)
                } else {
                    append('%')
                    append(HEX[value shr 4])
                    append(HEX[value and 0x0F])
                }
            }
        }
        return "file://$encoded"
    }

    /**
     * Les caractères qu'un chemin garde tels quels.
     *
     * La liste est celle de la RFC 3986 pour un segment de chemin, **plus la
     * barre oblique** — c'est un chemin, ses séparateurs doivent le rester. Tout
     * le reste s'échappe, y compris l'espace et les accents.
     */
    private fun Char.isUnreservedInPath(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' ||
            this == '-' || this == '.' || this == '_' || this == '~' || this == '/'

    private val HEX = "0123456789ABCDEF".toCharArray()
}
