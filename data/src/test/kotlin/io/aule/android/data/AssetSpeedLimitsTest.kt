package io.aule.android.data

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.repository.AssetBytes
import io.aule.android.data.tiles.LINE_SPEED_LIMITS_ASSET
import io.aule.android.data.tiles.loadSpeedLimits
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Les limitations embarquées, lues **telles qu'elles sont livrées**.
 *
 * Ce test ne parle pas de fixtures : il ouvre le fichier qui part dans l'APK, et
 * tient ce qu'aucune fixture ne peut tenir — que l'asset existe, qu'il se décode,
 * et qu'il décrit bien **cette note-ci**. Pendant de `SpeedLimitIndexTests` côté
 * iOS.
 */
class AssetSpeedLimitsTest {

    private class RepoAssets : AssetBytes {
        override fun readText(path: String): String? {
            val file = File(REPO_ROOT, "app/src/main/assets/$path")
            return if (file.isFile) file.readText() else null
        }
    }

    private val table = loadSpeedLimits(RepoAssets())

    @Test
    fun `la ligne 1 porte les troncons de la note 26 sur 639`() {
        val source = table.sourceFor("1")

        assertNotNull(source, "$LINE_SPEED_LIMITS_ASSET absent — vérifier app/src/main/assets")
        assertEquals("26/639", source.reference)
        assertEquals("2026-08-31", source.effectiveOn)
        assertEquals("Gare Maritime — Médiathèque", source.extent)
    }

    /**
     * Chantiers Navals, au quai V1 tel que le feed le publie
     * (`FR_NAOLIB:Quay:99`) : la note y met 30 km/h, « à hauteur des deux
     * demi-stations et du débranchement ».
     */
    @Test
    fun `au droit de Chantiers Navals, le panneau dit 30`() {
        assertEquals(30, table.limitFor("1", Coordinate(47.208916, -1.567305)))
    }

    @Test
    fun `hors des troncons decrits, le panneau reste eteint`() {
        // Commerce, deux stations plus loin : sur la ligne 1, hors de la note.
        assertNull(table.limitFor("1", Coordinate(47.213547, -1.556233)))
    }

    /**
     * ⚠️ Les limitations de la note sont celles de la plate-forme tramway. Un
     * conducteur de bus, ou une voiture, ne doit rien y lire.
     */
    @Test
    fun `une ligne qu'on n'assure pas n'a pas de limitation`() {
        assertNull(table.limitFor("C3", Coordinate(47.208916, -1.567305)))
    }

    @Test
    fun `le chemin de l asset est celui de la source`() {
        // Une copie qui change de nom est une copie qu'on ne retrouve plus dans
        // l'autre dépôt.
        assertEquals("tiles/line-speed-limits.json", LINE_SPEED_LIMITS_ASSET)
    }

    private companion object {
        /** La racine du dépôt, depuis le répertoire du module `:data`. */
        val REPO_ROOT: File = File(System.getProperty("user.dir")).parentFile
    }
}
