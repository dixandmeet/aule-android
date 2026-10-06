package io.aule.android.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.aule.android.core.designsystem.components.AuleNotice
import io.aule.android.core.designsystem.components.AuleNoticeLevel
import io.aule.android.core.designsystem.components.AuleSearchField
import io.aule.android.core.designsystem.states.AuleEmptyState
import io.aule.android.core.designsystem.states.AuleErrorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedInteractionsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun rechercheConserveLeFocusEtSynchroniseSaisieEffacementEtModele() {
        val value = mutableStateOf("")
        val focus = FocusRequester()
        compose.setContent {
            AuleTheme {
                AuleSearchField(
                    value = value.value,
                    onValueChange = { value.value = it },
                    placeholder = "Rechercher un arrêt",
                    clearLabel = "Effacer",
                    compact = true,
                    modifier = Modifier.focusRequester(focus),
                )
            }
        }
        compose.runOnIdle { focus.requestFocus() }
        val field = compose.onNode(hasSetTextAction())
        field.assertIsFocused().performTextInput("Ranzay")
        compose.runOnIdle { assertEquals("Ranzay", value.value) }
        compose.onNodeWithContentDescription("Effacer").performClick()
        assertEquals("", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.runOnIdle { assertEquals("", value.value); value.value = "Commerce" }
        field.assertTextContains("Commerce").assertIsFocused()
        field.performTextReplacement("Bouffay")
        compose.runOnIdle { assertEquals("Bouffay", value.value) }
    }

    @Test
    fun ouvertureDemandeeDonneLeFocusSansAppuiSupplementaire() {
        var focused = false
        compose.setContent {
            AuleTheme {
                AuleSearchField(
                    value = "",
                    onValueChange = {},
                    placeholder = "Rechercher un arrêt",
                    clearLabel = "Effacer",
                    requestFocus = true,
                    onFocus = { focused = true },
                )
            }
        }
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.runOnIdle { assertTrue(focused) }
    }

    @Test
    fun filtreCompactGranditAvecLaPoliceSysteme() {
        val scale = mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            // Monter chaque configuration séparément évite que le changement artificiel
            // de LocalDensity conserve le cache de texte de la configuration précédente.
            // Le changement de configuration Android réel est vérifié sur l'application.
            key(scale.value) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                    AuleTheme {
                        AuleSearchField(
                            value = "Ranzay et Bouffay",
                            onValueChange = {},
                            placeholder = "Rechercher un arrêt",
                            clearLabel = "Effacer",
                            compact = true,
                        )
                    }
                }
            }
        }
        val field = compose.onNode(hasSetTextAction())
        fun textLayout(): TextLayoutResult {
            val layouts = mutableListOf<TextLayoutResult>()
            field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single()
        }
        val normalText = textLayout()
        val normalBounds = field.getUnclippedBoundsInRoot()
        val normalHeight = normalBounds.bottom - normalBounds.top
        compose.runOnIdle { scale.value = 2f }
        // runOnIdle attend avant son bloc ; la nouvelle densité doit aussi être mise
        // en page avant de lire les bornes et le résultat de mise en forme du texte.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        field.assertHeightIsAtLeast(48.dp)
        // Android applique une échelle de police non linéaire : on vérifie le comportement,
        // sans supposer qu'un facteur 2 double mécaniquement chaque interligne.
        val largeText = textLayout()
        val largeBounds = field.getUnclippedBoundsInRoot()
        assertTrue(
            "Le texte doit grandir : normal=${normalText.size}, agrandi=${largeText.size}, densité=${largeText.layoutInput.density.fontScale}",
            largeText.size.height > normalText.size.height,
        )
        assertTrue(
            "Le champ doit grandir : normal=$normalBounds, agrandi=$largeBounds",
            largeBounds.bottom - largeBounds.top > normalHeight,
        )
        assertTrue(
            "Le texte agrandi doit tenir dans le champ",
            field.fetchSemanticsNode().boundsInRoot.height >= largeText.size.height,
        )
    }

    @Test
    fun erreurEstAnnonceeEtLeBoutonPermetDeReessayer() {
        var attempts = 0
        compose.setContent {
            AuleTheme {
                AuleErrorState(
                    title = "Passages indisponibles",
                    detail = "Le fournisseur ne répond pas",
                    retryLabel = "Réessayer",
                    onRetry = { attempts++ },
                    modifier = Modifier.testTag("failure"),
                )
            }
        }
        val error = compose.onNodeWithTag("failure").fetchSemanticsNode().config[SemanticsProperties.Error]
        assertTrue(error.contains("Passages indisponibles"))
        assertTrue(error.contains("Le fournisseur ne répond pas"))
        compose.onNodeWithText("Réessayer").performClick()
        compose.runOnIdle { assertEquals(1, attempts) }
    }

    @Test
    fun bandeauConserveSesActionsEtSaFermetureEnModeSombre() {
        val night = mutableStateOf(false)
        val visible = mutableStateOf(true)
        var attempts = 0
        compose.setContent {
            AuleTheme(night = night.value) {
                if (visible.value) {
                    AuleNotice(
                        level = AuleNoticeLevel.Disruption,
                        title = "Passages indisponibles",
                        detail = "Le fournisseur ne répond pas",
                        action = "Réessayer",
                        onAction = { attempts++ },
                        onDismiss = { visible.value = false },
                    )
                }
            }
        }
        compose.onNodeWithText("Réessayer").performClick()
        compose.runOnIdle { assertEquals(1, attempts); night.value = true }
        compose.onNodeWithText("Le fournisseur ne répond pas").assertExists()
        compose.onNodeWithText("Réessayer").performClick()
        compose.runOnIdle { assertEquals(2, attempts) }
        val dismissLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.aule_common_dismiss)
        compose.onNodeWithContentDescription(dismissLabel).performClick()
        compose.onNodeWithText("Passages indisponibles").assertDoesNotExist()
    }

    @Test
    fun etatVideAfficheUneActionSansPerdreSonExplication() {
        var attempts = 0
        compose.setContent {
            AuleTheme {
                AuleEmptyState(
                    title = "Aucun lieu enregistré",
                    detail = "Ajoutez une adresse pour la retrouver ici",
                    icon = Icons.Outlined.Search,
                    action = "Ajouter un lieu",
                    onAction = { attempts++ },
                )
            }
        }
        compose.onNodeWithText("Ajoutez une adresse pour la retrouver ici").assertExists()
        compose.onNodeWithText("Ajouter un lieu").performClick()
        compose.runOnIdle { assertEquals(1, attempts) }
    }
}
