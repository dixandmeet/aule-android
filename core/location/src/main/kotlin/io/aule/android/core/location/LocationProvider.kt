package io.aule.android.core.location

import kotlinx.coroutines.flow.StateFlow

/**
 * La seule porte vers la localisation.
 *
 * Derrière l'interface : [FusedLocationProvider] sur les Play Services, et
 * plus tard un `LocationManager` AOSP si un appareil n'a pas les services
 * Google. Aucun appelant ne doit le savoir.
 *
 * Deux réglages ne se négocient pas, hérités du proto iOS :
 *
 * **Aucun filtre de distance.** Un filtre rend le flux muet quand on ne
 * bouge pas, et on ne distingue alors plus « immobile » de « signal perdu ».
 * Or attendre un bus, c'est précisément être immobile.
 *
 * **Aucune mise en pause automatique.** Laisser le système « intelligemment »
 * suspendre les mises à jour le fait s'arrêter à un feu rouge — et ne
 * jamais repartir.
 */
interface LocationProvider {

    val authorization: StateFlow<LocationAuthorization>
    val lastFix: StateFlow<LocationFix?>

    /**
     * Le cap de l'appareil — où le téléphone **regarde**, en degrés depuis le
     * nord vrai. `null` quand rien ne répond : pas de magnétomètre, flux
     * arrêté, ou capteur déclaré inexploitable.
     *
     * Il vit ici et non dans [LocationFix] parce qu'il n'a pas la même
     * cadence : une position arrive une fois par seconde, un cap de boussole
     * cinquante. Le publier dans le flux ferait recomposer l'arbre Compose à
     * chaque frémissement du poignet — d'où cette propriété nue, qu'on **lit**
     * depuis le ticker caméra et qui n'émet rien (ADR-006).
     */
    val deviceHeadingDegrees: Double?

    /** Vrai dès qu'on a demandé le flux et qu'aucune position n'est encore arrivée. */
    val isAcquiring: StateFlow<Boolean>

    /** Renseigné quand le fournisseur renonce — pour dire *pourquoi* la carte ne suit pas. */
    val lastError: StateFlow<String?>

    /**
     * Démarre le flux, au palier [purpose].
     *
     * Sans autorisation, l'appel est un no-op : c'est l'écran qui demande
     * la permission, puis rappelle [refreshAuthorization] et [start].
     */
    fun start(purpose: LocationPurpose = LocationPurpose.READY)

    fun stop()

    /** Change ce que l'app est en train de faire, et donc ce que la localisation coûte. */
    fun setPurpose(purpose: LocationPurpose)

    /** Relit l'autorisation système. À appeler après un dialogue de permission. */
    fun refreshAuthorization()

    /**
     * Mémorise qu'on a posé la question. Sans ça, un refus se relirait comme
     * [LocationAuthorization.UNKNOWN] au prochain lancement, et on
     * redemanderait — ce que le contrat interdit.
     */
    fun markPermissionRequested()

    fun openSettings()
}
