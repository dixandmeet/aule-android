package io.aule.android.feature.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.repository.BiometricEnrollmentStore
import io.aule.android.core.security.BiometricAuthenticator
import io.aule.android.core.security.BiometricEnableResult
import io.aule.android.core.security.BiometricFailureKind
import io.aule.android.core.security.BiometricKeyInvalidatedException
import io.aule.android.core.security.BiometricKeyVault
import io.aule.android.core.security.BiometricOutcome
import io.aule.android.core.security.BiometricSupport
import io.aule.android.core.security.SecureBlob
import io.aule.android.core.security.enableBiometric
import kotlinx.coroutines.launch

/**
 * Les deux écrans du verrou biométrique, et le dialogue qui les dépanne.
 *
 * Ils vivent ensemble parce qu'ils partagent tout ce qui compte : la résolution
 * de l'activité hôte, la séquence d'activation, et le principe qui les tient —
 * **toute sortie mène au formulaire de connexion**, jamais à un écran sans
 * issue.
 */

/**
 * Les quatre pièces du verrou, réunies.
 *
 * Elles voyagent **toujours ensemble** — activer demande le capteur, le coffre,
 * le dialogue et le dépôt, et il n'existe aucun écran qui n'en voudrait que
 * deux. Les passer une à une donnait des signatures de sept paramètres dont
 * cinq de câblage, où l'ordre finit par se mélanger.
 *
 * Optionnelle partout où elle apparaît : absente, l'écran se comporte comme
 * avant la fonctionnalité. C'est ce qui permet à un aperçu ou à un test de ne
 * rien fabriquer.
 */
data class BiometricControls(
    val support: BiometricSupport,
    val vault: BiometricKeyVault,
    val authenticator: BiometricAuthenticator,
    val store: BiometricEnrollmentStore,
    val logger: AuleLogger,
)

/**
 * L'activité hôte, remontée du `Context` de composition.
 *
 * `BiometricPrompt` l'exige, et `LocalContext` rend souvent un `ContextWrapper`
 * de thème plutôt que l'activité elle-même — d'où la remontée. `null` ne
 * devrait jamais arriver ([MainActivity] est une `FragmentActivity`), mais le
 * cas se traite en rendant la main plutôt qu'en levant : un verrou qui plante
 * est pire qu'un verrou absent.
 */
internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * La proposition qui suit la première connexion.
 *
 * Un volet, et non un écran : ce n'est pas une étape du parcours, c'est une
 * offre qu'on peut écarter d'un geste. Il est modelé sur `EndServiceHost` —
 * médaillon, titre, deux actions — sans passer par `SheetChrome`, qui est
 * interne à `:feature:map`.
 *
 * Quoi qu'il arrive, [onDone] est appelé : activée, refusée ou simplement
 * fermée, la proposition ne revient pas. C'est le `ViewModel` qui note qu'elle
 * a eu lieu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricEnrollHost(
    controls: BiometricControls,
    userId: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val support = controls.support
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var working by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var askSettings by remember { mutableStateOf(false) }

    val type = remember(support) { support.detectedType() }
    val promptTitle = type.unlockTitle()
    val negative = stringResource(R.string.auth_biometric_use_password)
    val unavailable = stringResource(R.string.auth_biometric_unavailable)
    val failureMessages = biometricFailureMessages()

    if (askSettings) {
        NoBiometricEnrolledDialog(
            onDismiss = {
                askSettings = false
                onDone()
            },
            onOpenSettings = {
                askSettings = false
                context.startActivity(biometricEnrollIntent())
                onDone()
            },
        )
        return
    }

    AuleTheme {
        ModalBottomSheet(
            onDismissRequest = { if (!working) onDone() },
            modifier = modifier,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.xl)
                    .padding(bottom = AuleSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
                BiometricMedallion()
                Text(
                    text = stringResource(R.string.auth_biometric_proposal_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.auth_biometric_proposal_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (notice != null) {
                    AuleBanner(message = notice.orEmpty(), tone = AuleTone.ALERT)
                }
                Column(verticalArrangement = Arrangement.spacedBy(AuleTouch.minimum)) {
                    Button(
                        onClick = {
                            if (working) return@Button
                            val activity = context.findFragmentActivity()
                            if (activity == null) {
                                notice = unavailable
                                return@Button
                            }
                            working = true
                            notice = null
                            scope.launch {
                                val result = enableBiometric(
                                    activity = activity,
                                    support = support,
                                    vault = controls.vault,
                                    authenticator = controls.authenticator,
                                    store = controls.store,
                                    userId = userId,
                                    logger = controls.logger,
                                    title = promptTitle,
                                    subtitle = null,
                                    negativeLabel = negative,
                                )
                                working = false
                                when (result) {
                                    BiometricEnableResult.Enabled -> onDone()
                                    BiometricEnableResult.NotEnrolled -> askSettings = true
                                    is BiometricEnableResult.Refused -> {
                                        // Une annulation ne dit rien et ferme :
                                        // insister après un refus explicite
                                        // serait la seule façon de le rendre
                                        // désagréable.
                                        val message = failureMessages[result.kind]
                                        if (message == null) onDone() else notice = message
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuleControl.height),
                        enabled = !working,
                        shape = MaterialTheme.shapes.medium,
                        colors = auleAccentButtonColors(),
                    ) {
                        if (working) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(AuleControl.icon),
                                color = AuleTheme.tokens.onAccent.color,
                                strokeWidth = AuleStroke.glyph,
                            )
                        } else {
                            Text(
                                text = type.enableLabel(),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                        }
                    }
                    TextButton(
                        onClick = { if (!working) onDone() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuleTouch.minimum),
                        enabled = !working,
                    ) {
                        Text(stringResource(R.string.auth_biometric_later))
                    }
                }
                Spacer(Modifier.height(AuleSpacing.sm))
            }
        }
    }
}

/**
 * Le verrou du lancement.
 *
 * ## Pourquoi ce fond, et pas un écran de plus
 *
 * Le même fond ambiant que la branche « pas encore prêt » de la racine : entre
 * la fin du démarrage et l'ouverture du dialogue système, il ne doit rien se
 * passer à l'écran. Un écran de marque qui apparaîtrait pour disparaître sous
 * le dialogue une image plus tard ferait clignoter le lancement.
 *
 * ## Ce que le bouton fait là
 *
 * « Se connecter autrement » est visible **sans attendre un échec**. Le
 * dialogue système peut être fermé, le capteur peut ne pas répondre, et
 * quelqu'un peut simplement vouloir entrer avec son mot de passe : dans les
 * trois cas, l'issue doit être à l'écran plutôt que derrière un geste à deviner.
 */
@Composable
fun BiometricUnlockHost(
    controls: BiometricControls,
    userId: String,
    email: String?,
    onSucceeded: () -> Unit,
    onDeclined: (invalidated: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val type = remember(controls) { controls.support.detectedType() }
    val title = type.unlockTitle()
    val subtitle = email?.let { stringResource(R.string.auth_biometric_unlock_subtitle, it) }
    val negative = stringResource(R.string.auth_biometric_use_password)

    // `userId` en clé : si le compte changeait sous cet écran, le dialogue
    // repartirait sur le bon scellé plutôt que de rester sur le précédent.
    LaunchedEffect(userId) {
        val activity = context.findFragmentActivity()
        val enrollment = runCatching { controls.store.read(userId) }.getOrNull()
        if (activity == null || enrollment == null) {
            onDeclined(false)
            return@LaunchedEffect
        }
        val cipher = try {
            controls.vault.decryptCipher(SecureBlob.ivOf(enrollment))
        } catch (_: BiometricKeyInvalidatedException) {
            // La clé ne vaut plus rien, et on l'apprend **avant** d'avoir
            // affiché quoi que ce soit : rien ne sert d'ouvrir un dialogue qui
            // ne pourrait pas aboutir.
            onDeclined(true)
            return@LaunchedEffect
        }
        val outcome = controls.authenticator
            .authenticate(activity, cipher, title, subtitle, negative)
        when (outcome) {
            is BiometricOutcome.Refused ->
                onDeclined(outcome.kind == BiometricFailureKind.KEY_INVALIDATED)
            is BiometricOutcome.Granted -> {
                // La preuve n'est pas le rappel de succès : c'est ce
                // déchiffrement, que seul un `Cipher` autorisé par le matériel
                // peut mener à bien.
                val opened = runCatching { SecureBlob.open(outcome.cipher, enrollment) }
                if (opened.isSuccess) onSucceeded() else onDeclined(true)
            }
        }
    }

    AuleTheme {
        AuleAmbientBackground(modifier = modifier) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
                Text(
                    text = stringResource(R.string.auth_brand),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = { onDeclined(false) },
                    modifier = Modifier.defaultMinSize(minHeight = AuleTouch.minimum),
                ) {
                    Text(stringResource(R.string.auth_biometric_use_password))
                }
            }
        }
    }
}

/**
 * Le médaillon du volet, sur le modèle de celui de fin de service.
 *
 * Il n'est pas décoratif : c'est lui qui dit de quoi parle le volet avant
 * qu'une ligne soit lue.
 */
@Composable
private fun BiometricMedallion() {
    Surface(
        modifier = Modifier.size(MEDALLION),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = AuleGlyph.FINGERPRINT.asImageVector(),
                contentDescription = null,
                modifier = Modifier.size(AuleControl.icon),
            )
        }
    }
}

/**
 * Le capteur est là, rien n'y est enregistré.
 *
 * Calqué sur `CameraPermissionDialog` : le même geste — on n'a pas ce qu'il
 * faut, voici où l'obtenir — mérite le même dessin. Surtout pas un message
 * technique : « BIOMETRIC_ERROR_NONE_ENROLLED » n'aide personne à poser son
 * doigt sur un capteur.
 */
@Composable
fun NoBiometricEnrolledDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH),
        title = { Text(stringResource(R.string.auth_biometric_none_enrolled_title)) },
        text = { Text(stringResource(R.string.auth_biometric_none_enrolled_body)) },
        confirmButton = {
            Button(onClick = onOpenSettings, colors = auleAccentButtonColors()) {
                Text(stringResource(R.string.auth_biometric_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.auth_biometric_later))
            }
        },
    )
}

/**
 * Où envoyer quelqu'un qui n'a rien enregistré.
 *
 * `ACTION_BIOMETRIC_ENROLL` ouvre directement la bonne page, mais n'existe qu'à
 * partir de l'API 30. En dessous — et le plancher du projet est l'API 26 — on
 * retombe sur les réglages de sécurité, qui demandent deux gestes de plus mais
 * mènent au même endroit. Sans ce repli, le bouton ne ferait rien sur un
 * appareil ancien.
 */
internal fun biometricEnrollIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
            putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG)
        }
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }

/**
 * 56 dp : un cran au-dessus du médaillon de fin de service, parce que celui-ci
 * ouvre le volet au lieu d'accompagner un titre. Ce n'est pas une cible
 * tactile — rien ne s'y appuie — donc le plancher des 48 dp ne s'y applique pas.
 */
private val MEDALLION = 56.dp
