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
 * officielle (licence SIL OFL).
 *
 * Source : `https://github.com/google/fonts/tree/main/ofl/roboto`
 *
 * ## Ce que le fichier permet réellement
 *
 * Deux axes, relevés dans la table `fvar` du `.ttf` embarqué :
 *
 * - `wght` : **100 → 900**. Toute l'échelle de graisse est disponible, jusqu'au
 *   Black. C'est le levier d'emphase du produit.
 * - `wdth` : **75 → 100**. L'axe ne va que vers le condensé ; il n'existe pas de
 *   Roboto élargie dans ce fichier. Une variation demandée au-delà de 100 est
 *   silencieusement ramenée à 100 — c'est-à-dire sans effet, mais sans erreur
 *   non plus, ce qui est la pire des deux options. On n'y touche donc pas.
 *
 * Les quatre crans déclarés ci-dessous sont ceux dont l'échelle typographique
 * se sert. Un cran absent de cette liste **ne tombe pas en erreur** : Compose
 * choisit le plus proche déclaré, et un titre demandé en Black sortirait en
 * Bold sans que rien ne le signale — d'où l'intérêt de ne déclarer que ce qu'on
 * emploie, et de l'employer entièrement.
 *
 * [FontWeight.ExtraBold] et [FontWeight.Black] ont été retirés avec le gras
 * généralisé qui les réclamait. L'échelle appuyée culmine désormais à
 * `SemiBold` (voir `AuleType`), et [FontWeight.Bold] ne reste déclaré que pour
 * ce que le kit ne contrôle pas : un `SpanStyle` gras dans un texte annoté,
 * un composant Material qui demanderait le cran de son côté. Sans lui, ces
 * rares gras retomberaient sur `SemiBold` et l'emphase locale disparaîtrait.
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

/**
 * Space Grotesk, la typeface d'affichage de la marque.
 *
 * C'est la police d'aule.fr et des écrans d'accueil du web — connexion,
 * inscription, onboarding de SpacePro. Elle n'entre ici que pour ça : la
 * première image du produit doit être la même sur les trois surfaces, et une
 * grotesque à terminaisons coupées ne se confond avec aucune autre. Le reste de
 * l'application reste en [Roboto], qui est la voix de l'exploitation — celle
 * qu'on lit en vingt secondes, debout, et qui n'a pas à être remarquable.
 *
 * Le fichier est la variable officielle (licence SIL OFL).
 * Source : `https://github.com/google/fonts/tree/main/ofl/spacegrotesk`
 *
 * ## Ce que le fichier permet réellement
 *
 * Un seul axe, relevé dans la table `fvar` du `.ttf` embarqué : `wght`, de
 * **300 à 700**. Deux conséquences qu'on ne voit pas en relecture :
 *
 * - le défaut de l'axe est **300**, c'est-à-dire Light. Une graisse demandée
 *   sans réglage de variation sortirait donc en Light, y compris là où le style
 *   dit `Bold` — d'où les quatre crans déclarés ci-dessous, chacun avec sa
 *   variation ;
 * - l'échelle s'arrête à Bold. `ExtraBold` et `Black` n'existent pas, et une
 *   demande au-delà retomberait silencieusement sur le cran le plus proche. Le
 *   web ne monte pas plus haut non plus : ses titres d'auth sont en 700.
 */
internal val SpaceGrotesk = FontFamily(
    spaceGrotesk(FontWeight.Normal),
    spaceGrotesk(FontWeight.Medium),
    spaceGrotesk(FontWeight.SemiBold),
    spaceGrotesk(FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private fun spaceGrotesk(weight: FontWeight) = Font(
    resId = R.font.space_grotesk,
    weight = weight,
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
)
