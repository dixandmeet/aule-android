package io.aule.android.core.designsystem.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuleMessengerTest {
    @Test
    fun `les confirmations restent en ordre et une fermeture ne lance pas l action`() = runTest {
        val host = SnackbarHostState()
        val messenger = AuleMessenger(backgroundScope, host)
        var acted = false
        messenger.say("Lieu enregistré", "Annuler") { acted = true }
        messenger.say("Ligne suivie")
        runCurrent()
        assertEquals("Lieu enregistré", host.currentSnackbarData?.visuals?.message)
        host.currentSnackbarData!!.dismiss()
        runCurrent()
        assertFalse(acted)
        assertEquals("Ligne suivie", host.currentSnackbarData?.visuals?.message)
        host.currentSnackbarData!!.dismiss()
        runCurrent()
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `seul l appui sur l action execute son rappel`() = runTest {
        val host = SnackbarHostState()
        val messenger = AuleMessenger(backgroundScope, host)
        var acted = false
        messenger.say("Lieu enregistré", "Annuler") { acted = true }
        runCurrent()
        host.currentSnackbarData!!.performAction()
        runCurrent()
        assertTrue(acted)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `quitter l ecran annule les messages actifs et en attente`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val host = SnackbarHostState()
        val messenger = AuleMessenger(scope, host)
        var acted = false
        try {
            messenger.say("Lieu enregistré", "Annuler") { acted = true }
            messenger.say("Ligne suivie")
            runCurrent()
            scope.cancel()
            runCurrent()
            assertNull(host.currentSnackbarData)
            assertFalse(acted)
        } finally {
            scope.cancel()
        }
    }
}
