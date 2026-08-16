plugins {
    alias(libs.plugins.aule.jvm.library)
}

dependencies {
    api(projects.core.geo)
    api(libs.kotlinx.serialization.json)
}
