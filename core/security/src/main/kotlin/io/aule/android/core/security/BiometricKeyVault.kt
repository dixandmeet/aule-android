package io.aule.android.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * La clé du Keystore matériel qui garde le marqueur biométrique.
 *
 * ## Un alias fixe, et non un par compte
 *
 * Il n'y a jamais qu'une session locale à la fois : garder plusieurs clés
 * reviendrait à garder des clés pour des comptes qui ne peuvent pas entrer. La
 * garde « bon compte » est portée ailleurs, par `BiometricEnrollmentStore` qui
 * range par identifiant — un nom de clé n'est pas un contrôle d'accès.
 *
 * ## Ce que la clé ne peut pas faire
 *
 * Elle **ne sort pas de l'appareil**, et pas par discipline : le fournisseur
 * `AndroidKeyStore` ne rend jamais le matériau d'une clé, quoi qu'on lui
 * demande. On ne la lit pas, on lui fait faire le travail. Écrit ici pour que
 * l'absence de code d'export ne se relise pas un jour comme un oubli.
 *
 * ## Ce qui l'invalide
 *
 * `setInvalidatedByBiometricEnrollment(true)` : ajouter ou retirer une empreinte
 * détruit la clé. C'est exactement ce qu'on veut — sans cela, inscrire son
 * empreinte sur le téléphone d'un collègue ouvrirait sa session. Le prix est
 * qu'un changement d'empreinte redemande une activation, ce qui est le bon prix.
 */
class BiometricKeyVault(
    private val logger: AuleLogger,
    private val alias: String = DEFAULT_ALIAS,
) {

    /**
     * Détruit puis régénère.
     *
     * L'ordre n'est pas une précaution de style : réutiliser une clé AES-GCM
     * avec un vecteur d'initialisation déjà servi casse la garantie du mode.
     * Repartir d'une clé neuve à chaque activation rend la question sans objet.
     */
    fun createKey() {
        deleteKey()
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(specification())
        generator.generateKey()
        logger.info(LogDomain.AUTH, "Clé biométrique créée.")
    }

    /**
     * Le `Cipher` de scellage, à passer au dialogue.
     *
     * Lève [BiometricKeyInvalidatedException] — voir [decryptCipher], la raison
     * est la même et elle vaut d'être lue.
     */
    fun encryptCipher(): Cipher = cipher { it.init(Cipher.ENCRYPT_MODE, secretKey()) }

    /**
     * Le `Cipher` de réouverture, pour le vecteur rangé avec le chiffré.
     *
     * ⚠️ **C'est ici que l'invalidation se découvre, pas dans le dialogue.**
     * `Cipher.init` lève dès que la clé ne vaut plus rien — donc *avant* que
     * quoi que ce soit s'affiche. Attendre ce cas dans le rappel de
     * `BiometricPrompt` reviendrait à l'attendre à un endroit où il ne passe
     * jamais, et l'écran resterait sur un dialogue qui ne s'ouvre pas.
     */
    fun decryptCipher(iv: ByteArray): Cipher = cipher {
        it.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
    }

    /** Efface la clé. Ne lève jamais : une clé déjà absente est le but recherché. */
    fun deleteKey() {
        runCatching { keyStore().deleteEntry(alias) }
            .onFailure { logger.warn(LogDomain.AUTH, "Clé biométrique non effaçable.", it) }
    }

    private fun cipher(initialise: (Cipher) -> Unit): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            initialise(cipher)
        } catch (invalidated: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidatedException(
                "La clé biométrique a été invalidée par le système.",
                invalidated,
            )
        }
        return cipher
    }

    /**
     * La clé, ou une invalidation.
     *
     * Une clé **absente** est traitée comme une clé invalidée, et ce n'est pas
     * un raccourci : les deux cas appellent exactement la même suite — effacer
     * l'activation, retomber sur le formulaire, laisser réactiver ensuite. Les
     * distinguer donnerait deux chemins pour une seule réaction. Cela arrive
     * quand le fichier de préférences survit à une clé qui, elle, est partie.
     */
    private fun secretKey(): SecretKey {
        val key = runCatching { keyStore().getKey(alias, null) }.getOrNull()
        return key as? SecretKey
            ?: throw BiometricKeyInvalidatedException("Aucune clé biométrique utilisable.")
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun specification(): KeyGenParameterSpec = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(KEY_BITS)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Zéro seconde : une autorisation par usage, jamais une fenêtre
                // pendant laquelle la clé resterait ouverte. Et
                // `AUTH_BIOMETRIC_STRONG` seul — le code de verrouillage de
                // l'appareil n'ouvre rien ici, c'est la décision de périmètre.
                setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                // Le plancher du projet est l'API 26 : ces trois versions
                // existent encore et n'ont pas l'appel ci-dessus. `-1` y dit la
                // même chose — une authentification biométrique par usage.
                @Suppress("DEPRECATION")
                setUserAuthenticationValidityDurationSeconds(-1)
            }
        }
        .build()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val DEFAULT_ALIAS = "io.aule.android.security.biometric-gate"
    }
}

/**
 * La clé ne vaut plus rien : empreinte ajoutée ou retirée, verrouillage remis à
 * zéro, ou clé disparue sans que l'activation ait suivi.
 *
 * Un seul type pour ces cas parce qu'ils appellent une seule réaction. L'appelant
 * n'a donc qu'un `catch` à écrire, et le chemin de sortie — effacer, revenir au
 * formulaire, proposer de réactiver — ne se duplique pas.
 */
class BiometricKeyInvalidatedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
