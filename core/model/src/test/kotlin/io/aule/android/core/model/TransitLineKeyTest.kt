package io.aule.android.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'identité d'une ligne : `réseau:MATCH`, et plus l'indice seul.
 *
 * Le TER SNCF est rangé dans le réseau Aléop, et quatre de ses numéros — C2, C4, C6, C7 — sont
 * aussi ceux de Chronobus Naolib ; P2 et P5 désignent chacun deux lignes TER. Ce qui se vérifie
 * ici, sur un extrait **fabriqué** de l'index (et non l'asset embarqué, qui change à chaque
 * build des tuiles) : deux homonymes ne se confondent nulle part.
 */
class TransitLineKeyTest {

    /** Un extrait de `transit-lines-index.json`, dans l'ordre du vrai fichier : les bus avant les trains. */
    private val index = decodeTransitLineIndex(
        """
        [
          {"line":"C2","color":"#ee7402","mode":"bus","network":"naolib","headsigns":["Le Cardo","Gare Sud"]},
          {"line":"C6","color":"#a877b2","mode":"bus","network":"naolib","headsigns":["Hermeland","Chantrerie - Grandes Ecoles"]},
          {"line":"E309","color":"#0075BF","mode":"bus","network":"aleop","headsigns":["Nantes"],"routes":["ALEOP:309"]},
          {"line":"P2","color":"#006600","mode":"rail","network":"aleop","match":"P2 RENNES - VANNES",
           "headsigns":["Rennes","Vannes"],"routes":["ALEOP:TER:FR:Line::4BBFA233-37AB-432A-BCCF-FB90719B1F8A:"]},
          {"line":"P2","color":"#006600","mode":"rail","network":"aleop",
           "headsigns":["Saint-Nazaire","Nantes"],"routes":["ALEOP:TER:FR:Line::7D71200B-CBB4-462C-8975-6C8910BAD84F:"]},
          {"line":"C2","color":"#0749FF","mode":"rail","network":"aleop","headsigns":["Nantes","Savenay"],
           "routes":["ALEOP:TER:FR:Line::B92CEF62-3A1B-402A-9FB6-5293AD81B06E:"]},
          {"line":"C6","color":"#007F78","mode":"rail","network":"aleop","headsigns":["Nantes","Clisson"],
           "routes":["ALEOP:TER:FR:Line::359f7c82-fdec-4791-ae91-926c5827e59e:"]},
          {"line":"C42","color":"#0749FF","mode":"rail","network":"aleop","headsigns":["Ancenis","Savenay"],
           "routes":["ALEOP:TER:FR:Line::c101999b-d6cf-4022-94ac-fa86eb79643c:"]}
        ]
        """.trimIndent(),
    )

    private val lookup = TransitLineLookup(index)

    private fun only(network: TransitNetwork, name: String, match: String = name) =
        index.single { it.network == network && it.name == name && it.match == match }

    // --- Les clés ---

    @Test
    fun `la cle porte le reseau en minuscules et le match en majuscules`() {
        assertEquals("naolib:C6", only(TransitNetwork.NAOLIB, "C6").key)
        assertEquals("aleop:C6", only(TransitNetwork.ALEOP, "C6").key)
        // Sans réseau connu, la clé est le match seul — comme `lineIndexKey` côté web.
        assertEquals("C6", TransitLine(name = " c6 ").key)
    }

    @Test
    fun `naolib C6 et aleop C6 sont deux lignes`() {
        val chronobus = only(TransitNetwork.NAOLIB, "C6")
        val tramTrain = only(TransitNetwork.ALEOP, "C6")

        assertEquals(chronobus.match, tramTrain.match, "les tuiles les portent sous le même match")
        assertNotEquals(chronobus.key, tramTrain.key, "c'est le réseau qui les sépare")
        assertEquals(chronobus, lookup.resolve("naolib:C6"))
        assertEquals(tramTrain, lookup.resolve("aleop:C6"))
        // La casse ne compte pas, d'un côté comme de l'autre.
        assertEquals(tramTrain, lookup.resolve(" ALEOP:c6 "))
    }

    @Test
    fun `le champ match de l index se decode et sert de cle`() {
        val rennesVannes = index.first { it.headsigns.contains("Vannes") }

        assertEquals("P2 RENNES - VANNES", rennesVannes.tileMatch)
        assertEquals("P2 RENNES - VANNES", rennesVannes.match)
        assertEquals("aleop:P2 RENNES - VANNES", rennesVannes.key)
        // Le badge garde l'indice public.
        assertEquals("P2", rennesVannes.name)
    }

