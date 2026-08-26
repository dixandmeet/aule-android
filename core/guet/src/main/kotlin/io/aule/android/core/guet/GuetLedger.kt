package io.aule.android.core.guet

import io.aule.android.core.model.normalizeStopName
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Ce qu'il est advenu d'un passage.
 *
 * L'ordre des cas **est** l'ordre dans lequel un état peut en remplacer un autre
 * ([rank]). Sans lui, un second passage du moteur réécrirait `DETECTED` par-dessus
 * un `DECLINED` et l'alerte refusée reviendrait au tour suivant — le défaut le
 * plus agaçant qu'une veille puisse avoir, et le plus facile à écrire par accident.
 */
sealed interface PassageStatus {
    /** Le rang : un état ne se laisse remplacer que par un rang supérieur ou égal. */
    val rank: Int

    /** Repéré par le moteur, jamais annoncé. */
    data object Detected : PassageStatus {
        override val rank: Int get() = 0
    }

    /**
     * L'alerte a été montrée. **Reste dans cet état si l'utilisateur ne fait
     * rien** — voir l'invariant 2 du registre.
     */
    data class Alerted(val at: Instant) : PassageStatus {
        override val rank: Int get() = 1
    }

    /** L'heure est passée depuis assez longtemps pour que la question ne se pose plus. */
    data object Expired : PassageStatus {
        override val rank: Int get() = 2
    }

    /** L'utilisateur a touché « Pas celui-ci ». **Le seul état qui fait taire.** */
    data class Declined(val at: Instant) : PassageStatus {
        override val rank: Int get() = 3
    }

    data class Accepted(val at: Instant) : PassageStatus {
        override val rank: Int get() = 4
    }
}

/**
 * Ce que la veille a déjà fait, et ce qu'elle n'a donc plus à refaire.
 *
 * Trois invariants, un test chacun :
 *
 * 1. **Un refus vaut pour ce passage, jamais pour sa ligne.** Refuser le T1 de
 *    18:32 laisse arriver l'alerte du T1 de 18:38. C'est la raison d'être de
 *    [PassageKey] : une clé par ligne aurait été plus simple, et aurait éteint la
 *    ligne pour la soirée.
 * 2. **Ignorer n'est pas refuser.** Une alerte qu'on laisse s'éteindre reste
 *    [PassageStatus.Alerted]. Elle ne devient [PassageStatus.Declined] que sur un
 *    geste. La nuance décide si le passage suivant de la même ligne mérite d'être
 *    annoncé.
 * 3. **Le registre est borné.** [prune] et [CAPACITY]. Un registre qui grossit
 *    sans fin finit par être le bug : il est relu à chaque calcul, et il est
 *    persisté.
 *
 * Pur : rien ici ne touche au disque ni à l'horloge — `now` est toujours un
 * paramètre.
 *
 * Port de `Native/Aule/Core/Guet/GuetLedger.swift`.
 */
