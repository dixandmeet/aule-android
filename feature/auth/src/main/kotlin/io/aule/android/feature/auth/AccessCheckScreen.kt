package io.aule.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.AuleTypeface
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleNetworkBackdrop
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing

/**
 * Le temps de résoudre les habilitations, avant la carte.
 *
 * Flutter pose le même écran (`SAE/lib/app_shell.dart`) : monter MapLibre
 * pour un compte voyageur, puis le démonter une seconde plus tard, n'est
 * pas une vérification, c'est une fuite.
 *
 * ## Ce que l'écran doit dire
 *
 * C'est un écran d'**état**, et un écran d'état qui n'énonce pas lequel ne vaut
 * pas mieux qu'un écran blanc. Une roue seule dit « quelque chose tourne » ;
 * elle ne dit ni quoi, ni pour combien de temps, ni ce qui se passera ensuite.
 * L'état est donc énoncé trois fois, dans trois registres qui ne se répètent
 * pas :
 *
 * - le **bouclier**, sur la surface de marque : de quoi il s'agit — une
 *   habilitation, pas un chargement de données ;
 * - la **roue** qui l'entoure : que c'est en cours, et que ça finira ;
 * - le **texte** : qui vérifie quoi, en toutes lettres, pour qui lit l'écran
 *   avec TalkBack et n'aura jamais ni le bouclier ni la roue.
 *
 * L'anneau tourne **autour** de la tuile plutôt qu'à côté d'elle : deux objets
 * séparés donnent deux points de fixation à un regard qui n'a rien à faire
 * d'autre pendant une seconde, et l'écran se met à osciller. Concentriques, ils
 * n'en font qu'un.
 *
 * La cascade d'entrée porte sur les deux lignes de texte, et sur elles seules.
 * L'anneau, lui, est là dès la première image : c'est un écran qui ne dure
 * peut-être qu'une demi-seconde, et faire attendre le seul signe que quelque
 * chose travaille est la façon la plus sûre de transformer une attente courte
 * en écran figé.
 */
@Composable
fun AccessCheckScreen(modifier: Modifier = Modifier) {
    // La même porte d'entrée que la connexion : cet écran s'intercale entre
    // elle et la carte, et un fond qui change entre les deux se lit comme un
    // saut d'application.
    AuleTheme(night = true, typeface = AuleTypeface.BRAND) {
        val colors = MaterialTheme.colorScheme
        AuleNetworkBackdrop(modifier = modifier, quiet = true) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(AuleSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(
                    AuleSpacing.lg,
                    Alignment.CenterVertically,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Sans cascade, et c'est la seule chose de l'écran qui n'en
                // prenne pas : ce qui prouve qu'on travaille doit être là à la
                // première image. Un anneau qui se dépose en fondu offre un
                // quart de seconde d'écran muet — exactement ce que l'anneau
                // existe pour éviter.
                Box(
                    modifier = Modifier.padding(bottom = AuleSpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CHECK_RING),
                        color = colors.primary,
                        // Et non `AuleStroke.glyph` : ce trait-là appartient à
                        // la grille d'icône, où 1,75 dp sur 24 dp fait la
                        // famille. Le même trait sur un anneau de 88 dp donne
                        // un fil — invisible en plein soleil, qui est
                        // précisément la condition d'usage.
                        strokeWidth = CHECK_RING_STROKE,
                    )
                    // La seule surface de marque de l'écran, et il n'y a rien
                    // d'autre à regarder : c'est le cas où elle se justifie
                    // sans discussion.
                    AuleBrandSurface(shape = MaterialTheme.shapes.medium) {
                        Icon(
                            imageVector = AuleGlyph.SHIELD.asImageVector(filled = true),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(AuleSpacing.lg)
                                .size(AuleControl.icon),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.auth_checking_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .semantics { heading() }
                        .auleEnter(index = 1),
                )
                Text(
                    text = stringResource(R.string.auth_checking_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.auleEnter(index = 2),
                )
            }
        }
    }
}

/**
 * Le diamètre de l'anneau, autour d'une tuile de 56 dp.
 *
 * L'écart qui reste — seize points de chaque côté — est ce qui empêche
 * l'anneau de se lire comme la bordure de la tuile. Plus serré, il colle ;
 * plus large, les deux redeviennent deux objets.
 */
private val CHECK_RING = 88.dp

/**
 * L'épaisseur de cet anneau-là, et d'aucun autre.
 *
 * Aucun jeton ne convient : les trois traits d'[io.aule.android.core.designsystem.token.AuleStroke]
 * valent 1 à 1,75 dp parce qu'ils dessinent des contours et des icônes de 24 dp.
 * Un anneau de 88 dp n'est ni l'un ni l'autre. Quatre points — le trait que
 * Material donne à ses propres indicateurs — le laissent mince à cette taille,
 * moins de cinq pour cent du diamètre, tout en le gardant visible sur un
 * pare-brise en plein soleil.
 */
private val CHECK_RING_STROKE = 4.dp
