package io.aule.android.core.guet

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.NearbyDigest
import io.aule.android.core.model.StopDeparture
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le moteur : de ce qu'on sait à ce qu'on annonce.
 *
 * Trois défauts que ce fichier empêche, et chacun a coûté quelque chose côté iOS :
 *
 * 1. **La déduplication avant le classement** faisait disparaître de la veille un
 *    bus parfaitement attrapable, parce qu'un passage inatteignable au même
 *    couple avait réservé la place à l'arrêt le plus proche.
 * 2. **Le passage qui disparaît pendant l'accompagnement** figeait le volet sur
 *    son dernier niveau confortable pendant qu'on s'éloignait du quai.
 * 3. **Un seuil de score sans condition de phase** sonnerait un quart d'heure
 *    trop tôt.
 *
 * Port de `GuetEngineTests.swift`.
 */
class GuetEngineTest {

    private val reference: Instant = Instant.ofEpochSecond(1_755_792_000)
    private val utc: ZoneId = ZoneId.of("UTC")
    private val here = Coordinate(latitude = 47.2136, longitude = -1.5601)

    /** Environ 210 m au nord — soit un peu plus de deux minutes de marche. */
    private val nearby = Coordinate(latitude = 47.2155, longitude = -1.5601)

    private fun stop(name: String, at: Coordinate, mode: TransportMode = TransportMode.TRAM) =
        TransitStop(id = name, name = name, coordinate = at, mode = mode, stationName = name)

    private fun departure(
        line: String = "1",
        destination: String = "Beaujoire",
        inSeconds: Long = 600,
        isRealtime: Boolean = true,
        mode: TransportMode? = TransportMode.TRAM,
    ) = StopDeparture(
        id = "$line-$destination-$inSeconds",
        line = line,
        destination = destination,
        expectedAt = reference.plusSeconds(inSeconds),
        isRealtime = isRealtime,
        mode = mode,
    )

    private fun context(
        stops: List<Pair<TransitStop, List<StopDeparture>>>,
        preferences: GuetPreferences = GuetPreferences(isEnabled = true),
        ledger: GuetLedger = GuetLedger(),
        habits: GuetHabits = GuetHabits(),
        activeDestination: String? = null,
        position: Coordinate = here,
    ) = GuetContext(
        now = reference,
        position = position,
        nearby = stops.map { (stop, _) ->
            NearbyDigest.StopEntry(
                stop = stop,
                distanceMeters = io.aule.android.core.geo.GeoMath.distance(position, stop.coordinate),
            )
        },
        departures = stops.associate { (stop, list) ->
            stop.departuresKey to StopDepartures(
                stopName = stop.departuresKey,
                departures = list,
                outcome = DeparturesOutcome.ANNOUNCED,
                fetchedAt = reference,
            )
        },
        preferences = preferences,
        habits = habits,
        ledger = ledger,
        activeDestination = activeDestination,
        zone = utc,
    )

    // ------------------------------------------------------------ l'interrupteur

    @Test
    fun `une veille eteinte ne propose rien`() {
        val ctx = context(
            listOf(stop("Commerce", here) to listOf(departure())),
            preferences = GuetPreferences(isEnabled = false),
        )

        assertTrue(GuetEngine.candidates(ctx).isEmpty())
    }

    @Test
    fun `une veille allumee propose ce qui est atteignable`() {
        val ctx = context(listOf(stop("Commerce", here) to listOf(departure())))

        val found = GuetEngine.candidates(ctx)

        assertEquals(1, found.size)
        assertEquals("1", found.single().line)
        assertTrue(found.single().level.isReachable)
    }

    // ------------------------------------------------------------- les filtres

    @Test
    fun `un mode decoche n est pas un candidat`() {
        val ctx = context(
            listOf(stop("Commerce", here) to listOf(departure(mode = TransportMode.TRAM))),
            preferences = GuetPreferences(isEnabled = true, modes = setOf(TransportMode.BUS)),
        )

        assertTrue(GuetEngine.candidates(ctx).isEmpty())
    }

    /** « On ne sait pas » n'est pas « non ». */
    @Test
    fun `un mode absent laisse passer`() {
        val ctx = context(
            listOf(stop("Commerce", here) to listOf(departure(mode = null))),
            preferences = GuetPreferences(isEnabled = true, modes = setOf(TransportMode.BUS)),
        )

        assertEquals(1, GuetEngine.candidates(ctx).size)
    }

