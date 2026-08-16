plugins {
    alias(libs.plugins.aule.android.library)
}

android {
    namespace = "io.aule.android.core.location"
}

dependencies {
    api(projects.core.geo)
    implementation(projects.core.common)

    implementation(libs.play.services.location)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
