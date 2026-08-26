package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleConnectedButtonGroup
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.RouteCandidate
import io.aule.android.core.model.RouteMode
import io.aule.android.core.model.RouteProfile
import io.aule.android.core.model.RouteReliability
import io.aule.android.core.model.RouteSegment
import io.aule.android.core.model.durationMinutesFromSeconds
import java.text.DecimalFormatSymbols
import java.time.Duration

/**
 * Le panneau d'itinéraire.
 *
 * Il ne calcule rien : il lit l'état que le ViewModel tient et remonte des
 * intentions — choisir une variante, changer de mode, inverser les extrémités,
 * fermer. Un seul écrivain, et ce n'est pas lui.
 *
 * « Démarrer » n'apparaît que sur un trajet retenu : un bouton offert
 * sans guidage derrière lui mentirait.
 *
 * ## Un choix, donc des boutons radio
 *
 * Les variantes ne sont pas une liste qu'on parcourt, c'est un choix dont une
 * seule réponse survit — et le bouton d'après en dépend. Elles sont donc
 * regroupées (`selectableGroup`) et annoncées comme telles : TalkBack dit
 * « sélectionné, 2 sur 3 » au lieu de laisser deviner ce que la teinte du fond
 * voulait dire.
 *
 * **Sauf quand il n'y en a qu'une.** Le moteur ne renvoie souvent qu'un seul
 * trajet, et « sélectionné, 1 sur 1 » annonce alors un choix qui n'existe pas :
 * on n'y coche rien, on regarde ce qu'on va faire. La rangée unique perd donc
 * son rôle et son geste — elle garde l'aplat, qui ne dit plus « celle-ci parmi
 * les autres » mais « celle que Démarrer engage ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteSheet(
    state: RouteUiState,
    onSelect: (String) -> Unit,
    onMode: (RouteMode) -> Unit,
    onSwap: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetBody(modifier = modifier) {
        SheetTitle(stringResource(R.string.route_title))

        RouteEndpoints(
            origin = state.origin.label,
            destination = state.destination.label,
            onSwap = onSwap,
        )

        // L'ordre est celui du produit, pas celui de l'énumération : le transport
        // en commun d'abord — c'est l'objet d'Aule — puis la marche, qui est la
        // suite naturelle d'un trajet court, puis la voiture.
        val modes = listOf(RouteMode.TRANSIT, RouteMode.WALK, RouteMode.CAR)
        AuleConnectedButtonGroup(
            options = modes,
            selected = state.mode,
            // ⚠️ **Le libellé porte la durée**, sur une seconde ligne.
            //
            // Sans elle, le groupe offrait trois choix sans rien pour les
            // départager : il fallait en toucher un — donc relancer un calcul et
            // perdre le trajet affiché — pour apprendre ce qu'il coûtait. Le
            // segment fait déjà deux lignes (voir `AuleConnectedButtonGroup`), et
            // la seconde était vide.
            //
            // Un mode qui n'a pas encore répondu garde son libellé seul : une
            // ligne réservée pour un chiffre à venir ferait sauter le groupe
            // sous le doigt au moment où il arrive.
            label = { mode ->
                val minutes = state.durations[mode]
                if (minutes == null) {
                    stringResource(mode.labelRes())
                } else {
                    stringResource(
                        R.string.route_mode_with_duration,
                        stringResource(mode.labelRes()),
                        minutes,
                    )
                }
            },
            onSelect = onMode,
            modifier = Modifier.fillMaxWidth(),
        )

        when (state.status) {
            RouteLoadStatus.LOADING -> AuleLoadingState(
                label = stringResource(R.string.route_loading),
            )
            // Le message de l'exception reste au journal : « timeout » et
            // « Unable to resolve host » sont des traces, pas des phrases.
            RouteLoadStatus.ERROR -> AuleEmptyState(
                title = stringResource(R.string.route_error_title),
                detail = stringResource(R.string.route_error_detail),
            )
            RouteLoadStatus.READY -> {
                val plan = state.plan
                if (plan == null || plan.alternatives.isEmpty()) {
                    AuleEmptyState(
                        title = stringResource(R.string.route_empty_title),
                        detail = stringResource(R.string.route_empty_detail),
                    )
                } else {
                    val choice = plan.alternatives.size > 1
                    SheetCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (choice) Modifier.selectableGroup() else Modifier),
                    ) {
                        plan.alternatives.forEachIndexed { index, candidate ->
                            RouteCandidateRow(
                                candidate = candidate,
                                selected = candidate.id == state.selectedId,
                                choice = choice,
                                rank = index,
                                onClick = { onSelect(candidate.id) },
                            )
                            if (index < plan.alternatives.lastIndex) {
                                SheetRowDivider()
                            }
                        }
                    }
                    if (state.selected != null) {
                        RouteStartButton(onStart = onStart)
                    }
                }
            }
        }
    }
}

/**
 * Les deux extrémités, et le geste qui les retourne.
 *
 * Inverser un trajet est le deuxième calcul le plus demandé après le premier :
 * on rentre par où l'on est venu. Sans ce bouton il fallait refermer le volet,
 * rouvrir la recherche et resaisir une destination qu'on avait déjà sous les
 * yeux — six gestes pour dire « dans l'autre sens ».
 *
 * Il se pose à droite des **deux** lignes plutôt que sur l'une d'elles : ce
 * n'est l'action ni du départ ni de l'arrivée, c'est celle de la paire. Le
 * filet s'arrête donc au bord de la colonne de texte — un trait qui passerait
 * sous le bouton le rattacherait à la ligne du dessous.
 */
