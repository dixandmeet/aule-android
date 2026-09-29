package io.aule.android.data

import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.TransitLineFamily
import io.aule.android.core.model.TransitNetwork
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.repository.AssetBytes
import io.aule.android.data.caching.CachedNetworkLineRepository
import io.aule.android.data.tiles.AssetNetworkLineRepository
import io.aule.android.data.tiles.TRANSIT_LINES_INDEX_ASSET
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * L'index embarqué, lu **tel qu'il est livré**.
 *
 * Ce test ne parle pas de fixtures : il ouvre le fichier qui part dans l'APK.
 * C'est l'équivalent de `TransitLineIndexTests` côté iOS, et il tient trois
 * choses qu'aucun test de fixture ne peut tenir — que le fichier existe, qu'il
 * se décode entièrement, et qu'il décrit bien **ce réseau-ci**.
 */
class AssetNetworkLineRepositoryTest {

    /**
     * Les assets, lus depuis le dépôt.
     *
     * Le module `:data` est du JVM pur et n'a pas d'`AssetManager` ; le chemin
     * part donc de la racine du dépôt. Il est **calculé** et non écrit en dur :
     * le répertoire de travail d'un test Gradle est celui du module.
     */
    private class RepoAssets : AssetBytes {
        override fun readText(path: String): String? {
            val file = File(REPO_ROOT, "app/src/main/assets/$path")
            return if (file.isFile) file.readText() else null
        }
    }

    private val repository = AssetNetworkLineRepository(RepoAssets())

    @Test
    fun `l index est present et se decode entierement`() = runTest {
        val lines = repository.allLines()
        val trains = lines.count { it.mode == TransportMode.TER }

        // Un plancher, et non plus un compte exact : l'index grandit d'un build de tuiles à
        // l'autre — 138 lignes au 28/09, 163 avec les 23 TER et deux lignes Naolib de plus.
        // Ce qui se garde ici, c'est qu'il ne **maigrisse** pas : un fichier tronqué se verrait
        // ici plutôt que dans un volet à moitié vide.
        assertTrue(lines.size - trains >= 138, "au moins 138 lignes de bus, tram et Navibus")
        // Les TER arrivent tous ensemble, ou pas du tout — jamais une moitié d'import.
        assertTrue(trains == 0 || trains == 23, "0 ou 23 lignes TER, et non $trains")
        // Aucune entrée perdue au décodage : chaque ligne a au moins son indice.
        assertTrue(lines.all { it.name.isNotBlank() })
        // Et aucune clé en double : c'est elle, et non plus l'indice, qui désigne une ligne.
        assertEquals(lines.size, lines.map { it.key }.toSet().size, "une clé par ligne")
    }

    @Test
    fun `chaque ligne porte une couleur et un cadre`() = runTest {
        val lines = repository.allLines()

        // `null` est une réponse légitime du modèle, mais **pas** dans ce
        // fichier-ci : le build des tuiles les renseigne toutes. Une régression
        // du générateur se verrait ici avant de se voir en badges gris.
        assertTrue(lines.all { !it.colorHex.isNullOrBlank() }, "toutes les couleurs")
        assertTrue(lines.all { it.bounds != null }, "tous les cadres")
        assertTrue(
            lines.all { it.colorHex!!.startsWith("#") && it.colorHex!!.length == 7 },
            "la forme #RRGGBB qu'attend LineBadge",
        )
    }

    @Test
    fun `les cadres tombent bien sur la Loire-Atlantique`() = runTest {
        // **C'est ce test qui attrape une transposition latitude/longitude.** Le
        // contrôle générique de `transitLineBoundsFromGeoJson` ne peut pas la
        // voir — à Nantes, les deux valeurs restent dans les bornes une fois
        // échangées. Ici, un cadre transposé tomberait à une longitude de +47,
        // quelque part en Somalie, et l'enveloppe le refuse.
        //
        // Les TER sortent du département — Quimper, Orléans, Rennes : ils ont leur propre
        // enveloppe, celle du Grand Ouest, qui refuse tout autant une transposition.
        val lines = repository.allLines()
        val bounds = lines.filter { it.mode != TransportMode.TER }.mapNotNull { it.bounds }
        val railBounds = lines.filter { it.mode == TransportMode.TER }.mapNotNull { it.bounds }
        assertTrue(
            railBounds.all {
                it.southWest.latitude in 45.0..49.5 && it.northEast.latitude in 45.0..49.5 &&
                    it.southWest.longitude in -5.0..2.5 && it.northEast.longitude in -5.0..2.5
            },
            "les TER restent dans le Grand Ouest",
        )

        assertTrue(bounds.isNotEmpty())
        assertTrue(
            bounds.all { it.southWest.latitude in 46.0..49.0 },
            "latitudes au sud de la Bretagne et au nord de la Vendée",
        )
        assertTrue(
            bounds.all { it.southWest.longitude in -3.5..0.0 },
            "longitudes à l'ouest de Paris et à l'est de l'Atlantique",
        )
        assertTrue(bounds.all { it.northEast.latitude in 46.0..49.0 })
        assertTrue(bounds.all { it.northEast.longitude in -3.5..0.0 })
    }

