package io.aule.android.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.TransportBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.component.delayInk
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.VehicleLoad
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Le panneau d'un véhicule : sa ligne, où il va, ce qu'il dessert ensuite.
 *
 * Court par construction. Quand on touche un bus sur la carte, on veut savoir
 * en trois secondes si c'est le bon — pas ouvrir un dossier.
 *
 * ## Ce qui vient après le nom
 *
 * Une fiche qui ne donne que la ligne et la destination laisse sans réponse
 * les deux questions qu'on se pose vraiment devant un véhicule qu'on voit
 * bouger : *est-ce que la position que je regarde est encore vraie*, et
 * *est-ce que je vais pouvoir monter dedans*. Le réseau publie de quoi y
 * répondre — l'heure du relevé, la charge, la vitesse, le temps restant à
 * quai — et rien de tout cela n'était affiché.
 *
 * Chaque fait ne s'affiche que si le réseau le publie : une fiche qui montre
 * « Affluence — inconnue » a l'air en panne, alors qu'une fiche qui n'en parle
 * pas a simplement l'air courte.
 */
@Composable
internal fun VehicleDetailSheet(
    vehicle: TransportVehicle,
    lineColor: String?,
    isFollowing: Boolean,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // L'horloge du volet, comme celle d'un arrêt. Sans elle, « il y a 8 s »
    // resterait « il y a 8 s » pendant que la position, elle, vieillit — et
    // c'est précisément l'âge qui dit si on peut encore s'y fier.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(vehicle.id) {
        while (isActive) {
            delay(VEHICLE_TICK_MS)
            now = Instant.now()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.lg),
        // Le volet s'ouvre à mi-hauteur : ce qui déborde de ce cran demande un
        // geste de plus. Les trois blocs et le bouton doivent y tenir, donc on
        // respire d'un cran de moins qu'ailleurs.
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        VehicleIdentity(vehicle = vehicle, lineColor = lineColor, now = now)

        VehicleStatusCard(vehicle = vehicle)

        if (isFollowing) {
            FilledTonalButton(onClick = onFollow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vehicle_unfollow))
            }
        } else {
            Button(
                onClick = onFollow,
                modifier = Modifier.fillMaxWidth(),
                colors = auleAccentButtonColors(),
            ) {
                Text(stringResource(R.string.vehicle_follow))
            }
        }
    }
}

/**
 * Qui il est : la ligne, le mode, la destination, et d'où sort sa position.
 *
 * L'âge du relevé ne s'écrit que pour une position **mesurée** : une position
 * calculée depuis l'horaire n'a pas d'âge de relevé, et lui en donner un
 * laisserait croire qu'un véhicule a été vu là il y a huit secondes.
 */
@Composable
private fun VehicleIdentity(vehicle: TransportVehicle, lineColor: String?, now: Instant) {
    val colors = MaterialTheme.colorScheme
    val feed = stringResource(
        if (vehicle.isLive) R.string.vehicle_live else R.string.vehicle_estimated,
    )
    val age = vehicle.positionAgeSeconds(now)?.takeIf { vehicle.isLive }

    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineBadge(
                line = vehicle.lineName,
                colorHex = lineColor,
                contentDescription = stringResource(R.string.line_badge, vehicle.lineName),
            )
            TransportBadge(
                mode = vehicle.mode,
                label = vehicle.mode.label(),
                tint = vehicle.mode.markerColor(AuleTheme.night).color,
            )
        }
        SheetTitle(vehicle.destination ?: stringResource(R.string.vehicle_unknown_destination))
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
                text = if (age == null) feed else "$feed · ${positionAgeText(age)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Le bloc de faits : ce qu'il dessert ensuite, quand, et dans quel état.
 *
 * Un seul cartouche plutôt que deux. Deux boîtes empilées se disputent le
 * regard ; celle-ci n'en réclame qu'un, et le volet garde un seul point
 * d'ancrage entre le nom et le bouton.
 */
@Composable
private fun VehicleStatusCard(vehicle: TransportVehicle) {
    val hasNextStop = vehicle.nextStop != null || vehicle.etaSeconds != null
    val hasState = vehicle.load != null || vehicle.isStopped || vehicle.speedKmh != null
    if (!hasNextStop && !hasState) return

    SheetCard(modifier = Modifier.fillMaxWidth()) {
        if (hasNextStop) {
            NextStopRow(vehicle)
        }
        if (hasNextStop && hasState) {
            SheetRowDivider()
        }
        if (hasState) {
            VehicleStateRow(vehicle)
        }
    }
}

/**
 * Le prochain arrêt, et dans combien de temps.
 *
 * L'attente est écrite au rôle `DATA` : ses chiffres sont à chasse fixe, donc
 * « 10 min » ne fait pas danser la ligne en devenant « 9 min ». L'encre temps
 * réel ne s'allume que sur une position mesurée — sur un horaire, elle
 * promettrait une exactitude qui n'existe pas.
 *
 * Lue d'un trait par TalkBack : quatre nœuds séparés feraient quatre arrêts de
 * curseur pour une seule phrase, « prochain arrêt Ranzay, arrivée maintenant ».
 */
