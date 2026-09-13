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
 * Le rendu n'en lit d'ailleurs plus aucun : la position de l'œil se tire de la
 * matrice elle-même, qui ne peut pas mentir sur ses unités.
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
 *
 * ## La lumière (ADR-017)
 *
 * Le nuancier **éclaire**, là où la première version cuisait un ombrage fixe
 * dans les sommets. La lumière est celle du style — direction, couleur,
 * intensité du `light` qui ombre les bâtiments —, publiée par Kotlin avec
 * chaque trame. Trois matières s'en distinguent : la carrosserie satinée, le
 * vitrage qui reflète le ciel, le châssis mat ; les feux sont émissifs et
 * s'allument la nuit. Une ombre de contact, dessinée avant les caisses, pose
 * chaque véhicule sur la chaussée au lieu de l'y faire flotter.
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
using aule::Lighting;
using aule::Pose;
using aule::SceneState;
using aule::SceneStatus;

/// Le tour de la Terre à l'équateur — la valeur du sphéroïde Web Mercator, pas
/// un rayon moyen : celui-ci est bon pour une haversine, faux pour une projection.
constexpr double kEquatorMeters = 2.0 * M_PI * 6378137.0;

constexpr double kTileSize = 512.0;

/**
 * La hauteur de l'ombre de contact au-dessus de la chaussée, en mètres.
 *
 * Sous la semelle du modèle (`GROUND_CLEARANCE_M`, 0,05), pour rester dessous ;
 * au-dessus de zéro, pour la même raison que la semelle : à zéro exactement,
 * l'ombre et la chaussée se disputent le tampon de profondeur.
 */
constexpr float kShadowLiftMeters = 0.03f;

/// De combien l'ombre déborde de l'emprise, en mètres avant exagération : le
/// flou a besoin de place pour s'éteindre.
constexpr float kShadowReachMeters = 0.9f;

/// Le décalage de l'ombre à l'opposé de la lumière, en mètres. Assez pour
/// dire d'où vient le jour, pas assez pour détacher l'ombre de sa caisse.
constexpr float kShadowOffsetMeters = 0.45f;

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

