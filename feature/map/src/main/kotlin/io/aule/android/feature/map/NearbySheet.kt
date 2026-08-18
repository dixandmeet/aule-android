package io.aule.android.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.DepartureRow
import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.NearbyDigest
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.repository.StopRepository
import java.text.DecimalFormatSymbols
import java.time.Instant
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * « Autour de vous » — le chemin d'accès à la carte quand on ne la voit pas.
 *
 * La carte MapLibre est un tampon opaque : TalkBack n'y trouve rien, et la
 * sélection passe par un hit-test de 22 dp qui suppose qu'on sait déjà où
 * poser le doigt. Cette liste est la réponse, et elle répond à la vraie
 * question — « qu'est-ce qu'il y a autour de moi ? ».
 *
 * ## L'ordre de lecture
 *
 * Une liste d'arrêts qui ne donne que des noms et des distances laisse la
 * décision entière à l'usager : il lui reste à ouvrir chaque fiche pour savoir
 * laquelle sert. Les cartes répondent donc dans l'ordre où les questions se
 * posent — **où**, *combien de temps pour y aller*, **quelle ligne**, *quand* —
 * et le premier arrêt se distingue, parce qu'un choix où tout pèse pareil n'est
 * pas un choix.
 */
@Composable
internal fun NearbySheet(
    digest: NearbyDigest,
    linePalette: LinePalette,
    repository: StopRepository,
    dispatchers: AuleDispatchers,
    onSelectStop: (TransitStop) -> Unit,
    onSelectVehicle: (TransportVehicle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = remember(repository, dispatchers) { NearbyStopsModel(repository, dispatchers) }
    DisposableEffect(model) { onDispose { model.close() } }

    // Le volet bat comme celui d'un arrêt : sans cette horloge, « 3 min »
    // resterait « 3 min » aussi longtemps que la liste reste ouverte.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(NEARBY_TICK_MS)
            now = Instant.now()
        }
    }

    val modes = remember(digest) {
        (digest.stops.map { it.stop.mode } + digest.vehicles.map { it.vehicle.mode })
            .distinct()
            .sorted()
    }
    var filter by rememberSaveable { mutableStateOf<TransportMode?>(null) }
    // Un filtre dont le mode a disparu de la zone laisserait un volet vide sans
    // rien qui explique pourquoi. On retombe sur « Tous ».
    LaunchedEffect(modes) { if (filter != null && filter !in modes) filter = null }

    val stops = digest.stops.filter { filter == null || it.stop.mode == filter }
    val vehicles = digest.vehicles.filter { filter == null || it.vehicle.mode == filter }

    val watched = stops.take(NEARBY_DETAIL_LIMIT).map { it.stop.departuresKey }
    LaunchedEffect(watched) { model.watch(watched) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        NearbyHeader(stopCount = stops.size, farthestMeters = stops.lastOrNull()?.distanceMeters)

        if (modes.size > 1) {
            ModeFilterRow(modes = modes, selected = filter, onSelect = { filter = it })
        }

        if (digest.isEmpty) {
            AuleEmptyState(
                title = stringResource(R.string.nearby_empty_title),
                detail = stringResource(R.string.nearby_empty_detail),
                modifier = Modifier.padding(horizontal = AuleSpacing.lg),
            )
        } else if (stops.isEmpty() && vehicles.isEmpty()) {
            AuleEmptyState(
                title = stringResource(R.string.nearby_filtered_empty_title),
                detail = stringResource(R.string.nearby_filtered_empty_detail),
                modifier = Modifier.padding(horizontal = AuleSpacing.lg),
            )
        }

        if (stops.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
                SheetSectionLabel(
                        text = stringResource(R.string.nearby_section_stops),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                stops.forEachIndexed { index, entry ->
                    NearbyStopCard(
                        entry = entry,
                        detail = model.details[entry.stop.departuresKey],
                        now = now,
                        // « Le plus proche » n'apprend rien quand il n'y a
                        // qu'un arrêt : il l'est forcément.
                        isClosest = index == 0 && stops.size > 1,
                        onSelect = { onSelectStop(entry.stop) },
                    )
                }
            }
        }

        if (vehicles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
                SheetSectionLabel(
                        text = stringResource(R.string.nearby_section_vehicles),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                vehicles.forEach { entry ->
                    NearbyVehicleCard(
                        entry = entry,
                        lineColor = linePalette.colorOf(entry.vehicle.lineId),
                        onSelect = { onSelectVehicle(entry.vehicle) },
                    )
                }
            }
        }
    }
}

