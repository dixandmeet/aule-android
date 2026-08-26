package io.aule.android.search

import android.content.Context
import io.aule.android.core.model.Place
import io.aule.android.core.model.decodeSearchHistory
import io.aule.android.core.model.encodeHistory
import io.aule.android.core.model.rememberPlace
import io.aule.android.core.model.repository.SearchHistoryStore

/**
 * L'historique des destinations, dans les préférences.
 *
 * Fichier séparé de la session : une destination retenue n'a rien à voir avec un
 * jeton, et se déconnecter ne doit pas effacer où l'on allait. C'est la même
 * raison qui met le vérifieur PKCE dans son propre fichier.
 *
 * La règle — tête de liste, dédoublonnage, plafond — est dans `:core:model`
 * ([rememberPlace]) : ce dépôt ne sait que ranger et relire une chaîne, et c'est
 * ce qui permet de vérifier la règle sans disque.
 */
class PreferencesSearchHistoryStore(
    context: Context,
) : SearchHistoryStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(): List<Place> = decodeSearchHistory(prefs.getString(KEY, null))

    override fun remember(place: Place): List<Place> {
        val next = rememberPlace(place, read())
        prefs.edit().putString(KEY, next.encodeHistory()).apply()
        return next
    }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val PREFS = "io.aule.android.search"
        const val KEY = "search.history.v1"
    }
}
