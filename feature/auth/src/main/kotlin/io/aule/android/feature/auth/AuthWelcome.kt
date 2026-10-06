package io.aule.android.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.token.AuleSpacing

@Composable
internal fun AuthWelcome(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(
                    if (AuleTheme.night) io.aule.android.core.designsystem.R.drawable.aule_logo_blanc
                    else io.aule.android.core.designsystem.R.drawable.aule_logo,
                ),
                contentDescription = stringResource(R.string.auth_logo),
                modifier = Modifier.size(MARK_SIZE).authEnter(150),
            )
            Column {
                Text(
                    text = stringResource(R.string.auth_brand),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = colors.onSurface,
                    modifier = Modifier.authEnter(250).semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.auth_workspace).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.6.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.authEnter(350),
                )
            }
        }
        Spacer(Modifier.height(AuleSpacing.md))
        Text(
            text = stringResource(R.string.auth_welcome_tagline),
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = colors.onSurface,
            modifier = Modifier.authEnter(400),
        )
        // Cette réserve laisse la scène visible ; elle disparaît avec l'en-tête au clavier.
        Spacer(Modifier.height(AuleSpacing.xxl * 2 + AuleSpacing.lg))
    }
}

private val MARK_SIZE = 56.dp
