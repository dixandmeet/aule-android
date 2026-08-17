package io.aule.android.feature.auth

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.model.DriverProfile
import kotlinx.coroutines.CancellationException

/**
 * Le menu du compte, posé par-dessus la carte.
 *
 * Port de `SAE/lib/screens/map_menu_screen.dart` : l'identité en carte en
 * haut, la fin de session seule tout en bas. La distance entre les deux est
 * délibérée — un doigt qui dérape en roulant ne doit pas déconnecter — et la
 * confirmation qui suit vient de la même décision
 * (`SAE/docs/carte-app/REPRISE.md`, 09/08/2026).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountMenuScreen(
    viewModel: AuthViewModel,
    versionLabel: String,
    onClose: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }
    val title = stringResource(R.string.menu_title)

    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            onClose()
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    AuleTheme {
        val colors = MaterialTheme.colorScheme
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.surface)
                .safeDrawingPadding()
                .semantics {
                    this.paneTitle = title
                    isTraversalGroup = true
                },
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = AuleGlyph.BACK.asImageVector(),
                            contentDescription = stringResource(R.string.menu_close),
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AuleSpacing.lg),
            ) {
                IdentityCard(
                    email = state.email,
                    profile = state.profile,
                    depotLabel = state.depot?.label,
                    avatarBytes = state.avatarBytes,
                    onOpen = onOpenProfile,
                )

                Text(
                    text = stringResource(R.string.menu_group_account).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = AuleSpacing.xs,
                        top = AuleSpacing.xl,
                        bottom = AuleSpacing.sm,
                    ),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_my_profile)) },
                    leadingContent = {
                        Icon(
                            imageVector = AuleGlyph.PERSON.asImageVector(),
                            contentDescription = null,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = AuleGlyph.CHEVRON.asImageVector(),
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = colors.surface),
                    modifier = Modifier.clickable(onClick = onOpenProfile),
                )

                // Le vide, ici, est la mesure de sécurité : la fin de session
                // reste hors de portée du pouce qui vise l'identité.
                Spacer(modifier = Modifier.weight(1f))

                SignOutRow(
                    enabled = !state.isSubmitting,
                    onClick = { confirming = true },
                )
                Text(
                    text = stringResource(R.string.menu_version, versionLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AuleSpacing.lg, bottom = AuleSpacing.lg),
                )
            }
        }

        if (confirming) {
            SignOutDialog(
                onDismiss = { confirming = false },
                onConfirm = {
                    confirming = false
                    viewModel.signOut()
                },
            )
        }
    }
}

/**
 * Qui est connecté, en une carte.
 *
 * Le nom porte, le matricule et le dépôt deviennent des puces — ce sont les
 * deux choses qu'on lit ici pour les dire au régulateur —, l'adresse reste
 * en second. Toute la carte ouvre le profil : c'est la plus grande cible
 * de la page, et la plus haute.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityCard(
    email: String?,
    profile: DriverProfile?,
    depotLabel: String?,
    avatarBytes: ByteArray?,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fallback = stringResource(R.string.menu_local_session)
    val name = profile?.displayName()
        ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: fallback
    val facts = buildList {
        profile?.driverNumber?.trim()?.takeIf { it.isNotEmpty() }?.let {
            add(stringResource(R.string.menu_matricule, it))
        }
        depotLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }
    val openLabel = stringResource(R.string.menu_open_profile)
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AuleSpacing.sm)
            .semantics { contentDescription = "$name. $openLabel" },
        shape = MaterialTheme.shapes.medium,
        // Une carte de la couleur exacte de la page n'est pas une carte : de
        // nuit, l'ombre ne se voit pas et il ne restait rien pour la détacher
        // du fond. C'est le cran de conteneur qui fait ce travail.
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(
            defaultElevation = AuleElevation.RESTING.height(AuleTheme.night),
        ),
    ) {
        ListItem(
            headlineContent = { Text(name, maxLines = 2) },
            supportingContent = email?.let { { Text(it, maxLines = 1) } },
            leadingContent = { AvatarPortrait(name = name, bytes = avatarBytes) },
            trailingContent = {
                Icon(
                    imageVector = AuleGlyph.CHEVRON.asImageVector(),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            },
            // Transparent : la couleur vient de la carte qui le porte.
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        if (facts.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(
                    start = AuleSpacing.lg,
                    end = AuleSpacing.lg,
                    bottom = AuleSpacing.lg,
                ),
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            ) {
                facts.forEach { fact ->
                    AssistChip(
                        onClick = onOpen,
                        label = { Text(fact) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SignOutRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.menu_sign_out)
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = colors.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingContent = {
            Icon(
                imageVector = AuleGlyph.SIGN_OUT.asImageVector(),
                contentDescription = null,
                tint = colors.onErrorContainer,
            )
        },
        colors = ListItemDefaults.colors(containerColor = colors.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            // Un aplat teinté à angles vifs, au milieu d'une page où la carte
            // d'identité et les puces sont arrondies, se lit comme un bandeau
            // d'erreur et non comme l'action qu'il est. La forme du thème le
            // remet dans la famille — et borne le ripple au même contour.
            .clip(MaterialTheme.shapes.medium)
            .semantics { contentDescription = label }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

/**
 * La confirmation.
 *
 * L'action destructrice est en tête et la sortie juste dessous : c'est
 * l'ordre de la boîte système Android, et l'inverser ferait rater la sortie à
 * qui l'ouvre par erreur.
 */
@Composable
private fun SignOutDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_sign_out_confirm_title)) },
        text = { Text(stringResource(R.string.menu_sign_out_confirm_body)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                ),
            ) {
                Text(stringResource(R.string.menu_sign_out))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_cancel))
            }
        },
    )
}
