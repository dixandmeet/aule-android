package io.aule.android.feature.auth

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleButton
import io.aule.android.core.designsystem.component.AuleButtonProminence
import io.aule.android.core.designsystem.component.AuleCard
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleIcon
import io.aule.android.core.designsystem.component.AuleIconButton
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch
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
        val tokens = AuleTheme.tokens
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(tokens.surfaceSolid.color)
                .safeDrawingPadding()
                .semantics {
                    this.paneTitle = title
                    isTraversalGroup = true
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.sm, vertical = AuleSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuleIconButton(
                    glyph = AuleGlyph.BACK,
                    contentDescription = stringResource(R.string.menu_close),
                    onClick = onClose,
                )
                BasicText(
                    text = title,
                    style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                        .copy(color = tokens.onSurface.color),
                    modifier = Modifier
                        .padding(start = AuleSpacing.xs)
                        .semantics { heading() },
                )
            }

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

                Spacer(modifier = Modifier.height(AuleSpacing.xl))
                BasicText(
                    text = stringResource(R.string.menu_group_account).uppercase(),
                    style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                        .copy(color = tokens.onSurfaceMuted.color),
                    modifier = Modifier.padding(start = AuleSpacing.xs, bottom = AuleSpacing.sm),
                )
                MenuRow(
                    glyph = AuleGlyph.PERSON,
                    label = stringResource(R.string.menu_my_profile),
                    onClick = onOpenProfile,
                )

                // Le vide, ici, est la mesure de sécurité : la fin de session
                // reste hors de portée du pouce qui vise l'identité.
                Spacer(modifier = Modifier.weight(1f))

                SignOutRow(
                    enabled = !state.isSubmitting,
                    onClick = { confirming = true },
                )
                Spacer(modifier = Modifier.height(AuleSpacing.lg))
                BasicText(
                    text = stringResource(R.string.menu_version, versionLabel),
                    style = auleTextStyle(AuleRole.KICKER)
                        .copy(color = tokens.onSurfaceMuted.color.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AuleSpacing.lg),
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
    val tokens = AuleTheme.tokens
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
    AuleCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AuleSpacing.sm)
            .clickable(onClick = onOpen)
            .semantics {
                role = Role.Button
                contentDescription = "$name. $openLabel"
            },
        elevation = AuleElevation.RESTING,
        shape = RoundedCornerShape(AuleRadius.lg),
        contentPadding = PaddingValues(AuleSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarPortrait(name = name, bytes = avatarBytes)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AuleSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                BasicText(
                    text = name,
                    style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                        .copy(color = tokens.onSurface.color),
                    maxLines = 2,
                )
                if (email != null) {
                    BasicText(
                        text = email,
                        style = auleTextStyle(AuleRole.KICKER)
                            .copy(color = tokens.onSurfaceMuted.color),
                        maxLines = 1,
                    )
                }
            }
            AuleIcon(
                glyph = AuleGlyph.CHEVRON,
                tint = tokens.onSurfaceMuted.color,
            )
        }
        if (facts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(AuleSpacing.md))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            ) {
                facts.forEach { fact ->
                    BasicText(
                        text = fact,
                        style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                            .copy(color = tokens.onSurfaceMuted.color),
                        modifier = Modifier
                            .clip(RoundedCornerShape(AuleRadius.sm))
                            .background(tokens.surfaceSolid.color)
                            .padding(horizontal = AuleSpacing.sm, vertical = AuleSpacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    glyph: AuleGlyph,
    label: String,
    onClick: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val shape = RoundedCornerShape(AuleRadius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.md)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        AuleIcon(glyph = glyph, tint = tokens.onSurface.color)
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                .copy(color = tokens.onSurface.color),
            modifier = Modifier.weight(1f),
        )
        AuleIcon(glyph = AuleGlyph.CHEVRON, tint = tokens.onSurfaceMuted.color)
    }
}

@Composable
private fun SignOutRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val label = stringResource(R.string.menu_sign_out)
    val shape = RoundedCornerShape(AuleRadius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clip(shape)
            .background(tokens.alert.color.copy(alpha = AuleAlpha.TINT))
            .border(AuleStroke.hairline, tokens.alert.color.copy(alpha = AuleAlpha.OUTLINE), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.md)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        AuleIcon(glyph = AuleGlyph.SIGN_OUT, tint = tokens.alert.color)
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                .copy(color = tokens.alert.color),
        )
    }
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
    val tokens = AuleTheme.tokens
    val title = stringResource(R.string.menu_sign_out_confirm_title)
    Dialog(onDismissRequest = onDismiss) {
        AuleCard(
            modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH),
            shape = RoundedCornerShape(AuleRadius.lg),
            contentPadding = PaddingValues(AuleSpacing.xl),
        ) {
            Column(
                modifier = Modifier.semantics {
                    this.paneTitle = title
                    isTraversalGroup = true
                },
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
                BasicText(
                    text = title,
                    style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                        .copy(color = tokens.onSurface.color),
                    modifier = Modifier.semantics { heading() },
                )
                BasicText(
                    text = stringResource(R.string.menu_sign_out_confirm_body),
                    style = auleTextStyle(AuleRole.BODY)
                        .copy(color = tokens.onSurfaceMuted.color),
                )
                Spacer(modifier = Modifier.height(AuleSpacing.xs))
                AuleButton(
                    title = stringResource(R.string.menu_sign_out),
                    onClick = onConfirm,
                    prominence = AuleButtonProminence.DANGER,
                )
                AuleButton(
                    title = stringResource(R.string.menu_cancel),
                    onClick = onDismiss,
                    prominence = AuleButtonProminence.PLAIN,
                )
            }
        }
    }
}

private val DIALOG_MAX_WIDTH = 360.dp
