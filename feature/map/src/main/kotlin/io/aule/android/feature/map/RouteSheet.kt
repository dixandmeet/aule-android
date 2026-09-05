package io.aule.android.feature.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsTransit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleConnectedButtonGroup
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.component.delayInk
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleChrome
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
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
 * ## Ce que ce volet a rendu à la carte
 *
 * Il occupait **82 % de l'écran** une fois déployé, mesuré sur le S21 : un
 * titre, deux rangées de liste pour les extrémités, un sélecteur sur deux
 * lignes, deux rangées de trajet dont une en aplat de marque plein, un bouton.
 * Il restait à la ville une bande de la hauteur d'un doigt — c'est-à-dire que
 * l'écran ne montrait plus *où* passe le trajet qu'il décrivait.
 *
 * Quatre décisions lui ont rendu le tiers de sa hauteur, et aucune n'a retiré
 * d'information :
 *
 * - le **titre a disparu**. « Itinéraire » nommait ce qu'on venait de demander,
 *   au-dessus de deux lignes qui le disaient déjà mieux — d'où l'on part, où
 *   l'on va. TalkBack, lui, garde le nom du volet : il vient de `paneTitle`
 *   (`MapScreen`), pas d'un texte à l'écran ;
 * - les **extrémités tiennent sur deux lignes** au lieu de deux rangées de
 *   liste, tenues par un rail dessiné qui dit le sens sans l'écrire ;
 * - le **sélecteur de modes tient sur une ligne** : un glyphe, la durée, et
 *   c'est tout — voir [RouteModes] ;
 * - « Démarrer » **ne défile plus** : il a quitté le volet pour se poser au bord
 *   de l'écran — voir [RouteStartBar]. Le volet lui réserve sa hauteur en pied
 *   ([RouteActionBarHeight]), pour que la dernière variante puisse toujours être
 *   amenée au-dessus de lui.
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
 * on n'y coche rien, on regarde ce qu'on va faire. La carte unique perd donc
 * son rôle et son geste — elle garde le cerne, qui ne dit plus « celle-ci parmi
 * les autres » mais « celle que Démarrer engage ».
 */
