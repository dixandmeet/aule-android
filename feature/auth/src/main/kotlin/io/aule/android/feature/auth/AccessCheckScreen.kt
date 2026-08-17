package io.aule.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * Le temps de résoudre les habilitations, avant la carte.
 *
 * Flutter pose le même écran (`SAE/lib/app_shell.dart`) : monter MapLibre
 * pour un compte voyageur, puis le démonter une seconde plus tard, n'est
 * pas une vérification, c'est une fuite.
 */
@Composable
fun AccessCheckScreen(modifier: Modifier = Modifier) {
    AuleTheme {
        val colors = MaterialTheme.colorScheme
        AuleAmbientBackground(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(AuleSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(
                    AuleSpacing.lg,
                    Alignment.CenterVertically,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    color = colors.primary,
                    strokeWidth = AuleStroke.glyph,
                )
                Text(
                    text = stringResource(R.string.auth_checking_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.auth_checking_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
