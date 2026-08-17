package io.aule.android.core.designsystem

import java.io.File

/**
 * Les sources d'écran du dépôt : `app/` et `feature/`, hors tests.
 *
 * Les gardes du design system portent sur les écrans et non sur le design
 * system lui-même : c'est dans `:core:designsystem` — et là seulement — que les
 * couleurs, les mesures et les composants se décident.
 */
internal object ScreenSources {

    fun all(): List<File> {
        val root = repositoryRoot() ?: return emptyList()
        return listOf("app", "feature")
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { module ->
                module.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .filter { it.path.contains("${File.separator}main${File.separator}") }
                    .toList()
            }
    }

    fun repositoryRoot(): File? {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").isFile) return candidate
            candidate = candidate.parentFile
        }
        return null
    }

    /** La prose a le droit d'écrire une flèche ; l'interface, non. */
    fun String.isComment(): Boolean = trimStart().let {
        it.startsWith("*") || it.startsWith("//") || it.startsWith("/*")
    }
}
