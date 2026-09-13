# ADR-015 — Une couche native pour les véhicules en volume

**Statut** : acceptée · **Date** : 2026-08-31

## La question

En vue rapprochée, les véhicules étaient des boîtes `fill-extrusion` monochromes : le bon
volume, aux bonnes cotes, mais sans silhouette. Le web et iOS montrent au même seuil de
vrais modèles — vitres, roues, pare-chocs, feux. Comment combler l'écart sur Android ?

## La décision

**Une `CustomLayer` MapLibre pilotée par un hôte C++**, dans un module dédié
`:core:map3d`. C'est le seul point d'extension du SDK qui permette de dessiner autre chose
que les couches du style, et il ne s'écrit qu'en C++.

Le projet n'était pas 100 % Kotlin par principe mais par absence de raison contraire. Il y
en a une maintenant, et elle est confinée à un module.

## Ce qui a failli faire renoncer

`VehicleBody.kt` portait cette phrase : « le SDK Android n'offre pas cette porte ». Elle
était **fausse**, et elle a tenu lieu de décision pendant des semaines. L'AAR MapLibre
13.5.0 livre un module *prefab* avec les en-têtes de `CustomLayerHost` — le chemin est
exporté par l'artefact, pas contourné.

La leçon dépasse ce fichier : **une impossibilité affirmée dans un commentaire doit porter
sa mesure, ou ne pas être écrite.**

## Ce qu'il a fallu vérifier sur l'appareil

Rien de ce qui suit n'est documenté par MapLibre. Tout est mesuré sur le S21.

| Question | Réponse |
|---|---|
| `CustomLayer` fonctionne-t-elle en OpenGL 13.5.0 ? | Oui — `initialize` et `render` sont appelés |
| Repère de la matrice | x/y en **pixels-monde** mercator (`512 · 2^zoom`), axe y vers le **sud** |
| Axe vertical | en **mètres** — la matrice porte déjà la conversion |
| Thread de `render` | le thread GL de MapLibre, **pas** le principal |
| Contexte GL | ES 3.2 sur Mali-G78, alors que MapLibre demande ES 2.0 |

Deux pièges, tous deux coûteux et invisibles :

1. **`pitch` et `bearing` sont en radians**, pas en degrés. Lus en degrés, on croit la carte
   à plat (0,9 rad ≈ 52°) et on cherche un défaut qui n'existe pas.
2. **Mettre les trois axes à la même échelle** multiplie les hauteurs par un facteur qui
   **double à chaque niveau de zoom** : un bus de trois mètres devient une tour de cent à
   z18.

Le second est la raison d'être de l'ancrage : à z18 `worldSize` vaut 134 millions de
pixels, et un `float` n'y garde que quelques pixels de résolution. La matrice se compose
donc **en `double`** jusqu'à un repère ancré au centre de la caméra, et ne descend en
`float` qu'une fois les grands nombres annulés.

## Le prefab, et pourquoi on ne s'en sert pas

`libmaplibre.so` embarque une STL statique. Prefab refuse alors toute liaison depuis une
bibliothèque qui utilise la STL — or l'en-tête de MapLibre déclare lui-même un
`std::array`. Les quatre en-têtes nécessaires sont donc **recopiés** dans
`core/map3d/src/main/cpp/vendor/`, et on ne se lie à rien : aucune fonction de MapLibre
n'est appelée, seule la disposition de la table virtuelle compte.

Un des quatre est un bouchon : `custom_layer_host.hpp` inclut `<mbgl/gfx/context.hpp>`, que
l'AAR ne livre pas.

## L'occlusion : on la garde

Le web efface le tampon de profondeur — « le transport reste lisible par-dessus le décor ».
**On ne le suit pas**, et iOS non plus.

Trois raisons. La carte s'incline à 52° et les bâtiments se lèvent au même seuil que les
véhicules : un bus flottant devant une façade se verrait en permanence, pas dans un cas
limite. L'extrusion offrait déjà l'occlusion gratuitement, et l'abandonner serait une
régression. Et le web efface surtout parce qu'il greffe une scène three.js entière dans la
passe — une contrainte que nous n'avons pas, puisque nous écrivons le rendu.

Le corollaire est technique : on prend `nearClippedProjectionMatrix`, celle des
`fill-extrusion`, et non `projectionMatrix` dont l'en-tête dit lui-même qu'elle est faite
pour de la géométrie « 2D/flat ».

Si l'occlusion s'avérait trop forte à l'usage, le levier existe déjà et il est **continu** :
`MapController.setBuildingEmphasis`. Une décision de lisibilité se dose ; `glClear` est un
interrupteur.

## Pas d'instanciation

MapLibre demande un contexte `EGL_CONTEXT_CLIENT_VERSION 2` (vérifié dans le bytecode de
`EGLContextFactory`). Le pilote peut rendre un contexte ES 3.x — le Mali-G78 le fait — mais
rien ne l'oblige. Le rendu est donc écrit en **GLSL ES 1.00, un `glDrawArrays` par
véhicule**. Quarante-huit appels de mille cinq cents triangles ne coûtent rien, et on
s'épargne toute dépendance à une extension.

## La couleur de carrosserie n'est pas celle de la pastille

C'est contre-intuitif et c'est délibéré. Un aplat plat peut être sombre sans rien perdre ;
un modèle ne se lit que par le **contraste entre sa caisse et ses pièces**, vitres et
châssis étant presque noirs. Peint du teal `0x0D595E` de la pastille tram, le modèle devient
un bloc uniforme — tout le détail qu'on est allé chercher disparaît.

Les teintes sont donc celles du web (`0x2F9D80` tram, `0x45C299` bus), qui a tranché la même
chose à l'écran.

## Le repli

Trois défaillances, un seul chemin : ABI non couverte, asset illisible, `initialize` qui
échoue. Dans les trois cas, `VehiclesLayer` marque tous les véhicules en extrusion et
**l'écran est exactement celui d'avant**.

⚠️ Le statut se lit **à chaque image**, pas une fois au montage : `initialize` échoue sur le
thread de rendu, longtemps après. Un drapeau lu une seule fois laisserait la couche croire
la 3D disponible, cesser de publier les volumes, et la flotte deviendrait **invisible** sans
que rien ne le dise.

## Ce qu'on accepte en échange

Une chaîne de compilation native dans un projet qui n'en avait pas : NDK, CMake, et des
en-têtes recopiés qu'il faudra rafraîchir à la prochaine montée de MapLibre — une
divergence y serait un plantage à la première image, pas une erreur de compilation.

Et l'ADR-002 change de nature : OpenGL n'est plus seulement un choix de compatibilité,
**l'application écrit du GL**. Passer à Vulkan signifierait réécrire ce rendu.

## La parenté

`../Native/Aule/Core/Map/Render3D/` porte la même fonctionnalité contre le même cœur mbgl,
livrée avant celle-ci. C'est la référence — le repère, le sens de marche des modèles, le
nuancier non éclairé en viennent. La prochaine divergence entre les deux doit se voir.

**Elle se voit** : depuis l'[ADR-017](ADR-017-eclairage-des-vehicules.md), le nuancier
Android éclaire — normales par sommet, lumière du style, ombre de contact — là où iOS cuit
encore son ombrage. Le « aucune lumière » de cette ADR ne tient plus ; la 017 dit pourquoi.
