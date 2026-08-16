plugins {
    alias(libs.plugins.aule.android.library)
    alias(libs.plugins.aule.android.compose)
}

android {
    namespace = "io.aule.android.feature.map"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.map)
    implementation(projects.core.model)
    implementation(projects.core.location)
    // Pas de `:data` ici, et c'est le point : cette feature ne connaît que les
    // interfaces de repository. Un Composable ne peut donc pas atteindre le
    // réseau — erreur de compilation, pas règle de revue.

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
