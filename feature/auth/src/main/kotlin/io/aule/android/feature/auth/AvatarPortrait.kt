package io.aule.android.feature.auth

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.model.initialsOf

/**
 * Le portrait : la photo si on l'a, les initiales sinon.
 *
 * Pas de Chargeur d'images tiers — les octets viennent déjà du ViewModel,
 * décodés ici. Une photo illisible retombe sur les initiales, ce n'est
 * pas un bandeau d'erreur.
 */
@Composable
internal fun AvatarPortrait(
    name: String,
    bytes: ByteArray?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val bitmap = remember(bytes) {
        bytes?.takeIf { it.isNotEmpty() }?.let { payload ->
            BitmapFactory.decodeByteArray(payload, 0, payload.size)
        }
    }
    Surface(
        modifier = modifier.size(AuleControl.avatar),
        shape = MaterialTheme.shapes.small,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(AuleControl.avatar),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initialsOf(name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
