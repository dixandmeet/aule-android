cmake_minimum_required(VERSION 3.22.1)

project(aulemap3d LANGUAGES CXX)

add_library(aulemap3d SHARED vehicle_layer.cpp)

target_compile_features(aulemap3d PRIVATE cxx_std_17)

# Les en-têtes de `CustomLayerHost` sont recopiés dans `vendor/`, et non tirés du
# module prefab de l'AAR : celui-ci embarque une STL statique, et prefab refuse
# alors toute liaison depuis une bibliothèque qui utilise la STL. On n'appelle
# aucune fonction de `libmaplibre.so` — seule la disposition de la table
# virtuelle nous intéresse. Voir `vendor/README.md`.
target_include_directories(aulemap3d PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/vendor)

# GLESv2 et non v3 : MapLibre demande un contexte `EGL_CONTEXT_CLIENT_VERSION 2`
# (vérifié dans le bytecode de `EGLContextFactory`). Le pilote peut rendre un
# contexte ES 3.x rétrocompatible, mais rien ne l'oblige.
target_link_libraries(aulemap3d
    PRIVATE
        GLESv2
        log
)
