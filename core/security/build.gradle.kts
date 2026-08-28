plugins {
    alias(libs.plugins.aule.android.library)
}

android {
    namespace = "io.aule.android.core.security"
}

/**
 * Pas de Compose ici, et c'est le propos du module : `BiometricPrompt` et le
 * Keystore sont de l'Android pur, sans une ligne d'interface. Ce qui s'affiche
 * — le volet de proposition, la rangée des réglages — vit dans `:feature:auth`,
 * qui ne reçoit d'ici qu'un `Cipher` et un verdict.
 */
dependencies {
    // `api` et non `implementation` : [SecureBlob] rend un `BiometricEnrollment`,
    // qui vit dans `:core:model`. Un type qui traverse une signature publique
    // appartient au contrat, pas aux détails.
    api(projects.core.model)
    implementation(projects.core.common)

    api(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
