package io.aule.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.aule.android.core.common.config.AppConfig
import io.aule.android.core.common.config.DataSource
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing

/**
 * L'écran du lot 1 : il prouve que le socle tourne, et il dit quel binaire on
 * regarde.
 *
 * Ce dernier point n'est pas de la décoration. Trois APK Aule cohabitent sur
 * l'appareil de test ; lire l'environnement à l'écran évite de conclure sur le
 * mauvais.
 *
 * La carte lui a pris l'écran au lot 4 : il ne reste ici que comme premier
 * écran de secours. Raison de plus pour qu'il n'ait aucune couleur à lui —
 * une palette recopiée dans un écran qu'on n'ouvre plus est une palette qui
 * dérive sans que personne le voie.
 */
@Composable
fun BootScreen(config: AppConfig, modifier: Modifier = Modifier) {
    AuleTheme {
        val tokens = AuleTheme.tokens
        AuleAmbientBackground(modifier = modifier) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(horizontal = AuleSpacing.xl),
            ) {
                BasicText(
                    text = stringResource(R.string.boot_title),
                    style = auleTextStyle(AuleRole.HERO, FontWeight.SemiBold).copy(
                        color = tokens.accentOnSurface.color,
                        textAlign = TextAlign.Center,
                    ),
                )
                BasicText(
                    text = stringResource(R.string.boot_subtitle),
                    style = auleTextStyle(AuleRole.BODY).copy(
                        color = tokens.onSurface.color,
                        textAlign = TextAlign.Center,
                    ),
                )
                BasicText(
                    text = stringResource(R.string.boot_build_label, config.buildLabel),
                    style = auleTextStyle(AuleRole.KICKER).copy(
                        color = tokens.onSurfaceMuted.color,
                        textAlign = TextAlign.Center,
                    ),
                )
                if (!config.supabaseConfigured) {
                    BasicText(
                        text = stringResource(R.string.boot_supabase_missing),
                        style = auleTextStyle(AuleRole.KICKER).copy(
                            color = tokens.delay.color,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BootScreenPreview() {
    BootScreen(
        config = AppConfig(
            dataSource = DataSource.PRODUCTION,
            apiBase = "https://www.aule.fr",
            supabaseUrl = "",
            supabasePublishableKey = "",
            environmentLabel = "Développement",
            versionName = "0.1.0-dev",
            versionCode = 1,
        ),
    )
}
