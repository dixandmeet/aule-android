package io.aule.android.feature.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.delayInk
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.model.RunStopState
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.VehicleRun
import io.aule.android.core.model.VehicleRunStop
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Le plan de ligne du véhicule suivi : ce qu'il a desservi, ce qu'il dessert
 * encore, et à quelle heure.
 *
 * ## Pourquoi il n'apparaît qu'au suivi
 *
 * La fiche d'un véhicule répond en trois secondes à « c'est quelle ligne, il
 * va où ». Le plan répond à une autre question — « et après ? » — qu'on ne se
 * pose qu'une fois décidé à rester avec ce véhicule. Le mettre dans la fiche
 * d'un bus qu'on effleure allongerait de vingt rangées un volet qu'on ouvre
 * pour en lire trois.
 *
 * ## La coupure est structurelle, pas seulement colorée
 *
 * Ce qui est derrière le véhicule est **replié** : une rangée annonce le
 * compte, et le déplie qui veut. Un plan qui déroulerait quinze arrêts
 * desservis avant d'arriver au prochain ferait ouvrir le volet sur du passé,
 * exactement ce qu'on n'a pas besoin de savoir. Les deux derniers restent
 * visibles quand même : ils donnent le sens de marche, qu'aucune couleur ne
 * dit aussi bien qu'un nom de rue qu'on vient de dépasser.
 *
 * Le reste de la distinction se joue sur trois canaux à la fois — la taille du
 * texte, son encre, et la silhouette de la pastille sur le rail. Pas seulement
 * la couleur : cet écran se lit en plein soleil, et un conducteur daltonien le
 * lit aussi.
 *
 * ## Les heures ne promettent que ce qu'elles savent
 *
 * Sur une position mesurée posée sur le tracé, l'heure affichée est celle de
 * l'horaire **décalée du retard constaté** — c'est celle à laquelle le véhicule
 * passera vraiment, et le bandeau d'en-tête dit de combien il dérive. Sur une
 * position calculée, on affiche l'horaire, et rien n'annonce du temps réel.
 * C'est la même règle que partout ailleurs dans l'application : un théorique ne
 * se déguise jamais en mesuré.
 */
@Composable
internal fun VehicleLinePlan(
    run: VehicleRun,
    mode: TransportMode,
    modifier: Modifier = Modifier,
) {
    if (run.stops.isEmpty()) return
    val clock = rememberPassageClock()
    val tint = mode.markerColor(AuleTheme.night).color

    // Les arrêts desservis se replient, sauf les derniers. Le repli est mémorisé
    // par le volet : le déplier puis regarder la carte ne doit pas le refermer.
    var expanded by rememberSaveable(run.stops.size) { mutableStateOf(false) }
    val servedCount = run.nextIndex.coerceIn(0, run.stops.size)
    val hiddenCount = (servedCount - SERVED_KEPT).coerceAtLeast(0)
    // Un repli qui cache une rangée coûte plus qu'il ne fait gagner : sa propre
    // rangée, et un geste pour la défaire.
    val folds = hiddenCount > 1

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        PlanHeader(run = run)
        SheetCard(modifier = Modifier.fillMaxWidth()) {
            if (folds) {
                ServedToggle(
                    count = hiddenCount,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                )
            }
            run.stops.forEachIndexed { index, stop ->
                val hiddenByFold = folds && !expanded && index < hiddenCount
                AnimatedVisibility(
                    visible = !hiddenByFold,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    PlanStopRow(
                        stop = stop,
                        clock = clock,
                        tint = tint,
                        isLive = run.isLive,
                        isFirst = index == 0,
                        isLast = index == run.stops.lastIndex,
                    )
                }
            }
        }
    }
}

/**
 * L'intitulé du plan, et la dérive de la course quand elle est mesurée.
 *
 * Le retard voyage ici plutôt que sur chaque rangée : il est le même pour tous
 * les arrêts à venir — c'est ce qui le rend reportable — et l'écrire vingt fois
 * ferait vingt fois lire la même minute.
 */
