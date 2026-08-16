package io.aule.android.feature.auth

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBusyIndicator
import io.aule.android.core.designsystem.component.AuleButton
import io.aule.android.core.designsystem.component.AuleButtonProminence
import io.aule.android.core.designsystem.component.AuleCard
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleIcon
import io.aule.android.core.designsystem.component.AuleIconButton
import io.aule.android.core.designsystem.component.AuleTextField
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.AppearanceMode
import io.aule.android.core.model.AvatarException
import io.aule.android.core.model.AvatarFailureKind
import io.aule.android.core.model.Depot
import io.aule.android.core.model.DriverProfile
import io.aule.android.core.model.DriverProfileUpdate
import io.aule.android.core.model.TransportNetwork
import io.aule.android.core.model.forNetwork
import io.aule.android.core.model.repository.GpsTraceCatalog
import io.aule.android.core.model.repository.GpsTraceFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * La fiche agent, éditable, et les préférences de l'appareil.
 *
 * Deux onglets, comme Flutter : la fiche se soumet **en bloc**, l'apparence
 * s'applique au toucher. La déconnexion reste sans confirmation ; la
 * suppression de compte, elle, demande un second geste.
 */
@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    appearance: AppearanceMode,
    onAppearance: (AppearanceMode) -> Unit,
    traces: GpsTraceCatalog,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ProfileTab.PROFIL) }
    val title = stringResource(
        if (tab == ProfileTab.PROFIL) R.string.profile_title else R.string.profile_tab_preferences,
    )
    val subtitle = stringResource(
        if (tab == ProfileTab.PROFIL) {
            R.string.profile_subtitle
        } else {
            R.string.profile_preferences_subtitle
        },
    )
    val traceShareSubject = stringResource(R.string.profile_traces_share_subject)
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(ProfileDraft()) }
    var hydratedId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var avatarMenu by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingDeleteAccount by remember { mutableStateOf(false) }
    var confirmingDeleteTraces by remember { mutableStateOf(false) }
    var cameraSettings by remember { mutableStateOf(false) }
    var pickerMessage by remember { mutableStateOf<Int?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var tracesList by remember { mutableStateOf<List<GpsTraceFile>>(emptyList()) }
    var tracesLoading by remember { mutableStateOf(true) }
    var shareFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tracesList = traces.list()
        tracesLoading = false
    }

    fun uploadFrom(uri: Uri) {
        pickerMessage = null
        viewModel.clearAvatarFailure()
        try {
            viewModel.uploadAvatar(jpegFromUri(context.contentResolver, uri))
        } catch (failure: AvatarException) {
            pickerMessage = when (failure.kind) {
                AvatarFailureKind.EMPTY -> R.string.profile_avatar_error_empty
                AvatarFailureKind.UNSUPPORTED -> R.string.profile_avatar_error_unsupported
                else -> R.string.profile_avatar_error_send
            }
        } catch (_: Throwable) {
            pickerMessage = R.string.profile_avatar_error_unsupported
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        if (ok) captureUri?.let(::uploadFrom)
    }
    val pickVisual = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) uploadFrom(uri)
    }
    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            try {
                val uri = avatarCaptureUri(context)
                captureUri = uri
                takePicture.launch(uri)
            } catch (_: Throwable) {
                pickerMessage = R.string.profile_avatar_camera_unavailable
            }
        } else {
            val activity = context as? Activity
            val rationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            pickerMessage = if (rationale) {
                R.string.profile_avatar_camera_denied
            } else {
                cameraSettings = true
                null
            }
        }
    }

    LaunchedEffect(state.profile?.id) {
        val profile = state.profile ?: return@LaunchedEffect
        if (hydratedId != profile.id) {
            draft = ProfileDraft.from(profile)
            hydratedId = profile.id
        }
    }

    val saved = state.profile?.let { ProfileDraft.from(it) }
    val dirty = saved != null && draft.normalized() != saved.normalized()

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
                .imePadding()
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
                    contentDescription = stringResource(R.string.profile_close),
                    onClick = onClose,
                )
                Column(modifier = Modifier.padding(start = AuleSpacing.xs)) {
                    BasicText(
                        text = title,
                        style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                            .copy(color = tokens.onSurface.color),
                        modifier = Modifier.semantics { heading() },
                    )
                    BasicText(
                        text = subtitle,
                        style = auleTextStyle(AuleRole.KICKER)
                            .copy(color = tokens.onSurfaceMuted.color),
                    )
                }
            }

            ProfileTabSwitcher(
                current = tab,
                onChanged = { tab = it },
                modifier = Modifier.padding(
                    start = AuleSpacing.lg,
                    end = AuleSpacing.lg,
                    bottom = AuleSpacing.md,
                ),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AuleSpacing.lg)
                    .padding(bottom = AuleSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.lg),
            ) {
                when (tab) {
                    ProfileTab.PROFIL -> {
                        val headerName = draft.displayName()
                            .ifBlank { state.profile?.displayName().orEmpty() }
                            .ifBlank { stringResource(R.string.profile_agent) }
                        val avatarError = state.avatarFailure?.message()
                            ?: pickerMessage?.let { stringResource(it) }
                        AvatarBlock(
                            name = headerName,
                            email = state.email ?: state.profile?.email,
                            bytes = state.avatarBytes,
                            hasAvatar = !state.profile?.avatarUrl.isNullOrBlank(),
                            uploading = state.isUploadingAvatar,
                            enabled = state.profile != null && !state.isUploadingAvatar,
                            onTap = {
                                pickerMessage = null
                                viewModel.clearAvatarFailure()
                                avatarMenu = true
                            },
                        )
                        if (avatarError != null) {
                            AuleBanner(message = avatarError, tone = AuleTone.ALERT)
                        }

                        when {
                            state.isLoadingProfile && state.profile == null -> AuleBanner(
                                message = stringResource(R.string.profile_loading),
                            )
                            state.profileFailed -> AuleBanner(
                                message = stringResource(R.string.profile_load_error),
                                tone = AuleTone.ALERT,
                                action = stringResource(R.string.profile_retry),
                                onAction = viewModel::retryProfile,
                            )
                            state.profile == null -> AuleBanner(
                                message = stringResource(R.string.profile_missing),
                            )
                            else -> {
                                if (state.profileSaveFailed) {
                                    AuleBanner(
                                        message = stringResource(R.string.profile_save_error),
                                        tone = AuleTone.ALERT,
                                    )
                                }
                                IdentityEditor(
                                    draft = draft,
                                    enabled = !state.isSavingProfile,
                                    onChange = {
                                        draft = it
                                        viewModel.clearProfileSaveFailure()
                                    },
                                    onNext = { focus.moveFocus(FocusDirection.Down) },
                                )
                                AssignmentEditor(
                                    draft = draft,
                                    depots = state.depots,
                                    networks = state.networks,
                                    enabled = !state.isSavingProfile,
                                    onChange = {
                                        draft = it
                                        viewModel.clearProfileSaveFailure()
                                    },
                                )
                            }
                        }

                        AccountSection(
                            onSignOut = viewModel::signOut,
                            onDeleteAccount = { confirmingDeleteAccount = true },
                            deleting = state.isDeletingAccount,
                            deleteFailed = state.deleteFailed,
                        )
                    }
                    ProfileTab.PREFERENCES -> {
                        AppearanceSection(
                            appearance = appearance,
                            onAppearance = onAppearance,
                        )
                        GpsTracesSection(
                            enabled = traces.enabled,
                            files = tracesList,
                            loading = tracesLoading,
                            shareFailed = shareFailed,
                            onExport = {
                                shareFailed = false
                                try {
                                    shareTraceFiles(
                                        context,
                                        tracesList,
                                        traceShareSubject,
                                    )
                                } catch (_: Throwable) {
                                    shareFailed = true
                                }
                            },
                            onDelete = { confirmingDeleteTraces = true },
                        )
                    }
                }
            }

            if (tab == ProfileTab.PROFIL && dirty && state.profile != null) {
                SaveBar(
                    saving = state.isSavingProfile,
                    onCancel = { draft = saved },
                    onSave = {
                        focus.clearFocus()
                        viewModel.saveProfile(draft.toUpdate())
                    },
                )
            }
        }

        if (avatarMenu) {
            AvatarMenuDialog(
                hasAvatar = !state.profile?.avatarUrl.isNullOrBlank(),
                onDismiss = { avatarMenu = false },
                onCamera = {
                    avatarMenu = false
                    when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                        PackageManager.PERMISSION_GRANTED -> {
                            try {
                                val uri = avatarCaptureUri(context)
                                captureUri = uri
                                takePicture.launch(uri)
                            } catch (_: Throwable) {
                                pickerMessage = R.string.profile_avatar_camera_unavailable
                            }
                        }
                        else -> requestCamera.launch(Manifest.permission.CAMERA)
                    }
                },
                onGallery = {
                    avatarMenu = false
                    try {
                        pickVisual.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    } catch (_: Throwable) {
                        pickerMessage = R.string.profile_avatar_gallery_unavailable
                    }
                },
                onDelete = {
                    avatarMenu = false
                    confirmingDelete = true
                },
            )
        }
        if (confirmingDelete) {
            DeleteAvatarDialog(
                onDismiss = { confirmingDelete = false },
                onConfirm = {
                    confirmingDelete = false
                    viewModel.removeAvatar()
                },
            )
        }
        if (cameraSettings) {
            CameraPermissionDialog(
                onDismiss = { cameraSettings = false },
                onOpenSettings = {
                    cameraSettings = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                },
            )
        }
        if (confirmingDeleteAccount) {
            ConfirmDeleteAccountDialog(
                onDismiss = { confirmingDeleteAccount = false },
                onConfirm = {
                    confirmingDeleteAccount = false
                    viewModel.deleteAccount()
                },
            )
        }
        if (confirmingDeleteTraces) {
            ConfirmDeleteTracesDialog(
                count = tracesList.size,
                onDismiss = { confirmingDeleteTraces = false },
                onConfirm = {
                    confirmingDeleteTraces = false
                    scope.launch {
                        traces.deleteAll()
                        tracesList = traces.list()
                    }
                },
            )
        }
    }
}