    @Test
    fun `au-dela de l horizon ce n est pas encore l affaire du Guet`() {
        val far = departure(inSeconds = (GuetEngine.HORIZON + 600).toLong())
        val ctx = context(listOf(stop("Commerce", here) to listOf(far)))

        assertTrue(GuetEngine.candidates(ctx).isEmpty())
    }

    @Test
    fun `un passage manque ne se propose pas`() {
        val gone = departure(inSeconds = -120)
        val ctx = context(listOf(stop("Commerce", here) to listOf(gone)))

        assertTrue(GuetEngine.candidates(ctx).isEmpty())
    }

    // ---------------------------------------------------------- le classement

    @Test
    fun `une ligne suivie passe devant une ligne quelconque`() {
        val commerce = stop("Commerce", here)
        val ctx = context(
            listOf(
                commerce to listOf(
                    departure(line = "1", destination = "Beaujoire"),
                    departure(line = "2", destination = "Orvault"),
                ),
            ),
            preferences = GuetPreferences(isEnabled = true, followedLines = setOf("2")),
        )

        assertEquals("2", GuetEngine.candidates(ctx).first().line)
    }

    @Test
    fun `un passage refuse tombe au classement`() {
        val commerce = stop("Commerce", here)
        val refused = departure(line = "1", destination = "Beaujoire")
        val other = departure(line = "2", destination = "Orvault")
        val key = PassageKey.make("Commerce", "1", "Beaujoire", refused.expectedAt)
        val ledger = GuetLedger().record(
            PassageStatus.Declined(reference),
            key,
            "Commerce",
            "1",
            "Beaujoire",
            refused.expectedAt,
            reference,
        )

        val ctx = context(listOf(commerce to listOf(refused, other)), ledger = ledger)

        assertEquals("2", GuetEngine.candidates(ctx).first().line)
    }

    // ------------------------------------------------------ la déduplication

    /**
     * ⚠️ **Le défaut n° 1.** Un passage inatteignable à l'arrêt le plus proche
     * réservait le couple, et le même bus, parfaitement attrapable trois minutes
     * plus loin, disparaissait de la veille entière.
     */
    @Test
    fun `un passage inatteignable ne reserve pas le couple pour un autre arret`() {
        val close = stop("Commerce", here)
        val further = stop("Bouffay", nearby)

        val ctx = context(
            listOf(
                // Déjà parti : inatteignable, donc écarté avant toute réservation.
                close to listOf(departure(line = "1", destination = "Beaujoire", inSeconds = -60)),
                further to listOf(departure(line = "1", destination = "Beaujoire", inSeconds = 900)),
            ),
        )

        val found = GuetEngine.candidates(ctx)

        assertEquals(1, found.size)
        assertEquals("Bouffay", found.single().place, "le bus attrapable survit")
    }

    @Test
    fun `un couple n est propose qu a un seul arret`() {
        val close = stop("Commerce", here)
        val further = stop("Bouffay", nearby)
        val ctx = context(
            listOf(
                close to listOf(departure(line = "1", destination = "Beaujoire", inSeconds = 900)),
                further to listOf(departure(line = "1", destination = "Beaujoire", inSeconds = 960)),
            ),
        )

        val places = GuetEngine.candidates(ctx).map { it.place }.toSet()

        assertEquals(1, places.size)
    }

    /**
     * **La clé est le couple, l'unité écartée est l'arrêt — pas le passage.**
     * Dédupliquer jusqu'au passage rendrait le rang 0 des alternatives
     * inatteignable.
     */
    @Test
    fun `les passages successifs du meme couple au meme arret survivent tous`() {
        val commerce = stop("Commerce", here)
        val ctx = context(
            listOf(
                commerce to listOf(
                    departure(line = "1", destination = "Beaujoire", inSeconds = 600),
                    departure(line = "1", destination = "Beaujoire", inSeconds = 960),
                ),
            ),
        )

        assertEquals(2, GuetEngine.candidates(ctx).size)
    }

    @Test
    fun `les deux sens d une ligne restent distincts`() {
        val commerce = stop("Commerce", here)
        val ctx = context(
            listOf(
                commerce to listOf(
                    departure(line = "1", destination = "Beaujoire"),
                    departure(line = "1", destination = "François Mitterrand"),
                ),
            ),
        )

        assertEquals(2, GuetEngine.candidates(ctx).size)
    }

