package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleGlassSurface
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleChrome
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.location.LocationAuthorization
import io.aule.android.core.map.camera.CameraMode
import io.aule.android.core.model.DepartureWatch

/**
 * Ce qui flotte au-dessus de la carte.
 *
 * Pastilles en haut hors guidage, bandeau de guidage à la même place dès qu'un
 * trajet est engagé ; **la recherche en bas**, et la barre d'arrivée à sa place
 * quand on roule. Le bouton de cadrage reste : après un geste, c'est lui qui
 * rend la navigation.
 *
 * ## Ce que le HUD ne porte plus
 *
 * La recherche a vécu ici, en barre flottante. Elle est devenue un **volet** —
 * le socle, porté par le `BottomSheetScaffold` de `MapScreen`, comme sur iOS.
 * Un HUD pose ce qui flotte au-dessus de la carte ; un volet n'en est pas, et
 * le garder ici aurait demandé de tenir sa hauteur à deux endroits. Voir
 * [MapSearchSheet].
 */
@Composable
internal fun MapHud(
    state: MapUiState,
    authorization: LocationAuthorization,
    lastLocationError: String?,
    onShowNearby: () -> Unit,
    onRetryStops: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPrecise: () -> Unit,
    onOpenTrip: () -> Unit = {},
    onSummaryHeightPx: (Float) -> Unit = {},
    serviceBanner: String? = null,
    serviceBannerAction: String? = null,
    onServiceBannerAction: (() -> Unit)? = null,
    /**
     * La ligne dont on attend le passage, quand une veille tourne.
     *
     * Elle vient d'ailleurs que [state] — d'un modèle qui bat toutes les trente
     * secondes — et c'est pour cela qu'elle est passée à part : le HUD n'a
     * besoin que de son existence et de son nom, pas de son tableau.
     */
    watch: DepartureWatch? = null,
    onOpenWatch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val navigating = state.isNavigating
    val showingTrip = state.navigation?.showingTrip == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // La gouttière du haut est **serrée d'un cran**, et c'est de la carte
        // qu'on récupère : quatre points au-dessus de ce qui s'y pose, sur
        // toute la largeur de l'écran.
        //
        // L'écart **entre** les surfaces, lui, reste à huit : ce sont des
        // cibles tactiles distinctes, et les serrer davantage ferait viser
        // entre deux pastilles — le genre d'erreur qu'on ne fait qu'avec des
        // gants, c'est-à-dire ici.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuleSpacing.lg)
                .padding(top = AuleSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            if (serviceBanner != null) {
                AuleBanner(
                    message = serviceBanner,
                    action = serviceBannerAction,
                    onAction = onServiceBannerAction,
                )
            }
            if (navigating) {
                val navigation = state.navigation
                if (navigation != null) {
                    GuidanceBanner(state = navigation)
                }
            } else if (!state.search.isActive) {
                if (watch != null) {
                    WatchPill(watch = watch, onClick = onOpenWatch)
                }
                if (state.showsFleetStatus) {
                    FleetStatusPill(
                        label = state.fleetStatus.label(),
                        isLive = state.fleetStatus is io.aule.android.core.model.FleetStatus.LiveOnly ||
                            state.fleetStatus is io.aule.android.core.model.FleetStatus.Mixed,
                        onClick = onShowNearby,
                    )
                }
                IssueBanner(
                    state = state,
                    authorization = authorization,
                    lastLocationError = lastLocationError,
                    onRetryStops = onRetryStops,
                    onOpenSettings = onOpenSettings,
                    onRequestPrecise = onRequestPrecise,
                )
            }
        }

        Box(modifier = Modifier.weight(1f))

        // La bande du bas appartient au **volet** — au socle de recherche hors
        // guidage, à la barre d'arrivée dès qu'on roule. Le HUD n'y pose que la
        // seconde : la première est un volet, et un volet ne flotte pas.
        val navigation = state.navigation
        if (navigating && !showingTrip && navigation != null) {
            // La bande est mesurée **entière**, cadran compris : c'est elle qui
            // dit à la caméra quelle hauteur d'écran est masquée. Ne remonter
            // que la barre ferait poser le puck derrière le cadran.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { onSummaryHeightPx(it.height.toFloat()) },
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                // **Centré, et c'est une correction.** Le cadran était aligné au
                // début de la bande ; sur le S21 il passait sous la pastille ⓘ,
                // qui occupe ce coin-là. Les deux extrémités de cette bande sont
                // prises — la mention légale à gauche, le cadrage à droite — et
                // le milieu est le seul endroit libre. Un alignement plutôt
                // qu'une marge chiffrée : la pastille reste dégagée quelle que
                // soit la largeur de l'écran.
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val speed = navigation.speedKmh
                if (speed != null) {
                    SpeedPill(kmh = speed)
                }
                TripSummaryBar(
                    summary = navigation.summary,
                    onOpen = onOpenTrip,
                )
            }
        }
    }
}

