package io.aule.android.core.designsystem.token

import androidx.compose.ui.text.font.FontWeight

/**
 * Cinq rôles typographiques, ancrés dans l'échelle Material 3.
 *
 * Ce ne sont pas une deuxième échelle : ce sont des noms d'Aule pour cinq des
 * quinze slots Material. Les valeurs — taille, interligne, tracking, graisse —
 * sont celles des jetons `TypeScaleTokens` de `androidx.compose.material3`.
 * Changer un chiffre ici, c'est sortir de la charte M3.
 *
 * | Rôle    | Slot M3           | Taille / interligne | Graisse |
 * |---------|-------------------|---------------------|---------|
 * | KICKER  | `labelSmall`      | 11 / 16             | Medium  |
 * | BODY    | `bodyMedium`      | 14 / 20             | Regular |
 * | TITLE   | `titleMedium`     | 16 / 24             | Medium  |
 * | DATA    | `titleLarge`      | 22 / 28             | Regular |
 * | HERO    | `headlineMedium`  | 28 / 36             | Regular |
 *
 * La graisse du rôle est le défaut Material. Un appelant peut toujours en
 * passer une autre : un même palier s'écrit tantôt en regular, tantôt en gras,
 * sans changer de rôle.
 */
enum class AuleRole(
    val sizeSp: Float,
    val lineHeightSp: Float,
    val trackingSp: Float,
    val weight: FontWeight,
    val usesTabularFigures: Boolean,
) {
    /** Sur-titre, libellé de navigation. `labelSmall`. */
    KICKER(
        sizeSp = 11f,
        lineHeightSp = 16f,
        trackingSp = 0.5f,
        weight = FontWeight.Medium,
        usesTabularFigures = false,
    ),

    /** Le corps de texte. `bodyMedium`. */
    BODY(
        sizeSp = 14f,
        lineHeightSp = 20f,
        trackingSp = 0.2f,
        weight = FontWeight.Normal,
        usesTabularFigures = false,
    ),

    /** Titre de volet, nom d'arrêt. `titleMedium`. */
    TITLE(
        sizeSp = 16f,
        lineHeightSp = 24f,
        trackingSp = 0.2f,
        weight = FontWeight.Medium,
        usesTabularFigures = false,
    ),

    /** Un chiffre qu'on lit d'un coup d'œil : minutes d'attente, vitesse. `titleLarge`. */
    DATA(
        sizeSp = 22f,
        lineHeightSp = 28f,
        trackingSp = 0f,
        weight = FontWeight.Normal,
        usesTabularFigures = true,
    ),

    /** Le chiffre qui domine l'écran. `headlineMedium`. */
    HERO(
        sizeSp = 28f,
        lineHeightSp = 36f,
        trackingSp = 0f,
        weight = FontWeight.Normal,
        usesTabularFigures = true,
    );

    companion object {
        /** L'échelle, du plus petit au plus grand. L'ordre porte le rapport. */
        val ladder: List<AuleRole> = listOf(KICKER, BODY, TITLE, DATA, HERO)
    }
}