    // ------------------------------------------------ le passage qu'on accompagne

    /**
     * ⚠️ **Le défaut n° 2.** Pendant un accompagnement, un passage devenu hors
     * d'atteinte ne doit surtout pas disparaître : c'est exactement le moment où
     * l'écran doit dire « pressez le pas », puis « manqué ».
     */
    @Test
    fun `un passage devenu inatteignable se retrouve quand meme par sa cle`() {
        val commerce = stop("Commerce", here)
        val gone = departure(inSeconds = -120)
        val ctx = context(listOf(commerce to listOf(gone)))
        val key = PassageKey.make("Commerce", "1", "Beaujoire", gone.expectedAt)

        assertTrue(GuetEngine.candidates(ctx).isEmpty(), "il ne se propose plus")

        val followed = assertNotNull(GuetEngine.passage(key, ctx), "mais il se suit encore")
        assertEquals(GuetFeasibility.MISSED, followed.level.feasibility)
    }

    @Test
    fun `une cle inconnue ne rend rien`() {
        val ctx = context(listOf(stop("Commerce", here) to listOf(departure())))
        val stranger = PassageKey.make("Ailleurs", "9", "Nulle part", reference)

        assertNull(GuetEngine.passage(stranger, ctx))
    }

    // ------------------------------------------------------------- l'alerte

    /**
     * ⚠️ **Le défaut n° 3.** Un seuil de score sans condition de phase sonnerait
     * un quart d'heure trop tôt ; une condition de phase sans seuil sonnerait pour
     * n'importe quel bus.
     */
    @Test
    fun `on ne sonne pas avant l heure meme pour un excellent candidat`() {
        val commerce = stop("Commerce", here)
        val ctx = context(
            listOf(commerce to listOf(departure(inSeconds = 1500))),
            preferences = GuetPreferences(isEnabled = true, followedLines = setOf("1")),
        )

        val found = GuetEngine.candidates(ctx)

        assertEquals(GuetPhase.TOO_EARLY, found.single().level.phase)
        assertNull(GuetEngine.alertWorthy(found), "trop tôt, quel que soit le score")
    }

    @Test
    fun `on sonne quand c est l heure et que ca vaut la peine`() {
        // Le quai est à ~210 m, soit ~188 s de marche. Un véhicule dans 180 s
        // place donc l'heure de partir **juste derrière nous** : c'est l'instant
        // exact où le Guet doit sonner, et le seul qui compte.
        //
        // Un arrêt sous les pieds n'aurait pas marché : sans marche, `leaveAt`
        // vaut l'heure de passage, et l'alerte serait « trop tôt » jusqu'à ce que
        // le véhicule soit là.
        val commerce = stop("Commerce", nearby)
        val ctx = context(
            listOf(commerce to listOf(departure(inSeconds = 180))),
            preferences = GuetPreferences(
                isEnabled = true,
                platformMarginMinutes = 0,
                followedLines = setOf("1"),
            ),
        )

        val found = GuetEngine.candidates(ctx)
        assertTrue(found.isNotEmpty())

        val worthy = GuetEngine.alertWorthy(found)
        assertNotNull(worthy)
        assertTrue(
            worthy.level.phase == GuetPhase.LEAVE_NOW || worthy.level.phase == GuetPhase.PREPARE,
        )
    }

    // ---------------------------------------------------------- les alternatives

    /**
     * L'ordre **est** la règle, du moins dépaysant au plus. Quelqu'un qui vient de
     * rater son tram veut d'abord le suivant, pas un itinéraire différent.
     */
    @Test
    fun `le prochain passage de la meme ligne arrive en premier`() {
        val commerce = stop("Commerce", here)
        val bouffay = stop("Bouffay", nearby)
        val ctx = context(
            listOf(
                commerce to listOf(
                    departure(line = "1", destination = "Beaujoire", inSeconds = 600),
                    departure(line = "1", destination = "Beaujoire", inSeconds = 1200),
                    departure(line = "2", destination = "Beaujoire", inSeconds = 700),
                ),
                bouffay to listOf(
                    departure(line = "1", destination = "Beaujoire", inSeconds = 800),
                ),
            ),
        )
        val rejected = GuetEngine.candidates(ctx, deduplicated = false)
            .first { it.place == "Commerce" && it.line == "1" }

        val proposed = GuetEngine.alternatives(rejected, ctx)

        assertEquals("1", proposed[0].line)
        assertEquals("Commerce", proposed[0].place)
        assertEquals("2", proposed[1].line, "puis une autre ligne du même lieu")
        assertEquals("Bouffay", proposed[2].place, "puis la même ligne ailleurs")
    }

