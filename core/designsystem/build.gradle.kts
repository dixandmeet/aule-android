plugins {
    alias(libs.plugins.aule.android.library)
    alias(libs.plugins.aule.android.compose)
}

android {
    namespace = "io.aule.android.core.designsystem"
    defaultConfig {
        testApplicationId = "io.aule.android.core.designsystem.alignment.test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(projects.core.model)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Material 3 et Animation viennent du plugin de convention Compose.
    // `AuleTheme` leur fournit exclusivement les jetons de la marque : le kit
    // porte les comportements système, pas l'identité visuelle (ADR-010).
}
