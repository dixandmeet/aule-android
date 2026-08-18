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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
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
@OptIn(ExperimentalMaterial3Api::class)
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
        val colors = MaterialTheme.colorScheme
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.surface)
                .safeDrawingPadding()
                .imePadding()
                .semantics {
                    this.paneTitle = title
                    isTraversalGroup = true
                },
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = AuleGlyph.BACK.asImageVector(),
                            contentDescription = stringResource(R.string.profile_close),
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

            // Pleine largeur, sans gouttière : le filet d'un `TabRow` sépare
            // l'en-tête du contenu, et un filet qui s'arrête avant le bord se
            // lit comme un trait décoratif.
            ProfileTabSwitcher(
                current = tab,
                onChanged = { tab = it },
                modifier = Modifier.padding(bottom = AuleSpacing.md),
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
    val colors = MaterialTheme.colorScheme
    val action = stringResource(
        if (hasAvatar) R.string.profile_avatar_edit else R.string.profile_avatar_add,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AuleSpacing.sm),
        shape = MaterialTheme.shapes.medium,
        // La page est déjà `surface` : une carte de la même couleur ne se
        // détache que par son ombre, invisible de nuit.
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(
            defaultElevation = AuleElevation.RESTING.height(AuleTheme.night),
        ),
    ) {
        Row(
            modifier = Modifier.padding(AuleSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                                .clip(MaterialTheme.shapes.small)
                                .background(colors.surface.copy(alpha = AuleAlpha.VEIL)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(AuleControl.icon),
                                color = colors.onSurface,
                                strokeWidth = AuleStroke.glyph,
                            )
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
                            .background(AuleTheme.tokens.accent.color)
                            .border(AuleStroke.emphasis, colors.surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (hasAvatar) {
                                AuleGlyph.EDIT.asImageVector()
                            } else {
                                AuleGlyph.CAMERA.asImageVector()
                            },
                            contentDescription = null,
                            tint = AuleTheme.tokens.onAccent.color,
                            modifier = Modifier.size(AuleSpacing.md),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(start = AuleSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                if (email != null) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarMenuDialog(
    hasAvatar: Boolean,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = AuleSpacing.lg)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_avatar_camera)) },
                leadingContent = {
                    Icon(
                        imageVector = AuleGlyph.CAMERA.asImageVector(),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable(onClick = onCamera),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_avatar_gallery)) },
                leadingContent = {
                    Icon(
                        imageVector = AuleGlyph.IMAGE.asImageVector(),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable(onClick = onGallery),
            )
            if (hasAvatar) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.profile_avatar_delete),
                            color = colors.error,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = AuleGlyph.TRASH.asImageVector(),
                            contentDescription = null,
                            tint = colors.error,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onDelete),
                )
            }
        }
    }
}

@Composable
private fun DeleteAvatarDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH),
        title = { Text(stringResource(R.string.profile_avatar_delete_title)) },
        text = { Text(stringResource(R.string.profile_avatar_delete_body)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                ),
            ) {
                Text(stringResource(R.string.profile_avatar_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_cancel))
            }
        },
    )
}

@Composable
private fun CameraPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH),
        title = { Text(stringResource(R.string.profile_avatar_permission_title)) },
        text = { Text(stringResource(R.string.profile_avatar_permission_camera)) },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = auleAccentButtonColors(),
            ) {
                Text(stringResource(R.string.profile_avatar_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_cancel))
            }
        },
    )
}