/// Compile et lie un programme ; rend 0 et journalise si l'une des étapes échoue.
GLuint link(const char* vertexSource, const char* fragmentSource) {
    const GLuint vertex = compile(GL_VERTEX_SHADER, vertexSource);
    const GLuint fragment = compile(GL_FRAGMENT_SHADER, fragmentSource);
    if (vertex == 0 || fragment == 0) {
        if (vertex != 0) glDeleteShader(vertex);
        if (fragment != 0) glDeleteShader(fragment);
        return 0;
    }
    const GLuint program = glCreateProgram();
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    glLinkProgram(program);
    GLint linked = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    if (linked != GL_TRUE) {
        char log[1024] = {0};
        glGetProgramInfoLog(program, sizeof(log) - 1, nullptr, log);
        LOGE("édition de liens refusée : %s", log);
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

// ------------------------------------------------------------------ nuanciers

// Le nuancier des véhicules.
//
// `a_color.rgb` est la couleur propre d'une pièce fixe, et `a_color.a` le code
// de la pièce : 0 carrosserie, 1 vitrage, 2 roues, 3 feux, 4 bas de caisse. La
// carrosserie et le bas de caisse ne portent pas de couleur dans le maillage —
// ils prennent la teinte de la ligne au rendu, le second assombri. C'est ce qui
// permet **un seul maillage par modèle** quelle que soit la livrée : cuire la
// couleur de ligne dans les sommets demanderait un tampon par ligne.
//
// ⚠️ **Les roues sont le seul noir du véhicule.** Vu du ciel, un bus n'a pas de
// châssis visible : une caisse, des vitres, des roues. Peindre les jupes et les
// pare-chocs en anthracite neutre donnait une carcasse sous une carrosserie —
// le défaut qui empêchait la flotte de paraître vraie.
//
// Le sommet transporte sa normale de face : le modèle est bas-poly et non
// indexé, donc chaque triangle garde son facettage franc. L'éclairage se
// calcule **par fragment** — le reflet d'une vitre se déplace sur sa surface
// quand la caméra tourne, ce qu'un calcul par sommet ne donnerait pas.
const char* kVehicleVertexShader = R"(
attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec4 a_color;
uniform mat4 u_viewProjection;
uniform mat4 u_model;
uniform mat3 u_rotation;
uniform vec4 u_tint;
varying vec3 v_world;
varying vec3 v_normal;
varying vec3 v_albedo;
varying float v_part;
varying float v_height;
void main() {
    vec4 world = u_model * vec4(a_position, 1.0);
    gl_Position = u_viewProjection * world;
    v_world = world.xyz;
    // L'exagération est isotrope : la rotation seule transporte la normale.
    v_normal = u_rotation * a_normal;
    // Carrosserie (0) et bas de caisse (4) prennent la teinte de la ligne ; le
    // second l'assombrit, parce qu'une jupe n'est pas d'une autre matière que sa
    // caisse — elle est à l'ombre d'elle-même.
    float livree = (1.0 - step(0.5, a_color.a)) + step(3.5, a_color.a);
    // ⚠️ Une **nuance**, pas une seconde couleur. La livrée du tram est déjà
    // sombre (0x2F9D80) et un flanc reçoit 0,57 d'éclairement : à 0,48 le bas de
    // caisse tombait à RGB (26,44,37), soit la barre noire qu'on cherchait à
    // faire disparaître. Mesuré à l'écran le 12/09.
    float assombri = 1.0 - 0.22 * step(3.5, a_color.a);
    v_albedo = mix(a_color.rgb, u_tint.rgb * assombri, livree);
    v_part = a_color.a;
    // La hauteur dans le modèle, en mètres, avant exagération : c'est elle qui
    // assombrit le bas de caisse.
    v_height = a_position.z;
}
)";

const char* kVehicleFragmentShader = R"(
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec4 u_tint;
uniform vec3 u_camera;
uniform vec3 u_sunDir;
uniform vec3 u_sunColor;
uniform vec3 u_sky;
uniform vec3 u_ground;
uniform float u_lampGlow;
varying vec3 v_world;
varying vec3 v_normal;
varying vec3 v_albedo;
varying float v_part;
varying float v_height;

void main() {
    vec3 N = normalize(v_normal);
    vec3 V = normalize(u_camera - v_world);
    vec3 H = normalize(u_sunDir + V);
    float ndl = max(dot(N, u_sunDir), 0.0);
    float ndv = max(dot(N, V), 0.0);
    float ndh = max(dot(N, H), 0.0);
    // Schlick : les faces vues en rasant renvoient le ciel.
    float fresnel = pow(1.0 - ndv, 4.0);

    // Ambiante hémisphérique : le toit voit le ciel, la jupe voit la chaussée.
    vec3 ambient = mix(u_ground, u_sky, N.z * 0.5 + 0.5);
    // Occlusion de contact : le bas de caisse est dans l'ombre du véhicule
    // lui-même. Elle ne descend qu'à 0,82 et s'éteint à quatre-vingt-dix
    // centimètres, la hauteur des passages de roue — elle se **multiplie** à
    // l'ambiante, et à 0,45 sur un mètre vingt elle noircissait tout le bas du
    // tram (mesuré à z18 le 12/09).
    float occlusion = mix(0.82, 1.0, smoothstep(0.0, 0.9, v_height));

    // Les quatre pièces se sélectionnent sans branche : GLSL ES 1.00 ne
    // garantit pas le branchement sur une valeur interpolée.
    float isBody = 1.0 - step(0.5, v_part);
    float isGlass = step(0.5, v_part) * (1.0 - step(1.5, v_part));
    float isLamp = step(2.5, v_part) * (1.0 - step(3.5, v_part));
    // Roues et bas de caisse partagent la même réponse mate ; c'est leur albédo
    // qui les sépare, l'un presque noir, l'autre la livrée assombrie.
    float isMatte = step(1.5, v_part) * (1.0 - step(2.5, v_part)) + step(3.5, v_part);

    // Carrosserie : satinée. Un reflet large et doux, un liseré de ciel —
    // discret : à 0,35 il délavait les flancs vus en rasant, et la livrée
    // perdait sa teinte.
    vec3 body = v_albedo * (ambient + u_sunColor * ndl) * occlusion
              + u_sunColor * pow(ndh, 40.0) * 0.30
              + u_sky * fresnel * 0.18;

    // Vitrage : il **réfléchit**, et c'est ce qui le distingue d'un aplat sombre.
    // Vue d'un drone, une baie vitrée verticale renvoie la chaussée, pas le ciel
    // — le vecteur réfléchi pointe vers le bas —, et un réseau de trams vu d'en
    // haut n'est jamais noir. La part réfléchie de base pèse donc autant que
    // l'albédo ; sans elle, un tram dont les flancs sont vitrés aux deux tiers
    // devient une barre noire, ce qu'on a vu à l'écran le 12/09.
    vec3 R = reflect(-V, N);
    vec3 reflection = mix(u_ground, u_sky * 1.25, clamp(R.z * 1.5 + 0.5, 0.0, 1.0));
    vec3 glass = v_albedo * (ambient * 0.75 + u_sunColor * ndl * 0.35)
               + reflection * (0.24 + 0.55 * fresnel)
               + u_sunColor * pow(ndh, 90.0) * 0.8;

    // Roues et bas de caisse : mats, et dans l'ombre de la caisse.
    vec3 matte = v_albedo * (ambient + u_sunColor * ndl * 0.7) * occlusion;

    // Feux : émissifs, plus forts la nuit.
    vec3 lamp = v_albedo * (0.85 + 0.6 * u_lampGlow) + ambient * 0.15;

    vec3 color = body * isBody + glass * isGlass + matte * isMatte + lamp * isLamp;
    // Alpha prémultiplié : MapLibre compose ainsi. Une couleur droite cernerait
    // les véhicules translucides d'un halo sombre.
    gl_FragColor = vec4(color * u_tint.a, u_tint.a);
}
)";

