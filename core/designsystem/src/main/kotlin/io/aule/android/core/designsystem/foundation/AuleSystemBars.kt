package io.aule.android.core.designsystem.foundation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Ce qu'Android se réserve vraiment au bas de l'écran.
 *
 * ## ⚠️ Pourquoi `navigationBarsPadding()` ne suffit pas
 *
 * Relevé sur un Galaxy S21 (One UI, Android 15) en recette le 22/09/2026 : trois touches
 * affichées à l'écran, `navigation_mode = 0`, et pourtant Android annonce aux applications un
 * encart `navigationBars` de **45 px — 15 dp**, la hauteur d'une poignée de gestes. Il peint
 * par-dessus une barre de **144 px — 48 dp** de vraies touches.
 *
 * Un volet qui ne réserve que l'encart annoncé laisse donc ses trente-trois derniers points sous
 * les touches. Ce n'est pas un défaut d'esthétique : l'appui **part au système**. Toucher
 * « Démarrer » d'un itinéraire renvoyait à l'écran d'accueil du téléphone, et la dernière
 * destination récente ouvrait le multitâche — vérifié deux fois chacun.
 *
 * ## La mesure juste
 *
 * `mandatorySystemGestures` dit ce qu'Android s'octroie en bas **dans les deux modes** :
 * 48 dp pour la barre à trois touches, 48 dp pour la bande d'accueil et de retour en mode
 * gestes. Dans les deux cas, rien de **touchable** n'y a sa place. On prend donc le plus grand
 * des deux encarts : la mise en page ne dépend plus d'un seul chiffre, et surtout plus de celui
 * qui se trompe.
 *
 * ⚠️ **Ce n'est pas une marge de confort.** Elle vaut pour ce que le doigt doit atteindre. Un
 * fond, une ombre ou un tracé décoratif peuvent continuer de descendre jusqu'au bord — c'est
 * même ce qu'il faut, sans quoi une bande claire apparaît sous la feuille.
 */
val auleBottomSystemInset: Dp
    @Composable
    get() = maxOf(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        WindowInsets.mandatorySystemGestures.asPaddingValues().calculateBottomPadding(),
    )

/**
 * Réserve [auleBottomSystemInset] au bas du contenu.
 *
 * Remplace `navigationBarsPadding()` partout où ce qui touche le bas est **touchable** : volets,
 * feuilles modales, formulaires. Voir l'en-tête ci-dessus pour ce qui s'y joue.
 * Les insets sont consommés : un enfant ou le clavier ne réserve pas une seconde fois
 * la même bande. [systemInsets] permet à un hôte de fournir ses propres insets.
 */
@Composable
fun Modifier.auleBottomSystemPadding(
    systemInsets: WindowInsets = WindowInsets.navigationBars.union(WindowInsets.mandatorySystemGestures),
): Modifier = windowInsetsPadding(systemInsets.only(WindowInsetsSides.Bottom))
