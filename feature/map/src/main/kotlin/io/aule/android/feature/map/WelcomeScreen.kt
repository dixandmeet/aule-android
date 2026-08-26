package io.aule.android.feature.map

import android.Manifest
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBrandMark
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.location.LocationAuthorization
import io.aule.android.core.location.LocationProvider

/**
 * L'accueil et son dialogue système, montés ensemble.
 *
 * L'hôte existe parce que la demande de permission a besoin d'un lanceur de
 * résultat d'activité, et que la liste des permissions ne sort pas de ce module.
 * L'écran, lui, ne connaît que l'état et deux rappels — ce qui le laisse
 * vérifiable sans Android.
 *
 * [onDone] est appelé **une fois**, quelle que soit la réponse : autoriser,
 * refuser, ou passer. Laisser l'accueil affiché après un « Autoriser » donnerait
 * l'impression que ça n'a pas marché.
 */
@Composable
fun WelcomeHost(
    location: LocationProvider,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val authorization by location.authorization.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Le résultat n'est pas lu : `refreshAuthorization` relit l'état réel,
        // qui distingue la précision approchée de l'accord plein — ce que la
        // carte de booléens du contrat ne dit pas.
        location.markPermissionRequested()
        location.refreshAuthorization()
        onDone()
    }
    WelcomeScreen(
        authorization = authorization,
        onRequestLocation = { permissionLauncher.launch(WELCOME_LOCATION_PERMISSIONS) },
        onContinue = onDone,
        modifier = modifier,
    )
}

/**
 * L'accueil, réduit à ce qu'il doit obtenir : l'autorisation de localisation.
 *
 * Une seule page, pas un carrousel. On explique **pourquoi juste avant de
 * demander** — c'est le seul moment où la phrase sert à quelque chose — et on
 * laisse une sortie claire : la carte fonctionne sans position, elle est
 * simplement moins utile.
 *
 * ## Ce qu'il remplace
 *
 * Le dialogue système partait à froid, au premier `onResume` de la carte
 * ([MapScreen]) : Android affichait « Autoriser Aule à accéder à la position ? »
 * par-dessus une carte que l'utilisateur n'avait pas encore regardée, sans qu'un
 * mot ait dit à quoi elle servirait. Un refus à cet instant est définitif — on
 * ne redemande jamais — et il se payait pour une question posée trop tôt.
 *
 * ## Ce qu'il ne fait pas
 *
 * Il ne se remontre pas. Une fois vu, il est vu : le drapeau vit dans
 * `WelcomeStore`, et il est **distinct** de « la permission a été demandée » —
 * qui répond « Continuer sans ma position » n'a jamais vu le dialogue système,
 * et le compter comme un refus serait faux.
 *
 * @param authorization l'état courant. Il décide de la phrase **et** de
 *   l'action : demander n'a de sens qu'en [LocationAuthorization.UNKNOWN], et
 *   proposer « Activer » à qui a déjà refusé promet un dialogue qui ne viendra
 *   plus (Android 11+ l'ignore).
 */
@Composable
internal fun WelcomeScreen(
    authorization: LocationAuthorization,
    onRequestLocation: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    AuleTheme {
        AuleAmbientBackground(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = AuleSpacing.xl)
                    .padding(bottom = AuleSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                AuleBrandMark(
                    contentDescription = stringResource(R.string.welcome_logo),
                    modifier = Modifier.auleEnter(index = 0),
                )

                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .auleEnter(index = 1)
                        .semantics { heading() },
                )

                Spacer(modifier = Modifier.height(AuleSpacing.sm))

                Text(
                    text = stringResource(authorization.welcomeSubtitleRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = WELCOME_TEXT_WIDTH)
                        .auleEnter(index = 2),
                )

                Spacer(modifier = Modifier.weight(1f))

                WelcomeAction(
                    label = stringResource(authorization.welcomeActionRes()),
                    glyph = authorization.welcomeActionGlyph(),
                    modifier = Modifier.auleEnter(index = 3),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (authorization == LocationAuthorization.UNKNOWN) {
                            onRequestLocation()
                        } else {
                            onContinue()
                        }
                    },
                )

                // La sortie n'existe que tant qu'il y a quelque chose à
                // esquiver. Position accordée, l'action principale **est**
                // « Voir la carte » : une seconde ligne dirait la même chose en
                // plus petit.
                if (authorization != LocationAuthorization.GRANTED) {
                    TextButton(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .auleEnter(index = 4),
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_skip),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                        )
                    }
                }
            }
        }
    }
}

/**
 * L'action principale, et la seule surface de marque de l'écran — même
 * grammaire que la connexion, pour la même raison : c'est la seule chose qu'on
 * demande à cette page.
 */
@Composable
private fun WelcomeAction(
    label: String,
    glyph: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuleBrandSurface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Button },
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleControl.height)
                .padding(horizontal = AuleSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(
                AuleSpacing.md,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Icon(
                    imageVector = glyph,
                    contentDescription = null,
                    modifier = Modifier.size(AuleControl.icon),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
    }
}

/**
 * Ce que l'écran explique, selon ce qu'on peut encore obtenir.
 *
 * Cinq états, cinq phrases : dire « il faut votre position » à qui l'a refusée
 * dans les Réglages fait redemander ce qui ne s'obtient plus ici.
 */
private fun LocationAuthorization.welcomeSubtitleRes(): Int = when (this) {
    LocationAuthorization.UNKNOWN -> R.string.welcome_body_unknown
    LocationAuthorization.DENIED -> R.string.welcome_body_denied
    LocationAuthorization.REDUCED_ACCURACY -> R.string.welcome_body_reduced
    LocationAuthorization.SERVICES_DISABLED -> R.string.welcome_body_disabled
    LocationAuthorization.GRANTED -> R.string.welcome_body_granted
}

private fun LocationAuthorization.welcomeActionRes(): Int = when (this) {
    LocationAuthorization.UNKNOWN -> R.string.welcome_enable
    // Refusé ou approché, l'autorisation ne se regagne que dans les Réglages —
    // mais l'écran n'y envoie pas de force : on entre dans la carte, et c'est
    // la bannière du HUD qui proposera les Réglages là où le manque se voit.
    LocationAuthorization.DENIED, LocationAuthorization.REDUCED_ACCURACY,
    LocationAuthorization.SERVICES_DISABLED, LocationAuthorization.GRANTED,
    -> R.string.welcome_see_map
}

/** Le glyphe suit le libellé, et rien d'autre : un engrenage sur « Voir la carte » mentirait. */
private fun LocationAuthorization.welcomeActionGlyph(): ImageVector =
    if (this == LocationAuthorization.UNKNOWN) Icons.Filled.MyLocation else Icons.Outlined.Map

/**
 * Au-delà, la phrase s'étire sur toute la largeur d'une tablette et l'œil perd
 * le début de la ligne suivante.
 */
private val WELCOME_TEXT_WIDTH = 320.dp

/**
 * Les deux permissions demandées ensemble.
 *
 * La précision approchée est demandée **avec** la précision fine, et non à sa
 * place : sur Android 12+, l'utilisateur choisit dans le dialogue, et ne
 * proposer que « fine » ne lui laisse que refuser tout.
 */
private val WELCOME_LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
