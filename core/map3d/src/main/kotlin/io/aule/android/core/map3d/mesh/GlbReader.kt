package io.aule.android.core.map3d.mesh

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ce qu'on retient d'un `.glb` : des triangles, et le nom du matériau qui les porte.
 *
 * **Pourquoi un lecteur maison plutôt qu'une bibliothèque glTF.** On ne lit de ces
 * fichiers que les positions, les indices et le nom des matériaux — trois champs
 * sur une spécification qui en compte des centaines. Une dépendance glTF
 * apporterait les animations, les peaux, les textures, les extensions, et le
 * risque de les voir bouger sous nous. C'est aussi le choix d'iOS
 * (`Native/Aule/Core/Map/Render3D/GLBLoader.swift`), pour les mêmes raisons.
 *
 * Le fichier est du calcul pur — ni Android, ni MapLibre — donc vérifiable sur la
 * JVM de l'hôte.
 */
internal class GlbPrimitive(
    /** Positions en repère glTF, par triplets `x, y, z`. */
    val positions: FloatArray,
    /** Indices de sommets, trois par triangle. */
    val indices: IntArray,
    /** Le nom du matériau, vide s'il n'en porte pas. */
    val materialName: String,
    /**
     * Le nom du maillage qui porte la primitive, vide s'il n'en a pas.
     *
     * Il compte autant que le matériau : les roues du bus portent le matériau
     * générique `Material`, et seul le nom `FrontWheels` / `BackWheels` dit
     * ce qu'elles sont. Sans lui, elles se peignaient aux couleurs de la ligne.
     */
    val meshName: String = "",
) {
    val triangleCount: Int get() = indices.size / 3
}

internal object GlbReader {

    /** `glTF` en petit-boutiste — les quatre premiers octets de tout `.glb`. */
    private const val MAGIC = 0x46546C67
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942

    private const val COMPONENT_UNSIGNED_BYTE = 5121
    private const val COMPONENT_UNSIGNED_SHORT = 5123
    private const val COMPONENT_UNSIGNED_INT = 5125
    private const val COMPONENT_FLOAT = 5126

    /** Le mode `TRIANGLES` de glTF, et le défaut de la spécification. */
    private const val MODE_TRIANGLES = 4

    private const val HEADER_BYTES = 12
    private const val CHUNK_HEADER_BYTES = 8

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Lit les primitives triangulaires d'un `.glb`.
     *
     * Rend une liste **vide** plutôt que de lever si le fichier n'est pas
     * exploitable : un modèle abîmé doit laisser la carte tourner sur son chemin
     * de repli, pas empêcher l'écran de s'ouvrir.
     */
    fun read(bytes: ByteArray): List<GlbPrimitive> = try {
        parse(bytes)
    } catch (error: RuntimeException) {
        // Un `.glb` malformé lève de bien des façons — indice hors bornes, champ
        // absent, tampon trop court, JSON invalide. Aucune ne mérite d'emporter
        // l'application, et le repli couvre exactement ce cas.
        emptyList()
    }

