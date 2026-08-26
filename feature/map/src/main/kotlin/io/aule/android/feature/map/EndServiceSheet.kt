package io.aule.android.feature.map

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.DriverServiceFailureKind

/**
 * Terminer le service — deux gestes, et le second n'est pas une politesse.
 *
 * Solder un service écrit une heure de fin en base. Un doigt posé au
 * mauvais endroit, au volant, n'est pas rattrapable. La confirmation reste.
 *
 * ## Ce que le volet montre de la confirmation
 *
 * Le premier appui ne faisait que changer deux phrases. Sur un volet qu'on
 * regarde une seconde et demie, une phrase qui change **ne se voit pas** : le
 * conducteur croit que rien ne s'est passé, réappuie — et le second appui,
 * lui, solde le service. La demande de confirmation se retournait donc contre
 * ce qu'elle protège.
 *
 * Trois choses bougent maintenant ensemble, et aucune ne déplace quoi que ce
 * soit : le médaillon passe de l'aplat d'alerte tenu à l'alerte pleine, le
 * bouton fait le même chemin, et le texte est annoncé en région vivante pour
 * qui ne regarde pas l'écran. Rien ne change de taille ni de place — un volet
 * qui se réagence sous le doigt est un volet où l'on appuie à côté.
 *
 * ## La distance sous le bouton
 *
 * Même règle qu'à l'écran profil, et la même distance : une cible tactile
 * entière sépare « Terminer » de « Fermer ». Le second appui est irrattrapable
 * et « Fermer » est précisément ce qu'on vise quand on a changé d'avis — les
 * poser à douze points l'un de l'autre revient à mettre la sortie de secours
 * contre le levier.
 *
 * ## Pourquoi la fin de service n'est pas une surface de marque
 *
 * Prise et fin de service encadrent la journée et se répondent, mais elles ne
 * demandent pas la même chose. La prise désigne une action qu'on veut faire :
 * teal, dégradé, lueur d'accent. La fin retient une action qu'on ne refait
 * pas : rouge, et rien de séduisant. Habiller les deux pareil ferait de la
 * clôture une belle chose à toucher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndServiceHost(
    ending: Boolean,
    failure: DriverServiceFailureKind?,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AuleTheme {
        val colors = MaterialTheme.colorScheme
        val motion = MaterialTheme.motionScheme
        // Un ressort d'effets, jamais spatial : ces deux teintes ne changent ni
        // de forme ni de place, et un ressort spatial les ferait scintiller en
        // dépassant sa cible avant de s'y poser.
        val alertFill by animateColorAsState(
            targetValue = if (confirming) colors.error else colors.errorContainer,
            animationSpec = motion.defaultEffectsSpec(),
            label = "aplat d'alerte",
        )
        val alertInk by animateColorAsState(
            targetValue = if (confirming) colors.onError else colors.onErrorContainer,
            animationSpec = motion.defaultEffectsSpec(),
            label = "encre d'alerte",
        )
        ModalBottomSheet(
            onDismissRequest = { if (!ending) onClose() },
            modifier = modifier,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.xl)
                    .padding(bottom = AuleSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
                ) {
                    // Le médaillon n'est pas un ornement : c'est lui qui dit,
                    // sans une phrase de plus, que le volet a changé d'état.
                    // Un titre ne peut pas le faire — il est déjà lu.
                    Surface(
                        modifier = Modifier.size(END_MEDALLION),
                        shape = CircleShape,
                        color = alertFill,
                        contentColor = alertInk,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = AuleGlyph.SIGN_OUT.asImageVector(filled = confirming),
                                contentDescription = null,
                                modifier = Modifier.size(AuleControl.icon),
                            )
                        }
                    }
                    SheetTitle(text = stringResource(R.string.service_end_title))
                }
                Text(
                    text = stringResource(
                        if (confirming) R.string.service_end_confirm else R.string.service_end_detail,
                    ),
                    style = if (confirming) {
                        MaterialTheme.typography.bodyMediumEmphasized
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (confirming) colors.error else colors.onSurfaceVariant,
                    // La bascule doit s'entendre autant qu'elle se voit : sans
                    // région vivante, TalkBack laisse le conducteur devant un
                    // bouton qui a changé de sens sans l'annoncer.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (failure != null) {
                    AuleBanner(
                        message = stringResource(
                            when (failure) {
                                DriverServiceFailureKind.NETWORK -> R.string.service_error_network
                                DriverServiceFailureKind.NOT_SIGNED_IN -> R.string.service_error_session
                                else -> R.string.service_end_error
                            },
                        ),
                        tone = AuleTone.ALERT,
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(AuleTouch.minimum),
                ) {
                    val shape = MaterialTheme.shapes.medium
                    Button(
                        onClick = { if (confirming) onConfirm() else confirming = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            // L'ombre ne vient qu'avec la confirmation, et elle
                            // reste neutre : une lueur de marque sous une
                            // clôture rendrait désirable ce qu'on cherche à
                            // faire hésiter.
                            .auleShadow(
                                level = if (confirming && !ending) {
                                    AuleElevation.RESTING
                                } else {
                                    AuleElevation.NONE
                                },
                                shape = shape,
                                tint = AuleShadowTint.NEUTRAL,
                            )
                            .defaultMinSize(minHeight = AuleControl.height),
                        enabled = !ending,
                        shape = shape,
                        // Le seul motif d'extinction ici est la clôture en
                        // cours : le bouton garde donc ses couleurs, et c'est
                        // la roue qui dit qu'il travaille. Le gris de Material
                        // dirait « indisponible » — le contraire de ce qui se
                        // passe — et y noierait la roue par-dessus le marché.
                        colors = ButtonDefaults.buttonColors(
                            containerColor = alertFill,
                            contentColor = alertInk,
                            disabledContainerColor = alertFill,
                            disabledContentColor = alertInk,
                        ),
                    ) {
                        if (ending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(AuleControl.icon),
                                color = alertInk,
                                strokeWidth = AuleStroke.glyph,
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    if (confirming) {
                                        R.string.service_end_confirm_action
                                    } else {
                                        R.string.service_end_action
                                    },
                                ),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                        }
                    }
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuleTouch.minimum),
                        enabled = !ending,
                    ) {
                        Text(stringResource(R.string.sheet_dismiss))
                    }
                }
                Spacer(Modifier.height(AuleSpacing.sm))
            }
        }
    }
}

/**
 * Le médaillon de fin de service.
 *
 * 44 dp : la plus petite pastille qui laisse au glyphe de 24 points l'air qu'il
 * lui faut pour ne pas toucher le bord, et la plus grande qui tienne sur la
 * ligne du titre sans la faire descendre d'un cran. Ce n'est pas une cible
 * tactile — rien ne s'y appuie — donc le plancher de 48 dp ne s'applique pas.
 */
private val END_MEDALLION = 44.dp
