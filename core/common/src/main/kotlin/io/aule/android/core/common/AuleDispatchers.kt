package io.aule.android.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Les dispatchers, passés plutôt que référencés.
 *
 * Un `Dispatchers.IO` écrit en dur dans un repository est un repository qu'on ne
 * peut pas tester sans horloge réelle. On les injecte donc, et les tests passent
 * un dispatcher de test.
 */
interface AuleDispatchers {
    /** Calcul : projection sur tracé, interpolation, tri. */
    val default: CoroutineDispatcher

    /** Attente : réseau, disque. */
    val io: CoroutineDispatcher

    /** Le thread principal — MapLibre et l'UI l'exigent. */
    val main: CoroutineDispatcher
}

object DefaultDispatchers : AuleDispatchers {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Main.immediate
}
