package io.aule.android.core.guet

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le registre, et ses trois invariants.
 *
 * 1. Un refus vaut pour ce passage, **jamais pour sa ligne**.
 * 2. **Ignorer n'est pas refuser.**
 * 3. Le registre est **borné**.
 *
 * Port de `GuetLedgerTests.swift`.
 */
class GuetLedgerTest {

    private val reference: Instant = Instant.ofEpochSecond(1_755_792_000)

    private fun GuetLedger.recording(
        status: PassageStatus,
        place: String = "Commerce",
        line: String = "1",
        destination: String = "Beaujoire",
        at: Instant,
        now: Instant = reference,
    ): GuetLedger {
        val key = resolve(place, line, destination, at)
        return record(status, key, place, line, destination, at, now)
    }

    // ------------------------------------------------------------- invariant 1

    /**
     * Refuser le T1 de 18:32 laisse arriver l'alerte du T1 de 18:38. Une clé par
     * ligne aurait été plus simple, et aurait éteint la ligne pour la soirée.
     */
    @Test
    fun `un refus vaut pour ce passage jamais pour sa ligne`() {
        val first = reference.plusSeconds(120)
        val second = reference.plusSeconds(480)

        val ledger = GuetLedger()
            .recording(PassageStatus.Declined(reference), at = first)

        assertTrue(ledger.isDeclined(ledger.resolve("Commerce", "1", "Beaujoire", first)))
        assertFalse(ledger.isDeclined(ledger.resolve("Commerce", "1", "Beaujoire", second)))
    }

    // ------------------------------------------------------------- invariant 2

    /**
     * Une alerte qu'on laisse s'éteindre reste `Alerted`. Elle ne devient
     * `Declined` que sur un geste — et la nuance décide si le passage suivant de la
     * même ligne mérite d'être annoncé.
     */
    @Test
    fun `ignorer n est pas refuser`() {
        val at = reference.plusSeconds(120)
        val ledger = GuetLedger().recording(PassageStatus.Alerted(reference), at = at)
        val key = ledger.resolve("Commerce", "1", "Beaujoire", at)

        assertTrue(ledger.hasAlerted(key))
        assertFalse(ledger.isDeclined(key))
    }

    // ------------------------------------------------------------- invariant 3

    @Test
    fun `le registre est borne et evince les moins recemment touchees`() {
        var ledger = GuetLedger()
        repeat(GuetLedger.CAPACITY + 50) { index ->
            ledger = ledger.recording(
                PassageStatus.Detected,
                line = "L$index",
                at = reference.plusSeconds(index.toLong()),
                now = reference.plusSeconds(index.toLong()),
            )
        }

        assertEquals(GuetLedger.CAPACITY, ledger.entries.size)
        // Les survivants sont les plus récemment touchés : les cinquante premiers
        // sont partis.
        assertTrue(ledger.entries.values.all { it.updatedAt >= reference.plusSeconds(50) })
    }

    @Test
    fun `l elagage oublie ce qui est trop vieux pour peser`() {
        val old = reference.minusSeconds(7200)
        val fresh = reference.plusSeconds(120)
        var ledger = GuetLedger()
            .recording(PassageStatus.Declined(reference), line = "1", at = old)
        ledger = ledger.recording(PassageStatus.Detected, line = "2", at = fresh)

        val pruned = ledger.prune(before = reference.minusSeconds(3600))

        assertEquals(1, pruned.entries.size)
        assertEquals("2", pruned.entries.values.single().line)
    }

    // ------------------------------------------------------ ne jamais rétrograder

    /**
     * ⚠️ Le moteur repère le même passage à chaque calcul. Sans cette garde, il
     * effacerait le refus qu'il vient de recevoir, et l'alerte refusée reviendrait
     * au tour suivant.
     */
    @Test
    fun `un detected ne repasse jamais par-dessus un declined`() {
        val at = reference.plusSeconds(120)
        var ledger = GuetLedger().recording(PassageStatus.Declined(reference), at = at)
        ledger = ledger.recording(PassageStatus.Detected, at = at)

        val key = ledger.resolve("Commerce", "1", "Beaujoire", at)
        assertTrue(ledger.isDeclined(key))
    }

    @Test
    fun `l heure de passage est mise a jour meme quand l etat ne bouge pas`() {
        val at = reference.plusSeconds(120)
        val drifted = at.plusSeconds(60)
        var ledger = GuetLedger().recording(PassageStatus.Declined(reference), at = at)
        ledger = ledger.recording(PassageStatus.Detected, at = drifted)

        // C'est l'heure qui dérive, et la garder figée ferait échouer le
        // réappariement suivant.
        assertEquals(drifted, ledger.entries.values.single().expectedAt)
    }

    // ----------------------------------------------------------- réappariement

