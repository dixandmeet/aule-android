package io.aule.android.core.model

/**
 * Le mode d'apparence, persisté sur l'appareil.
 *
 * Port de `SAE/lib/services/theme_service.dart` : le défaut est [LIGHT], pas
 * le mode système — au redémarrage l'app repart sur le choix retenu.
 */
enum class AppearanceMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    val storageName: String
        get() = name.lowercase()

    fun isNight(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemDark
    }

    companion object {
        fun fromStorage(raw: String?): AppearanceMode =
            entries.find { it.storageName == raw } ?: LIGHT
    }
}
