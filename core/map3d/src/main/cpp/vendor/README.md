# En-têtes MapLibre recopiés

Ces quatre fichiers sont **vendorisés à dessein**. Trois viennent tels quels du module
*prefab* de `org.maplibre.gl:android-sdk-opengl:13.5.0` ; le quatrième est un bouchon
que l'AAR oublie de livrer.

## Pourquoi ne pas utiliser le prefab

C'était le premier réflexe, et il échoue :

    com.google.prefab.api.NoMatchingLibraryException: No compatible library found
    [CXX1211] Library is a shared library with a statically linked STL and cannot be
              used with any library using the STL [//MapLibreAndroid/maplibre]

`libmaplibre.so` embarque sa STL en statique (`"stl": "c++_static"` dans ses `abi.json`).
Prefab refuse alors que quiconque utilisant la STL s'y lie — sinon deux copies de libc++
cohabiteraient dans le processus. Et nous ne pouvons pas nous passer de la STL : l'en-tête
`custom_layer_render_parameters.hpp` déclare lui-même un `std::array<double, 16>`.

## Pourquoi c'est sans danger

**Nous n'appelons aucune fonction de `libmaplibre.so`.** `CustomLayerHost` est une classe
purement abstraite dont toutes les méthodes sont soit virtuelles pures, soit *inline* :
elle n'a pas de *key function*, donc le compilateur émet la table virtuelle et le
`typeinfo` en symboles faibles dans notre propre unité de compilation. Nous n'avons besoin
que de la **disposition** de la table virtuelle, pas du code de MapLibre.

Le pointeur que nous rendons est simplement `reinterpret_cast` par le JNI de MapLibre.
Rien d'autre ne traverse la frontière : les paramètres de rendu sont des données brutes,
et la destruction de l'hôte appelle notre destructeur virtuel, donc notre `operator
delete`, symétrique de notre `new`.

## Le bouchon

`custom_layer_host.hpp` inclut `<mbgl/gfx/context.hpp>`, **absent du prefab** — cinq
en-têtes seulement y sont exportés. `mbgl/gfx/context.hpp` est donc écrit ici : une simple
déclaration anticipée suffit, `preRender` ne prend qu'une référence.

## À la prochaine montée de version

Ces en-têtes sont figés sur **13.5.0** (`gradle/libs.versions.toml`, clé `maplibre`).
Changer cette version sans les rafraîchir ferait diverger silencieusement la disposition de
la table virtuelle — un plantage à la première image, pas une erreur de compilation. Les
reprendre depuis `prefab/modules/maplibre/include/` du nouvel AAR.
