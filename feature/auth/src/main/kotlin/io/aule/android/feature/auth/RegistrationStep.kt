package io.aule.android.feature.auth

/**
 * Les étapes de l'assistant, dans l'ordre Flutter
 * (`SAE/lib/screens/registration_screen.dart`).
 *
 * [storageName] est le `name` Dart : un brouillon persisté par Flutter se
 * relirait ici, et l'inverse.
 */
enum class RegistrationStep {
    WELCOME,
    PROFILE,
    NETWORK,
    IDENTITY,
    TRANSPORT_MODE,
    ACCOUNT,
    CONFIRMATION,
    ;

    val storageName: String
        get() = when (this) {
            WELCOME -> "welcome"
            PROFILE -> "profile"
            NETWORK -> "network"
            IDENTITY -> "identity"
            TRANSPORT_MODE -> "transportMode"
            ACCOUNT -> "account"
            CONFIRMATION -> "confirmation"
        }

    companion object {
        fun fromStorage(name: String?): RegistrationStep? =
            entries.find { it.storageName == name }
    }
}

enum class RegistrationNotice {
    CONFIRMATION_SENT,
    RATE_LIMITED,
    RESEND_FAILED,
}
