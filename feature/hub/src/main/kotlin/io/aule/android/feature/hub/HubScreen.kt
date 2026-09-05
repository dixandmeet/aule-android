package io.aule.android.feature.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.HubChannel
import io.aule.android.core.model.HubChannelKind
import io.aule.android.core.model.HubSection

/** L'avatar rond d'une discussion. Nommé, parce que la garde refuse un `.dp` nu. */
private val AVATAR = 40.dp

/**
 * La liste des discussions.
 *
 * ## Ce qui décide de l'ordre
 *
 * Les favoris d'abord, les canaux automatiques en dernier. Ils sont toujours
 * là — Réseau et Dépôt ne se quittent pas — et les laisser en tête reléguerait
 * les conversations vivantes sous deux lignes immuables.
 *
 * ⚠️ **Un canal automatique vide ne se montre pas** à qui ne peut pas y écrire.
 * Un conducteur dont le dépôt n'a rien publié verrait sinon deux lignes muettes
 * en permanence, et apprendrait à ne plus regarder cette partie de la liste.
 */
@Composable
fun HubChannelList(
    state: HubUiState,
    onOpen: (HubChannel) -> Unit,
    onCreateGroup: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibles = state.channels.filter { !it.isArchived && it.isVisible(state.isStaff) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AuleSpacing.lg),
        ) {
            Text(
                text = stringResource(R.string.hub_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = AuleGlyph.SWAP.asImageVector(),
                    contentDescription = stringResource(R.string.hub_refresh),
                )
            }
            IconButton(onClick = onCreateGroup) {
                Icon(
                    imageVector = AuleGlyph.EDIT.asImageVector(),
                    contentDescription = stringResource(R.string.hub_new_group),
                )
            }
        }

        // ⚠️ **Une liste périmée le dit.** Sans ce bandeau, un agent sorti d'un
        // tunnel lirait un écran d'il y a une heure en le croyant à jour.
        if (state.isStale && visibles.isNotEmpty()) {
            HubNotice(text = stringResource(R.string.hub_stale), onAction = onRefresh)
        }

        // ⚠️ **Ce qui attend le réseau se montre.** Un message muet dans une file
        // que rien ne montre est pire qu'un échec affiché : personne ne le
        // renvoie, et son auteur croit avoir écrit.
        if (state.pending.isNotEmpty()) {
            HubNotice(text = stringResource(R.string.hub_pending, state.pending.size))
        }

        if (visibles.isEmpty()) {
            AuleEmptyState(
                title = stringResource(R.string.hub_empty_title),
                detail = stringResource(R.string.hub_empty_detail),
                modifier = Modifier.fillMaxWidth().padding(AuleSpacing.lg),
            )
            return@Column
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = AuleSpacing.lg, end = AuleSpacing.lg, bottom = AuleSpacing.xxl,
            ),
        ) {
            HubSection.entries.forEach { section ->
                val dedans = visibles.filter { it.section == section }
                if (dedans.isEmpty()) return@forEach
                item(key = "section-${section.name}") {
                    Text(
                        text = section.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            top = AuleSpacing.lg, bottom = AuleSpacing.xs,
                        ),
                    )
                }
                items(dedans, key = { it.id }) { channel ->
                    HubChannelRow(channel = channel, onOpen = { onOpen(channel) })
                    if (channel.id != dedans.last().id) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HubNotice(text: String, onAction: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.sm)
            .then(if (onAction == null) Modifier else Modifier.clickable(onClick = onAction)),
    ) {
        Icon(
            imageVector = AuleGlyph.FLAG.asImageVector(),
            contentDescription = null,
            modifier = Modifier.size(AuleSpacing.lg),
        )
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HubChannelRow(channel: HubChannel, onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            // La cible tactile est celle du design system, jamais une mesure
            // écrite à la main : la garde refuserait un `.dp` nu, et le doigt
            // refuserait moins de 48.
            .heightIn(min = AuleTouch.minimum)
            .clickable(onClick = onOpen)
            .padding(vertical = AuleSpacing.sm),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AVATAR)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Icon(
                imageVector = channel.glyph().asImageVector(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.name,
                    style = if (channel.unreadCount > 0) {
                        MaterialTheme.typography.titleSmallEmphasized
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (channel.isMuted) {
                    Icon(
                        imageVector = AuleGlyph.EYE_OFF.asImageVector(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AuleSpacing.lg),
                    )
                }
            }
            Text(
                text = channel.subtitle(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (channel.unreadCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .sizeIn(minWidth = AuleSpacing.xl, minHeight = AuleSpacing.xl)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    text = channel.unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = AuleSpacing.xs),
                )
            }
        }
    }
}

/**
 * Le glyphe d'un canal.
 *
 * Il vit ici et non sur le modèle : `HubChannelKind` dit **ce qui est**, la vue
 * choisit le dessin — même partage que `SavedPlaceIcon.asImageVector`.
 */
internal fun HubChannel.glyph(): AuleGlyph = when (kind) {
    HubChannelKind.DIRECT -> AuleGlyph.PERSON
    HubChannelKind.NETWORK -> AuleGlyph.TRAM
    HubChannelKind.DEPOT -> AuleGlyph.PIN
    HubChannelKind.SUPPORT -> AuleGlyph.SHIELD
    HubChannelKind.GROUP, HubChannelKind.AUTRE -> AuleGlyph.MAIL
}

/** Le tourniquet d'un premier chargement, quand il n'y a rien à montrer. */
@Composable
internal fun HubLoading(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
        CircularProgressIndicator(
            modifier = Modifier.padding(AuleSpacing.xxl),
        )
    }
}
