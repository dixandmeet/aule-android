package io.aule.android.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import io.aule.android.core.designsystem.token.AuleRole

/**
 * L'échelle typographique Material 3, en Roboto.
 *
 * Les quinze rôles publics de [Typography] reprennent les jetons
 * `TypeScaleTokens` de `androidx.compose.material3` : taille, interligne,
 * tracking et graisse. On n'y ajoute que ce que Compose ne pose pas tout seul :
 * la famille [Roboto], l'interligne découpé au centre, et les chiffres
 * tabulaires sur les deux rôles Aule qui affichent un compte.
 *
 * Les cinq rôles historiques d'Aule — [AuleRole] — s'ancrent dans cette
 * échelle aux slots où Material les attend :
 *
 * | Rôle Aule | Slot Material 3 | Jeton M3          |
 * |-----------|-----------------|-------------------|
 * | `KICKER`  | `labelSmall`    | 11 / 16, Medium   |
 * | `BODY`    | `bodyMedium`    | 14 / 20, Regular  |
 * | `TITLE`   | `titleMedium`   | 16 / 24, Medium   |
 * | `DATA`    | `titleLarge`    | 22 / 28, Regular  |
 * | `HERO`    | `headlineMedium`| 28 / 36, Regular  |
 *
 * Les dix autres slots se remplissent aussi. Un slot laissé vide retombe
 * silencieusement sur la valeur Material par défaut — et donc sur le
 * sans-serif du téléphone, plus sur Roboto.
 */
internal fun auleTypography(): Typography = Typography(
    displayLarge = auleType(57f, 64f, -0.2f),
    displayMedium = auleType(45f, 52f, 0f),
    displaySmall = auleType(36f, 44f, 0f),

    headlineLarge = auleType(32f, 40f, 0f),
    headlineMedium = auleTextStyle(AuleRole.HERO),
    headlineSmall = auleType(24f, 32f, 0f),

    titleLarge = auleTextStyle(AuleRole.DATA),
    titleMedium = auleTextStyle(AuleRole.TITLE),
    titleSmall = auleType(14f, 20f, 0.1f, FontWeight.Medium),

    bodyLarge = auleType(16f, 24f, 0.5f),
    bodyMedium = auleTextStyle(AuleRole.BODY),
    bodySmall = auleType(12f, 16f, 0.4f),

    labelLarge = auleType(14f, 20f, 0.1f, FontWeight.Medium),
    labelMedium = auleType(12f, 16f, 0.5f, FontWeight.Medium),
    labelSmall = auleTextStyle(AuleRole.KICKER),
)

/**
 * Le style d'un rôle typographique Aule.
 *
 * L'interligne est posé en absolu et **découpé au centre** : sans
 * [LineHeightStyle], Compose ajoute tout l'espace supplémentaire sous la
 * dernière ligne, ce qui décentre un texte d'une ligne dans sa boîte — visible
 * dès qu'on aligne un chiffre à côté d'une icône.
 *
 * Les chiffres à chasse fixe (`tnum`) évitent qu'un compte à rebours fasse
 * danser la ligne à chaque seconde.
 */
fun auleTextStyle(role: AuleRole, weight: FontWeight = role.weight): TextStyle = auleType(
    sizeSp = role.sizeSp,
    lineHeightSp = role.lineHeightSp,
    trackingSp = role.trackingSp,
    weight = weight,
    tabular = role.usesTabularFigures,
)

/**
 * Un style de l'échelle, hors des cinq rôles ancrés.
 *
 * Même fabrique que [auleTextStyle], pour que les quinze slots partagent
 * Roboto, l'interligne et les chiffres plutôt que de le réinventer.
 */
private fun auleType(
    sizeSp: Float,
    lineHeightSp: Float,
    trackingSp: Float,
    weight: FontWeight = FontWeight.Normal,
    tabular: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = Roboto,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = trackingSp.sp,
    fontWeight = weight,
    fontFeatureSettings = if (tabular) "tnum" else null,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)
