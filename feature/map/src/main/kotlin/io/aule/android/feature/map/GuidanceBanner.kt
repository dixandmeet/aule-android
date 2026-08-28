package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing

/**
 * Le bandeau haut : « que dois-je faire maintenant ? », et rien d'autre.
 *
 * Un seul à la fois. GPS perdu et sortie de tracé passent devant la
 * manœuvre : sans position fiable, une consigne à « 80 m » peut être à
 * trois cents.
 *
 * Le recalcul passe devant la sortie de tracé. Les deux sont vrais en même
 * temps — on est bien hors de l'itinéraire pendant qu'on en cherche un autre —
 * mais ils ne disent pas la même chose au conducteur : « Vous avez quitté
 * l'itinéraire » lui demande d'agir, « Recalcul de l'itinéraire » lui dit que
 * c'est en cours. Au volant, la seconde est la seule des deux sur laquelle il
 * n'y a rien à faire, donc la seule qui repose.
 *
 * ## Pourquoi ce bandeau est la surface de marque de l'écran de guidage
 *
 * C'est le seul élément qu'un conducteur regarde **en conduisant**, par coups
 * d'œil d'une demi-seconde. Il était blanc, posé sur une carte claire, et il
 * fallait le chercher. Il prend maintenant le dégradé de marque : d'un coup
 * d'œil périphérique, on sait où il est avant même de lire ce qu'il dit.
 *
 * C'est aussi la raison pour laquelle l'écran de guidage n'a **pas** d'autre
 * `AuleBrandSurface` : deux surfaces de marque, et le coup d'œil hésite.
 *
 * ## La distance monte d'un cran
 *
 * « 200 m · Route de Saint-Joseph » tenait sur une seule ligne, au même poids.
 * Or ces deux informations ne se lisent pas au même moment : la distance dit
 * *quand*, et c'est elle qu'on relit tous les cinquante mètres ; le nom de rue
 * dit *où*, et il se lit une fois. La distance prend donc la ligne du dessus,
 * en chiffres appuyés — et le nom de rue cesse d'être ce qu'il faut traverser
 * pour l'atteindre.
 */
@Composable
internal fun GuidanceBanner(
    state: NavigationUiState,
    modifier: Modifier = Modifier,
) {
    val lead: String?
    val title: String
    val detail: String?
    val alert = state.signalLost || state.offRoute || state.recalculating
    when {
        state.signalLost -> {
            lead = null
            title = stringResource(R.string.nav_gps_lost)
            detail = stringResource(R.string.nav_gps_lost_detail)
        }
        state.recalculating -> {
            lead = null
            title = stringResource(R.string.nav_recalculating)
            detail = stringResource(R.string.nav_recalculating_detail)
        }
        state.offRoute -> {
            lead = null
            title = stringResource(R.string.nav_off_route)
            detail = stringResource(R.string.nav_off_route_detail)
        }
        else -> {
            val action = state.action ?: return
            lead = action.leadText()
            title = action.titleText()
            detail = action.detailText()
        }
    }
    // L'annonce garde la forme d'une phrase, quelle que soit la mise en page :
    // TalkBack lit une consigne, il ne décrit pas une composition.
    val spoken = listOfNotNull(lead, title, detail).joinToString(". ")
    val announced = Modifier.semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = spoken
    }

    if (alert) {
        val colors = MaterialTheme.colorScheme
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .then(announced),
            shape = MaterialTheme.shapes.medium,
            color = colors.error,
            contentColor = colors.onError,
        ) {
            BannerBody(lead = null, title = title, detail = detail)
        }
    } else {
        AuleBrandSurface(
            modifier = modifier
                .fillMaxWidth()
                .then(announced),
            shape = MaterialTheme.shapes.medium,
            elevation = AuleElevation.FLOATING,
        ) {
            BannerBody(lead = lead, title = title, detail = detail)
        }
    }
}

/**
 * Le contenu du bandeau, identique dans les deux registres.
 *
 * Il ne choisit aucune couleur : la surface qui le porte a déjà posé sa couleur
 * de contenu, et c'est ce qui permet au même corps de servir sous l'aplat de
 * marque et sous l'aplat d'erreur. Un corps qui piocherait `onSurface` dans le
 * thème écrirait en sombre sur le rouge.
 */
@Composable
private fun BannerBody(lead: String?, title: String, detail: String?) {
    Column(
        modifier = Modifier.padding(
            horizontal = AuleSpacing.lg,
            vertical = AuleSpacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
    ) {
        if (lead != null) {
            Text(
                text = lead,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
