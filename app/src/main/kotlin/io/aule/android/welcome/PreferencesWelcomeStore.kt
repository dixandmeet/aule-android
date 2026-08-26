package io.aule.android.welcome

import android.content.Context
import io.aule.android.core.model.repository.WelcomeStore

/**
 * Le drapeau « accueil vu », dans son propre fichier.
 *
 * Un seul booléen, et pourtant pas rangé avec le reste : il doit survivre à une
 * déconnexion — l'accueil explique la localisation, pas le compte — et les
 * préférences de session se vident en bloc.
 */
class PreferencesWelcomeStore(
    context: Context,
) : WelcomeStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun hasSeenWelcome(): Boolean = prefs.getBoolean(KEY, false)

    override fun markWelcomeSeen() {
        prefs.edit().putBoolean(KEY, true).apply()
    }

    private companion object {
        const val PREFS = "io.aule.android.welcome"
        const val KEY = "welcome.seen.v1"
    }
}
