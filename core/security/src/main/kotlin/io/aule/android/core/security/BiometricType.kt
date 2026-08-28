package io.aule.android.core.security

/**
 * De quoi parler à l'écran : « empreinte », ou « biométrie ».
 *
 * Deux valeurs seulement, là où Android connaît trois capteurs. C'est
 * volontaire : voir plus bas [BiometricTypeResolver], qui explique pourquoi on
 * ne peut pas honnêtement en distinguer davantage.
 */
enum class BiometricType {
    /** Le seul cas où l'on peut dire « empreinte » sans risquer de se tromper. */
    FINGERPRINT,

    /** Tout le reste : « biométrie », qui n'engage rien et n'est jamais faux. */
    GENERIC,
}

/**
 * Le mot à employer, déduit du matériel présent.
 *
 * ## Pourquoi cette fonction est si prudente
 *
 * Android n'expose **aucune** API publique disant ce qui est *enregistré* :
 * `PackageManager.hasSystemFeature` dit ce que l'appareil sait faire, pas ce
 * que son porteur a configuré. Un téléphone qui annonce un capteur d'empreinte
 * **et** la reconnaissance faciale ne dit nulle part lequel des deux
 * déverrouillera le dialogue — et ce sera celui que l'utilisateur a enrôlé,
 * qu'on ne peut pas connaître.
 *
 * D'où la règle : on ne dit « empreinte » que si c'est la **seule** chose que
 * l'appareil sache faire. Partout ailleurs, « biométrie ». Le mot générique est
 * un peu plus froid ; il a l'avantage de n'être jamais démenti par le dialogue
 * qui s'ouvre juste après.
 *
 * Fonction pure, et c'est ce qui la rend vérifiable : les trois booléens
 * arrivent de l'appelant, qui a le `PackageManager`. Elle se teste donc sur ses
 * huit combinaisons sans émulateur ni Robolectric.
 */
object BiometricTypeResolver {

    fun resolve(
        hasFingerprintFeature: Boolean,
        hasFaceFeature: Boolean,
        hasIrisFeature: Boolean,
    ): BiometricType = if (hasFingerprintFeature && !hasFaceFeature && !hasIrisFeature) {
        BiometricType.FINGERPRINT
    } else {
        BiometricType.GENERIC
    }
}
