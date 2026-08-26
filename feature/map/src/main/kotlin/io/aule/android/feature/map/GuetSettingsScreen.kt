package io.aule.android.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleConnectedButtonGroup
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.guet.AlertPreferences
import io.aule.android.core.guet.GuetPreferences
import io.aule.android.core.guet.WalkingPace
import io.aule.android.core.model.TransitLine
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.repository.GuetPreferencesStore
import io.aule.android.core.model.repository.NetworkLineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Les réglages du Guet.
 *
 * **Un écran plein, jamais un volet.** Les volets de la carte répondent à un
 * geste qu'on vient de faire — toucher un arrêt, un véhicule — et se referment
 * avec lui. Régler une veille est autre chose : on y descend, on lit, on revient.
 * C'est la même distinction que l'écran de profil.
 *
 * ## L'interrupteur d'abord, et il commande tout le reste
 *
 * Éteint, les autres réglages n'ont plus rien à régler : ils restent visibles —
 * les cacher ferait croire qu'ils n'existent pas — mais grisés. Un écran qui
 * laisse choisir une allure de marche alors que rien ne surveille est un écran
 * qui ment.
 *
 * Port de `Native/Aule/Features/Settings/GuetSettingsView.swift`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuetSettingsScreen(
    model: GuetSettingsModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * L'inventaire du réseau, pour choisir les lignes suivies. Vide tant que
     * l'index n'a pas été lu — la section disparaît alors, plutôt que d'offrir
     * une liste vide qu'on prendrait pour une panne.
     */
    lines: List<TransitLine> = emptyList(),
) {
    val prefs by model.state.collectAsStateWithLifecycle()
    val title = stringResource(R.string.guet_title)

    AuleTheme {
        val colors = MaterialTheme.colorScheme
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.surface)
                .safeDrawingPadding()
                .semantics {
                    this.paneTitle = title
                    isTraversalGroup = true
                },
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(R.string.guet_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = AuleGlyph.BACK.asImageVector(),
                            contentDescription = stringResource(R.string.guet_close),
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface,
                ),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AuleSpacing.lg)
                    .padding(bottom = AuleSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.lg),
            ) {
                GuetSection(
                    title = stringResource(R.string.guet_section_state),
                    modifier = Modifier.auleEnter(index = 0),
                ) {
                    SheetCard(modifier = Modifier.fillMaxWidth()) {
                        GuetSwitchRow(
                            label = stringResource(R.string.guet_enable),
                            detail = stringResource(
                                if (prefs.isEnabled) R.string.guet_on_detail else R.string.guet_off_detail,
                            ),
                            checked = prefs.isEnabled,
                            enabled = true,
                            onChange = model::setEnabled,
                        )
                    }
                }

                val on = prefs.isEnabled

                GuetSection(
                    title = stringResource(R.string.guet_section_when),
                    modifier = Modifier.auleEnter(index = 1),
                ) {
                    GuetMinutePicker(
                        label = stringResource(R.string.guet_preparation),
                        detail = stringResource(R.string.guet_preparation_detail),
                        choices = GuetPreferences.PREPARATION_CHOICES,
                        selected = prefs.preparationMinutes,
                        enabled = on,
                        onSelect = model::setPreparationMinutes,
                    )
                    GuetMinutePicker(
                        label = stringResource(R.string.guet_platform_margin),
                        detail = stringResource(R.string.guet_platform_margin_detail),
                        choices = GuetPreferences.PLATFORM_MARGIN_CHOICES,
                        selected = prefs.platformMarginMinutes,
                        enabled = on,
                        onSelect = model::setPlatformMarginMinutes,
                    )
                }

                GuetSection(
                    title = stringResource(R.string.guet_section_walk),
                    modifier = Modifier.auleEnter(index = 2),
                ) {
                    GuetPacePicker(
                        selected = prefs.pace,
                        enabled = on,
                        onSelect = model::setPace,
                    )
                }

                GuetSection(
                    title = stringResource(R.string.guet_section_modes),
                    detail = stringResource(R.string.guet_modes_detail),
                    modifier = Modifier.auleEnter(index = 3),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                    ) {
                        TransportMode.entries.forEach { mode ->
                            FilterChip(
                                selected = mode in prefs.modes,
                                onClick = { model.toggleMode(mode) },
                                enabled = on,
                                label = { Text(stringResource(mode.guetLabelRes())) },
                            )
                        }
                    }
                }

                if (lines.isNotEmpty()) {
                    GuetSection(
                        title = stringResource(R.string.guet_section_lines),
                        detail = stringResource(R.string.guet_lines_detail),
                        modifier = Modifier.auleEnter(index = 4),
                    ) {
                        GuetFollowedLines(
                            lines = lines,
                            followed = prefs.followedLines,
                            enabled = on,
                            onToggle = model::toggleFollowedLine,
                        )
                    }
                }

                GuetSection(
                    title = stringResource(R.string.guet_section_how),
                    modifier = Modifier.auleEnter(index = 5),
                ) {
                    SheetCard(modifier = Modifier.fillMaxWidth()) {
                        GuetSwitchRow(
                            label = stringResource(R.string.guet_sound),
                            checked = prefs.alerts.sound,
                            enabled = on,
                            onChange = model::setSound,
                        )
                        SheetRowDivider()
                        GuetSwitchRow(
                            label = stringResource(R.string.guet_haptics),
                            checked = prefs.alerts.haptics,
                            enabled = on,
                            onChange = model::setHaptics,
                        )
                        SheetRowDivider()
                        GuetSwitchRow(
                            label = stringResource(R.string.guet_notifications),
                            detail = stringResource(R.string.guet_notifications_detail),
                            checked = prefs.alerts.notifications,
                            enabled = on,
                            onChange = model::setNotifications,
                        )
                        SheetRowDivider()
                        GuetSwitchRow(
                            label = stringResource(R.string.guet_ongoing),
                            detail = stringResource(R.string.guet_ongoing_detail),
                            checked = prefs.alerts.ongoingNotification,
                            enabled = on,
                            onChange = model::setOngoingNotification,
                        )
                    }
                    GuetIntensityPicker(
                        selected = prefs.alerts.intensity,
                        enabled = on,
                        onSelect = model::setIntensity,
                    )
                }
            }
        }
    }
}

