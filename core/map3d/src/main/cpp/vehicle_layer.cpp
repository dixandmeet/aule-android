/**
 * La couche native des véhicules : des modèles, pas des volumes.
 *
 * ## Le repère
 *
 * `CustomLayerRenderParameters` livre une matrice dont l'espace d'entrée n'est
 * **pas homogène** — c'est le premier piège, et il est vérifié sur cet appareil,
 * pas supposé :
 *
 * - **x et y en pixels-monde**, mercator normalisé × `512 · 2^zoom`, l'axe y
 *   croissant vers le **sud** ;
 * - **z en mètres**, parce que la matrice porte déjà la conversion verticale —
 *   celle qui fait fonctionner les `fill-extrusion` du style.
 *
 * Mettre les trois axes à la même échelle multiplie les hauteurs par un facteur
 * qui **double à chaque niveau de zoom** : un bus de trois mètres devient une
 * tour de cent à z18. Mesuré ici, et documenté de la même façon côté iOS
 * (`Native/Aule/Core/Map/Render3D/MapMercator.swift`).
 *
 * ⚠️ `pitch` et `bearing` de ces paramètres sont en **radians**, pas en degrés.
 * Lus en degrés, on croit la carte à plat et on cherche un défaut qui n'existe pas.
 *
 * ## L'ancrage
 *
 * À z18, `worldSize` vaut 134 millions de pixels et Nantes tombe vers
 * x ≈ 66 500 000. Un `float` n'y garde que quelques pixels : la flotte
 * tremblerait à chaque mouvement de caméra. La matrice se compose donc **en
 * double** jusqu'à un repère ancré près du centre de l'écran, et ne descend en
 * `float` qu'une fois les grands nombres annulés. Les sommets et les instances
 * n'y voient plus que des mètres relatifs.
 *
 * ## ES 2.0
 *
 * `EGLContextFactory` de MapLibre demande `EGL_CONTEXT_CLIENT_VERSION 2`
 * (vérifié au bytecode). Le Mali-G78 rend en fait de l'ES 3.2, mais rien ne
 * l'oblige : GLSL ES 1.00, pas de VAO, pas d'instanciation. Quarante-huit
 * `glDrawArrays` de mille cinq cents triangles ne coûtent rien.
 */

#include <jni.h>

#include <GLES2/gl2.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <memory>
#include <vector>

#include <mbgl/style/layers/custom_layer_host.hpp>

#include "scene_state.hpp"

#define LOG_TAG "AuleMap3d"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

using aule::Frame;
using aule::Pose;
using aule::SceneState;
using aule::SceneStatus;

/// Le tour de la Terre à l'équateur — la valeur du sphéroïde Web Mercator, pas
/// un rayon moyen : celui-ci est bon pour une haversine, faux pour une projection.
constexpr double kEquatorMeters = 2.0 * M_PI * 6378137.0;

constexpr double kTileSize = 512.0;

/// Produit de deux matrices 4×4 en colonnes majeures — la disposition de `mbgl::mat4`.
template <typename T>
void multiply(T* out, const T* a, const T* b) {
    for (int c = 0; c < 4; ++c) {
        for (int r = 0; r < 4; ++r) {
            T sum = T(0);
            for (int k = 0; k < 4; ++k) {
                sum += a[k * 4 + r] * b[c * 4 + k];
            }
            out[c * 4 + r] = sum;
        }
    }
}

