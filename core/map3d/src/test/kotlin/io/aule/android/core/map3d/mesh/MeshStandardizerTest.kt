package io.aule.android.core.map3d.mesh

import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que la mise aux normes doit garantir, vérifié sur **les vrais fichiers**.
 *
 * Ces tests ne valent pas pour la géométrie qu'ils recalculent — ils valent pour
 * ce qu'ils empêchent : qu'un modèle parte à l'envers, à la mauvaise échelle, ou
 * enfoncé dans la chaussée. Trois défauts qui ne se voient qu'à l'écran, et deux
 * qui ne se voient qu'**en mouvement**.
 */
class MeshStandardizerTest {

    private fun load(model: VehicleMeshCatalog.Model): StandardMesh {
        val file = File("src/main/assets/${VehicleMeshCatalog.ASSET_DIR}/${model.asset}")
        assertTrue(file.exists(), "modèle absent : ${file.absolutePath}")
        val primitives = GlbReader.read(file.readBytes())
        assertTrue(primitives.isNotEmpty(), "aucune primitive lue dans ${model.asset}")
        return MeshStandardizer.standardize(
            primitives,
            model.dimensions,
            model.forwardIsPositiveZ,
            model.materialParts,
        )
    }

    private fun extent(mesh: StandardMesh, axis: Int): Pair<Float, Float> {
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        for (v in 0 until mesh.vertexCount) {
            val value = mesh.vertices[v * StandardMesh.FLOATS_PER_VERTEX + axis]
            if (value < low) low = value
            if (value > high) high = value
        }
        return low to high
    }

    @Test
    fun `le bus est mis a ses cotes reelles`() {
        val mesh = load(VehicleMeshCatalog.BUS)
        val (minX, maxX) = extent(mesh, 0)
        val (minY, maxY) = extent(mesh, 1)
        val (minZ, maxZ) = extent(mesh, 2)

        assertEquals(2.55, (maxX - minX).toDouble(), 1e-3, "largeur")
        assertEquals(11.0, (maxY - minY).toDouble(), 1e-3, "longueur, portée par l'axe nord")
        assertEquals(3.2, (maxZ - minZ).toDouble(), 1e-3, "hauteur")
    }

    @Test
    fun `le tram est mis a ses cotes reelles`() {
        val mesh = load(VehicleMeshCatalog.TRAM)
        val (minX, maxX) = extent(mesh, 0)
        val (minY, maxY) = extent(mesh, 1)
        val (minZ, maxZ) = extent(mesh, 2)

        assertEquals(2.65, (maxX - minX).toDouble(), 1e-3, "largeur")
        assertEquals(28.0, (maxY - minY).toDouble(), 1e-3, "longueur")
        assertEquals(3.35, (maxZ - minZ).toDouble(), 1e-3, "hauteur")
    }

    /**
     * Le modèle est centré en plan et **posé** sur la chaussée, pas dedans.
     *
     * Le décollement n'est pas une coquetterie : à zéro, la semelle et la
     * chaussée se disputent le tampon de profondeur et le bas du véhicule
     * papillote au moindre mouvement de caméra.
     */
    @Test
    fun `chaque modele est centre en plan et pose sur le sol`() {
        for (model in VehicleMeshCatalog.ALL) {
            val mesh = load(model)
            val (minX, maxX) = extent(mesh, 0)
            val (minY, maxY) = extent(mesh, 1)
            val (minZ, _) = extent(mesh, 2)

            assertEquals(0.0, ((minX + maxX) / 2).toDouble(), 1e-4, "${model.asset} centré en X")
            assertEquals(0.0, ((minY + maxY) / 2).toDouble(), 1e-4, "${model.asset} centré en Y")
            assertEquals(
                MeshStandardizer.GROUND_CLEARANCE_M,
                minZ.toDouble(),
                1e-4,
                "${model.asset} posé au décollement exact",
            )
        }
    }