@Composable
private fun AvatarBlock(
    name: String,
    email: String?,
    bytes: ByteArray?,
    hasAvatar: Boolean,
    uploading: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val action = stringResource(
        if (hasAvatar) R.string.profile_avatar_edit else R.string.profile_avatar_add,
    )
    AuleCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AuleSpacing.sm),
        elevation = AuleElevation.RESTING,
        shape = RoundedCornerShape(AuleRadius.lg),
        contentPadding = PaddingValues(AuleSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.padding(end = AuleSpacing.xs, bottom = AuleSpacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .semantics {
                            role = Role.Button
                            contentDescription = action
                            if (!enabled) disabled()
                        }
                        .clickable(enabled = enabled, onClick = onTap),
                ) {
                    AvatarPortrait(name = name, bytes = bytes)
                    if (uploading) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(AuleRadius.md))
                                .background(tokens.surfaceSolid.color.copy(alpha = AuleAlpha.VEIL)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AuleBusyIndicator(color = tokens.onSurface.color)
                        }
                    }
                }
                if (enabled && !uploading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = AuleSpacing.xs, y = AuleSpacing.xs)
                            .size(AuleControl.avatarBadge)
                            .clip(CircleShape)
                            .background(tokens.accent.color)
                            .border(AuleStroke.emphasis, tokens.surface.color, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AuleIcon(
                            glyph = if (hasAvatar) AuleGlyph.EDIT else AuleGlyph.CAMERA,
                            tint = tokens.onAccent.color,
                            size = AuleSpacing.md,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(start = AuleSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                BasicText(
                    text = name,
                    style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                        .copy(color = tokens.onSurface.color),
                )
                if (email != null) {
                    BasicText(
                        text = email,
                        style = auleTextStyle(AuleRole.KICKER)
                            .copy(color = tokens.onSurfaceMuted.color),
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarMenuDialog(
    hasAvatar: Boolean,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AuleCard(
            modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH),
            shape = RoundedCornerShape(AuleRadius.lg),
            contentPadding = PaddingValues(vertical = AuleSpacing.sm),
        ) {
            AvatarMenuRow(
                glyph = AuleGlyph.CAMERA,
                label = stringResource(R.string.profile_avatar_camera),
                onClick = onCamera,
            )
            AvatarMenuRow(
                glyph = AuleGlyph.IMAGE,
                label = stringResource(R.string.profile_avatar_gallery),
                onClick = onGallery,
            )
            if (hasAvatar) {
                AvatarMenuRow(
                    glyph = AuleGlyph.TRASH,
                    label = stringResource(R.string.profile_avatar_delete),
                    onClick = onDelete,
                    danger = true,
                )
            }
        }
    }
}

@Composable
private fun AvatarMenuRow(
    glyph: AuleGlyph,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tokens = AuleTheme.tokens
    val ink = if (danger) tokens.alert.color else tokens.onSurface.color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clickable(onClick = onClick)
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        AuleIcon(glyph = glyph, tint = ink)
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold).copy(color = ink),
        )
    }
}

