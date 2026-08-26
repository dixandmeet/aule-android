package io.aule.android.core.guet

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce qu'on programme d'avance, et ce qu'on sacrifie.
 *
 * Deux règles y sont vérifiées, et la première surprend :
 *
 * **Loin dans le temps, le théorique bat le temps réel.** Une notification
 * programmée porte l'heure calculée au moment de la programmation ; une mesure
 * dérive et rien ne la corrigera, un horaire est faux d'une façon stable donc
 * programmable.
 *
 * **La troncature s'annonce.** Une troncature silencieuse se lit « on a tout
 * couvert », ce qu'on ne peut pas laisser croire d'une veille.
 *
 * Port de `GuetScheduleTests.swift`.
 */
class GuetScheduleTest {

    private val reference: Instant = Instant.ofEpochSecond(1_755_792_000)
    private val here = Coordinate(latitude = 47.2136, longitude = -1.5601)

    private val stop = TransitStop(
        id = "COMM",
        name = "Commerce quai B",
        coordinate = here,
        mode = TransportMode.TRAM,
        stationName = "Commerce",
    )

    private fun candidate(
        line: String = "1",
        inSeconds: Long,
        isRealtime: Boolean = true,
        walk: Int = 300,
        feasibility: GuetFeasibility = GuetFeasibility.COMFORTABLE,
    ): GuetCandidate {
        val expectedAt = reference.plusSeconds(inSeconds)
        return GuetCandidate(
            key = PassageKey.make("Commerce", line, "Beaujoire", expectedAt),
            place = "Commerce",
            stop = stop,
            line = line,
            destination = "Beaujoire",
            mode = TransportMode.TRAM,
            isRealtime = isRealtime,
            timing = GuetTiming.of(expectedAt, walkSeconds = walk, platformSeconds = 0),
            level = GuetLevel(GuetPhase.TOO_EARLY, feasibility),
            score = GuetScore(emptyMap()),
        )
    }

    // ------------------------------------------------------------ les horizons

    /**
     * ⚠️ **L'inversion qu'il ne faut pas « corriger ».** Une mesure programmée
     * quarante minutes à l'avance sonnerait sur une heure vieille de quarante
     * minutes, sans que rien puisse la corriger.
     */
    @Test
    fun `le temps reel ne se programme pas au-dela d une demi-heure`() {
        val far = candidate(inSeconds = (GuetSchedule.REALTIME_HORIZON + 60).toLong())

        val plan = GuetSchedule.plan(listOf(far), GuetLedger(), reference)

        assertTrue(plan.alerts.isEmpty())
        assertEquals(0, plan.dropped, "hors horizon n'est pas sacrifié, c'est hors sujet")
    }

    @Test
    fun `le theorique se programme bien plus loin`() {
        val far = candidate(
            inSeconds = (GuetSchedule.REALTIME_HORIZON + 600).toLong(),
            isRealtime = false,
        )

        val plan = GuetSchedule.plan(listOf(far), GuetLedger(), reference)

        assertEquals(1, plan.alerts.size)
    }

    @Test
    fun `le theorique previent un peu plus tot`() {
        // Dans l'horizon du temps réel, sinon la comparaison n'a qu'un terme.
        val at = 1200L
        val live = candidate(inSeconds = at, isRealtime = true)
        val timetable = candidate(inSeconds = at, isRealtime = false, line = "2")

        val livePlan = GuetSchedule.plan(listOf(live), GuetLedger(), reference)
        val timetablePlan = GuetSchedule.plan(listOf(timetable), GuetLedger(), reference)

        // Un horaire est juste en moyenne et faux à la minute : mieux vaut
        // attendre au quai que regarder partir.
        assertEquals(
            GuetSchedule.TIMETABLE_MARGIN,
            livePlan.alerts.single().fireAt.epochSecond -
                timetablePlan.alerts.single().fireAt.epochSecond,
        )
    }

    @Test
    fun `un instant deja passe ne se programme pas`() {
        // Le présent est l'affaire de l'alerte en application, qui est déjà là et
        // qui sait répondre.
        val imminent = candidate(inSeconds = 120, walk = 300)

        val plan = GuetSchedule.plan(listOf(imminent), GuetLedger(), reference)

        assertTrue(plan.alerts.isEmpty())
    }

