package io.aule.android.core.designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Roboto, la typeface par défaut de Material 3.
 *
 * On l'embarque plutôt que de s'en remettre à [FontFamily.SansSerif] : sur
 * beaucoup de téléphones le sans-serif système n'est plus Roboto, et la charte
 * partirait avec la police du constructeur. Le fichier est la variable
 * officielle (axes `wght` et `wdth`, licence SIL OFL) : Regular, Medium,
 * SemiBold et Bold sont de vrais crans, pas des interpolations approximatives.
 *
 * Source : `https://github.com/google/fonts/tree/main/ofl/roboto`
 */
internal val Roboto = FontFamily(
    roboto(FontWeight.Normal),
    roboto(FontWeight.Medium),
    roboto(FontWeight.SemiBold),
    roboto(FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private fun roboto(weight: FontWeight) = Font(
    resId = R.font.roboto,
    weight = weight,
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
)