/**
 * Le cadran de vitesse.
 *
 * ## Pourquoi il est petit, et pourquoi il est là
 *
 * C'est le chiffre qu'on lit **sans quitter la route des yeux** : on le prend
 * en vision périphérique, on ne le cherche pas. Il n'a donc pas besoin de
 * place, il a besoin d'une position stable — la bande basse, sous le bandeau de
 * consigne et au-dessus de la barre d'arrivée.
 *
 * Au milieu de cette bande, parce que ses deux extrémités sont déjà prises : la
 * mention légale à gauche, le cadrage à droite. Aligné au début, il passait
 * sous la pastille ⓘ — relevé sur le S21, invisible partout ailleurs.
 *
 * ## Le chiffre et son unité ne pèsent pas pareil
 *
 * « 50 » est ce qu'on lit ; « km/h » est ce qu'on a lu une fois pour toutes.
 * Le premier prend le slot appuyé, à chasse fixe pour que le passage de 49 à
 * 50 ne fasse pas danser la pastille ; la seconde reste une étiquette.
 *
 * TalkBack, lui, reçoit une phrase entière : lire « cinquante » puis
 * « kilomètres heure » comme deux éléments distincts n'apprend rien.
 */
@Composable
private fun SpeedPill(kmh: Int) {
    val spoken = stringResource(R.string.nav_speed_a11y, kmh)
    AuleCappedFontScale(maxScale = 1.3f) {
        AuleGlassSurface(
            modifier = Modifier
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = spoken
                },
            shape = MaterialTheme.shapes.large,
            elevation = AuleElevation.FLOATING,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = AuleSpacing.md,
                    vertical = AuleSpacing.sm,
                ),
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(R.string.nav_speed_value, kmh),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    text = stringResource(R.string.nav_speed_unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * La preuve qu'une veille tourne, et le chemin pour la couper.
 *
 * Une alerte armée qu'on ne voit nulle part est une promesse invérifiable :
 * l'usager qui a refermé le volet n'a plus aucun moyen de savoir si l'app
 * surveille encore, ni de lui dire d'arrêter. La pastille le dit, et ramène
 * d'un geste à la ligne — là où « Ne plus suivre » attend.
 *
 * Elle ne montre pas de compteur, alors qu'elle en aurait la place. Ce serait
 * un deuxième chiffre vivant sur un écran qui en a déjà — celui du volet — et
 * il faudrait une horloge dans le HUD pour le tenir à jour. La pastille répond
 * à « est-ce que ça tourne, et sur quoi », pas à « dans combien de temps » :
 * cette question-là a déjà son écran.
 */
@Composable
private fun WatchPill(watch: DepartureWatch, onClick: () -> Unit) {
    val label = stringResource(R.string.watch_pill, watch.line, watch.destination)
    val hint = stringResource(R.string.watch_pill_hint)
    val colors = MaterialTheme.colorScheme
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = colors.surface,
            labelColor = colors.onSurface,
            leadingIconContentColor = realtimeInk(),
        ),
        border = null,
        modifier = Modifier.semantics {
            contentDescription = label
            onClick(label = hint, action = null)
        },
    )
}

