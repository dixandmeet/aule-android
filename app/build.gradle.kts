import java.util.Properties

plugins {
    alias(libs.plugins.aule.android.application)
    alias(libs.plugins.aule.android.compose)
}

/**
 * Les valeurs propres à la machine (URL, clé publiable Supabase) vivent dans
 * `local.properties`, qui n'est pas versionné. Aucune clé n'entre dans le dépôt
 * par ce fichier — et une clé absente se voit à l'écran plutôt que de produire
 * un écran vide inexplicable.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun setting(key: String, fallback: String = ""): String =
    localProperties.getProperty(key)
        ?: providers.gradleProperty(key).orNull
        ?: fallback

android {
    namespace = "io.aule.android"

    defaultConfig {
        applicationId = "io.aule.android"
        versionCode = 1
        versionName = "0.1.0"

        // Un seul écran, cadré sur la hauteur. Le verrou portrait est repris du
        // Flutter Pro, dont le manifeste explique pourquoi : « toute la carte de
        // navigation se cadre sur la hauteur ».
        resourceConfigurations += setOf("fr", "en")
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "environment"
    productFlavors {
        create("development") {
            dimension = "environment"
            applicationIdSuffix = ".development"
            versionNameSuffix = "-dev"
            buildConfigField("String", "ENVIRONMENT_LABEL", "\"Développement\"")
            buildConfigField("String", "DEFAULT_DATA_SOURCE", "\"production\"")
            buildConfigField("boolean", "VERBOSE_LOGGING", "true")
            // Seul flavor où les fixtures existent (ADR-005). Ailleurs, le module
            // qui les porte n'est pas sur le chemin de compilation.
            buildConfigField("boolean", "ALLOW_MOCK_SOURCE", "true")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "ENVIRONMENT_LABEL", "\"Recette\"")
            buildConfigField("String", "DEFAULT_DATA_SOURCE", "\"production\"")
            buildConfigField("boolean", "VERBOSE_LOGGING", "true")
            buildConfigField("boolean", "ALLOW_MOCK_SOURCE", "false")
        }
        create("production") {
            dimension = "environment"
            buildConfigField("String", "ENVIRONMENT_LABEL", "\"Production\"")
            buildConfigField("String", "DEFAULT_DATA_SOURCE", "\"production\"")
            buildConfigField("boolean", "VERBOSE_LOGGING", "false")
            buildConfigField("boolean", "ALLOW_MOCK_SOURCE", "false")
        }
    }

    // Communes aux trois environnements tant qu'il n'y a qu'un seul backend.
    // Le jour où la recette a le sien, la valeur descend dans le flavor.
    //
    // Les valeurs par défaut sont celles de `SAE/lib/config/backend_config.dart` :
    // la clé publiable est conçue pour être embarquée (RLS tient les droits).
    // `local.properties` reste prioritaire pour surcharger sans toucher au code.
    defaultConfig {
        buildConfigField(
            "String",
            "AULE_API_BASE",
            "\"${setting("aule.apiBase", "https://www.aule.fr")}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${setting("aule.supabaseUrl", "https://rllcdvuqduuyhdcifiwp.supabase.co")}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${setting("aule.supabasePublishableKey", "sb_publishable_SoVrtwgKHm3lkFaW8r5fmA_HEH7VpL6")}\"",
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    signingConfigs {
        val storeFilePath = setting("aule.android.storeFile")
        if (storeFilePath.isNotBlank()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = setting("aule.android.storePassword")
                keyAlias = setting("aule.android.keyAlias")
                keyPassword = setting("aule.android.keyPassword")
            }
        }
    }
}

/**
 * Un `release` signé avec la clé de debug s'installe, se lance, et ne se
 * distingue de rien — jusqu'au jour où Play le refuse. Le Flutter du dépôt pose
 * déjà cette garde ; on la reprend plutôt que de la redécouvrir.
 */
gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { it.name.contains("Release") && it.project == project }
    val signed = android.signingConfigs.findByName("release") != null
    val allowed = providers.gradleProperty("allowDebugReleaseSigning").orNull == "true"
    if (buildsRelease && !signed && !allowed) {
        throw GradleException(
            "Signature Android release absente. Renseignez aule.android.storeFile / " +
                "storePassword / keyAlias / keyPassword dans local.properties ; " +
                "la clé debug est refusée.",
        )
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)
    // `:app` est le seul module qui voie `:data`. Une feature ne dépend que des
    // interfaces de `:core:model`, ce qui rend impossible un appel réseau depuis
    // un Composable — erreur de compilation, pas règle de revue.
    implementation(projects.data)
    implementation(projects.core.map)
    implementation(projects.core.location)
    implementation(projects.feature.map)
    implementation(projects.feature.auth)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
}