@Composable
private fun PlanHeader(run: VehicleRun) {
    val delay = run.delay
    val drift = when {
        delay == null -> null
        abs(delay.seconds) < ON_TIME_SECONDS -> stringResource(R.string.vehicle_plan_on_time)
        delay.isNegative -> stringResource(
            R.string.vehicle_plan_early,
            (abs(delay.seconds) / 60L).toInt().coerceAtLeast(1),
        )
        else -> stringResource(
            R.string.vehicle_plan_late,
            (delay.seconds / 60L).toInt().coerceAtLeast(1),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetSectionLabel(stringResource(R.string.vehicle_plan_title))
        if (drift != null) {
            Text(
                text = drift,
                style = MaterialTheme.typography.labelMediumEmphasized,
                // Les deux encres du domaine, et pas une nuance de gris : le
                // vert dit « tenu », l'ambre dit « ça dérive ». Ce sont celles
                // qui portent déjà le retard d'un passage à l'arrêt.
                color = if (abs(delay?.seconds ?: 0L) >= ON_TIME_SECONDS) {
                    delayInk()
                } else {
                    realtimeInk()
                },
            )
        }
    }
}

/**
 * La rangée qui replie le passé : « 6 arrêts desservis ».
 *
 * Un bouton, et annoncé comme tel : ce qu'il fait ne s'apprend pas en le
 * regardant. Le chevron pivote plutôt que de changer d'icône — une flèche qui
 * tourne dit qu'on rouvrira ce qu'on vient de fermer.
 */
@Composable
private fun ServedToggle(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = if (expanded) {
        stringResource(R.string.vehicle_plan_served_hide)
    } else if (count == 1) {
        stringResource(R.string.vehicle_plan_served_one)
    } else {
        stringResource(R.string.vehicle_plan_served_many, count)
    }

    AuleCappedFontScale {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .clickable(onClick = onToggle)
                .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.sm)
                .semantics(mergeDescendants = true) { role = Role.Button },
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = AuleGlyph.CHEVRON.asImageVector(),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(CHEVRON_SIZE)
                    .rotate(if (expanded) -90f else 90f),
            )
        }
    }
}

/**
 * Un arrêt sur le rail : sa pastille, son nom, son heure.
 *
 * Lue d'un trait par TalkBack — « Platanes, prochain arrêt, 16 h 03 » — parce
 * que trois nœuds séparés feraient trois arrêts de curseur pour une phrase que
 * l'œil, lui, prend d'un seul coup.
 */
@Composable
private fun PlanStopRow(
    stop: VehicleRunStop,
    clock: DateTimeFormatter,
    tint: Color,
    isLive: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val served = stop.state == RunStopState.SERVED
    val next = stop.state == RunStopState.NEXT
    val time = clock.format(stop.at)
    val stateLabel = stringResource(
        when (stop.state) {
            RunStopState.SERVED -> R.string.vehicle_plan_state_served
            RunStopState.NEXT -> R.string.vehicle_plan_state_next
            RunStopState.AHEAD -> R.string.vehicle_plan_state_ahead
        },
    )

    // L'heure d'un passage se lit en colonne : chiffres à chasse fixe, sinon
    // « 16:11 » et « 16:08 » ne finissent pas à la même abscisse et la colonne
    // ondule d'une rangée à l'autre. C'est la seule chose qu'on ajoute au slot
    // — ni taille, ni graisse — et c'est ce que fait déjà le rôle `DATA` aux
    // deux crans au-dessus.
    val timeStyle = MaterialTheme.typography.let {
        if (next) it.titleMediumEmphasized else it.titleMedium
    }.copy(fontFeatureSettings = TABULAR_FIGURES)

    AuleCappedFontScale {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${stop.name}, $stateLabel, $time"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StopRail(
                state = stop.state,
                tint = tint,
                isLive = isLive,
                isFirst = isFirst,
                isLast = isLast,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(RAIL_WIDTH),
            )
            Text(
                text = stop.name,
                style = if (next) {
                    MaterialTheme.typography.titleMediumEmphasized
                } else if (served) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = if (served) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = AuleSpacing.sm),
            )
            PlanTime(
                text = time,
                style = timeStyle,
                color = when {
                    served -> colors.onSurfaceVariant
                    next && isLive -> realtimeInk()
                    else -> colors.onSurface
                },
            )
        }
    }
}

