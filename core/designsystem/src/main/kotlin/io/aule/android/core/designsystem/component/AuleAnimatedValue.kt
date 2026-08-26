package io.aule.android.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import io.aule.android.core.designsystem.reduceMotionEnabled

/**
 * Une valeur qui change sous les yeux, et qui le montre.
 *
 * « Dans 3 min » devient « Dans 2 min » pendant qu'on regarde le volet. Écrit
 * sans mouvement, le changement est invisible : soit on fixait le chiffre et il
 * a *sauté*, soit on regardait ailleurs et rien n'a jamais dit que la donnée
 * avait bougé. C'est précisément l'information qu'on ouvre ce volet pour
 * suivre, et c'était la seule à ne pas s'annoncer.
 *
 * L'ancienne valeur **monte** en s'effaçant, la nouvelle arrive par le bas :
 * c'est le sens d'un compte à rebours, et il se lit sans qu'on l'explique. Un
 * fondu croisé sur place aurait dit « ça a changé » ; le glissement dit en plus
 * « ça descend ».
 *
 * ## Ce qui ne bouge pas
 *
 * La **boîte**. Les styles qui portent un compte sont à chasse fixe — c'est la
 * raison d'être du rôle `DATA` — donc « 10 min » et « 9 min » ne se disputent
 * pas la largeur d'un caractère, et [SizeTransform] laisse le conteneur
 * tranquille plutôt que de l'animer. Une rangée qui se réajuste à chaque minute
 * ferait bouger l'écran entier pour un chiffre.
 *
 * Le mouvement n'est pas non plus **découpé** : `clip = false` laisse la valeur
 * sortante déborder pendant sa demi-seconde de sortie. Découpée, elle
 * disparaîtrait derrière un bord invisible au milieu de la rangée.
 *
 * ## Quand elle ne joue pas
 *
 * Si l'appareil a demandé moins de mouvement, la valeur est simplement écrite.
 * Pas de fondu plus court : le réglage système dit « pas d'animation ».
 *
 * TalkBack ne voit qu'un texte dans les deux cas — et le plus souvent la rangée
 * entière est déjà fusionnée en une phrase par son parent, ce qui est la bonne
 * granularité pour « prochain arrêt Beaujoire, arrivée dans 1 minute ».
 */
@Composable
fun AuleAnimatedValue(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLargeEmphasized,
    color: Color = Color.Unspecified,
) {
    if (reduceMotionEnabled()) {
        Text(text = value, style = style, color = color, maxLines = 1, modifier = modifier)
        return
    }

    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            val spatial = motion.defaultSpatialSpec<IntOffset>()
            val effects = motion.defaultEffectsSpec<Float>()
            val enter = slideInVertically(spatial) { height -> height } + fadeIn(effects)
            val exit = slideOutVertically(spatial) { height -> -height } + fadeOut(effects)
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "valeur",
    ) { shown ->
        Text(text = shown, style = style, color = color, maxLines = 1)
    }
}
