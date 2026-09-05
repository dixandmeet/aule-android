package io.aule.android.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleTouch

/**
 * Le panneau de limitation, à côté du cadran de vitesse.
 *
 * ## Pourquoi il ignore le thème
 *
 * Un conducteur ne *lit* pas ce panneau, il le **reconnaît** : disque blanc,
 * anneau rouge, chiffre noir. Le teinter à l'ambiance, l'arrondir autrement ou le
 * fondre dans le fond de carte lui retirerait la seule chose qui le rende
 * instantané. La nuit, un disque gris à anneau grenat n'est plus un panneau,
 * c'est une pastille de plus.
 *
 * C'est la seule pièce de l'interface dans ce cas, et c'est la raison.
 *
 * ## Ce qu'il ne fait pas
 *
 * Il ne clignote pas, ne rougit pas, ne compare pas la vitesse à la limite. Un
 * afficheur qui juge se met à parler pendant qu'on conduit ; celui-ci dit ce qui
 * est affiché au bord de la voie, et laisse le conducteur conduire.
 */
@Composable
fun SpeedLimitSign(kmh: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.speed_limit_a11y, kmh)

    Surface(
        modifier = modifier
            .size(kSignDiameter)
            .clearAndSetSemantics { contentDescription = description },
        shape = CircleShape,
        color = Color.White,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.drawBehind {
                // L'anneau est dessiné plutôt que posé en `border` : celui-ci se
                // pose **à l'extérieur** de la forme sur une `Surface` ronde, et
                // l'écrêtait d'un demi-trait sur le S21.
                drawCircle(
                    color = AuleBrand.regulatoryRed.color,
                    radius = size.minDimension / 2 - kSignRingWidth.toPx() / 2,
                    center = Offset(size.width / 2, size.height / 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(kSignRingWidth.toPx()),
                )
            },
        ) {
            Text(
                text = kmh.toString(),
                color = Color.Black,
                fontSize = kSignDigits,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
        }
    }
}

/**
 * Le diamètre du disque : la cible tactile minimale.
 *
 * Le panneau ne se touche pas — mais c'est la seule mesure du design system qui
 * décrive « ce qu'un doigt vise à bout de bras », et un panneau réglementaire se
 * lit dans les mêmes conditions. Plus petit, il cesserait d'être reconnaissable
 * d'un coup d'œil ; plus grand, il dominerait le cadran posé à côté.
 */
private val kSignDiameter = AuleTouch.minimum

/** L'anneau, au treizième du diamètre — la proportion du panneau réglementaire. */
private val kSignRingWidth = 6.dp

/** Le chiffre, en dur : un panneau ne suit pas l'échelle de texte du système. */
private val kSignDigits = 20.sp

