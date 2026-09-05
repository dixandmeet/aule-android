package io.aule.android.feature.hub

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aule.android.core.model.HubChannel
import io.aule.android.core.model.HubChannelKind
import io.aule.android.core.model.HubChannelStatus
import io.aule.android.core.model.HubColleague
import io.aule.android.core.model.HubDeliveryState
import io.aule.android.core.model.HubFailureKind
import io.aule.android.core.model.HubMessage
import io.aule.android.core.model.HubSection

/**
 * Le seul endroit qui relie un modèle de messagerie à une phrase (ADR-011).
 *
 * Un modèle qui contient une phrase française est un modèle qu'il faut rouvrir
 * pour traduire l'application — et rouvrir un modèle pour un mot est exactement
 * ce qui finit par en changer le sens.
 */

@Composable
fun HubSection.label(): String = stringResource(
    when (this) {
        HubSection.FAVORIS -> R.string.hub_section_favorites
        HubSection.GROUPES -> R.string.hub_section_groups
        HubSection.DIRECTS -> R.string.hub_section_directs
        HubSection.DIFFUSION -> R.string.hub_section_broadcast
    },
)

/**
 * Ce qui s'écrit sous le nom d'une discussion.
 *
 * Le dernier message quand il y en a un — c'est ce qui fait reconnaître une
 * conversation sans l'ouvrir. Sinon, ce qu'**est** le canal, et non « aucun
 * message » : dire le vide n'aide personne, dire « Annonces du réseau » explique
 * pourquoi la ligne est là.
 */
@Composable
fun HubChannel.subtitle(): String {
    val dernier = lastMessage
    if (dernier != null && dernier.bodyPreview.isNotEmpty()) {
        return if (kind == HubChannelKind.DIRECT || dernier.senderLabel.isEmpty()) {
            dernier.bodyPreview
        } else {
            stringResource(R.string.hub_subtitle_sender, dernier.senderLabel, dernier.bodyPreview)
        }
    }
    return when (kind) {
        HubChannelKind.NETWORK -> stringResource(R.string.hub_subtitle_network)
        HubChannelKind.DEPOT -> stringResource(R.string.hub_subtitle_depot)
        HubChannelKind.SUPPORT -> stringResource(R.string.hub_subtitle_support)
        HubChannelKind.DIRECT -> stringResource(R.string.hub_subtitle_direct)
        HubChannelKind.GROUP, HubChannelKind.AUTRE ->
            stringResource(R.string.hub_subtitle_members, memberCount)
    }
}

/**
 * Le bandeau qui remplace le composeur quand l'agent ne peut pas écrire.
 *
 * ⚠️ Deux raisons **différentes**, et les confondre laisserait croire qu'un
 * groupe fermé pourrait rouvrir en changeant de rôle.
 */
@Composable
fun HubChannel.writeBlock(): String? = when {
    status == HubChannelStatus.FERME -> stringResource(R.string.hub_closed)
    !canWrite -> stringResource(R.string.hub_read_only)
    else -> null
}

/** Ce qui s'affiche sous une bulle qui n'est pas partie. */
@Composable
fun HubMessage.deliveryLabel(): String? = when (deliveryState) {
    HubDeliveryState.ENVOYE -> null
    HubDeliveryState.EN_ATTENTE -> stringResource(R.string.hub_delivery_pending)
    HubDeliveryState.ECHOUE -> stringResource(R.string.hub_delivery_failed)
}

/** Le corps d'un message retiré. Le vide serait une bulle inexplicable. */
@Composable
fun HubMessage.bodyLabel(): String =
    if (isDeleted) stringResource(R.string.hub_message_deleted) else body

/** Ce qui identifie un collègue sous son nom : matricule et dépôt, jamais de coordonnée. */
fun HubColleague.detail(): String =
    listOfNotNull(driverNumber, depotName).joinToString(" · ")

/**
 * Ce qu'on dit à l'agent quand la base refuse.
 *
 * ⚠️ Un refus n'est pas une panne : proposer « réessayer » sur un canal en
 * lecture seule ferait tourner l'agent sans que rien n'explique pourquoi.
 */
@Composable
fun HubFailureKind.label(): String = stringResource(
    when (this) {
        HubFailureKind.NOT_AUTHENTICATED -> R.string.hub_error_not_authenticated
        HubFailureKind.NOT_MEMBER -> R.string.hub_error_not_member
        HubFailureKind.READ_ONLY -> R.string.hub_error_read_only
        HubFailureKind.NOT_OWNER -> R.string.hub_error_not_owner
        HubFailureKind.CANNOT_INVITE -> R.string.hub_error_cannot_invite
        HubFailureKind.NOT_SENDER -> R.string.hub_error_not_sender
        HubFailureKind.CHANNEL_CLOSED -> R.string.hub_error_channel_closed
        HubFailureKind.NOT_FOUND -> R.string.hub_error_not_found
        HubFailureKind.OTHER_NETWORK -> R.string.hub_error_other_network
        HubFailureKind.NO_NETWORK -> R.string.hub_error_no_network
        HubFailureKind.CANNOT_LEAVE -> R.string.hub_error_cannot_leave
        HubFailureKind.FILE_NOT_UPLOADED -> R.string.hub_error_file
        HubFailureKind.BAD_REQUEST -> R.string.hub_error_bad_request
        HubFailureKind.NOT_DEPLOYED -> R.string.hub_error_not_deployed
        HubFailureKind.UNKNOWN -> R.string.hub_error_unknown
    },
)

/**
 * Pourquoi ce collègue n'est pas invitable. `null` quand il l'est.
 *
 * Le faire disparaître de la liste laisserait croire qu'il n'existe pas ; le
 * laisser choisir produirait un refus que rien n'explique.
 */
@Composable
fun HubColleague.unavailable(): String? =
    if (hasAccount) null else stringResource(R.string.hub_no_account)
