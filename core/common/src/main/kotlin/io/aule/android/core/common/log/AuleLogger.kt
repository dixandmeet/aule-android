package io.aule.android.core.common.log

/**
 * Le journal de l'application, derrière une interface.
 *
 * L'interface existe pour deux raisons. La première est qu'un jour un rapporteur
 * de plantage se branchera ici, et qu'on ne veut pas relire tous les appels ce
 * jour-là. La seconde est qu'un test doit pouvoir vérifier qu'une panne a bien
 * été journalisée : un défaut qui se tait est un défaut qu'on cherche à la main.
 *
 * Ce qui ne s'écrit jamais dans un journal : un jeton, une clé, une adresse
 * e-mail, et une coordonnée précise ailleurs qu'en [Level.DEBUG].
 */
interface AuleLogger {

    fun log(level: Level, domain: LogDomain, message: String, error: Throwable? = null)

    fun debug(domain: LogDomain, message: String) = log(Level.DEBUG, domain, message)

    fun info(domain: LogDomain, message: String) = log(Level.INFO, domain, message)

    fun warn(domain: LogDomain, message: String, error: Throwable? = null) =
        log(Level.WARN, domain, message, error)

    fun error(domain: LogDomain, message: String, error: Throwable? = null) =
        log(Level.ERROR, domain, message, error)

    enum class Level { DEBUG, INFO, WARN, ERROR }
}

/**
 * Les domaines du journal.
 *
 * Ils servent au filtrage sur appareil : `adb logcat -s Aule.Map` isole la carte
 * sans le bruit du réseau. Un domaine par sous-système qu'on peut vouloir
 * observer seul — pas un par classe.
 *
 * Le point plutôt que le deux-points du proto iOS (`io.aule.native:map`) : `adb
 * logcat -s` lit `TAG:priorité`, et un deux-points dans le tag lui fait rejeter
 * l'expression entière. Trouvé au premier lancement sur le S21.
 */
enum class LogDomain(val tag: String) {
    APP("Aule.App"),
    MAP("Aule.Map"),
    NET("Aule.Net"),
    GPS("Aule.Gps"),
    AUTH("Aule.Auth"),
}

/** Un journal qui n'écrit rien. Pour les tests, et pour rien d'autre. */
object NoopLogger : AuleLogger {
    override fun log(level: AuleLogger.Level, domain: LogDomain, message: String, error: Throwable?) = Unit
}
