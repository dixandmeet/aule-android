package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.R
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * La marque Aule : le A et sa vague, sous une onde qui s'écarte.
 *
 * C'est **l'image du web** — `../dashboard/public/aule-logo.png`, fond
 * translucide retiré — et non un tracé. Il y avait ici un chevron de trois
 * traits, dessiné à la main parce qu'un dessin de trois lignes tient à toutes
 * les tailles sans fichier ; c'était vrai, et c'était un deuxième dessin de la
 * même marque. Le troisième était l'icône de lancement. Trois A pour un
 * produit, cela se voit à l'écran d'accueil. iOS a tranché avant nous, et
 * embarque ce même fichier (`../Native/Aule/DesignSystem/Components/AuleLogo.swift`).
 *
 * Il n'y a **pas d'aplat** sous la marque, et il y a **deux fichiers**. Le
 * dessin est un dégradé teal sur transparent : il se lit sur un fond clair, et
 * sur un fond sombre on pose sa contrepartie blanche — la « version
 * monochrome » de la planche de marque. C'est la règle du web au mot près
 * (`dashboard/components/carte-immersive/next/hud/map-header.tsx`) et celle
 * d'iOS (`../Native/Aule/DesignSystem/Components/AuleLogo.swift`).
 *
 * ⚠️ Cette règle **s'est inversée** avec le logo de septembre 2026, et c'est
 * pour cela qu'elle est écrite ici en toutes lettres. L'ancien dessin était
 * clair : il ne se lisait que sur du sombre, et il lui fallait une pastille
 * teal le jour. Le nouveau porte ses couleurs, donc il ne veut plus de
 * pastille — il veut sa contrepartie. Poser encore l'aplat mettrait du teal
 * sur du teal, et la marque disparaîtrait dans son propre fond.
 *
 * Le fichier porte sa propre réserve : le dessin n'occupe que 63 % du carré, le
 * reste est la marge qui l'écarte du bord de la tuile. Une marque de soixante-
 * seize points se lit donc comme une lettre de quarante-huit — c'est la
 * proportion du web et celle de l'icône.
 *
 * L'onde est une boucle infinie, donc elle s'arrête quand l'appareil demande
 * moins de mouvement — la marque reste, la respiration part.
 */
@Composable
fun AuleBrandMark(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = reduceMotionEnabled()
    val pulse = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "brand-mark")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(AuleMotion.PULSE_MS, easing = LinearEasing)),
            label = "brand-mark-pulse",
        )
        animated
    }
    Box(
        modifier = modifier
            .size(HALO_SIZE)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(WAVE_SIZE)
                .graphicsLayer {
                    val scale = 0.78f + pulse * 0.5f
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - pulse) * 0.28f
                }
                .border(AuleStroke.hairline, AuleBrand.teal.color, CircleShape),
        )
        Box(modifier = Modifier.size(TILE_SIZE)) {
            Mark()
        }
    }
}

/**
 * La marque en ligne : la tuile, le nom, et ce que le nom ne dit pas.
 *
 * C'est le `Logo` du web (`SpacePro/components/brand/logo.tsx`), et c'est la
 * forme que prend la marque quand elle est **en tête** d'un écran plutôt qu'au
 * centre. La différence n'est pas une question de place : au centre, la marque
 * est le sujet — elle a son halo, son onde, ses soixante-seize points ; en
 * tête, elle est une signature, et une signature qui pèse plus que le titre
 * qu'elle surplombe est une signature ratée.
 *
 * Le sur-titre en capitales espacées vient du web lui aussi. Il dit ce que le
 * nom seul ne dit pas — de quel espace il s'agit — et c'est exactement ce qu'on
 * veut sur un écran de connexion : « Aule Pro » identifie la maison, « espace
 * de travail » dit qu'on n'est pas dans l'application des voyageurs.
 */
@Composable
fun AuleWordmark(
    name: String,
    kicker: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        },
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(MARK_SIZE)) {
            Mark()
        }
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
            Text(
                text = kicker.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                letterSpacing = KICKER_TRACKING,
            )
        }
    }
}

/**
 * Le dessin lui-même, décoratif : la phrase est portée par le conteneur, qui
 * dit « logo Aule Pro » une fois pour toute la marque.
 *
 * Deux fichiers pour un seul dessin : le blanc **est** la silhouette du teal,
 * remplie d'un aplat, et non un second tracé. Voir la règle en tête de
 * [AuleBrandMark].
 */
@Composable
private fun Mark() {
    Image(
        painter = painterResource(
            if (AuleTheme.night) R.drawable.aule_logo_blanc else R.drawable.aule_logo,
        ),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
    )
}

/** La tuile de la marque en ligne. */
private val MARK_SIZE = 34.dp

/**
 * L'espacement des capitales du sur-titre.
 *
 * Le web le pose à `tracking-widest`, soit un dixième de cadratin. Sur dix
 * points, c'est ce qui distingue un mot en capitales d'un mot crié.
 */
private val KICKER_TRACKING = 1.2.sp

private val HALO_SIZE = 128.dp
private val WAVE_SIZE = 88.dp
private val TILE_SIZE = 76.dp