/**
 * Le titre, et ce que la liste couvre.
 *
 * Le sous-titre est ce qui manquait le plus : sans borne, « Autour de vous »
 * ne dit pas si la liste ratisse cent mètres ou deux kilomètres.
 */
@Composable
private fun NearbyHeader(stopCount: Int, farthestMeters: Double?) {
    Column(
        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
    ) {
        SheetTitle(stringResource(R.string.nearby_title))
        if (stopCount > 0 && farthestMeters != null) {
            val bound = formatDistance(roundedBound(farthestMeters))
            Text(
                text = if (stopCount == 1) {
                    stringResource(R.string.nearby_summary_one, bound)
                } else {
                    stringResource(R.string.nearby_summary_many, stopCount, bound)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeFilterRow(
    modes: List<TransportMode>,
    selected: TransportMode?,
    onSelect: (TransportMode?) -> Unit,
) {
    // Pas de `contentDescription` sur la rangée : posée sur un parent dont les
    // enfants sont eux-mêmes actionnables, elle brouille l'arbre au lieu de
    // l'éclairer. Chaque puce porte déjà son nom et son état sélectionné.
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.lg)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.nearby_filter_all)) },
        )
        modes.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(if (selected == mode) null else mode) },
                label = { Text(mode.label()) },
            )
        }
    }
}

/**
 * Un arrêt, et de quoi décider sans l'ouvrir.
 *
 * L'arrêt de tête prend l'aplat de marque : c'est le seul relief de la liste,
 * et il porte la seule recommandation qu'on puisse faire sans connaître la
 * destination — celui vers lequel on marchera le moins.
 */
