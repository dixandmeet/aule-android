package io.aule.android.core.map3d.mesh

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Les cotes réelles d'un modèle, en mètres. */
internal data class MeshDimensions(
    val widthMeters: Double,
    val heightMeters: Double,
    val lengthMeters: Double,
)

/**
 * Ce qu'une pièce du modèle devient à l'écran.
 *
 * [BODY] ne porte pas de couleur : elle transporte son ombrage, que le nuancier
 * multipliera par la teinte de la ligne. C'est ce qui permet de garder **un seul
 * maillage par modèle** quelle que soit la livrée ; cuire la couleur de ligne
 * dans les sommets exigerait un tampon par ligne.
 */
internal enum class MeshPart { BODY, GLASS, CHASSIS, LIGHTS }

/**
 * Comment le nom d'un matériau devient une couleur.
 *
 * L'heuristique est celle du web, mot pour mot, parce que ce sont les mêmes
 * fichiers et que leurs matériaux s'appellent `Windows`, `Bottom`, `Lights`,
 * `Black`. S'en écarter repeindrait des pièces que les deux autres plateformes
 * laissent sombres.
 */
internal object MeshPalette {

    /** Les vitres et les pièces noires. */
    const val GLASS = 0x1D3831

    /** Roues, bas de caisse, pare-chocs, détails. */
    const val CHASSIS = 0x363E3C

    /** Les feux. */
    const val LIGHTS = 0xF6E7AE

    fun part(materialName: String, overrides: Map<String, MeshPart> = emptyMap()): MeshPart {
        overrides[materialName]?.let { return it }
        val lower = materialName.lowercase()
        return when {
            "window" in lower || "black" in lower -> MeshPart.GLASS
            "wheel" in lower || "bottom" in lower ||
                "bumper" in lower || "detail" in lower -> MeshPart.CHASSIS
            "light" in lower -> MeshPart.LIGHTS
            else -> MeshPart.BODY
        }
    }

    fun color(part: MeshPart): Int = when (part) {
        MeshPart.GLASS -> GLASS
        MeshPart.CHASSIS -> CHASSIS
        MeshPart.LIGHTS -> LIGHTS
        // Jamais lu : la carrosserie prend la teinte de la ligne au rendu.
        MeshPart.BODY -> GLASS
    }
}

/**
 * Un modèle prêt à dessiner : des triangles en mètres, dans le repère de scène.
 *
 * La disposition d'un sommet est un **contrat avec le nuancier** de
 * `vehicle_layer.cpp` : sept flottants, `x y z r g b masque`. Y ajouter un champ
 * sans toucher au shader décale silencieusement toutes les couleurs.
 */
internal class StandardMesh(
    /** Sept flottants par sommet, trois sommets par triangle. */
    val vertices: FloatArray,
    val dimensions: MeshDimensions,
) {
    val vertexCount: Int get() = vertices.size / FLOATS_PER_VERTEX
    val triangleCount: Int get() = vertexCount / 3

    companion object {
        const val FLOATS_PER_VERTEX = 7
    }
}

/**
 * La mise aux normes d'un modèle brut : orientation, cotes, ancrage au sol,
 * ombrage cuit.
 *
 * C'est le portage du `MeshStandardizer` d'iOS, lui-même la version **corrigée**
 * du `standardize()` du web — celui-ci redresse un modèle exporté en largeur
 * mais ignore le **sens de marche**, et les deux fichiers du pack ne suivent pas
 * la même convention. Un modèle à l'envers recule sagement le long de sa voie :
 * invisible à l'arrêt, évident en mouvement.
 *
 * Chaque étape corrige un défaut constaté à l'écran ailleurs ; les refaire dans
 * un autre ordre les ramène.
 *
 * Calcul pur — vérifiable sur la JVM.
 */
internal object MeshStandardizer {

    /**
     * Le décollement du sol, en mètres.
     *
     * Sans lui, la semelle du modèle et la chaussée occupent exactement le même
     * plan et se disputent le tampon de profondeur : le bas du véhicule
     * papillote au moindre mouvement de caméra.
     */
    const val GROUND_CLEARANCE_M = 0.05