data class GuetLedger(
    val entries: Map<PassageKey, Entry> = emptyMap(),
) {
    /**
     * Ce qu'on retient d'un passage, au-delà de son état.
     *
     * Les trois libellés sont là pour le **réappariement**, pas pour l'affichage :
     * sans eux, une clé dont l'heure a dérivé n'aurait aucun moyen de se
     * reconnaître dans la suivante.
     */
    data class Entry(
        val status: PassageStatus,
        val place: String,
        val line: String,
        val destination: String,
        val expectedAt: Instant,
        /**
         * Sert l'éviction et l'élagage. Distinct de l'heure de passage : une
         * entrée peut être touchée longtemps après le passage qu'elle décrit.
         */
        val updatedAt: Instant,
    )

    // ------------------------------------------------------------------- lire

    fun status(of: PassageKey): PassageStatus? = entries[of]?.status

    /** Vrai quand ce passage a déjà été refusé. Le seul motif de silence définitif. */
    fun isDeclined(key: PassageKey): Boolean = entries[key]?.status is PassageStatus.Declined

    /** Vrai quand ce passage a déjà sonné, quelle qu'ait été la suite. */
    fun hasAlerted(key: PassageKey): Boolean {
        val status = entries[key]?.status ?: return false
        return status.rank >= ALERTED_RANK
    }

    // -------------------------------------------------------------- identifier

    /**
     * La clé de ce passage, en retrouvant celle qu'il portait avant que son heure
     * ne dérive.
     *
     * C'est **le** point d'entrée du moteur : appeler [PassageKey.make]
     * directement ferait naître une clé neuve à chaque rafraîchissement temps
     * réel, et un refus ne survivrait pas à trente secondes.
     *
     * En cas d'égalité, la candidate la plus proche dans le temps l'emporte :
     * deux passages de la même ligne vers la même destination à moins de six
     * minutes d'écart existent, et les confondre serait pire que de n'en
     * réapparier aucun.
     */
    fun resolve(
        place: String,
        line: String,
        destination: String,
        expectedAt: Instant,
    ): PassageKey {
        val exact = PassageKey.make(place, line, destination, expectedAt)
        if (entries.containsKey(exact)) return exact

        val placeKey = normalizeStopName(place)
        val lineKey = normalizeStopName(line)
        val destinationKey = normalizeStopName(destination)

        return entries.entries
            .filter { (_, entry) ->
                normalizeStopName(entry.place) == placeKey &&
                    normalizeStopName(entry.line) == lineKey &&
                    normalizeStopName(entry.destination) == destinationKey &&
                    abs(secondsBetween(entry.expectedAt, expectedAt)) <= REMATCH_TOLERANCE
            }
            .minByOrNull { abs(secondsBetween(it.value.expectedAt, expectedAt)) }
            ?.key
            ?: exact
    }

    // ------------------------------------------------------------------ écrire

    /**
     * Consigne un état.
     *
     * ⚠️ **Ne rétrograde jamais** : un `Detected` qui repasse sur un `Declined`
     * est ignoré. C'est l'invariant 1 en pratique — le moteur repère le même
     * passage à chaque calcul, et sans cette garde il effacerait le refus qu'il
     * vient de recevoir.
     *
     * L'heure de passage, elle, **est** mise à jour : c'est elle qui dérive, et la
     * garder figée ferait échouer le réappariement suivant.
     */
    fun record(
        status: PassageStatus,
        key: PassageKey,
        place: String,
        line: String,
        destination: String,
        expectedAt: Instant,
        now: Instant,
    ): GuetLedger {
        val existing = entries[key]
        val updated = if (existing != null) {
            existing.copy(
                expectedAt = expectedAt,
                updatedAt = now,
                status = if (status.rank >= existing.status.rank) status else existing.status,
            )
        } else {
            Entry(
                status = status,
                place = place,
                line = line,
                destination = destination,
                expectedAt = expectedAt,
                updatedAt = now,
            )
        }
        return GuetLedger(entries + (key to updated)).enforceCapacity()
    }

    /** Oublie ce qui est trop vieux pour peser sur une décision. */
    fun prune(before: Instant): GuetLedger =
        GuetLedger(entries.filterValues { !it.expectedAt.isBefore(before) })

    /** Évince les entrées les moins récemment touchées jusqu'à retomber sous [CAPACITY]. */
    private fun enforceCapacity(): GuetLedger {
        if (entries.size <= CAPACITY) return this
        val survivors = entries.entries
            .sortedByDescending { it.value.updatedAt }
            .take(CAPACITY)
            .associate { it.key to it.value }
        return GuetLedger(survivors)
    }

    companion object {
        /**
         * Au-delà, on évince les plus anciennes. Deux cents : une journée dense en
         * compte une cinquantaine, et le triple laisse de la marge sans peser.
         */
        const val CAPACITY = 200

        /**
         * De combien l'heure d'un passage peut dériver sans qu'il cesse d'être le
         * même passage.
         *
         * Trois minutes. Mesuré nulle part et assumé : c'est un ordre de grandeur
         * de retard de bus urbain, pas une constante du réseau. Il vit ici — et
         * non dans [PassageKey] — **parce qu'ici il se teste** : une tolérance
         * mise dans la clé la rendrait non hachable de fait, deux clés « égales »
         * n'ayant pas le même condensat.
         */
        const val REMATCH_TOLERANCE = 180.0

        private val ALERTED_RANK = PassageStatus.Alerted(Instant.EPOCH).rank

        private fun secondsBetween(from: Instant, to: Instant): Double =
            Duration.between(to, from).toMillis() / 1_000.0
    }
}
