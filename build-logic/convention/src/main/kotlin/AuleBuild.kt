import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Le catalogue de versions du projet, lu depuis un plugin de convention. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.versionInt(name: String): Int =
    findVersion(name).get().requiredVersion.toInt()

/**
 * La configuration Android commune à tous les modules.
 *
 * Elle vit ici et nulle part ailleurs : un `android { }` recopié dans dix modules,
 * c'est dix endroits où le `minSdk` peut diverger sans que rien ne le signale.
 *
 * Le `targetSdk` n'y est pas : depuis AGP 9 il n'existe que sur l'extension
 * application, et c'est juste — une bibliothèque n'a pas de cible propre, elle
 * hérite de celle de l'application qui la consomme.
 */
internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.compileSdk = libs.versionInt("compileSdk")
    extension.defaultConfig.minSdk = libs.versionInt("minSdk")

    extension.compileOptions.apply {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    extension.packaging.resources.excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/LICENSE*",
        "/META-INF/DEPENDENCIES",
    )

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            allWarningsAsErrors = false
        }
    }

    configureTests()
}

internal fun Project.configureJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    configureTests()
}

/**
 * JUnit 5 partout, y compris pour les modules Android : les tests unitaires
 * tournent sur la JVM de l'hôte et n'ont aucune raison de rester sur JUnit 4.
 */
internal fun Project.configureTests() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            showStackTraces = true
            showExceptions = true
        }
    }
    // JUnit 5 pour le cycle de vie et les annotations ; kotlin.test pour les
    // assertions, qui se lisent mieux et restent valables si le runner change.
    dependencies.add("testImplementation", libs.findLibrary("junit-jupiter").get())
    dependencies.add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
    dependencies.add("testImplementation", libs.findLibrary("kotlin-test").get())
    dependencies.add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
}
