package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'inventaire des lignes, lu et rangé.
 *
 * Port de `TransitLineTests`. Ce qui se vérifie ici n'est pas qu'un JSON se
 * décode, mais qu'une ligne tombe dans la bonne famille — c'est cette règle-là
 * qui décide de l'ordre du volet, et elle est dérivée, donc réécrivable par
 * mégarde.
 */
class TransitLineTest {

    private fun line(
        name: String,
        mode: TransportMode? = TransportMode.BUS,
        network: TransitNetwork? = TransitNetwork.NAOLIB,
        headsigns: List<String> = emptyList(),
    ) = TransitLine(name = name, mode = mode, network = network, headsigns = headsigns)

    @Test
    fun `l indice se canonise comme le web`() {
        assertEquals("C6", canonicalLineName(" c6 "))
        assertEquals("C6", canonicalLineName("C6"))
        // Sans cette règle, « c6 » et « C6 » désigneraient deux lignes, et la
        // jointure avec les tuiles — qui portent l'indice en majuscules — casserait.
        assertEquals(line("c6").match, line("C6").match)
    }

    @Test
    fun `le reseau decide avant l indice`() {
        // « E311 » est un car Aléop, « E1 » une ligne express urbaine, et les deux
        // commencent par la même lettre.
        assertEquals(
            TransitLineFamily.INTERURBAN,
            line("E311", network = TransitNetwork.ALEOP).family,
        )
        assertEquals(TransitLineFamily.EXPRESS, line("E1").family)
    }

    @Test
    fun `une lettre suivie de chiffres et rien d autre`() {
        assertEquals(TransitLineFamily.CHRONOBUS, line("C6").family)
        assertEquals(TransitLineFamily.CHRONOBUS, line("C20").family)
        // « C » seul et « NGG » ne sont pas des Chronobus : la règle demande des
        // chiffres après la lettre, et rien d'autre.
        assertEquals(TransitLineFamily.BUS, line("C").family)
        assertEquals(TransitLineFamily.BUS, line("NGG").family)
        assertEquals(TransitLineFamily.BUS, line("C6A").family)
    }

    @Test
    fun `le mode passe avant la lettre`() {
        assertEquals(TransitLineFamily.TRAM, line("1", mode = TransportMode.TRAM).family)
        assertEquals(TransitLineFamily.NAVIBUS, line("N1", mode = TransportMode.BOAT).family)
        // Mode inconnu : la ligne se range comme un bus, pas nulle part.
        assertEquals(TransitLineFamily.BUS, line("42", mode = null).family)
    }

    @Test
    fun `l indice se cherche par le debut et le terminus par le contenu`() {
        val tram3 = line("3", mode = TransportMode.TRAM, headsigns = listOf("Neustrie > Marcel Paul"))
        val bus23 = line("23", headsigns = listOf("Beaujoire > Commerce"))

        // « 3 » cherché dans le contenu remonterait les trente-neuf lignes qui
        // portent un 3 quelque part, dont le tram 3 noyé au milieu.
        assertTrue(tram3.matches("3"))
        assertFalse(bus23.matches("3"))

        // Un terminus se retient rarement par son premier mot.
        assertTrue(bus23.matches("commerce"))
        assertTrue(tram3.matches("marcel"))
    }

    @Test
    fun `la recherche de terminus ignore les accents`() {
        val l = line("42", headsigns = listOf("Gétigné > Nantes"))
        assertTrue(l.matches("getigne"))
        assertTrue(l.matches("Gétigné"))
    }

    @Test
    fun `une requete vide retient tout`() {
        // C'est l'état au repos du volet, pas un filtre.
        assertTrue(line("C6").matches(""))
        assertTrue(line("C6").matches("   "))
    }

    @Test
    fun `un bbox GeoJSON se lit ouest sud est nord`() {
        val bounds = transitLineBoundsFromGeoJson(listOf(-1.62042, 47.21236, -1.53368, 47.38805))

        assertEquals(Coordinate(latitude = 47.21236, longitude = -1.62042), bounds?.southWest)
        assertEquals(Coordinate(latitude = 47.38805, longitude = -1.53368), bounds?.northEast)
    }

