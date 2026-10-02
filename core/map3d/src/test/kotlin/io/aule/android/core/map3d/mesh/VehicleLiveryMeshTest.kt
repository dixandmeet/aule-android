package io.aule.android.core.map3d.mesh

import io.aule.android.core.map3d.VehicleScene
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que le nuancier de la livrée « neutre + accent » suppose des **vrais modèles**.
 *
 * `vehicle_layer.cpp` repère la bande de toit par la normale (`N.z > 0,9`) et la position dans le
 * repère du modèle (`|x| < 0,28 m`, `z > 2 m`), et le liseré de bas de caisse par une hauteur
 * (`z < 0,6 m`). Ces critères valent par la géométrie des deux fichiers : si le pack de modèles
 * change, ces mesures disent si l'accent a encore un toit et un flanc où se montrer.
 *
 * Ni le sens des faces ni le rendu ne se jugent ici — voir ADR-020, et l'œil sur l'appareil.
 */
class VehicleLiveryMeshTest {

    private fun load(model: VehicleMeshCatalog.Model): StandardMesh {
        val file = File("src/main/assets/${VehicleMeshCatalog.ASSET_DIR}/${model.asset}")
        assertTrue(file.exists(), "modèle absent : ${file.absolutePath}")
        return MeshStandardizer.standardize(
            GlbReader.read(file.readBytes()),
            model.dimensions,
            model.forwardIsPositiveZ,
            model.materialParts,
        )
    }

    /** L'aire, en m², des triangles dont un point d'échantillon vérifie [keep]. */
    private fun area(mesh: StandardMesh, keep: (part: Int, nz: Double, x: Double, z: Double) -> Boolean): Double {
        val steps = 24
        var total = 0.0
        for (t in 0 until mesh.triangleCount) {
            val at = t * 3 * StandardMesh.FLOATS_PER_VERTEX
            fun c(corner: Int, axis: Int) = mesh.vertices[at + corner * StandardMesh.FLOATS_PER_VERTEX + axis].toDouble()
            val ux = c(1, 0) - c(0, 0); val uy = c(1, 1) - c(0, 1); val uz = c(1, 2) - c(0, 2)
            val vx = c(2, 0) - c(0, 0); val vy = c(2, 1) - c(0, 1); val vz = c(2, 2) - c(0, 2)
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            val triangle = 0.5 * Math.sqrt(nx * nx + ny * ny + nz * nz)
            val part = mesh.vertices[at + StandardMesh.PART_OFFSET].toInt()
            val faceNz = mesh.vertices[at + StandardMesh.NORMAL_OFFSET + 2].toDouble()
            var hit = 0
            var all = 0
            for (i in 0..steps) for (j in 0..steps - i) {
                val b1 = i.toDouble() / steps
                val b2 = j.toDouble() / steps
                val b0 = 1 - b1 - b2
                val x = b0 * c(0, 0) + b1 * c(1, 0) + b2 * c(2, 0)
                val z = b0 * c(0, 2) + b1 * c(1, 2) + b2 * c(2, 2)
                all++
                if (keep(part, faceNz, x, z)) hit++
            }
            total += triangle * hit / all
        }
        return total
    }

    @Test
    fun `le toit de chaque modele porte une bande d'accent visible`() {
        for (model in VehicleMeshCatalog.ALL) {
            val mesh = load(model)
            val stripe = area(mesh) { part, nz, x, z ->
                part == MeshPart.BODY.code.toInt() && nz > 0.9 && z > 2.0 && Math.abs(x) < 0.28
            }
            // Un toit de bus fait 11 m × 0,56 m = 6 m² de bande théorique ; le modèle en a la
            // moitié à hauteur de toit (le reste est creusé de trappes). Moins de 2 m², et la bande
            // n'est plus qu'un pointillé.
            assertTrue(stripe > 2.0, "${model.asset} : seulement ${"%.1f".format(stripe)} m² de bande de toit")
        }
    }

    @Test
    fun `le bas de flanc a de quoi porter l'accent, parce que la piece bas de caisse ne le fait pas`() {
        for (model in VehicleMeshCatalog.ALL) {
            val mesh = load(model)
            val skirtPart = area(mesh) { part, _, _, _ -> part == MeshPart.SKIRT.code.toInt() }
            val sill = area(mesh) { part, nz, _, z ->
                (part == MeshPart.BODY.code.toInt() || part == MeshPart.SKIRT.code.toInt()) &&
                    Math.abs(nz) < 0.5 && z < 0.6
            }
            // La pièce 4 est (presque) absente des deux modèles : leurs jupes sont rangées en
            // carrosserie (voir `VehicleMeshCatalog`). Sans la bande de flanc du nuancier, l'accent
            // du bas de caisse ne se verrait sur aucun des deux.
            assertTrue(skirtPart < 2.0, "${model.asset} : la pièce bas de caisse couvre ${"%.1f".format(skirtPart)} m²")
            assertTrue(sill > 5.0, "${model.asset} : seulement ${"%.1f".format(sill)} m² de flanc sous 0,6 m")
        }
    }

    @Test
    fun `les poses ont la taille que le natif attend`() {
        // Dix flottants, l'entier du maillage, puis cinq flottants de livrée : `Pose` de
        // `scene_state.hpp`, que son `static_assert` garde à 64 octets de l'autre côté.
        assertEquals(10 * 4 + 4 + 5 * 4, VehicleScene.POSE_BYTES)
        assertEquals(44, VehicleScene.OFFSET_ACCENT)
        assertEquals(VehicleScene.OFFSET_ACCENT + 3 * 4, VehicleScene.OFFSET_ACCENT_MIX)
        assertEquals(VehicleScene.OFFSET_ACCENT_MIX + 4, VehicleScene.OFFSET_SHADOW_BOOST)
        assertEquals(VehicleScene.POSE_BYTES - 4, VehicleScene.OFFSET_SHADOW_BOOST)
    }

    @Test
    fun `le nuancier et la structure native disent la meme chose`() {
        val shader = File("src/main/cpp/vehicle_layer.cpp").readText()
        val state = File("src/main/cpp/scene_state.hpp").readText()
        // Les critères de la bande de toit : ceux de la spec de la livrée.
        assertTrue("smoothstep(0.27, 0.29, across)" in shader, "la bande de toit n'est plus à |x| < 0,28 m")
        assertTrue("smoothstep(0.88, 0.92, N.z)" in shader, "le toit n'est plus repéré par N.z > 0,9")
        assertTrue("static_assert(sizeof(Pose) == 64" in state)
        for (field in listOf("accentR", "accentG", "accentB", "accentMix", "shadowBoost")) {
            assertTrue(field in state, "le champ $field manque à `Pose`")
        }
    }
}