    @Test
    fun `deux P2 aleop se distinguent par leur match`() {
        val p2 = index.filter { it.name == "P2" }

        assertEquals(2, p2.size)
        assertEquals(setOf("aleop:P2", "aleop:P2 RENNES - VANNES"), p2.map { it.key }.toSet())
        assertEquals(listOf("Saint-Nazaire", "Nantes"), lookup.resolve("aleop:P2")?.headsigns)
        assertEquals(listOf("Rennes", "Vannes"), lookup.resolve("aleop:p2 rennes - vannes")?.headsigns)
    }

    // --- La résolution ---

    @Test
    fun `un numero nu designe la ligne Naolib`() {
        // L'index range les trains **après** les bus, mais la règle ne doit rien à cet ordre :
        // elle est écrite, pas héritée du fichier.
        assertEquals(TransitNetwork.NAOLIB, lookup.resolve("C6")?.network)
        assertEquals(TransitNetwork.NAOLIB, lookup.resolve("c2")?.network)
        val reversed = TransitLineLookup(index.reversed())
        assertEquals(TransitNetwork.NAOLIB, reversed.resolve("C6")?.network)
    }

    @Test
    fun `un numero nu porte par un train designe la ligne Aleop`() {
        assertEquals(only(TransitNetwork.ALEOP, "C6"), lookup.resolve("C6", TransportMode.TER))
        // Un numéro qui n'existe qu'en TER se trouve sans le mode.
        assertEquals("C42", lookup.resolve("C42")?.name)
        // Un car Aléop se trouve par son indice public.
        assertEquals("E309", lookup.resolve("e309")?.name)
    }

    @Test
    fun `le route_id TER se resout quelle que soit sa casse`() {
        val tramTrain = only(TransitNetwork.ALEOP, "C6")
        // L'index écrit cet UUID en minuscules…
        assertEquals(tramTrain, lookup.resolve("ALEOP:TER:FR:Line::359f7c82-fdec-4791-ae91-926c5827e59e:"))
        // … et une position théorique peut l'écrire en majuscules.
        assertEquals(tramTrain, lookup.resolve("ALEOP:TER:FR:LINE::359F7C82-FDEC-4791-AE91-926C5827E59E:"))
        // L'inverse : écrit en majuscules dans l'index, demandé en minuscules.
        assertEquals(
            only(TransitNetwork.ALEOP, "C2"),
            lookup.resolve("aleop:ter:fr:line::b92cef62-3a1b-402a-9fb6-5293ad81b06e:"),
        )
        // Le `route_id` d'un car, qui n'est pas son indice public.
        assertEquals("E309", lookup.resolve("ALEOP:309")?.name)
    }

    @Test
    fun `une cle qualifiee inconnue ne retombe pas sur le numero nu`() {
        // « aleop:C4 » n'existe pas dans cet extrait : rendre le Chronobus C4 — ou le C2 —
        // serait ouvrir une autre ligne en prétendant ouvrir celle-ci.
        assertNull(lookup.resolve("aleop:C4"))
        assertNull(lookup.resolve("naolib:C42"))
        assertNull(lookup.resolve("   "))
        assertNull(lookup.resolve(null))
        assertNull(TransitLineLookup.EMPTY.resolve("C6"))
    }

    // --- Les formes canoniques ---

    @Test
    fun `une reference se canonise comme le web`() {
        assertEquals("naolib:C6", normalizeTransitLineKey(" NAOLIB:c6 "))
        assertEquals("C6", normalizeTransitLineKey("c6"))
        // Tout ce qui suit le premier deux-points est la clé de tuile.
        assertEquals("aleop:TER:FR:LINE::ABC:", normalizeTransitLineKey("ALEOP:TER:FR:Line::abc:"))
        assertNull(normalizeTransitLineKey(""))

        assertEquals("aleop" to "P2 RENNES - VANNES", splitTransitLineKey("aleop:P2 Rennes - Vannes"))
        assertEquals(null to "C6", splitTransitLineKey("c6"))
        assertNull(splitTransitLineKey(null))
    }

