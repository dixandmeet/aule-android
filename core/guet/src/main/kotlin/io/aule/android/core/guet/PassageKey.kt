package io.aule.android.core.guet

import io.aule.android.core.model.StopDeparture
import io.aule.android.core.model.normalizeStopName
import java.time.Instant

/**
 * L'identité d'un passage annoncé.
 *
 * ## Pourquoi un type opaque
 *
 * Le registre, les notifications programmées et les raccourcis ont tous besoin de
 * désigner « ce tram-là ». Le fournisseur temps réel ne servant pas
 * d'identifiant de course, cette désignation est une **concaténation** — et une
 * concaténation recopiée à quatre endroits diverge au premier changement, sans
 * que rien ne le signale : un refus cesserait simplement de porter.
 *
 * D'où la forme : **un seul constructeur**, et la chaîne cachée derrière. Le
 * jour où le BFF sert un identifiant de course, le corps de [make] change et
 * rien d'autre dans le projet ne bouge.
 *
 * ## La clé est exacte, à la seconde
 *
 * Le premier jet iOS prévoyait de la quantifier par tranches d'une minute, pour
 * absorber la dérive du temps réel. C'est un piège : deux passages annoncés à
 * 18:31:59 et 18:32:01 tombent alors dans deux tranches différentes, et
 * l'anti-répétition les prend pour deux véhicules. Le bord d'une tranche est un
 * défaut qu'on ne voit qu'une fois sur soixante.
 *
 * La dérive est donc absorbée ailleurs, là où elle se teste : [GuetLedger]
 * réapparie à ±180 s sur le même lieu, la même ligne et la même destination.
 *
 * Port de `Native/Aule/Core/Guet/PassageKey.swift`.
 */
@JvmInline
value class PassageKey private constructor(
    /**
     * Lisible dans un journal et dans un échec de test. C'est la seule sortie de
     * la chaîne, et le constructeur privé garantit que rien ne la reconstruit à
     * la main ailleurs.
     */
    val raw: String,
) {
    override fun toString(): String = raw

    companion object {
        /**
         * **LE** constructeur.
         *
         * @param place le nom de lieu, tel que l'API des passages le connaît —
         *   `TransitStop.departuresKey`. Normalisé ici, parce que les deux bouts
         *   de l'API n'écrivent pas « Chantrerie - Grandes Écoles » pareil.
         */
        fun make(
            place: String,
            line: String,
            destination: String,
            expectedAt: Instant,
        ): PassageKey = PassageKey(
            listOf(
                normalizeStopName(place),
                normalizeStopName(line),
                normalizeStopName(destination),
                expectedAt.epochSecond.toString(),
            ).joinToString(SEPARATOR),
        )

        /**
         * Le même, depuis un passage. **Transmet, ne construit pas** : la règle
         * d'identité reste dans une seule fonction.
         */
        fun make(place: String, departure: StopDeparture): PassageKey = make(
            place = place,
            line = departure.line,
            destination = departure.destination,
            expectedAt = departure.expectedAt,
        )

        /**
         * Relit une clé écrite par [make] — pour un registre persisté, et rien
         * d'autre.
         *
         * ⚠️ **Ce n'est pas un second constructeur.** Il ne fabrique pas une
         * identité, il en relit une déjà fabriquée : donner une chaîne
         * quelconque produirait une clé qui n'apparie rien, ce qui est
         * exactement le défaut que le constructeur unique évite. `null` sur une
         * chaîne qui n'a pas la forme attendue.
         */
        fun parse(raw: String): PassageKey? {
            val parts = raw.split(SEPARATOR)
            if (parts.size != 4) return null
            if (parts.any { it.isEmpty() }) return null
            if (parts[3].toLongOrNull() == null) return null
            return PassageKey(raw)
        }

        /**
         * La barre verticale : elle ne peut pas apparaître dans un nom normalisé,
         * qui ne garde que lettres, chiffres et espaces.
         */
        private const val SEPARATOR = "|"
    }
}
