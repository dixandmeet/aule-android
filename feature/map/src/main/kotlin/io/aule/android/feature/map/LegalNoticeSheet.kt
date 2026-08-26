package io.aule.android.feature.map

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.map.LegalNotice
import io.aule.android.core.map.LegalNoticeKind
import io.aule.android.core.map.MAP_LEGAL_NOTICES

/**
 * Les mentions légales de la carte.
 *
 * ## Pourquoi cet écran existe
 *
 * `MapController.attach` éteint le logo et le bouton d'attribution natifs de
 * MapLibre pour que la carte reste le produit. Cette extinction **crée une
 * obligation** : la licence ODbL d'OpenStreetMap demande que le crédit soit
 * visible ou atteignable en un geste. La pastille ⓘ du HUD est ce geste, et ce
 * volet est ce qu'elle ouvre.
 *
 * ## Ce qui se traduit et ce qui ne se traduit pas
 *
 * Les intitulés de genre et la phrase de pied sont à nous : ils vivent dans les
 * ressources (ADR-011). Les crédits eux-mêmes viennent de [MAP_LEGAL_NOTICES] et
 * n'y passent pas — leur formulation est imposée par les licences, et les
 * traduire serait les réécrire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegalNoticeSheet(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AuleTheme {
        ModalBottomSheet(
            onDismissRequest = onClose,
            modifier = modifier,
            sheetState = sheetState,
        ) {
            SheetBody {
                SheetTitle(stringResource(R.string.legal_title))

                SheetCard(modifier = Modifier.fillMaxWidth()) {
                    MAP_LEGAL_NOTICES.forEachIndexed { index, notice ->
                        if (index > 0) SheetRowDivider()
                        LegalNoticeRow(notice)
                    }
                }

                Text(
                    text = stringResource(R.string.legal_footer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Une mention : ce qu'elle couvre, le crédit, la licence.
 *
 * La rangée entière est **un seul élément** pour TalkBack : le crédit se lit
 * d'un tenant, pas en trois fragments dont le dernier serait un sigle de licence
 * sorti de nulle part.
 */
@Composable
private fun LegalNoticeRow(notice: LegalNotice) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val kindLabel = stringResource(notice.kind.labelRes())
    val openHint = stringResource(R.string.legal_open_hint)
    val spoken = buildString {
        append(kindLabel)
        append(". ")
        append(notice.credit)
        notice.licence?.let {
            append(". ")
            append(it)
        }
        if (notice.url != null) {
            append(". ")
            append(openHint)
        }
    }

    val url = notice.url
    val opens = url != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (url != null) {
                    Modifier.clickable {
                        // Un téléphone sans navigateur n'est pas une panne
                        // d'Aule : la mention reste lisible à l'écran, qui est
                        // exactement ce que la licence demande.
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }.onFailure { failure ->
                            if (failure !is ActivityNotFoundException) throw failure
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                if (opens) role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kindLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = notice.credit,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            val licence = notice.licence
            if (licence != null) {
                Text(
                    text = licence,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (opens) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(LEGAL_LINK_GLYPH),
            )
        }
    }
}

/** Ce qu'une mention couvre, dit en français — la part qui, elle, est à nous. */
@Composable
private fun LegalNoticeKind.labelRes(): Int = when (this) {
    LegalNoticeKind.BASEMAP -> R.string.legal_kind_basemap
    LegalNoticeKind.TILES -> R.string.legal_kind_tiles
    LegalNoticeKind.SCHEMA -> R.string.legal_kind_schema
    LegalNoticeKind.TRANSIT -> R.string.legal_kind_transit
}

/** La flèche de sortie, contre un texte de 14 — sous la grille d'icône de 24. */
private val LEGAL_LINK_GLYPH = 18.dp
