package io.aule.android.core.designsystem.foundation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Borne le contenu d'un volet sous la barre d'état, poignée comprise.
 * Le palier et le contenu replié restent du ressort de chaque parcours.
 * Une fenêtre plus petite que son chrome ne produit jamais une hauteur négative.
 */
fun auleSheetContentHeight(
    windowHeight: Dp,
    topInset: Dp,
    gripHeight: Dp,
    topGap: Dp = 0.dp,
): Dp = (windowHeight - topInset - gripHeight - topGap).coerceAtLeast(0.dp)

/** Garde le palier dans la fenêtre et distinct du cran déployé, même en paysage. */
fun auleSheetPeekHeight(requestedHeight: Dp, expandedHeight: Dp): Dp =
    requestedHeight.coerceIn(0.dp, (expandedHeight - 1.dp).coerceAtLeast(0.dp))