    @Test
    fun `un vehicule deja passe ne se programme pas`() {
        val gone = candidate(inSeconds = -60)

        assertTrue(GuetSchedule.plan(listOf(gone), GuetLedger(), reference).alerts.isEmpty())
    }

    // ------------------------------------------------------------- le registre

    /**
     * La même règle qu'en application, et elle doit valoir hors d'elle : sans
     * cela, on réveillerait quelqu'un pour un bus qu'il vient de refuser.
     */
    @Test
    fun `un passage refuse ou deja annonce ne se programme pas`() {
        val one = candidate(inSeconds = 1200)
        var ledger = GuetLedger().record(
            PassageStatus.Declined(reference),
            one.key,
            one.place,
            one.line,
            one.destination,
            one.timing.expectedAt,
            reference,
        )

        assertTrue(GuetSchedule.plan(listOf(one), ledger, reference).alerts.isEmpty())

        ledger = GuetLedger().record(
            PassageStatus.Alerted(reference),
            one.key,
            one.place,
            one.line,
            one.destination,
            one.timing.expectedAt,
            reference,
        )
        assertTrue(GuetSchedule.plan(listOf(one), ledger, reference).alerts.isEmpty())
    }

    @Test
    fun `un passage seulement repere se programme`() {
        val one = candidate(inSeconds = 1200)
        val ledger = GuetLedger().record(
            PassageStatus.Detected,
            one.key,
            one.place,
            one.line,
            one.destination,
            one.timing.expectedAt,
            reference,
        )

        assertEquals(1, GuetSchedule.plan(listOf(one), ledger, reference).alerts.size)
    }

    @Test
    fun `un passage inatteignable ne se programme pas`() {
        val lost = candidate(inSeconds = 1200, feasibility = GuetFeasibility.MISSED)

        assertTrue(GuetSchedule.plan(listOf(lost), GuetLedger(), reference).alerts.isEmpty())
    }

    // -------------------------------------------------------------- le plafond

    /**
     * ⚠️ **Le test qui interdit la troncature silencieuse.** Elle se lit « on a
     * tout couvert », et quelqu'un qui compte sur la veille n'aurait aucun moyen
     * de savoir qu'elle s'est arrêtée au douzième.
     */
    @Test
    fun `au-dela du plafond la troncature s annonce`() {
        val many = (1..GuetSchedule.MAX_SCHEDULED + 5).map { index ->
            candidate(line = "L$index", inSeconds = 600 + index * 30L, isRealtime = false)
        }

        val plan = GuetSchedule.plan(many, GuetLedger(), reference)

        assertEquals(GuetSchedule.MAX_SCHEDULED, plan.alerts.size)
        assertEquals(5, plan.dropped)
    }

    /** Le plus tôt d'abord : c'est celui qu'on raterait si on le sacrifiait. */
    @Test
    fun `le plus tot est programme en premier et le plus tard sacrifie`() {
        val many = (1..GuetSchedule.MAX_SCHEDULED + 1).map { index ->
            candidate(line = "L$index", inSeconds = 600 + index * 60L, isRealtime = false)
        }.reversed()

        val plan = GuetSchedule.plan(many, GuetLedger(), reference)

        assertEquals(
            plan.alerts.map { it.fireAt }.sorted(),
            plan.alerts.map { it.fireAt },
            "programmés dans l'ordre où ils tombent",
        )
        assertEquals("L1", plan.alerts.first().line)
        assertTrue(plan.alerts.none { it.line == "L13" }, "le plus tard est celui qu'on sacrifie")
    }

    @Test
    fun `un meme passage n est programme qu une fois`() {
        val twice = candidate(inSeconds = 1200)

        val plan = GuetSchedule.plan(listOf(twice, twice), GuetLedger(), reference)

        assertEquals(1, plan.alerts.size)
    }

    @Test
    fun `l alerte porte de quoi l ecrire sans une seule phrase`() {
        val one = candidate(inSeconds = 1200)

        val alert = GuetSchedule.plan(listOf(one), GuetLedger(), reference).alerts.single()

        // Des valeurs, pas des phrases : la formulation vit dans les ressources.
        assertEquals("1", alert.line)
        assertEquals("Beaujoire", alert.destination)
        assertEquals("Commerce", alert.place)
        assertEquals("Commerce quai B", alert.stopName)
        assertEquals(one.timing.expectedAt, alert.expectedAt)
        assertEquals(one.key, alert.key)
    }
}