@Composable
private fun NextStopRow(vehicle: TransportVehicle) {
    val colors = MaterialTheme.colorScheme
    val stopName = vehicle.nextStop
    val stopLabel = stringResource(R.string.vehicle_next_stop)
    val eta = vehicle.etaSeconds
    val arriving = eta != null && eta < 60
    val etaLabel = stringResource(
        if (arriving) R.string.vehicle_eta_arrival else R.string.vehicle_eta_in,
    )
    val etaValue = when {
        eta == null -> null
        arriving -> stringResource(R.string.vehicle_eta_arriving)
        else -> stringResource(R.string.vehicle_eta_minutes, (eta / 60).toInt())
    }
    val spoken = listOfNotNull(
        stopName?.let { "$stopLabel $it" },
        etaValue?.let { "$etaLabel $it" },
    ).joinToString(", ")

    AuleCappedFontScale {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StopGlyph(mode = vehicle.mode)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stopLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = stopName ?: stringResource(R.string.value_unknown),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (etaValue != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = etaLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = etaValue,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        color = if (vehicle.isLive) realtimeInk() else colors.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Dans quel état il roule : le monde à bord, et s'il avance.
 *
 * Une rangée lue d'un seul tenant par TalkBack : « Affluence, bien rempli, à
 * l'arrêt » est une phrase ; trois nœuds séparés sont trois arrêts de curseur
 * pour la même information.
 */
@Composable
private fun VehicleStateRow(vehicle: TransportVehicle) {
    val colors = MaterialTheme.colorScheme
    val load = vehicle.load
    val loadText = load?.label()
    val loadName = stringResource(R.string.vehicle_load)
    // L'arrêt prime sur la vitesse : les deux se lisent sur le même relevé, et
    // « 2 km/h » n'apprend rien que « À l'arrêt » ne dise mieux.
    val motion = when {
        vehicle.isStopped -> stringResource(R.string.vehicle_at_stop)
        else -> vehicle.speedKmh?.let { stringResource(R.string.vehicle_speed, it) }
    }
    val spoken = listOfNotNull(loadText?.let { "$loadName, $it" }, motion).joinToString(", ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (load != null && loadText != null) {
            LoadMeter(load = load)
            Text(
                text = loadText,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (motion != null) {
            if (load != null) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                text = motion,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * La jauge de remplissage : quatre barres qui montent.
 *
 * Un taux nu — « 62 % » — se lit avec la tête ; quatre barres se lisent avec
 * l'œil, et c'est tout ce qu'on demande à un chiffre qu'on regarde une seconde
 * avant de décider de monter. La couleur porte le même verdict que la
 * hauteur : elle passe à l'ambre puis au rouge quand il n'y a plus de place,
 * pour que la jauge reste lisible sans compter les barres.
 */
@Composable
private fun LoadMeter(load: VehicleLoad) {
    val filled = load.ordinal + 1
    val tint = load.ink()
    val idle = MaterialTheme.colorScheme.outlineVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(METER_GAP),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Autant de barres que de paliers : un palier ajouté au modèle se voit
        // dans la jauge sans qu'on y revienne.
        repeat(VehicleLoad.entries.size) { index ->
            Box(
                modifier = Modifier
                    .size(
                        width = METER_BAR_WIDTH,
                        height = METER_BAR_FLOOR + METER_BAR_STEP * index,
                    )
                    .background(
                        color = if (index < filled) tint else idle,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/**
 * L'encre d'un palier de remplissage.
 *
 * Rien de tout cela n'est un rôle Material : « il reste de la place » et « il
 * est complet » sont des faits transport, comme le temps réel et le retard, et
 * ils empruntent les mêmes encres.
 */
@Composable
private fun VehicleLoad.ink(): Color = when (this) {
    VehicleLoad.QUIET, VehicleLoad.STEADY -> realtimeInk()
    VehicleLoad.BUSY -> delayInk()
    VehicleLoad.FULL -> AuleTheme.tokens.alert.color
}

/**
 * La pastille de l'arrêt, dans la teinte du mode.
 *
 * La même que celle du marqueur sur la carte : c'est ce qui relie le véhicule
 * qu'on vient de toucher à la fiche qui s'ouvre.
 */
@Composable
private fun StopGlyph(mode: TransportMode) {
    val tint = mode.markerColor(AuleTheme.night).color
    Box(
        modifier = Modifier
            .size(GLYPH_SLOT)
            .background(color = tint.copy(alpha = AuleAlpha.TINT), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AuleGlyph.PIN.asImageVector(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(GLYPH_SIZE),
        )
    }
}

/** Assez pour que « il y a 8 s » devienne « il y a 18 s » sans qu'on l'ait vu vieillir. */
private const val VEHICLE_TICK_MS = 10_000L

/** La pastille du prochain arrêt : un cran de plus que l'icône, pour qu'elle respire. */
private val GLYPH_SLOT = 40.dp
private val GLYPH_SIZE = 22.dp

/** La plus basse des barres de la jauge, et le cran qui les sépare. */
private val METER_BAR_FLOOR = 7.dp
private val METER_BAR_STEP = 4.dp
private val METER_BAR_WIDTH = 4.dp
private val METER_GAP = 3.dp