GLuint compile(GLenum type, const char* source) {
    const GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint ok = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (ok != GL_TRUE) {
        char log[1024] = {0};
        glGetShaderInfoLog(shader, sizeof(log) - 1, nullptr, log);
        LOGE("compilation du nuancier refusée : %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

// Le nuancier, transposé de `Shaders.metal` d'iOS.
//
// `a_color.rgb` porte soit la couleur déjà ombrée d'une pièce fixe, soit
// l'ombrage seul pour la carrosserie ; `a_color.a` est le masque qui distingue
// les deux. C'est ce masque qui permet **un seul maillage par modèle** quelle
// que soit la livrée : cuire la couleur de ligne dans les sommets demanderait un
// tampon par ligne.
//
// Aucune lumière. C'est délibéré : l'éclairage du web dépendait de l'ordre de
// chargement et de l'espace colorimétrique actif, et rendait la flotte sombre
// une fois sur deux. Le modelé est cuit, plus rien ne peut l'assombrir.
const char* kVertexShader = R"(
attribute vec3 a_position;
attribute vec4 a_color;
uniform mat4 u_viewProjection;
uniform mat4 u_model;
uniform vec4 u_tint;
varying vec4 v_color;
void main() {
    gl_Position = u_viewProjection * (u_model * vec4(a_position, 1.0));
    float shade = a_color.r;
    vec3 painted = mix(a_color.rgb, u_tint.rgb * shade, a_color.a);
    // Alpha prémultiplié : MapLibre compose ainsi. Une couleur droite cernerait
    // les véhicules translucides d'un halo sombre.
    v_color = vec4(painted * u_tint.a, u_tint.a);
}
)";

const char* kFragmentShader = R"(
precision mediump float;
varying vec4 v_color;
void main() {
    gl_FragColor = v_color;
}
)";

class VehicleSceneHost : public mbgl::style::CustomLayerHost {
public:
    explicit VehicleSceneHost(std::shared_ptr<SceneState> state) : state_(std::move(state)) {}

    void initialize(const mbgl::style::CustomLayerInitParameters&) override {
        LOGI("initialize — %s / %s",
             reinterpret_cast<const char*>(glGetString(GL_VERSION)),
             reinterpret_cast<const char*>(glGetString(GL_RENDERER)));

        const GLuint vertex = compile(GL_VERTEX_SHADER, kVertexShader);
        const GLuint fragment = compile(GL_FRAGMENT_SHADER, kFragmentShader);
        if (vertex == 0 || fragment == 0) {
            state_->setStatus(SceneStatus::Failed);
            return;
        }

        program_ = glCreateProgram();
        glAttachShader(program_, vertex);
        glAttachShader(program_, fragment);
        glLinkProgram(program_);
        GLint linked = GL_FALSE;
        glGetProgramiv(program_, GL_LINK_STATUS, &linked);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (linked != GL_TRUE) {
            char log[1024] = {0};
            glGetProgramInfoLog(program_, sizeof(log) - 1, nullptr, log);
            LOGE("édition de liens refusée : %s", log);
            glDeleteProgram(program_);
            program_ = 0;
            state_->setStatus(SceneStatus::Failed);
            return;
        }

        viewProjectionUniform_ = glGetUniformLocation(program_, "u_viewProjection");
        modelUniform_ = glGetUniformLocation(program_, "u_model");
        tintUniform_ = glGetUniformLocation(program_, "u_tint");
        positionAttrib_ = glGetAttribLocation(program_, "a_position");
        colorAttrib_ = glGetAttribLocation(program_, "a_color");

        uploadMeshes();

        if (!state_->hasAllMeshes()) {
            // Les maillages arrivent depuis Kotlin ; s'ils ne sont pas encore là,
            // ce n'est pas un échec — `render` retentera le téléversement.
            LOGI("initialize — maillages pas encore installés");
            state_->setStatus(SceneStatus::NeedsInit);
            return;
        }
        state_->setStatus(SceneStatus::Ready);
        LOGI("initialize terminé — programme %u", program_);
    }

    void render(const mbgl::style::CustomLayerRenderParameters& p) override {
        if (program_ == 0) return;
        if (state_->consumeMeshesChanged()) uploadMeshes();
        if (!state_->hasAllMeshes()) return;
        if (buffers_[0] == 0 || buffers_[1] == 0) return;
        state_->setStatus(SceneStatus::Ready);

        const Frame* frame = state_->acquire();
        if (frame == nullptr || frame->count == 0) return;

        // `worldSize` vient du zoom de **cette** image, l'ancre de la trame
        // publiée. Une ancre en retard d'une trame est exactement neutre — le
        // décalage et l'origine se recomposent en la coordonnée mercator
        // absolue — alors qu'un `worldSize` périmé décalerait toute la flotte.
        const double worldSize = kTileSize * std::pow(2.0, p.zoom);
        const double metersToWorldPixels =
            worldSize / (kEquatorMeters * std::cos(frame->anchorLatitude * M_PI / 180.0));

        const double sceneToProjection[16] = {
            metersToWorldPixels, 0.0, 0.0, 0.0,
            // Le nord de la scène va vers le sud de mercator : le signe vit ici,
            // une seule fois. Le recomposer ailleurs met la scène en miroir.
            0.0, -metersToWorldPixels, 0.0, 0.0,
            // L'altitude reste en mètres : la matrice de MapLibre porte déjà sa
            // conversion.
            0.0, 0.0, 1.0, 0.0,
            frame->anchorMercX * worldSize, frame->anchorMercY * worldSize, 0.0, 1.0,
        };

        double composed[16];
        multiply(composed, p.nearClippedProjectionMatrix.data(), sceneToProjection);
        float viewProjection[16];
        for (int i = 0; i < 16; ++i) viewProjection[i] = static_cast<float>(composed[i]);

        saveState();
        glUseProgram(program_);
        glUniformMatrix4fv(viewProjectionUniform_, 1, GL_FALSE, viewProjection);

        // On **garde** l'occlusion : `nearClippedProjectionMatrix` est celle des
        // `fill-extrusion`, donc du même espace de profondeur que les bâtiments.
        // Un bus derrière un immeuble passe derrière l'immeuble — c'est ce que
        // l'extrusion offrait déjà, et ce qu'iOS a tranché de son côté. Le web
        // efface la profondeur, mais parce qu'il greffe une scène three.js dans
        // la passe : une contrainte que nous n'avons pas.
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        // Sans faces arrière écartées, un solide fermé reste juste tant que la
        // profondeur écrit ; la translucidité, elle, doublerait. On trie donc les
        // instances et on écarte l'arrière — voir `drawPass`.
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        // La scène retourne l'axe nord-sud, donc l'orientation des triangles.
        glFrontFace(GL_CW);

        glEnableVertexAttribArray(static_cast<GLuint>(positionAttrib_));
        glEnableVertexAttribArray(static_cast<GLuint>(colorAttrib_));

        // Deux passes. Les opaques d'abord, profondeur en écriture : elles posent
        // le relief. Les translucides ensuite, profondeur en lecture seule et
        // **triées du plus lointain au plus proche** — sans ce tri, un bus
        // derrière un autre se composerait par-dessus lui.
        drawPass(*frame, viewProjection, /* opaque */ true);
        drawPass(*frame, viewProjection, /* opaque */ false);

        glDisableVertexAttribArray(static_cast<GLuint>(positionAttrib_));
        glDisableVertexAttribArray(static_cast<GLuint>(colorAttrib_));
        restoreState();
    }

    void contextLost() override {
        // Le contexte n'existe plus : `glDelete*` y serait au mieux inutile. On
        // oublie les identifiants ; `initialize` sera rappelé, et les maillages
        // sont conservés côté processeur précisément pour ce moment-là.
        LOGI("contextLost — objets GL oubliés, maillages conservés");
        program_ = 0;
        buffers_[0] = 0;
        buffers_[1] = 0;
        state_->setStatus(SceneStatus::NeedsInit);
    }

    void deinitialize() override {
        // Peut être appelée sans `initialize` préalable : la spécification le dit.
        if (buffers_[0] != 0 || buffers_[1] != 0) glDeleteBuffers(2, buffers_);
        if (program_ != 0) glDeleteProgram(program_);
        program_ = 0;
        buffers_[0] = 0;
        buffers_[1] = 0;
        state_->setStatus(SceneStatus::NeedsInit);
    }

private:
    void uploadMeshes() {
        if (!state_->hasAllMeshes()) return;
        if (buffers_[0] == 0) glGenBuffers(2, buffers_);
        for (uint32_t i = 0; i < aule::kMeshCount; ++i) {
            const std::vector<float>& mesh = state_->mesh(i);
            vertexCount_[i] = static_cast<GLsizei>(mesh.size() / aule::kFloatsPerVertex);
            glBindBuffer(GL_ARRAY_BUFFER, buffers_[i]);
            glBufferData(GL_ARRAY_BUFFER,
                         static_cast<GLsizeiptr>(mesh.size() * sizeof(float)),
                         mesh.data(),
                         GL_STATIC_DRAW);
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        LOGI("maillages téléversés — %d et %d sommets", vertexCount_[0], vertexCount_[1]);
    }

    void drawPass(const Frame& frame, const float* viewProjection, bool opaque) {
        // Les instances de la passe, triées si elles sont translucides.
        order_.clear();
        for (uint32_t i = 0; i < frame.count; ++i) {
            const bool isOpaque = frame.poses[i].a >= 0.999f;
            if (isOpaque == opaque) order_.push_back(i);
        }
        if (order_.empty()) return;

        glDepthMask(opaque ? GL_TRUE : GL_FALSE);

        if (!opaque) {
            // La profondeur projetée sert de clé : plus grande d'abord, donc du
            // plus lointain au plus proche.
            std::sort(order_.begin(), order_.end(), [&](uint32_t a, uint32_t b) {
                return depthOf(frame.poses[a], viewProjection) >
                    depthOf(frame.poses[b], viewProjection);
            });
        }

        GLuint bound = 0;
        for (const uint32_t index : order_) {
            const Pose& pose = frame.poses[index];
            const uint32_t mesh = pose.mesh < aule::kMeshCount ? pose.mesh : 0;
            if (vertexCount_[mesh] == 0) continue;

            if (buffers_[mesh] != bound) {
                bound = buffers_[mesh];
                glBindBuffer(GL_ARRAY_BUFFER, bound);
                const GLsizei stride = aule::kFloatsPerVertex * sizeof(float);
                glVertexAttribPointer(static_cast<GLuint>(positionAttrib_), 3, GL_FLOAT, GL_FALSE,
                                      stride, reinterpret_cast<void*>(0));
                // Quatre composantes à partir du quatrième flottant : `r g b` et
                // le masque. Le contrat avec `MeshStandardizer`.
                glVertexAttribPointer(static_cast<GLuint>(colorAttrib_), 4, GL_FLOAT, GL_FALSE,
                                      stride, reinterpret_cast<void*>(sizeof(float) * 3));
            }

            float model[16];
            modelMatrix(pose, model);
            glUniformMatrix4fv(modelUniform_, 1, GL_FALSE, model);
            glUniform4f(tintUniform_, pose.r, pose.g, pose.b, pose.a);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount_[mesh]);
        }
    }

    /**
     * La pose d'une instance : une rotation de cap, puis une mise à l'échelle du
     * modèle, puis une position.
     *
     * Le cap est un **azimut** — sens des aiguilles depuis le nord — alors que la
     * rotation mathématique tourne dans l'autre sens : d'où le signe. L'oublier
     * fait rouler toute la flotte en miroir, ce qui ne se remarque que dans les
     * virages.
     *
     * Rotation **puis** échelle (`R · S`). Dans l'autre ordre, l'échelle
     * s'appliquerait aux axes de la carte, et un bus en diagonale s'étirerait
     * vers l'est.
     */
    static void modelMatrix(const Pose& pose, float* out) {
        const float angle = -pose.heading;
        const float c = std::cos(angle);
        const float s = std::sin(angle);
        out[0] = c * pose.scaleX;  out[1] = s * pose.scaleX;  out[2] = 0.f;          out[3] = 0.f;
        out[4] = -s * pose.scaleY; out[5] = c * pose.scaleY;  out[6] = 0.f;          out[7] = 0.f;
        out[8] = 0.f;              out[9] = 0.f;              out[10] = pose.scaleZ; out[11] = 0.f;
        out[12] = pose.east;       out[13] = pose.north;      out[14] = 0.f;         out[15] = 1.f;
    }

    static float depthOf(const Pose& pose, const float* m) {
        const float z = m[2] * pose.east + m[6] * pose.north + m[14];
        const float w = m[3] * pose.east + m[7] * pose.north + m[15];
        return w == 0.f ? 0.f : z / w;
    }

    /**
     * Sauvegarde l'état graphique qu'on va toucher.
     *
     * L'en-tête de `CustomLayerHost` dit qu'on n'est **pas** tenu de le
     * restaurer. L'expérience dit le contraire : côté iOS, un `cull face` laissé
     * derrière coupait la moitié de chaque pastille d'arrêt posée au-dessus. Les
     * couches suivantes n'ont pas à payer nos réglages.
     */
    void saveState() {
        glGetIntegerv(GL_CURRENT_PROGRAM, &saved_.program);
        glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &saved_.arrayBuffer);
        saved_.depthTest = glIsEnabled(GL_DEPTH_TEST);
        glGetIntegerv(GL_DEPTH_FUNC, &saved_.depthFunc);
        glGetBooleanv(GL_DEPTH_WRITEMASK, &saved_.depthMask);
        saved_.blend = glIsEnabled(GL_BLEND);
        glGetIntegerv(GL_BLEND_SRC_RGB, &saved_.blendSrcRgb);
        glGetIntegerv(GL_BLEND_DST_RGB, &saved_.blendDstRgb);
        glGetIntegerv(GL_BLEND_SRC_ALPHA, &saved_.blendSrcAlpha);
        glGetIntegerv(GL_BLEND_DST_ALPHA, &saved_.blendDstAlpha);
        saved_.cullFace = glIsEnabled(GL_CULL_FACE);
        glGetIntegerv(GL_CULL_FACE_MODE, &saved_.cullFaceMode);
        glGetIntegerv(GL_FRONT_FACE, &saved_.frontFace);
    }

    void restoreState() {
        glUseProgram(static_cast<GLuint>(saved_.program));
        glBindBuffer(GL_ARRAY_BUFFER, static_cast<GLuint>(saved_.arrayBuffer));
        if (saved_.depthTest) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
        glDepthFunc(static_cast<GLenum>(saved_.depthFunc));
        glDepthMask(saved_.depthMask);
        if (saved_.blend) glEnable(GL_BLEND); else glDisable(GL_BLEND);
        glBlendFuncSeparate(static_cast<GLenum>(saved_.blendSrcRgb),
                            static_cast<GLenum>(saved_.blendDstRgb),
                            static_cast<GLenum>(saved_.blendSrcAlpha),
                            static_cast<GLenum>(saved_.blendDstAlpha));
        if (saved_.cullFace) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
        glCullFace(static_cast<GLenum>(saved_.cullFaceMode));
        glFrontFace(static_cast<GLenum>(saved_.frontFace));
    }

    struct SavedState {
        GLint program = 0;
        GLint arrayBuffer = 0;
        GLboolean depthTest = GL_FALSE;
        GLint depthFunc = GL_LESS;
        GLboolean depthMask = GL_TRUE;
        GLboolean blend = GL_FALSE;
        GLint blendSrcRgb = GL_ONE;
        GLint blendDstRgb = GL_ZERO;
        GLint blendSrcAlpha = GL_ONE;
        GLint blendDstAlpha = GL_ZERO;
        GLboolean cullFace = GL_FALSE;
        GLint cullFaceMode = GL_BACK;
        GLint frontFace = GL_CCW;
    };

    std::shared_ptr<SceneState> state_;

    GLuint program_ = 0;
    GLuint buffers_[aule::kMeshCount] = {0, 0};
    GLsizei vertexCount_[aule::kMeshCount] = {0, 0};
    GLint viewProjectionUniform_ = -1;
    GLint modelUniform_ = -1;
    GLint tintUniform_ = -1;
    GLint positionAttrib_ = -1;
    GLint colorAttrib_ = -1;
    /// Réutilisé d'une image à l'autre : trier ne doit rien allouer.
    std::vector<uint32_t> order_;
    SavedState saved_;
};