    @Test
    fun `un numero se qualifie comme qualifyLineId`() {
        // Les quatre numéros partagés deviennent Naolib sans autre indice…
        assertEquals("naolib:C6", qualifyTransitLineKey("C6"))
        // … Aléop quand c'est un train…
        assertEquals("aleop:C6", qualifyTransitLineKey("C6", mode = TransportMode.TER))
        // … et le réseau connu l'emporte sur tout.
        assertEquals("aleop:E309", qualifyTransitLineKey("E309", network = TransitNetwork.ALEOP))
        // Les autres restent nus : aucun homonyme à départager.
        assertEquals("1", qualifyTransitLineKey("1"))
        assertEquals("naolib:C6", qualifyTransitLineKey("NAOLIB:c6"))
        assertNull(qualifyTransitLineKey(" "))
    }

    // --- Le train dans l'inventaire ---

    @Test
    fun `un train tombe dans la famille TER et un car dans l interurbain`() {
        assertEquals(TransitLineFamily.TER, only(TransitNetwork.ALEOP, "C6").family)
        assertEquals(TransitLineFamily.INTERURBAN, only(TransitNetwork.ALEOP, "E309").family)
        assertEquals(TransitLineFamily.CHRONOBUS, only(TransitNetwork.NAOLIB, "C6").family)
        // Le TER se range juste avant les cars.
        assertEquals(
            TransitLineFamily.INTERURBAN.ordinal - 1,
            TransitLineFamily.TER.ordinal,
        )
    }

    @Test
    fun `seuls C6 et C7 ferres sont des tram-trains`() {
        assertTrue(only(TransitNetwork.ALEOP, "C6").isTramTrain)
        assertFalse(only(TransitNetwork.NAOLIB, "C6").isTramTrain, "un Chronobus n'en devient pas un")
        assertFalse(only(TransitNetwork.ALEOP, "C2").isTramTrain, "le TER C2 reste un TER")
        assertTrue(isTramTrainLine("c7", TransportMode.TER))
        assertFalse(isTramTrainLine("C7", TransportMode.BUS))
        assertFalse(isTramTrainLine(null, TransportMode.TER))
    }

    @Test
    fun `train ter et sncf retrouvent les TER`() {
        listOf("train", "TER", "Sncf").forEach { word ->
            val found = index.filter { it.matches(word) }
            assertTrue(found.isNotEmpty(), "« $word » trouve des lignes")
            assertTrue(found.all { it.mode == TransportMode.TER }, "« $word » ne trouve que des trains")
            assertEquals(index.count { it.mode == TransportMode.TER }, found.size, "« $word » les trouve tous")
        }
        // « tram-train » ne rend que les tram-trains, pas les Chronobus du même numéro.
        assertEquals(listOf("aleop:C6"), index.filter { it.matches("tram-train") }.map { it.key })
    }

    @Test
    fun `P2 trouve les deux lignes qui portent ce numero`() {
        val digest = NetworkLinesDigest.build(index, "P2")

        assertEquals(
            listOf("aleop:P2", "aleop:P2 RENNES - VANNES"),
            digest.sections.flatMap { it.lines }.map { it.key },
        )
    }

    // --- Le nuancier ---

    @Test
    fun `le numero nu C2 prend la couleur du Chronobus`() {
        val palette = LinePalette.of(index)

        // Le défaut d'origine : `name to color` puis `toMap()` gardait la **dernière** entrée,
        // le TER, et le Chronobus C2 passait au bleu.
        assertEquals("#ee7402", palette.colorOf("C2"))
        assertEquals("#ee7402", palette.colorOf("naolib:C2"))
        assertEquals("#0749FF", palette.colorOf("aleop:C2"))
        assertEquals("#a877b2", palette.colorOf("C6"))
        assertEquals("#007F78", palette.colorOf("aleop:C6"))
    }

    @Test
    fun `un train prend la couleur du train`() {
        val palette = LinePalette.of(index)

        assertEquals("#0749FF", palette.colorOf("C2", TransportMode.TER))
        assertEquals("#ee7402", palette.colorOf("C2", TransportMode.BUS))
        // Par son `route_id`, dans les deux casses.
        assertEquals("#007F78", palette.colorOf("ALEOP:TER:FR:LINE::359F7C82-FDEC-4791-AE91-926C5827E59E:"))
        // Un numéro qui n'existe qu'en train se trouve sans le mode.
        assertEquals("#0749FF", palette.colorOf("C42"))
        assertNull(palette.colorOf("aleop:C4", TransportMode.TER))
        assertNotNull(palette.colorOf("E309"))
    }
}
