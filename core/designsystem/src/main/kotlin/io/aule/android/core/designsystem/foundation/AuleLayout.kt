package io.aule.android.core.designsystem.foundation

import androidx.compose.ui.unit.dp

/**
 * Les mesures de la langue Aule qui ne sont ni un espacement ni un rayon.
 *
 * Les espacements restent ceux du socle (`AuleSpacing`, base 4) ; la refonte n'a aucune raison
 * d'en inventer une seconde grille. Ce qui vit ici est ce que le brief ajoute : les hauteurs de
 * contrôle, le plancher tactile, les gouttières de la carte.
 */
object AuleLayout {
    /** Le plancher tactile, tenu partout : 48 dp. */
    val touch = 48.dp

    /** Le bouton principal d'un volet ou d'un formulaire. */
    val button = 56.dp

    /** Un bouton secondaire, une rangée d'action. */
    val buttonCompact = 48.dp

    /** La capsule de recherche posée sur la carte, et le champ qui la remplace. */
    val searchBar = 60.dp

    /** Le champ de formulaire avec son libellé et sa saisie. */
    val field = 60.dp

    /** Un contrôle rond de la carte : localiser, autour de vous, la voix. */
    val mapControl = 52.dp

    /** Le contrôle principal de la carte. */
    val mapControlLarge = 60.dp

    /** La grille d'icône. */
    val icon = 24.dp

    /** Un glyphe dans une pastille ou devant une note. */
    val glyph = 20.dp

    /** Le petit signe d'une note ou d'une mesure, à la taille de son texte. */
    val noteGlyph = 16.dp

    /** Le médaillon d'une rangée. */
    val medallion = 44.dp

    /** Le médaillon d'un état vide. */
    val emptyMedallion = 88.dp

    /** L'avatar du compte, dans la capsule de recherche. */
    val avatar = 36.dp

    /** L'avatar en tête d'une feuille ou d'un volet : le sujet, plus grand que la pastille de la capsule. */
    val avatarHero = 56.dp

    /** La gouttière du chrome flottant, tout autour de la carte. */
    val gutter = 16.dp

    /** La largeur au-delà de laquelle une colonne de formulaire cesse de grandir. */
    val formMaxWidth = 420.dp

    /** La prise d'un volet. */
    val gripWidth = 36.dp
    val gripHeight = 4.dp
}

/**
 * Les opacités nommées. Une opacité écrite à la main est une couleur inventée.
 */
object AuleOpacity {
    /** Le verre posé sur la carte : lisible sur n'importe quelle tuile, et l'on sait qu'il y a une carte dessous. */
    const val GLASS = 0.92f

    /** Le liseré d'une surface de verre. */
    const val GLASS_EDGE = 0.70f

    /** Un contrôle qui ne répond plus. */
    const val DISABLED = 0.38f

    /** Ce qui est passé : une minute déjà écoulée dans une grille. */
    const val PAST = 0.45f

    /** La poignée d'un volet, un chevron : présent, jamais lu en premier. */
    const val SUBDUED = 0.35f

    /** Un fantôme de chargement. */
    const val SKELETON = 0.12f

    /** Le voile derrière un volet modal. */
    const val SCRIM = 0.32f

    /** Le lavis de marque sous un glyphe. */
    const val WASH = 0.14f

    /** Le contour d'un aplat teinté. */
    const val OUTLINE = 0.30f
}

/**
 * Trois hauteurs d'ombre, pas plus : le brief veut une faible dépendance aux ombres.
 *
 * Le décollement vient de la couleur des surfaces et du liseré ; l'ombre ne dit que la
 * distance, et elle la dit à voix basse.
 */
object AuleElevation {
    val none = 0.dp

    /** Ce qui flotte sur la carte : la capsule de recherche, un contrôle, un bandeau. */
    val floating = 3.dp

    /** Le volet, qui recouvre une part de l'écran. */
    val lifted = 8.dp
}
