package io.aule.android.core.designsystem

import io.aule.android.core.designsystem.ScreenSources.isComment
import java.io.File
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * La garde du design system.
 *
 * Le style graphique commun aux trois plateformes pose la règle et son moyen :
 * « un test de garde échoue sur la première réapparition »
 * (`dashboard/docs/carte-immersive/08-style-graphique.md`, § 5 et 6). Sans
 * elle, la règle tient le temps d'une revue de code : le prochain écran pressé
 * réinvente son ombre, son trait et son espacement, et le HUD finit avec neuf
 * variantes pour quatre intentions — ce qui vient d'arriver à l'écran de
 * connexion.
 *
 * La garde porte sur les **écrans** (`app/`, `feature/`), pas sur le design
 * system lui-même : c'est ici, et ici seulement, que les valeurs se décident.
 */
class DesignSystemGuardTest {

    @Test
    fun `aucun ecran ne chiffre une mesure a la main`() {
        val offenders = screenSources().flatMap { file ->
            file.readLines()
                .withIndex()
                .filter { (_, line) -> DP_LITERAL.containsMatchIn(line) && !line.isNamedConstant() }
                .map { (index, line) -> "${file.name}:${index + 1} ${line.trim()}" }
        }
        assertTrue(
            offenders.isEmpty(),
            "Une mesure appartient au design system, ou à une constante nommée :\n" +
                offenders.joinToString("\n"),
        )
    }

    /**
     * `Modifier.shadow` réclame une élévation **et** deux couleurs. Laissées
     * par défaut, elles rendent une ombre invisible de nuit ; passées à la main,
     * elles divergent d'un écran à l'autre.
     */
    @Test
    fun `aucun ecran ne pose son ombre lui-meme`() {
        val offenders = screenSources().filter { file ->
            file.readLines().any { RAW_SHADOW.containsMatchIn(it) }
        }
        assertTrue(
            offenders.isEmpty(),
            "Ces écrans doivent passer par auleShadow : " + offenders.joinToString { it.name },
        )
    }

    @Test
    fun `aucun ecran n invente une opacite`() {
        val offenders = screenSources().flatMap { file ->
            file.readLines()
                .withIndex()
                .filterNot { (_, line) -> line.isComment() }
                .filter { (_, line) -> RAW_ALPHA.containsMatchIn(line) }
                .map { (index, line) -> "${file.name}:${index + 1} ${line.trim()}" }
        }
        assertTrue(
            offenders.isEmpty(),
            "Une opacité d'écran doit venir d'AuleAlpha :\n" +
                offenders.joinToString("\n"),
        )
    }

    /**
     * Un hex dans un écran est une couleur qui échappe au thème : changer
     * `AuleTokens` ne la touchera pas, et le HUD finit avec deux verts Aule.
     */
    @Test
    fun `aucun ecran n ecrit une couleur en dur`() {
        val offenders = screenSources().flatMap { file ->
            file.readLines()
                .withIndex()
                .filterNot { (_, line) -> line.isComment() }
                .filter { (_, line) -> RAW_HEX.containsMatchIn(line) }
                .map { (index, line) -> "${file.name}:${index + 1} ${line.trim()}" }
        }
        assertTrue(
            offenders.isEmpty(),
            "Une couleur d'écran doit venir du thème ou d'un jeton sémantique :\n" +
                offenders.joinToString("\n"),
        )
    }

    /**
     * Ni emoji ni glyphe textuel : les premiers changent d'aspect d'un appareil
     * à l'autre, les seconds n'ont ni la graisse ni l'alignement de la famille
     * d'icônes. Une icône se dessine, elle ne s'écrit pas.
     */
    @Test
    fun `aucun ecran ne remplace une icone par un caractere`() {
        val offenders = screenSources().flatMap { file ->
            file.readLines()
                .withIndex()
                .filterNot { (_, line) -> line.isComment() }
                .filter { (_, line) -> FORBIDDEN_GLYPHS.any { it in line } }
                .map { (index, _) -> "${file.name}:${index + 1}" }
        }
        assertTrue(
            offenders.isEmpty(),
            "Un caractère tient lieu d'icône : " + offenders.joinToString(),
        )
    }

    private fun screenSources(): List<File> {
        val sources = ScreenSources.all()
        assumeTrue(sources.isNotEmpty(), "Racine du dépôt introuvable depuis ${File(".").absolutePath}")
        return sources
    }

    /** Une mesure nommée reste lisible et se relit d'un endroit unique. */
    private fun String.isNamedConstant(): Boolean =
        NAMED_CONSTANT.containsMatchIn(this) || ZERO_ONLY.containsMatchIn(this)

    private companion object {
        val DP_LITERAL = Regex("""\b\d+(\.\d+)?\.dp\b""")
        val NAMED_CONSTANT = Regex("""^\s*(private|internal)\s+val\s+\w+\s*=\s*[\d.]+\.dp""")

        /** `0.dp` ne mesure rien : c'est l'absence de mesure. */
        val ZERO_ONLY = Regex("""^[^"]*\b0\.dp\b""")

        val RAW_SHADOW = Regex("""\.shadow\(""")
        val RAW_ALPHA = Regex("""copy\(\s*alpha\s*=\s*\d""")
        val RAW_HEX = Regex("""(?:Color|AuleRgba)\s*\(\s*0x|#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\b""")

        val FORBIDDEN_GLYPHS = listOf("✕", "✖", "←", "→", "‹", "›", "▸", "▾", "⤓", "📍", "🚋", "🔔")
    }
}
