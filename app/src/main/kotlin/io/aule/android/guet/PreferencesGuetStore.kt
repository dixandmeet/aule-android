package io.aule.android.guet

import android.content.Context
import io.aule.android.core.model.repository.GuetPreferencesStore

/**
 * Les réglages du Guet, dans leur propre fichier.
 *
 * Séparé du reste pour la raison habituelle : ce sont des réglages de service,
 * pas de session, et se déconnecter ne doit pas rendre muette une veille qu'on a
 * demandée.
 *
 * Il ne sait rien du contenu — c'est `GuetPreferences.encode` et
 * `GuetPreferences.decode` qui portent la forme, et leur tolérance se vérifie
 * sans disque.
 */
class PreferencesGuetStore(
    context: Context,
) : GuetPreferencesStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    override fun write(encoded: String) {
        prefs.edit().putString(KEY, encoded).apply()
    }

    private companion object {
        const val PREFS = "io.aule.android.guet"
        const val KEY = "guet.preferences.v1"
    }
}