    @Test
    fun `le reseau reel se range dans les bonnes familles`() = runTest {
        val lines = repository.allLines()
        val byFamily = lines.groupingBy { it.family }.eachCount()

        // Trois trams, quatre Navibus : ce sont les lignes qu'on peut compter de
        // tête, et elles ancrent le reste.
        assertEquals(3, byFamily[TransitLineFamily.TRAM])
        assertEquals(4, byFamily[TransitLineFamily.NAVIBUS])
        // Neuf Chronobus — C1 à C9 plus C20, moins C5 qui n'existe pas.
        assertEquals(9, byFamily[TransitLineFamily.CHRONOBUS])
        assertEquals(4, byFamily[TransitLineFamily.EXPRESS])
        // Vingt-neuf cars Aléop, tous en interurbain — **y compris ceux dont
        // l'indice commence par E**, qui sont la raison d'être de la règle
        // « le réseau décide avant l'indice ».
        assertEquals(29, byFamily[TransitLineFamily.INTERURBAN])
        assertEquals(
            29,
            lines.count { it.network == TransitNetwork.ALEOP && it.mode != TransportMode.TER },
        )
        // Les trains à part des cars, bien que rangés dans le même réseau : la famille TER les
        // prend tous, et rien d'autre.
        val trains = lines.filter { it.mode == TransportMode.TER }
        assertEquals(trains.size, byFamily[TransitLineFamily.TER] ?: 0)
        assertTrue(trains.all { it.network == TransitNetwork.ALEOP }, "les TER sont rangés chez Aléop")
        assertEquals(lines.size, byFamily.values.sum(), "aucune ligne sans famille")
    }

    @Test
    fun `le mode ferry de l index devient un Navibus`() = runTest {
        // L'index écrit `ferry`, le modèle parle `BOAT`, l'écran dit « Navibus ».
        // Trois mots pour la même chose : celui du milieu doit tenir.
        val navibus = repository.allLines().filter { it.mode == TransportMode.BOAT }

        assertEquals(4, navibus.size)
        assertTrue(navibus.all { it.family == TransitLineFamily.NAVIBUS })
    }

    @Test
    fun `une ligne se retrouve quelle que soit la casse`() = runTest {
        val direct = assertNotNull(repository.line("C6"))
        assertEquals("C6", direct.name)
        // Le numéro nu désigne le Chronobus, même quand l'index porte aussi le tram-train C6.
        assertEquals(TransitNetwork.NAOLIB, direct.network)
        assertEquals(direct, repository.line("naolib:c6"))
        assertEquals(direct, repository.line("c6"))
        assertEquals(direct, repository.line("  c6  "))
        // Une ligne absente rend `null`, et c'est une réponse : le badge garde
        // son gris plutôt que d'inventer une teinte.
        assertNull(repository.line("ligne-qui-n-existe-pas"))
    }

    @Test
    fun `un asset absent ne fait pas lever`() = runTest {
        val empty = AssetNetworkLineRepository(
            assets = object : AssetBytes {
                override fun readText(path: String): String? = null
            },
        )

        // Sans index, les badges restent gris et lisibles. Lever ici viderait la
        // carte au premier véhicule peint.
        assertTrue(empty.allLines().isEmpty())
        assertNull(empty.line("C6"))
    }

    // --- Les homonymes, sur un index fabriqué ---
    //
    // L'asset change à chaque build des tuiles ; les cas qui suivent doivent tenir quel que soit
    // son contenu. Ils lisent donc un extrait écrit ici, dans l'ordre du vrai fichier : les
    // bus avant les trains.

