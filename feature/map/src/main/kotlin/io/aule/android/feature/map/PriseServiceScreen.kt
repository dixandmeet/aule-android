package io.aule.android.feature.map

import android.Manifest
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.location.LocationAuthorization
import io.aule.android.core.location.LocationProvider
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.DriverServiceFailureKind
import io.aule.android.core.model.ServiceDirection
import io.aule.android.core.model.ServiceLine
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException

/**
 * L'assistant de prise de service, posé par-dessus la carte.
 *
 * Six étapes, comme Flutter. L'heure, le train et le véhicule restent
 * facultatifs ; le GPS, non. La carte n'est pas démontée pendant ce temps.
 *
 * ## Trois poids, et trois seulement
 *
 * Prendre son service est le premier geste de la journée et il n'arrive qu'une
 * fois : c'est un moment, pas une page de réglages. L'écran s'organise donc
 * autour de trois choses, dans cet ordre.
 *
 * 1. **La question**, en tête de l'étape. « Quelle ligne ? » est le sujet de
 *    l'écran ; il se lisait jusqu'ici au même poids que la phrase
 *    d'explication qui le suit, ce qui revenait à n'avoir aucun titre.
 * 2. **Le choix**, une fois fait. Un aplat pastel dit « coché » ; la surface de
 *    marque dit « c'est celle-là ». Sur une ligne prise parmi trente, en plein
 *    soleil, à travers un pare-brise, la différence n'est pas décorative.
 * 3. **La confirmation**, ancrée au bas de l'écran. Elle ne défile plus avec le
 *    contenu : le pouce la retrouve au même endroit d'une étape à l'autre, et
 *    le clavier des deux étapes de saisie la pousse devant lui au lieu de
 *    l'enterrer sous une liste.
 *
 * ## Une seule surface de marque à la fois
 *
 * C'est la contrainte qui tient l'ensemble, et l'enchaînement des étapes la
 * respecte sans qu'on ait à arbitrer : sur les deux premières il n'y a pas de
 * bouton — choisir avance — donc la rangée choisie porte l'accent ; à partir de
 * l'heure, plus aucune liste n'est affichée, donc le bouton le porte seul.
 *
 * La dernière étape est le seul endroit où les deux pourraient se croiser, et
 * l'inversion y est **voulue** : tant que la position n'est pas accordée, c'est
 * la carte du GPS qui est l'action, et le bouton est éteint parce qu'il ne mène
 * nulle part ; dès qu'elle l'est, le poids passe au bouton et la carte redevient
 * un constat. Le regard suit l'action au lieu d'avoir à la chercher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriseServiceScreen(
    viewModel: PriseServiceViewModel,
    location: LocationProvider,
    onClose: () -> Unit,
    onStarted: (ActiveDriverService) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authorization by location.authorization.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        location.markPermissionRequested()
        location.refreshAuthorization()
    }

    LaunchedEffect(state.started) {
        state.started?.let(onStarted)
    }
    LaunchedEffect(authorization) {
        if (authorization == LocationAuthorization.GRANTED) {
            viewModel.setGpsReady(true)
        } else if (state.gpsReady) {
            viewModel.setGpsReady(false)
        }
    }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            if (viewModel.back()) onClose()
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    AuleTheme {
        val colors = MaterialTheme.colorScheme
        AuleAmbientBackground(modifier = modifier.fillMaxSize()) {
            // La gouttière appartient au contenu, pas à l'écran : posée ici,
            // elle enfermait la barre d'application dans une carte flottante
            // large de deux marges — là où Material la veut bord à bord.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding(),
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.service_title),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = stringResource(
                                    R.string.service_step,
                                    state.step.index + 1,
                                    PriseServiceStep.entries.size,
                                ),
                                style = MaterialTheme.typography.labelSmallEmphasized,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (viewModel.back()) onClose() }) {
                            Icon(
                                imageVector = AuleGlyph.BACK.asImageVector(),
                                contentDescription = stringResource(R.string.service_back),
                            )
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    // Transparente, comme la relève : le fond d'ambiance passe
                    // dessous d'un bout à l'autre au lieu d'être coupé net par
                    // un bandeau blanc.
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = colors.onSurface,
                        navigationIconContentColor = colors.onSurface,
                    ),
                )
                StepTrail(
                    current = state.step.index,
                    total = PriseServiceStep.entries.size,
                    modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                )
                val line = state.selectedLine
                if (line != null && state.step > PriseServiceStep.DIRECTION) {
                    Spacer(modifier = Modifier.height(AuleSpacing.md))
                    ServiceRecap(
                        line = line,
                        terminus = state.selectedDirection?.terminus,
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                }
                Spacer(modifier = Modifier.height(AuleSpacing.lg))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AuleSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
                ) {
                    when (state.step) {
                        PriseServiceStep.LINE -> LineStep(
                            state = state,
                            onSearch = viewModel::setSearch,
                            onPick = viewModel::pickLine,
                            onRetry = viewModel::loadLines,
                        )
                        PriseServiceStep.DIRECTION -> DirectionStep(
                            line = state.selectedLine,
                            selectedKey = state.selectedDirectionKey,
                            onPick = viewModel::pickDirection,
                        )
                        PriseServiceStep.TIME -> TimeStep(
                            departure = state.scheduledDeparture,
                            onPick = viewModel::setTimeOfDay,
                        )
                        PriseServiceStep.TRAIN -> {
                            StepIntro(
                                title = R.string.service_train_title,
                                detail = R.string.service_train_detail,
                                modifier = Modifier.auleEnter(),
                            )
                            OutlinedTextField(
                                value = state.trainNumber,
                                onValueChange = viewModel::setTrainNumber,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .auleEnter(index = 1),
                                label = { Text(stringResource(R.string.service_train_field)) },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Next,
                                ),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                        PriseServiceStep.VEHICLE -> {
                            StepIntro(
                                title = R.string.service_vehicle_title,
                                detail = R.string.service_vehicle_detail,
                                modifier = Modifier.auleEnter(),
                            )
                            OutlinedTextField(
                                value = state.vehicleId,
                                onValueChange = viewModel::setVehicleId,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .auleEnter(index = 1),
                                label = { Text(stringResource(R.string.service_vehicle_field)) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                        PriseServiceStep.GPS -> GpsStep(
                            ready = state.gpsReady,
                            authorization = authorization,
                            onEnable = {
                                when (authorization) {
                                    LocationAuthorization.UNKNOWN ->
                                        permissionLauncher.launch(LOCATION_PERMISSIONS)
                                    LocationAuthorization.GRANTED -> viewModel.setGpsReady(true)
                                    else -> location.openSettings()
                                }
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(AuleSpacing.lg))
                }
                if (state.step >= PriseServiceStep.TIME) {
                    ServiceActionBar(
                        label = stringResource(
                            if (state.step.isLast) {
                                R.string.service_start
                            } else {
                                R.string.service_continue
                            },
                        ),
                        last = state.step.isLast,
                        enabled = state.canContinue && !state.isStarting,
                        busy = state.isStarting,
                        failure = state.startFailure?.label(),
                        onClick = viewModel::continueOrStart,
                    )
                }
            }
        }
    }
}

/**
 * La question de l'étape, et la phrase qui l'explique.
 *
 * Le titre passe sur `headlineSmallEmphasized` : c'est le slot appuyé de
 * Material 3 Expressive, même taille et même boîte que son homologue ordinaire,
 * mais d'un cran de graisse au-dessus. Rien ne bouge dans la mise en page, et
 * pourtant la question cesse d'être une phrase parmi les autres.
 *
 * Les deux textes sont **serrés** l'un contre l'autre — un cran d'espacement au
 * lieu de celui que la colonne applique entre ses blocs. Un titre et sa glose
 * qui respirent autant que deux blocs distincts se lisent comme deux blocs
 * distincts, et l'œil ne sait plus lequel commande.
 */
