package io.aule.android.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import io.aule.android.core.designsystem.token.AuleRole

/** L'échelle partagée par Aule et Aule Pro, issue des fondations du design system. */
internal fun auleTypography(
    family: FontFamily = io.aule.android.core.designsystem.foundation.AuleFontFamily,
): Typography = io.aule.android.core.designsystem.foundation.auleTypography(family)

/** Les noms historiques restent utilisables par les écrans professionnels. */
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
 * Même fabrique que [auleTextStyle], pour que les trente slots partagent
 * Roboto, l'interligne et les chiffres plutôt que de le réinventer.
 */
private fun auleType(
    sizeSp: Float,
    lineHeightSp: Float,
    trackingSp: Float,
    weight: FontWeight = FontWeight.Normal,
    tabular: Boolean = false,
    family: FontFamily = Roboto,
): TextStyle = TextStyle(
    fontFamily = family,
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
