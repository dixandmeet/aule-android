package io.aule.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aule.android.core.common.config.AppConfig
import io.aule.android.core.common.config.DataSource
import io.aule.android.core.common.config.PUBLIC_DEMO_ROAD_ROUTER
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBrandMark
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.token.AuleSpacing

/**
 * L'écran d'amorçage : ce que le produit montre avant d'avoir quoi que ce soit
 * à montrer.
 *
 * Deux choses s'y jouent, et une seule est technique. La technique d'abord :
 * trois APK Aule cohabitent sur l'appareil de test, et lire l'environnement à
 * l'écran évite de conclure sur le mauvais binaire.
 *
 * L'autre est qu'un écran d'amorçage n'est pas un écran d'attente. Il ne dure
 * qu'une seconde, mais c'est la première seconde, et une seconde sans identité
 * dit « patientez » là où on veut qu'elle dise qui parle. La marque arrive la
 * première, le nom suit, le reste se dépose derrière — c'est la cascade
 * d'entrée du kit, elle ne coûte pas une milliseconde de rendu, et sur un
 * appareil réglé sur « moins de mouvement » tout est simplement là.
 *
 * Il n'a toujours aucune couleur à lui : le fond ambiant, la marque et les
 * rôles du thème font l'écran entier. Une palette recopiée dans un écran qu'on
 * traverse sans le regarder est une palette qui dérive sans que personne le
 * voie.
 *
 * ## Où il est branché — et où il ne l'est pas
 *
 * La carte lui a pris l'écran au lot 4. Aujourd'hui, l'instant du lancement est
 * tenu par la branche `!authState.isReady` d'`AuleRoot`, qui pose un simple
 * libellé centré ; cet écran-ci ne s'affiche plus. Le rebrancher est une
 * décision de coquille, pas d'apparence, et elle ne se prend pas ici — le seuil
 * `isReady` peut retomber en deux cents millisecondes, et une cascade
 * interrompue à mi-course est pire que le libellé qu'elle remplace.
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
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .padding(horizontal = AuleSpacing.xl),
            ) {
                AuleBrandMark(
                    contentDescription = stringResource(R.string.boot_logo),
                    modifier = Modifier.auleEnter(index = 0),
                )
                Text(
                    text = stringResource(R.string.boot_title),
                    // Le nom du produit en display appuyé. La couleur reste
                    // l'encre de la page : le teal est déjà tenu par la marque
                    // juste au-dessus, et deux teals empilés n'en font plus
                    // qu'un — celui qu'on ne regarde plus.
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .semantics { heading() }
                        .auleEnter(index = 1),
                )
                Text(
                    text = stringResource(R.string.boot_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.auleEnter(index = 2),
                )
                BuildStamp(
                    label = stringResource(R.string.boot_build_label, config.buildLabel),
                    modifier = Modifier.auleEnter(index = 3),
                )
                if (!config.supabaseConfigured) {
                    AuleBanner(
                        message = stringResource(R.string.boot_supabase_missing),
                        tone = AuleTone.ALERT,
                        modifier = Modifier
                            .padding(top = AuleSpacing.sm)
                            .auleEnter(index = 4),
                    )
                }
            }
        }
    }
}

/**
 * Le binaire, en cartouche.
 *
 * Posée en troisième ligne de texte, l'étiquette d'environnement se lisait
 * comme la suite du sous-titre — donc comme un message au conducteur, ce
 * qu'elle n'est pas. Dans un conteneur, elle redevient ce qu'elle est : une
 * marque d'atelier, qu'on cherche quand on la cherche et qu'on ne lit pas
 * sinon.
 */
@Composable
private fun BuildStamp(label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.padding(top = AuleSpacing.sm),
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                horizontal = AuleSpacing.md,
                vertical = AuleSpacing.sm,
            ),
        )
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
            roadRouterOrigin = PUBLIC_DEMO_ROAD_ROUTER,
            environmentLabel = "Développement",
            versionName = "0.1.0-dev",
            versionCode = 1,
        ),
    )
}

/**
 * La largeur au-delà de laquelle une colonne centrée cesse d'être une colonne.
 *
 * Même valeur que la carte de connexion : les deux écrans se suivent au
 * lancement, et deux largeurs différentes se verraient au passage de l'un à
 * l'autre.
 */
private val CONTENT_MAX_WIDTH = 420.dp