/** L'heure, calée à droite sur une colonne de largeur constante. */
@Composable
private fun PlanTime(text: String, style: TextStyle, color: Color) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = Modifier.padding(start = AuleSpacing.sm, end = AuleSpacing.lg),
    )
}

/**
 * Le rail et la pastille d'un arrêt.
 *
 * Le trait est **atténué derrière le véhicule et teinté devant** : la limite
 * entre les deux tombe exactement sur la pastille du prochain arrêt, et c'est
 * elle qui dit d'un coup d'œil, sans lire un seul nom, où en est la course.
 *
 * Trois silhouettes, une par état — un point discret pour ce qui est fait, un
 * anneau pour ce qui reste, un disque cerclé pour celui vers lequel on roule.
 * La silhouette porte la même information que l'encre : c'est la redite qui
 * permet de lire le plan à travers une vitre teintée.
 */
@Composable
private fun StopRail(
    state: RunStopState,
    tint: Color,
    isLive: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val past = colors.outlineVariant
    val ahead = tint
    val dot = when (state) {
        RunStopState.SERVED -> past
        RunStopState.NEXT -> if (isLive) realtimeInk() else ahead
        RunStopState.AHEAD -> ahead
    }
    val surface = colors.surfaceContainerHigh

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val stroke = RAIL_STROKE.toPx()
        // Au-dessus de la pastille, le trait décrit ce qui est déjà parcouru —
        // y compris le tronçon en cours, sur lequel le véhicule roule encore.
        val above = if (state == RunStopState.AHEAD) ahead else past
        val below = if (state == RunStopState.SERVED) past else ahead

        if (!isFirst) {
            drawLine(
                color = above,
                start = Offset(centerX, 0f),
                end = Offset(centerX, centerY),
                strokeWidth = stroke,
            )
        }
        if (!isLast) {
            drawLine(
                color = below,
                start = Offset(centerX, centerY),
                end = Offset(centerX, size.height),
                strokeWidth = stroke,
            )
        }

        val center = Offset(centerX, centerY)
        when (state) {
            RunStopState.SERVED -> drawCircle(color = dot, radius = DOT_SERVED.toPx(), center = center)
            RunStopState.AHEAD -> {
                drawCircle(color = surface, radius = DOT_AHEAD.toPx(), center = center)
                drawCircle(
                    color = dot,
                    radius = DOT_AHEAD.toPx(),
                    center = center,
                    style = Stroke(width = stroke),
                )
            }
            RunStopState.NEXT -> {
                drawCircle(
                    color = dot.copy(alpha = AuleAlpha.TINT),
                    radius = DOT_NEXT_HALO.toPx(),
                    center = center,
                )
                drawCircle(color = surface, radius = DOT_NEXT.toPx(), center = center)
                drawCircle(color = dot, radius = DOT_NEXT.toPx() - stroke, center = center)
            }
        }
    }
}

/**
 * Combien d'arrêts desservis restent visibles au-dessus du repli.
 *
 * Deux : celui qu'on vient de quitter, et celui d'avant. Un seul ne donne pas
 * le sens de marche — il pourrait être devant — et trois commencent à occuper
 * le cran partiel du volet, qui est celui qu'on regarde sans lâcher le volant.
 */
private const val SERVED_KEPT = 2

/** En deçà, on ne parle pas de retard : c'est la précision de l'horaire lui-même. */
private const val ON_TIME_SECONDS = 60L

private const val TABULAR_FIGURES = "tnum"

/** La colonne du rail : de quoi loger le halo du prochain arrêt, sans plus. */
private val RAIL_WIDTH = 40.dp
private val RAIL_STROKE = 2.dp
private val DOT_SERVED = 3.dp
private val DOT_AHEAD = 5.dp
private val DOT_NEXT = 7.dp
private val DOT_NEXT_HALO = 13.dp
private val CHEVRON_SIZE = 20.dp
