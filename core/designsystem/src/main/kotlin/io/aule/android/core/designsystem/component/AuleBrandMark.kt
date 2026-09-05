package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.R
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleElevation
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
 * L'aplat n'est posé **que de jour**. Le dessin est clair sur transparent : sur
 * un fond sombre il se suffit, sur un fond clair il s'efface presque
 * entièrement. C'est la règle du web au mot près
 * (`dashboard/components/carte-immersive/next/hud/map-header.tsx`), et l'aplat
 * y est le teal de l'**identité**, jamais l'accent d'ambiance — le logo d'Aule
 * ne devient pas menthe à la nuit tombée.
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
    val tile = RoundedCornerShape(TILE_RADIUS)
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
        Box(modifier = Modifier.size(TILE_SIZE).brandGround(tile, lifted = true)) {
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
    val tile = RoundedCornerShape(MARK_RADIUS)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        },
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(MARK_SIZE).brandGround(tile, lifted = false)) {
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
 */
@Composable
private fun Mark() {
    Image(
        painter = painterResource(R.drawable.aule_logo),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * L'aplat sous la marque — et pourquoi il disparaît la nuit.
 *
 * Voir la règle en tête de [AuleBrandMark] : le dessin est clair, donc il lui
 * faut un fond sombre, et de nuit l'écran le lui donne déjà. Poser la tuile
 * quand même mettrait une pastille teal sur du presque noir, et l'ombre qui va
 * avec creuserait un relief là où il n'y a plus de relief à creuser.
 */
@Composable
private fun Modifier.brandGround(shape: Shape, lifted: Boolean): Modifier {
    if (AuleTheme.night) return this
    val raised = if (lifted) auleShadow(AuleElevation.LIFTED, shape, AuleShadowTint.ACCENT) else this
    return raised.clip(shape).background(AuleBrand.teal.color)
}

/** La tuile de la marque en ligne. */
private val MARK_SIZE = 34.dp
private val MARK_RADIUS = 10.dp

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
private val TILE_RADIUS = 21.dp