@Composable
private fun DeleteAvatarDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val title = stringResource(R.string.profile_avatar_delete_title)
    Dialog(onDismissRequest = onDismiss) {
        AuleCard(
            modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH),
            shape = RoundedCornerShape(AuleRadius.lg),
            contentPadding = PaddingValues(AuleSpacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
                BasicText(
                    text = title,
                    style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                        .copy(color = tokens.onSurface.color),
                    modifier = Modifier.semantics { heading() },
                )
                BasicText(
                    text = stringResource(R.string.profile_avatar_delete_body),
                    style = auleTextStyle(AuleRole.BODY)
                        .copy(color = tokens.onSurfaceMuted.color),
                )
                AuleButton(
                    title = stringResource(R.string.profile_avatar_delete_confirm),
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

@Composable
private fun CameraPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val title = stringResource(R.string.profile_avatar_permission_title)
    Dialog(onDismissRequest = onDismiss) {
        AuleCard(
            modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH),
            shape = RoundedCornerShape(AuleRadius.lg),
            contentPadding = PaddingValues(AuleSpacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
                BasicText(
                    text = title,
                    style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                        .copy(color = tokens.onSurface.color),
                    modifier = Modifier.semantics { heading() },
                )
                BasicText(
                    text = stringResource(R.string.profile_avatar_permission_camera),
                    style = auleTextStyle(AuleRole.BODY)
                        .copy(color = tokens.onSurfaceMuted.color),
                )
                AuleButton(
                    title = stringResource(R.string.profile_avatar_settings),
                    onClick = onOpenSettings,
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

@Composable
private fun IdentityEditor(
    draft: ProfileDraft,
    enabled: Boolean,
    onChange: (ProfileDraft) -> Unit,
    onNext: () -> Unit,
) {
    ProfileSection(title = stringResource(R.string.profile_identity)) {
        Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
            AuleTextField(
                label = stringResource(R.string.profile_first_name),
                value = draft.firstName,
                onValueChange = { onChange(draft.copy(firstName = it)) },
                enabled = enabled,
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
                onImeAction = onNext,
            )
            AuleTextField(
                label = stringResource(R.string.profile_last_name),
                value = draft.lastName,
                onValueChange = { onChange(draft.copy(lastName = it)) },
                enabled = enabled,
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
                onImeAction = onNext,
            )
            AuleTextField(
                label = stringResource(R.string.profile_phone),
                value = draft.phone,
                onValueChange = { onChange(draft.copy(phone = it)) },
                enabled = enabled,
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
                onImeAction = onNext,
            )
            AuleTextField(
                label = stringResource(R.string.profile_driver_number),
                value = draft.driverNumber,
                onValueChange = { onChange(draft.copy(driverNumber = it)) },
                enabled = enabled,
                imeAction = ImeAction.Done,
                onImeAction = onNext,
            )
        }
    }
}

@Composable
private fun AssignmentEditor(
    draft: ProfileDraft,
    depots: List<Depot>,
    networks: List<TransportNetwork>,
    enabled: Boolean,
    onChange: (ProfileDraft) -> Unit,
) {
    val filtered = depots.forNetwork(draft.networkId)
    ProfileSection(title = stringResource(R.string.profile_assignment)) {
        Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
            OptionPicker(
                label = stringResource(R.string.profile_network),
                hint = stringResource(R.string.profile_select_network),
                empty = stringResource(R.string.profile_no_choice),
                options = networks.map { it.id to it.label },
                selectedId = draft.networkId,
                enabled = enabled,
                onSelect = { id ->
                    val kept = draft.depotId.takeIf { depotId ->
                        depots.forNetwork(id).any { it.id == depotId }
                    }
                    onChange(draft.copy(networkId = id, depotId = kept))
                },
            )
            OptionPicker(
                label = stringResource(R.string.profile_depot),
                hint = stringResource(R.string.profile_select_depot),
                empty = stringResource(R.string.profile_no_choice),
                options = filtered.map { it.id to it.directoryLabel },
                selectedId = draft.depotId,
                enabled = enabled,
                onSelect = { onChange(draft.copy(depotId = it)) },
            )
        }
    }
}

@Composable
private fun OptionPicker(
    label: String,
    hint: String,
    empty: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val tokens = AuleTheme.tokens
    var open by remember { mutableStateOf(false) }
    val chosen = options.find { it.first == selectedId }?.second
    val vacant = options.isEmpty()
    val shown = when {
        vacant -> empty
        chosen != null -> chosen
        else -> hint
    }
    val shape = RoundedCornerShape(AuleRadius.lg)
    val interactive = enabled && !vacant
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.KICKER)
                .copy(color = tokens.onSurfaceMuted.color),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleControl.field)
                .clip(shape)
                .background(tokens.surface.color)
                .border(AuleStroke.hairline, tokens.hairline.color, shape)
                .clickable(enabled = interactive) { open = true }
                .padding(horizontal = AuleSpacing.md)
                .semantics {
                    role = Role.Button
                    contentDescription = "$label. $shown"
                    if (!interactive) disabled()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = shown,
                style = auleTextStyle(AuleRole.BODY).copy(
                    color = if (chosen != null) tokens.onSurface.color else tokens.onSurfaceMuted.color,
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            AuleIcon(
                glyph = AuleGlyph.CHEVRON,
                tint = tokens.onSurfaceMuted.color,
                modifier = Modifier.graphicsLayer { rotationZ = 90f },
            )
        }
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            AuleCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = AuleElevation.OVERLAY,
                shape = RoundedCornerShape(AuleRadius.lg),
                contentPadding = PaddingValues(vertical = AuleSpacing.sm),
            ) {
                BasicText(
                    text = label,
                    style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                        .copy(color = tokens.onSurface.color),
                    modifier = Modifier
                        .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md)
                        .semantics { heading() },
                )
                options.forEach { (id, title) ->
                    val selected = id == selectedId
                    BasicText(
                        text = title,
                        style = auleTextStyle(AuleRole.BODY, if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            .copy(color = if (selected) tokens.accentOnSurface.color else tokens.onSurface.color),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuleTouch.minimum)
                            .clickable {
                                onSelect(id)
                                open = false
                            }
                            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSection(
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    deleting: Boolean,
    deleteFailed: Boolean,
) {
    val tokens = AuleTheme.tokens
    val label = stringResource(R.string.menu_sign_out)
    val deleteLabel = stringResource(R.string.profile_delete_account)
    val shape = RoundedCornerShape(AuleRadius.md)
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        BasicText(
            text = stringResource(R.string.profile_account).uppercase(),
            style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                .copy(color = tokens.onSurfaceMuted.color),
            modifier = Modifier.padding(start = AuleSpacing.xs),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .clip(shape)
                .background(tokens.surface.color)
                .border(AuleStroke.hairline, tokens.hairline.color, shape)
                .clickable(onClick = onSignOut)
                .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.md)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            AuleIcon(glyph = AuleGlyph.SIGN_OUT, tint = tokens.onSurface.color)
            BasicText(
                text = label,
                style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                    .copy(color = tokens.onSurface.color),
            )
        }
        if (deleteFailed) {
            AuleBanner(
                message = stringResource(R.string.profile_delete_error),
                tone = AuleTone.ALERT,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .clickable(enabled = !deleting, onClick = onDeleteAccount)
                .semantics {
                    role = Role.Button
                    contentDescription = deleteLabel
                    if (deleting) disabled()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (deleting) {
                AuleBusyIndicator(color = tokens.alert.color)
            } else {
                BasicText(
                    text = deleteLabel,
                    style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                        .copy(color = tokens.alert.color),
                )
            }
        }
    }
}

@Composable
private fun SaveBar(
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.surface.color)
            .border(AuleStroke.hairline, tokens.hairline.color)
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        BasicText(
            text = stringResource(R.string.profile_unsaved),
            style = auleTextStyle(AuleRole.KICKER)
                .copy(color = tokens.onSurfaceMuted.color),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AuleButton(
                    title = stringResource(R.string.menu_cancel),
                    onClick = onCancel,
                    enabled = !saving,
                    prominence = AuleButtonProminence.PLAIN,
                )
            }
            Box(modifier = Modifier.weight(2f)) {
                AuleButton(
                    title = stringResource(R.string.profile_save),
                    onClick = onSave,
                    enabled = !saving,
                    loading = saving,
                )
            }
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val tokens = AuleTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        BasicText(
            text = title.uppercase(),
            style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                .copy(color = tokens.onSurfaceMuted.color),
            modifier = Modifier.padding(start = AuleSpacing.xs),
        )
        content()
    }
}

private enum class ProfileTab { PROFIL, PREFERENCES }

@Composable
private fun ProfileTabSwitcher(
    current: ProfileTab,
    onChanged: (ProfileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AuleTheme.tokens
    val shape = RoundedCornerShape(AuleRadius.md)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.surface.color)
            .border(AuleStroke.hairline, tokens.hairline.color, shape)
            .padding(AuleSpacing.xs),
    ) {
        SegmentPill(
            label = stringResource(R.string.profile_title),
            active = current == ProfileTab.PROFIL,
            onClick = { onChanged(ProfileTab.PROFIL) },
            modifier = Modifier.weight(1f),
        )
        SegmentPill(
            label = stringResource(R.string.profile_tab_preferences),
            active = current == ProfileTab.PREFERENCES,
            onClick = { onChanged(ProfileTab.PREFERENCES) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AppearanceSection(
    appearance: AppearanceMode,
    onAppearance: (AppearanceMode) -> Unit,
) {
    val tokens = AuleTheme.tokens
    val shape = RoundedCornerShape(AuleRadius.md)
    ProfileSection(title = stringResource(R.string.profile_appearance)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(tokens.surface.color)
                .border(AuleStroke.hairline, tokens.hairline.color, shape)
                .padding(AuleSpacing.xs),
        ) {
            AppearanceMode.entries.forEach { mode ->
                SegmentPill(
                    label = stringResource(mode.labelRes()),
                    glyph = mode.glyph(),
                    active = appearance == mode,
                    onClick = { onAppearance(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GpsTracesSection(
    enabled: Boolean,
    files: List<GpsTraceFile>,
    loading: Boolean,
    shareFailed: Boolean,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val deleteLabel = stringResource(R.string.profile_traces_delete)
    val summary = when {
        loading -> stringResource(R.string.profile_loading)
        files.isEmpty() && enabled -> stringResource(R.string.profile_traces_empty)
        files.isEmpty() -> stringResource(R.string.profile_traces_disabled)
        else -> {
            val kilos = (files.sumOf { it.bytes } / 1024L).toString()
            stringResource(
                if (files.size == 1) {
                    R.string.profile_traces_summary_one
                } else {
                    R.string.profile_traces_summary_many
                },
                files.size,
                kilos,
            )
        }
    }
    ProfileSection(title = stringResource(R.string.profile_traces)) {
        AuleCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = AuleElevation.NONE,
            shape = RoundedCornerShape(AuleRadius.md),
            contentPadding = PaddingValues(AuleSpacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuleTouch.minimum),
                        contentAlignment = Alignment.Center,
                    ) {
                        AuleBusyIndicator(color = tokens.onSurfaceMuted.color)
                    }
                } else {
                    BasicText(
                        text = summary,
                        style = auleTextStyle(AuleRole.BODY)
                            .copy(color = tokens.onSurfaceMuted.color),
                    )
                }
                if (shareFailed) {
                    AuleBanner(
                        message = stringResource(R.string.profile_traces_share_failed),
                        tone = AuleTone.ALERT,
                    )
                }
                if (files.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AuleButton(
                                title = stringResource(R.string.profile_traces_export),
                                onClick = onExport,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = AuleTouch.minimum,
                                    minHeight = AuleTouch.minimum,
                                )
                                .clip(RoundedCornerShape(AuleRadius.md))
                                .background(tokens.alert.color.copy(alpha = AuleAlpha.TINT))
                                .clickable(onClick = onDelete)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = deleteLabel
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            AuleIcon(glyph = AuleGlyph.TRASH, tint = tokens.alert.color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: AuleGlyph? = null,
) {
    val tokens = AuleTheme.tokens
    val ink = if (active) tokens.onAccent.color else tokens.onSurfaceMuted.color
    val shape = RoundedCornerShape(AuleRadius.sm)
    Row(
        modifier = modifier
            .clip(shape)
            .then(if (active) Modifier.background(tokens.accent.color) else Modifier)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .padding(horizontal = AuleSpacing.xs, vertical = AuleSpacing.sm)
            .semantics {
                role = Role.Button
                contentDescription = label
                selected = active
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            AuleIcon(
                glyph = if (active) AuleGlyph.CHECK else glyph,
                tint = ink,
                size = AuleSpacing.lg,
                filled = active,
                modifier = Modifier.padding(end = AuleSpacing.xs),
            )
        }
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold).copy(color = ink),
        )
    }
}

@Composable
private fun ConfirmDeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val title = stringResource(R.string.profile_delete_title)
    Dialog(onDismissRequest = onDismiss) {
        AuleCard(
            modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH),
            shape = RoundedCornerShape(AuleRadius.lg),
            contentPadding = PaddingValues(AuleSpacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
                BasicText(
                    text = title,
                    style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                        .copy(color = tokens.onSurface.color),
                    modifier = Modifier.semantics { heading() },
                )
                BasicText(
                    text = stringResource(R.string.profile_delete_body),
                    style = auleTextStyle(AuleRole.BODY)
                        .copy(color = tokens.onSurfaceMuted.color),
                )
                AuleButton(
                    title = stringResource(R.string.profile_delete_confirm),
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

@Composable
private fun ConfirmDeleteTracesDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val title = stringResource(R.string.profile_traces_delete_title)
    val body = stringResource(
        if (count == 1) {
            R.string.profile_traces_delete_body_one
        } else {
            R.string.profile_traces_delete_body_many
        },
        count,
    )
    Dialog(onDismissRequest = onDismiss) {
        AuleCard(
            modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH),
            shape = RoundedCornerShape(AuleRadius.lg),
            contentPadding = PaddingValues(AuleSpacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
                BasicText(
                    text = title,
                    style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                        .copy(color = tokens.onSurface.color),
                    modifier = Modifier.semantics { heading() },
                )
                BasicText(
                    text = body,
                    style = auleTextStyle(AuleRole.BODY)
                        .copy(color = tokens.onSurfaceMuted.color),
                )
                AuleButton(
                    title = stringResource(R.string.profile_avatar_delete_confirm),
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

private fun AppearanceMode.labelRes(): Int = when (this) {
    AppearanceMode.LIGHT -> R.string.profile_theme_light
    AppearanceMode.DARK -> R.string.profile_theme_dark
    AppearanceMode.SYSTEM -> R.string.profile_theme_auto
}

private fun AppearanceMode.glyph(): AuleGlyph = when (this) {
    AppearanceMode.LIGHT -> AuleGlyph.SUN
    AppearanceMode.DARK -> AuleGlyph.MOON
    AppearanceMode.SYSTEM -> AuleGlyph.AUTO
}

private fun shareTraceFiles(
    context: android.content.Context,
    files: List<GpsTraceFile>,
    subject: String,
) {
    val uris = ArrayList<Uri>(files.size)
    files.forEach { file ->
        uris.add(
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                File(file.path),
            ),
        )
    }
    val first = uris.first()
    val share = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "text/csv"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri(subject, first).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
    }
    context.startActivity(Intent.createChooser(share, subject))
}

/**
 * La saisie du moment, comparable à ce que le serveur a.
 *
 * Deux instances aux mêmes valeurs sont égales : c'est tout ce que la barre
 * d'enregistrement lui demande.
 */
internal data class ProfileDraft(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val driverNumber: String = "",
    val depotId: String? = null,
    val networkId: String? = null,
) {
    fun displayName(): String = listOf(firstName.trim(), lastName.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" ")

    fun normalized(): ProfileDraft = copy(
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        phone = phone.trim(),
        driverNumber = driverNumber.trim(),
    )

    fun toUpdate(): DriverProfileUpdate = DriverProfileUpdate(
        firstName = firstName.trim().ifEmpty { null },
        lastName = lastName.trim().ifEmpty { null },
        phone = phone.trim().ifEmpty { null },
        driverNumber = driverNumber.trim().ifEmpty { null },
        depotId = depotId,
        networkId = networkId,
    )

    companion object {
        fun from(profile: DriverProfile) = ProfileDraft(
            firstName = profile.firstName.orEmpty(),
            lastName = profile.lastName.orEmpty(),
            phone = profile.phone.orEmpty(),
            driverNumber = profile.driverNumber.orEmpty(),
            depotId = profile.depotId,
            networkId = profile.networkId,
        )
    }
}
