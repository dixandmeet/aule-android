package io.aule.android.core.model

import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les notes de service, par ce qui se juge sans réseau.
 *
 * Le découpage du corps, l'état d'une note et le filtre de ligne — c'est-à-dire
 * tout ce qui décide de ce qu'un conducteur lit avant de partir. Les mêmes cas que
 * `Native/AuleTests/ServiceNoteTests.swift` : la règle est commune aux trois
 * clients, et deux implémentations qui divergeraient feraient lire la même note
 * différemment selon l'écran.
 */
class ServiceNoteTest {

    private fun note(
        body: String = "",
        lines: List<String> = listOf("1"),
        effective: String = "2026-08-31",
        until: String? = "2026-09-30",
    ) = ServiceNote(
        id = "n1",
        reference = "26/639",
        issuer = null,
        signatory = null,
        title = "Ligne 1 — fin des travaux d'été",
        summary = null,
        body = body,
        lines = lines,
        kind = ServiceNote.Kind.INFO_TRAFIC,
        isScheduled = true,
        issuedOn = LocalDate.parse("2026-08-26"),
        effectiveOn = LocalDate.parse(effective),
        displayUntil = until?.let(LocalDate::parse),
    )

    // ----------------------------------------------------------------- le corps

    @Test
    fun `un titre de section se distingue d'un paragraphe et d'une puce`() {
        val blocks = note(
            body = """
                Secteur Quai de la Fosse
                Les 2 quais sont implantés de part et d'autre.

                • Première puce.
                • Seconde puce.
            """.trimIndent(),
        ).blocks

        assertEquals(
            listOf(
                ServiceNote.Block.Heading("Secteur Quai de la Fosse"),
                ServiceNote.Block.Paragraph("Les 2 quais sont implantés de part et d'autre."),
                ServiceNote.Block.Bullets(listOf("Première puce.", "Seconde puce.")),
            ),
            blocks,
        )
    }

    /**
     * La signature ferme la note : une ligne isolée que rien ne suit. En faire un
     * titre afficherait un intertitre en gras, sans rien dessous.
     */
    @Test
    fun `une ligne seule en fin de note n'est pas un titre`() {
        val blocks = note(
            body = """
                Terminus François Mitterrand
                Les 2 quais de montée sont exploitables.

                Antony ARDOUIN
            """.trimIndent(),
        ).blocks

        assertEquals(ServiceNote.Block.Paragraph("Antony ARDOUIN"), blocks.last())
    }

    /**
     * Sans l'espace exigé après le tiret, un mot composé en début de ligne —
     * « Tram-Bus », qui est dans cette note-ci — passerait pour une puce et
     * perdrait son premier mot.
     */
    @Test
    fun `un mot compose en debut de ligne n'est pas une puce`() {
        val blocks = note(
            body = """
                Secteur Quai de la Fosse
                Tram-Bus : les VUT sont interdites.
            """.trimIndent(),
        ).blocks

        assertEquals(
            listOf(
                ServiceNote.Block.Heading("Secteur Quai de la Fosse"),
                ServiceNote.Block.Paragraph("Tram-Bus : les VUT sont interdites."),
            ),
            blocks,
        )
    }

    @Test
    fun `les lignes d'un meme paragraphe se recollent`() {
        val blocks = note(body = "La ligne 1 retrouve\nson exploitation nominale.").blocks

        assertEquals(
            listOf(ServiceNote.Block.Paragraph("La ligne 1 retrouve son exploitation nominale.")),
            blocks,
        )
    }

    // ------------------------------------------------------------------- l'état

    @Test
    fun `une note affichee hier reste acquise, et non expiree`() {
        val subject = note()

        assertEquals(ServiceNote.Status.UPCOMING, subject.statusOn(LocalDate.parse("2026-08-30")))
        assertEquals(ServiceNote.Status.ACTIVE, subject.statusOn(LocalDate.parse("2026-08-31")))
        assertEquals(ServiceNote.Status.ACTIVE, subject.statusOn(LocalDate.parse("2026-09-30")))
        assertEquals(ServiceNote.Status.SETTLED, subject.statusOn(LocalDate.parse("2026-10-01")))
    }

    @Test
    fun `une note sans terme d'affichage ne devient jamais acquise`() {
        assertEquals(
            ServiceNote.Status.ACTIVE,
            note(until = null).statusOn(LocalDate.parse("2027-01-01")),
        )
    }

    /**
     * Le jour de service se compte à Paris. Le 31 août 2026 à 4 h 30 heure de Paris
     * est encore le 30 en UTC : lue dans ce fuseau, la note du 31 serait « à venir »
     * exactement au moment de la prise de service qu'elle concerne.
     */
    @Test
    fun `une prise de service a l'aube lit deja la note du jour`() {
        val aube = Instant.parse("2026-08-31T02:30:00Z")

        assertEquals(ServiceNote.Status.ACTIVE, note().statusAt(aube))
    }

    @Test
    fun `une date illisible ne devient pas aujourd'hui`() {
        assertNull(ServiceNote.parseDay("31/08/2026"))
        assertNull(ServiceNote.parseDay("2026-8-31"))
        assertNull(ServiceNote.parseDay("2026-08-31T00:00:00Z"))
        assertNull(ServiceNote.parseDay(null))
        assertEquals(LocalDate.parse("2026-08-31"), ServiceNote.parseDay("2026-08-31"))
    }

    // ---------------------------------------------------------- le filtre ligne

    /**
     * Une note sans ligne est une note de réseau. La ranger sous « aucune ligne »
     * la ferait disparaître de tous les écrans à la fois, sans que rien ne le
     * signale.
     */
    @Test
    fun `une note sans ligne vaut pour toutes`() {
        assertTrue(note(lines = emptyList()).concerns("C3"))
        assertTrue(note(lines = emptyList()).concerns("1"))
    }

    @Test
    fun `le filtre suit la forme canonique de l'indice`() {
        val subject = note(lines = listOf("c3"))

        assertTrue(subject.concerns("C3"))
        assertFalse(subject.concerns("3"))
    }

    @Test
    fun `un type de note inconnu retombe sur info trafic`() {
        assertEquals(ServiceNote.Kind.CONSIGNE, ServiceNote.Kind.parse("consigne"))
        assertEquals(ServiceNote.Kind.INFO_TRAFIC, ServiceNote.Kind.parse("exploitation"))
        assertEquals(ServiceNote.Kind.INFO_TRAFIC, ServiceNote.Kind.parse(null))
    }
}