@Composable
private fun IdentityEditor(
    draft: ProfileDraft,
    enabled: Boolean,
    onChange: (ProfileDraft) -> Unit,
    onNext: () -> Unit,
) {
    ProfileSection(title = stringResource(R.string.profile_identity)) {
        Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
            OutlinedTextField(
                value = draft.firstName,
                onValueChange = { onChange(draft.copy(firstName = it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(stringResource(R.string.profile_first_name)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                value = draft.lastName,
                onValueChange = { onChange(draft.copy(lastName = it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(stringResource(R.string.profile_last_name)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                value = draft.phone,
                onValueChange = { onChange(draft.copy(phone = it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(stringResource(R.string.profile_phone)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                value = draft.driverNumber,
                onValueChange = { onChange(draft.copy(driverNumber = it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(stringResource(R.string.profile_driver_number)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onNext() }),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val chosen = options.find { it.first == selectedId }?.second
    val vacant = options.isEmpty()
    val shown = when {
        vacant -> empty
        chosen != null -> chosen
        else -> hint
    }
    val interactive = enabled && !vacant
    ExposedDropdownMenuBox(
        expanded = expanded && interactive,
        onExpandedChange = { if (interactive) expanded = it },
    ) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = interactive,
                )
                .fillMaxWidth(),
            readOnly = true,
            enabled = interactive,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && interactive)
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded && interactive,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (id, title) ->
                val selected = id == selectedId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) colors.primary else colors.onSurface,
                        )
                    },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
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
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.menu_sign_out)
    val deleteLabel = stringResource(R.string.profile_delete_account)
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        Text(
            text = stringResource(R.string.profile_account).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = AuleSpacing.xs),
        )
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            leadingContent = {
                Icon(
                    imageVector = AuleGlyph.SIGN_OUT.asImageVector(),
                    contentDescription = null,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSignOut)
                .semantics { contentDescription = label },
        )
        if (deleteFailed) {
            AuleBanner(
                message = stringResource(R.string.profile_delete_error),
                tone = AuleTone.ALERT,
            )
        }
        // La largeur d'une cible tactile, et non un espacement : c'est
        // exactement ce qu'on veut dire. Se déconnecter se rattrape en se
        // reconnectant ; supprimer son compte, non. Les huit points qui les
        // séparaient mettaient l'irréversible à un tremblement de doigt de
        // l'ordinaire.
        Spacer(Modifier.height(AuleTouch.minimum))
        TextButton(
            onClick = onDeleteAccount,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum),
            enabled = !deleting,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
        ) {
            if (deleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    color = colors.error,
                    strokeWidth = AuleStroke.glyph,
                )
            } else {
                Text(deleteLabel)
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
    val colors = MaterialTheme.colorScheme
    val notice = stringResource(R.string.profile_unsaved)
    Surface(
        // La barre n'existe que lorsque la saisie s'écarte du serveur : son
        // apparition est une information, pas un décor. Sans région vivante,
        // un lecteur d'écran qui a le doigt dans un champ ne saurait jamais
        // qu'il y a désormais quelque chose à enregistrer.
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.surface,
        border = BorderStroke(AuleStroke.hairline, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = AuleSpacing.lg,
                vertical = AuleSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = AuleTouch.minimum),
                    enabled = !saving,
                ) {
                    Text(stringResource(R.string.menu_cancel))
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AuleControl.height),
                    enabled = !saving,
                    colors = auleAccentButtonColors(),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AuleControl.icon),
                            color = AuleTheme.tokens.onAccent.color,
                            strokeWidth = AuleStroke.glyph,
                        )
                    } else {
                        Text(stringResource(R.string.profile_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = AuleSpacing.xs),
        )
        content()
    }
}

private enum class ProfileTab { PROFIL, PREFERENCES }

/**
 * La bascule entre les deux pages de l'écran.
 *
 * Des **onglets**, et non une barre segmentée : la page des préférences en
 * porte une, pour l'apparence, et les deux se retrouvaient empilées à trois
 * centimètres l'une de l'autre — même dessin, même taille, deux rôles
 * étrangers. L'une change de page, l'autre règle un réglage ; les confondre
 * fait chercher le retour en arrière après avoir voulu choisir « Sombre ».
 *
 * Material tranche d'ailleurs pareil : `TabRow` navigue, `SegmentedButton`
 * choisit dans un ensemble.
 */
@Composable
private fun ProfileTabSwitcher(
    current: ProfileTab,
    onChanged: (ProfileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = ProfileTab.entries
    PrimaryTabRow(
        selectedTabIndex = tabs.indexOf(current),
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = current == tab,
                onClick = { onChanged(tab) },
                modifier = Modifier.defaultMinSize(minHeight = AuleTouch.minimum),
                text = {
                    Text(
                        stringResource(
                            if (tab == ProfileTab.PROFIL) {
                                R.string.profile_title
                            } else {
                                R.string.profile_tab_preferences
                            },
                        ),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(
    appearance: AppearanceMode,
    onAppearance: (AppearanceMode) -> Unit,
) {
    val modes = AppearanceMode.entries
    ProfileSection(title = stringResource(R.string.profile_appearance)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = appearance == mode,
                    onClick = { onAppearance(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    icon = {
                        Icon(
                            imageVector = if (appearance == mode) {
                                AuleGlyph.CHECK.asImageVector(filled = true)
                            } else {
                                mode.glyph().asImageVector()
                            },
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(mode.labelRes())) },
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
    val colors = MaterialTheme.colorScheme
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
        // `surface` sur une page `surface`, sans ombre : la carte n'existait
        // que dans le code. Le cran de conteneur la pose pour de bon, comme
        // celle du portrait plus haut.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
            elevation = CardDefaults.cardElevation(
                defaultElevation = AuleElevation.RESTING.height(AuleTheme.night),
            ),
        ) {
            Column(
                modifier = Modifier.padding(AuleSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuleTouch.minimum),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AuleControl.icon),
                            color = colors.onSurfaceVariant,
                            strokeWidth = AuleStroke.glyph,
                        )
                    }
                } else {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (shareFailed) {
                    AuleBanner(
                        message = stringResource(R.string.profile_traces_share_failed),
                        tone = AuleTone.ALERT,
                    )
                }
                if (files.isNotEmpty()) {
                    Button(
                        onClick = onExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuleControl.height),
                        colors = auleAccentButtonColors(),
                    ) {
                        Text(stringResource(R.string.profile_traces_export))
                    }
                    // Même règle qu'au compte, et la même distance : une
                    // cible tactile entière. Douze points suffisaient à
                    // séparer deux blocs, pas à protéger un effacement
                    // définitif du doigt qui visait « Exporter ». Il est
                    // écrit en toutes lettres plutôt qu'en corbeille — une
                    // icône seule laisse deviner ce qu'elle emporte.
                    Spacer(Modifier.height(AuleTouch.minimum))
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuleTouch.minimum),
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                    ) {
                        Text(deleteLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH),
        title = { Text(stringResource(R.string.profile_delete_title)) },
        text = { Text(stringResource(R.string.profile_delete_body)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                ),
            ) {
                Text(stringResource(R.string.profile_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_cancel))
            }
        },
    )
}

@Composable
private fun ConfirmDeleteTracesDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val body = stringResource(
        if (count == 1) {
            R.string.profile_traces_delete_body_one
        } else {
            R.string.profile_traces_delete_body_many
        },
        count,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH),
        title = { Text(stringResource(R.string.profile_traces_delete_title)) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                ),
            ) {
                Text(stringResource(R.string.profile_avatar_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_cancel))
            }
        },
    )
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

private val DIALOG_MAX_WIDTH = 360.dp

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
