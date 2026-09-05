package io.aule.android.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Une note de service : ce que l'exploitant a décidé, et depuis quand.
 *
 * ## Ce qu'elle n'est pas
 *
 * Ni une perturbation, ni une alerte. Une perturbation **arrive** — un retard, une
 * panne —, elle naît et meurt dans la journée, et le voyageur la lit. Une note de
 * service est un **document interne** : elle porte une référence (« 26/639 »), une
 * date de prise d'effet, une signature, et elle reste vraie des semaines. C'est la
 * RLS de `line_service_notes` qui borne qui la lit, pas cette classe.
 *
 * ## Les trois dates, et pourquoi aucune ne suffit seule
 *
 * [issuedOn] est le jour où la note est écrite ; elle ne dit rien de ce qui
 * s'applique — la note du 26 août annonce le 31. [effectiveOn] est le jour où la
 * consigne prend effet. [displayUntil] est la fin de l'**affichage**, pas de la
 * consigne : ce qu'une note a changé ne se dé-change pas. Voir [Status.SETTLED].
 *
 * Les jours sont des [LocalDate] et non des [Instant] : ce sont des *jours de
 * service* du réseau nantais, pas des instants. Un instant les ferait basculer
 * d'un jour dès qu'on change de fuseau — et `startOfDay` en UTC recule d'une
 * journée toute la nuit d'été.
 */
