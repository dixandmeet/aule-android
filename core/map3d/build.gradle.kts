plugins {
    alias(libs.plugins.aule.android.library)
}

android {
    namespace = "io.aule.android.core.map3d"

    // Épinglé : la chaîne native doit être la même d'une machine à l'autre, et
    // l'AAR MapLibre déclare `"ndk": 28` dans ses `abi.json`. Laisser AGP choisir
    // ferait dépendre le résultat de ce qui traîne dans le SDK local.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        ndk {
            // Le S21 de référence, et rien d'autre pour l'instant. Les autres ABI
            // se paient en temps de compilation à chaque build ; on les ajoutera
            // avant toute diffusion réelle, pas avant.
            abiFilters += "arm64-v8a"
        }
    }

    // Pas de `prefab = true` : `libmaplibre.so` embarque une STL statique, et
    // prefab refuse alors toute liaison depuis une bibliothèque qui utilise la
    // STL — or l'en-tête de MapLibre déclare lui-même un `std::array`. Les
    // en-têtes sont donc recopiés dans `src/main/cpp/vendor/`, et on ne se lie à
    // rien : on n'appelle aucune fonction de MapLibre. Voir `vendor/README.md`.

    // ⚠️ Cette configuration reste ici, et non dans les plugins de convention de
    // `build-logic/`, à dessein. La règle du projet vise la configuration
    // **partagée** — un `minSdk` recopié dans dix modules est un `minSdk` qui
    // peut diverger dans dix endroits. Un chemin CMake, lui, ne concerne que ce
    // module : c'est le seul du dépôt qui porte du C++. Le poser dans
    // `AuleBuild.kt` l'imposerait aux quatorze autres.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // `CustomLayer` est une classe MapLibre : le pont natif ne peut pas l'ignorer.
    api(libs.maplibre.android)

    // Pour lire l'en-tête JSON des `.glb`. Seule l'API `JsonElement` est
    // utilisée — aucune classe `@Serializable`, donc pas besoin du plugin de
    // compilation, et le lecteur reste vérifiable sur la JVM de l'hôte.
    implementation(libs.kotlinx.serialization.json)
}
