package io.aule.android.feature.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleFormField
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.HubColleague

/** L'avatar d'une fiche, à la mesure de celui d'une discussion. */
private val AVATAR_FICHE = 40.dp

/**
 * Le répertoire de son réseau.
 *
 * ## Ce qu'il montre, et ce qu'il ne montre pas
 *
 * **Les agents du réseau, et eux seuls.** Le périmètre n'est pas décidé ici : la
 * base s'arrête au réseau courant, et un client ne peut pas en sortir. C'est ce
 * qui permet d'afficher cette liste sans crainte — elle ne porte ni e-mail, ni
 * téléphone, ni aucune coordonnée (doctrine de la relève).
 *
 * ## Pourquoi un collègue non joignable reste affiché
 *
 * ⚠️ **Le faire disparaître laisserait croire qu'il n'existe pas.** Un agent qui
 * cherche un collègue par son nom et ne le trouve pas conclut qu'il s'est trompé
 * de personne, ou que l'application est cassée — puis il appelle la régulation.
 * La rangée reste donc là, avec sa raison écrite : « pas encore de compte », ou
 * « n'accepte pas les messages directs ». Deux phrases différentes pour deux
 * situations qui n'appellent pas la même patience.
 *
 * ## Sa propre porte, en tête
 *
 * L'interrupteur est ici et non dans un écran de réglages : c'est le seul
 * endroit où l'agent constate qu'il peut écrire à tout le monde sans que
 * personne ne puisse lui répondre. Un consentement qu'il faut aller chercher
 * dans un menu est un consentement que personne ne donne.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubDirectoryScreen(
    state: HubDirectoryState,
    isNotDeployed: Boolean,
    onQuery: (String) -> Unit,
    onOpen: (HubColleague) -> Unit,
    onLoadMore: () -> Unit,
    onReachable: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.hub_directory),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = AuleGlyph.BACK.asImageVector(),
                        contentDescription = stringResource(R.string.hub_back),
                    )
                }
            },
            // `windowInsets` à zéro : l'écran a déjà écarté les barres système
            // une fois, dans [HubScreen].
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.surface,
                titleContentColor = colors.onSurface,
                navigationIconContentColor = colors.onSurface,
            ),
        )

        AuleFormField(
            label = stringResource(R.string.hub_search_colleagues),
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.padding(
                start = AuleSpacing.lg, end = AuleSpacing.lg, bottom = AuleSpacing.sm,
            ),
        )

        // ⚠️ Pas de porte à ouvrir sur un serveur qui n'a pas de messagerie :
        // l'interrupteur poserait un réglage qu'aucune route ne sait écrire, et
        // reviendrait à sa place sous une erreur que rien n'annonçait.
        if (!isNotDeployed) {
            HubReachableRow(checked = state.meAcceptsDirect, onChange = onReachable)
            HorizontalDivider()
        }

        val refus = state.failure
        if (state.colleagues.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    state.isLoading -> HubLoading()
                    // ⚠️ **« Répertoire vide » est une affirmation**, et une
                    // lecture ne peut pas la faire : le 404 d'une route absente
                    // est aplati en liste vide, par convention du dépôt. Seul
                    // l'amorçage, qui écrit, distingue « ce réseau n'a personne »
                    // de « ce serveur n'a pas de messagerie ». Sans ce cas,
                    // l'écran affirmait qu'un réseau de trois cents agents était
                    // désert — c'est ce que la première capture montrait.
                    isNotDeployed -> AuleEmptyState(
                        title = stringResource(R.string.hub_unavailable_title),
                        detail = stringResource(R.string.hub_error_not_deployed),
                        icon = AuleGlyph.FLAG.asImageVector(),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                    // ⚠️ « Aucun collègue » est une affirmation. Quand le
                    // chargement a échoué, l'écran ne sait rien : la liste est
                    // vide parce qu'elle n'est jamais arrivée.
                    refus != null -> AuleEmptyState(
                        title = stringResource(R.string.hub_directory_unreachable),
                        detail = refus.label(),
                        icon = AuleGlyph.FLAG.asImageVector(),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                    // Une lettre seule ne cherche rien, et la liste vide qu'elle
                    // rend n'est pas un résultat : le dire évite de conclure que
                    // le collègue n'existe pas.
                    state.isTypingTooShort -> AuleEmptyState(
                        title = stringResource(R.string.hub_no_colleague),
                        detail = stringResource(R.string.hub_directory_typing),
                        icon = AuleGlyph.SEARCH.asImageVector(),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                    state.query.isNotBlank() -> AuleEmptyState(
                        title = stringResource(R.string.hub_no_colleague),
                        detail = null,
                        icon = AuleGlyph.SEARCH.asImageVector(),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                    else -> AuleEmptyState(
                        title = stringResource(R.string.hub_directory_empty_title),
                        detail = stringResource(R.string.hub_directory_empty_detail),
                        icon = AuleGlyph.PERSON.asImageVector(),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = AuleSpacing.lg, end = AuleSpacing.lg, bottom = AuleSpacing.xxl,
            ),
        ) {
            // ⚠️ Un refus survenu **après** l'affichage — une porte refermée
            // entre la liste et le doigt — se dit au-dessus de la liste, et non
            // à sa place : effacer le répertoire pour une rangée refusée ferait
            // perdre les autres.
            if (refus != null) {
                item(key = "refus") {
                    Text(
                        text = refus.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.error,
                        modifier = Modifier.padding(vertical = AuleSpacing.sm),
                    )
                }
            }
            items(state.colleagues, key = { it.key }) { collegue ->
                HubColleagueRow(collegue = collegue, onOpen = { onOpen(collegue) })
                if (collegue.key != state.colleagues.last().key) HorizontalDivider()
            }
            if (state.hasMore) {
                item(key = "suite") {
                    TextButton(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = AuleTouch.minimum),
                    ) {
                        Text(text = stringResource(R.string.hub_directory_more))
                    }
                }
            }
        }
    }
}

/**
 * Sa propre porte.
 *
 * ⚠️ **La phrase change avec l'état, et elle dit la conséquence** — pas le
 * réglage. « Personne ne peut vous écrire tant que vous ne l'acceptez pas »
 * s'agit d'un fait dont l'agent subit l'effet ; « joignabilité : désactivée »
 * n'aurait rien dit à celui qui attend un message.
 */
