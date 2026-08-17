package io.aule.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.aule.android.core.common.config.AppConfig
import io.aule.android.core.common.config.DataSource
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleAmbientBackground
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
        val colors = MaterialTheme.colorScheme
        AuleAmbientBackground(modifier = modifier) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(horizontal = AuleSpacing.xl),
            ) {
                Text(
                    text = stringResource(R.string.boot_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.boot_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.boot_build_label, config.buildLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!config.supabaseConfigured) {
                    Text(
                        text = stringResource(R.string.boot_supabase_missing),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.error,
                        textAlign = TextAlign.Center,
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
