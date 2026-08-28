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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
// `ExposedDropdownMenu` s'importe explicitement depuis Material 3 1.5 : la
// version membre d'`ExposedDropdownMenuBoxScope` y est passée en obsolescence
// masquée (`DeprecationLevel.HIDDEN`) au profit de cette extension. Sans cet
// import, l'appel ne résout plus — et l'erreur ne dit pas que c'est une
// dépréciation, elle dit que le symbole n'existe pas.
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBrandSurface
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
import kotlin.math.ceil
import kotlinx.coroutines.launch

/**
 * La fiche agent, éditable, et les préférences de l'appareil.
 *
 * Deux onglets, comme Flutter : la fiche se soumet **en bloc**, l'apparence
 * s'applique au toucher. La déconnexion reste sans confirmation ; la
 * suppression de compte, elle, demande un second geste.
 *
 * ## La hiérarchie de l'écran, en trois plans
 *
 * L'écran est dense — identité, portrait, quatre champs, deux listes
 * déroulantes, apparence, traces, compte — et il l'était **à plat** : chaque
 * bloc s'annonçait par un intitulé gris de onze points et rien d'autre. Deux
 * cartes seulement avaient un fond, le portrait et les traces, si bien que
 * l'écran se lisait comme une longue liste de champs entrecoupée de deux
 * boîtes. On y trouvait tout, à condition de chercher. Trois plans le
 * rangent :
 *
 * 1. **L'en-tête d'identité** est la seule surface de marque de l'écran. Un
 *    conducteur qui ouvre son profil vient vérifier *qui il est pour le
 *    système* : son nom, son adresse, son portrait. Le dégradé teal le pose au
 *    premier plan sans qu'aucun autre bloc n'ait à s'effacer.
 * 2. **Les sections** vivent sur un cran de conteneur, pas dans un contour. La
 *    nouvelle échelle de surfaces descend assez bas pour qu'un aplat suffise —
 *    et un bloc sans contour se lit comme un bloc, là où le même cerné se lit
 *    comme une boîte. Elles se distinguent donc du fond de la même façon de
 *    jour et de nuit, ce qu'un trait ne faisait pas.
 * 3. **La barre d'enregistrement** monte d'un cran encore, et porte l'ombre :
 *    elle survient, elle ne fait pas partie de la page.
 *
 * L'écran porte la bascule Clair / Sombre / Auto : c'est le seul de
 * l'application qu'on regarde forcément dans les deux ambiances, à une seconde
 * d'intervalle. Rien n'y est réglé pour le jour puis rattrapé pour la nuit —
 * la surface de marque est un teal profond dans les deux, l'échelle de
 * conteneurs monte dans les deux, et la tuile choisie prend l'aplat HUD dans
 * les deux.
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
                            // Le slot appuyé de Material 3 Expressive : même
                            // boîte que `titleMedium`, donc la barre ne bouge
                            // pas d'un point, mais la graisse sépare enfin le
                            // titre de sa légende. Les deux se lisaient au même
                            // poids, à quatre points d'écart de taille.
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
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
                // Les rangs de la cascade sont fixes et suivent l'ordre de
                // lecture : c'est ce qui fait qu'un même bloc entre toujours au
                // même moment, que les bandeaux d'erreur soient là ou non. Un
                // rang calculé sur les blocs présents ferait avancer la section
                // Compte de deux crans dès qu'un envoi de photo échoue, et la
                // cascade se mettrait à dépendre de l'état du réseau.
                when (tab) {
                    ProfileTab.PROFIL -> {
                        val headerName = draft.displayName()
                            .ifBlank { state.profile?.displayName().orEmpty() }
                            .ifBlank { stringResource(R.string.profile_agent) }
                        val avatarError = state.avatarFailure?.message()
                            ?: pickerMessage?.let { stringResource(it) }
                        IdentityHeader(
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
                            modifier = Modifier.auleEnter(index = 0),
                        )
                        if (avatarError != null) {
                            AuleBanner(
                                message = avatarError,
                                tone = AuleTone.ALERT,
                                modifier = Modifier.auleEnter(index = 1),
                            )
                        }

                        when {
                            state.isLoadingProfile && state.profile == null -> AuleBanner(
                                message = stringResource(R.string.profile_loading),
                                modifier = Modifier.auleEnter(index = 2),
                            )
                            state.profileFailed -> AuleBanner(
                                message = stringResource(R.string.profile_load_error),
                                tone = AuleTone.ALERT,
                                action = stringResource(R.string.profile_retry),
                                onAction = viewModel::retryProfile,
                                modifier = Modifier.auleEnter(index = 2),
                            )
                            state.profile == null -> AuleBanner(
                                message = stringResource(R.string.profile_missing),
                                modifier = Modifier.auleEnter(index = 2),
                            )
                            else -> {
                                if (state.profileSaveFailed) {
                                    AuleBanner(
                                        message = stringResource(R.string.profile_save_error),
                                        tone = AuleTone.ALERT,
                                        modifier = Modifier.auleEnter(index = 2),
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
                                    modifier = Modifier.auleEnter(index = 2),
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
                                    modifier = Modifier.auleEnter(index = 3),
                                )
                            }
                        }

                        AccountSection(
                            onSignOut = viewModel::signOut,
                            onDeleteAccount = { confirmingDeleteAccount = true },
                            deleting = state.isDeletingAccount,
                            deleteFailed = state.deleteFailed,
                            modifier = Modifier.auleEnter(index = 4),
                        )
                    }
                    ProfileTab.PREFERENCES -> {
                        AppearanceSection(
                            appearance = appearance,
                            onAppearance = onAppearance,
                            modifier = Modifier.auleEnter(index = 0),
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
                            modifier = Modifier.auleEnter(index = 1),
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

/**
 * L'en-tête d'identité : la seule surface de marque de l'écran.
 *
 * C'était une carte grise avec un portrait dedans, posée sur une page de la
 * même famille de gris. Elle portait pourtant ce que le conducteur vient
 * vérifier — son nom tel que le réseau l'écrit — et rien ne le disait. Le
 * dégradé teal le dit d'un coup d'œil, et il le dit **de jour comme de nuit** :
 * l'aplat de marque est un teal profond dans les deux ambiances, seule son
 * encre change.
 *
 * ## Ce qui empêche la carte de tourner à la décoration
 *
 * Une seule surface de marque par écran, dit le kit, et c'est celle-ci. Rien
 * d'autre ici n'est dégradé : les sections restent des aplats de conteneur.
 * Sans cette règle, le teal cesserait d'être un rang pour devenir un fond, et
 * l'en-tête ne désignerait plus rien.
 *
 * ## Le portrait et sa pastille, retournés
 *
 * Les deux aplats habituels ne fonctionnent pas sur un fond de marque :
 * `primaryContainer` **est** la couleur d'arrivée du dégradé de nuit, et la
 * pastille caméra était peinte à l'accent, c'est-à-dire à la couleur sur
 * laquelle elle se pose maintenant. Les deux prennent donc l'encre de marque en
 * aplat et l'aplat de marque en encre : une tuile claire, un glyphe teal, et le
 * contraste est le même aux deux heures. La bordure de la pastille disparaît
 * avec ce retournement — elle servait à détacher un rond teal d'un fond gris,
 * un problème que cet en-tête n'a plus.
 */
