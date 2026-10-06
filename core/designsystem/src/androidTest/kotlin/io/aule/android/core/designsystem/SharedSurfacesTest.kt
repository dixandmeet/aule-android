package io.aule.android.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.aule.android.core.designsystem.components.AuleCard
import io.aule.android.core.designsystem.components.AulePrimaryButton
import io.aule.android.core.designsystem.components.AuleSheetGrip
import io.aule.android.core.designsystem.components.AuleSheetGripHeight
import io.aule.android.core.designsystem.foundation.auleBottomSystemPadding
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedSurfacesTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun carteConserveLeRoleEtLeLibelleDeSonAction() {
        var clicks = 0
        compose.setContent {
            AuleTheme {
                AuleCard(
                    modifier = Modifier.testTag("card"),
                    onClick = { clicks++ },
                    onClickLabel = "Voir le détail du trajet",
                    role = Role.Button,
                ) { Text("Commerce vers Ranzay") }
            }
        }
        val card = compose.onNodeWithTag("card").assertHasClickAction()
        val semantics = card.fetchSemanticsNode().config
        assertEquals(Role.Button, semantics[SemanticsProperties.Role])
        assertEquals("Voir le détail du trajet", semantics[androidx.compose.ui.semantics.SemanticsActions.OnClick].label)
        card.performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun actionEnfantResteAtteignableSansDeclencherLaCarte() {
        var cards = 0
        var actions = 0
        compose.setContent {
            AuleTheme {
                AuleCard(onClick = { cards++ }) {
                    Text("Un lieu enregistré")
                    AulePrimaryButton(label = "Modifier le lieu", onClick = { actions++ })
                }
            }
        }
        compose.onNodeWithText("Modifier le lieu").performClick()
        compose.runOnIdle {
            assertEquals(1, actions)
            assertEquals(0, cards)
        }
    }

    @Test
    fun carteInformativeNeDevientPasUneAction() {
        compose.setContent {
            AuleTheme {
                AuleCard(modifier = Modifier.testTag("card")) { Text("Les prochains passages") }
            }
        }
        compose.onNodeWithTag("card").assertHasNoClickAction()
    }

    @Test
    fun hauteurPublieeDeLaPoigneeCorrespondALaBandeMesuree() {
        compose.setContent {
            AuleTheme {
                Box(Modifier.width(300.dp)) { AuleSheetGrip(Modifier.testTag("grip")) }
            }
        }
        compose.onNodeWithTag("grip")
            .assertHeightIsEqualTo(AuleSheetGripHeight)
            .assertWidthIsEqualTo(300.dp)
    }

    @Test
    fun barreSousDeclareeReserveLaBandeCompleteUneSeuleFois() {
        compose.setContent {
            val systemInsets = with(LocalDensity.current) {
                WindowInsets(bottom = 15.dp.roundToPx()).union(WindowInsets(bottom = 48.dp.roundToPx()))
            }
            Box(Modifier.size(200.dp).auleBottomSystemPadding(systemInsets)) {
                Box(Modifier.fillMaxSize().auleBottomSystemPadding(systemInsets)) {
                    Box(Modifier.fillMaxSize().testTag("content"))
                }
            }
        }
        compose.onNodeWithTag("content").assertHeightIsEqualTo(152.dp)
    }

    @Test
    fun reserveBasseNeDeplacePasLeContenuParLesAutresBords() {
        compose.setContent {
            val systemInsets = with(LocalDensity.current) {
                WindowInsets(
                    left = 16.dp.roundToPx(),
                    top = 39.dp.roundToPx(),
                    right = 16.dp.roundToPx(),
                    bottom = 48.dp.roundToPx(),
                )
            }
            Box(Modifier.size(200.dp).auleBottomSystemPadding(systemInsets)) {
                Box(Modifier.fillMaxSize().testTag("content"))
            }
        }
        compose.onNodeWithTag("content")
            .assertHeightIsEqualTo(152.dp)
            .assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun clavierEtBarreSystemeNeCumulePasDeuxBandes() {
        compose.setContent {
            val systemInsets = with(LocalDensity.current) {
                WindowInsets(bottom = 15.dp.roundToPx()).union(WindowInsets(bottom = 48.dp.roundToPx()))
            }
            val keyboard = with(LocalDensity.current) { WindowInsets(bottom = 300.dp.roundToPx()) }
            Box(
                Modifier.size(500.dp)
                    .auleBottomSystemPadding(systemInsets)
                    .windowInsetsPadding(keyboard),
            ) {
                Box(Modifier.fillMaxSize().testTag("content"))
            }
        }
        compose.onNodeWithTag("content").assertHeightIsEqualTo(200.dp)
    }
}