    @Test
    fun `des coins a l envers ou hors bornes sont refuses`() {
        // Nord avant sud, est avant ouest : le cadre est retourné.
        assertNull(transitLineBoundsFromGeoJson(listOf(-1.5, 47.4, -1.6, 47.2)))
        // Hors bornes — une latitude de 147 n'existe pas.
        assertNull(transitLineBoundsFromGeoJson(listOf(-1.6, 147.2, -1.5, 147.4)))
        // Un tableau incomplet.
        assertNull(transitLineBoundsFromGeoJson(listOf(-1.6, 47.2, -1.5)))
        assertNull(transitLineBoundsFromGeoJson(emptyList()))
    }

    @Test
    fun `une transposition latitude longitude n est pas detectable ici`() {
        // Ce test **documente une limite**, il ne célèbre pas un comportement.
        // Sur Nantes, échanger latitude et longitude donne des valeurs qui
        // restent toutes deux dans les bornes et dans le bon ordre : le contrôle
        // générique ne peut rien en dire. C'est le test sur le vrai fichier
        // (`TransitLineIndexAssetTest`) qui attrape cette erreur-là, en regardant
        // **où** tombent les cadres.
        val transposed = transitLineBoundsFromGeoJson(
            listOf(47.21236, -1.62042, 47.38805, -1.53368),
        )
        assertEquals(-1.62042, transposed?.southWest?.latitude)
    }

    @Test
    fun `les lignes se rangent comme dans le Finder`() {
        val digest = NetworkLinesDigest.build(
            listOf(line("C20"), line("C3"), line("C1"), line("C10")),
        )

        // Sans le tri naturel, « C10 » et « C20 » se rangeraient avant « C3 ».
        assertEquals(
            listOf("C1", "C3", "C10", "C20"),
            digest.sections.single().lines.map { it.name },
        )
    }

    @Test
    fun `les sections suivent l ordre de lecture et les vides disparaissent`() {
        val digest = NetworkLinesDigest.build(
            listOf(
                line("E311", network = TransitNetwork.ALEOP),
                line("12"),
                line("1", mode = TransportMode.TRAM),
                line("C6"),
            ),
        )

        // Le structurant d'abord, l'interurbain en dernier. Pas de section
        // « Navibus » : une section vide sous un en-tête se lit comme une panne.
        assertEquals(
            listOf(
                TransitLineFamily.TRAM,
                TransitLineFamily.CHRONOBUS,
                TransitLineFamily.BUS,
                TransitLineFamily.INTERURBAN,
            ),
            digest.sections.map { it.family },
        )
        assertEquals(4, digest.count)
    }

    @Test
    fun `une entree sans indice est sautee et les autres restent`() {
        // L'index est décodé d'un bloc : une seule entrée fatale emporterait les
        // 137 autres, et avec elles la couleur de tous les badges.
        val raw = """
            [
              {"line":"C6","color":"#FF0000","mode":"bus","network":"naolib"},
              {"color":"#00FF00"},
              {"line":"  "},
              {"line":"3","mode":"tram","bbox":"pas un tableau"},
              {"line":"N1","mode":"ferry","headsigns":["Trentemoult > Gare Maritime"]}
            ]
        """.trimIndent()

        val lines = decodeTransitLineIndex(raw)

        assertEquals(listOf("C6", "3", "N1"), lines.map { it.name })
        // Un champ qu'on ne sait pas lire vaut `null`, il n'emporte pas la ligne.
        assertNull(lines[1].bounds)
        assertEquals(TransportMode.TRAM, lines[1].mode)
        assertEquals(TransportMode.BOAT, lines[2].mode)
    }

    @Test
    fun `un index illisible rend une liste vide plutot que de lever`() {
        assertTrue(decodeTransitLineIndex(null).isEmpty())
        assertTrue(decodeTransitLineIndex("").isEmpty())
        assertTrue(decodeTransitLineIndex("ceci n'est pas du JSON").isEmpty())
        assertTrue(decodeTransitLineIndex("""{"line":"pas un tableau"}""").isEmpty())
    }
}
