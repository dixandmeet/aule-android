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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.DepartureWatch
import io.aule.android.core.model.DepartureWatchEngine
import io.aule.android.core.model.StopDeparture
import io.aule.android.core.model.TimetableFailureKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Une ligne, à un arrêt : ce qui arrive, ce qui est prévu, et ce qu'on peut
 * demander à l'application de faire à notre place.
 *
 * ## Pourquoi ce volet existe
 *
 * Le tableau d'un arrêt répond à « qu'est-ce qui passe ici, bientôt » : il
 * regroupe, il coupe à trois attentes, il borne à une heure. C'est la bonne
 * réponse quand on ne sait pas encore quoi prendre. Elle ne répond pas à la
 * question d'après, qui est **« et si je pars dans deux heures ? »**, ni à
 * celle du dimanche suivant — et le seul endroit où ces questions se posent est
 * la ligne qu'on a déjà choisie.
 *
 * ## Deux listes, et jamais une seule
 *
 * En tête, ce qui est **mesuré** : les prochains passages annoncés, avec leur
 * point temps réel. Dessous, ce qui est **prévu** : la fiche horaire de la
 * journée, telle que le catalogue la publie. Les fondre aurait donné une liste
 * d'apparence exacte dont une entrée sur dix aurait menti — rien, vu d'ici, ne
 * dit quel passage théorique correspond à quel passage mesuré. La première
 * liste disparaît d'ailleurs dès qu'on regarde un autre jour : elle n'aurait
 * plus rien à dire.
 *
 * ## Une seule surface de marque, et c'est le groupe de boutons
 *
 * Comme sur la fiche d'un véhicule : ce qu'on peut faire ici tient en deux
 * gestes, le reste se lit. Le prochain passage ne prend donc **pas** le
 * cartouche de marque qu'il porte sur la fiche d'arrêt.
 */
@Composable
internal fun LineDepartureSheet(
    watch: DepartureWatch,
    state: DepartureWatchUiState,
    timetable: TimetableUiState,
    onBack: () -> Unit,
    onToggleWatch: () -> Unit,
    onToggleFocus: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onRetryTimetable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // La même horloge que la fiche d'arrêt : sans elle, « 3 min » resterait
    // « 3 min » pendant que l'heure d'à côté, elle, devient fausse.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(watch.id) {
        while (isActive) {
            delay(LINE_TICK_MS)
            now = Instant.now()
        }
    }

    val times = state.times()
    val today = remember(now) { LocalDate.now() }
    val showingToday = timetable.date == null || timetable.date == today

    SheetBody(modifier = modifier) {
        LineIdentity(watch = watch, onBack = onBack)
        LineActions(state = state, onToggleWatch = onToggleWatch, onToggleFocus = onToggleFocus)

        if (showingToday) {
            when {
                state.isLoading && times.isEmpty() -> {
                    AuleLoadingState(label = stringResource(R.string.stop_loading))
                }
                times.isNotEmpty() -> {
                    LiveSection(times = times, now = now)
                }
                state.failed -> {
                    AuleEmptyState(
                        title = stringResource(R.string.stop_unavailable_title),
                        detail = stringResource(R.string.stop_unavailable_detail),
                    )
                }
                else -> {
                    AuleEmptyState(
                        title = stringResource(R.string.line_no_times_title),
                        detail = stringResource(R.string.line_no_times_detail),
                    )
                }
            }
        }

        TimetableSection(
            timetable = timetable,
            today = today,
            now = now,
            onPickDate = onPickDate,
            onRetry = onRetryTimetable,
        )
    }
}

