plugins {
    alias(libs.plugins.aule.android.library)
    alias(libs.plugins.aule.android.compose)
}

android {
    namespace = "io.aule.android.core.designsystem"
}

dependencies {
    api(projects.core.model)

    // Aucune dépendance Material 3, et c'est une décision (ADR-010) : le produit
    // doit se reconnaître comme Aule, pas comme une démonstration Material. Les
    // fondations viennent de compose.foundation, apportée par le plugin de
    // convention Compose.
}
