package io.aule.android.core.location

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * La garde du service de premier plan.
 *
 * Elle tient la règle que l'arbitre a perdue : demander un service qui tourne déjà ne sert à
 * rien, sauf si le libellé de sa notification doit changer (BUG-AND-220, S21, 22/09/2026 —
 * trois démarrages en dix millisecondes sur un simple retour au premier plan).
 */
@DisplayName("La garde du service de premier plan")
class ForegroundServiceGateTest {

    @Test
    @DisplayName("rien ne tourne : il faut démarrer")
    fun premierDemarrage() {
        val gate = ForegroundServiceGate()

        assertFalse(gate.isActive, "rien n'a encore été demandé")
        assertTrue(gate.needsStart(onDuty = false))
    }

    @Test
    @DisplayName("le même palier ne se redemande pas")
    fun pasDeuxFois() {
        // C'est le défaut lui-même : l'arbitre synchronise trois fois de suite pour un seul
        // guidage, et chaque passage repartait au système.
        val gate = ForegroundServiceGate()
        gate.started(onDuty = false)

        assertTrue(gate.isActive)
        assertFalse(gate.needsStart(onDuty = false), "le service tourne déjà sous ce libellé")
        assertFalse(gate.needsStart(onDuty = false), "et il tourne toujours au passage suivant")
    }

    @Test
    @DisplayName("changer de libellé repart, dans les deux sens")
    fun leLibelleCompte() {
        // ⚠️ Une garde qui ne retiendrait que « ça tourne » laisserait la barre d'état annoncer
        // un guidage pendant un service, ou l'inverse : un défaut d'affichage échangé contre un
        // défaut de démarrage.
        val gate = ForegroundServiceGate()

        gate.started(onDuty = false)
        assertTrue(gate.needsStart(onDuty = true), "le guidage passe en service")

        gate.started(onDuty = true)
        assertTrue(gate.needsStart(onDuty = false), "et le service repasse en guidage")
        assertFalse(gate.needsStart(onDuty = true))
    }

    @Test
    @DisplayName("après un arrêt, tout se redemande")
    fun apresArret() {
        val gate = ForegroundServiceGate()
        gate.started(onDuty = false)
        gate.stopped()

        assertFalse(gate.isActive, "il n'y a plus rien à arrêter")
        assertTrue(gate.needsStart(onDuty = false))
        assertTrue(gate.needsStart(onDuty = true))
    }

    @Test
    @DisplayName("un démarrage refusé ne bloque pas le suivant")
    fun leRefusNeBloquePas() {
        // Le refus passe par `stopped()` : sans cela, une garde optimiste retiendrait qu'un
        // service tourne alors qu'il n'a jamais pu se poser, et la synchronisation suivante —
        // celle qui aurait peut-être réussi — ne partirait plus jamais.
        val gate = ForegroundServiceGate()
        gate.stopped()

        assertTrue(gate.needsStart(onDuty = false))
    }
}
