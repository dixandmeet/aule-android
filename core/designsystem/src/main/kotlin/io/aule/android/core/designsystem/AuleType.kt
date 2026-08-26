package io.aule.android.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
 *
 * ## Les quinze slots *emphasized*
 *
 * Material 3 Expressive double l'échelle : à côté de chaque slot vit une
 * variante **appuyée**, et c'est elle qui porte l'expressivité typographique du
 * kit. Le principe tient en une phrase : *même encombrement, plus de présence*.
 * Taille et interligne ne bougent pas d'un point — un titre appuyé occupe la
 * même boîte que son homologue ordinaire, donc les substituer ne casse aucune
 * mise en page. Ce qui change est la **graisse**, et sur les grandes tailles le
 * **tracking**, resserré parce qu'un caractère plus gras a besoin de moins
 * d'air pour se détacher.
 *
 * C'est ce couple qui manquait à Aule. L'application affichait ses heures
 * d'arrivée, ses temps restants et ses noms d'arrêt au même poids que le texte
 * courant : tout se lisait, rien ne ressortait. Les slots appuyés donnent aux
 * écrans de quoi désigner ce qui compte sans changer de taille — et sans
 * inventer un `fontWeight` au cas par cas, ce qui est exactement la dérive que
 * l'échelle existe pour empêcher.
 *
 * ## L'appui vaut **un** cran de graisse, jamais deux
 *
 * Les quinze variantes ont d'abord été posées en `Bold`, soit deux crans
 * au-dessus du texte courant et trois au-dessus du corps. C'était une erreur
 * d'échelle, et elle ne se voit pas sur un composant isolé : elle se voit sur
 * un écran entier. Quatre-vingt-dix-sept appels s'en servent — le titre du
 * volet, le nom du prochain arrêt, le temps restant, l'intitulé qui l'annonce,
 * le libellé du bouton — si bien que tout ce qui compte était gras, donc que
 * plus rien ne ressortait. Une hiérarchie où le rang le plus haut est occupé
 * par les trois quarts du texte n'est plus une hiérarchie.
 *
 * L'appui est donc **`SemiBold`**, un cran au-dessus du `Medium` des titres et
 * deux au-dessus du corps. La distance reste lisible — c'est le même écart que
 * celui qui sépare déjà un titre d'un paragraphe — et l'écran cesse de crier.
 * Seuls les trois rôles de corps s'arrêtent à `Medium` : un paragraphe appuyé
 * en `SemiBold` devient un pavé gras, ce qu'aucun texte long ne supporte.
 *
 * Le **tracking** suit la graisse. Un caractère gras a besoin de moins d'air
 * pour se détacher, donc les grandes tailles se resserraient jusqu'à −0,6 ;
 * à `SemiBold` ce resserrement devient une crispation, et il retombe de
 * moitié. Sur les trois `label`, il rejoint tout simplement celui du slot
 * ordinaire : appuyer un libellé de onze points ne justifie pas de lui changer
 * son espacement en plus de sa graisse.
 */
internal fun auleTypography(family: FontFamily = Roboto): Typography {
    // Les trente slots ne disent que des métriques ; la famille, elle, est un
    // paramètre de l'écran. Deux fabriques locales la portent, pour que le
    // tableau ci-dessous reste ce qu'il doit rester : la liste des tailles.
    fun t(
        sizeSp: Float,
        lineHeightSp: Float,
        trackingSp: Float,
        weight: FontWeight = FontWeight.Normal,
        tabular: Boolean = false,
    ) = auleType(sizeSp, lineHeightSp, trackingSp, weight, tabular, family)

    fun r(role: AuleRole) = auleTextStyle(role).copy(fontFamily = family)

    return Typography(
        displayLarge = t(57f, 64f, -0.2f),
        displayMedium = t(45f, 52f, 0f),
        displaySmall = t(36f, 44f, 0f),

        headlineLarge = t(32f, 40f, 0f),
        headlineMedium = r(AuleRole.HERO),
        headlineSmall = t(24f, 32f, 0f),

        titleLarge = r(AuleRole.DATA),
        titleMedium = r(AuleRole.TITLE),
        titleSmall = t(14f, 20f, 0.1f, FontWeight.Medium),

        bodyLarge = t(16f, 24f, 0.5f),
        bodyMedium = r(AuleRole.BODY),
        bodySmall = t(12f, 16f, 0.4f),

        labelLarge = t(14f, 20f, 0.1f, FontWeight.Medium),
        labelMedium = t(12f, 16f, 0.5f, FontWeight.Medium),
        labelSmall = r(AuleRole.KICKER),

        // Les quinze variantes appuyées. Mêmes métriques, à la ligne près.
        displayLargeEmphasized = t(57f, 64f, -0.3f, AULE_EMPHASIS),
        displayMediumEmphasized = t(45f, 52f, -0.2f, AULE_EMPHASIS),
        displaySmallEmphasized = t(36f, 44f, -0.15f, AULE_EMPHASIS),

        headlineLargeEmphasized = t(32f, 40f, -0.15f, AULE_EMPHASIS),
        headlineMediumEmphasized = t(28f, 36f, -0.1f, AULE_EMPHASIS, tabular = true),
        headlineSmallEmphasized = t(24f, 32f, 0f, AULE_EMPHASIS),

        titleLargeEmphasized = t(22f, 28f, 0f, AULE_EMPHASIS, tabular = true),
        titleMediumEmphasized = t(16f, 24f, 0.2f, AULE_EMPHASIS),
        titleSmallEmphasized = t(14f, 20f, 0.1f, AULE_EMPHASIS),

        bodyLargeEmphasized = t(16f, 24f, 0.5f, AULE_BODY_EMPHASIS),
        bodyMediumEmphasized = t(14f, 20f, 0.2f, AULE_BODY_EMPHASIS),
        bodySmallEmphasized = t(12f, 16f, 0.4f, AULE_BODY_EMPHASIS),

        labelLargeEmphasized = t(14f, 20f, 0.1f, AULE_EMPHASIS),
        labelMediumEmphasized = t(12f, 16f, 0.5f, AULE_EMPHASIS),
        labelSmallEmphasized = t(11f, 16f, 0.5f, AULE_EMPHASIS),
    )
}

/**
 * La graisse de l'appui : un cran au-dessus des titres, deux au-dessus du corps.
 *
 * Nommée plutôt qu'écrite quinze fois, parce que c'est **une** décision. Écrite
 * quinze fois, elle se serait défaite au premier slot qu'on aurait voulu « juste
 * un peu plus fort », et l'échelle serait repartie vers le gras généralisé
 * qu'elle vient de quitter.
 */
private val AULE_EMPHASIS = FontWeight.SemiBold

/**
 * L'appui du corps de texte, qui s'arrête un demi-cran plus bas.
 *
 * Un paragraphe en `SemiBold` n'est plus un paragraphe appuyé, c'est un pavé
 * gras : la graisse se lit sur un mot, elle s'endure mal sur cinq lignes.
 */
private val AULE_BODY_EMPHASIS = FontWeight.Medium

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