@Composable
private fun RouteEndpoints(origin: String, destination: String, onSwap: () -> Unit) {
    SheetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                RouteEndpoint(label = stringResource(R.string.route_from), value = origin)
                SheetRowDivider()
                RouteEndpoint(label = stringResource(R.string.route_to), value = destination)
            }
            IconButton(
                onClick = onSwap,
                modifier = Modifier
                    .padding(end = AuleSpacing.sm)
                    .defaultMinSize(minWidth = AuleTouch.minimum, minHeight = AuleTouch.minimum),
            ) {
                Icon(
                    imageVector = AuleGlyph.SWAP.asImageVector(),
                    contentDescription = stringResource(R.string.route_swap),
                )
            }
        }
    }
}

/**
 * Une extrémité du trajet.
 *
 * L'intitulé passe en surtitre plutôt qu'en sous-titre : « Départ » qualifie le
 * lieu qui suit, et le lire après lui oblige à revenir en arrière.
 */
@Composable
private fun RouteEndpoint(label: String, value: String) {
    ListItem(
        overlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(
                text = value,
                // Un nom de lieu n'est pas du texte courant : c'est une réponse
                // à « d'où » et « vers où », et ces deux réponses sont ce qu'on
                // relit avant de valider un trajet. Le corps de texte les
                // rendait aussi discrètes que leur propre intitulé.
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        // Transparent : la couleur vient du cartouche qui la porte.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * Le seul engagement du volet, et il ouvre le guidage.
 *
 * Il prend la hauteur des actions principales de la maison — [AuleControl.height] —
 * au lieu de celle que Material donne par défaut, qui passe sous le plancher
 * tactile tenu partout ailleurs. Il porte l'icône de lecture pour la même
 * raison que « Y aller » porte celle d'itinéraire : un bouton pleine largeur
 * qui n'a qu'un mot se confond avec une bannière.
 *
 * TalkBack, lui, entend « Démarrer **le guidage** » : « Démarrer » seul, lu
 * hors de la rangée qui le précède, ne dit pas ce qui démarre.
 */
@Composable
private fun RouteStartButton(onStart: () -> Unit) {
    val spoken = stringResource(R.string.route_start_a11y)
    Button(
        onClick = onStart,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleControl.height)
            .semantics { contentDescription = spoken },
        colors = auleAccentButtonColors(),
        // Les crans d'un bouton à icône sont ceux de Material : ni la taille de
        // l'icône ni son écart au texte ne se décident ici.
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(
            imageVector = AuleGlyph.PLAY.asImageVector(),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.route_start))
    }
}

/**
 * Une variante de trajet : quand elle part, combien elle dure, quand elle
 * arrive, ce qu'elle emprunte, ce qu'elle vaut.
 *
 * La durée est l'information qu'on compare — elle porte donc le rôle `DATA`,
 * aux chiffres à chasse fixe, pour que trois variantes empilées alignent leurs
 * minutes au lieu de les décaler.
 *
 * ## L'heure d'arrivée, qui manquait
 *
 * La rangée disait « 9 min » et « Départ 17:24 », et laissait faire l'addition.
 * Or ce qu'on cherche en calculant un itinéraire n'est presque jamais sa durée :
 * c'est l'heure à laquelle on sera là-bas, parce que c'est elle qu'on a promise
 * à quelqu'un. Le modèle la portait déjà (`arrivalAt`), le volet ne l'affichait
 * pas. Elle prend la droite de la ligne de titre, en encre secondaire — le
 * premier regard reste sur la durée, le deuxième trouve l'heure sans quitter
 * la ligne.
 *
 * Les deux heures se lisent en 24 heures via [rememberPassageClock], comme
 * partout ailleurs dans l'application. `FormatStyle.SHORT`, qui était employé
 * ici, suit la locale : le même trajet annonçait « 17:24 » en français et
 * « 5:24 PM » en anglais, quand les poteaux du réseau affichent 17:24 dans les
 * deux cas.
 *
 * ## La chaîne, puis les faits
 *
 * Ce qu'on emprunte se lit dans l'ordre où on l'emprunte : marche, ligne,
 * marche. Les badges seuls disaient « 80 » sans dire qu'il fallait d'abord
 * marcher — et une variante qui commence par dix minutes de marche n'est pas la
 * même que celle qui commence à l'arrêt d'en face.
 *
 * Sous la chaîne, ce que le serveur sait de la variante et que la rangée
 * jetait : le temps de marche, le nombre de changements, la tenue de la
 * correspondance. Ce sont les trois critères qui départagent deux trajets de
 * durée voisine.
 *
 * La distance, elle, ne s'affiche plus **que** sur un trajet qu'on fait par ses
 * propres moyens — la voiture, ou la marche seule. Sur un trajet en transports,
 * les kilomètres parcourus assis ne se décident pas, et ce qu'on marche est
 * déjà dit en minutes.
 */
@Composable
private fun RouteCandidateRow(
    candidate: RouteCandidate,
    selected: Boolean,
    choice: Boolean,
    rank: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Sur l'aplat de marque, aucun rôle « variant » du thème ne tient : ils sont
    // tous calculés pour une surface claire. L'encre de l'accent, voilée, rend
    // le même rapport de lecture sur l'autre fond.
    val secondaryInk = if (selected) {
        colors.onPrimary.copy(alpha = AuleAlpha.VEIL)
    } else {
        colors.onSurfaceVariant
    }
    val clock = rememberPassageClock()
    val duration = stringResource(R.string.route_duration, candidate.durationMinutes)
    val departure = candidate.departureAt?.let {
        stringResource(R.string.route_departs, clock.format(it))
    }
    val arrival = candidate.arrivalAt?.let {
        stringResource(R.string.route_arrives, clock.format(it))
    }
    val chain = candidate.chain()
    val facts = candidate.facts(chain).joinToString(FACT_SEPARATOR)
    val lines = chain.filterIsInstance<RouteChainItem.Line>()
        .map { stringResource(R.string.line_badge, it.id) }
    val spoken = listOfNotNull(
        duration,
        arrival,
        departure,
        facts.takeIf { it.isNotEmpty() },
        lines.takeIf { it.isNotEmpty() }?.joinToString(FACT_SEPARATOR),
    ).joinToString(", ")

    AuleCappedFontScale {
        ListItem(
            overlineContent = departure?.let {
                {
                    Text(text = it, style = MaterialTheme.typography.labelSmallEmphasized)
                }
            },
            headlineContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = duration,
                        // La durée est ce qu'on compare entre trois variantes :
                        // celle qu'on a retenue doit se distinguer des deux
                        // autres autrement que par son fond.
                        style = if (selected) {
                            MaterialTheme.typography.titleLargeEmphasized
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                    )
                    if (arrival != null) {
                        Text(
                            text = arrival,
                            style = MaterialTheme.typography.titleMedium,
                            color = secondaryInk,
                            maxLines = 1,
                        )
                    }
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
                    if (chain.isNotEmpty()) {
                        RouteChain(chain = chain, ink = secondaryInk)
                    } else {
                        candidate.steps.firstOrNull()?.let { step ->
                            Text(text = step.label, maxLines = 2)
                        }
                    }
                    if (facts.isNotEmpty()) {
                        Text(text = facts, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            modifier = Modifier
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .auleEnter(index = rank)
                // `selectable` et non `clickable` : c'est un choix parmi
                // plusieurs, et le rôle est ce qui le fait annoncer comme tel.
                // Seule d'une liste d'une, la rangée n'est plus un choix : elle
                // cesse d'offrir un geste qui ne change rien.
                .then(
                    if (choice) {
                        Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            colors = ListItemDefaults.colors(
                // L'aplat de marque plein, et non le conteneur pastel. Trois
                // variantes empilées dont une surlignée en pastel clair, c'est
                // une ligne de tableur mise en évidence ; la même en teal profond,
                // c'est un choix arrêté. La différence est celle entre « voici
                // celle qui est cochée » et « voici celle que vous prenez ».
                containerColor = if (selected) colors.primary else Color.Transparent,
                headlineColor = if (selected) colors.onPrimary else colors.onSurface,
                overlineColor = secondaryInk,
                supportingColor = secondaryInk,
            ),
        )
    }
}

/**
 * Ce qu'on emprunte, dans l'ordre.
 *
 * La rangée coule (`FlowRow`) : un trajet à deux changements aligne cinq
 * éléments, et à 130 % de taille de police ils ne tiennent plus sur une ligne.
 * Ils passent alors à la suivante au lieu de sortir du cartouche.
 *
 * Rien ne sépare les maillons — ni chevron, ni flèche. La famille d'icônes
 * d'Aule n'en a pas, et un caractère typographique en guise de signe est
 * précisément ce que le kit interdit. L'ordre de lecture suffit à dire l'ordre
 * du trajet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteChain(chain: List<RouteChainItem>, ink: Color) {
    val walkLabel = stringResource(R.string.route_leg_walk)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        chain.forEach { item ->
            when (item) {
                RouteChainItem.Walk -> Icon(
                    imageVector = Icons.AutoMirrored.Outlined.DirectionsWalk,
                    contentDescription = walkLabel,
                    tint = ink,
                )
                is RouteChainItem.Line -> LineBadge(
                    line = item.id,
                    colorHex = item.color,
                    contentDescription = stringResource(R.string.line_badge, item.id),
                )
            }
        }
    }
}

/** Un maillon du trajet : une ligne qu'on prend, ou la marche qui y mène. */
private sealed interface RouteChainItem {
    data object Walk : RouteChainItem
    data class Line(val id: String, val color: String?) : RouteChainItem
}

/**
 * La suite des maillons, tronçons du serveur repliés sur ce qui se voit.
 *
 * Deux marches qui se suivent sont une marche : le serveur coupe parfois une
 * traversée en deux au carrefour, et deux pictogrammes collés feraient croire à
 * deux étapes.
 *
 * Vide si aucune ligne n'y figure — c'est la voiture, ou la marche seule. Un
 * unique pictogramme de piéton n'apprendrait rien qu'un trajet à pied ne dise
 * déjà, et sur un trajet en voiture il serait faux.
 */
private fun RouteCandidate.chain(): List<RouteChainItem> {
    val items = mutableListOf<RouteChainItem>()
    segments.forEach { segment ->
        val item = segment.chainItem()
        if (item == items.lastOrNull()) return@forEach
        items += item
    }
    return if (items.any { it is RouteChainItem.Line }) items else emptyList()
}

private fun RouteSegment.chainItem(): RouteChainItem {
    val line = routeId?.takeIf { it.isNotBlank() }
    return if (walk || line == null) RouteChainItem.Walk else RouteChainItem.Line(line, color)
}

/**
 * Ce qui départage deux variantes de durée voisine, dans l'ordre où on le lit.
 *
 * Le profil d'abord : c'est le verdict du moteur, et il tient en trois mots. Le
 * reste ensuite, du plus décidant au moins — ce qu'on marche, ce qu'on change,
 * la tenue de la correspondance.
 */
@Composable
private fun RouteCandidate.facts(chain: List<RouteChainItem>): List<String> = buildList {
    profiles.firstOrNull()?.let { add(it.label()) }
    walk?.walkMinutes()?.let { add(stringResource(R.string.route_walk, it)) }
    transfers?.let { add(transfersLabel(it)) }
    reliability?.let { add(it.label()) }
    if (chain.isEmpty()) {
        add(
            GeoMath.formatDistance(
                distanceMeters.toDouble(),
                DecimalFormatSymbols.getInstance().decimalSeparator,
            ),
        )
    }
}

/**
 * Les minutes de marche, ou rien du tout.
 *
 * Une variante sans marche — on est déjà à l'arrêt — n'a pas à dire « 0 min à
 * pied » ; et quarante secondes de marche font une minute, pas zéro, parce
 * qu'on ne traverse pas une rue en moins d'une.
 */
private fun Duration.walkMinutes(): Int? =
    if (isZero || isNegative) null else durationMinutesFromSeconds(seconds.toDouble())

@Composable
private fun transfersLabel(count: Int): String = when {
    count <= 0 -> stringResource(R.string.route_transfers_none)
    count == 1 -> stringResource(R.string.route_transfers_one)
    else -> stringResource(R.string.route_transfers_many, count)
}

@Composable
internal fun RouteProfile.label(): String = when (this) {
    RouteProfile.FASTEST -> stringResource(R.string.route_profile_fastest)
    RouteProfile.LEAST_WALK -> stringResource(R.string.route_profile_least_walk)
    RouteProfile.LEAST_TRANSFERS -> stringResource(R.string.route_profile_least_transfers)
    RouteProfile.MOST_RELIABLE -> stringResource(R.string.route_profile_most_reliable)
}

@Composable
internal fun RouteReliability.label(): String = when (this) {
    RouteReliability.COMFORTABLE -> stringResource(R.string.route_reliability_comfortable)
    RouteReliability.TIGHT -> stringResource(R.string.route_reliability_tight)
    RouteReliability.RISKY -> stringResource(R.string.route_reliability_risky)
}

/** Ce qui sépare deux faits d'une même ligne de sous-titre. */
private const val FACT_SEPARATOR = " · "
