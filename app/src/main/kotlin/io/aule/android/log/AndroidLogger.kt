package io.aule.android.log

import android.util.Log
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain

/**
 * L'implémentation qui écrit dans logcat.
 *
 * Elle vit dans `:app` et non dans `:core:common` : c'est le seul endroit du
 * projet qui doive connaître `android.util.Log`, et l'y garder laisse toute la
 * chaîne `common → network → data` en Kotlin pur, testable sans appareil.
 *
 * [verbose] est faux en production : le niveau DEBUG porte des coordonnées et des
 * corps de réponse, qui n'ont rien à faire dans le journal d'un appareil livré.
 */
class AndroidLogger(private val verbose: Boolean) : AuleLogger {

    override fun log(level: AuleLogger.Level, domain: LogDomain, message: String, error: Throwable?) {
        if (level == AuleLogger.Level.DEBUG && !verbose) return
        when (level) {
            AuleLogger.Level.DEBUG -> Log.d(domain.tag, message)
            AuleLogger.Level.INFO -> Log.i(domain.tag, message)
            AuleLogger.Level.WARN -> Log.w(domain.tag, message, error)
            AuleLogger.Level.ERROR -> Log.e(domain.tag, message, error)
        }
    }
}
