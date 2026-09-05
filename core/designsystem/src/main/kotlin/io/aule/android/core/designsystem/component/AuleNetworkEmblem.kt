package io.aule.android.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AulePalette
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * L'emblème d'un réseau de transport : son logo, ou son initiale à défaut.
 *
 * ## Pourquoi il ne prend pas la teinte de la sélection
 *
 * Le jeton d'une carte de choix bascule au primaire quand la rangée est
 * retenue. C'est légitime pour une icône du kit : elle appartient à
 * l'application, elle a le droit de changer de couleur avec elle. Un logo de
 * réseau, non. Il appartient à quelqu'un d'autre, ses couleurs *sont* sa
 * signature, et un logo repeint en teal Aule pour dire « coché » n'est plus son
 * logo. La sélection se lit ailleurs — sur le bord, l'aplat et la coche de la
 * carte —, ce qui fait déjà trois marques pour une information binaire.
 *
 * ## Pourquoi la feuille reste claire la nuit
 *
 * Un logo est dessiné pour du papier : sombre et contrasté sur blanc, donc
 * illisible posé nu sur le presque-noir de l'ambiance nocturne. L'emblème lui
 * apporte sa propre feuille, la même aux deux thèmes. C'est ce que fait toute
 * grille de partenaires, et c'est pour cette raison-là — pas par goût du
 * cartouche.
 *
 * Le filet qui la cerne n'existe **que de jour** : c'est là, et là seulement,
 * que la feuille blanche se confond avec la carte claire qui la porte.
 *
 * @param logo le dessin de la marque, `null` tant qu'on ne l'a pas.
 * @param initial la lettre de repli. L'initiale du réseau, et non un glyphe :
 *   une épingle de carte répond « où » quand la question est « qui ».
 */
@Composable
fun AuleNetworkEmblem(
    logo: Painter?,
    initial: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(AuleControl.avatar),
        shape = RoundedCornerShape(AuleRadius.md),
        color = AulePalette.Neutral.T100.color,
        contentColor = AulePalette.Neutral.ink.color,
        border = if (AuleTheme.night) {
            null
        } else {
            BorderStroke(AuleStroke.hairline, AulePalette.Neutral.T90.color)
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (logo == null) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            } else {
                Image(
                    painter = logo,
                    // Décoratif : le nom du réseau est écrit juste à côté, et
                    // l'entendre deux fois n'apprend rien à personne.
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AuleSpacing.sm),
                )
            }
        }
    }
}