/**
 * D'où l'on vient, et ce qu'on regarde.
 *
 * Le retour est écrit, alors que les autres volets se contentent du geste
 * système : c'est le seul volet qui se pose **par-dessus** un autre, et rien à
 * l'écran ne dirait autrement qu'on peut revenir au tableau. Il porte le nom de
 * l'arrêt plutôt qu'un « Retour » générique — c'est la même largeur, et ça dit
 * aussi où l'on est.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineIdentity(watch: DepartureWatch, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.defaultMinSize(minHeight = AuleTouch.minimum),
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = watch.stopName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            LineBadge(
                line = watch.line,
                colorHex = watch.lineColor,
                contentDescription = stringResource(R.string.line_badge, watch.line),
            )
            SheetTitle(watch.destination)
        }
    }
}

/**
 * Les deux choses qu'on peut demander, côte à côte.
 *
 * ## Un groupe de boutons, et pas deux boutons
 *
 * « M'alerter » et « Focus » ne sont pas deux actions rangées l'une à côté de
 * l'autre par manque de place : ce sont **les deux réponses possibles à la même
 * question** — que faire de ce passage-là. Material réserve exactement ce cas au
 * groupe de boutons, qui les traite comme un ensemble : un seul écart entre eux,
 * des extrémités arrondies vers l'extérieur, et un rapport de largeur qui se
 * déforme sous le doigt — celui qu'on presse grandit, l'autre cède la place.
 * C'est cette déformation qui dit qu'ils appartiennent au même choix.
 *
 * Le débordement du groupe n'est pas utilisé : à deux éléments, il n'y a rien à
 * faire déborder, et l'indicateur ne s'affiche jamais.
 *
 * ## Des bascules, et non des boutons d'action
 *
 * Les deux gestes s'annulent, et un bouton qui annule doit dire **l'état où
 * l'on est**, pas seulement ce qu'il fera. Écrit dans le libellé — « M'alerter »
 * qui devient « Ne plus m'alerter » —, cet état coûtait un texte deux fois plus
 * long sur une demi-largeur d'écran, donc tronqué au premier appui. Porté par la
 * coche, il ne coûte rien : le bouton coché prend l'aplat de marque (le thème y
 * place l'accent d'Aule), change de forme, et TalkBack l'annonce coché. Deux
 * canaux visuels et un canal parlé, pour un libellé qui ne bouge plus.
 *
 * « Focus » s'éteint quand aucun véhicule n'a été reconnu dans le flux. Un
 * bouton qui ne mènerait à rien — une carte qui ne bouge pas — se lirait comme
 * une panne ; désactivé, avec la phrase qui l'explique dessous, il se lit comme
 * une information sur le réseau.
 */
@Composable
private fun LineActions(
    state: DepartureWatchUiState,
    onToggleWatch: () -> Unit,
    onToggleFocus: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val armed = state.isArmed
    val focused = state.isFocused
    val canFocus = state.vehicleId != null

    val alertLabel = stringResource(R.string.line_alert)
    val focusLabel = stringResource(R.string.line_focus)

    val detail = when {
        armed && focused -> stringResource(R.string.line_state_alert_and_focus)
        armed -> stringResource(R.string.line_state_alert)
        focused -> stringResource(R.string.line_state_focus)
        canFocus -> stringResource(
            R.string.line_state_idle,
            DepartureWatchEngine.DEFAULT_MINUTES_BEFORE,
        )
        else -> stringResource(
            R.string.line_state_unseen,
            DepartureWatchEngine.DEFAULT_MINUTES_BEFORE,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            toggleableItem(
                checked = armed,
                label = alertLabel,
                onCheckedChange = { onToggleWatch() },
                icon = {
                    Icon(
                        imageVector = if (armed) {
                            Icons.Outlined.NotificationsActive
                        } else {
                            Icons.Outlined.Notifications
                        },
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                },
                weight = 1f,
            )
            toggleableItem(
                checked = focused,
                label = focusLabel,
                onCheckedChange = { onToggleFocus() },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                },
                weight = 1f,
                enabled = canFocus,
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            // Verte quand une promesse est tenue par une donnée mesurée — la
            // même encre que « Temps réel », qui dit la même chose.
            color = if (armed || focused) realtimeInk() else colors.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = detail },
        )
    }
}

/** Ce qui arrive : mesuré, court, périssable. */
@Composable
private fun LiveSection(times: List<StopDeparture>, now: Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        SheetSectionLabel(stringResource(R.string.stop_next_departures))
        SheetCard(modifier = Modifier.fillMaxWidth()) {
            times.forEachIndexed { index, departure ->
                LiveRow(departure = departure, now = now, rank = index)
                if (index < times.lastIndex) {
                    SheetRowDivider()
                }
            }
        }
    }
}