    @Test
    fun `une alternative deja refusee ne se propose pas`() {
        val commerce = stop("Commerce", here)
        val first = departure(line = "1", destination = "Beaujoire", inSeconds = 600)
        val second = departure(line = "1", destination = "Beaujoire", inSeconds = 1200)
        val secondKey = PassageKey.make("Commerce", "1", "Beaujoire", second.expectedAt)
        val ledger = GuetLedger().record(
            PassageStatus.Declined(reference),
            secondKey,
            "Commerce",
            "1",
            "Beaujoire",
            second.expectedAt,
            reference,
        )
        val ctx = context(listOf(commerce to listOf(first, second)), ledger = ledger)
        val rejected = GuetEngine.candidates(ctx, deduplicated = false)
            .first { it.timing.expectedAt == first.expectedAt }

        // Proposer ce qu'on vient de refuser est la façon la plus rapide de perdre
        // la confiance qu'une alerte demande.
        assertTrue(GuetEngine.alternatives(rejected, ctx).none { it.key == secondKey })
    }

    // ---------------------------------------------------------------- la marche

    /**
     * ⚠️ **La marche se recalcule depuis la position du moment.** Sinon elle ne
     * bougerait pas pendant qu'on marche : la marge ne descendrait que parce que
     * le véhicule approche, et partir dans la mauvaise direction ne changerait
     * rien à l'écran.
     */
    @Test
    fun `se rapprocher du quai raccourcit la marche`() {
        val quay = stop("Commerce", nearby)
        val far = context(listOf(quay to listOf(departure(inSeconds = 900))), position = here)
        val close = context(
            listOf(quay to listOf(departure(inSeconds = 900))),
            position = nearby,
        )

        val farWalk = GuetEngine.candidates(far).single().timing.walkSeconds
        val closeWalk = GuetEngine.candidates(close).single().timing.walkSeconds

        assertTrue(closeWalk < farWalk)
    }

    @Test
    fun `l allure declaree allonge ou raccourcit la marche`() {
        val quay = stop("Commerce", nearby)
        val slow = context(
            listOf(quay to listOf(departure(inSeconds = 900))),
            preferences = GuetPreferences(isEnabled = true, pace = WalkingPace.SLOW),
        )
        val fast = context(
            listOf(quay to listOf(departure(inSeconds = 900))),
            preferences = GuetPreferences(isEnabled = true, pace = WalkingPace.FAST),
        )

        assertTrue(
            GuetEngine.candidates(slow).single().timing.walkSeconds >
                GuetEngine.candidates(fast).single().timing.walkSeconds,
        )
    }

    // -------------------------------------------------------------- la direction

    @Test
    fun `sans trajet actif la direction ne penalise personne`() {
        val ctx = context(listOf(stop("Commerce", here) to listOf(departure())))

        // `null` fait peser le critère neutre, jamais zéro.
        assertEquals(
            GuetScoring.NEUTRAL,
            GuetEngine.candidates(ctx).single().score.criteria[GuetScoring.Criterion.DIRECTION],
        )
    }

    @Test
    fun `aller dans la bonne direction vaut mieux que l inverse`() {
        val commerce = stop("Commerce", here)
        val ctx = context(
            listOf(
                commerce to listOf(
                    departure(line = "1", destination = "Beaujoire"),
                    departure(line = "2", destination = "Orvault"),
                ),
            ),
            activeDestination = "Beaujoire",
        )

        assertEquals("1", GuetEngine.candidates(ctx).first().line)
    }

    // ------------------------------------------------------------- le plafond

    @Test
    fun `on ne classe pas plus de huit passages`() {
        val commerce = stop("Commerce", here)
        val many = (1..20).map { departure(line = "L$it", destination = "D$it", inSeconds = 600 + it * 10L) }

        assertEquals(GuetEngine.DEFAULT_LIMIT, GuetEngine.candidates(context(listOf(commerce to many))).size)
    }
}
