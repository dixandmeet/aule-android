package io.aule.android.core.security

import androidx.fragment.app.FragmentActivity
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.repository.BiometricEnrollmentStore
import kotlinx.coroutines.CancellationException

/**
 * Ce qu'a donné une tentative d'activation.
 *
 * [NotEnrolled] n'est pas rangé parmi les refus, et c'est le point de la
 * distinction : c'est le seul cas qui appelle un écran — celui qui propose
 * d'ouvrir les réglages du téléphone. Les autres retombent en silence.
 */
sealed interface BiometricEnableResult {

    /** La clé existe, le marqueur est scellé, le compte est protégé. */
    data object Enabled : BiometricEnableResult

    /** Matériel présent, rien d'enregistré : à orienter vers les réglages. */
    data object NotEnrolled : BiometricEnableResult

    /** Annulation, verrou, panne. Aucun bandeau pour une annulation. */
    data class Refused(val kind: BiometricFailureKind) : BiometricEnableResult
}

/**
 * Activer la biométrie pour [userId] : la séquence complète, en un seul endroit.
 *
 * ## Pourquoi une fonction libre, et non une méthode d'`AuthViewModel`
 *
 * Parce qu'il faut une `Activity`, et qu'un `ViewModel` ne doit jamais en tenir
 * une : il survit aux recréations de configuration, l'activité non, et la
 * garder revient à fuir une fenêtre entière à chaque rotation. Le
 * `BiometricKeyVault` et le `BiometricAuthenticator` vivent donc sur le graphe
 * et sont consommés par les Composables, qui ont l'activité sous la main.
 *
 * Deux écrans appellent cette séquence — la proposition qui suit la première
 * connexion, et la rangée « Connexion biométrique » des réglages — et ils
 * doivent l'appeler **à l'identique**. Deux copies divergeraient au premier
 * correctif appliqué d'un seul côté ; c'est le genre d'écart qui laisse une clé
 * créée sans marqueur scellé, donc un compte qu'on croit protégé et qui ne
 * l'est pas.
 *
 * ## L'ordre des gestes
 *
 * La clé d'abord, le dialogue ensuite, le scellé en dernier — et l'écriture du
 * dépôt **après** le scellé. Rien n'est écrit tant que l'empreinte n'a pas été
 * reconnue : un dépôt renseigné devant une clé refusée ferait croire à une
 * protection active et présenterait, au lancement suivant, un dialogue qui ne
 * peut pas aboutir.
 *
 * [BiometricEnrollmentStore.markOffered] n'est **pas** appelé ici : proposer et
 * activer sont deux choses, et les réglages activent sans qu'on ait rien
 * proposé. C'est l'écran qui propose qui note qu'il l'a fait.
 */
suspend fun enableBiometric(
    activity: FragmentActivity,
    support: BiometricSupport,
    vault: BiometricKeyVault,
    authenticator: BiometricAuthenticator,
    store: BiometricEnrollmentStore,
    userId: String,
    logger: AuleLogger,
    title: String,
    subtitle: String?,
    negativeLabel: String,
): BiometricEnableResult {
    val availability = support.availability()
    if (availability == BiometricAvailability.NONE_ENROLLED) return BiometricEnableResult.NotEnrolled
    if (!availability.isReady) {
        logger.info(LogDomain.AUTH, "Activation biométrique impossible ($availability).")
        return BiometricEnableResult.Refused(BiometricFailureKind.HARDWARE_UNAVAILABLE)
    }

    return try {
        vault.createKey()
        val outcome = authenticator.authenticate(
            activity = activity,
            cipher = vault.encryptCipher(),
            title = title,
            subtitle = subtitle,
            negativeLabel = negativeLabel,
        )
        when (outcome) {
            is BiometricOutcome.Refused -> {
                // La clé ne sert à rien sans marqueur scellé : la laisser
                // derrière soi encombrerait le Keystore d'une entrée que plus
                // rien ne référence, et qu'aucun chemin ne viendrait effacer.
                vault.deleteKey()
                BiometricEnableResult.Refused(outcome.kind)
            }
            is BiometricOutcome.Granted -> {
                val enrollment = SecureBlob.seal(outcome.cipher, SecureBlob.randomMarker())
                store.write(userId, enrollment)
                logger.info(LogDomain.AUTH, "Connexion biométrique activée.")
                BiometricEnableResult.Enabled
            }
        }
    } catch (cancelled: CancellationException) {
        // L'écran s'est refermé pendant le dialogue. Ne rien laisser derrière,
        // puis laisser l'annulation remonter — l'avaler ferait continuer une
        // coroutine dont la portée est déjà morte.
        vault.deleteKey()
        throw cancelled
    } catch (invalidated: BiometricKeyInvalidatedException) {
        logger.warn(LogDomain.AUTH, "Clé biométrique invalidée pendant l'activation.", invalidated)
        vault.deleteKey()
        BiometricEnableResult.Refused(BiometricFailureKind.KEY_INVALIDATED)
    } catch (failure: Throwable) {
        logger.warn(LogDomain.AUTH, "Activation biométrique en échec.", failure)
        vault.deleteKey()
        BiometricEnableResult.Refused(BiometricFailureKind.UNKNOWN)
    }
}

/**
 * Désactiver : la clé, puis le dépôt.
 *
 * `hasBeenOffered` n'est pas touché — c'est tout l'intérêt qu'il vive à part.
 * Qui désactive dans les réglages ne doit pas se voir reproposer la chose à la
 * connexion suivante. La session, elle, ne bouge pas : on retire un verrou, on
 * ne ferme pas la porte.
 */
suspend fun disableBiometric(
    vault: BiometricKeyVault,
    store: BiometricEnrollmentStore,
    logger: AuleLogger,
) {
    vault.deleteKey()
    runCatching { store.clear() }
        .onFailure { logger.warn(LogDomain.AUTH, "Réglage biométrique non effacé.", it) }
}
