package io.aule.android.core.model

/**
 * La fiche agent, lue dans `drivers`.
 *
 * L'adresse de session n'est pas la fiche : un compte peut exister sans
 * matricule ni dépôt, et l'écran doit pouvoir le dire plutôt que d'inventer
 * un agent. Les champs optionnels sont donc des absences, pas des chaînes
 * vides.
 */
data class DriverProfile(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val driverNumber: String? = null,
    val depotId: String? = null,
    val networkId: String? = null,
    val avatarUrl: String? = null,
    val msrControl: Boolean = false,
    val msrIntervention: Boolean = false,
) {
    /**
     * « Prénom Nom », ou `null` si la fiche n'a ni l'un ni l'autre.
     *
     * Le menu a alors le droit de retomber sur l'adresse : afficher une fiche
     * sans nom, c'est afficher une fiche qui ne dit pas à qui elle appartient.
     */
    fun displayName(): String? {
        val joined = listOfNotNull(firstName.trimmedOrNull(), lastName.trimmedOrNull())
            .joinToString(" ")
        return joined.takeIf { it.isNotEmpty() }
    }
}

/** Un dépôt Semitan, référencé par `drivers.depot_id`. */
data class Depot(
    val id: String,
    val code: String,
    val name: String,
    val networkId: String? = null,
) {
    /** « BLX · Dépôt Haluchère », tel que le menu l'affiche. */
    val label: String get() = "$code · $name"

    /** « Dépôt Haluchère (BLX) », tel que le profil l'affiche. */
    val directoryLabel: String get() = "$name ($code)"
}

/** Un réseau de l'annuaire, référencé par `drivers.network_id`. */
data class TransportNetwork(
    val id: String,
    val code: String,
    val name: String,
) {
    /** « Nantes ({code}) », tel que le profil l'affiche. */
    val label: String get() = "$name ($code)"
}

/**
 * Deux lettres tirées du nom affiché.
 *
 * Un prénom et un nom donnent leurs initiales. Un seul mot — l'adresse locale,
 * « Session locale » — donne ses deux premiers caractères. C'est ce qui
 * distingue deux comptes d'un coup d'œil quand personne n'a mis de photo.
 */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size >= 2 ->
            "${parts[0].first()}${parts[1].first()}".uppercase()
        else -> parts[0].take(2).uppercase()
    }
}

/**
 * Ce qu'on envoie pour enregistrer la fiche.
 *
 * Les `null` sont des **vides** : un champ effacé doit partir comme null,
 * pas être omis, sinon la base garderait l'ancienne valeur.
 */
data class DriverProfileUpdate(
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val driverNumber: String? = null,
    val depotId: String? = null,
    val networkId: String? = null,
)

/** Les dépôts d'un réseau, ou tous si aucun n'est choisi. */
fun List<Depot>.forNetwork(networkId: String?): List<Depot> =
    if (networkId == null) this else filter { it.networkId == networkId }

/**
 * Pourquoi l'envoi ou le retrait de la photo a échoué, sans phrase.
 *
 * L'UI traduit (ADR-011). Les cas reprennent le mapping Flutter
 * (`SAE/lib/services/driver_profile_service.dart`).
 */
enum class AvatarFailureKind {
    EMPTY,
    NOT_CONFIGURED,
    DENIED,
    UNSUPPORTED,
    NETWORK,
    UNKNOWN,
}

class AvatarException(
    val kind: AvatarFailureKind,
) : Exception(kind.name)

private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