@Composable
private fun NearbyStopCard(
    entry: NearbyDigest.StopEntry,
    detail: NearbyStopDetail?,
    now: Instant,
    isClosest: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val distance = formatDistance(entry.distanceMeters)
    val walk = stringResource(R.string.nearby_walk, entry.walkMinutes)
    val modeLabel = entry.stop.mode.label()
    val closest = stringResource(R.string.nearby_closest)
    val atDistance = stringResource(R.string.nearby_at_distance, distance)
    val wheelchair = stringResource(R.string.nearby_wheelchair)
    val hint = stringResource(R.string.nearby_hint_stop)

    // Une ligne, une attente. `grouped` sépare les destinations : sans ce
    // dédoublonnage, un arrêt affiche « C6 · 19 min » puis « C6 · 22 min »,
    // deux rangées qui ne se distinguent que par une destination que la carte
    // n'a pas la place d'écrire. La place gagnée va à la ligne suivante.
    val rows = detail?.departures?.grouped(from = now, maxPerRow = 1)
        ?.distinctBy { it.line }
        ?.take(NEARBY_DEPARTURE_COUNT)
        .orEmpty()
    // Les lignes desservies gardent leur objet plutôt que leur seul nom : la
    // couleur est déjà là, et un badge gris à côté d'un badge colorié se lit
    // comme une ligne à part.
    val servingLines = detail?.servingLines
        ?.distinctBy { it.line }
        ?.take(NEARBY_LINE_COUNT)
        .orEmpty()
    // `title()` est vide pour ANNOUNCED : sans rien à annoncer, on n'écrit rien.
    val quiet = if (rows.isEmpty()) {
        detail?.departures?.outcome?.title()?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    val waits = rows.map { row -> row.line to row.nextWait?.label().orEmpty() }

    // TalkBack lit la carte d'un trait : quatre nœuds séparés feraient quatre
    // arrêts sur une seule entrée de liste.
    val label = buildString {
        if (isClosest) {
            append(closest)
            append(", ")
        }
        append(entry.stop.departuresKey)
        append(", ")
        append(modeLabel)
        append(", ")
        append(atDistance)
        append(", ")
        append(walk)
        if (entry.stop.isWheelchairAccessible) {
            append(", ")
            append(wheelchair)
        }
        waits.forEach { (line, wait) ->
            append(", ")
            append(line)
            append(" ")
            append(wait)
        }
        if (rows.isEmpty() && servingLines.isNotEmpty()) {
            append(", ")
            append(servingLines.joinToString(", ") { it.line })
        }
        quiet?.let {
            append(", ")
            append(it)
        }
    }

    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuleSpacing.lg)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                onClick(label = hint, action = null)
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isClosest) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if (isClosest) colors.onPrimaryContainer else colors.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(AuleSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            ModeAvatar(mode = entry.stop.mode)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.stop.departuresKey,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = distance,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isClosest) {
                            colors.onPrimaryContainer
                        } else {
                            colors.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = AuleSpacing.sm),
                    )
                }
                // L'étiquette voyage sur la ligne du temps de marche : seule,
                // elle coûtait une rangée entière, et c'est exactement celle
                // qui poussait les passages hors du cran partiel du volet.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$walk · $modeLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isClosest) {
                            colors.onPrimaryContainer
                        } else {
                            colors.onSurfaceVariant
                        },
                    )
                    if (isClosest) {
                        ClosestTag(text = closest)
                    }
                }
                when {
                    // Les passages portent déjà leur ligne : ajouter la rangée
                    // des lignes desservies redirait la même chose deux fois,
                    // sur une carte qui doit rester en retrait de l'écran.
                    rows.isNotEmpty() -> DepartureStrip(rows = rows, muted = isClosest)
                    servingLines.isNotEmpty() -> ServingStrip(lines = servingLines)
                    quiet != null -> Text(
                        text = quiet,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isClosest) {
                            colors.onPrimaryContainer
                        } else {
                            colors.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** L'étiquette de l'arrêt recommandé — un fait, pas une décoration. */
@Composable
private fun ClosestTag(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = AuleSpacing.sm, vertical = TAG_PADDING),
        )
    }
}

/**
 * Les prochains passages : une ligne, son attente.
 *
 * Une suite de minutes nues — « 2 · 7 · 14 » — ne dit pas de quoi elle parle
 * dès qu'un arrêt est desservi par deux lignes, et c'est le cas courant.
 */
