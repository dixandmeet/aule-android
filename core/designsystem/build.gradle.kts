plugins {
    alias(libs.plugins.aule.android.library)
    alias(libs.plugins.aule.android.compose)
}

android {
    namespace = "io.aule.android.core.designsystem"
}

dependencies {
    api(projects.core.model)

    // Material 3 et Animation viennent du plugin de convention Compose.
    // `AuleTheme` leur fournit exclusivement les jetons de la marque : le kit
    // porte les comportements système, pas l'identité visuelle (ADR-010).
}
