plugins {
    alias(libs.plugins.aule.android.library)
}

android {
    namespace = "io.aule.android.core.map"
}

dependencies {
    api(projects.core.model)
    api(projects.core.location)
    implementation(projects.core.common)
    // Pour les jetons de couleur : les icônes de carte sont dessinées au Canvas à
    // l'exécution et doivent suivre l'ambiance. Le module apporte Compose sur le
    // chemin de compilation, mais `:core:map` n'en utilise rien — il ne connaît
    // qu'`AuleRgba.argb`, qui est du calcul pur.
    implementation(projects.core.designsystem)
    // Le pont vers la couche native : MapLibre n'a pas de couche de modèles, et
    // son seul point d'extension s'écrit en C++.
    //
    // `api` et non `implementation` : `VehiclesLayer` prend une `VehicleScene`
    // dans son constructeur, donc l'écran qui la monte doit pouvoir la nommer.
    api(projects.core.map3d)

    api(libs.maplibre.android)
    implementation(libs.kotlinx.coroutines.android)
}
