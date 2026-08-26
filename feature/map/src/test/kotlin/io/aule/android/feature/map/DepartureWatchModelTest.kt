package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.DepartureWatch
import io.aule.android.core.model.DepartureWatchAlert
import io.aule.android.core.model.DepartureWatchAlertKind
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDeparture
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.VehicleFeed
import io.aule.android.core.model.repository.StopRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * La veille, du côté où elle se branche.
 *
 * Le domaine sait déjà quand alerter ([io.aule.android.core.model.DepartureWatchEngine]) ;
 * ce qui se teste ici est ce que le domaine ignore : qui sonde, jusqu'à quand,
 * et ce qui reste quand le volet se referme.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DepartureWatchModelTest {

    private val watch = DepartureWatch(
        stopName = "Ranzay",
        line = "C6",
        destination = "Hermeland",
        stopCoordinate = Coordinate(47.24, -1.53),
    )

    @Test
    fun `le volet ouvert ne montre que la ligne demandee`() = runTest {
        val stops = FakeStops(minutesUntilNext = 12)
        val model = model(stops)

        model.open(watch)
        runCurrent()

        val times = model.state.value.times()
        assertEquals(listOf("C6"), times.map { it.line }.distinct())
        assertEquals(listOf("Hermeland"), times.map { it.destination }.distinct())
    }

    /** Une veille sert à ne plus regarder : elle doit survivre au volet. */
    @Test
    fun `la veille continue apres la fermeture du volet`() = runTest {
        val stops = FakeStops(minutesUntilNext = 20)
        val model = model(stops)

        model.open(watch)
        runCurrent()
        model.arm()
        model.close()
        val callsBefore = stops.calls
        advanceTimeBy(DEPARTURES_REFRESH_MS * 3)
        runCurrent()

        assertTrue(stops.calls > callsBefore, "le sondage s'est arrêté avec le volet")
        // `isArmed` parle du bouton, donc de la ligne affichée : volet fermé, il
        // est faux alors que la veille, elle, tourne toujours.
        assertNotNull(model.state.value.armed)
    }

    /**
     * Le défaut qu'on ne verrait jamais : jeter un œil à une autre ligne
     * déplaçait la veille sur elle, en silence, et le bus attendu passait sans
     * rien dire.
     */
    @Test
    fun `regarder une autre ligne ne deplace pas la veille`() = runTest {
        val stops = FakeStops(minutesUntilNext = 20)
        val alerts = mutableListOf<DepartureWatchAlert>()
        val model = model(stops, alerts)

        model.open(watch)
        runCurrent()
        model.arm()
        model.open(watch.copy(line = "23", destination = "Bellevue"))
        runCurrent()

        assertEquals("C6", model.state.value.armed?.line)
        assertEquals("23", model.state.value.viewed?.line)
        // Le bouton parle de la ligne qu'on regarde, pas de celle qu'on veille.
        assertTrue(!model.state.value.isArmed)
        // Et la 23, qui passe dans trois minutes, n'a rien déclenché.
        assertTrue(alerts.isEmpty())
    }

    /** Deux arrêts distincts se sondent dans le même tour, au même rythme. */
    @Test
    fun `une veille ailleurs continue d etre sondee`() = runTest {
        val stops = FakeStops(minutesUntilNext = 20)
        val model = model(stops)

        model.open(watch)
        runCurrent()
        model.arm()
        model.open(watch.copy(stopName = "Chassay"))
        runCurrent()
        val callsBefore = stops.calls
        advanceTimeBy(DEPARTURES_REFRESH_MS + 1)
        runCurrent()

        assertEquals(callsBefore + 2, stops.calls)
    }

    /** Fermé et désarmé, plus rien ne doit tourner en fond. */
    @Test
    fun `un volet ferme sans veille arrete tout`() = runTest {
        val stops = FakeStops(minutesUntilNext = 20)
        val model = model(stops)

        model.open(watch)
        runCurrent()
        model.close()
        val callsBefore = stops.calls
        advanceTimeBy(DEPARTURES_REFRESH_MS * 3)
        runCurrent()

        assertEquals(callsBefore, stops.calls)
        assertNull(model.state.value.viewed)
    }

    /** Le seuil parle une fois, l'approche parle une fois, puis c'est fini. */
    @Test
    fun `la veille alerte puis se termine a l approche`() = runTest {
        val stops = FakeStops(minutesUntilNext = 2)
        val alerts = mutableListOf<DepartureWatchAlert>()
        val model = model(stops, alerts)

        model.open(watch)
        runCurrent()
        model.arm()
        runCurrent()

        assertEquals(
            listOf(
                DepartureWatchAlertKind.MINUTES_BEFORE,
            ),
            alerts.map { it.kind },
        )
        assertTrue(model.state.value.isArmed)

        stops.minutesUntilNext = 0
        advanceTimeBy(DEPARTURES_REFRESH_MS + 1)
        runCurrent()

        assertEquals(
            listOf(
                DepartureWatchAlertKind.MINUTES_BEFORE,
                DepartureWatchAlertKind.APPROACHING,
            ),
            alerts.map { it.kind },
        )
        // L'office est rempli : sans cela, la veille annoncerait ensuite tous
        // les passages de la soirée.
        assertNull(model.state.value.armed)
    }

    /** Une veille armée sur un bus déjà là n'attend pas le prochain sondage. */
    @Test
    fun `armer sur un tableau deja charge alerte tout de suite`() = runTest {
        val stops = FakeStops(minutesUntilNext = 1)
        val alerts = mutableListOf<DepartureWatchAlert>()
        val model = model(stops, alerts)

        model.open(watch)
        runCurrent()
        val callsBefore = stops.calls
        model.arm()

        assertEquals(callsBefore, stops.calls, "l'alerte a attendu un nouveau sondage")
        assertEquals(DepartureWatchAlertKind.MINUTES_BEFORE, alerts.single().kind)
    }

    /**
     * Le véhicule se reconnaît dès que la ligne est ouverte, avant toute
     * alerte : c'est lui qui décide si « Focus » a quelque chose à suivre.
     */
    @Test
    fun `le vehicule se reconnait des la ligne ouverte`() = runTest {
        val stops = FakeStops(minutesUntilNext = 6)
        val model = model(stops)

        model.open(watch)
        runCurrent()
        model.onFleetSnapshot(FleetSnapshot(vehicles = listOf(vehicle())))

        assertEquals("bus-1", model.state.value.vehicleId)
    }

    /** Sans véhicule reconnu, il n'y a rien à suivre — et le focus le refuse. */
    @Test
    fun `le focus ne s allume pas sans vehicule`() = runTest {
        val stops = FakeStops(minutesUntilNext = 6)
        val model = model(stops)

        model.open(watch)
        runCurrent()
        model.setFocused(true)
        assertTrue(!model.state.value.isFocused)

        model.onFleetSnapshot(FleetSnapshot(vehicles = listOf(vehicle())))
        model.setFocused(true)
        assertTrue(model.state.value.isFocused)
    }

    /**
     * Le véhicule sort du flux — terminus, panne de balise — et la carte
     * cesserait de bouger sans rien dire. Le focus tombe avec lui.
     */
    @Test
    fun `le focus tombe avec le vehicule qui disparait`() = runTest {
        val stops = FakeStops(minutesUntilNext = 6)
        val model = model(stops)

        model.open(watch)
        runCurrent()
        model.onFleetSnapshot(FleetSnapshot(vehicles = listOf(vehicle())))
        model.setFocused(true)
        model.onFleetSnapshot(FleetSnapshot(vehicles = emptyList()))

        assertNull(model.state.value.vehicleId)
        assertTrue(!model.state.value.isFocused)
    }

    /** Changer de ligne rend la caméra : le véhicule suivi était celui d'avant. */
    @Test
    fun `changer de ligne coupe le focus`() = runTest {
        val stops = FakeStops(minutesUntilNext = 6)
        val model = model(stops)

        model.open(watch)
        runCurrent()
        model.onFleetSnapshot(FleetSnapshot(vehicles = listOf(vehicle())))
        model.setFocused(true)
        model.open(watch.copy(line = "23", destination = "Bellevue"))

        assertTrue(!model.state.value.isFocused)
        assertNull(model.state.value.vehicleId)
    }

    /** Un instantané périmé ferait suivre un marqueur qui n'est plus là. */
    @Test
    fun `un instantane perime ne change pas le vehicule suivi`() = runTest {
        val stops = FakeStops(minutesUntilNext = 6)
        val model = model(stops)

        model.open(watch)
        runCurrent()
        model.onFleetSnapshot(FleetSnapshot(vehicles = listOf(vehicle())))
        model.onFleetSnapshot(FleetSnapshot(vehicles = emptyList(), isStale = true))

        assertEquals("bus-1", model.state.value.vehicleId)
    }

    /** Une panne n'efface pas les horaires affichés — elle les laisse vieillir. */
    @Test
    fun `un sondage en echec garde le tableau precedent`() = runTest {
        val stops = FakeStops(minutesUntilNext = 8)
        val model = model(stops)

        model.open(watch)
        runCurrent()
        stops.failing = true
        advanceTimeBy(DEPARTURES_REFRESH_MS + 1)
        runCurrent()

        assertTrue(model.state.value.failed)
        assertTrue(model.state.value.times().isNotEmpty())
    }

    // ------------------------------------------------------------- fabriques

    private fun kotlinx.coroutines.test.TestScope.model(
        stops: FakeStops,
        alerts: MutableList<DepartureWatchAlert> = mutableListOf(),
    ): DepartureWatchModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DepartureWatchModel(
            repository = stops,
            dispatchers = TestDispatchers(dispatcher),
            scope = backgroundScope,
            logger = NoopLogger,
            clock = { stops.now },
            onAlert = { alert, _ -> alerts += alert },
        )
    }

    private fun vehicle() = TransportVehicle(
        id = "bus-1",
        mode = TransportMode.BUS,
        feed = VehicleFeed.LIVE,
        lineId = "C6",
        lineName = "C6",
        destination = "Hermeland",
        coordinate = Coordinate(47.24, -1.53),
        nextStop = "Ranzay",
    )

    /**
     * Un arrêt qui annonce toujours la même chose, à l'heure qu'on lui dit.
     *
     * L'horloge du faux est **celle du tableau** : c'est ce qui permet d'écrire
     * « il reste deux minutes » sans faire avancer le temps du test, et de
     * vérifier au passage qu'aucune alerte ne part d'un tableau périmé.
     */
    private class FakeStops(var minutesUntilNext: Long) : StopRepository {
        var calls = 0
        var failing = false
        var now: Instant = Instant.ofEpochSecond(100_000)

        override suspend fun allStops() = emptyList<io.aule.android.core.model.TransitStop>()

        override suspend fun departures(atStopNamed: String): StopDepartures {
            calls++
            if (failing) error("502")
            return StopDepartures(
                stopName = atStopNamed,
                departures = listOf(
                    departure("C6", "Hermeland", minutesUntilNext),
                    departure("C6", "Hermeland", minutesUntilNext + 12),
                    departure("23", "Bellevue", 3),
                    departure("C6", "Chantrerie", 4),
                ),
                outcome = DeparturesOutcome.ANNOUNCED,
                fetchedAt = now,
            )
        }

        override suspend fun servingLines(atStopNamed: String) = emptyList<ServingLine>()

        private fun departure(line: String, destination: String, inMinutes: Long) = StopDeparture(
            id = "$line-$destination-$inMinutes",
            line = line,
            lineColor = null,
            destination = destination,
            expectedAt = now.plusSeconds(inMinutes * 60),
            isRealtime = true,
            mode = TransportMode.BUS,
        )
    }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : AuleDispatchers {
        override val default = dispatcher
        override val io = dispatcher
        override val main = dispatcher
    }
}