    fun standardize(
        primitives: List<GlbPrimitive>,
        dimensions: MeshDimensions,
        forwardIsPositiveZ: Boolean,
        overrides: Map<String, MeshPart> = emptyMap(),
    ): StandardMesh {
        // 1. Dé-indexer, en retenant la pièce que porte chaque triangle.
        //
        // Non indexé, comme le web et iOS : chaque triangle garde sa propre
        // normale, donc le facettage franc du modèle bas-poly. Indexer
        // moyennerait les normales aux arêtes et rendrait un bus savonneux.
        // Chaque primitive est tronquée à un multiple de trois : un reste
        // désynchroniserait le compte de sommets de celui des triangles, et les
        // couleurs se décaleraient d'une pièce à l'autre sans rien signaler.
        val total = primitives.sumOf { it.triangleCount * 3 }
        if (total < 3) return StandardMesh(FloatArray(0), dimensions)

        val position = DoubleArray(total * 3)
        val part = arrayOfNulls<MeshPart>(total / 3)
        var vertex = 0
        var triangleCursor = 0
        for (primitive in primitives) {
            val piece = MeshPalette.part(primitive.materialName, overrides)
            val triangles = primitive.triangleCount
            for (i in 0 until triangles * 3) {
                val source = primitive.indices[i] * 3
                // Un indice qui sort du tampon veut dire un fichier incohérent :
                // on rend un maillage vide, et le repli prend la main.
                if (source < 0 || source + 2 >= primitive.positions.size) {
                    return StandardMesh(FloatArray(0), dimensions)
                }
                position[vertex * 3] = primitive.positions[source].toDouble()
                position[vertex * 3 + 1] = primitive.positions[source + 1].toDouble()
                position[vertex * 3 + 2] = primitive.positions[source + 2].toDouble()
                vertex++
            }
            for (t in 0 until triangles) part[triangleCursor + t] = piece
            triangleCursor += triangles
        }

        // 2. Coucher le modèle dans le repère de scène.
        //
        // glTF pose « +Y en haut, −Z devant » ; la scène veut « +Y devant (nord),
        // +Z en haut ». Le passage est un quart de tour autour de X — **pas une
        // permutation d'axes**, qui aurait un déterminant négatif et rendrait le
        // modèle en miroir, normales comprises.
        //
        // Un modèle peut malgré tout être exporté couché en largeur : on le
        // redresse d'abord, en comparant les deux emprises horizontales.
        var box = BoundingBox.of(position)
        if (box.sizeX > box.sizeZ) {
            // Quart de tour autour de l'axe vertical de glTF : (x, y, z) → (−z, y, x).
            for (v in 0 until total) {
                val x = position[v * 3]
                val z = position[v * 3 + 2]
                position[v * 3] = -z
                position[v * 3 + 2] = x
            }
        }
        val flip = if (forwardIsPositiveZ) -1.0 else 1.0
        for (v in 0 until total) {
            val x = position[v * 3]
            val y = position[v * 3 + 1]
            val z = position[v * 3 + 2]
            position[v * 3] = flip * x
            position[v * 3 + 1] = flip * -z
            position[v * 3 + 2] = y
        }

        // 3. Mettre aux cotes réelles, axe par axe.
        box = BoundingBox.of(position)
        val factorX = dimensions.widthMeters / max(box.sizeX, EPSILON)
        val factorY = dimensions.lengthMeters / max(box.sizeY, EPSILON)
        val factorZ = dimensions.heightMeters / max(box.sizeZ, EPSILON)
        for (v in 0 until total) {
            position[v * 3] *= factorX
            position[v * 3 + 1] *= factorY
            position[v * 3 + 2] *= factorZ
        }

        // 4. Centrer en plan, poser sur le sol.
        box = BoundingBox.of(position)
        val shiftX = -(box.minX + box.maxX) / 2
        val shiftY = -(box.minY + box.maxY) / 2
        val shiftZ = -box.minZ + GROUND_CLEARANCE_M
        for (v in 0 until total) {
            position[v * 3] += shiftX
            position[v * 3 + 1] += shiftY
            position[v * 3 + 2] += shiftZ
        }

        // 5. Cuire la normale de chaque triangle dans ses trois sommets.
        //
        // La normale se calcule **après** la mise aux cotes : celle-ci est
        // anisotrope, et transporter les normales du fichier à travers elle
        // demanderait la transposée de son inverse. Les recalculer coûte moins
        // cher et ne peut pas se tromper.
        val out = FloatArray(total * StandardMesh.FLOATS_PER_VERTEX)
        for (triangle in 0 until total / 3) {
            val a = triangle * 3
            val ux = position[(a + 1) * 3] - position[a * 3]
            val uy = position[(a + 1) * 3 + 1] - position[a * 3 + 1]
            val uz = position[(a + 1) * 3 + 2] - position[a * 3 + 2]
            val vx = position[(a + 2) * 3] - position[a * 3]
            val vy = position[(a + 2) * 3 + 1] - position[a * 3 + 1]
            val vz = position[(a + 2) * 3 + 2] - position[a * 3 + 2]
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            val length = sqrt(nx * nx + ny * ny + nz * nz)
            if (length > EPSILON) {
                nx /= length; ny /= length; nz /= length
            } else {
                // Triangle dégénéré : pas de normale, donc pas de rehaut.
                nx = 0.0; ny = 0.0; nz = 0.0
            }

            // L'ombrage du web, à la lettre : toits pleins, flancs assourdis, un
            // rehaut à l'est. Il est **cuit**, et le matériau n'est pas éclairé —
            // aucune lumière, aucun espace colorimétrique, aucun ordre de
            // chargement ne peut plus assombrir la flotte. C'est le défaut que le
            // web a payé une fois.
            val shade = (0.72 + 0.24 * max(0.0, nz) + 0.06 * max(0.0, nx)).toFloat()

            val piece = part[triangle] ?: MeshPart.BODY
            val r: Float
            val g: Float
            val b: Float
            val mask: Float
            if (piece == MeshPart.BODY) {
                r = shade; g = shade; b = shade; mask = 1f
            } else {
                val rgb = MeshPalette.color(piece)
                r = min(1f, (rgb shr 16 and 0xFF) / 255f * shade)
                g = min(1f, (rgb shr 8 and 0xFF) / 255f * shade)
                b = min(1f, (rgb and 0xFF) / 255f * shade)
                mask = 0f
            }

            for (corner in 0 until 3) {
                val v = a + corner
                val at = v * StandardMesh.FLOATS_PER_VERTEX
                out[at] = position[v * 3].toFloat()
                out[at + 1] = position[v * 3 + 1].toFloat()
                out[at + 2] = position[v * 3 + 2].toFloat()
                out[at + 3] = r
                out[at + 4] = g
                out[at + 5] = b
                out[at + 6] = mask
            }
        }

        return StandardMesh(out, dimensions)
    }

    private const val EPSILON = 1e-9

    /**
     * Une boîte englobante en `double` : la mise aux cotes en dépend, et un
     * `float` y perdrait des millimètres qui se voient sur un modèle de 28 m.
     */
    private class BoundingBox(
        val minX: Double, val minY: Double, val minZ: Double,
        val maxX: Double, val maxY: Double, val maxZ: Double,
    ) {
        val sizeX: Double get() = abs(maxX - minX)
        val sizeY: Double get() = abs(maxY - minY)
        val sizeZ: Double get() = abs(maxZ - minZ)

        companion object {
            fun of(position: DoubleArray): BoundingBox {
                var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var minZ = Double.MAX_VALUE
                var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
                var v = 0
                while (v < position.size) {
                    minX = min(minX, position[v]); maxX = max(maxX, position[v])
                    minY = min(minY, position[v + 1]); maxY = max(maxY, position[v + 1])
                    minZ = min(minZ, position[v + 2]); maxZ = max(maxZ, position[v + 2])
                    v += 3
                }
                return BoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
            }
        }
    }
}