/// Ce que Kotlin détient : une possession partagée, pour que l'état survive à
/// l'hôte qu'un rechargement de style emporte.
using StateHandle = std::shared_ptr<SceneState>;

StateHandle* handleOf(jlong value) { return reinterpret_cast<StateHandle*>(value); }

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_aule_android_core_map3d_VehicleScene_nativeCreateState(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(new StateHandle(std::make_shared<SceneState>()));
}

JNIEXPORT void JNICALL
Java_io_aule_android_core_map3d_VehicleScene_nativeDestroyState(JNIEnv*, jclass, jlong handle) {
    delete handleOf(handle);
}

JNIEXPORT void JNICALL
Java_io_aule_android_core_map3d_VehicleScene_nativeInstallMesh(
    JNIEnv* env, jclass, jlong handle, jint index, jfloatArray data) {
    StateHandle* state = handleOf(handle);
    if (state == nullptr || data == nullptr) return;
    const jsize count = env->GetArrayLength(data);
    jfloat* values = env->GetFloatArrayElements(data, nullptr);
    if (values == nullptr) return;
    (*state)->installMesh(static_cast<uint32_t>(index), values, static_cast<size_t>(count));
    env->ReleaseFloatArrayElements(data, values, JNI_ABORT);
}

/**
 * Le tampon où Kotlin écrit ses poses, enveloppé une fois pour toutes.
 *
 * Un `ByteBuffer` direct sur une adresse stable : plus une seule allocation par
 * image côté Kotlin, là où publier la flotte en GeoJSON en créait des centaines.
 */