@Composable
private fun FleetStatusPill(
    label: String,
    isLive: Boolean,
    onClick: () -> Unit,
) {
    val hint = stringResource(R.string.fleet_nearby_hint)
    val colors = MaterialTheme.colorScheme
    AssistChip(
        onClick = onClick,
        // Le libellé descend d'un cran, appuyé. Une pastille d'état n'est pas
        // une commande : elle répond à « est-ce que ça vit ? » d'un coup d'œil
        // et rien ne se joue si on ne la lit pas. Au corps d'un libellé de
        // bouton, elle se disputait le bandeau posé juste au-dessus.
        label = {
            Text(text = label, style = MaterialTheme.typography.labelMediumEmphasized)
        },
        leadingIcon = {
            RealtimeDot(
                isLive = isLive,
                liveDescription = label,
                scheduledDescription = label,
            )
        },
        // Une puce transparente posée sur une carte se lit sur ce qui passe
        // dessous : au-dessus d'un toit sombre, « 22 à l'horaire » disparaissait.
        // Le verre lui donne un fond sans la couper de la ville, et l'ombre la
        // pose franchement au-dessus plutôt que dedans.
        colors = AssistChipDefaults.assistChipColors(
            containerColor = colors.surface.copy(alpha = AuleAlpha.GLASS),
            labelColor = colors.onSurface,
        ),
        border = BorderStroke(AuleStroke.hairline, colors.outlineVariant),
        modifier = Modifier
            .auleShadow(AuleElevation.RESTING, AssistChipDefaults.shape)
            .semantics { contentDescription = "$label. $hint" },
    )
}

/**
 * Ce qui ne va pas, et ce qu'on peut y faire.
 *
 * Un seul incident à la fois, le plus grave : trois bandeaux empilés ne se
 * lisent pas. Par ordre : sans fond de carte rien n'est lisible, sans arrêts
 * la carte ment par omission, sans position elle reste utilisable mais ne
 * suit plus.
 */
@Composable
private fun IssueBanner(
    state: MapUiState,
    authorization: LocationAuthorization,
    lastLocationError: String?,
    onRetryStops: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPrecise: () -> Unit,
) {
    val issue = when {
        state.mapError != null -> BannerIssue(
            message = stringResource(R.string.issue_map, state.mapError),
            action = null,
            onAction = null,
        )
        state.stopsFailure != null -> BannerIssue(
            message = stringResource(R.string.issue_stops, state.stopsFailure),
            action = stringResource(
                if (state.isLoadingStops) R.string.issue_retrying else R.string.issue_retry,
            ),
            onAction = if (state.isLoadingStops) null else onRetryStops,
        )
        authorization == LocationAuthorization.DENIED ||
            authorization == LocationAuthorization.SERVICES_DISABLED -> BannerIssue(
            message = stringResource(R.string.map_location_denied),
            action = stringResource(R.string.map_location_settings),
            onAction = onOpenSettings,
        )
        authorization == LocationAuthorization.REDUCED_ACCURACY -> BannerIssue(
            message = stringResource(R.string.map_location_reduced),
            action = stringResource(R.string.map_location_allow),
            onAction = onRequestPrecise,
        )
        lastLocationError != null -> BannerIssue(
            message = lastLocationError,
            action = null,
            onAction = null,
        )
        else -> null
    } ?: return

    AuleBanner(
        message = issue.message,
        action = issue.action,
        onAction = issue.onAction,
    )
}

/**
 * Le bouton qui rend la carte à l'utilisateur.
 *
 * Il est passé au **verre**, comme les pastilles au-dessus de lui. L'aplat
 * plein qu'il portait le faisait lire comme un bouton d'action de plus, à
 * quelques centimètres du FAB qui, lui, en est un : deux disques opaques
 * empilés dans le même coin, dont un seul engage quelque chose. Le verre le
 * range avec ce qu'il est — un contrôle de la vue, posé sur la carte et qui
 * la laisse voir dessous — et rend l'aplat plein au seul bouton qui agit.
 *
 * Le suivi reste visible : le verre prend la teinte de la marque et le contour
 * s'allume, plutôt que de repeindre le disque entier.
 */
@Composable
internal fun RecenterButton(
    mode: CameraMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val active = mode.followsSomething
    val label = stringResource(
        if (active) R.string.map_orient else R.string.map_recenter,
    )
    val colors = MaterialTheme.colorScheme
    val tokens = AuleTheme.tokens
    val shape = FloatingActionButtonDefaults.smallShape
    val fill = if (active) tokens.accent.color else colors.surface
    val glyph = if (active) tokens.onAccent.color else colors.onSurface
    val edge = if (active) tokens.accent.color else colors.outlineVariant

    SmallFloatingActionButton(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        // L'ombre du composant est éteinte au profit de celle du design system :
        // quand le bouton suit le véhicule, elle prend la teinte de la marque et
        // le bouton cesse d'être posé sur la carte pour se mettre à y flotter.
        // Les deux ombres cumulées donneraient un halo deux fois trop lourd.
        //
        // Le contour, lui, n'est pas une décoration : sur du verre, c'est lui
        // qui tient le bord du bouton au-dessus d'une tuile claire, là où
        // l'aplat opaque se suffisait à lui-même.
        modifier = modifier
            .auleShadow(
                level = AuleElevation.FLOATING,
                shape = shape,
                tint = if (active) AuleShadowTint.ACCENT else AuleShadowTint.NEUTRAL,
            )
            .border(AuleStroke.hairline, edge, shape),
        shape = shape,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        ),
        containerColor = fill.copy(alpha = AuleAlpha.GLASS),
        contentColor = glyph,
    ) {
        Icon(
            imageVector = AuleGlyph.HEADING.asImageVector(filled = active),
            contentDescription = label,
        )
    }
}

