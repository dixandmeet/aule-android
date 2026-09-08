package io.aule.android.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Port de `StopScheduleTests` dans `Native/AuleTests/`. */
class StopScheduleTest {

    private val lundi: LocalDate = LocalDate.of(2026, 9, 7)

    /** L'instant parisien de ce jour-là, à l'heure murale dite. */
    private fun instant(heure: Int, minute: Int = 0, jour: LocalDate = lundi): Instant =
        LocalDateTime.of(jour, java.time.LocalTime.of(heure, minute))
            .atZone(ServiceDay.ZONE)
            .toInstant()

    private fun grille(
        vararg secondes: Int,
        date: LocalDate = lundi,
        outcome: DayScheduleOutcome = DayScheduleOutcome.PUBLISHED,
    ) = StopDaySchedule(
        serviceDate = date,
        line = "1",
        direction = "Beaujoire",
        lineColor = "#00a754",
        times = secondes.map { ScheduledPassage(seconds = it) },
        outcome = outcome,
    )

    // MARK: - L'heure d'un passage

    /**
     * 25 h 40 est une heure valide, et elle ne veut pas dire 1 h 40 du même matin.
     *
     * C'est tout l'intérêt de compter en secondes : ramené à l'heure murale, ce
     * passage se rangerait en tête de grille, avant le premier tram du matin, et
     * le lecteur croirait avoir le temps de le prendre le soir même.
     */
    @Test
    fun `un passage après minuit garde son rang de fin de journée`() {
        val dernier = ScheduledPassage(seconds = 25 * 3_600 + 40 * 60)

        assertEquals(1, dernier.hour)
        assertEquals(40, dernier.minute)
        assertEquals(1, dernier.dayOffset)
    }

    /**
     * Deux courses peuvent partir à la même seconde — le serveur ne dédoublonne
     * pas les profils. Sans la course dans l'identité, la liste sauterait sous le
     * doigt.
     */
    @Test
    fun `deux départs à la même minute ont deux identités`() {
        val a = ScheduledPassage(seconds = 20_280, departureId = "D002760")
        val b = ScheduledPassage(seconds = 20_280, departureId = "D004411")

        assertTrue(a.id != b.id)
    }

    // MARK: - La grille par heures

    /** Le « 00 h » du lendemain matin ne se range pas avec un « 00 h » de tête de grille. */
    @Test
    fun `les heures se regroupent sans confondre les deux minuits`() {
        val rows = grille(
            5 * 3_600 + 38 * 60, // 05:38
            5 * 3_600 + 52 * 60, // 05:52
            6 * 3_600, // 06:00
            24 * 3_600 + 10 * 60, // 00:10 le lendemain
        ).hourRows

        assertEquals(listOf("0-5", "0-6", "1-0"), rows.map { it.id })
        assertEquals(2, rows.first().times.size)
        assertEquals(0, rows.last().hour)
        assertEquals(1, rows.last().dayOffset)
    }

    // MARK: - Ce qui reste à venir

    /**
     * ⚠️ **Le cœur du calcul.** À 0 h 30, on lit la grille de la veille : le tram
     * de « 24 h 50 » y est encore à venir. Comparé en heure murale — 0 h 50
     * contre 0 h 30 — le résultat serait juste par accident ; comparé au mauvais
     * minuit, il annoncerait une journée finie depuis longtemps.
     */
    @Test
    fun `le prochain départ se compte depuis le minuit du jour de service`() {
        val nuit = grille(24 * 3_600 + 50 * 60) // 00:50, le lendemain du lundi
        val apresMinuit = instant(0, 30, jour = lundi.plusDays(1))

        assertEquals(0, nuit.nextIndex(apresMinuit))
        assertFalse(nuit.isOver(apresMinuit))
    }

    @Test
    fun `une journée dont tout est passé est terminée`() {
        val jour = grille(5 * 3_600, 6 * 3_600)

        assertNull(jour.nextIndex(instant(23)))
        assertTrue(jour.isOver(instant(23)))
    }

    /**
     * Une journée sans service n'est pas une journée finie : elle n'a jamais rien
     * promis, et lui proposer une « reprise » n'aurait aucun sens.
     */
    @Test
    fun `une journée sans départ n'est pas une journée terminée`() {
        assertFalse(grille().isOver(instant(23)))
    }

    // MARK: - Pourquoi c'est vide

    @Test
    fun `une grille publiée et vide dit une absence de service`() {
        assertEquals(
            EmptyDayReason.NO_SERVICE,
            grille().emptyReason(liveContradicts = false),
        )
    }

    @Test
    fun `un référentiel muet ne dit rien du service`() {
        assertEquals(
            EmptyDayReason.UNKNOWN_DESSERTE,
            StopDaySchedule.unknown("1", "Beaujoire", lundi).emptyReason(liveContradicts = false),
        )
    }

    /**
     * Le cas mesuré du 01/09/2026 : la grille publiée s'arrête, la route répond
     * 200 avec zéro départ, et les trams passent quand même. On ne dit pas
     * pourquoi — on dit qu'on n'a pas l'horaire.
     */
    @Test
    fun `une grille muette là où le direct parle ne dit pas une absence de service`() {
        assertEquals(
            EmptyDayReason.GRID_SILENT,
            grille().emptyReason(liveContradicts = true),
        )
    }

    // MARK: - Le tamis des passages

    private fun passage(
        line: String,
        destination: String,
        direction: String? = null,
    ) = StopDeparture(
        id = "$line-$destination",
        line = line,
        destination = destination,
        expectedAt = Instant.EPOCH,
        isRealtime = true,
        directionLabel = direction,
    )

