package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * La veille d'approche, côté domaine.
 *
 * Trois choses s'y jouent, et elles sont testées séparément parce qu'elles
 * échouent séparément : quand la veille parle, quels passages elle montre, et
 * quel véhicule elle accepte de désigner.
 */
class DepartureWatchTest {

    private val origin: Instant = Instant.ofEpochSecond(10_000)

    private val watch = DepartureWatch(
        stopName = "Ranzay",
        line = "C6",
        destination = "Hermeland",
        stopCoordinate = Coordinate(47.24, -1.53),
    )

    // ------------------------------------------------------------- le moteur

    /** Un temps annoncé n'est pas une position GPS : une lecture suffit. */
    @Test
    fun `le seuil alerte des le premier releve`() {
        val engine = DepartureWatchEngine(minutesBefore = 3)

        val alerts = engine.evaluate(Wait.Minutes(3), fresh = true)

        assertEquals(1, alerts.size)
        assertEquals(DepartureWatchAlertKind.MINUTES_BEFORE, alerts[0].kind)
        assertEquals(3, alerts[0].minutes)
    }

    /** Un seuil franchi trois fois de suite reste une seule alerte. */
    @Test
    fun `le loquet ne laisse passer qu une alerte par genre`() {
        val engine = DepartureWatchEngine(minutesBefore = 3)

        engine.evaluate(Wait.Minutes(3), fresh = true)
        val again = engine.evaluate(Wait.Minutes(2), fresh = true)
        val approach = engine.evaluate(Wait.Approaching, fresh = true)
        val approachAgain = engine.evaluate(Wait.Approaching, fresh = true)

        assertTrue(again.isEmpty())
        assertEquals(listOf(DepartureWatchAlertKind.APPROACHING), approach.map { it.kind })
        assertTrue(approachAgain.isEmpty())
    }

    /**
     * Une veille ouverte à 18 h doit encore parler à 18 h 20. Quand le bus
     * attendu est parti, le suivant devient le prochain et l'attente remonte :
     * c'est le signal du réarmement.
     */
    @Test
    fun `le passage suivant rearme la veille`() {
        val engine = DepartureWatchEngine(minutesBefore = 3)
        engine.evaluate(Wait.Approaching, fresh = true)

        // Le bus est passé : la ligne annonce maintenant son successeur.
        assertTrue(engine.evaluate(Wait.Minutes(12), fresh = true).isEmpty())
        val next = engine.evaluate(Wait.Minutes(2), fresh = true)

        assertEquals(
            listOf(DepartureWatchAlertKind.MINUTES_BEFORE),
            next.map { it.kind },
        )
    }

    /** Un temps réel qui oscille d'une minute n'est pas un nouveau bus. */
    @Test
    fun `une oscillation du temps reel ne rearme pas`() {
        val engine = DepartureWatchEngine(minutesBefore = 3)
        engine.evaluate(Wait.Minutes(2), fresh = true)

        engine.evaluate(Wait.Minutes(5), fresh = true)
        val back = engine.evaluate(Wait.Minutes(1), fresh = true)

        assertTrue(back.isEmpty())
    }

    /** Annoncer une approche sur un tableau périmé fait manquer le bus. */
    @Test
    fun `rien ne part d une donnee perimee`() {
        val engine = DepartureWatchEngine(minutesBefore = 3)

        assertTrue(engine.evaluate(Wait.Approaching, fresh = false).isEmpty())
        assertTrue(engine.evaluate(Wait.Approaching, fresh = true).isNotEmpty())
    }

    /** Le silence ne dit pas que le bus arrive : il dit qu'on ne sait plus. */
    @Test
    fun `plus rien d annonce n alerte pas`() {
        val engine = DepartureWatchEngine(minutesBefore = 3)

        assertTrue(engine.evaluate(wait = null, fresh = true).isEmpty())
    }

    /**
     * Armer une veille sur un bus déjà là franchit les deux seuils d'un coup :
     * une seule alerte part, et c'est la plus vraie des deux.
     */
    @Test
    fun `une veille armee tard n annonce que l approche`() {
        val engine = DepartureWatchEngine(minutesBefore = 3)

        val alerts = engine.evaluate(Wait.Approaching, fresh = true)

        assertEquals(listOf(DepartureWatchAlertKind.APPROACHING), alerts.map { it.kind })
        // Le seuil a bien été franchi : il ne reparlera pas au relevé suivant.
        assertTrue(engine.hasFired(DepartureWatchAlertKind.MINUTES_BEFORE))
    }