@Composable
private fun HubReachableRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AuleTouch.minimum)
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.sm),
    ) {
        Icon(
            imageVector = AuleGlyph.MAIL.asImageVector(filled = checked),
            contentDescription = null,
            modifier = Modifier.size(AuleControl.icon),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.hub_reachable_title),
                style = MaterialTheme.typography.bodyLargeEmphasized,
            )
            Text(
                text = stringResource(
                    if (checked) R.string.hub_reachable_detail else R.string.hub_unreachable_detail,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Une fiche du répertoire.
 *
 * Non contactable, la rangée reste lisible mais **ne se clique pas** : un doigt
 * qui obtient un refus au lieu d'une conversation apprend à ne plus essayer, et
 * ne saura pas que la rangée d'à côté, elle, aurait marché.
 */
@Composable
private fun HubColleagueRow(collegue: HubColleague, onOpen: () -> Unit) {
    val empeche = collegue.unavailable()
    val ouvrable = empeche == null
    val ecrireA = stringResource(R.string.hub_write_to, collegue.label)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AuleTouch.minimum)
            .then(if (ouvrable) Modifier.clickable(onClick = onOpen) else Modifier)
            .then(
                // Le libellé d'action ne se prononce que s'il y a une action :
                // l'annoncer sur une rangée inerte enverrait un lecteur d'écran
                // sur un geste qui ne fait rien.
                if (ouvrable) {
                    Modifier.semantics { contentDescription = "$ecrireA, ${collegue.detail()}" }
                } else {
                    Modifier
                },
            )
            .padding(vertical = AuleSpacing.sm),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AVATAR_FICHE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Icon(
                imageVector = AuleGlyph.PERSON.asImageVector(),
                contentDescription = null,
                tint = if (ouvrable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = collegue.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (ouvrable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = collegue.detail()
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // La raison, écrite : c'est elle qui évite l'appel à la régulation.
            if (empeche != null) {
                Text(
                    text = empeche,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (ouvrable) {
            Icon(
                imageVector = AuleGlyph.CHEVRON.asImageVector(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AuleSpacing.lg),
            )
        }
    }
}
