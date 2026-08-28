package io.aule.android.core.security

/**
 * Ce que l'appareil répond quand on lui demande s'il sait reconnaître son
 * porteur — en `BIOMETRIC_STRONG`, seul niveau que ce projet accepte.
 *
 * Les six cas ne se valent pas, et c'est tout l'intérêt de ne pas rendre un
 * booléen. Trois d'entre eux sont définitifs sur cet appareil ([NO_HARDWARE]),
 * ou le temps d'une panne ([HARDWARE_UNAVAILABLE]), ou franchement obscurs
 * ([UNKNOWN]) : dans ces cas la biométrie ne se propose pas, elle n'existe pas,
 * et un écran qui l'annoncerait pour ensuite échouer serait pire que le silence.
 *
 * [NONE_ENROLLED] est le seul qui appelle une **action** : le matériel est là,
 * rien n'y est enregistré, et le chemin honnête est d'ouvrir les réglages du
 * téléphone. C'est aussi pourquoi il ne se confond pas avec [NO_HARDWARE] —
 * « votre appareil ne sait pas faire » et « vous ne lui avez pas encore appris »
 * mènent au même écran vide et n'appellent pas la même phrase.
 */
enum class BiometricAvailability {
    /** Matériel présent, au moins une empreinte ou un visage enregistré. */
    READY,

    /** Aucun capteur — un vieil appareil, une tablette d'atelier. Définitif. */
    NO_HARDWARE,

    /** Capteur présent mais occupé ou en panne. Passager : on ne conclut rien. */
    HARDWARE_UNAVAILABLE,

    /** Capteur présent, rien d'enregistré. Le seul cas qui mène aux réglages. */
    NONE_ENROLLED,

    /**
     * Le système réclame une mise à jour de sécurité avant de rendre le capteur.
     * Rien à faire depuis l'application, et surtout rien à promettre.
     */
    SECURITY_UPDATE_REQUIRED,

    /** Ni oui ni non : une réponse qu'on ne sait pas lire se traite comme un non. */
    UNKNOWN,
    ;

    /** Vrai quand un dialogue peut réellement s'ouvrir maintenant. */
    val isReady: Boolean get() = this == READY

    /**
     * Vrai quand la biométrie mérite d'être **proposée**.
     *
     * [NONE_ENROLLED] en fait partie : c'est le cas « ouvrir les paramètres »,
     * pas une erreur. Le refuser ici priverait de la fonction quelqu'un qui n'a
     * simplement jamais posé son doigt sur le capteur de son téléphone neuf.
     */
    val isOfferable: Boolean get() = this == READY || this == NONE_ENROLLED
}
