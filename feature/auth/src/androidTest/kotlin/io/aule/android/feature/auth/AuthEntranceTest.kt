package io.aule.android.feature.auth

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.aule.android.core.designsystem.LocalAppearanceMode
import io.aule.android.core.model.AccountModes
import io.aule.android.core.model.AgentAccess
import io.aule.android.core.model.AgentRole
import io.aule.android.core.model.AppearanceMode
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.NetworkFailureReason
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthEntranceTest {
    @get:Rule val compose = createComposeRule()

    private fun mount(
        state: AuthUiState = AuthUiState(),
        signIn: (String, String) -> Unit = { _, _ -> },
        forgotten: (String) -> Unit = {},
        create: () -> Unit = {},
        entered: () -> Unit = {},
        appearance: AppearanceMode = AppearanceMode.LIGHT,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalAppearanceMode provides appearance) {
                AuthScreenContent(state, signIn, {}, {}, create, forgotten, entered)
            }
        }
    }

    @Test
    fun laMarqueEtLeBoutonSontVisiblesAvantTouteSaisie() {
        mount()
        compose.onNodeWithText("Aule Pro").assertIsDisplayed()
        compose.onNodeWithTag("auth-submit").assertIsDisplayed()
    }

    @Test
    fun formulaireVideResteSurLaConnexionEtAnnonceLesErreurs() {
        var calls = 0
        mount(signIn = { _, _ -> calls++ })
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick()
        compose.onNodeWithTag("auth-email").assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "E-mail requis"))
        compose.onNodeWithTag("auth-password")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Mot de passe requis"))
        assertEquals(0, calls)
    }

    @Test
    fun clavierSoumetUneFoisLesIdentifiantsEtLeMotDePasseResteMasque() {
        val calls = mutableListOf<Pair<String, String>>()
        mount(signIn = { email, password -> calls.add(email to password) })
        compose.onNodeWithTag("auth-email").performScrollTo().performTextInput(" agent@operateur.fr ")
        compose.onNodeWithTag("auth-email").performImeAction()
        compose.onNodeWithTag("auth-password").assertIsFocused().performTextInput("test-password")
        compose.onNodeWithTag("auth-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithTag("auth-password").performImeAction()
        assertEquals(listOf("agent@operateur.fr" to "test-password"), calls)
    }

    @Test
    fun visibiliteDuMotDePasseConserveLaSaisie() {
        mount()
        compose.onNodeWithTag("auth-password").performScrollTo().performTextInput("test-password")
        compose.onNodeWithTag("auth-password-visibility").performClick()
        compose.onNodeWithTag("auth-password").assertTextContains("test-password")
        assertEquals("test-password", renderedPassword())
        compose.onNodeWithContentDescription("Masquer le mot de passe").assertExists()
        compose.onNodeWithTag("auth-password-visibility").performClick()
        compose.onNodeWithTag("auth-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertEquals("\u2022".repeat("test-password".length), renderedPassword())
    }

    private fun renderedPassword(): String {
        var text = ""
        compose.onNodeWithTag("auth-password").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            assertTrue(action(results))
            text = results.single().layoutInput.text.text
        }
        return text
    }

    @Test
    fun recuperationTransmetLEmailEtInscriptionResteAccessible() {
        var email: String? = null
        var creations = 0
        mount(forgotten = { email = it }, create = { creations++ })
        compose.onNodeWithTag("auth-email").performScrollTo().performTextInput(" agent@operateur.fr ")
        compose.onNodeWithTag("auth-forgot-password").performScrollTo().performClick()
        assertEquals("agent@operateur.fr", email)
        compose.onNodeWithTag("auth-create-account").performScrollTo().performClick()
        assertEquals(1, creations)
    }

    @Test
    fun verificationDesHabilitationsBloqueLaSoumissionSansOuvrirLEspace() {
        var entered = false
        mount(AuthUiState(isSignedIn = true, isCheckingAccess = true), entered = { entered = true })
        compose.onNodeWithTag("auth-submit").assertIsNotEnabled()
        compose.onNodeWithTag("auth-email").assertIsNotEnabled()
        compose.onNodeWithText("Vérification…").assertExists()
        assertFalse(entered)
    }

    @Test
    fun leSuccesAttendUneHabilitationEtNeRejouePasSurRecomposition() {
        var entries = 0
        val state = mutableStateOf(AuthUiState(isSignedIn = true, isCheckingAccess = true))
        compose.setContent {
            AuthScreenContent(state.value, { _, _ -> }, {}, {}, {}, {}, { entries++ })
        }
        compose.runOnIdle { assertEquals(0, entries) }
        compose.runOnIdle {
            state.value = state.value.copy(isCheckingAccess = false, access = AgentAccess(AccountModes.CONDUCTEUR, AgentRole.CONDUCTEUR))
        }
        // Attendre les animations avec l’horloge Compose, plutôt qu’une seconde murale.
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, entries) }
        compose.runOnIdle { state.value = state.value.copy(email = "agent@aule.fr") }
        compose.waitForIdle()
        assertEquals(1, entries)
    }

    @Test
    fun panneServeurEtRefusBiometriqueRestentLisiblesEnModeSombre() {
        mount(AuthUiState(failure = AuthFailureKind.NETWORK, failureNetworkReason = NetworkFailureReason.UNAVAILABLE, canRetryBiometric = true), appearance = AppearanceMode.DARK)
        compose.onNodeWithText("Le service est momentanément indisponible. Réessayez dans un instant.").assertExists()
        compose.onNodeWithText("Se connecter avec la biométrie").assertExists()
        compose.onNodeWithTag("auth-submit").assertIsEnabled()
    }
    @Test
    fun demonstrationPeutEtreSuspendueEtReprise() {
        mount()
        compose.onNodeWithTag("auth-showcase-pause").performClick()
        compose.onNodeWithContentDescription("Reprendre la démonstration").assertExists().performClick()
        compose.onNodeWithContentDescription("Mettre la démonstration en pause").assertExists()
    }

    @Test
    fun connexionResteAccessibleSurPetitEcranAvecGrandTexte() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f),
            ) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(320.dp, 640.dp)) {
                    AuthScreenContent(AuthUiState(), { _, _ -> }, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithTag("auth-email").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth-create-account").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

}