    /**
     * **Le sens de marche du tram**, lu dans le fichier et non à l'œil.
     *
     * La cabine est le bout où le vitrage monte jusqu'à la pointe : un pare-brise
     * affleurant. À l'autre extrémité, les derniers décimètres sont de la caisse
     * pleine, sans une vitre. Si le modèle partait à l'envers, le tram
     * **reculerait** le long de sa voie — invisible à l'arrêt, évident en
     * mouvement, et c'est exactement le genre de défaut qu'aucune capture ne
     * rattrape.
     */
    @Test
    fun `la cabine du tram regarde vers l avant`() {
        val mesh = load(VehicleMeshCatalog.TRAM)
        val (minY, maxY) = extent(mesh, 1)

        var glassFront = -Float.MAX_VALUE
        var glassBack = Float.MAX_VALUE
        for (v in 0 until mesh.vertexCount) {
            val at = v * StandardMesh.FLOATS_PER_VERTEX
            // Le masque distingue la carrosserie du reste ; les vitres sont la
            // pièce sombre non masquée la plus basse en rouge.
            if (mesh.vertices[at + 6] != 0f) continue
            val y = mesh.vertices[at + 1]
            if (y > glassFront) glassFront = y
            if (y < glassBack) glassBack = y
        }

        assertTrue(
            abs(maxY - glassFront) < 0.35f,
            "le vitrage devrait affleurer l'avant (+Y) : bout à $maxY, vitre à $glassFront",
        )
        assertTrue(
            glassBack - minY > 0.4f,
            "l'arrière devrait être de la caisse pleine : bout à $minY, vitre à $glassBack",
        )
    }

    /** La longueur est portée par l'axe nord, jamais par la largeur. */
    @Test
    fun `la longueur est portee par l axe nord`() {
        for (model in VehicleMeshCatalog.ALL) {
            val mesh = load(model)
            val (minX, maxX) = extent(mesh, 0)
            val (minY, maxY) = extent(mesh, 1)
            assertTrue(
                (maxY - minY) > (maxX - minX),
                "${model.asset} est couché en travers : ${maxY - minY} × ${maxX - minX}",
            )
        }
    }

    /**
     * Le contrat avec le nuancier : sept flottants par sommet, un masque binaire,
     * des couleurs dans l'intervalle. Le shader ne vérifie rien de tout cela.
     */
    @Test
    fun `la disposition des sommets respecte le contrat du nuancier`() {
        for (model in VehicleMeshCatalog.ALL) {
            val mesh = load(model)
            assertEquals(
                0,
                mesh.vertices.size % StandardMesh.FLOATS_PER_VERTEX,
                "${model.asset} : sommets tronqués",
            )
            assertEquals(0, mesh.vertexCount % 3, "${model.asset} : sommets non triangulaires")
            assertTrue(mesh.triangleCount > 100, "${model.asset} : maillage suspicieusement vide")

            for (v in 0 until mesh.vertexCount) {
                val at = v * StandardMesh.FLOATS_PER_VERTEX
                for (channel in 3..5) {
                    val value = mesh.vertices[at + channel]
                    assertTrue(
                        value in 0f..1f,
                        "${model.asset} : couleur hors bornes ($value)",
                    )
                }
                val mask = mesh.vertices[at + 6]
                assertTrue(mask == 0f || mask == 1f, "${model.asset} : masque non binaire ($mask)")
            }
        }
    }

    /**
     * Un fichier abîmé rend un maillage vide, il ne lève pas.
     *
     * C'est la moitié du contrat de repli : la carte doit continuer de tourner en
     * volume extrudé, pas refuser d'ouvrir l'écran.
     */
    @Test
    fun `un fichier illisible ne leve pas`() {
        assertTrue(GlbReader.read(ByteArray(0)).isEmpty())
        assertTrue(GlbReader.read(ByteArray(64) { 0x7F }).isEmpty())
        // Une magie correcte suivie de n'importe quoi.
        val truncated = byteArrayOf(0x67, 0x6C, 0x54, 0x46) + ByteArray(40) { 0x11 }
        assertTrue(GlbReader.read(truncated).isEmpty())
    }
}
