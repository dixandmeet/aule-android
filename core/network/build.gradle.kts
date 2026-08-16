plugins {
    alias(libs.plugins.aule.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.common)

    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.okhttp.mockwebserver)
}
