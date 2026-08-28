package io.aule.android.core.security

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Ce que le dialogue a répondu.
 *
 * [Granted] porte le `Cipher` **autorisé par le système**, et non un simple
 * « c'est bon » : c'est lui qu'il faut faire travailler pour que la preuve
 * existe. Le rappel de succès seul ne prouve rien qu'un appelant distrait ne
 * pourrait ignorer.
 */
sealed interface BiometricOutcome {

    data class Granted(val cipher: Cipher) : BiometricOutcome

    data class Refused(val kind: BiometricFailureKind) : BiometricOutcome
}

/**
 * Le dialogue système, ramené à une fonction qui suspend.
 *
 * ## Ce que cette classe ne fait pas
 *
 * Elle ne connaît **aucune phrase** : titre, sous-titre et libellé du bouton
 * arrivent de l'appelant, qui les tient de `stringResource`. C'est la règle
 * d'[ADR-011] appliquée hors modèle — un module qui ne s'affiche pas n'a pas à
 * savoir dans quelle langue on lui parle.
 *
 * Elle ne traite pas non plus `onAuthenticationFailed` — le doigt posé que le
 * capteur n'a pas reconnu. Le dialogue gère seul la nouvelle tentative, et s'en
 * mêler couperait la seconde chance que le système offre déjà.
 */
class BiometricAuthenticator(
    private val logger: AuleLogger,
) {

    /**
     * Ouvre le dialogue et attend.
     *
     * `Dispatchers.Main.immediate` n'est pas une précaution : `authenticate`
     * touche à la hiérarchie de fragments de l'activité, et l'appeler d'ailleurs
     * que du fil principal lève.
     *
     * @param negativeLabel le libellé du bouton de refus — « Se connecter
     *   autrement ». Le personnaliser n'est permis que parce que
     *   `DEVICE_CREDENTIAL` n'est pas dans les authentificateurs : Android
     *   refuse les deux ensemble, et c'est ce bouton qui porte tout le repli.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String?,
        negativeLabel: String,
    ): BiometricOutcome = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {

                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        // `isActive` garde chacun des deux rappels : le système
                        // peut enchaîner une erreur après une annulation déjà
                        // servie, et reprendre une continuation deux fois lève.
                        if (!continuation.isActive) return
                        continuation.resume(
                            BiometricOutcome.Refused(biometricFailureKindOf(code)),
                        )
                    }

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        if (!continuation.isActive) return
                        val granted = result.cryptoObject?.cipher
                        continuation.resume(
                            if (granted == null) {
                                // Ne devrait pas arriver : on passe toujours un
                                // CryptoObject. Si le système en rendait un
                                // succès sans chiffre, il n'y aurait aucune
                                // preuve — donc un refus, jamais un passage.
                                logger.warn(LogDomain.AUTH, "Succès biométrique sans chiffre.")
                                BiometricOutcome.Refused(BiometricFailureKind.UNKNOWN)
                            } else {
                                BiometricOutcome.Granted(granted)
                            },
                        )
                    }
                },
            )

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(negativeLabel)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .apply { if (!subtitle.isNullOrBlank()) setSubtitle(subtitle) }
                // `setConfirmationRequired` n'est pas touché : le défaut du
                // système est d'exiger un geste après une reconnaissance
                // **passive** (le visage). C'est ce qu'on veut ici — un
                // téléphone posé sur le tableau de bord, regardé par hasard, ne
                // doit pas ouvrir une session à lui seul. L'empreinte, elle,
                // n'est pas concernée : elle est déjà un geste.
                .build()

            continuation.invokeOnCancellation { prompt.cancelAuthentication() }

            try {
                prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            } catch (failure: Throwable) {
                logger.warn(LogDomain.AUTH, "Dialogue biométrique impossible.", failure)
                if (continuation.isActive) {
                    continuation.resume(
                        BiometricOutcome.Refused(BiometricFailureKind.HARDWARE_UNAVAILABLE),
                    )
                }
            }
        }
    }
}