    /**
     * Mesuré le 18/08/2026 : la ligne 1 annonce `direction: "Beaujoire / Babinière"`
     * et `destination: "Babinière"`, quand le GTFS ne connaît que le premier. Les
     * deux libellés doivent être essayés, sinon la moitié des dessertes n'ouvre
     * aucun passage en direct.
     */
    @Test
    fun `le sens du GTFS reconnaît la girouette du poteau`() {
        val cible = ScheduleTarget(line = "1", direction = "Beaujoire / Babinière")

        assertTrue(cible.sieve.serves(passage("1", "Babinière")))
        assertTrue(cible.sieve.serves(passage("1", "Beaujoire / Babinière")))
    }

    /** Et l'inverse : un rang de passages annonce la girouette, la grille le sens. */
    @Test
    fun `la girouette du poteau reconnaît le sens du GTFS`() {
        val cible = ScheduleTarget(line = "1", direction = "Babinière")

        assertTrue(cible.sieve.serves(passage("1", "Beaujoire / Babinière")))
    }

    /** L'inclusion porte sur des **mots entiers** : sinon « Beau » serait un sens. */
    @Test
    fun `un mot tronqué n'est pas un sens`() {
        val cible = ScheduleTarget(line = "1", direction = "Beau")

        assertFalse(cible.sieve.serves(passage("1", "Beaujoire")))
    }

    @Test
    fun `une autre ligne n'est jamais servie`() {
        val cible = ScheduleTarget(line = "1", direction = "Beaujoire")

        assertFalse(cible.sieve.serves(passage("2", "Beaujoire")))
    }

    /** Accents et ponctuation ne comptent pas : les deux bouts de l'API ne s'accordent pas dessus. */
    @Test
    fun `les accents et la ponctuation ne départagent pas`() {
        val cible = ScheduleTarget(line = "C3", direction = "Chantrerie - Grandes Écoles")

        assertTrue(cible.sieve.serves(passage("c3", "Chantrerie Grandes Ecoles")))
    }

    @Test
    fun `les passages d'une desserte sortent dans l'ordre`() {
        val cible = ScheduleTarget(line = "1", direction = "Beaujoire")
        val tableau = StopDepartures(
            stopName = "Commerce",
            departures = listOf(
                passage("1", "Beaujoire").copy(id = "tard", expectedAt = Instant.EPOCH.plusSeconds(600)),
                passage("2", "Orvault"),
                passage("1", "Beaujoire").copy(id = "tôt", expectedAt = Instant.EPOCH.plusSeconds(60)),
            ),
            outcome = DeparturesOutcome.ANNOUNCED,
            fetchedAt = Instant.EPOCH,
        )

        assertEquals(listOf("tôt", "tard"), tableau.matching(cible).map { it.id })
    }

    // MARK: - Les lignes desservies

    /**
     * Le serveur rend une entrée par sens. Rendues telles quelles, quatre lignes
     * en occupent sept et chaque badge paraît deux fois.
     */
    @Test
    fun `les sens se regroupent sous leur ligne`() {
        val groupes = listOf(
            ServingLine(line = "1", direction = "Beaujoire", lineColor = "#00a754"),
            ServingLine(line = "1", direction = "François Mitterrand"),
            ServingLine(line = "C3", direction = "Hôtel Dieu"),
        ).groupedByLine()

        assertEquals(listOf("1", "C3"), groupes.map { it.line })
        assertEquals(listOf("Beaujoire", "François Mitterrand"), groupes.first().directions)
        // La couleur d'une entrée profite au groupe : un badge gris pour une ligne
        // qui a la sienne se remarque tout de suite.
        assertEquals("#00a754", groupes.first().lineColor)
    }

    /**
     * L'ordre est celui du serveur — mode puis indice. Retrier sur la chaîne
     * ferait passer « 10 » avant « 2 », un ordre qui n'est celui de rien.
     */
    @Test
    fun `l'ordre du serveur est conservé`() {
        val groupes = listOf(
            ServingLine(line = "2", direction = "Orvault"),
            ServingLine(line = "10", direction = "Hermeland"),
        ).groupedByLine()

        assertEquals(listOf("2", "10"), groupes.map { it.line })
    }

    // MARK: - L'arithmétique des jours

    /**
     * ⚠️ **La nuit du changement d'heure compte 23 heures.** Un décalage en
     * 86 400 secondes fixes y placerait le dernier tram une heure à côté — le
     * calcul doit passer par le fuseau.
     */
    @Test
    fun `le minuit de service suit le fuseau, pas une durée fixe`() {
        // L'heure d'hiver arrive le dimanche 25/10/2026 à 3 h du matin : c'est donc
        // la journée de service du 25 qui compte 25 heures, pas celle de la veille.
        val jourLong = LocalDate.of(2026, 10, 25)
        val minuitSuivant = LocalDate.of(2026, 10, 26).atStartOfDay(ServiceDay.ZONE).toInstant()

        assertEquals(25 * 3_600L, ServiceDay.elapsedSeconds(jourLong, minuitSuivant))
        // Le contrôle négatif : la veille, elle, est une journée ordinaire — sans
        // quoi ce test passerait avec une arithmétique en 86 400 secondes fixes.
        assertEquals(
            24 * 3_600L,
            ServiceDay.elapsedSeconds(LocalDate.of(2026, 10, 24), jourLong.atStartOfDay(ServiceDay.ZONE).toInstant()),
        )
    }

    @Test
    fun `le jour du réseau est parisien`() {
        // 23 h 30 UTC un 7 septembre : il est déjà le 8 à Paris.
        val tard = Instant.parse("2026-09-07T23:30:00Z")

        assertEquals(LocalDate.of(2026, 9, 8), ServiceDay.today(tard))
    }
}
