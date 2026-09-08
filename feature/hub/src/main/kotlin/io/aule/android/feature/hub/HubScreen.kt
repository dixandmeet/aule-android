package io.aule.android.feature.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubChannelList(
    state: HubUiState,
    onOpen: (HubChannel) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onDirectory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibles = state.channels.filter { !it.isArchived && it.isVisible(state.isStaff) }
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxSize()) {
        // La barre du kit, comme le profil et le mode Guet, et non une `Row`
        // nue : elle porte la hauteur, l'alignement de la flèche et le titre au
        // même endroit que les autres écrans. `windowInsets` à zéro parce que
        // l'écran a déjà écarté les barres système une fois, dans [HubScreen].
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.hub_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = AuleGlyph.BACK.asImageVector(),
                        contentDescription = stringResource(R.string.hub_close),
                    )
                }
            },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = AuleGlyph.SWAP.asImageVector(),
                        contentDescription = stringResource(R.string.hub_refresh),
                    )
                }
                // Le répertoire, et non la création de groupe — qui n'existe
                // toujours pas ici (voir `Docs/PLAN-MESSAGERIE.md`). C'est le
                // seul chemin vers une première conversation : sans lui, un
                // agent dont personne ne s'est encore approché n'a aucun moyen
                // d'écrire à qui que ce soit.
                IconButton(onClick = onDirectory) {
                    Icon(
                        imageVector = AuleGlyph.PERSON.asImageVector(),
                        contentDescription = stringResource(R.string.hub_directory_open),
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.surface,
                titleContentColor = colors.onSurface,
                navigationIconContentColor = colors.onSurface,
                actionIconContentColor = colors.onSurface,
            ),
        )

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
            // Le médaillon et le centrage, parce que c'est une **page** et non
            // une section de volet : la variante calée en haut à gauche —
            // celle des volets de la carte — laissait deux lignes suspendues
            // au-dessus d'une page blanche, qui se lit comme un chargement
            // inachevé plutôt que comme une réponse.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                val refus = state.channelsFailure
                when {
                    state.isLoadingChannels -> HubLoading()
                    // ⚠️ **« Aucune discussion » est une affirmation.** Quand
                    // le chargement a échoué, l'écran ne sait rien : la liste
                    // est vide parce qu'elle n'est jamais arrivée. L'écrire
                    // « aucune discussion » ferait passer un serveur muet — ou
                    // une messagerie pas encore ouverte — pour un compte neuf,
                    // et le conducteur attendrait des messages qui ne peuvent
                    // pas venir. Le refus se dit, avec ses mots à lui.
                    refus != null -> AuleEmptyState(
                        title = stringResource(R.string.hub_unreachable_title),
                        detail = refus.label(),
                        icon = AuleGlyph.FLAG.asImageVector(),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                    // La messagerie absente du serveur se dit aussi : la
                    // lecture, elle, a répondu « rien » sans pouvoir faire la
                    // différence — c'est l'amorçage qui l'a apprise.
                    state.isNotDeployed -> AuleEmptyState(
                        title = stringResource(R.string.hub_unavailable_title),
                        detail = stringResource(R.string.hub_error_not_deployed),
                        icon = AuleGlyph.FLAG.asImageVector(),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                    else -> AuleEmptyState(
                        title = stringResource(R.string.hub_empty_title),
                        detail = stringResource(R.string.hub_empty_detail),
                        icon = AuleGlyph.MAIL.asImageVector(),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                }
            }
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
