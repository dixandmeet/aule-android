plugins {
    alias(libs.plugins.aule.android.library)
    alias(libs.plugins.aule.android.compose)
}

android {
    namespace = "io.aule.android.feature.auth"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    // Le verrou biométrique. Cet écran touche déjà à des API Android — la
    // permission caméra, le sélecteur de photo — et `:core:security` ne voit ni
    // le réseau ni `:data` : la règle qui empêche un Composable d'appeler le BFF
    // reste entière.
    implementation(projects.core.security)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // L'onglet de navigateur de l'inscription Google. Voir le catalogue : une
    // WebView est refusée par Google, et un navigateur complet laisse son
    // onglet ouvert derrière l'application.
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.android)
}