JNIEXPORT jobject JNICALL
Java_io_aule_android_core_map3d_VehicleScene_nativeStagingBuffer(
    JNIEnv* env, jclass, jlong handle) {
    StateHandle* state = handleOf(handle);
    if (state == nullptr) return nullptr;
    return env->NewDirectByteBuffer((*state)->stagingPoses(),
                                    static_cast<jlong>(SceneState::stagingBytes()));
}

/**
 * Publie la trame, et rend le statut de la scène.
 *
 * **Le retour n'est pas un confort.** `initialize` échoue sur le thread de
 * rendu ; sans cette valeur, le thread principal ne l'apprendrait jamais et
 * cesserait de dessiner en extrusion pour une 3D qui ne vient pas — une flotte
 * invisible, et rien pour le dire.
 */
JNIEXPORT jint JNICALL
Java_io_aule_android_core_map3d_VehicleScene_nativeCommit(
    JNIEnv*, jclass, jlong handle, jint count,
    jdouble anchorMercX, jdouble anchorMercY, jdouble anchorLatitude) {
    StateHandle* state = handleOf(handle);
    if (state == nullptr) return static_cast<jint>(SceneStatus::Failed);
    (*state)->commit(static_cast<uint32_t>(count < 0 ? 0 : count),
                     anchorMercX, anchorMercY, anchorLatitude);
    return static_cast<jint>((*state)->status());
}

JNIEXPORT jlong JNICALL
Java_io_aule_android_core_map3d_VehicleScene_nativeCreateHost(JNIEnv*, jclass, jlong handle) {
    StateHandle* state = handleOf(handle);
    if (state == nullptr) return 0;
    // MapLibre adopte le pointeur en construisant sa `CustomLayer` : on ne le
    // libère jamais soi-même. L'état, lui, est partagé et survit.
    return reinterpret_cast<jlong>(new VehicleSceneHost(*state));
}

} // extern "C"
