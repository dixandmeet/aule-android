package io.aule.android.auth

import android.content.Context
import androidx.core.content.edit
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.repository.AuthSessionStore

/**
 * Persistance locale de la session GoTrue.
 *
 * SharedPreferences plutôt que DataStore pour ce premier lot : quatre
 * chaînes, pas de migration, et le fichier ne quitte pas l'appareil.
 * Les jetons n'entrent jamais dans le journal.
 */
class PreferencesAuthSessionStore(
    context: Context,
) : AuthSessionStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun read(): AuthSession? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val expires = prefs.getLong(KEY_EXPIRES, 0L)
        if (id.isBlank() || email.isBlank() || access.isBlank() || refresh.isBlank() || expires <= 0L) {
            return null
        }
        return AuthSession(
            user = AuthUser(id = id, email = email),
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = expires,
        )
    }

    override suspend fun write(session: AuthSession) {
        prefs.edit {
            putString(KEY_USER_ID, session.user.id)
            putString(KEY_EMAIL, session.user.email)
            putString(KEY_ACCESS, session.accessToken)
            putString(KEY_REFRESH, session.refreshToken)
            putLong(KEY_EXPIRES, session.expiresAtEpochSeconds)
        }
    }

    override suspend fun clear() {
        prefs.edit {
            clear()
        }
    }

    private companion object {
        const val PREFS = "io.aule.android.auth"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
    }
}
