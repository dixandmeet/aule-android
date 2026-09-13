package io.aule.android.core.map3d

import android.content.res.AssetManager
import io.aule.android.core.map3d.mesh.GlbReader
import io.aule.android.core.map3d.mesh.MeshStandardizer
import io.aule.android.core.map3d.mesh.VehicleMeshCatalog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.maplibre.android.style.layers.CustomLayer

/**
 * La scène 3D des véhicules : son état natif, et ce que Kotlin lui dit.
 *
 * MapLibre n'expose pas de couche de modèles, mais son AAR livre les en-têtes de
 * `CustomLayerHost` — un point d'extension qui ne s'écrit qu'en C++. C'est toute
 * la raison d'être de ce module.
 *
 * ## Deux durées de vie, et il faut les distinguer
 *
 * L'**hôte** natif appartient à MapLibre : la `CustomLayer` l'adopte et le
 * détruit avec elle, donc un rechargement de style l'emporte. L'**état** — les
 * maillages téléversés, les poses en vol, le statut — doit survivre à cela,
 * sinon chaque bascule d'ambiance relirait les assets. D'où deux objets, et une
 * possession partagée côté C++.
 *
 * ⚠️ Ne jamais réutiliser un pointeur d'hôte d'un style à l'autre : ce serait
 * écrire dans de la mémoire libérée. La version native du piège que
 * `MapLayer.forgetStyle` décrit pour les sources, en moins clément — ici il n'y a
 * pas de garde du moteur, juste un `SIGSEGV`.
 */
class VehicleScene private constructor(private val handle: Long) {

    /**
     * Le tampon de poses, enveloppé une fois pour toutes.
     *
     * Adresse stable côté natif : on y écrit à chaque image sans allouer quoi que
     * ce soit, là où publier la flotte en GeoJSON créait des centaines d'objets
     * par seconde.
     */
    val staging: ByteBuffer = requireNotNull(nativeStagingBuffer(handle)) {
        "le tampon de poses natif n'a pas pu être exposé"
    }.order(ByteOrder.nativeOrder())

    private var released = false

    /**
     * Publie les poses écrites dans [staging] et rend l'état du rendu.
     *
     * [anchorLatitude] donne l'échelle métrique locale ; [anchorMercX] et
     * [anchorMercY] sont l'ancre en mercator normalisé. Le zoom n'est
     * volontairement pas transmis : le thread de rendu prend celui de **son**
     * image, sans quoi la flotte se décalerait d'une trame à chaque mouvement.
     */
    fun commit(
        count: Int,
        anchorMercX: Double,
        anchorMercY: Double,
        anchorLatitude: Double,
    ): SceneStatus {
        if (released) return SceneStatus.FAILED
        return SceneStatus.of(nativeCommit(handle, count, anchorMercX, anchorMercY, anchorLatitude))
    }

    /**
     * Change la lumière de la scène — à chaque bascule d'ambiance.
     *
     * Elle part avec la prochaine trame publiée ; il n'y a rien à redessiner
     * ici, la prochaine image suffit. La nuit ne passe plus par la teinte de
     * carrosserie : c'est la lumière qui baisse, comme sur les façades.
     */
    fun setLighting(lighting: VehicleLighting) {
        if (released) return
        nativeSetLighting(handle, lighting.toFloatArray())
    }

    /**
     * Une couche MapLibre portant un hôte natif **neuf**.
     *
     * À rappeler à chaque montage — voir la note sur les durées de vie.
     */
    fun layer(id: String): CustomLayer = CustomLayer(id, nativeCreateHost(handle))

    /** Libère l'état natif. La carte doit en avoir fini avec la couche. */
    fun release() {
        if (released) return
        released = true
        nativeDestroyState(handle)
    }

    enum class SceneStatus {
        /** Pas encore de contexte GL, ou il vient d'être perdu. */
        NEEDS_INIT,

        READY,

        /** Le rendu a renoncé : on reste sur le volume extrudé. */
        FAILED,
        ;

        val isReady: Boolean get() = this == READY

        internal companion object {
            fun of(raw: Int): SceneStatus = when (raw) {
                1 -> READY
                2 -> FAILED
                else -> NEEDS_INIT
            }
        }
    }

    companion object {
        /** Octets par pose — contrat avec la structure `Pose` de `scene_state.hpp`. */
        const val POSE_BYTES = 44

        /** Le plafond d'instances, aligné sur le `MAX_BODIES` de `VehiclesLayer`. */
        const val MAX_POSES = 48

        /** L'ordre dans lequel les maillages sont installés. */
        const val MESH_BUS = 0
        const val MESH_TRAM = 1

        /**
         * La teinte de carrosserie d'un maillage, en plein jour.
         *
         * ⚠️ **Ce n'est pas la couleur de la pastille, et c'est délibéré.** Un
         * aplat plat peut être sombre sans rien perdre ; un modèle ne se lit que
         * par le contraste entre sa caisse et ses pièces — vitres et châssis
         * sont presque noirs. Peint du teal sombre de la pastille tram,
         * l'ensemble devient un bloc uniforme où ni vitre ni roue n'apparaît :
         * tout le détail qu'on est allé chercher disparaît.
         */
        fun bodyColor(mesh: Int): Int = when (mesh) {
            MESH_TRAM -> VehicleMeshCatalog.TRAM.bodyColor
            else -> VehicleMeshCatalog.BUS.bodyColor
        }

        init {
            // `libmaplibre.so` est déjà en mémoire dès qu'une carte existe ; la
            // nôtre n'en dépend pas, mais l'ordre reste sans surprise.
            System.loadLibrary("aulemap3d")
        }

        /**
         * Prépare la scène : lit les `.glb`, les met aux normes, les installe.
         *
         * Rend `null` si un modèle manque ou se lit mal — la carte reste alors
         * sur son volume extrudé, ce qui est exactement le comportement
         * d'aujourd'hui. **Un échec ici ne doit jamais empêcher l'écran de
         * s'ouvrir.**
         */
        fun create(assets: AssetManager): VehicleScene? {
            val meshes = VehicleMeshCatalog.ALL.map { model ->
                val bytes = try {
                    assets.open("${VehicleMeshCatalog.ASSET_DIR}/${model.asset}").use { it.readBytes() }
                } catch (error: java.io.IOException) {
                    return null
                }
                val primitives = GlbReader.read(bytes)
                if (primitives.isEmpty()) return null
                MeshStandardizer.standardize(
                    primitives,
                    model.dimensions,
                    model.forwardIsPositiveZ,
                    model.materialParts,
                ).also { if (it.vertexCount == 0) return null }
            }

            val handle = nativeCreateState()
            if (handle == 0L) return null
            meshes.forEachIndexed { index, mesh -> nativeInstallMesh(handle, index, mesh.vertices) }
            return VehicleScene(handle)
        }

        @JvmStatic private external fun nativeCreateState(): Long

        @JvmStatic private external fun nativeDestroyState(handle: Long)

        @JvmStatic private external fun nativeInstallMesh(handle: Long, index: Int, data: FloatArray)

        @JvmStatic private external fun nativeSetLighting(handle: Long, data: FloatArray)

        @JvmStatic private external fun nativeStagingBuffer(handle: Long): ByteBuffer?

        @JvmStatic private external fun nativeCommit(
            handle: Long,
            count: Int,
            anchorMercX: Double,
            anchorMercY: Double,
            anchorLatitude: Double,
        ): Int

        @JvmStatic private external fun nativeCreateHost(handle: Long): Long
    }
}