@Composable
internal fun RouteSheet(
    state: RouteUiState,
    onSelect: (String) -> Unit,
    onMode: (RouteMode) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val engaged = state.engaged()

    // La marge de pied ordinaire ne sert qu'en l'absence de barre : quand il y
    // en a une, c'est sa propre hauteur que le volet réserve, plus bas.
    SheetBody(modifier = modifier, footer = engaged == null) {
        RouteEndpoints(
            origin = state.origin.label,
            destination = state.destination.label,
            onSwap = onSwap,
        )

        RouteModes(mode = state.mode, durations = state.durations, onMode = onMode)

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
                    AuleCappedFontScale {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (choice) Modifier.selectableGroup() else Modifier),
                        ) {
                            plan.alternatives.forEachIndexed { index, candidate ->
                                RouteOptionCard(
                                    candidate = candidate,
                                    selected = candidate.id == engaged?.id,
                                    choice = choice,
                                    rank = index,
                                    onClick = { onSelect(candidate.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // La barre flotte **au-dessus** du volet : sans cette réserve, la
        // dernière variante ne pourrait jamais être amenée en clair, quel que
        // soit le défilement. Elle ne compte pas la barre système — le volet
        // porte déjà celle-ci, et la barre d'action aussi.
        if (engaged != null) Spacer(Modifier.height(RouteActionBarHeight))
    }
}

/**
 * Le trajet que « Démarrer » engage, et lui seul.
 *
 * `selectedId` peut être nul le temps d'un aller-retour du modèle ; le plan, lui,
 * retombe toujours sur sa première variante. Prendre l'un pour l'autre donnait
 * une barre d'action armée au-dessus d'une liste où rien n'était cerné.
 *
 * Le volet et la barre lisent **la même** fonction : elle vit ici, et non dans
 * chacun d'eux, parce que c'est la définition de « ce qu'on va prendre » — deux
 * copies auraient fini par désigner deux trajets différents.
 */
internal fun RouteUiState.engaged(): RouteCandidate? =
    selected?.takeIf { status == RouteLoadStatus.READY }

/**
 * Les deux extrémités, le rail qui les relie, et le geste qui les retourne.
 *
 * ## Deux lignes, et non deux rangées de liste
 *
 * C'étaient deux `ListItem` empilés, chacun avec son surtitre — « Départ »,
 * « Arrivée » — au-dessus de son lieu. Cent-cinquante points de haut pour deux
 * noms de rue, en tête d'un volet qui n'en avait pas à donner.
 *
 * Ce que les surtitres disaient, le **rail** le dit sans texte : un anneau en
 * haut, un carré plein en bas, un pointillé entre les deux. C'est la grammaire
 * commune à toutes les applications de mobilité, et c'est la seule chose de
 * l'écran qu'on n'ait pas besoin d'apprendre. Les mots restent pour TalkBack,
 * qui lit le bloc d'une traite — l'annoncer en deux rangées séparées faisait
 * deux arrêts au balayage pour une seule question, « d'où vers où ».
 *
 * Le bloc porte aussi le rôle d'en-tête : le volet n'a plus de titre au-dessus
 * de lui, et une navigation par en-têtes qui ne trouve rien tombe directement
 * dans la liste des trajets.
 *
 * ## Le bouton d'inversion
 *
 * Inverser un trajet est le deuxième calcul le plus demandé après le premier :
 * on rentre par où l'on est venu. Sans ce bouton il fallait refermer le volet,
 * rouvrir la recherche et resaisir une destination qu'on avait déjà sous les
 * yeux — six gestes pour dire « dans l'autre sens ».
 *
 * Il se pose à droite des **deux** lignes plutôt que sur l'une d'elles : ce
 * n'est l'action ni du départ ni de l'arrivée, c'est celle de la paire.
 */
@Composable
private fun RouteEndpoints(origin: String, destination: String, onSwap: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spoken = stringResource(R.string.route_endpoints_a11y, origin, destination)
    SheetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(start = AuleSpacing.lg, end = AuleSpacing.xs)
                .padding(vertical = AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        heading()
                        contentDescription = spoken
                    },
            ) {
                RouteEndpointLine(value = origin) {
                    // L'anneau, et non le disque : on part d'un point qu'on
                    // occupe, on ne le vise pas. C'est la même distinction que
                    // le point temps réel fait entre mesuré et théorique.
                    Canvas(Modifier.size(RAIL_MARK)) {
                        val stroke = AuleStroke.emphasis.toPx()
                        drawCircle(
                            color = colors.onSurfaceVariant,
                            radius = size.minDimension / 2f - stroke / 2f,
                            style = Stroke(width = stroke),
                        )
                    }
                }
                RouteRail(color = colors.outlineVariant)
                RouteEndpointLine(value = destination) {
                    // Le carré plein, à l'encre de marque : c'est la seule
                    // chose de la paire qu'on ait choisie, et la seule qui
                    // mérite la couleur du produit.
                    Canvas(Modifier.size(RAIL_MARK)) {
                        val side = size.minDimension
                        drawRoundRect(
                            color = colors.primary,
                            topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                            size = Size(side, side),
                            cornerRadius = CornerRadius(RAIL_MARK_CORNER.toPx()),
                        )
                    }
                }
            }
            IconButton(
                onClick = onSwap,
                modifier = Modifier.defaultMinSize(
                    minWidth = AuleTouch.minimum,
                    minHeight = AuleTouch.minimum,
                ),
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
 * Une extrémité : sa marque sur le rail, puis son nom.
 *
 * Une seule ligne, coupée s'il le faut. Un nom de lieu qui se replierait sur
 * deux ferait glisser la marque du dessous hors de son alignement, et le rail
 * cesserait d'être un rail.
 */
@Composable
private fun RouteEndpointLine(value: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.width(RAIL_WIDTH),
            contentAlignment = Alignment.Center,
            content = { mark() },
        )
        Spacer(Modifier.width(AuleSpacing.md))
        Text(
            text = value,
            // Un nom de lieu n'est pas du texte courant : c'est une réponse à
            // « d'où » et « vers où », et ces deux réponses sont ce qu'on relit
            // avant de valider un trajet.
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Le pointillé entre les deux marques.
 *
 * Dessiné, et non écrit : la famille d'icônes d'Aule n'a pas de trait vertical,
 * et un caractère typographique en guise de signe est ce que le kit interdit.
 * Trois points suffisent à dire « et ensuite » ; un trait plein dirait « et
 * pendant ce temps », ce qui serait faux — entre les deux extrémités il y a un
 * trajet, pas une continuité de lieu.
 */
@Composable
private fun RouteRail(color: Color) {
    Box(modifier = Modifier.width(RAIL_WIDTH), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .width(RAIL_WIDTH)
                .height(RAIL_GAP),
        ) {
            val radius = RAIL_DOT.toPx() / 2f
            val step = RAIL_DOT_STEP.toPx()
            var y = radius
            while (y <= size.height - radius) {
                drawCircle(color = color, radius = radius, center = Offset(size.width / 2f, y))
                y += step
            }
        }
    }
}

/**
 * Le choix du mode : un glyphe, une durée.
 *
 * ## Le libellé a laissé la place au chiffre
 *
 * Le segment portait « Transports » sur une ligne et « 15 min » sur la
 * suivante. Le mot y coûtait vingt points de hauteur, répétés sur toute la
 * largeur de l'écran, pour redire ce que le pictogramme dit plus vite — et
 * personne ne lit « Transports » : on cherche le chiffre à côté du bus.
 *
 * Le glyphe prend donc le nom à sa charge et le groupe passe de deux lignes à
 * une. TalkBack, lui, entend la phrase entière — « Transports, 15 min » — parce
 * qu'un « 15 min » lu seul n'apprend pas de quoi il est la durée.
 *
 * L'ordre est celui du produit, pas celui de l'énumération : le transport en
 * commun d'abord — c'est l'objet d'Aule — puis la marche, qui est la suite
 * naturelle d'un trajet court, puis la voiture.
 *
 * Un mode qui n'a pas encore répondu n'affiche **que** son glyphe. Il ne
 * réserve pas la place du chiffre à venir : le segment ne change que de
 * largeur intérieure quand la durée arrive, jamais de hauteur, et le groupe ne
 * saute pas sous le doigt.
 */
@Composable
private fun RouteModes(
    mode: RouteMode,
    durations: Map<RouteMode, Int?>,
    onMode: (RouteMode) -> Unit,
) {
    val modes = listOf(RouteMode.TRANSIT, RouteMode.WALK, RouteMode.CAR)
    AuleConnectedButtonGroup(
        options = modes,
        selected = mode,
        label = { candidate ->
            durations[candidate]
                ?.let { stringResource(R.string.route_duration, it) }
                .orEmpty()
        },
        onSelect = onMode,
        modifier = Modifier.fillMaxWidth(),
        icon = { candidate ->
            Icon(
                imageVector = candidate.icon(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        },
        spoken = { candidate ->
            val name = stringResource(candidate.labelRes())
            when (val minutes = durations[candidate]) {
                null -> stringResource(R.string.route_mode_unknown_a11y, name)
                else -> stringResource(R.string.route_mode_a11y, name, minutes)
            }
        },
    )
}

/** Le pictogramme d'un mode, à la place du mot qu'il remplace. */
private fun RouteMode.icon(): ImageVector = when (this) {
    RouteMode.TRANSIT -> Icons.Outlined.DirectionsTransit
    RouteMode.WALK -> Icons.AutoMirrored.Outlined.DirectionsWalk
    RouteMode.CAR -> Icons.Outlined.DirectionsCar
}

/**
 * La barre d'action, posée au bord de l'écran et non dans le volet.
 *
 * ## Pourquoi elle a quitté le volet
 *
 * « Démarrer » était le dernier bloc de la colonne qui défile : sur un plan à
 * trois variantes, il passait sous le pli, et la seule action du volet demandait
 * de faire défiler pour la trouver.
 *
 * L'épingler en bas du **volet** ne suffisait pas, et le S21 l'a montré tout de
 * suite : un `BottomSheetScaffold` a deux crans, et le bas du contenu ne tombe
 * au bas de l'écran qu'à l'un des deux. Au palier — 45 % de l'écran, voir
 * `SHEET_PEEK_FRACTION` — le volet est simplement *descendu*, et son contenu
 * dépasse par le bas : le bouton s'y retrouvait coupé en deux par les trois
 * touches de navigation du système. Le calcul n'a pas de bonne issue : pour que
 * la coupe tombe ailleurs que sur la barre, il faudrait que le contenu tienne
 * sous le palier (il fait 449 dp pour un seul trajet, contre 360 de palier) ou
 * qu'il le dépasse d'au moins la hauteur de la barre — et les plans réels
 * tombent entre les deux.
 *
 * Elle est donc **hors du volet**, posée par `MapScreen` au bas de la fenêtre.
 * C'est la position que les cartes de mobilité lui donnent toutes, et la seule
 * qui vaille aux deux crans : au palier comme déployé, le pouce la trouve au
 * même endroit.
 *
 * Elle porte son propre aplat, à la surface du volet : le contenu défile
 * **dessous**, et un bouton posé sur du texte qui glisse se lit comme un défaut
 * d'empilement. Le volet lui réserve [RouteActionBarHeight] en pied, de sorte
 * que la dernière variante puisse toujours être amenée en clair.
 *
 * ## Pourquoi elle porte la durée
 *
 * Le bouton engage **une** variante, et la barre est précisément l'endroit d'où
 * l'on ne voit plus laquelle : au palier, la carte cernée est au-dessus du pli.
 * « Démarrer · 15 min » referme cette distance en trois caractères — c'est le
 * même chiffre que la carte retenue affiche en grand, et le lire ici confirme
 * qu'on part bien avec celle-là.
 *
 * TalkBack entend la phrase entière, heure d'arrivée comprise : c'est la
 * dernière chose annoncée avant un geste qui allume le GPS et ouvre un service
 * de premier plan.
 */
@Composable
internal fun RouteStartBar(
    candidate: RouteCandidate,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val hairline = MaterialTheme.colorScheme.outlineVariant
    val clock = rememberPassageClock()
    val spoken = listOfNotNull(
        stringResource(R.string.route_start_a11y),
        stringResource(R.string.route_duration, candidate.durationMinutes),
        candidate.arrivalAt?.let { stringResource(R.string.route_arrives, clock.format(it)) },
    ).joinToString(", ")

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .drawBehind {
                    // Le filet d'arête : il dit que la barre est **posée sur**
                    // la liste, et non qu'elle en est le dernier élément. Sans
                    // lui, une variante coupée net par un aplat de la même
                    // couleur que le volet se lit comme une variante tronquée.
                    drawRect(
                        color = hairline,
                        size = Size(size.width, AuleStroke.hairline.toPx()),
                    )
                }
                .padding(horizontal = AuleSpacing.lg)
                .padding(top = AuleSpacing.md, bottom = AuleSpacing.lg),
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .auleShadow(
                        level = AuleElevation.RESTING,
                        shape = shape,
                        tint = AuleShadowTint.ACCENT,
                    )
                    // La hauteur des actions principales de la maison, au lieu de
                    // celle que Material donne par défaut — qui passe sous le
                    // plancher tactile tenu partout ailleurs.
                    .defaultMinSize(minHeight = AuleControl.height)
                    .semantics { contentDescription = spoken },
                shape = shape,
                colors = auleAccentButtonColors(),
                // Les crans d'un bouton à icône sont ceux de Material : ni la
                // taille de l'icône ni son écart au texte ne se décident ici.
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    imageVector = AuleGlyph.PLAY.asImageVector(),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(
                        R.string.route_start_with_duration,
                        candidate.durationMinutes,
                    ),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
        }
    }
}

/**
 * Ce que la barre prend au volet, hors barre système.
 *
 * Calculée et non chiffrée : la barre et la réserve que le volet lui laisse
 * sont **la même** mesure, et deux nombres écrits séparément auraient divergé au
 * premier réglage de marge.
 */
internal val RouteActionBarHeight = AuleSpacing.md + AuleControl.height + AuleSpacing.lg

/**
 * Une variante de trajet, sur sa propre carte.
 *
 * ## Une carte par proposition, et non des rangées dans une carte
 *
 * Les variantes vivaient dans **un** cartouche, séparées par un filet. Deux
 * trajets séparés d'un trait d'un point se lisent comme deux lignes d'un même
 * tableau — or ce ne sont pas deux lignes d'un tableau, ce sont deux offres
 * concurrentes entre lesquelles il faut trancher. Huit points de vide entre
 * deux cartes disent ce que le filet ne disait pas : ce sont deux objets, pas
 * deux moitiés d'un objet.
 *
 * ## La retenue se cerne, elle ne se peint plus
 *
 * La variante retenue prenait l'**aplat de marque plein** : un rectangle teal
 * de cent-vingt points de haut, deux fois dans l'écran quand deux plans se
 * suivaient. Le procédé disait bien « celle que vous prenez », et il le disait
 * si fort qu'il ne restait plus de hiérarchie à l'intérieur de la carte — durée,
 * heures et lignes s'écrasaient toutes sur le même fond coloré, à des encres
 * voilées choisies pour survivre au teal plutôt que pour se lire.
 *
 * La retenue se dit maintenant **trois fois, sans aplat** : un cerne à l'encre
 * de marque, la durée passée à cette même encre et appuyée, et la pastille de
 * profil remplie au lieu d'être posée. Trois signaux qui ne coûtent pas un
 * point de surface, et qui laissent le contenu se lire sur le fond de tous les
 * autres cartouches du volet. Le cerne se dessine **dans** la forme : la carte
 * ne change pas de taille en devenant celle qu'on prend, et la liste ne bouge
 * pas sous le doigt.
 *
 * ## Ce que la carte dit, dans cet ordre
 *
 * 1. la **durée**, qui est ce qu'on compare — rôle `DATA`, chiffres à chasse
 *    fixe, pour que trois variantes empilées alignent leurs minutes ;
 * 2. le **profil**, en pastille, quand il y a un choix à départager. Seul dans
 *    la liste, « La plus rapide » ne recommande rien : il rejoint alors les
 *    faits, où il n'est qu'une qualification de plus ;
 * 3. les **deux heures**, départ et arrivée sur une ligne. Ce qu'on cherche en
 *    calculant un itinéraire n'est presque jamais sa durée : c'est l'heure à
 *    laquelle on sera là-bas, parce que c'est elle qu'on a promise à
 *    quelqu'un. Elles se lisent en 24 heures via [rememberPassageClock], comme
 *    les poteaux du réseau ;
 * 4. la **chaîne** de ce qu'on emprunte, dans l'ordre où on l'emprunte ;
 * 5. les **faits** qui départagent deux durées voisines — ce qu'on marche, ce
 *    qu'on change ;
 * 6. l'**état** de la variante, et lui seul est coloré : tenue de la
 *    correspondance, perturbations annoncées. Voir [RouteStatusLine].
 */
@Composable
private fun RouteOptionCard(
    candidate: RouteCandidate,
    selected: Boolean,
    choice: Boolean,
    rank: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val clock = rememberPassageClock()
    val duration = stringResource(R.string.route_duration, candidate.durationMinutes)
    val departure = candidate.departureAt?.let {
        stringResource(R.string.route_departs, clock.format(it))
    }
    val arrival = candidate.arrivalAt?.let {
        stringResource(R.string.route_arrives, clock.format(it))
    }
    val chain = candidate.chain()
    // La pastille ne recommande que s'il y a de quoi choisir. Seule, la
    // variante garde son profil parmi les faits plutôt que de s'auto-décerner
    // un titre.
    val profile = candidate.profiles.firstOrNull()?.takeIf { choice }
    val facts = candidate.facts(chain, keepProfile = profile == null)
        .joinToString(FACT_SEPARATOR)
    val status = candidate.statusLabels()
    val lines = chain.filterIsInstance<RouteChainItem.Line>()
        .map { stringResource(R.string.line_badge, it.id) }
    val spoken = listOfNotNull(
        duration,
        profile?.label(),
        departure,
        arrival,
        lines.takeIf { it.isNotEmpty() }?.joinToString(FACT_SEPARATOR),
        facts.takeIf { it.isNotEmpty() },
        status.takeIf { it.isNotEmpty() }?.joinToString(FACT_SEPARATOR) { it.text },
    ).joinToString(", ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .auleEnter(index = rank),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        border = if (selected) {
            BorderStroke(AuleStroke.emphasis, colors.primary)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                // `selectable` et non `clickable` : c'est un choix parmi
                // plusieurs, et le rôle est ce qui le fait annoncer comme tel.
                // Seule d'une liste d'une, la carte n'est plus un choix : elle
                // cesse d'offrir un geste qui ne change rien.
                //
                // Posé **dans** la surface, il laisse l'ondulation se faire
                // découper par la forme ; posé dessus, elle déborderait des
                // angles.
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
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.sm)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            // Quatre points entre les lignes, et non huit. Ce sont quatre
            // formulations d'un même trajet, pas quatre sections : serrées, elles
            // se lisent comme un bloc, et la carte rend douze points de hauteur —
            // douze points sur lesquels la variante suivante vient dépasser
            // sous la barre d'action, ce qui est la seule chose qui dise qu'elle
            // existe.
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = duration,
                    style = if (selected) {
                        MaterialTheme.typography.titleLargeEmphasized
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    color = if (selected) colors.primary else colors.onSurface,
                )
                if (profile != null) {
                    // `fill = false` : la pastille prend ce qu'il lui faut et
                    // pas davantage, mais jamais plus que ce qui reste. Sans le
                    // poids, « Moins de changements » à 130 % de taille de
                    // police poussait la durée hors de la carte.
                    RouteProfileBadge(
                        label = profile.label(),
                        filled = selected,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }

            val window = listOfNotNull(departure, arrival).joinToString(FACT_SEPARATOR)
            if (window.isNotEmpty()) {
                Text(
                    text = window,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (chain.isNotEmpty()) {
                RouteChain(chain = chain, ink = colors.onSurfaceVariant)
            } else {
                candidate.steps.firstOrNull()?.let { step ->
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (facts.isNotEmpty()) {
                Text(
                    text = facts,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            if (status.isNotEmpty()) {
                RouteStatusLine(status = status)
            }
        }
    }
}

/**
 * La pastille de profil : ce que le moteur reproche aux autres variantes.
 *
 * Remplie sur la variante retenue, posée en lavis sur les autres. C'est le
 * troisième signal de la retenue — après le cerne et l'encre de la durée — et
 * le seul qui se voie du coin de l'œil quand on compare deux cartes empilées.
 *
 * `CircleShape` sur une plaque plus large que haute donne la pilule ; le kit
 * interdit d'écrire une forme dans un écran, et c'est la seule forme du thème
 * qui rende celle-là sans la chiffrer.
 */
@Composable
private fun RouteProfileBadge(label: String, filled: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (filled) colors.primary else colors.secondaryContainer,
        contentColor = if (filled) colors.onPrimary else colors.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmallEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = AuleSpacing.sm,
                vertical = BADGE_VERTICAL_PADDING,
            ),
        )
    }
}

/**
 * Ce qu'on emprunte, dans l'ordre.
 *
 * La rangée coule (`FlowRow`) : un trajet à deux changements aligne cinq
 * maillons et autant de traits, et à 130 % de taille de police ils ne tiennent
 * plus sur une ligne. Ils passent alors à la suivante au lieu de sortir de la
 * carte.
 *
 * ## Le trait entre deux maillons
 *
 * Rien ne les séparait : ni chevron, ni flèche, faute d'en avoir dans la
 * famille d'icônes et parce qu'un caractère typographique en guise de signe est
 * ce que le kit interdit. Restaient des badges posés côte à côte, dont on ne
 * savait pas s'ils formaient une suite ou une liste — « 80 » et « 1 » lus l'un
 * à côté de l'autre peuvent aussi bien être deux lignes possibles que deux
 * lignes successives.
 *
 * Le trait est **dessiné**, ce qui lève l'interdit sans le contourner : c'est
 * une forme du kit, pas un caractère emprunté à la fonte. Court, épais d'un
 * point et demi, à l'encre secondaire — il relie sans se faire lire.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteChain(chain: List<RouteChainItem>, ink: Color) {
    val walkLabel = stringResource(R.string.route_leg_walk)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        chain.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(CHAIN_LINK)
                        .height(CHAIN_LINK_THICKNESS)
                        .background(color = ink, shape = CircleShape),
                )
            }
            when (item) {
                RouteChainItem.Walk -> Icon(
                    imageVector = Icons.AutoMirrored.Outlined.DirectionsWalk,
                    contentDescription = walkLabel,
                    tint = ink,
                    modifier = Modifier.size(AuleChrome.pillGlyph),
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

/**
 * La ligne d'état : la seule chose colorée de la carte.
 *
 * ## Ce qu'elle porte, et ce qu'elle ne peut pas porter
 *
 * `GET /api/route` ne rend **aucun drapeau temps réel** par variante : il n'y a
 * pas de champ à afficher pour dire « ces horaires sont mesurés ». Ce qu'il
 * rend et que ce volet jetait, ce sont les deux seules choses qui datent :
 * la tenue de la correspondance la plus tendue (`reliability`) et le nombre de
 * perturbations relevées sur le trajet (`alerts`). Les inventer autrement
 * aurait donné un point vert qui n'atteste de rien — exactement ce que le point
 * temps réel des passages existe pour ne pas faire.
 *
 * ## Pourquoi elle est colorée alors que le reste ne l'est plus
 *
 * Le volet a perdu ses aplats ; il lui reste une couleur, et elle est ici.
 * C'est ce qui la rend lisible : sur une carte entièrement en gris et en encre
 * de marque, un mot orange se voit sans avoir à crier. Les teintes sont celles
 * du métier — [realtimeInk] pour ce qui tient, [delayInk] pour ce qui est
 * serré — et non des rôles de hiérarchie : « correspondance risquée » n'est pas
 * une erreur d'interface, c'est un fait du réseau.
 *
 * Le point n'est pas décoratif pour autant qu'il soit lu : la carte entière
 * porte déjà sa description, ce qui vaut à ce point de n'être qu'une forme.
 */
@Composable
private fun RouteStatusLine(status: List<RouteStatus>) {
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
        status.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(STATUS_DOT)) {
                    drawCircle(color = entry.ink, radius = size.minDimension / 2f)
                }
                Spacer(Modifier.width(AuleSpacing.sm))
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = entry.ink,
                )
            }
        }
    }
}

/** Un fait daté de la variante, avec l'encre qui dit de quel côté il penche. */
private data class RouteStatus(val text: String, val ink: Color)

/**
 * Ce que le serveur sait de l'état de la variante.
 *
 * La fiabilité d'abord — c'est elle qui décide si on court —, les
 * perturbations ensuite. Une variante sans correspondance tendue et sans alerte
 * ne rend rien : une ligne « tout va bien » sur chaque carte cesserait d'être
 * lue au deuxième trajet.
 */
@Composable
private fun RouteCandidate.statusLabels(): List<RouteStatus> = buildList {
    reliability?.let { add(RouteStatus(it.label(), it.ink())) }
    if (alertCount > 0) {
        val text = if (alertCount == 1) {
            stringResource(R.string.route_alerts_one)
        } else {
            stringResource(R.string.route_alerts_many, alertCount)
        }
        add(RouteStatus(text, MaterialTheme.colorScheme.error))
    }
}

/**
 * L'encre d'une tenue de correspondance.
 *
 * Elle emprunte au vocabulaire des passages plutôt qu'à la hiérarchie du thème :
 * une correspondance confortable est de la même famille qu'un passage mesuré,
 * une correspondance serrée de la même famille qu'un retard. `error` est réservé
 * à la seule qui puisse coûter le trajet.
 */
@Composable
private fun RouteReliability.ink(): Color = when (this) {
    RouteReliability.COMFORTABLE -> realtimeInk()
    RouteReliability.TIGHT -> delayInk()
    RouteReliability.RISKY -> MaterialTheme.colorScheme.error
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
 * Ce qui départage deux variantes de durée voisine, dans l'ordre où on le lit :
 * ce qu'on marche, ce qu'on change.
 *
 * Le profil n'y figure que lorsqu'il n'est pas monté en pastille — seul dans la
 * liste, il ne recommande rien et redescend au rang de qualification.
 *
 * La tenue de la correspondance en est sortie : elle a sa ligne, et sa couleur.
 *
 * La distance ne s'affiche **que** sur un trajet qu'on fait par ses propres
 * moyens — la voiture, ou la marche seule. Sur un trajet en transports, les
 * kilomètres parcourus assis ne se décident pas, et ce qu'on marche est déjà
 * dit en minutes.
 */
@Composable
private fun RouteCandidate.facts(
    chain: List<RouteChainItem>,
    keepProfile: Boolean,
): List<String> = buildList {
    if (keepProfile) profiles.firstOrNull()?.let { add(it.label()) }
    walk?.walkMinutes()?.let { add(stringResource(R.string.route_walk, it)) }
    transfers?.let { add(transfersLabel(it)) }
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

/** La colonne du rail : assez large pour centrer la plus grosse des marques. */
private val RAIL_WIDTH = 16.dp

/** L'anneau du départ, le carré de l'arrivée. Ils font la même taille. */
private val RAIL_MARK = 11.dp

/** L'arrondi du carré d'arrivée : une plaque, pas un pixel. */
private val RAIL_MARK_CORNER = 3.dp

/** Ce que le pointillé sépare : assez pour trois points, pas assez pour un vide. */
private val RAIL_GAP = 12.dp

private val RAIL_DOT = 2.5.dp

/** Le pas du pointillé. Deux fois et demie le point : on lit trois marques. */
private val RAIL_DOT_STEP = 6.dp

/** Le trait entre deux maillons de la chaîne. */
private val CHAIN_LINK = 10.dp

private val CHAIN_LINK_THICKNESS = 1.5.dp

/** Le point d'état. Plus petit que celui des passages : il ne pulse pas. */
private val STATUS_DOT = 8.dp

/** La pastille respire moins que le reste : c'est ce qui la fait lire comme une étiquette. */
private val BADGE_VERTICAL_PADDING = 3.dp