/**
 * Les FAB du coin bas droit, et la pastille légale qui leur fait face.
 *
 * Il n'y a plus de barre en bas. Elle occupait toute la largeur et quatre-vingts
 * points de haut pour trois entrées, dont une — Découvrir — ne menait qu'à
 * l'écran déjà affiché : une destination qui ne déplace personne. Signaler et
 * Correspondances ont rejoint le menu flottant, où elles voisinent avec le
 * service, la relève et l'itinéraire. Tout ce qu'on peut faire depuis la carte
 * tient désormais sous un seul bouton, et la carte récupère son bord bas.
 *
 * Le cadrage disparaît le temps que le menu soit ouvert. Il viendrait se
 * ranger au milieu des actions déployées, avec la même forme et la même
 * taille qu'elles, sans en être une. La pastille légale part avec lui : sous
 * un voile de menu, elle serait touchable sans être lisible.
 *
 * Elle occupe le coin gauche que le copyright natif de MapLibre laissait vide,
 * et ce n'est pas une coïncidence — c'est exactement ce qu'elle remplace.
 */
@Composable
internal fun MapActionChrome(
    cameraMode: CameraMode,
    onRecenter: () -> Unit,
    onOpenLegal: () -> Unit,
    actions: List<MapFabAction> = emptyList(),
    fabExpanded: Boolean = false,
    onFabExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Plus rien ne couvre le bas de la fenêtre : les FAB écartent
            // eux-mêmes la barre système, là où la barre de navigation s'en
            // chargeait pour eux.
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )
            .padding(bottom = AuleSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        horizontalAlignment = Alignment.End,
    ) {
        if (!fabExpanded) {
            Row(
                // La gouttière est posée ici et non sur la colonne : le menu
                // flottant porte déjà la sienne, à la même valeur, et les deux
                // s'additionnaient. Le bouton d'action se retrouvait à trente-deux
                // points du bord quand le cadrage juste au-dessus était à seize —
                // deux cibles du même coin, décalées l'une de l'autre sans raison.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegalNoticeButton(onClick = onOpenLegal)
                RecenterButton(mode = cameraMode, onClick = onRecenter)
            }
        }
        MapFabMenu(
            actions = actions,
            expanded = fabExpanded,
            onExpandedChange = onFabExpandedChange,
        )
    }
}

/**
 * La pastille ⓘ : le geste qui rend la carte conforme.
 *
 * Elle est **petite et en retrait**, et c'est le but : la licence demande que le
 * crédit soit atteignable, pas qu'il concurrence la carte. Le verre la range
 * avec ce qu'elle est — une mention posée sur la carte, qui la laisse voir
 * dessous — au lieu d'un aplat plein qui la ferait passer pour une commande.
 *
 * Le contour tient son bord au-dessus d'une tuile claire, où le verre seul
 * disparaîtrait ; c'est la même raison qu'au bouton de cadrage.
 *
 * Elle est dessinée au cran de la pastille, sous les 40 dp du bouton de cadrage
 * qui lui fait face : la mention doit être atteignable, pas se mettre au rang
 * d'une commande de la carte. La cible, elle, ne descend pas avec le dessin —
 * Material l'agrandit au plancher autour de toute surface cliquable.
 */
@Composable
private fun LegalNoticeButton(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = CircleShape
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(AuleChrome.pill)
            .border(AuleStroke.hairline, colors.outlineVariant, shape),
        shape = shape,
        color = colors.surface.copy(alpha = AuleAlpha.GLASS),
        contentColor = colors.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.legal_open),
                modifier = Modifier.size(AuleChrome.pillGlyph),
            )
        }
    }
}

private data class BannerIssue(
    val message: String,
    val action: String?,
    val onAction: (() -> Unit)?,
)