    private val fixture = AssetNetworkLineRepository(
        assets = object : AssetBytes {
            override fun readText(path: String): String = """
                [
                  {"line":"C2","color":"#ee7402","mode":"bus","network":"naolib"},
                  {"line":"C6","color":"#a877b2","mode":"bus","network":"naolib"},
                  {"line":"P2","color":"#006600","mode":"rail","network":"aleop","match":"P2 RENNES - VANNES",
                   "headsigns":["Rennes","Vannes"],"routes":["ALEOP:TER:FR:Line::4BBFA233-37AB-432A-BCCF-FB90719B1F8A:"]},
                  {"line":"P2","color":"#006600","mode":"rail","network":"aleop",
                   "headsigns":["Saint-Nazaire","Nantes"],"routes":["ALEOP:TER:FR:Line::7D71200B-CBB4-462C-8975-6C8910BAD84F:"]},
                  {"line":"C2","color":"#0749FF","mode":"rail","network":"aleop",
                   "routes":["ALEOP:TER:FR:Line::B92CEF62-3A1B-402A-9FB6-5293AD81B06E:"]},
                  {"line":"C6","color":"#007F78","mode":"rail","network":"aleop",
                   "routes":["ALEOP:TER:FR:Line::359f7c82-fdec-4791-ae91-926c5827e59e:"]}
                ]
            """.trimIndent()
        },
    )

    @Test
    fun `naolib C6 n est pas aleop C6`() = runTest {
        val chronobus = assertNotNull(fixture.line("naolib:C6"))
        val tramTrain = assertNotNull(fixture.line("aleop:C6"))

        assertEquals(TransportMode.BUS, chronobus.mode)
        assertEquals(TransportMode.TER, tramTrain.mode)
        assertTrue(chronobus != tramTrain)
        // Le numéro nu : Naolib, sauf si l'on sait que c'est un train.
        assertEquals(chronobus, fixture.line("C6"))
        assertEquals(tramTrain, fixture.line("c6", TransportMode.TER))
    }

    @Test
    fun `deux P2 aleop restent deux lignes`() = runTest {
        val saintNazaire = assertNotNull(fixture.line("aleop:P2"))
        val rennesVannes = assertNotNull(fixture.line("aleop:P2 RENNES - VANNES"))

        assertEquals(listOf("Saint-Nazaire", "Nantes"), saintNazaire.headsigns)
        assertEquals(listOf("Rennes", "Vannes"), rennesVannes.headsigns)
        assertEquals("P2", rennesVannes.name, "le badge garde l'indice public")
        assertEquals(4, fixture.allLines().count { it.network == TransitNetwork.ALEOP })
    }

    @Test
    fun `un route_id TER se retrouve en majuscules comme en minuscules`() = runTest {
        val tramTrain = assertNotNull(fixture.line("aleop:C6"))

        assertEquals(tramTrain, fixture.line("ALEOP:TER:FR:Line::359f7c82-fdec-4791-ae91-926c5827e59e:"))
        assertEquals(tramTrain, fixture.line("ALEOP:TER:FR:LINE::359F7C82-FDEC-4791-AE91-926C5827E59E:"))
        assertEquals(
            fixture.line("aleop:C2"),
            fixture.line("aleop:ter:fr:line::b92cef62-3a1b-402a-9fb6-5293ad81b06e:"),
        )
    }

    @Test
    fun `le numero nu C2 garde la couleur du Chronobus`() = runTest {
        val palette = LinePalette.of(fixture.allLines())

        assertEquals("#ee7402", palette.colorOf("C2"))
        assertEquals("#0749FF", palette.colorOf("aleop:C2"))
        assertEquals("#0749FF", palette.colorOf("C2", TransportMode.TER))
        // Le décorateur en mémoire répond comme le dépôt qu'il enveloppe.
        val cached = CachedNetworkLineRepository(fixture)
        assertEquals("#ee7402", cached.line("C2")?.colorHex)
        assertEquals("#0749FF", cached.line("C2", TransportMode.TER)?.colorHex)
        assertEquals("#006600", cached.line("aleop:p2 rennes - vannes")?.colorHex)
    }

    @Test
    fun `le chemin de l asset est celui de la source`() {
        // Une copie qui change de nom est une copie qu'on ne retrouve plus dans
        // l'autre dépôt.
        assertEquals("tiles/transit-lines-index.json", TRANSIT_LINES_INDEX_ASSET)
    }

    private companion object {
        /** La racine du dépôt, depuis le répertoire du module `:data`. */
        val REPO_ROOT: File = File(System.getProperty("user.dir")).parentFile
    }
}