// L'ombre de contact : un rectangle arrondi, flou sur ses bords, posé sous la
// caisse. Ce n'est pas une ombre portée — elle ne suit ni la hauteur ni la
// forme du véhicule — mais c'est ce qui le **pose** sur la chaussée : sans
// elle, un modèle éclairé par-dessus flotte à quelques centimètres du sol.
const char* kShadowVertexShader = R"(
attribute vec2 a_corner;
uniform mat4 u_viewProjection;
uniform mat4 u_model;
uniform vec2 u_halfQuad;
varying vec2 v_local;
void main() {
    v_local = a_corner * u_halfQuad;
    gl_Position = u_viewProjection * (u_model * vec4(v_local, 0.0, 1.0));
}
)";

const char* kShadowFragmentShader = R"(
precision mediump float;
uniform vec2 u_halfBody;
uniform float u_strength;
varying vec2 v_local;
void main() {
    // Distance signée au rectangle arrondi de l'emprise, en mètres.
    float radius = 0.6;
    vec2 q = abs(v_local) - (u_halfBody - vec2(radius));
    float d = length(max(q, 0.0)) - radius;
    // Pleine sous la caisse, éteinte à soixante-dix centimètres du bord.
    float alpha = (1.0 - smoothstep(-0.25, 0.7, d)) * u_strength;
    gl_FragColor = vec4(0.0, 0.0, 0.0, alpha);
}
)";