@Composable
private fun DepartureStrip(rows: List<DepartureRow>, muted: Boolean) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(top = AuleSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineBadge(
                    line = row.line,
                    colorHex = row.lineColor,
                    contentDescription = stringResource(R.string.line_badge, row.line),
                )
                RealtimeDot(
                    isLive = row.isRealtime,
                    liveDescription = stringResource(R.string.stop_realtime),
                    scheduledDescription = stringResource(R.string.stop_scheduled),
                )
                Text(
                    text = row.nextWait?.label().orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        muted -> MaterialTheme.colorScheme.onPrimaryContainer
                        row.isRealtime -> realtimeInk()
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/**
 * Les lignes desservies, quand rien ne passe.
 *
 * La nuit, un arrêt n'annonce aucun passage mais dessert toujours les mêmes
 * lignes : c'est cette information-là qui reste utile.
 */
@Composable
private fun ServingStrip(lines: List<ServingLine>) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(top = AuleSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lines.forEach { serving ->
            LineBadge(
                line = serving.line,
                colorHex = serving.lineColor,
                contentDescription = stringResource(R.string.line_badge, serving.line),
            )
        }
    }
}

@Composable
private fun NearbyVehicleCard(
    entry: NearbyDigest.VehicleEntry,
    lineColor: String?,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val vehicle = entry.vehicle
    val distance = formatDistance(entry.distanceMeters)
    val modeLabel = vehicle.mode.label()
    val destination = vehicle.destination
        ?: stringResource(R.string.vehicle_unknown_destination)
    val towards = vehicle.destination?.let { stringResource(R.string.nearby_towards, it) }
    val atDistance = stringResource(R.string.nearby_at_distance, distance)
    val feedLabel = stringResource(
        if (vehicle.isLive) R.string.nearby_live else R.string.nearby_estimated,
    )
    val hint = stringResource(R.string.nearby_hint_vehicle)
    val label = buildString {
        append(modeLabel)
        append(" ")
        append(vehicle.lineName)
        if (towards != null) {
            append(", ")
            append(towards)
        }
        append(", ")
        append(atDistance)
        append(", ")
        append(feedLabel)
    }

    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuleSpacing.lg)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                onClick(label = hint, action = null)
            },
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(AuleSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineBadge(
                line = vehicle.lineName,
                colorHex = lineColor,
                contentDescription = stringResource(R.string.line_badge, vehicle.lineName),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                Text(
                    text = destination,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RealtimeDot(
                        isLive = vehicle.isLive,
                        liveDescription = stringResource(R.string.vehicle_live),
                        scheduledDescription = stringResource(R.string.vehicle_estimated),
                    )
                    Text(
                        text = feedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = distance,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Le mode, dans sa teinte de carte.
 *
 * La même que celle du marqueur : reconnaître un arrêt de tram à sa couleur
 * dans la liste puis sur la carte est ce qui relie les deux vues.
 */
@Composable
private fun ModeAvatar(mode: TransportMode) {
    val tint = mode.markerColor(AuleTheme.night).color
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .background(color = tint.copy(alpha = AuleAlpha.TINT), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = mode.icon(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(AVATAR_ICON_SIZE),
        )
    }
}

private fun TransportMode.icon(): ImageVector = when (this) {
    TransportMode.BUS -> AuleGlyph.BUS.asImageVector()
    TransportMode.TRAM -> AuleGlyph.TRAM.asImageVector()
    TransportMode.BOAT -> Icons.Outlined.DirectionsBoat
}

@Composable
private fun formatDistance(meters: Double): String =
    GeoMath.formatDistance(meters, DecimalFormatSymbols.getInstance().decimalSeparator)

/**
 * Une borne ronde.
 *
 * « À moins de 500 m » se retient ; « à moins de 450 m » donne une précision
 * que la mesure n'a pas, sur une distance à vol d'oiseau.
 */
private fun roundedBound(meters: Double): Double = when {
    meters <= BOUND_FLOOR -> BOUND_FLOOR
    meters <= BOUND_STEP_LIMIT -> ceil(meters / BOUND_STEP) * BOUND_STEP
    else -> ceil(meters / BOUND_COARSE_STEP) * BOUND_COARSE_STEP
}

/** En deçà, on ne prétend pas mesurer plus fin. */
private const val BOUND_FLOOR = 100.0

/** Jusqu'au kilomètre, on arrondit à la centaine ; au-delà, au demi-kilomètre. */
private const val BOUND_STEP_LIMIT = 1000.0
private const val BOUND_STEP = 100.0
private const val BOUND_COARSE_STEP = 500.0

/** Assez pour que « 3 min » devienne « 2 min » sans qu'on l'ait vu vieillir. */
private const val NEARBY_TICK_MS = 10_000L

/** Trois attentes suffisent à décider ; la quatrième déborde de la carte. */
private const val NEARBY_DEPARTURE_COUNT = 3

/** Au-delà, la rangée de badges devient une liste qu'il faut lire. */
private const val NEARBY_LINE_COUNT = 6

/** La pastille du mode : un cran de plus que l'icône, pour qu'elle respire. */
private val AVATAR_SIZE = 40.dp
private val AVATAR_ICON_SIZE = 22.dp

/** L'étiquette respire moins que le reste : c'est ce qui la fait lire comme telle. */
private val TAG_PADDING = 3.dp
