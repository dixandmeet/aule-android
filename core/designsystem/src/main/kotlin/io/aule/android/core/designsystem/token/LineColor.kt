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

/**
 * En deçà de ce contraste avec la surface qui le porte, le badge reçoit un liseré.
 *
 * ## Le défaut, et pourquoi la bascule d'encre ne suffisait pas
 *
 * [LINE_BADGE_LUMINANCE_FLIP] sauve le **texte** d'un badge clair : « NC » s'y écrit en
 * sombre, donc il se lit. Il ne sauve pas la **plaque**, qui reste blanche sur un volet
 * blanc — vu à l'écran sur la ligne NC, dont la couleur GTFS est `#ffffff` exactement : le
 * numéro flottait dans la rangée, sans le rectangle coloré qui fait reconnaître une ligne
 * avant qu'on l'ait lue. C'est précisément ce que le badge existe pour donner.
 *
 * ## Pourquoi 1,35, et pas un chiffre rond
 *
 * Mesuré sur les 140 lignes de l'index embarqué (`transit-lines-index.json`), contre la
 * surface claire : neuf tombent sous 1,35 — le blanc du NC à 1,00, le jaune pâle du NO à
 * 1,11, le `#ffed00` que partagent sept lignes à 1,21, le vert d'eau du NS à 1,31 — et la
 * suivante est à 1,50. La distribution a un trou entre les deux, et le seuil s'y pose : il
 * prend le groupe des couleurs claires en entier, sans couper entre deux jaunes voisins que
 * rien ne distingue à l'œil.
 *
 * De nuit, aucune ligne du réseau ne descend sous 1,65 : le liseré ne paraît jamais, et c'est
 * juste — sur une surface sombre, ce sont ces couleurs-là qui éclatent.
 */
const val LINE_BADGE_OUTLINE_RATIO = 1.35

/**
 * Ce badge se confond-il avec la surface qui le porte ?
 *
 * Pure, et sortie du composant pour cette seule raison : c'est une règle de contraste, elle
 * se mesure. Voir [LINE_BADGE_OUTLINE_RATIO].
 */
fun lineBadgeNeedsOutline(background: AuleRgba, surface: AuleRgba): Boolean =
    background.contrastRatio(surface) < LINE_BADGE_OUTLINE_RATIO

/** L'encre à poser sur un badge de cette couleur. */
fun badgeInk(background: AuleRgba): AuleRgba =
    if (background.perceivedLuminance > LINE_BADGE_LUMINANCE_FLIP) {
        AulePalette.Neutral.ink
    } else {
        AulePalette.Teal.T100
    }
