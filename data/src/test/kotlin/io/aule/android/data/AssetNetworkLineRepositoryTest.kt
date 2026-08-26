package io.aule.android.data

import io.aule.android.core.model.TransitLineFamily
import io.aule.android.core.model.TransitNetwork
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.repository.AssetBytes
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

        // Le compte exact : si le fichier maigrit d'un build de tuiles à l'autre,
        // on veut le voir ici plutôt que dans un volet à moitié vide.
        assertEquals(138, lines.size, "138 lignes dans l'index livré")
        // Aucune entrée perdue au décodage : chaque ligne a au moins son indice.
        assertTrue(lines.all { it.name.isNotBlank() })
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
        val bounds = repository.allLines().mapNotNull { it.bounds }

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
            lines.count { it.network == TransitNetwork.ALEOP },
        )
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
