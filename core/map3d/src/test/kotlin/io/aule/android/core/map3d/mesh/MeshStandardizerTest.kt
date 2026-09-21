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
            // Le code de pièce dit ce qu'est chaque sommet : on ne garde que le vitrage.
            if (mesh.vertices[at + StandardMesh.PART_OFFSET] != MeshPart.GLASS.code) continue
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
     * Le contrat avec le nuancier : dix flottants par sommet, une normale
     * unitaire, des couleurs dans l'intervalle, un code de pièce connu. Le
     * shader ne vérifie rien de tout cela.
     */
    @Test
    fun `la disposition des sommets respecte le contrat du nuancier`() {
        val codes = MeshPart.entries.map { it.code }.toSet()
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
                val nx = mesh.vertices[at + StandardMesh.NORMAL_OFFSET]
                val ny = mesh.vertices[at + StandardMesh.NORMAL_OFFSET + 1]
                val nz = mesh.vertices[at + StandardMesh.NORMAL_OFFSET + 2]
                assertEquals(
                    1.0,
                    kotlin.math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()),
                    1e-3,
                    "${model.asset} : normale non unitaire",
                )
                for (channel in StandardMesh.COLOR_OFFSET until StandardMesh.PART_OFFSET) {
                    val value = mesh.vertices[at + channel]
                    assertTrue(
                        value in 0f..1f,
                        "${model.asset} : couleur hors bornes ($value)",
                    )
                }
                val part = mesh.vertices[at + StandardMesh.PART_OFFSET]
                assertTrue(part in codes, "${model.asset} : pièce inconnue ($part)")
            }
        }
    }

    /**
     * Les roues du bus sont du châssis, pas de la carrosserie.
     *
     * Elles portent le matériau générique `Material` : seul le nom du maillage
     * les distingue. Un bus aux jantes vert de ligne, c'est ce que le web
     * affiche — et ce que la première version d'ici affichait aussi.
     */
    @Test
    fun `les roues du bus sont du chassis`() {
        val primitives = GlbReader.read(
            File("src/main/assets/${VehicleMeshCatalog.ASSET_DIR}/${VehicleMeshCatalog.BUS.asset}").readBytes(),
        )
        val wheels = primitives.filter { "wheel" in it.meshName.lowercase() }
        assertEquals(2, wheels.size, "deux trains de roues attendus, nommés dans le fichier")
        for (wheel in wheels) {
            assertEquals(MeshPart.CHASSIS, MeshPalette.part(wheel.materialName, wheel.meshName))
        }
    }

    /**
     * Les normales regardent vers l'**extérieur**.
     *
     * Une normale retournée n'empêche rien de compiler ni de s'afficher : elle
     * rend noire la face qui devrait être en plein jour. On ne peut pas compter
     * les faces « vers le haut » — le toit du bus porte des blocs dont le
     * dessous, caché, regarde le sol. Le volume signé, lui, ne se trompe pas :
     * positif si les triangles tournent dans le sens direct vus de dehors, ce
     * qui est exactement la convention sur laquelle la normale est calculée.
     * Les rotations de la mise aux normes le conservent, et les échelles sont
     * positives : le signe du fichier est celui de la scène.
     */
    @Test
    fun `les normales regardent vers l exterieur`() {
        for (model in VehicleMeshCatalog.ALL) {
            val mesh = load(model)
            var volume = 0.0
            for (t in 0 until mesh.triangleCount) {
                val at = t * 3 * StandardMesh.FLOATS_PER_VERTEX
                val ax = mesh.vertices[at].toDouble()
                val ay = mesh.vertices[at + 1].toDouble()
                val az = mesh.vertices[at + 2].toDouble()
                val bx = mesh.vertices[at + StandardMesh.FLOATS_PER_VERTEX].toDouble()
                val by = mesh.vertices[at + StandardMesh.FLOATS_PER_VERTEX + 1].toDouble()
                val bz = mesh.vertices[at + StandardMesh.FLOATS_PER_VERTEX + 2].toDouble()
                val cx = mesh.vertices[at + 2 * StandardMesh.FLOATS_PER_VERTEX].toDouble()
                val cy = mesh.vertices[at + 2 * StandardMesh.FLOATS_PER_VERTEX + 1].toDouble()
                val cz = mesh.vertices[at + 2 * StandardMesh.FLOATS_PER_VERTEX + 2].toDouble()
                volume += ax * (by * cz - bz * cy) + ay * (bz * cx - bx * cz) + az * (bx * cy - by * cx)
            }
            volume /= 6.0
            val box = model.dimensions.widthMeters * model.dimensions.lengthMeters * model.dimensions.heightMeters
            assertTrue(
                volume > box * 0.2,
                "${model.asset} : volume signé $volume pour une boîte de $box — triangles retournés ?",
            )
        }
    }

    /**
     * **Le toit regarde le ciel.**
     *
     * Une invariante que rien d'autre ne tient : le nuancier éclaire à l'ambiante de la
     * chaussée toute face tournée vers le sol, donc une grande surface haute retournée rend
     * un toit plus sombre que ses flancs — et l'œil lit cette inversion comme un véhicule
     * couché sur le dos.
     *
     * ⚠️ **Ce test ne dit rien du sens des faces à l'écran.** Il éprouve le maillage, pas
     * le rendu : les triangles peuvent être parfaitement orientés ici et le pilote peindre
     * l'**intérieur** des caisses, ce qui est exactement ce qui est arrivé le 16/09/2026
     * (`glFrontFace` inversé, voir `vehicle_layer.cpp`). Aucune épreuve JVM ne peut
     * l'attraper ; seul l'œil sur l'appareil le fait.
     */
    @Test
    fun `aucune face haute ne regarde le sol`() {
        for (model in VehicleMeshCatalog.ALL) {
            val mesh = load(model)
            val haut = model.dimensions.heightMeters * 0.8
            var versLeCiel = 0.0
            var versLeSol = 0.0
            for (t in 0 until mesh.triangleCount) {
                val at = t * 3 * StandardMesh.FLOATS_PER_VERTEX
                val z = (0 until 3)
                    .map { mesh.vertices[at + it * StandardMesh.FLOATS_PER_VERTEX + 2] }
                    .average()
                if (z < haut) continue
                val nz = mesh.vertices[at + StandardMesh.NORMAL_OFFSET + 2].toDouble()
                val aire = areaOf(mesh, t)
                if (nz > 0.5) versLeCiel += aire
                if (nz < -0.5) versLeSol += aire
            }
            assertTrue(versLeCiel > 1.0, "${model.asset} : pas de toit au-dessus de $haut m ?")
            // ⚠️ **La marge n'est pas de la complaisance, elle est mesurée.** Le toit du bus
            // garde 1,5 m² de faces tournées vers le sol pour 23,9 m² tournées vers le ciel —
            // des dessous de trappes et de blocs, jamais vus, et qu'il serait faux d'accuser.
            // Le défaut qu'on garde, lui, n'est pas de cet ordre : le toit du tram entier était
            // retourné, soit **quinze mètres carrés** sur un toit qui en fait quarante-cinq.
            // Entre 6 % et 100 %, le seuil n'a pas besoin d'être fin.
            assertTrue(
                versLeSol < versLeCiel * 0.15,
                "${model.asset} : ${"%.1f".format(versLeSol)} m² de surface haute regarde le sol" +
                    " pour ${"%.1f".format(versLeCiel)} m² qui regarde le ciel — le toit est" +
                    " bobiné à l'envers, et il s'éclairera comme un dessous",
            )
        }
    }

    /** L'aire d'un triangle du maillage mis aux normes, en mètres carrés. */
    private fun areaOf(mesh: StandardMesh, triangle: Int): Double {
        val at = triangle * 3 * StandardMesh.FLOATS_PER_VERTEX
        fun coord(corner: Int, axis: Int) =
            mesh.vertices[at + corner * StandardMesh.FLOATS_PER_VERTEX + axis].toDouble()
        val ux = coord(1, 0) - coord(0, 0)
        val uy = coord(1, 1) - coord(0, 1)
        val uz = coord(1, 2) - coord(0, 2)
        val vx = coord(2, 0) - coord(0, 0)
        val vy = coord(2, 1) - coord(0, 1)
        val vz = coord(2, 2) - coord(0, 2)
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        return 0.5 * kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
    }

    /**
     * **La carrosserie doit dominer la surface du modèle.**
     *
     * C'est le test que l'absence a coûté cher. Les noms de matériaux du pack
     * mentent : sur le bus, `Bottom` est le panneau latéral inférieur sur toute
     * la longueur et `Bumper` la deuxième plus grande surface du modèle. Les
     * ranger au châssis — ce que fait l'heuristique du web — peignait **la
     * moitié du bus en presque noir**, une coque sombre surmontée d'une
     * verrière. Vu à l'écran le 12/09, pas en lisant le code.
     *
     * Le seuil vaut pour ce que le regard attend d'un véhicule : une livrée,
     * avec des vitres et des roues dessus — pas l'inverse.
     */
    @Test
    fun `la carrosserie couvre la majorite de la surface`() {
        for (model in VehicleMeshCatalog.ALL) {
            val mesh = load(model)
            val aire = DoubleArray(MeshPart.entries.size)
            for (t in 0 until mesh.triangleCount) {
                val at = t * 3 * StandardMesh.FLOATS_PER_VERTEX
                fun coord(corner: Int, axis: Int) =
                    mesh.vertices[at + corner * StandardMesh.FLOATS_PER_VERTEX + axis].toDouble()
                val ux = coord(1, 0) - coord(0, 0)
                val uy = coord(1, 1) - coord(0, 1)
                val uz = coord(1, 2) - coord(0, 2)
                val vx = coord(2, 0) - coord(0, 0)
                val vy = coord(2, 1) - coord(0, 1)
                val vz = coord(2, 2) - coord(0, 2)
                val nx = uy * vz - uz * vy
                val ny = uz * vx - ux * vz
                val nz = ux * vy - uy * vx
                val code = mesh.vertices[at + StandardMesh.PART_OFFSET]
                val piece = MeshPart.entries.first { it.code == code }
                aire[piece.ordinal] += 0.5 * kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
            }
            val total = aire.sum()
            val part = aire[MeshPart.BODY.ordinal] / total
            assertTrue(
                part > 0.5,
                "${model.asset} : la carrosserie ne couvre que ${(part * 100).toInt()} % de la surface —" +
                    " le reste est peint en pièce sombre, le véhicule se lira comme une coque noire",
            )
        }
    }

    /**
     * **Le noir est réservé aux roues.**
     *
     * « On ne doit pas voir le châssis » : vu du ciel, un véhicule est une
     * caisse, des vitres et des roues. Toute pièce sombre qui déborde de ce
     * compte se lit comme une carcasse posée sous la carrosserie — le défaut
     * exact que montrait la flotte avant le 12/09. Les jupes et pare-chocs
     * prennent donc la livrée assombrie ([MeshPart.SKIRT]), qui appartient
     * visuellement au véhicule.
     */
    @Test
    fun `seules les roues sont peintes en sombre`() {
        for (model in VehicleMeshCatalog.ALL) {
            val mesh = load(model)
            val aire = DoubleArray(MeshPart.entries.size)
            for (t in 0 until mesh.triangleCount) {
                val at = t * 3 * StandardMesh.FLOATS_PER_VERTEX
                fun coord(corner: Int, axis: Int) =
                    mesh.vertices[at + corner * StandardMesh.FLOATS_PER_VERTEX + axis].toDouble()
                val ux = coord(1, 0) - coord(0, 0)
                val uy = coord(1, 1) - coord(0, 1)
                val uz = coord(1, 2) - coord(0, 2)
                val vx = coord(2, 0) - coord(0, 0)
                val vy = coord(2, 1) - coord(0, 1)
                val vz = coord(2, 2) - coord(0, 2)
                val nx = uy * vz - uz * vy
                val ny = uz * vx - ux * vz
                val nz = ux * vy - uy * vx
                val code = mesh.vertices[at + StandardMesh.PART_OFFSET]
                val piece = MeshPart.entries.first { it.code == code }
                aire[piece.ordinal] += 0.5 * kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
            }
            val total = aire.sum()
            val sombre = aire[MeshPart.CHASSIS.ordinal] / total
            assertTrue(
                sombre < 0.12,
                "${model.asset} : ${(sombre * 100).toInt()} % de la surface est peinte en pièce" +
                    " sombre — au-delà des roues, ça se lit comme un châssis apparent",
            )
        }
    }

    /**
     * **L'avant du bus est du côté de ses roues avant.**
     *
     * Le modèle porte deux maillages nommés : `FrontWheels` est à x ≈ 1,2–2,1 m
     * dans le fichier, `BackWheels` à x ≈ 8,9–9,8 m. Après redressement, l'avant
     * doit regarder le nord de la scène (+Y). À l'envers, le bus recule le long
     * de sa ligne — invisible à l'arrêt, évident en mouvement, et le tram a déjà
     * son propre test pour la même raison.
     */
    @Test
    fun `les roues avant du bus regardent vers l avant`() {
        val model = VehicleMeshCatalog.BUS
        val primitives = GlbReader.read(
            File("src/main/assets/${VehicleMeshCatalog.ASSET_DIR}/${model.asset}").readBytes(),
        )
        val avant = primitives.first { "frontwheel" in it.meshName.lowercase().replace("_", "") }
        val arriere = primitives.first { "backwheel" in it.meshName.lowercase().replace("_", "") }

        // Les deux trains ensemble : mis aux normes séparément, chacun se
        // recentrerait sur zéro et la comparaison ne dirait plus rien.
        val ensemble = MeshStandardizer.standardize(
            listOf(avant, arriere), model.dimensions, model.forwardIsPositiveZ, model.materialParts,
        )
        val moitie = ensemble.vertexCount / 2
        var yAvant = 0.0
        var yArriere = 0.0
        for (v in 0 until moitie) yAvant += ensemble.vertices[v * StandardMesh.FLOATS_PER_VERTEX + 1]
        for (v in moitie until ensemble.vertexCount) yArriere += ensemble.vertices[v * StandardMesh.FLOATS_PER_VERTEX + 1]
        assertTrue(
            yAvant / moitie > yArriere / (ensemble.vertexCount - moitie),
            "le bus roule en marche arrière : roues avant à ${yAvant / moitie}, arrière à ${yArriere / (ensemble.vertexCount - moitie)}",
        )
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
