package io.aule.android.feature.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.HubChannel
import io.aule.android.core.model.HubChannelKind
import io.aule.android.core.model.HubDeliveryState
import io.aule.android.core.model.HubMessage

/**
 * Une conversation : les bulles, et de quoi écrire — quand on en a le droit.
 *
 * ⚠️ **Le composeur suit [HubChannel.canWrite], jamais le type de canal.** Un
 * canal Réseau est en lecture seule pour un conducteur et ouvert à un
 * régulateur : décider sur le type cacherait la barre de saisie au second, ou la
 * montrerait au premier — qui serait refusé après avoir tapé son message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubConversation(
    channel: HubChannel,
    messages: List<HubMessage>,
    isLoading: Boolean,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(channel.id) { mutableStateOf("") }
    val defilement = rememberLazyListState()

    // Le dernier message reste sous les yeux : une conversation qui n'accroche
    // pas le bas oblige à faire défiler après chaque envoi.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) defilement.animateScrollToItem(messages.lastIndex)
    }

    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxSize()) {
        // Même barre que la liste, donc même hauteur et même flèche : passer
        // d'un écran à l'autre ne doit pas déplacer le titre de trois points.
        // `windowInsets` à zéro — [HubScreen] a déjà écarté les barres.
        TopAppBar(
            title = {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = AuleGlyph.BACK.asImageVector(),
                        contentDescription = stringResource(R.string.hub_back),
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

        if (isLoading && messages.isEmpty()) {
            HubLoading(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = defilement,
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AuleSpacing.lg),
                modifier = Modifier.weight(1f),
            ) {
                items(messages, key = { it.id }) { message ->
                    HubBubble(
                        message = message,
                        showsSender = channel.kind != HubChannelKind.DIRECT,
                        onRetry = onRetry,
                        onDiscard = onDiscard,
                        onReact = { onReact(message.id) },
                    )
                }
            }
        }

        val blocage = channel.writeBlock()
        if (blocage != null) {
            Text(
                text = blocage,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(AuleSpacing.lg),
            )
        } else {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                modifier = Modifier.fillMaxWidth().padding(AuleSpacing.md),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(stringResource(R.string.hub_message_hint)) },
                    maxLines = 5,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val texte = draft
                        draft = ""
                        onSend(texte)
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.size(AuleTouch.minimum),
                ) {
                    Icon(
                        imageVector = AuleGlyph.CHECK.asImageVector(),
                        contentDescription = stringResource(R.string.hub_send),
                    )
                }
            }
        }
    }
}

/**
 * Une bulle.
 *
 * ⚠️ **L'état de livraison se voit.** Un message en attente dans une file que
 * rien ne montre est pire qu'un échec affiché : personne ne le renvoie, et son
 * auteur croit avoir écrit.
 */
@Composable
private fun HubBubble(
    message: HubMessage,
    showsSender: Boolean,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onReact: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showsSender && message.senderLabel.isNotEmpty()) {
            Text(
                text = message.senderLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        message.replyTo?.let { reponse ->
            Text(
                text = reponse.bodyPreview,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = message.bodyLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (message.isDeleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        message.file?.let { fichier ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                modifier = Modifier.heightIn(min = AuleTouch.minimum),
            ) {
                Icon(
                    imageVector = AuleGlyph.IMAGE.asImageVector(),
                    contentDescription = null,
                    modifier = Modifier.size(AuleSpacing.xl),
                )
                Text(text = fichier.fileName, style = MaterialTheme.typography.labelMedium)
            }
        }

        if (message.reactions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
                message.reactions.forEach { reaction ->
                    // La cible tactile fait 48 : une pastille de 20 serait
                    // invisible au doigt.
                    TextButton(onClick = onReact, modifier = Modifier.heightIn(min = AuleTouch.minimum)) {
                        Text(
                            text = "${reaction.emoji} ${reaction.count}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        val livraison = message.deliveryLabel()
        if (livraison != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            ) {
                Text(
                    text = livraison,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.deliveryState == HubDeliveryState.ECHOUE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                val clientId = message.clientId
                if (message.deliveryState == HubDeliveryState.ECHOUE && clientId != null) {
                    TextButton(onClick = { onRetry(clientId) }) {
                        Text(stringResource(R.string.hub_retry))
                    }
                    TextButton(onClick = { onDiscard(clientId) }) {
                        Text(stringResource(R.string.hub_discard))
                    }
                }
            }
        }
    }
}
