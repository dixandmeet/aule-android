package io.aule.android.auth

import android.content.Context
import io.aule.android.core.model.repository.AuthPkceStore
import io.aule.android.core.model.repository.RegistrationDraftStore

/**
 * Vérifieur PKCE, fichier séparé de la session : [PreferencesAuthSessionStore.clear]
 * vide tout son fichier, et le lien de confirmation peut arriver après une
 * déconnexion.
 */
class PreferencesAuthPkceStore(
    context: Context,
) : AuthPkceStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun writeVerifier(verifier: String) {
        prefs.edit().putString(KEY_VERIFIER, verifier).apply()
    }

    override suspend fun readVerifier(): String? =
        prefs.getString(KEY_VERIFIER, null)?.takeIf { it.isNotBlank() }

    override suspend fun clearVerifier() {
        prefs.edit().remove(KEY_VERIFIER).apply()
    }

    private companion object {
        const val PREFS = "io.aule.android.pkce"
        const val KEY_VERIFIER = "code_verifier"
    }
}

/**
 * Brouillon d'inscription V2, sans mot de passe.
 *
 * Les clés reprennent Flutter (`aulepro-onboarding-v2-*`) pour qu'un port
 * de test relise le même JSON.
 */
class PreferencesRegistrationDraftStore(
    context: Context,
) : RegistrationDraftStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun readDraft(): String? = prefs.getString(KEY_DRAFT, null)

    override suspend fun readStep(): String? = prefs.getString(KEY_STEP, null)

    override suspend fun write(draftJson: String, step: String) {
        prefs.edit()
            .putString(KEY_DRAFT, draftJson)
            .putString(KEY_STEP, step)
            .apply()
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS = "io.aule.android.registration"
        const val KEY_DRAFT = "aulepro-onboarding-v2-draft"
        const val KEY_STEP = "aulepro-onboarding-v2-step"
    }
}
