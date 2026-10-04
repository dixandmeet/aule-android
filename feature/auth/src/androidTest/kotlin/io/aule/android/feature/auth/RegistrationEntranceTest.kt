package io.aule.android.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.model.ProRegistrationDraft
import io.aule.android.core.model.ProfessionalProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationEntranceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun accueilCompactAvecGrandsCaracteresConserveSonActionEtSaSortie() {
        var started = 0
        var closed = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AuleTheme(night = false) {
                    Box(Modifier.width(320.dp).fillMaxSize()) {
                        RegistrationChrome(RegistrationUiState(isHydrated = true), { closed++ }, {}, { started++ }) {
                            RegistrationWelcome { closed++ }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Commencer").assertIsDisplayed().performClick()
        assertEquals(1, started)
        compose.onNodeWithText("Déjà un compte ? Se connecter").assertIsDisplayed().performClick()
        assertEquals(1, closed)
    }

    @Test
    fun choixDeMetierActiveContinuerEtGardeLaSemantiqueDeSelectionMultiple() {
        val state = mutableStateOf(RegistrationUiState(isHydrated = true, step = RegistrationStep.PROFILE))
        var continued = 0
        compose.setContent {
            AuleTheme(night = false) {
                RegistrationChrome(state.value, {}, {}, { continued++ }) {
                    ProfilesStep(state.value) { profile ->
                        state.value = state.value.copy(draft = state.value.draft.toggleProfile(profile))
                    }
                }
            }
        }
        compose.onNodeWithTag("registration-continue").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Conducteur").performScrollTo().performClick()
        compose.onNode(isToggleable() and hasText("Conducteur")).assertIsOn()
        compose.onNodeWithTag("registration-continue").assertIsEnabled().performClick()
        assertEquals(1, continued)
        compose.onNodeWithTag("registration-progress")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.1f, 0f..1f)))
    }

    @Test
    fun progressionEtDernierMetierRestentLisiblesAvecGrandsCaracteres() {
        val state = mutableStateOf(RegistrationUiState(isHydrated = true, step = RegistrationStep.PROFILE))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AuleTheme(night = false) {
                    Box(Modifier.width(320.dp).fillMaxSize()) {
                        RegistrationChrome(state.value, {}, {}, {}) {
                            ProfilesStep(state.value) { profile ->
                                state.value = state.value.copy(draft = state.value.draft.toggleProfile(profile))
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("ÉTAPE 1 SUR 5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Agent de maîtrise").performScrollTo().assertIsDisplayed().performClick()
        compose.onNode(isToggleable() and hasText("Agent de maîtrise")).assertIsOn()
        compose.onNodeWithTag("registration-continue").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("ÉTAPE 1 SUR 4").assertExists()
    }

    @Test
    fun parcoursSansConduiteCompteQuatreEtapes() {
        val state = RegistrationUiState(isHydrated = true, step = RegistrationStep.IDENTITY,
            draft = ProRegistrationDraft(profiles = setOf(ProfessionalProfile.CONTROLEUR)))
        var back = 0
        compose.setContent {
            AuleTheme(night = false) {
                RegistrationChrome(state, {}, { back++ }, {}) { IdentityStep(state, {}, {}) }
            }
        }
        compose.onNodeWithText("ÉTAPE 3 SUR 4").assertIsDisplayed()
        compose.onNodeWithText("Retour").assertIsDisplayed().performClick()
        assertEquals(1, back)
    }

    @Test
    fun creationEnCoursBloqueLeDoubleEnvoiEtLeRetour() {
        var continued = 0
        var back = 0
        val state = RegistrationUiState(isHydrated = true, step = RegistrationStep.ACCOUNT, isSubmitting = true)
        compose.setContent {
            AuleTheme(night = false) { RegistrationChrome(state, {}, { back++ }, { continued++ }) {} }
        }
        compose.onNodeWithTag("registration-continue").assertIsDisplayed().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Retour").assertIsNotEnabled().performClick()
        assertEquals(0, continued)
        assertEquals(0, back)
    }

    @Test
    fun matriculeEtActionsRestentAccessiblesAvecLeClavier() {
        val state = mutableStateOf(RegistrationUiState(isHydrated = true, step = RegistrationStep.IDENTITY))
        compose.setContent {
            AuleTheme(night = false) {
                RegistrationChrome(state.value, {}, {}, {}) {
                    IdentityStep(state.value,
                        { name -> state.value = state.value.copy(draft = state.value.draft.copy(fullName = name)) },
                        { id -> state.value = state.value.copy(draft = state.value.draft.copy(employeeId = id)) })
                }
            }
        }
        compose.onNodeWithTag("registration-name").performScrollTo().performTextInput("Vérification interface")
        compose.onNodeWithTag("registration-employee").performScrollTo().performTextInput("QA-interface")
        compose.onNodeWithTag("registration-employee").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("registration-continue").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Retour").assertIsDisplayed()
    }
}
