plugins {
    alias(libs.plugins.aule.jvm.library)
}

dependencies {
    // Kotlin pur, sans Android. C'est ce qui permet à `:core:network` et `:data`
    // de l'être aussi, et donc de se tester en JVM sans Robolectric.
    // L'implémentation Android du journal vit dans `:app`.
    implementation(libs.kotlinx.coroutines.core)
}
