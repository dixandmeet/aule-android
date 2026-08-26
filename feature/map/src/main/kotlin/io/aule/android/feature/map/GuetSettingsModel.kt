package io.aule.android.feature.map

import io.aule.android.core.guet.AlertPreferences
import io.aule.android.core.guet.GuetPreferences
import io.aule.android.core.guet.WalkingPace
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.repository.GuetPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Les réglages du Guet, lus une fois et réécrits à chaque geste.
 *
 * ## Pourquoi une écriture par geste
 *
 * Un écran de réglages n'a pas de bouton « Enregistrer », et il ne doit pas en
 * avoir : ce sont des interrupteurs, ils valent dès qu'on les touche. Chaque
 * changement réécrit donc le fichier entier — quelques centaines d'octets, ce
 * qu'un `SharedPreferences.apply()` fait sans bloquer.
 *
 * L'alternative — enregistrer à la fermeture — perdrait tout si l'écran est tué
 * par le système, ce qui est exactement ce qui arrive quand on part chercher
 * l'autorisation d'alarme exacte dans les paramètres.
 */
internal class GuetSettingsModel(
    private val store: GuetPreferencesStore?,
) {
    private val _state = MutableStateFlow(GuetPreferences.decode(store?.read()))
    val state: StateFlow<GuetPreferences> = _state.asStateFlow()

    fun setEnabled(value: Boolean) = update { it.copy(isEnabled = value) }

    fun setPreparationMinutes(value: Int) = update { it.copy(preparationMinutes = value) }

    fun setPlatformMarginMinutes(value: Int) = update { it.copy(platformMarginMinutes = value) }

    fun setPace(value: WalkingPace) = update { it.copy(pace = value) }

    /**
     * Un mode ne se décoche pas jusqu'au vide.
     *
     * ⚠️ Sans cette garde, l'écran laisserait éteindre les trois modes : la veille
     * resterait « allumée » et ne proposerait plus jamais rien, sans qu'aucun mot
     * ne dise pourquoi. Un réglage qui rend le service muet doit être
     * l'interrupteur principal, pas un effet de bord de trois cases.
     */
    fun toggleMode(mode: TransportMode) = update { current ->
        val next = if (mode in current.modes) current.modes - mode else current.modes + mode
        if (next.isEmpty()) current else current.copy(modes = next)
    }

    fun toggleFollowedLine(line: String) = update { current ->
        val next = if (line in current.followedLines) {
            current.followedLines - line
        } else {
            current.followedLines + line
        }
        current.copy(followedLines = next)
    }

    fun setSound(value: Boolean) = updateAlerts { it.copy(sound = value) }

    fun setHaptics(value: Boolean) = updateAlerts { it.copy(haptics = value) }

    fun setNotifications(value: Boolean) = updateAlerts { it.copy(notifications = value) }

    fun setOngoingNotification(value: Boolean) = updateAlerts { it.copy(ongoingNotification = value) }

    fun setIntensity(value: AlertPreferences.Intensity) = updateAlerts { it.copy(intensity = value) }

    private fun updateAlerts(transform: (AlertPreferences) -> AlertPreferences) =
        update { it.copy(alerts = transform(it.alerts)) }

    private fun update(transform: (GuetPreferences) -> GuetPreferences) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        store?.write(next.encode())
    }
}