/** Un intertitre et son bloc. Même grammaire que les sections d'un volet. */
@Composable
private fun GuetSection(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        SheetSectionLabel(title)
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

/**
 * Une rangée à interrupteur.
 *
 * La rangée entière est touchable, pas seulement l'interrupteur : viser un
 * commutateur de 52 points au pouce, en marchant, se manque.
 */
@Composable
private fun GuetSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    detail: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        // L'interrupteur ne porte pas d'action propre : la rangée l'a déjà, et
        // deux cibles superposées font annoncer la commande deux fois.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Un choix de minutes, en pastilles. Les paliers viennent du modèle, pas de la vue. */
@Composable
private fun GuetMinutePicker(
    label: String,
    detail: String,
    choices: List<Int>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.xs))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            choices.forEach { minutes ->
                FilterChip(
                    selected = minutes == selected,
                    onClick = { onSelect(minutes) },
                    enabled = enabled,
                    label = {
                        Text(
                            text = if (minutes == 0) {
                                stringResource(R.string.guet_minutes_none)
                            } else {
                                stringResource(R.string.guet_minutes, minutes)
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun GuetPacePicker(
    selected: WalkingPace,
    enabled: Boolean,
    onSelect: (WalkingPace) -> Unit,
) {
    val paces = WalkingPace.entries
    GuetExclusiveGroup(
        options = paces,
        selected = selected,
        enabled = enabled,
        onSelect = onSelect,
        label = { pace -> stringResource(pace.labelRes()) },
    )
}

@Composable
private fun GuetIntensityPicker(
    selected: AlertPreferences.Intensity,
    enabled: Boolean,
    onSelect: (AlertPreferences.Intensity) -> Unit,
) {
    val levels = AlertPreferences.Intensity.entries
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
        GuetExclusiveGroup(
            options = levels,
            selected = selected,
            enabled = enabled,
            onSelect = onSelect,
            label = { intensity -> stringResource(intensity.labelRes()) },
        )
        // Ce que chaque cran change, écrit noir sur blanc : un réglage dont on ne
        // peut pas dire l'effet est un réglage qui ment.
        Text(
            text = stringResource(selected.detailRes()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Les lignes suivies.
 *
 * ⚠️ Elles **ne restreignent pas** la veille : elles pèsent dans le classement,
 * elles n'en excluent pas les autres. Une veille qui ne regarderait que les
 * lignes déclarées manquerait le bus qu'on ne prend qu'une fois — d'où la phrase
 * qui accompagne la section.
 */
@Composable
private fun GuetFollowedLines(
    lines: List<TransitLine>,
    followed: Set<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        lines.forEach { line ->
            FilterChip(
                selected = line.name in followed,
                onClick = { onToggle(line.name) },
                enabled = enabled,
                label = { Text(line.name) },
            )
        }
    }
}

private fun TransportMode.guetLabelRes(): Int = when (this) {
    TransportMode.BUS -> R.string.guet_mode_bus
    TransportMode.TRAM -> R.string.guet_mode_tram
    TransportMode.BOAT -> R.string.guet_mode_boat
}

private fun WalkingPace.labelRes(): Int = when (this) {
    WalkingPace.SLOW -> R.string.guet_pace_slow
    WalkingPace.NORMAL -> R.string.guet_pace_normal
    WalkingPace.FAST -> R.string.guet_pace_fast
    WalkingPace.AUTOMATIC -> R.string.guet_pace_auto
}

private fun AlertPreferences.Intensity.labelRes(): Int = when (this) {
    AlertPreferences.Intensity.DISCREET -> R.string.guet_intensity_discreet
    AlertPreferences.Intensity.STANDARD -> R.string.guet_intensity_standard
    AlertPreferences.Intensity.INSISTENT -> R.string.guet_intensity_insistent
}

private fun AlertPreferences.Intensity.detailRes(): Int = when (this) {
    AlertPreferences.Intensity.DISCREET -> R.string.guet_intensity_discreet_detail
    AlertPreferences.Intensity.STANDARD -> R.string.guet_intensity_standard_detail
    AlertPreferences.Intensity.INSISTENT -> R.string.guet_intensity_insistent_detail
}

/**
 * L'écran de réglages et ce qui l'alimente, montés ensemble.
 *
 * L'hôte existe pour la même raison que celui de l'accueil : l'écran ne connaît
 * que des valeurs et des rappels — donc il se vérifie sans Android —, et c'est
 * ici qu'on lit le dépôt et l'inventaire des lignes.
 *
 * L'inventaire est lu **hors du fil principal** et une seule fois : 23 Ko de JSON
 * ne se relisent pas à chaque recomposition d'un écran de réglages.
 */
@Composable
fun GuetSettingsHost(
    store: GuetPreferencesStore,
    networkLines: NetworkLineRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = remember(store) { GuetSettingsModel(store) }
    val lines by produceState(initialValue = emptyList<TransitLine>(), networkLines) {
        value = withContext(Dispatchers.IO) {
            runCatching { networkLines.allLines() }.getOrDefault(emptyList())
        }
    }
    GuetSettingsScreen(
        model = model,
        onClose = onClose,
        lines = lines,
        modifier = modifier,
    )
}

@Composable
private fun <T> GuetExclusiveGroup(
    options: List<T>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
) {
    AuleConnectedButtonGroup(
        options = options,
        selected = selected,
        label = label,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    )
}
