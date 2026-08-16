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

    api(libs.maplibre.android)
    implementation(libs.kotlinx.coroutines.android)
}