/// Les quatre coins d'un carré unité, en deux triangles.
const float kQuadCorners[12] = {
    -1.f, -1.f,  1.f, -1.f,  1.f, 1.f,
    -1.f, -1.f,  1.f,  1.f, -1.f, 1.f,
};

class VehicleSceneHost : public mbgl::style::CustomLayerHost {
public:
    explicit VehicleSceneHost(std::shared_ptr<SceneState> state) : state_(std::move(state)) {}

    void initialize(const mbgl::style::CustomLayerInitParameters&) override {
        LOGI("initialize — %s / %s",
             reinterpret_cast<const char*>(glGetString(GL_VERSION)),
             reinterpret_cast<const char*>(glGetString(GL_RENDERER)));

        program_ = link(kVehicleVertexShader, kVehicleFragmentShader);
        shadowProgram_ = link(kShadowVertexShader, kShadowFragmentShader);
        if (program_ == 0 || shadowProgram_ == 0) {
            releasePrograms();
            state_->setStatus(SceneStatus::Failed);
            return;
        }

        viewProjectionUniform_ = glGetUniformLocation(program_, "u_viewProjection");
        modelUniform_ = glGetUniformLocation(program_, "u_model");
        rotationUniform_ = glGetUniformLocation(program_, "u_rotation");
        tintUniform_ = glGetUniformLocation(program_, "u_tint");
        cameraUniform_ = glGetUniformLocation(program_, "u_camera");
        sunDirUniform_ = glGetUniformLocation(program_, "u_sunDir");
        sunColorUniform_ = glGetUniformLocation(program_, "u_sunColor");
        skyUniform_ = glGetUniformLocation(program_, "u_sky");
        groundUniform_ = glGetUniformLocation(program_, "u_ground");
        lampGlowUniform_ = glGetUniformLocation(program_, "u_lampGlow");
        positionAttrib_ = glGetAttribLocation(program_, "a_position");
        normalAttrib_ = glGetAttribLocation(program_, "a_normal");
        colorAttrib_ = glGetAttribLocation(program_, "a_color");

        shadowViewProjectionUniform_ = glGetUniformLocation(shadowProgram_, "u_viewProjection");
        shadowModelUniform_ = glGetUniformLocation(shadowProgram_, "u_model");
        shadowHalfQuadUniform_ = glGetUniformLocation(shadowProgram_, "u_halfQuad");
        shadowHalfBodyUniform_ = glGetUniformLocation(shadowProgram_, "u_halfBody");
        shadowStrengthUniform_ = glGetUniformLocation(shadowProgram_, "u_strength");
        cornerAttrib_ = glGetAttribLocation(shadowProgram_, "a_corner");

        glGenBuffers(1, &quadBuffer_);
        glBindBuffer(GL_ARRAY_BUFFER, quadBuffer_);
        glBufferData(GL_ARRAY_BUFFER, sizeof(kQuadCorners), kQuadCorners, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        uploadMeshes();

        if (!state_->hasAllMeshes()) {
            // Les maillages arrivent depuis Kotlin ; s'ils ne sont pas encore là,
            // ce n'est pas un échec — `render` retentera le téléversement.
            LOGI("initialize — maillages pas encore installés");
            state_->setStatus(SceneStatus::NeedsInit);
            return;
        }
        state_->setStatus(SceneStatus::Ready);
        LOGI("initialize terminé — programmes %u et %u", program_, shadowProgram_);
    }

    void render(const mbgl::style::CustomLayerRenderParameters& p) override {
        if (program_ == 0 || shadowProgram_ == 0) return;
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

        float camera[3];
        eyeOf(composed, camera);

        saveState();

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

        // Les ombres d'abord, sans écrire la profondeur : les caisses opaques
        // les recouvrent ensuite, les translucides les laissent voir — et une
        // caisse posée sur son ombre est justement ce qu'on cherche.
        drawShadows(*frame, viewProjection);

        // Sans faces arrière écartées, un solide fermé reste juste tant que la
        // profondeur écrit ; la translucidité, elle, doublerait. On trie donc les
        // instances et on écarte l'arrière — voir `drawPass`.
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        // La scène retourne l'axe nord-sud, donc l'orientation des triangles.
        glFrontFace(GL_CW);

        glUseProgram(program_);
        glUniformMatrix4fv(viewProjectionUniform_, 1, GL_FALSE, viewProjection);
        glUniform3fv(cameraUniform_, 1, camera);
        const Lighting& light = frame->lighting;
        glUniform3f(sunDirUniform_, light.sunEast, light.sunNorth, light.sunUp);
        glUniform3f(sunColorUniform_, light.sunR, light.sunG, light.sunB);
        glUniform3f(skyUniform_, light.skyR, light.skyG, light.skyB);
        glUniform3f(groundUniform_, light.groundR, light.groundG, light.groundB);
        glUniform1f(lampGlowUniform_, light.lampGlow);

        glEnableVertexAttribArray(static_cast<GLuint>(positionAttrib_));
        glEnableVertexAttribArray(static_cast<GLuint>(normalAttrib_));
        glEnableVertexAttribArray(static_cast<GLuint>(colorAttrib_));

        // Deux passes. Les opaques d'abord, profondeur en écriture : elles posent
        // le relief. Les translucides ensuite, profondeur en lecture seule et
        // **triées du plus lointain au plus proche** — sans ce tri, un bus
        // derrière un autre se composerait par-dessus lui.
        drawPass(*frame, viewProjection, /* opaque */ true);
        drawPass(*frame, viewProjection, /* opaque */ false);

        glDisableVertexAttribArray(static_cast<GLuint>(positionAttrib_));
        glDisableVertexAttribArray(static_cast<GLuint>(normalAttrib_));
        glDisableVertexAttribArray(static_cast<GLuint>(colorAttrib_));
        restoreState();
    }

    void contextLost() override {
        // Le contexte n'existe plus : `glDelete*` y serait au mieux inutile. On
        // oublie les identifiants ; `initialize` sera rappelé, et les maillages
        // sont conservés côté processeur précisément pour ce moment-là.
        LOGI("contextLost — objets GL oubliés, maillages conservés");
        program_ = 0;
        shadowProgram_ = 0;
        buffers_[0] = 0;
        buffers_[1] = 0;
        quadBuffer_ = 0;
        state_->setStatus(SceneStatus::NeedsInit);
    }

    void deinitialize() override {
        // Peut être appelée sans `initialize` préalable : la spécification le dit.
        if (buffers_[0] != 0 || buffers_[1] != 0) glDeleteBuffers(2, buffers_);
        if (quadBuffer_ != 0) glDeleteBuffers(1, &quadBuffer_);
        releasePrograms();
        buffers_[0] = 0;
        buffers_[1] = 0;
        quadBuffer_ = 0;
        state_->setStatus(SceneStatus::NeedsInit);
    }

private:
    void releasePrograms() {
        if (program_ != 0) glDeleteProgram(program_);
        if (shadowProgram_ != 0) glDeleteProgram(shadowProgram_);
        program_ = 0;
        shadowProgram_ = 0;
    }

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

    /**
     * L'œil de la caméra dans le repère de scène, tiré de la matrice.
     *
     * Pour toute projection en perspective, l'œil est l'unique point que la
     * matrice envoie en `x = y = w = 0`. Trois lignes de la matrice, trois
     * inconnues : un système linéaire, résolu en `double` par Cramer. On ne
     * lit ainsi ni `bearing`, ni `pitch`, ni `fieldOfView` — trois valeurs dont
     * les unités ne sont pas documentées et dont l'une a déjà coûté une journée.
     *
     * Le reflet d'une vitre et le liseré de Fresnel en dépendent ; un œil faux
     * ne planterait rien, il rendrait simplement les reflets incohérents d'un
     * bord de l'écran à l'autre.
     */
    static void eyeOf(const double* m, float* out) {
        // Lignes 0, 1 et 3 de la matrice en colonnes majeures : m[c * 4 + r].
        const double a[3][3] = {
            {m[0], m[4], m[8]},
            {m[1], m[5], m[9]},
            {m[3], m[7], m[11]},
        };
        const double b[3] = {-m[12], -m[13], -m[15]};

        auto det3 = [](const double c0[3], const double c1[3], const double c2[3]) {
            return c0[0] * (c1[1] * c2[2] - c1[2] * c2[1])
                 - c1[0] * (c0[1] * c2[2] - c0[2] * c2[1])
                 + c2[0] * (c0[1] * c1[2] - c0[2] * c1[1]);
        };
        const double col0[3] = {a[0][0], a[1][0], a[2][0]};
        const double col1[3] = {a[0][1], a[1][1], a[2][1]};
        const double col2[3] = {a[0][2], a[1][2], a[2][2]};
        const double det = det3(col0, col1, col2);
        if (std::fabs(det) < 1e-30) {
            // Une projection sans point de fuite — jamais chez MapLibre, mais on
            // ne divise pas par zéro sur un thread de rendu. Un œil très haut
            // rend un éclairage plausible, pas un plantage.
            out[0] = 0.f;
            out[1] = 0.f;
            out[2] = 1.0e6f;
            return;
        }
        const double bx[3] = {b[0], b[1], b[2]};
        out[0] = static_cast<float>(det3(bx, col1, col2) / det);
        out[1] = static_cast<float>(det3(col0, bx, col2) / det);
        out[2] = static_cast<float>(det3(col0, col1, bx) / det);
    }

    void drawShadows(const Frame& frame, const float* viewProjection) {
        const Lighting& light = frame.lighting;
        if (light.shadowStrength <= 0.f) return;

        // L'ombre glisse à l'opposé de la lumière, d'autant plus qu'elle est basse.
        const float horizontal = std::sqrt(light.sunEast * light.sunEast +
                                           light.sunNorth * light.sunNorth);
        float offsetEast = 0.f;
        float offsetNorth = 0.f;
        if (horizontal > 1e-4f) {
            const float reach = kShadowOffsetMeters * std::min(1.f, horizontal / std::max(light.sunUp, 0.2f));
            offsetEast = -light.sunEast / horizontal * reach;
            offsetNorth = -light.sunNorth / horizontal * reach;
        }

        glDepthMask(GL_FALSE);
        // Le quadrilatère n'a pas d'orientation qui compte : on ne l'écarte pas.
        glDisable(GL_CULL_FACE);
        glUseProgram(shadowProgram_);
        glUniformMatrix4fv(shadowViewProjectionUniform_, 1, GL_FALSE, viewProjection);
        glBindBuffer(GL_ARRAY_BUFFER, quadBuffer_);
        glEnableVertexAttribArray(static_cast<GLuint>(cornerAttrib_));
        glVertexAttribPointer(static_cast<GLuint>(cornerAttrib_), 2, GL_FLOAT, GL_FALSE, 0,
                              reinterpret_cast<void*>(0));

        for (uint32_t i = 0; i < frame.count; ++i) {
            const Pose& pose = frame.poses[i];
            const uint32_t mesh = pose.mesh < aule::kMeshCount ? pose.mesh : 0;
            const float* half = state_->halfExtent(mesh);
            if (half[0] <= 0.f || half[1] <= 0.f) continue;

            float model[16];
            modelMatrix(pose, model);
            model[12] += offsetEast;
            model[13] += offsetNorth;
            model[14] = kShadowLiftMeters;
            glUniformMatrix4fv(shadowModelUniform_, 1, GL_FALSE, model);
            glUniform2f(shadowHalfQuadUniform_, half[0] + kShadowReachMeters,
                        half[1] + kShadowReachMeters);
            glUniform2f(shadowHalfBodyUniform_, half[0], half[1]);
            // L'ombre suit l'opacité de sa caisse : un véhicule qui s'estompe au
            // seuil de zoom n'en laisse pas une derrière lui.
            glUniform1f(shadowStrengthUniform_, light.shadowStrength * pose.a);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }

        glDisableVertexAttribArray(static_cast<GLuint>(cornerAttrib_));
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
                // La disposition d'un sommet — le contrat avec `MeshStandardizer` :
                // position, normale, puis `r g b` et le code de pièce.
                glVertexAttribPointer(static_cast<GLuint>(positionAttrib_), 3, GL_FLOAT, GL_FALSE,
                                      stride, reinterpret_cast<void*>(0));
                glVertexAttribPointer(static_cast<GLuint>(normalAttrib_), 3, GL_FLOAT, GL_FALSE,
                                      stride, reinterpret_cast<void*>(sizeof(float) * 3));
                glVertexAttribPointer(static_cast<GLuint>(colorAttrib_), 4, GL_FLOAT, GL_FALSE,
                                      stride, reinterpret_cast<void*>(sizeof(float) * 6));
            }

            float model[16];
            modelMatrix(pose, model);
            glUniformMatrix4fv(modelUniform_, 1, GL_FALSE, model);
            float rotation[9];
            rotationMatrix(pose, rotation);
            glUniformMatrix3fv(rotationUniform_, 1, GL_FALSE, rotation);
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

    /// La rotation seule, pour les normales — l'exagération est isotrope, donc
    /// elle ne les déforme pas.
    static void rotationMatrix(const Pose& pose, float* out) {
        const float angle = -pose.heading;
        const float c = std::cos(angle);
        const float s = std::sin(angle);
        out[0] = c;   out[1] = s;   out[2] = 0.f;
        out[3] = -s;  out[4] = c;   out[5] = 0.f;
        out[6] = 0.f; out[7] = 0.f; out[8] = 1.f;
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
    GLuint shadowProgram_ = 0;
    GLuint buffers_[aule::kMeshCount] = {0, 0};
    GLuint quadBuffer_ = 0;
    GLsizei vertexCount_[aule::kMeshCount] = {0, 0};

    GLint viewProjectionUniform_ = -1;
    GLint modelUniform_ = -1;
    GLint rotationUniform_ = -1;
    GLint tintUniform_ = -1;
    GLint cameraUniform_ = -1;
    GLint sunDirUniform_ = -1;
    GLint sunColorUniform_ = -1;
    GLint skyUniform_ = -1;
    GLint groundUniform_ = -1;
    GLint lampGlowUniform_ = -1;
    GLint positionAttrib_ = -1;
    GLint normalAttrib_ = -1;
    GLint colorAttrib_ = -1;

    GLint shadowViewProjectionUniform_ = -1;
    GLint shadowModelUniform_ = -1;
    GLint shadowHalfQuadUniform_ = -1;
    GLint shadowHalfBodyUniform_ = -1;
    GLint shadowStrengthUniform_ = -1;
    GLint cornerAttrib_ = -1;

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
 * La lumière de la scène — celle du style, publiée par Kotlin à chaque
 * bascule d'ambiance. Rare, donc un tableau alloué côté JVM ne coûte rien ici.
 */
JNIEXPORT void JNICALL
Java_io_aule_android_core_map3d_VehicleScene_nativeSetLighting(
    JNIEnv* env, jclass, jlong handle, jfloatArray data) {
    StateHandle* state = handleOf(handle);
    if (state == nullptr || data == nullptr) return;
    const jsize count = env->GetArrayLength(data);
    jfloat* values = env->GetFloatArrayElements(data, nullptr);
    if (values == nullptr) return;
    (*state)->setLighting(values, static_cast<size_t>(count));
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
