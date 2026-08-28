package io.aule.android.auth

import android.content.Context
import androidx.core.content.edit
import io.aule.android.core.model.AccountModes
import io.aule.android.core.model.AgentAccess
import io.aule.android.core.model.AgentRole
import io.aule.android.core.model.repository.AgentAccessStore

/**
 * L'habilitation accordée, gardée sur l'appareil.
 *
 * Deux énumérations et un identifiant de compte : SharedPreferences suffit, et
 * un fichier à part de la session pour que vider l'un ne vide pas l'autre par
 * mégarde — c'est justement ce qu'on cherche à éviter.
 *
 * ## Ce que ce fichier ne contient pas
 *
 * Aucun jeton, aucune adresse, aucun nom. Seulement « ce compte-ci avait le
 * droit d'entrer, dans ce mode-là ». Quelqu'un qui lirait le fichier n'y
 * gagnerait rien qu'il ne sache déjà en ouvrant l'application.
 *
 * ## Pourquoi l'identifiant est stocké à côté de la valeur
 *
 * Deux comptes se succèdent sur un même téléphone — un remplaçant qui prend le
 * poste, un agent qui prête son appareil. Ranger l'habilitation sans dire à qui
 * elle appartient ouvrirait l'application au second avec les droits du premier.
 * [read] ne rend donc rien si l'identifiant ne correspond pas.
 */
class PreferencesAgentAccessStore(
    context: Context,
) : AgentAccessStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun read(userId: String): AgentAccess? {
        if (userId.isBlank()) return null
        if (prefs.getString(KEY_USER_ID, null) != userId) return null
        val modes = prefs.getString(KEY_MODES, null)?.let { stored ->
            AccountModes.entries.firstOrNull { it.name == stored }
        } ?: return null
        val role = prefs.getString(KEY_ROLE, null)?.let { stored ->
            AgentRole.entries.firstOrNull { it.name == stored }
        } ?: return null
        return AgentAccess(modes = modes, initialRole = role)
    }

    override suspend fun write(userId: String, access: AgentAccess) {
        if (userId.isBlank()) return
        prefs.edit {
            putString(KEY_USER_ID, userId)
            putString(KEY_MODES, access.modes.name)
            putString(KEY_ROLE, access.initialRole.name)
        }
    }

    override suspend fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val PREFS = "io.aule.android.auth.access"
        const val KEY_USER_ID = "user_id"
        const val KEY_MODES = "modes"
        const val KEY_ROLE = "initial_role"
    }
}