    // ------------------------------------------------------------ la fraîcheur

    @Test
    fun `un tableau de plus de trois minutes n engage plus`() {
        val table = departures(fetchedAt = origin)

        assertTrue(table.isFresh(origin.plusSeconds(120)))
        assertTrue(!table.isFresh(origin.plusSeconds(200)))
    }

    // -------------------------------------------------------- les horaires

    /**
     * Le tableau d'un arrêt résume ; la ligne ouverte rend sa colonne entière,
     * y compris ce que l'horizon des rangées coupe.
     */
    @Test
    fun `la ligne rend tous ses passages, dans l ordre`() {
        val table = StopDepartures(
            stopName = "Ranzay",
            departures = listOf(
                departure("C6", "Hermeland", 44),
                departure("23", "Bellevue", 4),
                departure("C6", "Chantrerie", 6),
                departure("C6", "hermeland", 14),
            ),
            outcome = DeparturesOutcome.ANNOUNCED,
            fetchedAt = origin,
        )

        val times = table.forLine("c6", "Hermeland")

        assertEquals(listOf(14L, 44L), times.map { it.waitMinutes(origin).toLong() })
    }

    // ------------------------------------------------------- le rapprochement

    /** Le prochain arrêt départage deux bus de la même ligne. */
    @Test
    fun `le vehicule retenu est celui dont le prochain arret est le notre`() {
        val ahead = vehicle(id = "loin", nextStop = "Chassay", coordinate = Coordinate(47.25, -1.53))
        val ours = vehicle(id = "nous", nextStop = "Ranzay", coordinate = Coordinate(47.30, -1.60))

        assertEquals("nous", matchWatchedVehicle(listOf(ahead, ours), watch)?.id)
    }

    /** Le même numéro dans l'autre sens n'est pas le bus qu'on attend. */
    @Test
    fun `le sens oppose est ecarte`() {
        val opposite = vehicle(
            id = "retour",
            nextStop = "Ranzay",
            destination = "Chantrerie - Grandes Écoles",
            coordinate = Coordinate(47.24, -1.53),
        )

        assertNull(matchWatchedVehicle(listOf(opposite), watch))
    }

    /** Sans prochain arrêt publié, la proximité décide — mais pas à six kilomètres. */
    @Test
    fun `un bus trop loin n est pas celui qu on attend`() {
        val far = vehicle(id = "ailleurs", nextStop = null, coordinate = Coordinate(47.30, -1.60))
        val near = vehicle(id = "proche", nextStop = null, coordinate = Coordinate(47.245, -1.532))

        assertNull(matchWatchedVehicle(listOf(far), watch))
        assertEquals("proche", matchWatchedVehicle(listOf(far, near), watch)?.id)
    }

    /** Aucune ligne ne correspond : la veille continue, sans véhicule. */
    @Test
    fun `une autre ligne ne designe personne`() {
        val other = vehicle(id = "autre", line = "23", nextStop = "Ranzay")

        assertNull(matchWatchedVehicle(listOf(other), watch))
    }

    // ------------------------------------------------------------- fabriques

    private fun departure(
        line: String,
        destination: String,
        inMinutes: Long,
    ) = StopDeparture(
        id = "$line-$destination-$inMinutes",
        line = line,
        lineColor = "#8d6cbf",
        destination = destination,
        expectedAt = origin.plusSeconds(inMinutes * 60),
        isRealtime = true,
        mode = TransportMode.BUS,
    )

    private fun departures(fetchedAt: Instant) = StopDepartures(
        stopName = "Ranzay",
        departures = listOf(departure("C6", "Hermeland", 5)),
        outcome = DeparturesOutcome.ANNOUNCED,
        fetchedAt = fetchedAt,
    )

    private fun vehicle(
        id: String,
        line: String = "C6",
        nextStop: String?,
        destination: String? = "Hermeland",
        coordinate: Coordinate = Coordinate(47.24, -1.53),
        etaSeconds: Double? = null,
    ) = TransportVehicle(
        id = id,
        mode = TransportMode.BUS,
        feed = VehicleFeed.LIVE,
        lineId = line,
        lineName = line,
        destination = destination,
        coordinate = coordinate,
        nextStop = nextStop,
        etaSeconds = etaSeconds,
    )
}
