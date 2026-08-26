package io.aule.android.search

import android.content.Context
import io.aule.android.core.model.SavedPlace
import io.aule.android.core.model.decodeSavedPlaces
import io.aule.android.core.model.encodeSavedPlaces
import io.aule.android.core.model.repository.SavedPlacesStore

/**
 * Les adresses favorites, dans les préférences.
 *
 * Même fichier que l'historique des destinations, et pour la même raison qu'il
 * est séparé de la session : où l'on va n'a rien à voir avec un jeton, et se
 * déconnecter ne doit pas effacer son domicile. Le compte les retrouvera à la
 * reconnexion (`SupabaseSavedPlaceRepository`) ; ils n'ont pas attendu pour être
 * là.
 *
 * Ce dépôt ne sait que ranger et relire **une chaîne**. Les règles — remplacer,
 * supprimer, fusionner, ordonner — vivent dans `:core:model`, ce qui permet de
 * les vérifier sans disque.
 */
class PreferencesSavedPlacesStore(
    context: Context,
) : SavedPlacesStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(owner: String?): List<SavedPlace> =
        decodeSavedPlaces(prefs.getString(key(owner), null))

    override fun write(owner: String?, places: List<SavedPlace>) {
        prefs.edit().putString(key(owner), places.encodeSavedPlaces()).apply()
    }

    /**
     * Une entrée par compte.
     *
     * Un téléphone de service passe de main en main : une clé unique aurait
     * montré le domicile du collègue précédent, puis l'aurait poussé sur le
     * compte du suivant à la première synchronisation.
     *
     * Sans session, la clé anonyme : elle n'est jamais écrite en pratique — la
     * carte vit derrière une session — et elle évite d'avoir à traiter le nul
     * plus haut.
     */
    private fun key(owner: String?): String = "$KEY.${owner ?: ANONYMOUS}"

    private companion object {
        /** Le fichier de la recherche, déjà ouvert pour l'historique. */
        const val PREFS = "io.aule.android.search"
        const val KEY = "saved.places.v1"
        const val ANONYMOUS = "anonyme"
    }
}
