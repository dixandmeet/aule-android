package io.aule.android.feature.auth

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleStroke
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
    val tokens = AuleTheme.tokens
    val shape = RoundedCornerShape(AuleRadius.md)
    val bitmap = remember(bytes) {
        bytes?.takeIf { it.isNotEmpty() }?.let { payload ->
            BitmapFactory.decodeByteArray(payload, 0, payload.size)
        }
    }
    Box(
        modifier = modifier
            .size(AuleControl.avatar)
            .clip(shape)
            .background(tokens.accent.color.copy(alpha = AuleAlpha.TINT))
            .border(
                AuleStroke.hairline,
                tokens.accent.color.copy(alpha = AuleAlpha.OUTLINE),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            BasicText(
                text = initialsOf(name),
                style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                    .copy(color = tokens.accentOnSurface.color),
            )
        }
    }
}