/**
 * Un passage annoncé : à quelle heure, mesuré ou théorique, dans combien de temps.
 *
 * Lu d'un trait par TalkBack — « 15:34, temps réel, 4 min » — plutôt qu'en trois
 * arrêts de curseur pour une phrase qu'on dit d'un souffle.
 *
 * L'heure prend le cran appuyé et le rôle `DATA`, dont les chiffres à chasse
 * fixe alignent enfin une colonne qui en est une ; l'attente passe en second.
 * C'est l'inverse de la fiche d'arrêt, et c'est la raison d'avoir ouvert ce
 * volet — un écran qui répète la hiérarchie du précédent n'a rien apporté.
 */
@Composable
private fun LiveRow(departure: StopDeparture, now: Instant, rank: Int) {
    val colors = MaterialTheme.colorScheme
    val clock = rememberPassageClock()
    val time = clock.format(departure.expectedAt)
    val minutes = departure.waitMinutes(now)
    val wait = if (minutes == 0) {
        stringResource(R.string.wait_approaching)
    } else {
        stringResource(R.string.wait_minutes, minutes)
    }
    val feedLabel = stringResource(
        if (departure.isRealtime) R.string.stop_realtime else R.string.stop_scheduled,
    )

    AuleCappedFontScale {
        ListItem(
            modifier = Modifier
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .auleEnter(index = rank)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$time, $feedLabel, $wait"
                },
            headlineContent = {
                Text(
                    text = time,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 1,
                )
            },
            supportingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RealtimeDot(
                        isLive = departure.isRealtime,
                        liveDescription = stringResource(R.string.stop_realtime),
                        scheduledDescription = stringResource(R.string.stop_scheduled),
                    )
                    Text(
                        text = feedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            trailingContent = {
                Text(
                    text = wait,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    color = if (departure.isRealtime) realtimeInk() else colors.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/**
 * Ce qui est prévu : la journée entière, telle que le catalogue la publie.
 *
 * ## En fiche horaire, pas en liste
 *
 * Une ligne fréquente passe cent fois par jour. Cent rangées font un volet
 * qu'on fait défiler à l'aveugle pour trouver « vers 18 h » ; la fiche horaire —
 * une rangée par heure, les minutes dessus — tient en un écran et se lit comme
 * celle du poteau. C'est aussi la seule disposition où l'on voit d'un coup
 * **la fréquence** de la ligne, qui est souvent ce qu'on est venu chercher.
 */
@Composable
private fun TimetableSection(
    timetable: TimetableUiState,
    today: LocalDate,
    now: Instant,
    onPickDate: (LocalDate) -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetSectionLabel(stringResource(R.string.line_timetable_title))
            DateChip(date = timetable.date ?: today, today = today, onPick = onPickDate)
        }
        when {
            timetable.isLoading -> {
                AuleLoadingState(label = stringResource(R.string.line_timetable_loading))
            }
            timetable.failure != null -> {
                TimetableFailure(kind = timetable.failure, onRetry = onRetry)
            }
            timetable.timetable?.isEmpty != false -> {
                AuleEmptyState(
                    title = stringResource(R.string.line_timetable_empty_title),
                    detail = stringResource(R.string.line_timetable_empty_detail),
                )
            }
            else -> {
                val grid = remember(timetable.timetable) {
                    timetable.timetable.passages.groupBy { instant ->
                        instant.atZone(ZoneId.systemDefault()).hour
                    }
                }
                val nextOne = remember(timetable.timetable, now) {
                    timetable.timetable.next(now)
                }
                SheetCard(modifier = Modifier.fillMaxWidth()) {
                    val hours = grid.keys.sorted()
                    hours.forEachIndexed { index, hour ->
                        TimetableHourRow(
                            hour = hour,
                            passages = grid[hour].orEmpty(),
                            next = nextOne,
                            rank = index,
                        )
                        if (index < hours.lastIndex) {
                            SheetRowDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Une heure, et ses minutes.
 *
 * La minute du prochain passage est **appuyée et colorée** quand la journée
 * regardée est celle qu'on vit : c'est le repère qui évite de chercher où l'on
 * en est dans une colonne de cent chiffres. Sur une autre date, rien n'est
 * désigné — il n'y aurait rien à désigner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimetableHourRow(
    hour: Int,
    passages: List<Instant>,
    next: Instant?,
    rank: Int,
) {
    val colors = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    val hourLabel = stringResource(R.string.line_timetable_hour, hour)
    val minutes = passages.map { it to it.atZone(zone).minute }
    val spoken = "$hourLabel ${minutes.joinToString(", ") { it.second.toString() }}"

    AuleCappedFontScale {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.sm)
                .auleEnter(index = rank)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = hourLabel,
                // Le rôle `DATA` : deux chiffres à chasse fixe, pour que la
                // colonne des heures reste une colonne.
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurfaceVariant,
                modifier = Modifier.widthIn(min = HOUR_COLUMN_WIDTH),
            )
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                for ((instant, minute) in minutes) {
                    val isNext = next != null && instant == next
                    val passed = next != null && instant.isBefore(next)
                    Text(
                        text = stringResource(R.string.line_timetable_minute, minute),
                        style = if (isNext) {
                            MaterialTheme.typography.bodyLargeEmphasized
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = when {
                            isNext -> colors.primary
                            // Ce qui est passé ne s'efface pas : une fiche
                            // horaire dit aussi à quelle heure on **aurait** dû
                            // partir, et c'est ce qui fait choisir le lendemain.
                            passed -> colors.onSurfaceVariant.copy(alpha = PASSED_ALPHA)
                            else -> colors.onSurface
                        },
                    )
                }
            }
        }
    }
}

/**
 * La date qu'on regarde, et le moyen d'en changer.
 *
 * Le sélecteur est celui de Material : il connaît les fuseaux, les semaines qui
 * commencent le lundi, la saisie au clavier et le lecteur d'écran. Aucune de
 * ces quatre choses ne se réécrit en une après-midi.
 */
@Composable
private fun DateChip(date: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val label = if (date == today) {
        stringResource(R.string.line_timetable_today)
    } else {
        rememberDayFormatter().format(date)
    }

    AssistChip(
        onClick = { picking = true },
        label = { Text(text = label, maxLines = 1) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        modifier = Modifier.semantics {
            contentDescription = label
        },
    )

    if (picking) {
        val zone = ZoneId.systemDefault()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = pickerState.selectedDateMillis
                        picking = false
                        if (millis != null) {
                            // Le sélecteur rend un minuit **UTC** : le relire
                            // dans le fuseau de l'appareil décalerait la date
                            // d'un jour à l'ouest de Greenwich, et c'est
                            // exactement le genre de défaut qu'on ne voit qu'en
                            // production.
                            onPick(
                                Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.of("UTC"))
                                    .toLocalDate(),
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.line_timetable_pick_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(R.string.line_timetable_pick_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * Pourquoi la grille manque — et ce qu'on peut y faire.
 *
 * Une panne réseau se réessaie, une ligne absente du catalogue non, et une
 * session refusée se règle ailleurs. Proposer « réessayer » aux trois aurait
 * fait tourner en rond deux personnes sur trois.
 */
@Composable
private fun TimetableFailure(kind: TimetableFailureKind, onRetry: () -> Unit) {
    val title = stringResource(
        when (kind) {
            TimetableFailureKind.NOT_SIGNED_IN -> R.string.line_timetable_signed_out_title
            TimetableFailureKind.NOT_CONFIGURED -> R.string.line_timetable_absent_title
            TimetableFailureKind.NOT_IN_CATALOG -> R.string.line_timetable_absent_title
            TimetableFailureKind.UNAVAILABLE -> R.string.line_timetable_failed_title
        },
    )
    val detail = stringResource(
        when (kind) {
            TimetableFailureKind.NOT_SIGNED_IN -> R.string.line_timetable_signed_out_detail
            TimetableFailureKind.NOT_CONFIGURED -> R.string.line_timetable_absent_detail
            TimetableFailureKind.NOT_IN_CATALOG -> R.string.line_timetable_absent_detail
            TimetableFailureKind.UNAVAILABLE -> R.string.line_timetable_failed_detail
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        AuleEmptyState(title = title, detail = detail)
        if (kind == TimetableFailureKind.UNAVAILABLE) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.defaultMinSize(minHeight = AuleTouch.minimum),
            ) {
                Text(stringResource(R.string.line_timetable_retry))
            }
        }
    }
}

/** La même seconde que la fiche d'arrêt : les deux volets vieillissent ensemble. */
private const val LINE_TICK_MS = 10_000L

/** Assez pour « 23 » à 200 % de police, sans écarter les minutes du reste. */
private val HOUR_COLUMN_WIDTH = 40.dp

/** Ce qui est passé s'estompe, mais reste lisible : c'est encore une information. */
private const val PASSED_ALPHA = 0.55f
