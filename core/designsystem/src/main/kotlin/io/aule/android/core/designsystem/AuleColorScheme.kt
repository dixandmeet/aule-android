package io.aule.android.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import io.aule.android.core.designsystem.token.AulePalette
import io.aule.android.core.designsystem.token.AuleTokens

/**
 * Les rôles Material 3, servis par les jetons HUD.
 *
 * Un rôle n'est pas une couleur : c'est un **emploi**. `primary` est l'action
 * qu'on veut voir en premier, `surfaceContainer` est ce sur quoi une carte se
 * pose, `outlineVariant` est ce qui sépare sans découper. Les composants
 * Material lisent ces rôles ; changer un rôle ici change l'application entière.
 *
 * Quatre règles tiennent le fichier :
 *
 * 1. **Aucune couleur n'est écrite ici.** Tout vient d'`AuleTokens` ou
 *    d'`AulePalette`. Un rôle qui aurait besoin d'une teinte absente signale
 *    un manque dans la palette, pas une exception à faire ici.
 *
 * 2. **`primary` reste une encre sur `surface`.** Material s'en sert comme
 *    contenu (bouton texte, icône sélectionnée, titre). De nuit, l'aplat HUD
 *    (`accent`, `#005255`) n'a pas le contraste : `primary` prend alors
 *    `accentOnSurface`, et l'aplat va dans `primaryContainer` — c'est lui que
 *    le FAB lit. Confondre les deux rôles, c'est le texte illisible.
 *
 * 3. **`secondary` porte la marque, pas le métier.** Material se sert de
 *    `secondaryContainer` comme aplat de **sélection** : c'est lui que prennent
 *    la puce de filtre, le bouton segmenté, l'indicateur de la barre de
 *    navigation, la rangée choisie d'une liste. Il portait le conteneur du
 *    temps réel, au motif que « dans Aule, secondaire veut dire vivant » — une
 *    idée juste sur le papier et fausse à l'écran : elle donnait un aplat vert
 *    menthe à **tout** ce qui est sélectionné, c'est-à-dire à des dizaines
 *    d'états qui n'ont aucun rapport avec une donnée mesurée. Un filtre « Bus »
 *    actif s'affichait dans la couleur qui, deux centimètres plus bas, signifie
 *    « ce passage vient du véhicule ».
 *
 *    Le temps réel n'a rien perdu : il n'a jamais été lu depuis le
 *    `ColorScheme`. Les neuf endroits qui l'affichent passent tous par
 *    `AuleTheme.tokens.realtime` ou `realtimeInk()`, et c'est la bonne porte —
 *    un fait transport n'est pas un cran de hiérarchie visuelle. Le rôle
 *    Material revient donc à la famille de la marque, un cran plus soutenu que
 *    `primaryContainer` pour qu'une sélection se distingue d'une action.
 *
 * 4. **L'échelle de conteneurs est opaque, et elle est une échelle.** Material
 *    lit `surfaceContainer*` comme cinq niveaux distincts : c'est ce qui
 *    distingue une carte de son fond, un menu de la page, un volet de la
 *    carte. Les cinq crans ont donc cinq tons — et aucun n'est translucide.
 *    Un conteneur translucide laisse remonter ce qu'il y a dessous : sur la
 *    carte, un volet de saisie devient illisible, et la barre de navigation
 *    se salit du bâtiment qui passe derrière.
 *
 *    Le **verre** reste l'identité d'Aule, mais il se demande : c'est
 *    [AuleTokens.surface], et les surfaces de carte le posent explicitement.
 *
 * ## Ce qui a changé, et pourquoi
 *
 * L'échelle claire tenait dans six centièmes de clarté : `#F7FAF9`, `#F2F6F4`,
 * `#ECF1EF`, `#E6EBE9`. Cinq crans nommés, un seul blanc perçu. Une carte posée
 * sur un volet ne se détachait donc pas, et il fallait lui dessiner un contour
 * pour qu'elle existe — d'où l'impression d'écran fade, malgré une hiérarchie
 * correcte sur le papier.
 *
 * Les cinq crans descendent maintenant jusqu'à `#D9E4E4`, un gris franc. La
 * hiérarchie se voit sans contour, les contours peuvent redevenir discrets, et
 * l'ombre reprend son rôle — dire la **distance**, pas rattraper l'absence de
 * contraste.
 *
 * Les couleurs dynamiques restent désactivées : l'identité d'Aule ne dépend pas
 * du fond d'écran de l'appareil.
 */