@Composable
private fun IdentityHeader(
    name: String,
    email: String?,
    bytes: ByteArray?,
    hasAvatar: Boolean,
    uploading: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AuleTheme.tokens
    val action = stringResource(
        if (hasAvatar) R.string.profile_avatar_edit else R.string.profile_avatar_add,
    )
    AuleBrandSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = AuleElevation.FLOATING,
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
                    AvatarPortrait(
                        name = name,
                        bytes = bytes,
                        container = tokens.onAccent.color,
                        onContainer = tokens.accent.color,
                    )
                    if (uploading) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(MaterialTheme.shapes.small)
                                .background(tokens.accent.color.copy(alpha = AuleAlpha.VEIL)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(AuleControl.icon),
                                color = tokens.onAccent.color,
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
                            .background(tokens.onAccent.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (hasAvatar) {
                                AuleGlyph.EDIT.asImageVector()
                            } else {
                                AuleGlyph.CAMERA.asImageVector()
                            },
                            contentDescription = null,
                            tint = tokens.accent.color,
                            modifier = Modifier.size(AuleSpacing.md),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = AuleSpacing.md)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                Text(
                    text = name,
                    // Deux crans au-dessus de ce qu'il était. Le nom est le
                    // sujet de l'écran ; à seize points il avait la taille d'un
                    // intitulé de champ, et l'en-tête ressemblait à une rangée
                    // de liste qu'on aurait agrandie.
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = tokens.onAccent.color,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (email != null) {
                    // Pleine encre, et non un voile : sur une surface de marque
                    // lue en plein soleil, un texte atténué disparaît avant
                    // d'être secondaire. C'est l'écart de taille qui hiérarchise
                    // ici, pas l'opacité.
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.onAccent.color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
    // `enabledValues` sans `PartiallyExpanded` **est** l'ancien
    // `skipPartiallyExpanded = true` : l'état en dérive
    // (`SheetState.skipPartiallyExpanded`), et le palier intermédiaire n'existe
    // alors plus du tout. Migration à comportement identique.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = AuleSpacing.lg)) {
            // Le conteneur transparent n'est pas un détail : un `ListItem`
            // peint `surface` par défaut, or le volet est posé sur
            // `surfaceContainerLow`. Trois rangées blanches sur un volet gris
            // clair, c'est trois rectangles qu'on voit avant de lire ce qu'ils
            // contiennent — et de nuit, l'inverse.
            //
            // La cascade, elle, sert à ce que le volet **arrive** plutôt qu'il
            // n'apparaisse : trois rangées qui se déroulent en cent vingt
            // millisecondes disent d'où elles viennent.
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_avatar_camera)) },
                leadingContent = {
                    Icon(
                        imageVector = AuleGlyph.CAMERA.asImageVector(),
                        contentDescription = null,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .auleEnter(index = 0)
                    .clickable(onClick = onCamera),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_avatar_gallery)) },
                leadingContent = {
                    Icon(
                        imageVector = AuleGlyph.IMAGE.asImageVector(),
                        contentDescription = null,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .auleEnter(index = 1)
                    .clickable(onClick = onGallery),
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .auleEnter(index = 2)
                        .clickable(onClick = onDelete),
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
    modifier: Modifier = Modifier,
) {
    ProfileSection(
        title = stringResource(R.string.profile_identity),
        modifier = modifier,
    ) {
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

@Composable
private fun AssignmentEditor(
    draft: ProfileDraft,
    depots: List<Depot>,
    networks: List<TransportNetwork>,
    enabled: Boolean,
    onChange: (ProfileDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = depots.forNetwork(draft.networkId)
    ProfileSection(
        title = stringResource(R.string.profile_assignment),
        modifier = modifier,
    ) {
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
                        // La graisse vient du slot appuyé et non d'un
                        // `fontWeight` posé à la main : un menu de dépôts en
                        // compte une vingtaine, et c'est exactement le genre
                        // d'endroit où un « SemiBold » local finit par
                        // diverger de celui du menu d'à côté.
                        Text(
                            text = title,
                            style = if (selected) {
                                MaterialTheme.typography.bodyLargeEmphasized
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
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

/**
 * Le compte : une rangée ordinaire, puis ce qui ne l'est pas.
 *
 * La déconnexion vit dans le cartouche de la section, avec le fond, l'ondulation
 * et la cible tactile d'une rangée de réglage. La suppression de compte, elle,
 * reste **hors** du cartouche, à même la page : ce n'est pas un réglage, et rien
 * ne doit laisser croire qu'elle appartient au même ensemble que les autres.
 */
@Composable
private fun AccountSection(
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    deleting: Boolean,
    deleteFailed: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.menu_sign_out)
    val deleteLabel = stringResource(R.string.profile_delete_account)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        ProfileSection(
            title = stringResource(R.string.profile_account),
            // Sans marge intérieure : un `ListItem` porte déjà la sienne, et la
            // rangée doit toucher les bords du cartouche pour que l'ondulation
            // couvre la largeur qu'on croit toucher. Le cartouche découpe à sa
            // forme, donc l'ondulation s'arrête aux angles arrondis.
            contentPadding = 0.dp,
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = AuleGlyph.SIGN_OUT.asImageVector(),
                        contentDescription = null,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSignOut)
                    .semantics { contentDescription = label },
            )
        }
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

/**
 * La barre d'enregistrement : ce qui survient, et qu'on ne peut pas manquer.
 *
 * Elle n'existe que lorsque la saisie s'écarte du serveur, donc son apparition
 * est une information et non un décor. D'où la région vivante, sans laquelle un
 * lecteur d'écran qui a le doigt dans un champ ne saurait jamais qu'il y a
 * désormais quelque chose à enregistrer — et d'où l'entrée en ressort, qui la
 * fait monter à sa place plutôt que se téléporter sous le contenu.
 *
 * Le filet d'un point qui la séparait de la page a disparu. Il compensait une
 * barre de la **même** couleur que la page : sans lui, elle n'existait pas.
 * Deux crans de conteneur au-dessus de la page et l'ombre du kit disent la même
 * chose en la disant mieux — un trait sépare, une ombre éloigne, et c'est bien
 * d'éloignement qu'il s'agit ici.
 */
@Composable
private fun SaveBar(
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val notice = stringResource(R.string.profile_unsaved)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .auleEnter()
            .auleShadow(AuleElevation.RESTING, RectangleShape)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
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
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = colors.onSurface,
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

/**
 * Une section : un intitulé, puis un cartouche.
 *
 * L'intitulé seul ne suffisait pas. Onze points de gris au-dessus de contenus
 * posés à même la page, c'est une table des matières, pas une hiérarchie : rien
 * ne dit où une section finit, et l'écran se lit comme une liste continue de
 * champs. Le cartouche donne à la section un **corps**, et c'est ce corps qu'on
 * balaie du pouce quand on cherche « Affectation » plutôt que de lire les
 * intitulés un à un.
 *
 * Il est un aplat et non un contour, et c'est neuf : la précédente échelle de
 * surfaces tenait dans six centièmes de clarté, où un cartouche sans trait
 * n'existait pas. Elle descend maintenant jusqu'à un gris franc, donc le cran
 * suffit — de jour comme de nuit, ce qu'un trait d'un point ne faisait pas.
 *
 * L'intitulé est annoncé comme un titre. C'est ce qui permet à TalkBack de
 * sauter d'« Identité » à « Affectation » sans traverser quatre champs de
 * saisie, sur un écran qui en compte six.
 *
 * @param contentPadding la marge intérieure du cartouche. Nulle pour une
 *   section faite de rangées, qui portent la leur et doivent toucher les bords.
 */
@Composable
private fun ProfileSection(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: Dp = AuleSpacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .padding(start = AuleSpacing.md)
                .semantics { heading() },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainer,
            contentColor = colors.onSurface,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
                content = content,
            )
        }
    }
}

private enum class ProfileTab { PROFIL, PREFERENCES }

/**
 * La bascule entre les deux pages de l'écran.
 *
 * Des **onglets**, et non une barre segmentée : la page des préférences porte
 * elle aussi un choix — l'apparence — et les deux se retrouvaient empilées à
 * trois centimètres l'une de l'autre. L'une change de page, l'autre règle un
 * réglage ; les confondre fait chercher le retour en arrière après avoir voulu
 * choisir « Sombre ».
 *
 * Material tranche d'ailleurs pareil : `TabRow` navigue, et ce qui choisit dans
 * un ensemble se dessine autrement. Le choix d'apparence est maintenant fait de
 * tuiles, ce qui met entre les deux contrôles la distance que ce commentaire
 * réclamait depuis le début — la barre segmentée gardait la silhouette d'une
 * rangée d'onglets, des tuiles n'en ont plus rien.
 *
 * L'onglet actif prend le slot appuyé. La barre d'indicateur dit déjà où l'on
 * est, mais elle le dit **sous** le texte : la graisse le dit dans le texte, et
 * c'est ce qu'on lit d'abord.
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
            val selected = current == tab
            Tab(
                selected = selected,
                onClick = { onChanged(tab) },
                modifier = Modifier.defaultMinSize(minHeight = AuleTouch.minimum),
                text = {
                    Text(
                        text = stringResource(
                            if (tab == ProfileTab.PROFIL) {
                                R.string.profile_title
                            } else {
                                R.string.profile_tab_preferences
                            },
                        ),
                        style = if (selected) {
                            MaterialTheme.typography.labelLargeEmphasized
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                    )
                },
            )
        }
    }
}

/**
 * Le choix d'ambiance : trois tuiles, dont une allumée.
 *
 * C'était une barre segmentée, et elle avait deux défauts qui ne se voient
 * qu'à l'usage. Le premier : elle ressemblait aux onglets posés vingt points
 * plus haut — même hauteur, même dessin, deux rôles étrangers. Le second, plus
 * gênant : le segment sélectionné remplaçait l'icône du mode par une coche,
 * donc le mode courant était le seul des trois à ne plus montrer son soleil ou
 * sa lune. On perdait le repère exactement là où on venait le chercher.
 *
 * Les tuiles gardent l'icône du mode en toutes circonstances — pleine quand le
 * mode est choisi, ajourée sinon — et disent la sélection par l'aplat de
 * marque, qui est aussi ce que porte l'action principale ailleurs dans
 * l'application. Elles offrent au passage une cible de la hauteur d'un bouton
 * plutôt que d'un tiers de barre, ce qui compte pour un pouce ganté.
 */
@Composable
private fun AppearanceSection(
    appearance: AppearanceMode,
    onAppearance: (AppearanceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = AppearanceMode.entries
    ProfileSection(
        title = stringResource(R.string.profile_appearance),
        modifier = modifier,
    ) {
        Row(
            // `selectableGroup` : TalkBack annonce « 2 sur 3 » au lieu de trois
            // boutons sans rapport. C'est ce que la barre segmentée faisait
            // toute seule, et c'est la seule chose qu'il faut lui reprendre.
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            modes.forEach { mode ->
                AppearanceTile(
                    mode = mode,
                    selected = appearance == mode,
                    onSelect = { onAppearance(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Une tuile d'ambiance.
 *
 * Les deux couleurs sont **animées**, sur le régime d'effets du schéma
 * expressif. Un choix d'apparence bascule le thème de l'application entière au
 * même instant : si la tuile changeait sèchement de couleur pendant que le
 * fond, lui, est repeint par la recomposition, le geste donnerait deux
 * événements pour un seul appui. Le ressort d'effets — celui qui ne dépasse
 * jamais sa cible, contrairement au spatial — les remet ensemble.
 *
 * L'ombre teintée n'apparaît que sous la tuile choisie, et elle porte la
 * couleur de la marque plutôt que du noir : c'est la même lueur que sous
 * l'action principale de l'écran. Une ombre neutre sous un aplat teal le
 * salirait.
 */
@Composable
private fun AppearanceTile(
    mode: AppearanceMode,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tokens = AuleTheme.tokens
    val shape = MaterialTheme.shapes.medium
    val container by animateColorAsState(
        targetValue = if (selected) tokens.accent.color else colors.surfaceContainerHighest,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "fond de la tuile d'ambiance",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) tokens.onAccent.color else colors.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "encre de la tuile d'ambiance",
    )
    Surface(
        // L'ordre compte : l'ombre se pose autour de la tuile, le découpage
        // arrive ensuite, et l'ondulation naît à l'intérieur du découpage.
        // Inversés, l'ondulation déborderait des angles arrondis.
        modifier = modifier
            .auleShadow(
                level = if (selected) AuleElevation.RESTING else AuleElevation.NONE,
                shape = shape,
                tint = AuleShadowTint.ACCENT,
            )
            .clip(shape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        shape = shape,
        color = container,
        contentColor = ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .padding(vertical = AuleSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        ) {
            Icon(
                imageVector = mode.glyph().asImageVector(filled = selected),
                contentDescription = null,
                modifier = Modifier.size(AuleControl.icon),
            )
            // Sans `maxLines` : aux tailles de police d'accessibilité,
            // « Sombre » ne tient plus sur un tiers de largeur, et un mode
            // d'affichage tronqué en « Somb… » est pire qu'une tuile plus
            // haute que ses voisines. L'alignement centré tient le texte en
            // place quand il passe à deux lignes.
            Text(
                text = stringResource(mode.labelRes()),
                style = if (selected) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
                textAlign = TextAlign.Center,
            )
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
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val deleteLabel = stringResource(R.string.profile_traces_delete)
    val summary = when {
        loading -> stringResource(R.string.profile_loading)
        files.isEmpty() && enabled -> stringResource(R.string.profile_traces_empty)
        files.isEmpty() -> stringResource(R.string.profile_traces_disabled)
        else -> {
            // **Jamais zéro.** Une trace de trente secondes pèse quatre cents
            // octets : « 1 trace · 0 Ko » se lit comme un fichier vide, donc
            // comme une panne, alors que le fichier est bon. Même règle que
            // les minutes de marche, qui ne descendent pas sous une.
            val kilos = ceil(files.sumOf { it.bytes } / 1024.0)
                .toInt()
                .coerceAtLeast(1)
                .toString()
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
    ProfileSection(
        title = stringResource(R.string.profile_traces),
        modifier = modifier,
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
            // Le décompte est la seule donnée de la section : il monte d'un
            // cran de taille. Quatorze points de gris, c'était une note de bas
            // de page pour la seule ligne que quelqu'un vient lire ici.
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
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
            // Même règle qu'au compte, et la même distance : une cible
            // tactile entière. Douze points suffisaient à séparer deux blocs,
            // pas à protéger un effacement définitif du doigt qui visait
            // « Exporter ». Il est écrit en toutes lettres plutôt qu'en
            // corbeille — une icône seule laisse deviner ce qu'elle emporte.
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
