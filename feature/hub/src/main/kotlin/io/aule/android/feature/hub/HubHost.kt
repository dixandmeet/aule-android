package io.aule.android.feature.hub

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.token.AuleSpacing
import kotlin.coroutines.cancellation.CancellationException

/**
 * L'écran de la messagerie : la liste, et la conversation qui la recouvre.
 *
 * Une seule vue, pas une pile de navigation : l'application n'en a pas, et
 * l'écran se pose **sur** la carte sans la démonter — même règle que le profil
 * et la prise de service.
 *
 * ⚠️ **Se poser sur la carte se paie, et rien ne le rappelle.** La carte reste
 * composée dessous — c'est voulu, la démonter coûterait un rechargement de
 * style au retour — donc cet écran doit porter son thème, peindre son fond,
 * écarter les barres système et prendre le geste de retour. Les quatre
 * manquaient : la messagerie s'affichait en transparence sur les toits, en
 * lilas Material, titre sous l'heure, et le retour fermait un volet de carte
 * invisible.
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
    val repertoire = state.directory.isOpen
    val titre = when {
        ouvert != null -> ouvert.name
        repertoire -> stringResource(R.string.hub_directory)
        else -> stringResource(R.string.hub_title)
    }

    // La cadence suit l'écran : le poller relit la portée à chaque tour, et
    // ouvrir une conversation la fait passer de quinze à dix secondes.
    LaunchedEffect(Unit) { viewModel.startPolling() }

    // Le retour appartient à l'écran du dessus tant qu'il est là. Sans ce
    // gestionnaire, le geste redescendait à la carte — toujours composée
    // dessous — et fermait un volet invisible au lieu de la messagerie.
    // ⚠️ L'ordre des trois cas suit celui de l'empilement à l'écran, et non
    // celui du modèle : une conversation ouverte **depuis** le répertoire
    // recouvre les deux, et le retour doit défaire ce qu'on voit — pas ce qui
    // reste vrai dessous.
    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            when {
                ouvert != null -> viewModel.closeConversation()
                repertoire -> viewModel.closeDirectory()
                else -> onClose()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    // ⚠️ **Le thème s'applique par écran, pas une fois pour toutes.** `AuleRoot`
    // n'enveloppe que sa branche de démarrage ; chaque écran porte `AuleTheme`.
    // Sans lui, `MaterialTheme.colorScheme` rend le jeu **par défaut** de
    // Material — et la messagerie s'est peinte en `#FEF7FF`, le lilas de
    // baseline, au milieu d'une application teal. Un fond opaque ne suffit pas :
    // encore faut-il que ce soit le bon.
    AuleTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                // L'aplat de surface d'abord : sans lui, MapLibre continue de
                // peindre dessous et les bulles se lisent sur des immeubles.
                .background(MaterialTheme.colorScheme.surface)
                // `safeDrawing` et non `systemBars` : il réunit barres, encoche
                // **et clavier**. C'est ce qui fait remonter le composeur à la
                // frappe, sans `imePadding` de plus — l'écart serait consommé deux
                // fois et la barre de saisie flotterait au-dessus du clavier.
                .safeDrawingPadding()
                .semantics {
                    this.paneTitle = titre
                    isTraversalGroup = true
                },
        ) {
            if (ouvert == null && repertoire) {
                HubDirectoryScreen(
                    state = state.directory,
                    isNotDeployed = state.isNotDeployed,
                    onQuery = viewModel::searchDirectory,
                    onOpen = { collegue ->
                        // `userId` est non nul dès que la rangée est ouvrable :
                        // `isContactable` exige un compte. Le vérifier ici
                        // encore, c'est refuser d'ouvrir plutôt que de planter
                        // si les deux règles divergeaient un jour.
                        collegue.userId?.let(viewModel::openDirectWith)
                    },
                    onLoadMore = viewModel::loadMoreDirectory,
                    onReachable = viewModel::setContactPreference,
                    onBack = viewModel::closeDirectory,
                )
            } else if (ouvert == null) {
                HubChannelList(
                    state = state,
                    onOpen = { viewModel.open(it.id) },
                    onClose = onClose,
                    onRefresh = viewModel::refresh,
                    onDirectory = viewModel::openDirectory,
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
}

/**
 * La réaction que le geste long pose.
 *
 * Une seule en v1 : un sélecteur d'emoji demande un clavier système, et le
 * besoin exprimé était « dire que j'ai lu », pas « nuancer ».
 */
private const val REACTION_PAR_DEFAUT = "👍"