internal fun auleLightColorScheme(): ColorScheme {
    val tokens = AuleTokens.day
    return lightColorScheme(
        primary = tokens.accent.color,
        onPrimary = tokens.onAccent.color,
        primaryContainer = AulePalette.Teal.T90.color,
        onPrimaryContainer = AulePalette.Teal.T10.color,
        inversePrimary = AulePalette.Teal.T80.color,

        secondary = AulePalette.Teal.T40.color,
        onSecondary = AulePalette.Teal.T100.color,
        secondaryContainer = AulePalette.Teal.T80.color,
        onSecondaryContainer = AulePalette.Teal.T10.color,

        tertiary = AulePalette.Amber.T40.color,
        onTertiary = AulePalette.Teal.T100.color,
        tertiaryContainer = tokens.delay.container.color,
        onTertiaryContainer = tokens.delay.onContainer.color,

        background = tokens.surfaceSolid.color,
        onBackground = tokens.onSurface.color,

        surface = tokens.surfaceSolid.color,
        onSurface = tokens.onSurface.color,
        surfaceVariant = AulePalette.Neutral.T92.color,
        onSurfaceVariant = tokens.onSurfaceMuted.color,
        surfaceTint = tokens.accent.color,

        surfaceBright = AulePalette.Neutral.T100.color,
        surfaceDim = AulePalette.Neutral.T87.color,
        surfaceContainerLowest = AulePalette.Neutral.T100.color,
        surfaceContainerLow = AulePalette.Neutral.T96.color,
        surfaceContainer = AulePalette.Neutral.T94.color,
        surfaceContainerHigh = AulePalette.Neutral.T92.color,
        surfaceContainerHighest = AulePalette.Neutral.T90.color,

        inverseSurface = AulePalette.Neutral.T22.color,
        inverseOnSurface = AulePalette.Neutral.T94.color,

        // L'aplat HUD (`alert`) ne tient pas 4,5:1 sous du blanc. Le rôle
        // Material sert de texte d'erreur : on descend d'un cran.
        error = AulePalette.Red.T40.color,
        onError = tokens.onAlert.color,
        errorContainer = AulePalette.Red.T90.color,
        onErrorContainer = AulePalette.Red.T10.color,

        outline = AulePalette.Neutral.T50.color,
        outlineVariant = tokens.hairline.color,

        scrim = AulePalette.Neutral.T0.color,
    )
}

/**
 * La nuit d'Aule.
 *
 * L'aplat HUD reste un aplat (`primaryContainer`). L'encre de marque, elle,
 * s'éclaircit (`primary` = `accentOnSurface`) pour rester lisible sur la
 * surface quasi-noire — et c'est cette encre que Material pose sur un titre
 * ou une icône.
 *
 * La nuit a par ailleurs **changé de teinte**. Son aplat primaire était un vert
 * à 168° et son encre un vert menthe, quand le jour est teal à 200° : Aule
 * avait donc deux identités selon l'heure, et personne ne l'avait vu parce
 * qu'on ne compare jamais les deux ambiances côte à côte. Les deux jeux
 * partagent maintenant la même teinte.
 */
internal fun auleDarkColorScheme(): ColorScheme {
    val tokens = AuleTokens.night
    return darkColorScheme(
        primary = tokens.accentOnSurface.color,
        onPrimary = tokens.surfaceSolid.color,
        primaryContainer = tokens.accent.color,
        onPrimaryContainer = tokens.onAccent.color,
        inversePrimary = AulePalette.Teal.T30.color,

        secondary = AulePalette.Teal.T60.color,
        onSecondary = AulePalette.Neutral.T8.color,
        secondaryContainer = AulePalette.Teal.T20.color,
        onSecondaryContainer = AulePalette.Teal.T90.color,

        tertiary = tokens.delay.color.color,
        onTertiary = tokens.delay.onColor.color,
        tertiaryContainer = tokens.delay.container.color,
        onTertiaryContainer = tokens.delay.onContainer.color,

        background = tokens.surfaceSolid.color,
        onBackground = tokens.onSurface.color,

        surface = tokens.surfaceSolid.color,
        onSurface = tokens.onSurface.color,
        surfaceVariant = AulePalette.Neutral.T30.color,
        onSurfaceVariant = tokens.onSurfaceMuted.color,
        surfaceTint = tokens.accentOnSurface.color,

        surfaceBright = AulePalette.Neutral.T24.color,
        surfaceDim = AulePalette.Neutral.T8.color,
        surfaceContainerLowest = AulePalette.Neutral.T6.color,
        surfaceContainerLow = AulePalette.Neutral.T10.color,
        surfaceContainer = AulePalette.Neutral.T12.color,
        surfaceContainerHigh = AulePalette.Neutral.T17.color,
        surfaceContainerHighest = AulePalette.Neutral.T22.color,

        inverseSurface = AulePalette.Neutral.T90.color,
        inverseOnSurface = AulePalette.Neutral.T20.color,

        // L'aplat HUD (`alert`) tient le texte sur la surface ; l'encre HUD
        // `onAlert` ne tient pas 4,5:1 dessus. Le rôle Material sert de texte
        // sur l'aplat d'erreur : on prend le cran 10 de la rampe.
        error = tokens.alert.color,
        onError = AulePalette.Red.T10.color,
        errorContainer = AulePalette.Red.T30.color,
        onErrorContainer = AulePalette.Red.T90.color,

        outline = AulePalette.Neutral.T60.color,
        outlineVariant = tokens.hairline.color,

        scrim = AulePalette.Neutral.T0.color,
    )
}
