package io.aule.android.appearance

import android.content.Context
import androidx.core.content.edit
import io.aule.android.core.model.AppearanceMode
import io.aule.android.core.model.repository.AppearanceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences du thème, clés alignées sur Flutter (`sae.theme_mode`).
 */
class PreferencesAppearanceStore(
    context: Context,
) : AppearanceStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(): AppearanceMode =
        AppearanceMode.fromStorage(prefs.getString(KEY, null))

    override fun write(mode: AppearanceMode) {
        prefs.edit {
            putString(KEY, mode.storageName)
        }
    }

    private companion object {
        const val PREFS = "io.aule.android.appearance"
        const val KEY = "sae.theme_mode"
    }
}

/**
 * Le choix d'apparence, lu une fois au démarrage puis poussé aux écrans.
 */
class AppearanceSettings(
    private val store: AppearanceStore,
) {
    private val _mode = MutableStateFlow(store.read())
    val mode: StateFlow<AppearanceMode> = _mode.asStateFlow()

    fun setMode(mode: AppearanceMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        store.write(mode)
    }
}
