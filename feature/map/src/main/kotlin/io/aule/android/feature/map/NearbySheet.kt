package io.aule.android.feature.map

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.model.DepartureRow
import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.NearbyDigest
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.repository.StopRepository
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

    // Les rangées de ce volet vont d'un bord à l'autre : la gouttière est portée
    // par chaque cartouche, pas par le corps.
    SheetBody(modifier = modifier, gutters = false) {
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
                        rank = index,
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
                vehicles.forEachIndexed { index, entry ->
                    NearbyVehicleCard(
                        entry = entry,
                        lineColor = linePalette.colorOf(entry.vehicle.lineId),
                        rank = index,
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
                // Le cran d'un sous-titre, celui que `SheetHeading` donne déjà
                // aux siens : à quatorze points sous un titre qui en fait
                // seize, le compte se lisait aussi fort que ce qu'il précise.
                style = MaterialTheme.typography.labelMedium,
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
 * ## L'arrêt de tête, et ce qu'il a cessé d'être
 *
 * Il prenait `primaryContainer` : un aplat pastel pleine largeur. Sur
 * l'ancienne palette, ce conteneur valait `#B8F0F6` — un cyan de piscine — et
 * c'est précisément lui qu'on désignait en disant que « les couleurs font mal
 * aux yeux ». Le diagnostic était juste, mais la cause n'était pas la teinte :
 * c'était le **rôle**. Un pastel clair posé en grand aplat au milieu de cartes
 * grises attire l'œil sans rien porter ; il crie sans parler.
 *
 * L'arrêt de tête prend maintenant la surface de marque : le dégradé teal
 * profond, l'encre claire, l'ombre teintée. Le même besoin — désigner une
 * recommandation — servi par une surface qui **pèse** au lieu d'une surface qui
 * éclaire. C'est aussi ce que fait n'importe quelle application où l'on choisit
 * dans une liste, et pour la même raison : ce qu'on recommande doit avoir l'air
 * plus **dense** que le reste, pas plus lumineux.
 *
 * C'est la seule surface de marque de ce volet. La deuxième la banaliserait.
 *
 * @param rank le rang dans la liste, qui porte la cascade d'apparition.
 */
@Composable
private fun NearbyStopCard(
    entry: NearbyDigest.StopEntry,
    detail: NearbyStopDetail?,
    now: Instant,
    isClosest: Boolean,
    rank: Int,
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

    // L'encre secondaire de la carte de tête n'est pas un rôle du thème : sur le
    // dégradé de marque, `onSurfaceVariant` écrirait en gris sombre sur du teal
    // sombre. C'est l'encre de l'accent, voilée, qui joue ce rôle-là — le même
    // rapport de lecture, transposé sur l'autre fond.
    val secondaryInk = if (isClosest) {
        AuleTheme.tokens.onAccent.color.copy(alpha = AuleAlpha.VEIL)
    } else {
        colors.onSurfaceVariant
    }

    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = AuleSpacing.lg)
        .auleEnter(index = rank)
        .semantics(mergeDescendants = true) {
            contentDescription = label
            onClick(label = hint, action = null)
        }

    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(AuleSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            ModeAvatar(mode = entry.stop.mode, onBrand = isClosest)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.stop.departuresKey,
                        // Le nom d'arrêt est la réponse à « où » : c'est le seul
                        // mot de la carte qu'on lit avant tous les autres.
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = distance,
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryInk,
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
                        color = secondaryInk,
                    )
                    if (isClosest) {
                        ClosestTag(text = closest)
                    }
                }
                when {
                    // Les passages portent déjà leur ligne : ajouter la rangée
                    // des lignes desservies redirait la même chose deux fois,
                    // sur une carte qui doit rester en retrait de l'écran.
                    rows.isNotEmpty() -> DepartureStrip(rows = rows, onBrand = isClosest)
                    servingLines.isNotEmpty() -> ServingStrip(lines = servingLines)
                    quiet != null -> Text(
                        text = quiet,
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryInk,
                    )
                }
            }
        }
    }

    if (isClosest) {
        AuleBrandSurface(
            modifier = cardModifier,
            shape = MaterialTheme.shapes.medium,
            // `RESTING` et non `FLOATING` : la carte est posée dans un volet qui
            // porte déjà son ombre. Une ombre haute ferait flotter une surface
            // au-dessus d'une surface, et l'œil ne saurait plus laquelle est le
            // support de l'autre.
            elevation = AuleElevation.RESTING,
            onClick = onSelect,
        ) {
            body()
        }
    } else {
        Card(
            onClick = onSelect,
            modifier = cardModifier,
            colors = CardDefaults.cardColors(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
            ),
        ) {
            body()
        }
    }
}

/**
 * L'étiquette de l'arrêt recommandé — un fait, pas une décoration.
 *
 * Elle **s'inverse**. Elle prenait l'aplat de marque sur un fond pastel ; la
 * carte ayant pris cet aplat, la même étiquette y disparaîtrait. Elle passe
 * donc à l'encre de l'accent en fond et à l'accent en texte : le rapport de
 * contraste est le même, la hiérarchie aussi, seul le sens de lecture a changé.
 */
@Composable
private fun ClosestTag(text: String) {
    val tokens = AuleTheme.tokens
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = tokens.onAccent.color,
        contentColor = tokens.accent.color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmallEmphasized,
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
private fun DepartureStrip(rows: List<DepartureRow>, onBrand: Boolean) {
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
                    // Le point suit la carte : sur le dégradé de marque, son
                    // gris de repli tombe sous le contraste d'un signe visible.
                    // C'est l'anneau, et non plus la couleur, qui dit alors
                    // « théorique ».
                    onBrand = onBrand,
                )
                Text(
                    text = row.nextWait?.label().orEmpty(),
                    // L'attente est le chiffre qu'on lit en dernier et qui
                    // décide de tout : elle prend le slot appuyé.
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = when {
                        // Sur le dégradé de marque, le vert du temps réel perd
                        // son contraste et sa signification : l'information
                        // « c'est mesuré » y est déjà portée par le point qui
                        // pulse juste à côté.
                        onBrand -> AuleTheme.tokens.onAccent.color
                        row.isRealtime -> realtimeInk()
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun NearbyVehicleCard(
    entry: NearbyDigest.VehicleEntry,
    lineColor: String?,
    rank: Int,
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
            .auleEnter(index = rank)
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
            modifier = Modifier.padding(AuleSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
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
                    style = MaterialTheme.typography.titleSmallEmphasized,
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
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

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

/** L'étiquette respire moins que le reste : c'est ce qui la fait lire comme telle. */
private val TAG_PADDING = 3.dp
