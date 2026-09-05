package io.aule.android.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.ServiceNote
import java.time.Instant

/**
 * Le diamètre de l'anneau d'attente, et l'épaisseur de son trait.
 *
 * Plus petits que ceux d'un chargement d'écran : celui-ci vit **dans une ligne de
 * texte**, à hauteur de « Lecture des notes… ». Au calibre courant il pèserait plus
 * que la phrase qu'il accompagne.
 */
private val kNoteSpinner = 16.dp
private val kNoteSpinnerStroke = 2.dp

/**
 * La puce d'une pastille d'état : un cran sous l'espacement le plus fin.
 *
 * `AuleSpacing.xs` (4 dp) haut et bas ferait une capsule aussi haute qu'un bouton,
 * là où elle doit se lire comme une étiquette posée sur la ligne de la référence.
 */
private val kNoteChipInset = 3.dp

/**
 * Le point d'une puce, et sa retombée sur la ligne de base.
 *
 * Dessiné plutôt qu'écrit : le « • » est retiré par le découpage du corps, et le
 * reposer en texte le laisserait se faire couper par un retour à la ligne du premier
 * mot. Les 7 dp alignent le point sur l'œil de la première ligne — un `Row` centré
 * verticalement le placerait au milieu d'un item de trois lignes.
 */
private val kNoteBulletSize = 4.dp
private val kNoteBulletBaseline = 7.dp

/**
 * Les notes de service, dans la prise de service.
 *
 * ## Pourquoi elles sont ici, et pas derrière une entrée de menu
 *
 * Une note de service se lit **avant de partir**, et la prise de service est le
 * seul moment où tout le monde passe. Rangée dans un menu, elle serait lue par
 * ceux qui la cherchent — c'est-à-dire par ceux qui savent déjà qu'elle existe.
 *
 * ## Ce que la carte ne fait pas
 *
 * Elle ne déplie rien. Une note fait deux pages ; trois notes dépliées, c'est six
 * pages entre le conducteur et le bouton « Démarrer », posé dessous. Le rang dit la
 * référence, le titre et l'état — de quoi savoir s'il faut ouvrir — et l'ouverture
 * est un volet, donc un geste qui se referme.
 *
 * Aucune note : **rien du tout**. Pas de carte vide qui dirait qu'il n'y a rien à
 * dire — c'est le cas de la plupart des jours.
 */
@Composable
fun ServiceNotesCard(
    notes: List<ServiceNote>,
    isLoading: Boolean,
    isUnavailable: Boolean,
    modifier: Modifier = Modifier,
    now: () -> Instant = Instant::now,
) {
    var opened by remember { mutableStateOf<ServiceNote?>(null) }

    when {
        isUnavailable -> {
            Column(modifier = modifier.fillMaxWidth()) {
                AuleBanner(
                    message = stringResource(R.string.service_notes_unavailable),
                    tone = AuleTone.ALERT,
                )
                Spacer(modifier = Modifier.height(AuleSpacing.sm))
                Text(
                    // Ce que le conducteur doit faire de cette phrase : ne pas
                    // conclure de son écran qu'il n'y a rien à savoir.
                    text = stringResource(R.string.service_notes_unavailable_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        isLoading && notes.isEmpty() -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(kNoteSpinner),
                    strokeWidth = kNoteSpinnerStroke,
                )
                Text(
                    text = stringResource(R.string.service_notes_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        notes.isNotEmpty() -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.service_notes_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column {
                        notes.forEachIndexed { index, note ->
                            if (index > 0) HorizontalDivider()
                            ServiceNoteRow(note = note, now = now) { opened = note }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.service_notes_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    opened?.let { note ->
        ServiceNoteSheet(note = note, now = now, onClose = { opened = null })
    }
}

@Composable
private fun ServiceNoteRow(
    note: ServiceNote,
    now: () -> Instant,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            // En cabine, on vise le rang, pas son libellé.
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .padding(AuleSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServiceNoteStatusChip(status = note.statusAt(now()))
            Text(
                text = stringResource(R.string.service_note_reference, note.reference),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = note.title,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        note.summary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ServiceNoteStatusChip(status: ServiceNote.Status) {
    val colors = MaterialTheme.colorScheme
    val (label, container, ink) = when (status) {
        ServiceNote.Status.UPCOMING ->
            Triple(R.string.service_note_status_upcoming, colors.secondaryContainer, colors.onSecondaryContainer)
        ServiceNote.Status.ACTIVE ->
            Triple(R.string.service_note_status_active, colors.primary, colors.onPrimary)
        ServiceNote.Status.SETTLED ->
            Triple(R.string.service_note_status_settled, colors.surfaceContainerHighest, colors.onSurfaceVariant)
    }
    Surface(shape = CircleShape, color = container) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmallEmphasized,
            color = ink,
            modifier = Modifier.padding(horizontal = AuleSpacing.sm, vertical = kNoteChipInset),
        )
    }
}

/**
 * Une note en entier.
 *
 * ## Le corps n'est jamais interprété
 *
 * [ServiceNote.blocks] ne rend que trois formes — titre, puces, paragraphe — et
 * chacune traverse Compose comme du texte. Une note est saisie par un humain dans
 * un outil d'administration ; y passer un moteur Markdown ferait disparaître les
 * `*` d'un futur « 2*30 m » et changerait un texte réglementaire sans que personne
 * ne le voie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceNoteSheet(
    note: ServiceNote,
    now: () -> Instant,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AuleSpacing.lg)
                .padding(bottom = AuleSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServiceNoteStatusChip(status = note.statusAt(now()))
                Text(
                    text = stringResource(R.string.service_note_reference, note.reference),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            note.summary?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }

            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
                Text(
                    text = stringResource(
                        R.string.service_note_effective,
                        ServiceNote.formatDay(note.effectiveOn),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                // ⚠️ « Affichage » et non « valable jusqu'au » : ce que la note a
                // changé ne se dé-change pas. Passée cette date elle quitte l'écran,
                // pas les consignes.
                Text(
                    text = note.displayUntil?.let {
                        stringResource(R.string.service_note_display_until, ServiceNote.formatDay(it))
                    } ?: stringResource(R.string.service_note_display_none),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                note.isScheduled?.let { scheduled ->
                    Text(
                        text = stringResource(
                            if (scheduled) R.string.service_note_scheduled
                            else R.string.service_note_not_scheduled,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            note.blocks.forEach { block ->
                when (block) {
                    is ServiceNote.Block.Heading -> Text(
                        text = block.text,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        modifier = Modifier.padding(top = AuleSpacing.sm),
                    )

                    is ServiceNote.Block.Paragraph -> Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )

                    is ServiceNote.Block.Bullets -> Column(
                        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                    ) {
                        block.items.forEach { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
                                // Une puce dessinée : le caractère est déjà retiré par
                                // le découpage, et le poser en texte le laisserait se
                                // faire couper par un retour à la ligne.
                                Surface(
                                    shape = CircleShape,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(top = kNoteBulletBaseline)
                                        .size(kNoteBulletSize),
                                ) {}
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            listOfNotNull(note.issuer, note.signatory).takeIf { it.isNotEmpty() }?.let { lines ->
                Text(
                    text = lines.joinToString(" — "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = AuleSpacing.sm),
                )
            }

            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                Icon(imageVector = AuleGlyph.CLOSE.asImageVector(), contentDescription = null)
                Spacer(modifier = Modifier.size(AuleSpacing.xs))
                Text(stringResource(R.string.service_note_close))
            }
        }
    }
}
