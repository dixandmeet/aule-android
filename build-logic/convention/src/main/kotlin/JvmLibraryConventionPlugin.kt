import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Pour les modules qui ne dépendent de rien d'Android — `core:model`, `core:geo`.
 *
 * C'est précisément ce qui permet de les tester sur la JVM sans Robolectric ni
 * appareil, et c'est la raison de les avoir séparés.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureJvm()
    }
}
