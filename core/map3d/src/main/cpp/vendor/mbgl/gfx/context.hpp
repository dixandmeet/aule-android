#pragma once

/**
 * Bouchon délibéré.
 *
 * Le prefab de l'AAR MapLibre 13.5.0 livre `custom_layer_host.hpp`, qui inclut
 * `<mbgl/gfx/context.hpp>` — mais pas cet en-tête-là. Cinq fichiers seulement
 * sont exportés, et celui-ci n'en fait pas partie.
 *
 * Une déclaration anticipée suffit : `CustomLayerHost::preRender` ne prend
 * qu'une **référence** à `gfx::Context`, et son implémentation par défaut est
 * vide. Un type incomplet est donc parfaitement légal ici — on ne déréférence
 * jamais rien.
 *
 * Si un jour MapLibre exporte le vrai en-tête, ce fichier disparaît et le
 * répertoire `stub/` sort des chemins d'inclusion.
 */

namespace mbgl {
namespace gfx {
class Context;
} // namespace gfx
} // namespace mbgl
