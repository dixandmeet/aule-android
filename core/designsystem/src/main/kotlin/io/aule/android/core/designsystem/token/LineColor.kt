package io.aule.android.core.designsystem.token

/** Le gris de repli d'un badge de ligne. */
val LINE_FALLBACK_COLOR = AuleRgba(0x525252)

/**
 * Au-dessus de ce seuil de luminance perçue, le badge s'écrit en sombre.
 *
 * Le seuil existe pour une raison très concrète : le réseau de nuit a une
 * couleur GTFS **blanche**, et sans bascule « N1 » s'écrivait en blanc sur blanc.
 */
const val LINE_BADGE_LUMINANCE_FLIP = 0.65

/**
 * Lit une couleur de ligne telle que le GTFS la donne.
 *
 * Les formes rencontrées dans les données réelles : `#RRGGBB`, `RRGGBB` sans
 * dièse, `#RGB` en trois chiffres, et n'importe laquelle entourée d'espaces.
 *
 * Une couleur illisible donne le repli, **jamais rien** : le badge porte le
 * numéro de ligne, et mieux vaut un gris qu'un badge absent.
 */
fun parseLineColor(raw: String?): AuleRgba {
    val cleaned = raw?.trim()?.removePrefix("#") ?: return LINE_FALLBACK_COLOR
    val hex = when (cleaned.length) {
        6 -> cleaned
        3 -> cleaned.map { "$it$it" }.joinToString("")
        else -> return LINE_FALLBACK_COLOR
    }
    val value = hex.toIntOrNull(radix = 16) ?: return LINE_FALLBACK_COLOR
    return AuleRgba(value)
}

/** L'encre à poser sur un badge de cette couleur. */
fun badgeInk(background: AuleRgba): AuleRgba =
    if (background.perceivedLuminance > LINE_BADGE_LUMINANCE_FLIP) {
        AulePalette.Neutral.ink
    } else {
        AulePalette.Teal.T100
    }
