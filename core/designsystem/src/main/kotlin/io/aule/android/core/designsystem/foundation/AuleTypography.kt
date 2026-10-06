package io.aule.android.core.designsystem.foundation

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import io.aule.android.core.designsystem.R as SocleR

/**
 * Les graisses de la langue Aule.
 *
 * Le brief les donne en axes de police variable : titres 600–640, bouton ≈ 580, libellés ≈ 600,
 * corps 400. Ce ne sont pas des crans Material (`Medium`, `SemiBold`) mais des valeurs d'axe,
 * et la famille ci-dessous les déclare **exactement** : Compose choisit la fonte déclarée la
 * plus proche du poids demandé, et lui applique ses réglages de variation.
 */
object AuleWeight {
    val display = FontWeight(640)
    val headline = FontWeight(620)
    val title = FontWeight(600)
    val label = FontWeight(600)
    val button = FontWeight(580)
    val bodyEmphasis = FontWeight(500)
    val body = FontWeight(400)
}

/**
 * La famille de la langue Aule.
 *
 * ## Google Sans Flex, et pourquoi elle n'est pas là
 *
 * La référence est Google Sans Flex, sur deux axes (`wdth`, `ROND`). Aucun fichier n'est
 * présent dans le dépôt, ni ici ni dans le socle : la déclaration `next/font/google` du web ne
 * constitue pas un asset Android, et rien ne se télécharge en silence. La famille est donc
 * **Roboto variable** — déjà embarquée par `:core:designsystem`, licence SIL OFL, axe `wght`
 * 100 → 900 — derrière un seul nom.
 *
 * Le jour où `google_sans_flex.ttf` entre dans `res/font/`, la bascule tient en une ligne : la
 * fabrique [face] ci-dessous. Toute l'échelle suit, sans qu'un écran ait à changer.
 */
val AuleFontFamily: FontFamily = FontFamily(
    face(AuleWeight.body),
    face(AuleWeight.bodyEmphasis),
    face(AuleWeight.button),
    face(AuleWeight.title),
    face(AuleWeight.headline),
    face(AuleWeight.display),
    face(FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private fun face(weight: FontWeight) = Font(
    resId = SocleR.font.roboto,
    weight = weight,
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
)

/**
 * L'échelle typographique Material 3, dans la voix d'Aule.
 *
 * Les quinze rôles publics gardent les tailles et interlignes du kit — c'est ce qui fait que
 * chaque composant Material tombe juste — et prennent les graisses du brief. Les quinze
 * variantes *emphasized* montent d'un cran ; les grandes tailles resserrent leur tracking, un
 * caractère plus gras ayant besoin de moins d'air.
 *
 * Deux détails que Compose ne pose pas seul : l'interligne **découpé au centre** (sans lui, un
 * texte d'une ligne descend dans sa boîte à côté d'une icône) et les **chiffres tabulaires** sur
 * les rôles qui affichent un compte — une attente qui change chaque minute ne doit pas faire
 * danser la ligne.
 *
 * `fontScale` reste celui du système : aucune taille n'est écrite en dp.
 */
internal fun auleTypography(family: FontFamily = AuleFontFamily): Typography {
    fun t(
        size: Float,
        line: Float,
        tracking: Float,
        weight: FontWeight,
        tabular: Boolean = false,
    ) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
        fontWeight = weight,
        fontFeatureSettings = if (tabular) "tnum" else null,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    return Typography(
        displayLarge = t(57f, 64f, -0.5f, AuleWeight.display),
        displayMedium = t(45f, 52f, -0.3f, AuleWeight.display),
        displaySmall = t(36f, 44f, -0.2f, AuleWeight.display),

        headlineLarge = t(32f, 40f, -0.3f, AuleWeight.headline),
        headlineMedium = t(28f, 36f, -0.2f, AuleWeight.headline, tabular = true),
        headlineSmall = t(24f, 32f, -0.1f, AuleWeight.headline),

        titleLarge = t(22f, 28f, 0f, AuleWeight.title, tabular = true),
        titleMedium = t(16f, 24f, 0.1f, AuleWeight.title),
        titleSmall = t(14f, 20f, 0.1f, AuleWeight.title),

        bodyLarge = t(16f, 24f, 0.3f, AuleWeight.body),
        bodyMedium = t(14f, 20f, 0.2f, AuleWeight.body),
        bodySmall = t(12f, 16f, 0.3f, AuleWeight.body),

        labelLarge = t(14f, 20f, 0.1f, AuleWeight.button),
        labelMedium = t(12f, 16f, 0.4f, AuleWeight.label),
        labelSmall = t(11f, 16f, 0.4f, AuleWeight.label),

        displayLargeEmphasized = t(57f, 64f, -0.7f, FontWeight.Bold),
        displayMediumEmphasized = t(45f, 52f, -0.5f, FontWeight.Bold),
        displaySmallEmphasized = t(36f, 44f, -0.4f, FontWeight.Bold),

        headlineLargeEmphasized = t(32f, 40f, -0.5f, AuleWeight.display),
        headlineMediumEmphasized = t(28f, 36f, -0.4f, AuleWeight.display, tabular = true),
        headlineSmallEmphasized = t(24f, 32f, -0.3f, AuleWeight.display),

        titleLargeEmphasized = t(22f, 28f, -0.2f, AuleWeight.headline, tabular = true),
        titleMediumEmphasized = t(16f, 24f, 0f, AuleWeight.headline),
        titleSmallEmphasized = t(14f, 20f, 0f, AuleWeight.headline),

        bodyLargeEmphasized = t(16f, 24f, 0.3f, AuleWeight.bodyEmphasis),
        bodyMediumEmphasized = t(14f, 20f, 0.2f, AuleWeight.bodyEmphasis),
        bodySmallEmphasized = t(12f, 16f, 0.3f, AuleWeight.bodyEmphasis),

        labelLargeEmphasized = t(14f, 20f, 0.1f, AuleWeight.headline),
        labelMediumEmphasized = t(12f, 16f, 0.4f, AuleWeight.headline),
        labelSmallEmphasized = t(11f, 16f, 0.4f, AuleWeight.headline),
    )
}
