package io.aule.android.core.map

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'URL de l'archive PMTiles.
 *
 * C'est le seul endroit du lot où une faute **ne produit aucun message** : une
 * URL mal formée est acceptée par MapLibre, qui ne peint alors rien du tout — ni
 * erreur, ni tuile manquante, seulement une carte nue. Le piège a déjà été payé
 * deux fois, côté Flutter puis côté iOS, sur des chemins contenant une espace.
 */
class TransitTilesTest {

    @Test
    fun `un chemin ordinaire garde ses barres obliques`() {
        // Les séparateurs restent des séparateurs : les échapper donnerait un
        // chemin d'un seul segment, que le lecteur natif ne trouverait pas.
        assertEquals(
            "file:///data/user/0/io.aule.android/files/transit.pmtiles",
            TransitTiles.fileUrl("/data/user/0/io.aule.android/files/transit.pmtiles"),
        )
    }

    @Test
    fun `une espace dans le chemin est percent-encodee`() {
        // Le défaut historique. Sur Android, un profil professionnel peut poser
        // `filesDir` sous un chemin qui en contient une.
        assertEquals(
            "file:///data/Mon%20Profil/transit.pmtiles",
            TransitTiles.fileUrl("/data/Mon Profil/transit.pmtiles"),
        )
    }

    @Test
    fun `les accents partent en UTF-8 percent-encode`() {
        // « é » vaut deux octets en UTF-8, donc deux groupes de pourcentage.
        assertEquals(
            "file:///data/donn%C3%A9es/transit.pmtiles",
            TransitTiles.fileUrl("/data/données/transit.pmtiles"),
        )
    }

    @Test
    fun `les caracteres non reserves restent lisibles`() {
        // Un chemin illisible est un chemin qu'on ne reconnaît pas dans un
        // journal : on n'échappe que ce qui doit l'être.
        assertEquals(
            "file:///a-b_c.d~e/f0/transit.pmtiles",
            TransitTiles.fileUrl("/a-b_c.d~e/f0/transit.pmtiles"),
        )
    }

    @Test
    fun `les caracteres reserves d une URL sont echappes`() {
        val url = TransitTiles.fileUrl("/data/a?b#c/transit.pmtiles")

        // Sans échappement, « ? » ouvrirait une chaîne de requête et « # » un
        // fragment : le chemin serait tronqué avant le nom du fichier.
        assertFalse('?' in url)
        assertFalse('#' in url)
        assertEquals("file:///data/a%3Fb%23c/transit.pmtiles", url)
    }

    @Test
    fun `l URL PMTiles enveloppe une URL de fichier et non un chemin nu`() {
        val url = TransitTiles.pmtilesUrl(File("/data/files/transit.pmtiles"))

        // ⚠️ La forme nue `pmtiles:///chemin` est acceptée sans erreur et reste
        // muette. Le `file://` intérieur n'est pas décoratif.
        assertEquals("pmtiles://file:///data/files/transit.pmtiles", url)
        assertTrue(url.startsWith("pmtiles://file://"))
    }

    @Test
    fun `le nom de la couche source est celui que partagent les trois cartes`() {
        // Il change des deux côtés ou d'aucun : c'est la clé de jointure avec
        // l'archive, et une faute ici rend une couche vide sans le dire.
        assertEquals("transit_lines", TransitTiles.LINES_SOURCE_LAYER)
        assertEquals("tiles/transit.pmtiles", TransitTiles.ASSET_PATH)
    }
}
