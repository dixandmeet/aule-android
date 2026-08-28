package io.aule.android.core.security

import io.aule.android.core.model.repository.BiometricEnrollment
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher

/**
 * Sceller et rouvrir le marqueur biométrique.
 *
 * ## Ce qu'on chiffre, et pourquoi ce n'est pas le jeton
 *
 * Le contenu n'a aucune valeur : trente-deux octets tirés au sort à
 * l'activation. Ce qui a de la valeur, c'est de pouvoir les **rouvrir** — le
 * `Cipher` nécessaire n'existe qu'après une empreinte reconnue, parce que la
 * clé du Keystore est née avec `setUserAuthenticationRequired(true)`.
 *
 * Autrement dit : le déchiffrement *est* la preuve. Un `BiometricPrompt` sans
 * `CryptoObject` se contenterait de rappeler « c'est bon », ce qui n'engage que
 * le code qui écoute ; ici, le matériel refuse de coopérer tant que le doigt
 * n'est pas le bon.
 *
 * Le jeton de session, lui, reste où il est — en clair dans les préférences,
 * comme avant. Le durcir est un autre chantier ; le coupler à celui-ci ferait
 * dépendre la session d'une clé qu'un simple changement d'empreinte invalide.
 *
 * ## Pourquoi GCM
 *
 * Il **authentifie** en plus de chiffrer : un blob retouché à la main dans les
 * préférences ne rend pas des octets faux, il lève `AEADBadTagException`. La
 * différence compte — des octets faux se propageraient en silence.
 *
 * `java.util.Base64` et non `android.util.Base64` : le premier existe depuis
 * l'API 26, exactement le plancher du projet, et il tourne sur la JVM de
 * l'hôte. C'est ce qui rend ce fichier vérifiable sans émulateur.
 */
object SecureBlob {

    /**
     * Trente-deux octets : la taille d'un secret sérieux, alors qu'un seul
     * suffirait à la démonstration. Le coût est nul et la question « est-ce
     * assez ? » ne se posera jamais.
     */
    private const val MARKER_BYTES = 32

    /** Le marqueur tiré à l'activation. Jamais relu pour ce qu'il contient. */
    fun randomMarker(): ByteArray = ByteArray(MARKER_BYTES).also { SecureRandom().nextBytes(it) }

    /**
     * Scelle [plaintext] avec un `Cipher` déjà autorisé par le dialogue.
     *
     * Le vecteur d'initialisation est celui que le Keystore a choisi : il se lit
     * **après** l'opération et se range avec le chiffré, faute de quoi rien ne
     * pourra être rouvert.
     */
    fun seal(cipher: Cipher, plaintext: ByteArray): BiometricEnrollment = BiometricEnrollment(
        cipherText = encode(cipher.doFinal(plaintext)),
        iv = encode(cipher.iv),
    )

    /**
     * Rouvre le marqueur. Lève `AEADBadTagException` si le blob a été touché.
     *
     * C'est cet appel — et non le seul rappel `onAuthenticationSucceeded` — qui
     * prouve que la biométrie a eu lieu.
     */
    fun open(cipher: Cipher, enrollment: BiometricEnrollment): ByteArray =
        cipher.doFinal(decode(enrollment.cipherText))

    /** Le vecteur d'initialisation, à donner à [BiometricKeyVault.decryptCipher]. */
    fun ivOf(enrollment: BiometricEnrollment): ByteArray = decode(enrollment.iv)

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
}
