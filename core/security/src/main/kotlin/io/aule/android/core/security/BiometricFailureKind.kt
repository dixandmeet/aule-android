package io.aule.android.core.security

import androidx.biometric.BiometricPrompt

/**
 * Pourquoi le déverrouillage n'a pas eu lieu.
 *
 * Android en donne une quinzaine de codes numériques ; l'écran, lui, n'a que
 * quatre réactions possibles — se taire, orienter vers les réglages, dire
 * d'attendre, ou dire que c'est fini pour cette fois. Ces genres-là sont ce
 * qui reste des codes une fois cette question posée.
 *
 * **Aucune branche ne mène à une impasse.** Quel que soit le genre, le repli
 * est le même et il existe toujours : le formulaire de connexion. C'est la
 * règle qui tient toute la fonctionnalité — une biométrie qui refuse ne doit
 * jamais enfermer quelqu'un dehors, elle doit rendre la main au mot de passe.
 */
enum class BiometricFailureKind {
    /** Plus rien d'enregistré sur l'appareil : le seul cas qui mène aux réglages. */
    NOT_ENROLLED,

    /** Capteur absent, occupé ou en panne. */
    HARDWARE_UNAVAILABLE,

    /** Trop d'essais ratés : le capteur se rouvre tout seul dans trente secondes. */
    TEMPORARY_LOCKOUT,

    /** Trop d'essais ratés, définitivement : il faudra déverrouiller autrement. */
    PERMANENT_LOCKOUT,

    /**
     * L'utilisateur a fermé le dialogue, ou pris « Se connecter autrement ».
     *
     * Ce n'est pas une panne, et rien ne doit s'afficher : un bandeau rouge
     * pour un geste délibéré apprend surtout à ignorer les bandeaux rouges.
     */
    USER_CANCELED,

    /**
     * La clé du Keystore ne vaut plus rien — empreinte ajoutée ou retirée,
     * verrouillage d'écran remis à zéro.
     *
     * ⚠️ **Ce genre n'a pas de code Android.** Il ne vient jamais du dialogue :
     * il est levé par [BiometricKeyVault] au moment de fabriquer le `Cipher`,
     * donc **avant** que le dialogue s'affiche. Le chercher dans le rappel de
     * `BiometricPrompt` serait l'attendre à un endroit où il ne passe pas.
     */
    KEY_INVALIDATED,

    /** Un raté passager — temps écoulé, capteur qui n'a pas su lire. On réessaie. */
    TRANSIENT,

    /** Un code qu'on ne sait pas lire. Traité comme un refus, jamais comme un oui. */
    UNKNOWN,
}

/**
 * Le genre correspondant à un code d'erreur de `BiometricPrompt`.
 *
 * Fonction pure sur un `Int`, et c'est ce qui la rend vérifiable sur la JVM :
 * les `ERROR_*` d'androidx sont des constantes de compilation, donc les citer
 * n'appelle aucun code Android à l'exécution. La table entière se teste sans
 * appareil.
 */
fun biometricFailureKindOf(errorCode: Int): BiometricFailureKind = when (errorCode) {
    BiometricPrompt.ERROR_NO_BIOMETRICS -> BiometricFailureKind.NOT_ENROLLED

    // « Aucun code de verrouillage configuré » ne peut se produire que si l'on
    // avait demandé DEVICE_CREDENTIAL — ce que ce projet ne fait jamais. Mappé
    // quand même : un code jamais attendu qui tomberait dans `else` deviendrait
    // un « UNKNOWN » qu'on passerait des heures à comprendre.
    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> BiometricFailureKind.NOT_ENROLLED

    BiometricPrompt.ERROR_HW_NOT_PRESENT,
    BiometricPrompt.ERROR_HW_UNAVAILABLE,
    BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
    -> BiometricFailureKind.HARDWARE_UNAVAILABLE

    BiometricPrompt.ERROR_LOCKOUT -> BiometricFailureKind.TEMPORARY_LOCKOUT
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricFailureKind.PERMANENT_LOCKOUT

    // Les trois façons de dire non. `ERROR_CANCELED` vient du système et non du
    // doigt — écran éteint, appel entrant, changement d'utilisateur — mais il
    // appelle exactement la même suite : rendre la main sans rien afficher.
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED,
    -> BiometricFailureKind.USER_CANCELED

    BiometricPrompt.ERROR_TIMEOUT,
    BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
    BiometricPrompt.ERROR_NO_SPACE,
    -> BiometricFailureKind.TRANSIENT

    // `ERROR_VENDOR` est le fourre-tour des constructeurs : Samsung y met ce que
    // l'AOSP ne prévoit pas. Sur l'appareil de référence, aucun sens fiable à en
    // tirer — donc un refus, comme tout ce qu'on ne comprend pas.
    else -> BiometricFailureKind.UNKNOWN
}