    private fun parse(bytes: ByteArray): List<GlbPrimitive> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.remaining() < HEADER_BYTES || buffer.int != MAGIC) return emptyList()
        buffer.int // version : la 2 est la seule qui existe
        buffer.int // longueur totale, redondante avec la taille du tableau

        var document: JsonObject? = null
        var binary: ByteBuffer? = null
        while (buffer.remaining() >= CHUNK_HEADER_BYTES) {
            val length = buffer.int
            val type = buffer.int
            if (length < 0 || length > buffer.remaining()) return emptyList()
            when (type) {
                CHUNK_JSON -> {
                    val raw = ByteArray(length)
                    buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).get(raw)
                    document = json.parseToJsonElement(String(raw, Charsets.UTF_8)).jsonObject
                }
                CHUNK_BIN -> binary = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).also {
                    it.limit(length)
                }
            }
            buffer.position(buffer.position() + length)
        }

        val root = document ?: return emptyList()
        val bin = binary ?: return emptyList()
        return primitivesOf(root, bin)
    }

    private fun primitivesOf(root: JsonObject, bin: ByteBuffer): List<GlbPrimitive> {
        val accessors = root.array("accessors")
        val views = root.array("bufferViews")
        val materials = root.array("materials")

        val out = ArrayList<GlbPrimitive>()
        for (mesh in root.array("meshes")) {
            val meshName = (mesh as? JsonObject)?.string("name").orEmpty()
            for (primitive in (mesh as? JsonObject)?.array("primitives").orEmpty()) {
                val node = primitive as? JsonObject ?: continue
                // Bandes, éventails et lignes n'ont rien à faire dans ce pack ; les
                // accepter en silence produirait un maillage replié sur lui-même.
                if ((node.int("mode") ?: MODE_TRIANGLES) != MODE_TRIANGLES) continue

                val attributes = node["attributes"] as? JsonObject ?: continue
                val positionIndex = attributes.int("POSITION") ?: continue
                val positions = readPositions(accessors, views, bin, positionIndex) ?: continue

                val indices = node.int("indices")
                    ?.let { readIndices(accessors, views, bin, it) }
                    // Sans indices, les sommets se suivent dans l'ordre du tampon.
                    ?: IntArray(positions.size / 3) { it }
                if (indices.size < 3) continue

                val materialName = node.int("material")
                    ?.let { (materials.getOrNull(it) as? JsonObject)?.string("name") }
                    .orEmpty()

                out += GlbPrimitive(positions, indices, materialName, meshName)
            }
        }
        return out
    }

    private fun readPositions(
        accessors: JsonArray,
        views: JsonArray,
        bin: ByteBuffer,
        index: Int,
    ): FloatArray? {
        val accessor = accessors.getOrNull(index) as? JsonObject ?: return null
        if (accessor.int("componentType") != COMPONENT_FLOAT) return null
        if (accessor.string("type") != "VEC3") return null
        val count = accessor.int("count") ?: return null
        val view = views.getOrNull(accessor.int("bufferView") ?: return null) as? JsonObject
            ?: return null

        val start = (view.int("byteOffset") ?: 0) + (accessor.int("byteOffset") ?: 0)
        // Un `byteStride` absent ou nul veut dire « serré » : trois flottants par
        // sommet, sans rembourrage.
        val stride = view.int("byteStride")?.takeIf { it > 0 } ?: (3 * Float.SIZE_BYTES)

        val out = FloatArray(count * 3)
        for (vertex in 0 until count) {
            val base = start + vertex * stride
            if (base < 0 || base + 3 * Float.SIZE_BYTES > bin.limit()) return null
            out[vertex * 3] = bin.getFloat(base)
            out[vertex * 3 + 1] = bin.getFloat(base + Float.SIZE_BYTES)
            out[vertex * 3 + 2] = bin.getFloat(base + 2 * Float.SIZE_BYTES)
        }
        return out
    }

    private fun readIndices(
        accessors: JsonArray,
        views: JsonArray,
        bin: ByteBuffer,
        index: Int,
    ): IntArray? {
        val accessor = accessors.getOrNull(index) as? JsonObject ?: return null
        val componentType = accessor.int("componentType") ?: return null
        val count = accessor.int("count") ?: return null
        val view = views.getOrNull(accessor.int("bufferView") ?: return null) as? JsonObject
            ?: return null

        val size = when (componentType) {
            COMPONENT_UNSIGNED_BYTE -> Byte.SIZE_BYTES
            COMPONENT_UNSIGNED_SHORT -> Short.SIZE_BYTES
            COMPONENT_UNSIGNED_INT -> Int.SIZE_BYTES
            else -> return null
        }
        val start = (view.int("byteOffset") ?: 0) + (accessor.int("byteOffset") ?: 0)
        if (start < 0 || start + count * size > bin.limit()) return null

        val out = IntArray(count)
        for (i in 0 until count) {
            val at = start + i * size
            // Les masques sont là pour une raison : `toInt()` sur un `Short`
            // négatif signerait l'indice, et un maillage de plus de 32 767
            // sommets se replierait sur lui-même.
            out[i] = when (componentType) {
                COMPONENT_UNSIGNED_BYTE -> bin.get(at).toInt() and 0xFF
                COMPONENT_UNSIGNED_SHORT -> bin.getShort(at).toInt() and 0xFFFF
                else -> bin.getInt(at)
            }
        }
        return out
    }

    private fun JsonObject.array(key: String): JsonArray =
        (this[key] as? JsonArray) ?: JsonArray(emptyList())

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