@Composable
private fun StepIntro(title: Int, detail: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(detail),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

/**
 * Où l'on en est, en autant de segments que d'étapes.
 *
 * Deux choses ont changé, et chacune corrige un défaut qu'on ne voyait qu'une
 * fois l'écran sous les yeux.
 *
 * Le segment courant **s'allonge**. Six barres identiques dont trois sont
 * teintées disent combien d'étapes sont faites, pas laquelle est en cours : il
 * faut compter. Un segment plus long désigne l'étape sans qu'on compte, et
 * c'est la seule information qu'on cherche à cet endroit.
 *
 * Les segments à venir passent d'`outlineVariant` à `surfaceContainerHighest`.
 * Le premier est le filet de séparation de la maison — huit centièmes d'encre,
 * invisibles posés sur le lavis d'ambiance : la barre n'avait donc pas de piste,
 * seulement des marques flottantes. Le second est un gris franc depuis que
 * l'échelle des surfaces descend vraiment, et il donne au trajet un fond.
 *
 * Les deux animations ne tournent pas sur le même régime : la largeur est un
 * déplacement, donc un ressort **spatial**, qui dépasse et se pose ; la couleur
 * n'est qu'une couleur, donc un ressort d'**effets**, qui ne dépasse jamais. Une
 * teinte animée sur un ressort spatial scintille au passage de l'étape.
 */
@Composable
private fun StepTrail(current: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(STEP_MARK_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val span by animateFloatAsState(
                targetValue = if (index == current) STEP_MARK_CURRENT_SPAN else 1f,
                animationSpec = motion.defaultSpatialSpec(),
                label = "largeur du segment",
            )
            val tint by animateColorAsState(
                targetValue = if (index <= current) {
                    colors.primary
                } else {
                    colors.surfaceContainerHighest
                },
                animationSpec = motion.defaultEffectsSpec(),
                label = "teinte du segment",
            )
            Box(
                modifier = Modifier
                    .weight(span)
                    .height(STEP_MARK_HEIGHT)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

/**
 * Le rappel de ce qui est déjà choisi.
 *
 * Quatre étapes séparent le choix de la ligne du démarrage, et sur ces quatre
 * étapes l'écran ne disait plus rien de ce qu'on est en train de configurer. Un
 * conducteur qui hésite n'avait qu'un moyen de vérifier : revenir en arrière
 * deux fois, donc perdre sa saisie de vue.
 *
 * Le rappel tient sur une ligne, au badge de la ligne près — celui-là même que
 * la liste affichait, dans la couleur du réseau, ce qui le rend reconnaissable
 * avant d'être lu. Il n'est pas une carte : posé sur un cran de surface, sans
 * ombre et sans contour, il se lit comme une étiquette, ce qu'il est.
 */
@Composable
private fun ServiceRecap(
    line: ServiceLine,
    terminus: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = if (terminus.isNullOrBlank()) {
        stringResource(R.string.service_direction_other)
    } else {
        stringResource(R.string.service_direction, terminus)
    }
    Surface(
        modifier = modifier.auleEnter(),
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AuleSpacing.md,
                vertical = AuleSpacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            LineBadge(
                line = line.label,
                colorHex = line.colorHex,
                contentDescription = stringResource(R.string.line_badge, line.label),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LineStep(
    state: PriseServiceUiState,
    onSearch: (String) -> Unit,
    onPick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    StepIntro(
        title = R.string.service_line_title,
        detail = R.string.service_line_detail,
        modifier = Modifier.auleEnter(),
    )
    when {
        // Un chargement qui ne dit pas ce qu'il charge ne renseigne personne, et
        // la roue nue laissait le conducteur devant un écran vide sans savoir si
        // l'application travaillait ou si elle avait renoncé.
        state.isLoadingLines -> AuleLoadingState(
            label = stringResource(R.string.service_lines_loading),
        )
        state.loadFailure != null -> Column(
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            AuleEmptyState(
                title = stringResource(R.string.service_lines_error_title),
                detail = state.loadFailure.label(),
            )
            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AuleControl.height),
            ) {
                Text(stringResource(R.string.issue_retry))
            }
        }
        else -> {
            OutlinedTextField(
                value = state.search,
                onValueChange = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .auleEnter(index = 1),
                label = { Text(stringResource(R.string.service_line_search)) },
                leadingIcon = {
                    Icon(
                        imageVector = AuleGlyph.SEARCH.asImageVector(),
                        contentDescription = null,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            if (state.filteredLines.isEmpty()) {
                AuleEmptyState(
                    title = if (state.search.isBlank()) {
                        stringResource(R.string.service_lines_empty)
                    } else {
                        stringResource(R.string.service_lines_none, state.search)
                    },
                    detail = null,
                )
            }
            state.filteredLines.forEachIndexed { index, line ->
                // La cascade se règle sur l'identité de la ligne, pas sur son
                // rang : sans clef, filtrer la liste décale les rangées d'un
                // cran et fait rejouer l'entrée à celles qui n'ont pas bougé —
                // la liste clignote à chaque caractère tapé.
                key(line.id) {
                    LineChoice(
                        line = line,
                        selected = line.id == state.selectedLineId,
                        onClick = { onPick(line.id) },
                        modifier = Modifier.auleEnter(index = index + LIST_STAGGER_OFFSET),
                    )
                }
            }
        }
    }
}

/**
 * L'enveloppe d'une rangée qu'on choisit, dans ses deux états.
 *
 * Ce composant n'existe que pour tenir les deux états **ensemble**. Séparés, la
 * ligne et le sens divergeaient d'un rayon, d'une teinte de fond ou d'un cran
 * d'ombre au premier écran qu'on retouchait, alors que ce sont le même geste.
 *
 * Au repos, la rangée prend `surfaceContainerHigh` et non `surface` : sur le
 * fond d'ambiance — qui est précisément `surface` — une rangée en surface
 * n'était visible que par son ombre, et une liste de trente ombres sans
 * contenant est exactement ce qui donnait l'impression d'écran fade.
 *
 * Choisie, elle passe à la surface de marque : dégradé, reflet, et une ombre à
 * la couleur de l'accent. Ce n'est pas un aplat plus foncé, c'est une autre
 * matière — et c'est ce qu'il faut pour qu'un choix se voie d'un coup d'œil
 * lancé entre deux manœuvres.
 */
@Composable
private fun ChoiceSurface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    if (selected) {
        AuleBrandSurface(
            modifier = modifier,
            shape = shape,
            onClick = onClick,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
        ) {
            Box(content = content)
        }
    }
}

@Composable
private fun LineChoice(
    line: ServiceLine,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    ChoiceSurface(
        selected = selected,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = line.label
                this.selected = selected
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .padding(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            // Le badge garde la couleur du réseau dans les deux états. C'est
            // l'identité de la ligne, pas un état de sélection : la teindre en
            // accent ferait bouger le repère au moment précis où l'on vérifie
            // qu'on a pris la bonne.
            LineBadge(
                line = line.label,
                colorHex = line.colorHex,
                contentDescription = stringResource(R.string.line_badge, line.label),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.label,
                    style = if (selected) {
                        MaterialTheme.typography.titleMediumEmphasized
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                )
                Text(
                    text = line.description,
                    // Sur la surface de marque, la hiérarchie vient de la
                    // taille et non de l'opacité : une encre affaiblie sur un
                    // dégradé teal décroche au soleil.
                    color = if (selected) LocalContentColor.current else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                )
            }
            if (selected) {
                Icon(
                    imageVector = AuleGlyph.CHECK.asImageVector(filled = true),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun DirectionStep(
    line: ServiceLine?,
    selectedKey: String?,
    onPick: (String) -> Unit,
) {
    StepIntro(
        title = R.string.service_direction_title,
        detail = R.string.service_direction_detail,
        modifier = Modifier.auleEnter(),
    )
    line?.directions?.forEachIndexed { index, direction ->
        DirectionChoice(
            direction = direction,
            selected = direction.key == selectedKey,
            onClick = { onPick(direction.key) },
            modifier = Modifier.auleEnter(index = index + 1),
        )
    }
}

@Composable
private fun DirectionChoice(
    direction: ServiceDirection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val label = if (direction.terminus.isBlank()) {
        stringResource(R.string.service_direction_other)
    } else {
        stringResource(R.string.service_direction, direction.terminus)
    }
    ChoiceSurface(
        selected = selected,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                this.selected = selected
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .padding(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            // Le drapeau de terminus, plein quand le sens est retenu. C'est
            // l'emploi que la famille d'icônes prévoit pour l'état sélectionné :
            // la même icône, remplie — jamais une seconde icône.
            Icon(
                imageVector = AuleGlyph.FLAG.asImageVector(filled = selected),
                contentDescription = null,
            )
            Text(
                text = label,
                style = if (selected) {
                    MaterialTheme.typography.titleMediumEmphasized
                } else {
                    MaterialTheme.typography.titleMedium
                },
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            if (selected) {
                Icon(
                    imageVector = AuleGlyph.CHECK.asImageVector(filled = true),
                    contentDescription = null,
                )
            }
        }
    }
}

/**
 * L'heure de prise de service, facultative — et affichée comme une donnée.
 *
 * « 05:42 » n'est pas du texte : c'est le seul chiffre de tout l'assistant, et
 * il porte sur `headlineMediumEmphasized`, le slot appuyé du rôle `HERO`. Ce
 * slot a les **chiffres à chasse fixe** : sans eux, deux heures de longueurs
 * apparentes différentes décalent la ligne d'un écran à l'autre.
 *
 * Le cartouche reste calme malgré tout — cran de surface, pas de marque. L'heure
 * est facultative ; lui donner le poids de l'action laisserait croire qu'il faut
 * la renseigner pour continuer, ce qui retiendrait un conducteur au dépôt pour
 * une grille qu'il n'a pas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeStep(
    departure: java.time.Instant?,
    onPick: (Int, Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val view = LocalView.current
    var showPicker by remember { mutableStateOf(false) }
    StepIntro(
        title = R.string.service_time_title,
        detail = R.string.service_time_detail,
        modifier = Modifier.auleEnter(),
    )
    val label = if (departure == null) {
        stringResource(R.string.service_time_choose)
    } else {
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(departure)
    }
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            showPicker = true
        },
        modifier = Modifier
            .fillMaxWidth()
            .auleEnter(index = 1)
            .semantics { contentDescription = label },
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .padding(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = if (departure == null) colors.onSurfaceVariant else colors.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = if (departure == null) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.headlineMediumEmphasized
                    },
                    color = if (departure == null) colors.onSurfaceVariant else colors.onSurface,
                )
                if (departure != null) {
                    Text(
                        text = stringResource(R.string.service_time_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (departure != null) {
                Text(
                    text = stringResource(R.string.service_time_change),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = colors.primary,
                )
            }
        }
    }
    Text(
        text = stringResource(
            if (departure == null) R.string.service_time_missing else R.string.service_time_hint,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.auleEnter(index = 2),
    )
    if (showPicker) {
        val zone = ZoneId.systemDefault()
        val base = departure?.atZone(zone) ?: ZonedDateTime.now(zone)
        val pickerState = rememberTimePickerState(
            initialHour = base.hour,
            initialMinute = base.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        TimePickerDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.service_time_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPick(pickerState.hour, pickerState.minute)
                        showPicker = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            TimePicker(state = pickerState)
        }
    }
}

/**
 * La position, seule étape dont on ne peut pas se passer — et le seul endroit
 * où le poids de l'écran se déplace.
 *
 * Tant que la position n'est pas accordée, la carte **est** l'action : elle
 * prend la surface de marque, et le bouton du bas est éteint parce qu'il ne
 * mène nulle part. Dès qu'elle l'est, la carte redevient un constat — cran de
 * surface, coche à l'encre d'accent — et le poids passe au bouton, qui est
 * désormais le seul geste qui reste.
 *
 * L'icône suit la même bascule et ne raconte pas la même chose de part et
 * d'autre : un viseur de position tant qu'il faut l'autoriser, une coche
 * ensuite. Le repère d'arrêt qui servait avant disait « un point sur la
 * carte » — ce n'est pas de cela qu'il s'agit ici.
 */
@Composable
private fun GpsStep(
    ready: Boolean,
    authorization: LocationAuthorization,
    onEnable: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    StepIntro(
        title = R.string.service_gps_title,
        detail = R.string.service_gps_detail,
        modifier = Modifier.auleEnter(),
    )
    val label = stringResource(
        if (ready) R.string.service_gps_on else R.string.service_gps_off,
    )
    val press = {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onEnable()
    }
    val marked = Modifier
        .fillMaxWidth()
        .auleEnter(index = 1)
        .semantics {
            contentDescription = label
            this.selected = ready
        }
    if (ready) {
        Surface(
            onClick = press,
            modifier = marked,
            shape = MaterialTheme.shapes.small,
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
        ) {
            Box {
                GpsRow(
                    icon = AuleGlyph.CHECK.asImageVector(filled = true),
                    tint = colors.primary,
                    label = label,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    } else {
        AuleBrandSurface(
            modifier = marked,
            shape = MaterialTheme.shapes.small,
            onClick = press,
        ) {
            GpsRow(
                icon = Icons.Outlined.MyLocation,
                tint = LocalContentColor.current,
                label = label,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
        val notice = when (authorization) {
            LocationAuthorization.DENIED -> R.string.service_gps_denied
            LocationAuthorization.SERVICES_DISABLED -> R.string.service_gps_disabled
            LocationAuthorization.REDUCED_ACCURACY -> R.string.service_gps_reduced
            else -> null
        }
        if (notice != null) {
            AuleBanner(
                message = stringResource(notice),
                tone = AuleTone.ALERT,
                modifier = Modifier.auleEnter(index = 2),
            )
        }
    }
}

@Composable
private fun GpsRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    style: TextStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .padding(AuleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Text(text = label, style = style, modifier = Modifier.weight(1f))
    }
}

/**
 * La barre de confirmation, ancrée sous la colonne qui défile.
 *
 * Elle était le dernier bloc de la liste, donc quelque part sous le pli dès que
 * la ligne avait deux directions ou que le clavier montait. Une action qu'il
 * faut aller chercher n'est pas l'action principale, quelle que soit sa
 * couleur.
 *
 * Elle est **hors** de la zone de défilement et non posée par-dessus : le
 * contenu ne passe donc jamais dessous, et il n'y a rien à compenser par une
 * gouttière de bas de liste qu'on oublierait de tenir à jour. L'`imePadding` de
 * l'écran la fait monter avec le clavier, ce qui la place exactement là où le
 * pouce se trouve déjà pendant la saisie du numéro de véhicule.
 *
 * L'ombre du bouton est **teintée d'accent**, et seulement quand il est
 * actionnable : une lueur de marque sous l'action désigne, une ombre noire
 * salirait le teal, et la même lueur sous un bouton éteint promettrait un geste
 * qui ne marche pas.
 *
 * La dernière étape ajoute le triangle de lecture au libellé. « Continuer » et
 * « Démarrer le service » ne sont pas le même engagement : le premier revient
 * sur ses pas, le second ouvre un service en base. Deux mots suffisent à les
 * distinguer quand on lit ; le glyphe les distingue quand on ne lit pas.
 */
@Composable
private fun ServiceActionBar(
    label: String,
    last: Boolean,
    enabled: Boolean,
    busy: Boolean,
    failure: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuleSpacing.lg)
            .padding(top = AuleSpacing.md, bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        if (failure != null) {
            AuleBanner(message = failure, tone = AuleTone.ALERT)
        }
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .auleShadow(
                    level = if (enabled) AuleElevation.FLOATING else AuleElevation.NONE,
                    shape = shape,
                    tint = AuleShadowTint.ACCENT,
                )
                .defaultMinSize(minHeight = AuleControl.height),
            enabled = enabled,
            shape = shape,
            colors = auleAccentButtonColors(),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    color = AuleTheme.tokens.onAccent.color,
                    strokeWidth = AuleStroke.glyph,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                ) {
                    if (last) {
                        Icon(
                            imageVector = AuleGlyph.PLAY.asImageVector(filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(AuleControl.icon),
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverServiceFailureKind.label(): String = stringResource(
    when (this) {
        DriverServiceFailureKind.NOT_SIGNED_IN -> R.string.service_error_session
        DriverServiceFailureKind.NO_DRIVER -> R.string.service_error_driver
        DriverServiceFailureKind.ALREADY_ON_SERVICE -> R.string.service_error_already
        DriverServiceFailureKind.NOT_CONFIGURED -> R.string.service_error_config
        DriverServiceFailureKind.NETWORK -> R.string.service_error_network
        DriverServiceFailureKind.LINES_EMPTY -> R.string.service_error_lines
        DriverServiceFailureKind.REJECTED -> R.string.service_error_rejected
        DriverServiceFailureKind.UNKNOWN -> R.string.service_error_unknown
    },
)

/**
 * Le segment de progression a gagné deux points de hauteur.
 *
 * À quatre points, la piste des étapes à venir se lisait comme un filet de
 * séparation oublié ; à six, elle se lit comme un trajet. C'est la hauteur la
 * plus basse à laquelle un segment arrondi garde encore sa forme.
 */
private val STEP_MARK_HEIGHT = 6.dp

/** L'écart entre deux segments : assez pour les compter, pas pour les séparer. */
private val STEP_MARK_GAP = 6.dp

/**
 * Ce que le segment courant prend de plus que les autres.
 *
 * Deux fois et demie : en deçà, l'allongement passe pour une irrégularité de
 * rendu ; au-delà, les cinq segments restants deviennent des points et la barre
 * cesse de dire combien d'étapes il reste.
 */
private const val STEP_MARK_CURRENT_SPAN = 2.5f

/**
 * Le rang de la première rangée de la liste dans la cascade d'entrée.
 *
 * La question et le champ de recherche occupent les deux premiers rangs ; les
 * lignes partent donc du troisième, faute de quoi la première rangée arriverait
 * avant le champ qui la filtre.
 */
private const val LIST_STAGGER_OFFSET = 2

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
