#pragma once

#include <atomic>
#include <cstdint>
#include <cstring>
#include <vector>

namespace aule {

/// Le plafond d'instances, aligné sur le `MAX_BODIES` de `VehiclesLayer`.
constexpr uint32_t kMaxPoses = 48;

/// Deux modèles : le bus et le tram. Le navibus garde son volume extrudé.
constexpr uint32_t kMeshCount = 2;

/// Sept flottants par sommet — contrat avec `MeshStandardizer`.
constexpr uint32_t kFloatsPerVertex = 7;

/// Ce que le thread principal dit d'un véhicule pour une image.
struct Pose {
    /// Mètres depuis l'ancre : est, puis nord.
    float east, north;
    /// Cap en **radians**, azimut depuis le nord dans le sens des aiguilles.
    float heading;
    /// L'exagération, dans l'ordre du maillage : largeur, longueur, hauteur.
    float scaleX, scaleY, scaleZ;
    /// La teinte de la ligne, et l'opacité de l'instance.
    float r, g, b, a;
    uint32_t mesh;
};

/// Tout ce qu'il faut pour dessiner une image, publié d'un bloc.
struct Frame {
    /// L'ancre en mercator normalisé, et sa latitude — celle qui donne l'échelle.
    ///
    /// ⚠️ `worldSize` **n'est pas ici** : il doit venir du zoom de l'image en
    /// cours de rendu, pas de celui qu'avait le thread principal. Une ancre d'une
    /// trame en retard est exactement neutre — le décalage et l'origine se
    /// recomposent en la coordonnée mercator absolue — alors qu'un `worldSize`
    /// périmé décalerait toute la flotte.
    double anchorMercX = 0.0;
    double anchorMercY = 0.0;
    double anchorLatitude = 0.0;
    uint32_t count = 0;
    Pose poses[kMaxPoses] = {};
};

/// L'état du rendu, tel que le thread principal peut l'apprendre.
enum class SceneStatus : int32_t {
    /// Pas encore de contexte GL, ou il vient d'être perdu.
    NeedsInit = 0,
    Ready = 1,
    /// Le rendu a renoncé — nuancier refusé, maillage absent. On reste en repli.
    Failed = 2,
};

/**
 * L'état partagé entre le thread principal et le thread de rendu.
 *
 * **C'est le point que l'ADR-006 ne couvrait pas.** `VehiclesLayer` interpole sur
 * le thread principal à la fréquence de l'écran ; `CustomLayerHost::render`
 * s'exécute sur le thread GL de MapLibre. Il faut passer les poses sans verrou
 * (qui bloquerait l'un des deux à 120 Hz), sans allocation par image, et sans que
 * le lecteur puisse voir une trame à moitié écrite.
 *
 * D'où le **tampon triple à échange atomique** : trois trames allouées une fois,
 * chacune possédée par un seul thread à la fois. L'écrivain remplit la sienne
 * puis l'échange contre celle du milieu ; le lecteur échange la sienne contre
 * celle du milieu quand elle est marquée neuve. Aucun des deux n'attend jamais
 * l'autre, et aucun ne copie la charge utile.
 *
 * Un *seqlock* serait plus court, mais imposerait au lecteur une copie et une
 * reprise possible — au milieu d'une passe de rendu, c'est exactement ce qu'on
 * ne veut pas.
 *
 * L'objet survit à son hôte : un rechargement de style détruit la `CustomLayer`
 * et l'hôte avec elle, mais les maillages et les poses doivent rester. D'où la
 * possession partagée (`std::shared_ptr`) entre Kotlin et l'hôte.
 */
class SceneState {
public:
    // ---------------------------------------------------------------- maillages

    /**
     * Installe un maillage, conservé **côté processeur**.
     *
     * On le garde même après téléversement : c'est ce qui permet de tout
     * reconstruire après une perte de contexte, sans repasser par les assets ni
     * réveiller la JVM depuis le thread de rendu.
     */
    void installMesh(uint32_t index, const float* data, size_t floatCount) {
        if (index >= kMeshCount) return;
        meshes_[index].assign(data, data + floatCount);
        meshesChanged_.store(true, std::memory_order_release);
    }

    const std::vector<float>& mesh(uint32_t index) const { return meshes_[index]; }

    bool hasAllMeshes() const {
        for (const auto& mesh : meshes_) {
            if (mesh.empty()) return false;
        }
        return true;
    }

    bool consumeMeshesChanged() {
        return meshesChanged_.exchange(false, std::memory_order_acq_rel);
    }

    // -------------------------------------------------------------- trames

    /**
     * Le tampon où le thread principal écrit ses poses.
     *
     * ⚠️ **Il est distinct des trois trames, et c'est nécessaire** : l'indice
     * d'écriture change à chaque publication, donc l'adresse d'une trame ne peut
     * pas être exposée une fois pour toutes à Kotlin. Ce tampon-ci ne bouge
     * jamais, ce qui permet de l'envelopper dans un `ByteBuffer` direct au
     * montage et d'y écrire ensuite sans une seule allocation par image.
     *
     * Le prix est un `memcpy` de deux kilo-octets par image — sous le bruit de
     * mesure, en regard des allocations qu'il remplace côté Kotlin.
     */
    Pose* stagingPoses() { return staging_; }
    static constexpr size_t stagingBytes() { return sizeof(staging_); }

    /// Recopie le tampon dans la trame d'écriture et la publie. Sans attente.
    void commit(uint32_t count, double anchorMercX, double anchorMercY, double anchorLatitude) {
        Frame& frame = frames_[write_];
        frame.anchorMercX = anchorMercX;
        frame.anchorMercY = anchorMercY;
        frame.anchorLatitude = anchorLatitude;
        frame.count = count > kMaxPoses ? kMaxPoses : count;
        std::memcpy(frame.poses, staging_, sizeof(Pose) * frame.count);
        write_ = middle_.exchange(write_ | kDirty, std::memory_order_acq_rel) & kIndexMask;
    }

    /**
     * La trame la plus récemment publiée, ou la précédente si rien n'est neuf.
     *
     * Rend `nullptr` tant qu'aucune trame n'a jamais été publiée : le rendu doit
     * alors ne rien peindre, et surtout pas dessiner une trame nulle.
     */
    const Frame* acquire() {
        if (middle_.load(std::memory_order_acquire) & kDirty) {
            read_ = middle_.exchange(read_, std::memory_order_acq_rel) & kIndexMask;
            everPublished_ = true;
        }
        return everPublished_ ? &frames_[read_] : nullptr;
    }

    // -------------------------------------------------------------- statut

    void setStatus(SceneStatus status) {
        status_.store(static_cast<int32_t>(status), std::memory_order_release);
    }

    SceneStatus status() const {
        return static_cast<SceneStatus>(status_.load(std::memory_order_acquire));
    }

private:
    static constexpr unsigned kIndexMask = 0x3;
    static constexpr unsigned kDirty = 0x4;

    std::vector<float> meshes_[kMeshCount];
    std::atomic<bool> meshesChanged_{false};

    Frame frames_[3];
    /// Écrit par le thread principal seul, d'adresse stable. Voir `stagingPoses`.
    Pose staging_[kMaxPoses] = {};
    /// Possédée par le thread principal.
    unsigned write_ = 0;
    /// Possédée par le thread de rendu.
    unsigned read_ = 2;
    std::atomic<unsigned> middle_{1};
    bool everPublished_ = false;

    std::atomic<int32_t> status_{static_cast<int32_t>(SceneStatus::NeedsInit)};
};

} // namespace aule
