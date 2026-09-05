package io.aule.android.feature.hub

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.token.AuleSpacing

/**
 * L'écran de la messagerie : la liste, et la conversation qui la recouvre.
 *
 * Une seule vue, pas une pile de navigation : l'application n'en a pas, et
 * l'écran se pose **sur** la carte sans la démonter — même règle que le profil
 * et la prise de service.
 *
 * ⚠️ Aucun `@Composable` ne lit le réseau : cette vue ne connaît que le
 * ViewModel, et `:feature:hub` ne dépend pas de `:data`. Ce n'est pas une règle
 * de revue, c'est une erreur de compilation.
 */
@Composable
fun HubScreen(
    viewModel: HubViewModel,
    senderLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ouvert = state.openChannel

    // La cadence suit l'écran : le poller relit la portée à chaque tour, et
    // ouvrir une conversation la fait passer de quinze à dix secondes.
    LaunchedEffect(Unit) { viewModel.startPolling() }

    Box(modifier = modifier.fillMaxSize()) {
        if (ouvert == null) {
            HubChannelList(
                state = state,
                onOpen = { viewModel.open(it.id) },
                onCreateGroup = onClose,
                onRefresh = viewModel::refresh,
            )
        } else {
            HubConversation(
                channel = ouvert,
                messages = state.messages,
                isLoading = state.isLoading,
                onSend = { viewModel.send(it, ouvert.id, senderLabel) },
                onBack = viewModel::closeConversation,
                onRetry = viewModel::retry,
                onDiscard = viewModel::discard,
                onReact = { viewModel.toggleReaction(it, REACTION_PAR_DEFAUT, ouvert.id) },
            )
        }

        val refus = state.failure
        if (refus != null) {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(AuleSpacing.lg),
            ) {
                Text(text = refus.label(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * La réaction que le geste long pose.
 *
 * Une seule en v1 : un sélecteur d'emoji demande un clavier système, et le
 * besoin exprimé était « dire que j'ai lu », pas « nuancer ».
 */
private const val REACTION_PAR_DEFAUT = "👍"