    /**
     * ⚠️ **Le point d'entrée du moteur.** Sans réappariement, une clé neuve
     * naîtrait à chaque rafraîchissement temps réel, et un refus ne survivrait pas
     * à trente secondes.
     */
    @Test
    fun `une heure qui derive retrouve la cle du meme passage`() {
        val at = reference.plusSeconds(600)
        val ledger = GuetLedger().recording(PassageStatus.Declined(reference), at = at)

        val drifted = ledger.resolve("Commerce", "1", "Beaujoire", at.plusSeconds(90))

        assertTrue(ledger.isDeclined(drifted))
    }

    @Test
    fun `au-dela de la tolerance c est un autre passage`() {
        val at = reference.plusSeconds(600)
        val ledger = GuetLedger().recording(PassageStatus.Declined(reference), at = at)

        val far = ledger.resolve(
            "Commerce",
            "1",
            "Beaujoire",
            at.plusSeconds(GuetLedger.REMATCH_TOLERANCE.toLong() + 1),
        )

        assertFalse(ledger.isDeclined(far))
    }

    @Test
    fun `une autre ligne ou une autre destination ne se reapparie pas`() {
        val at = reference.plusSeconds(600)
        val ledger = GuetLedger().recording(PassageStatus.Declined(reference), at = at)

        assertFalse(ledger.isDeclined(ledger.resolve("Commerce", "2", "Beaujoire", at.plusSeconds(30))))
        assertFalse(ledger.isDeclined(ledger.resolve("Commerce", "1", "Orvault", at.plusSeconds(30))))
        assertFalse(ledger.isDeclined(ledger.resolve("Bretagne", "1", "Beaujoire", at.plusSeconds(30))))
    }

    /**
     * Deux passages de la même ligne vers la même destination à moins de six
     * minutes d'écart existent, et les confondre serait pire que de n'en
     * réapparier aucun.
     */
    @Test
    fun `en cas d egalite la candidate la plus proche l emporte`() {
        // Les deux entrées sont à 300 s l'une de l'autre — **au-delà** de la
        // tolérance, donc elles ne se réapparient pas entre elles et coexistent
        // bien au registre. La sonde, elle, tombe à portée des deux.
        val early = reference.plusSeconds(600)
        val late = reference.plusSeconds(900)
        var ledger = GuetLedger().recording(PassageStatus.Declined(reference), at = early)
        ledger = ledger.recording(PassageStatus.Alerted(reference), at = late)
        assertEquals(2, ledger.entries.size, "deux passages distincts au registre")

        // 760 s est à 160 s d'`early` et à 140 s de `late` : c'est `late` qui gagne.
        val resolved = ledger.resolve("Commerce", "1", "Beaujoire", reference.plusSeconds(760))

        assertTrue(ledger.hasAlerted(resolved))
        assertFalse(ledger.isDeclined(resolved))
    }

    /**
     * Les deux bouts de l'API n'écrivent pas « Chantrerie - Grandes Écoles »
     * pareil : la clé normalise, sinon un refus ne porterait que sur
     * l'orthographe qui l'a produit.
     */
    @Test
    fun `l orthographe du lieu ne change pas l identite`() {
        val at = reference.plusSeconds(600)
        val ledger = GuetLedger()
            .recording(PassageStatus.Declined(reference), place = "Chantrerie - Grandes Écoles", at = at)

        val other = ledger.resolve("chantrerie   grandes ecoles", "1", "Beaujoire", at)

        assertTrue(ledger.isDeclined(other))
    }

    // ------------------------------------------------------------------- la clé

    @Test
    fun `la cle est exacte a la seconde`() {
        // ⚠️ Quantifier par tranches d'une minute ferait tomber 18:31:59 et
        // 18:32:01 dans deux tranches différentes. Le bord d'une tranche est un
        // défaut qu'on ne voit qu'une fois sur soixante.
        val a = PassageKey.make("Commerce", "1", "Beaujoire", reference)
        val b = PassageKey.make("Commerce", "1", "Beaujoire", reference.plusSeconds(1))

        assertNotEquals(a, b)
        assertEquals(a, PassageKey.make("Commerce", "1", "Beaujoire", reference))
    }

    @Test
    fun `une cle se relit et une chaine quelconque ne s en fait pas passer pour une`() {
        val key = PassageKey.make("Commerce", "1", "Beaujoire", reference)

        assertEquals(key, PassageKey.parse(key.raw))
        assertNull(PassageKey.parse("n'importe quoi"))
        assertNull(PassageKey.parse("commerce|1|beaujoire"))
        assertNull(PassageKey.parse("commerce|1|beaujoire|pas-un-nombre"))
        assertNull(PassageKey.parse("commerce||beaujoire|123"))
    }
}
