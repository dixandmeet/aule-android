package io.aule.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBusyIndicator
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing

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
        val tokens = AuleTheme.tokens
        AuleAmbientBackground(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(AuleSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.lg, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AuleBusyIndicator(color = tokens.accent.color)
                BasicText(
                    text = stringResource(R.string.auth_checking_title),
                    style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                        .copy(color = tokens.onSurface.color, textAlign = TextAlign.Center),
                    modifier = Modifier.semantics { heading() },
                )
                BasicText(
                    text = stringResource(R.string.auth_checking_body),
                    style = auleTextStyle(AuleRole.BODY)
                        .copy(color = tokens.onSurfaceMuted.color, textAlign = TextAlign.Center),
                )
            }
        }
    }
}
