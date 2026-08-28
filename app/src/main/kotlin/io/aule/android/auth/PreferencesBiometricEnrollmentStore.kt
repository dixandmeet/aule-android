package io.aule.android.auth

import android.content.Context
import androidx.core.content.edit
import io.aule.android.core.model.repository.BiometricEnrollment
import io.aule.android.core.model.repository.BiometricEnrollmentStore

/**
 * L'activation biométrique, dans son propre fichier.
 *
 * Un fichier à part de la session et de l'habilitation, pour la raison qui vaut
 * déjà pour celles-ci : vider l'un ne doit pas vider l'autre par mégarde. Ici
 * la conséquence serait visible — une déconnexion qui emporterait le drapeau
 * « déjà proposé » ferait resurgir la proposition à chaque connexion.
 *
 * ## Ce que ce fichier contient
 *
 * Un chiffré et son vecteur d'initialisation, tous deux inutiles sans la clé du
 * Keystore — qui, elle, ne sort pas du matériel. Quelqu'un qui lirait ce fichier
 * n'y trouverait ni jeton, ni adresse, ni rien qui ouvre quoi que ce soit.
 *
 * ## Pourquoi « déjà proposé » survit à [clear]
 *
 * Les deux moitiés de ce dépôt ne répondent pas à la même question. « Ce compte
 * est-il protégé ? » se remet à zéro dès qu'on déconnecte. « Lui a-t-on déjà
 * posé la question ? » ne se remet jamais à zéro : c'est un souvenir de
 * conversation, pas un état de sécurité. Les ranger ensemble ferait réapparaître
 * un volet que l'utilisateur a déjà écarté.
 */
class PreferencesBiometricEnrollmentStore(
    context: Context,
) : BiometricEnrollmentStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * ⚠️ La garde d'identité est **le** contrôle d'accès de cette fonctionnalité.
     *
     * Un poste de conduite se partage, et fermer l'application n'est pas se
     * déconnecter : la session du conducteur précédent peut très bien être
     * encore là quand son collègue reprend l'appareil. Sans cette comparaison,
     * l'empreinte du premier ouvrirait la session du premier à celui qui
     * l'a remplacé.
     */
    override suspend fun read(userId: String): BiometricEnrollment? {
        if (userId.isBlank()) return null
        if (prefs.getString(KEY_USER_ID, null) != userId) return null
        val cipherText = prefs.getString(KEY_CIPHER, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return BiometricEnrollment(cipherText = cipherText, iv = iv)
    }

    override suspend fun write(userId: String, enrollment: BiometricEnrollment) {
        if (userId.isBlank()) return
        prefs.edit {
            putString(KEY_USER_ID, userId)
            putString(KEY_CIPHER, enrollment.cipherText)
            putString(KEY_IV, enrollment.iv)
        }
    }

    /**
     * Efface l'activation **sans toucher** à la liste des comptes déjà
     * sollicités : un `clear()` global emporterait les deux, et c'est
     * précisément ce qu'on ne veut pas.
     */
    override suspend fun clear() {
        prefs.edit {
            remove(KEY_USER_ID)
            remove(KEY_CIPHER)
            remove(KEY_IV)
        }
    }

    override suspend fun hasBeenOffered(userId: String): Boolean =
        userId.isNotBlank() && userId in offered()

    override suspend fun markOffered(userId: String) {
        if (userId.isBlank()) return
        // La copie n'est pas un réflexe de style : `getStringSet` rend un
        // ensemble que la documentation interdit de modifier, et le muter
        // corromprait la valeur gardée en mémoire par SharedPreferences.
        prefs.edit { putStringSet(KEY_OFFERED, offered() + userId) }
    }

    private fun offered(): Set<String> = prefs.getStringSet(KEY_OFFERED, emptySet()).orEmpty()

    private companion object {
        const val PREFS = "io.aule.android.auth.biometric"
        const val KEY_USER_ID = "user_id"
        const val KEY_CIPHER = "cipher_text"
        const val KEY_IV = "iv"
        const val KEY_OFFERED = "offered.v1"
    }
}
