// Racine volontairement nue : toute la configuration partagée vit dans les
// plugins de convention de build-logic, pas dans un `allprojects` qui rendrait
// chaque module dépendant de la racine.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}
