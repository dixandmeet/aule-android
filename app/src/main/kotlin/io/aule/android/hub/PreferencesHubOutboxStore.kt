package io.aule.android.hub

import android.content.Context
import androidx.core.content.edit
import io.aule.android.core.model.HubPendingMessage
import io.aule.android.core.model.decodeHubPending
import io.aule.android.core.model.encodeHubPending
import io.aule.android.core.model.repository.HubOutboxStore

/**
 * La file hors ligne, sur le disque.
 *
 * ## Pourquoi elle vit ici, et pas en mémoire
 *
 * Un tunnel, un dépôt en sous-sol, une zone blanche : ce sont exactement les
 * endroits d'où un conducteur écrit. Une file en mémoire meurt avec le
 * processus, et le message avec elle — **sans que personne ne le sache**.
 *
 * Ce magasin ne sait que ranger et relire **une chaîne**. Les règles — l'ordre,
 * les essais, le refus définitif — vivent dans `:core:model` et le ViewModel, ce
 * qui permet de les vérifier sans disque.
 *
 * ⚠️ **Une entrée par compte.** Un téléphone de service passe de main en main :
 * une clé unique ferait repartir, au nom du collègue suivant, un message que le
 * précédent avait tapé.
 */
class PreferencesHubOutboxStore(
    context: Context,
    private val owner: () -> String?,
) : HubOutboxStore {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun load(): List<HubPendingMessage> =
        decodeHubPending(prefs.getString(key(), null))

    override suspend fun save(pending: List<HubPendingMessage>) {
        prefs.edit { putString(key(), pending.encodeHubPending()) }
    }

    private fun key(): String = "$KEY.${owner() ?: ANONYME}"

    private companion object {
        const val PREFS = "io.aule.android.hub"
        const val KEY = "hub.outbox.v1"
        const val ANONYME = "anonyme"
    }
}