data class ServiceNote(
    val id: String,
    /** La référence de l'exploitant — « 26/639 ». Affichée telle quelle. */
    val reference: String,
    val issuer: String?,
    val signatory: String?,
    val title: String,
    val summary: String?,
    /** Le corps, tel que l'exploitant l'a écrit. Voir [blocks]. */
    val body: String,
    /**
     * Les indices publics concernés.
     *
     * ⚠️ **Vide veut dire « tout le réseau »**, et non « aucune ligne » — voir
     * [concerns]. Un appelant qui filtrerait sur l'appartenance ferait disparaître
     * les notes générales de tous les écrans à la fois.
     */
    val lines: List<String>,
    val kind: Kind,
    /**
     * La case « Graphiqué » / « Non graphiqué » de l'en-tête : l'horaire a-t-il été
     * refait pour l'occasion. `null` quand la note ne le dit pas — ce n'est pas
     * « non graphiqué ».
     */
    val isScheduled: Boolean?,
    val issuedOn: LocalDate,
    val effectiveOn: LocalDate,
    val displayUntil: LocalDate?,
) {
    enum class Kind {
        INFO_TRAFIC,
        CONSIGNE,
        FORMATION,
        SECURITE,
        ;

        companion object {
            /**
             * Une valeur inconnue retombe sur `INFO_TRAFIC` : l'exploitant peut
             * ajouter un type demain, et une note illisible vaut moins qu'une note
             * mal rangée.
             */
            fun parse(raw: String?): Kind = when (raw?.lowercase(Locale.ROOT)) {
                "consigne" -> CONSIGNE
                "formation" -> FORMATION
                "securite", "sécurité" -> SECURITE
                else -> INFO_TRAFIC
            }
        }
    }

    /** Où en est la note, le jour donné. */
    enum class Status {
        /** Écrite, pas encore en vigueur. */
        UPCOMING,

        /** S'applique, et s'affiche. */
        ACTIVE,

        /**
         * S'applique **toujours** ; sa date d'affichage est passée. « Acquise », et
         * non « expirée » : l'appeler expirée ferait croire à un retour en arrière.
         */
        SETTLED,
    }

    fun statusOn(day: LocalDate): Status = when {
        effectiveOn.isAfter(day) -> Status.UPCOMING
        displayUntil?.isBefore(day) == true -> Status.SETTLED
        else -> Status.ACTIVE
    }

    fun statusAt(instant: Instant): Status = statusOn(serviceDay(instant))

    /** Vrai quand la note vaut pour cette ligne — **ou pour tout le réseau**. */
    fun concerns(line: String): Boolean {
        if (lines.isEmpty()) return true
        val wanted = canonicalLineName(line)
        return lines.any { canonicalLineName(it) == wanted }
    }

    /** Un morceau du corps, prêt à être posé dans une colonne. */
    sealed interface Block {
        data class Heading(val text: String) : Block

        data class Bullets(val items: List<String>) : Block

        data class Paragraph(val text: String) : Block
    }

    /**
     * Le corps découpé.
     *
     * ## Pourquoi ni Markdown, ni HTML
     *
     * Une note est du texte saisi par un humain dans un outil d'administration. Un
     * moteur Markdown y ferait disparaître les `*` d'un futur « 2*30 m » et
     * changerait un texte réglementaire sans que personne ne le voie.
     *
     * Le découpage est donc **structurel et minimal**, et c'est le même sur les
     * trois clients — voir `ServiceNote.swift` et `dashboard/lib/service-notes.ts`.
     * Sans quoi la même note se lirait différemment selon l'écran, ce qu'une
     * consigne ne peut pas se permettre.
     *
     * ⚠️ **Une ligne seule n'est un titre que si un texte la suit.** La signature
     * ferme la note — une ligne isolée que rien ne suit — et deviendrait autrement
     * un intertitre en gras, sans rien dessous.
     */
    val blocks: List<Block> by lazy { parseBody(body) }

    companion object {
        /**
         * Le fuseau des jours de service.
         *
         * ⚠️ **Europe/Paris, et non celui de l'appareil.** C'est le pendant exact du
         * `timeZone: "Europe/Paris"` du BFF : le serveur choisit les notes qu'il
         * envoie, le client choisit celles qu'il met en avant, et les deux doivent
         * tomber d'accord.
         */
        val ZONE: ZoneId = ZoneId.of("Europe/Paris")

        fun serviceDay(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()

        /**
         * Un jour de service — « 2026-08-31 » — tel qu'il arrive du serveur.
         *
         * `null` sur toute autre forme : une date illisible ne devient pas
         * aujourd'hui, ce qui afficherait une note comme prenant effet le jour où on
         * la lit.
         */
        fun parseDay(text: String?): LocalDate? {
            if (text == null || text.length != 10) return null
            return runCatching { LocalDate.parse(text) }.getOrNull()
        }

        /** « lundi 31 août 2026 ». */
        fun formatDay(day: LocalDate, locale: Locale = Locale.FRANCE): String =
            day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

        /**
         * Les marqueurs de puce que l'exploitant emploie.
         *
         * Le tiret exige l'espace qui le suit : sans lui, « Tram-Bus » en début de
         * ligne passerait pour une puce et perdrait son premier mot.
         */
        private val BULLETS = listOf("• ", "- ", "– ")

        internal fun parseBody(body: String): List<Block> {
            val lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n")
            val blocks = mutableListOf<Block>()
            val paragraph = mutableListOf<String>()
            val bullets = mutableListOf<String>()

            fun flushParagraph() {
                if (paragraph.isNotEmpty()) {
                    blocks += Block.Paragraph(paragraph.joinToString(" "))
                    paragraph.clear()
                }
            }
            fun flushBullets() {
                if (bullets.isNotEmpty()) {
                    blocks += Block.Bullets(bullets.toList())
                    bullets.clear()
                }
            }

            lines.forEachIndexed { index, raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() -> {
                        flushBullets()
                        flushParagraph()
                    }

                    BULLETS.any { line.startsWith(it) } -> {
                        flushParagraph()
                        val marker = BULLETS.first { line.startsWith(it) }
                        bullets += line.removePrefix(marker).trim()
                    }

                    else -> {
                        flushBullets()
                        val previous = if (index > 0) lines[index - 1].trim() else ""
                        val next = if (index + 1 < lines.size) lines[index + 1].trim() else ""
                        val isHeading = paragraph.isEmpty() &&
                            previous.isEmpty() &&
                            next.isNotEmpty() &&
                            // ⚠️ Une ligne que suit une minuscule **continue une
                            // phrase**, elle ne la titre pas : c'est un paragraphe
                            // replié sur la largeur du document source. Sans cette
                            // condition, « La ligne 1 retrouve / son exploitation
                            // nominale. » commencerait par un intertitre en gras.
                            !next.first().isLowerCase() &&
                            !line.endsWith(".") &&
                            line.length <= 80
                        if (isHeading) blocks += Block.Heading(line) else paragraph += line
                    }
                }
            }

            flushBullets()
            flushParagraph()
            return blocks
        }
    }
}
