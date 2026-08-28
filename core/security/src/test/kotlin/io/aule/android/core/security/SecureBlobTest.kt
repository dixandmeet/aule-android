package io.aule.android.core.security

import io.aule.android.core.model.repository.BiometricEnrollment
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le scellage, vérifié sans Keystore.
 *
 * Une `SecretKeySpec` en mémoire remplace la clé matérielle : ce qui se teste
 * ici n'est pas le Keystore — il n'existe que sur l'appareil — mais le format
 * et l'aller-retour, qui sont du `javax.crypto` ordinaire. La partie
 * proprement Android (la clé qui exige une empreinte) se vérifie sur le S21, et
 * nulle part ailleurs.
 */
class SecureBlobTest {

    private val key = SecretKeySpec(ByteArray(32) { (it * 7).toByte() }, "AES")

    private fun encryptCipher(): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }

    private fun decryptCipher(iv: ByteArray): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
    }

    @Test
    fun `sceller puis rouvrir rend le clair`() {
        val marker = SecureBlob.randomMarker()

        val sealed = SecureBlob.seal(encryptCipher(), marker)
        val reopened = SecureBlob.open(decryptCipher(SecureBlob.ivOf(sealed)), sealed)

        assertContentEquals(marker, reopened)
    }

    @Test
    fun `le marqueur ne se lit pas dans le scelle`() {
        val marker = SecureBlob.randomMarker()

        val sealed = SecureBlob.seal(encryptCipher(), marker)

        // Le contenu n'a aucune valeur en soi, mais un « chiffrement » qui
        // laisserait le clair apparent ne prouverait plus rien du tout.
        val encodedMarker = Base64.getEncoder().encodeToString(marker)
        assertNotEquals(encodedMarker, sealed.cipherText)
        assertTrue(sealed.iv.isNotEmpty(), "le vecteur d'initialisation doit être conservé")
    }

    @Test
    fun `deux activations ne produisent pas le meme scelle`() {
        // Chaque activation tire un marqueur neuf et un vecteur neuf : deux
        // appareils, ou deux activations successives, ne laissent pas la même
        // trace dans les préférences.
        val premier = SecureBlob.seal(encryptCipher(), SecureBlob.randomMarker())
        val second = SecureBlob.seal(encryptCipher(), SecureBlob.randomMarker())

        assertNotEquals(premier.cipherText, second.cipherText)
        assertNotEquals(premier.iv, second.iv)
    }

    /**
     * GCM authentifie : un blob retouché ne rend pas des octets faux, il lève.
     * C'est la différence qui compte — des octets faux se propageraient en
     * silence jusqu'à une session ouverte sur rien.
     */
    @Test
    fun `un scelle altere leve plutot que de rendre du faux`() {
        val sealed = SecureBlob.seal(encryptCipher(), SecureBlob.randomMarker())
        val octets = Base64.getDecoder().decode(sealed.cipherText)
        octets[0] = (octets[0].toInt() xor 0x01).toByte()
        val altered = BiometricEnrollment(
            cipherText = Base64.getEncoder().encodeToString(octets),
            iv = sealed.iv,
        )

        assertFailsWith<AEADBadTagException> {
            SecureBlob.open(decryptCipher(SecureBlob.ivOf(altered)), altered)
        }
    }

    @Test
    fun `un vecteur d'initialisation etranger ne rouvre rien`() {
        val sealed = SecureBlob.seal(encryptCipher(), SecureBlob.randomMarker())
        val autre = SecureBlob.seal(encryptCipher(), SecureBlob.randomMarker())

        assertFailsWith<AEADBadTagException> {
            SecureBlob.open(decryptCipher(SecureBlob.ivOf(autre)), sealed)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
