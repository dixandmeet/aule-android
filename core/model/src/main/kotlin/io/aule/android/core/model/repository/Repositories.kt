package io.aule.android.core.model.repository

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.AppearanceMode
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.Depot
import io.aule.android.core.model.DriverProfile
import io.aule.android.core.model.DriverProfileUpdate
import io.aule.android.core.model.DriverReport
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.GpsTracePoint
import io.aule.android.core.model.HandoverAlertPrefs
import io.aule.android.core.model.HandoverEngagement
import io.aule.android.core.model.HandoverSummary
import io.aule.android.core.model.HandoverTarget
import io.aule.android.core.model.HandoverTrack
import io.aule.android.core.model.LineJourney
import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.PositionPublishRequest
import io.aule.android.core.model.ProRegistrationDraft
import io.aule.android.core.model.ScheduledTrip
import io.aule.android.core.model.ServiceHeartbeat
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.ServiceStartRequest
import io.aule.android.core.model.TransportNetwork
import io.aule.android.core.model.Place
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.RoadManeuver
import io.aule.android.core.model.RouteMode
import io.aule.android.core.model.RoutePlan
import io.aule.android.core.model.RoutePreferences
import java.time.Instant

/**
 * Les contrats d'accès aux données.
 *
 * Ils vivent avec les modèles, et non dans le module qui les implémente : c'est
 * ce qui permet à un écran de dépendre de `:core:model` sans jamais voir OkHttp
 * ni Supabase. Un `@Composable` ne peut donc pas atteindre le réseau — ce n'est
 * pas une règle de revue, c'est une erreur de compilation.
 *
 * Toutes ces fonctions **lèvent** en cas de panne. Aucune ne rend une liste vide
 * pour masquer un incident : côté Flutter, l'inverse avait produit une carte
 * d'apparence normale, sans véhicules et sans message, pendant une coupure.
 */

interface VehicleRepository {
    suspend fun vehicles(around: Coordinate, radiusMeters: Double, limit: Int): FleetSnapshot
}

interface StopRepository {
    suspend fun allStops(): List<TransitStop>

    /**
     * Les passages annoncés à un lieu.
     *
     * Ne lève pas sur 404 ni 502 : ces deux cas sont des **résultats**, portés par
     * [StopDepartures.outcome]. Ils mènent au même écran vide mais n'appellent pas
     * la même réaction, et c'est l'appelant qui doit pouvoir les distinguer.
     */
    suspend fun departures(atStopNamed: String): StopDepartures

    suspend fun servingLines(atStopNamed: String): List<ServingLine>
}

/**
 * Le nuancier des lignes, tel que le catalogue GTFS le publie.
 *
 * À part du reste parce que la donnée est ailleurs : le flux de flotte
 * n'annonce qu'un `route_id`, et la couleur d'une ligne vit dans le catalogue.
 * Une seule requête au démarrage suffit — un nuancier change à la fréquence
 * d'un GTFS, pas d'un sondage.
 */
interface LinePaletteRepository {
    suspend fun palette(): LinePalette
}

interface PlaceSearchRepository {
    suspend fun search(query: String): List<Place>
}

interface RoutingRepository {
    /**
     * Un itinéraire de [from] vers [to].
     *
     * Lève en cas de panne, y compris sur 404 : « aucun arrêt à proximité »
     * est une information, et l'écran doit pouvoir la dire. Inverser
     * `lng,lat` produit exactement ce 404 — voir `docs/CONTRAT-BFF.md`.
     */
    suspend fun plan(
        mode: RouteMode,
        from: Coordinate,
        to: Coordinate,
        preferences: RoutePreferences = RoutePreferences(),
        departureAt: Instant? = null,
        arriveBy: Boolean = false,
    ): RoutePlan
}

enum class RoadProfile {
    CAR,
    PEDESTRIAN,
    ;

    val osrmPath: String
        get() = if (this == PEDESTRIAN) "walking" else "driving"
}

data class RoadRoute(
    val points: List<Coordinate>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val maneuvers: List<RoadManeuver> = emptyList(),
)

/**
 * Un routeur de voirie — OSRM public aujourd'hui.
 *
 * Rend `null` en cas d'échec : l'écran retombe sur le libellé de la jambe,
 * et ce silence n'est pas une panne. Contrairement aux autres contrats,
 * lever ici forcerait un bandeau d'erreur pour un complément optionnel.
 */
interface RoadRouter {
    suspend fun route(
        from: Coordinate,
        to: Coordinate,
        profile: RoadProfile,
    ): RoadRoute?
}

/**
 * Auth e-mail + mot de passe — GoTrue / Supabase.
 *
 * Lève [io.aule.android.core.model.AuthException] sur un refus métier.
 * Une panne réseau lève aussi : l'écran doit pouvoir la dire.
 */
interface AuthRepository {
    /** La session courante, ou `null` si personne n'est connecté. */
    fun currentSession(): AuthSession?

    /**
     * Relit le dépôt local, et rafraîchit le jeton s'il a expiré.
     *
     * À appeler une fois au démarrage, avant de choisir entre la carte
     * et l'écran de connexion.
     */
    suspend fun restore(): AuthSession?

    suspend fun signIn(email: String, password: String): AuthSession

    suspend fun signOut()

    /**
     * Le rôle porté par `user_profiles.role`.
     *
     * `null` si la ligne n'existe pas : ce n'est pas une panne, c'est un
     * compte sans habilitation staff, et la résolution d'accès décide alors
     * d'après la fiche `drivers`. Une panne réseau, elle, **lève**.
     */
    suspend fun fetchStaffRole(session: AuthSession): String?

    /**
     * Crée le compte GoTrue avec les métadonnées d'onboarding v2.
     *
     * Ne persiste **pas** de session : la confirmation d'e-mail (PKCE) ouvre
     * la session plus tard. Lève [io.aule.android.core.model.AuthException].
     */
    suspend fun signUpProfessional(draft: ProRegistrationDraft, password: String)

    /** Renvoie l'e-mail de confirmation, avec le même redirect PKCE. */
    suspend fun resendSignupConfirmation(email: String)

    /**
     * Échange le `code` du deep link `io.aule.pro://login-callback/` contre
     * une session, grâce au vérifieur PKCE conservé depuis l'inscription.
     */
    suspend fun exchangeAuthCode(code: String): AuthSession

    /**
     * Suppression définitive : RPC `delete_my_account`, puis fermeture de
     * session. Un échec réseau **ne** ferme **pas** la session — l'écran
     * peut réessayer (`SAE/test/widget_test.dart`).
     */
    suspend fun deleteAccount()
}

/**
 * Le vérifieur PKCE de l'inscription, distinct de la session.
 *
 * Il doit survivre à la mort du processus : le lien de confirmation arrive
 * souvent après que l'app a été tuée. `signOut` ne l'efface pas.
 */
interface AuthPkceStore {
    suspend fun writeVerifier(verifier: String)
    suspend fun readVerifier(): String?
    suspend fun clearVerifier()
}

/**
 * Le brouillon d'inscription, sans le mot de passe.
 *
 * JSON + nom d'étape, tels que [io.aule.android.core.model.ProRegistrationDraft.encode]
 * les produit. L'implémentation Android vit dans `:app`.
 */
interface RegistrationDraftStore {
    suspend fun readDraft(): String?
    suspend fun readStep(): String?
    suspend fun write(draftJson: String, step: String)
    suspend fun clear()
}

/**
 * Le mode d'apparence, lu de façon synchrone pour éviter un flash au
 * démarrage — comme `ThemeService.load` avant `runApp`.
 */
interface AppearanceStore {
    fun read(): AppearanceMode
    fun write(mode: AppearanceMode)
}

/**
 * Seuils d'alerte de relève et lignes récemment relevées, persistés localement.
 *
 * Clés alignées sur Flutter (`sae.handover.alerts`, `sae.handover.recent_lines`).
 * L'implémentation Android vit dans `:app` ; les tests utilisent une mémoire.
 */
interface HandoverAlertPrefsStore {
    fun read(): HandoverAlertPrefs
    fun write(prefs: HandoverAlertPrefs)

    /** Lignes récemment relevées, la plus récente en tête (au plus 4). */
    fun readRecentLines(): List<String>

    /** Place [lineId] en tête et tronque à 4 entrées. */
    fun pushRecentLine(lineId: String): List<String>
}

/**
 * Les fichiers CSV de diagnostic GPS, sur l'appareil.
 *
 * Désactivé en production (`GpsTraceService.enabled`). L'écran profil les
 * liste, les exporte et les purge ; le guidage les écrit.
 */
data class GpsTraceFile(
    val name: String,
    val path: String,
    val bytes: Long,
)

/**
 * Les traces de guidage : les lister, les effacer, et en ouvrir une.
 *
 * L'écriture passe par un [GpsTraceRecorder] plutôt que par un `record()` sur
 * le catalogue, parce qu'une trace a un **début et une fin** — ceux du
 * guidage. Un catalogue qui accepterait des points au fil de l'eau devrait
 * deviner tout seul quand refermer le fichier, et il devinerait mal.
 */
interface GpsTraceCatalog {
    val enabled: Boolean
    suspend fun list(): List<GpsTraceFile>
    suspend fun deleteAll()

    /**
     * Ouvre une trace, ou rend `null` quand l'enregistrement est coupé.
     *
     * `null` plutôt qu'un enregistreur qui n'écrit rien : l'appelant n'a alors
     * rien à porter, et le cas « désactivé » se voit à la lecture du code
     * plutôt qu'au fond d'une implémentation.
     */
    fun startRecording(): GpsTraceRecorder?
}

/**
 * Une trace ouverte, le temps d'un guidage.
 *
 * [record] ne suspend pas : il est appelé depuis la boucle de guidage, une
 * fois par seconde, et cette boucle n'a pas à attendre un disque. Le fichier
 * n'existe qu'à partir du premier point — un guidage arrêté aussitôt ne laisse
 * pas un fichier vide derrière lui.
 */
interface GpsTraceRecorder {
    fun record(point: GpsTracePoint)

    /** Vide ce qui reste et referme. Après quoi la trace apparaît dans [GpsTraceCatalog.list]. */
    suspend fun close()
}

/**
 * Où la session survit aux redémarrages.
 *
 * L'implémentation Android vit dans `:app` (SharedPreferences) ; les tests
 * utilisent une mémoire. Le repository ne connaît que ce contrat.
 */
interface AuthSessionStore {
    suspend fun read(): AuthSession?
    suspend fun write(session: AuthSession)
    suspend fun clear()
}

/**
 * La fiche agent — PostgREST / table `drivers`.
 *
 * Lève en cas de panne. Une fiche absente n'est pas une panne : [fetchProfile]
 * rend `null`, et l'écran dit alors qu'il n'y a rien à afficher. Confondre les
 * deux ferait disparaître un agent derrière un bandeau réseau, ou l'inverse.
 *
 * La session est un argument, pas une lecture cachée : le repository n'a pas
 * à connaître le dépôt de jetons, et un test lui passe une session inventée.
 */
interface DriverProfileRepository {
    suspend fun fetchProfile(session: AuthSession): DriverProfile?

    suspend fun fetchDepots(session: AuthSession): List<Depot>

    suspend fun fetchNetworks(session: AuthSession): List<TransportNetwork>

    /**
     * Enregistre la fiche. Rend la ligne telle que la base la voit après
     * écriture — c'est elle qui devient la référence, pas la saisie.
     */
    suspend fun updateProfile(
        session: AuthSession,
        driverId: String,
        update: DriverProfileUpdate,
    ): DriverProfile

    /**
     * Envoie la photo vers Storage (`driver-avatars/{uid}/avatar.{ext}`)
     * puis écrit `drivers.avatar_url`. Le chemin doit commencer par
     * `auth.uid()` — c'est la politique RLS `driver_avatars_*_own`.
     *
     * Lève [io.aule.android.core.model.AvatarException].
     */
    suspend fun uploadAvatar(
        session: AuthSession,
        driverId: String,
        bytes: ByteArray,
        contentType: String,
        extension: String,
    ): DriverProfile

    /**
     * Retire les variantes Storage et vide `drivers.avatar_url`.
     *
     * Lève [io.aule.android.core.model.AvatarException].
     */
    suspend fun removeAvatar(
        session: AuthSession,
        driverId: String,
    ): DriverProfile

    /**
     * Les octets de la photo publique.
     *
     * `null` si l'URL est vide ou illisible : le portrait retombe alors
     * sur les initiales, ce n'est pas une panne de fiche.
     */
    suspend fun fetchAvatarImage(url: String): ByteArray?
}

/**
 * Les signalements de terrain — table `driver_reports`.
 *
 * L'identifiant du conducteur est résolu ici, pas reçu : la RLS n'accepte
 * que `driver_id = current_driver_id()`, et le laisser à l'appelant
 * promettrait plus que la base ne permet.
 *
 * Lève [io.aule.android.core.model.DriverReportException].
 */
interface DriverReportRepository {
    suspend fun submit(
        session: AuthSession,
        report: DriverReport,
        driverServiceId: String? = null,
        vehicleId: String? = null,
    )
}

/**
 * Prise et clôture de service — table `driver_services` et RPC
 * `driver_service_start`.
 *
 * Lève [io.aule.android.core.model.DriverServiceException].
 */
interface DriverServiceRepository {
    suspend fun fetchLines(session: AuthSession): List<ServiceLine>

    /**
     * La desserte d'une ligne dans un sens : le plus long circuit du jour,
     * pour proposer tous les arrêts réels. Sans tracé : le repli de relève
     * n'en a pas besoin.
     */
    suspend fun fetchJourney(
        session: AuthSession,
        lineId: String,
        directionId: Int,
    ): LineJourney

    /**
     * Course GTFS du jour la plus proche de [near] : profils, départs actifs
     * et desserte horodatée. `null` si aucune course ne colle (pas une panne).
     */
    suspend fun nearestActiveTrip(
        session: AuthSession,
        lineId: String,
        directionId: Int,
        destinationHint: String?,
        near: Coordinate,
        at: Instant = Instant.now(),
    ): ScheduledTrip?

    suspend fun fetchActiveService(session: AuthSession): ActiveDriverService?

    suspend fun startService(
        session: AuthSession,
        request: ServiceStartRequest,
    ): ActiveDriverService

    suspend fun endService(session: AuthSession, serviceId: String)

    /**
     * Publie la position et rend l'état serveur.
     *
     * C'est par cette réponse — pas par une notification — que le
     * conducteur sortant apprend qu'une relève a été engagée puis
     * qu'elle a abouti. Ne pas appeler [endService] après une relève
     * réussie : `handover_confirm` a déjà soldé le service.
     */
    suspend fun publishPosition(
        session: AuthSession,
        request: PositionPublishRequest,
    ): ServiceHeartbeat
}

/**
 * Relève d'un collègue — table `service_handovers` et RPC `handover_*`.
 *
 * `handover_lookup` est volontairement muet : la position n'arrive qu'après
 * l'engagement, par `handover_track`. Un collègue n'apparaît au lookup que
 * s'il a publié une position dans les quinze dernières minutes. Lève
 * [io.aule.android.core.model.HandoverException].
 */
interface HandoverRepository {
    suspend fun lookup(
        session: AuthSession,
        lineId: String,
        query: String,
    ): List<HandoverTarget>

    suspend fun request(
        session: AuthSession,
        outgoingServiceId: String,
    ): HandoverSummary

    suspend fun track(session: AuthSession, handoverId: String): HandoverTrack

    suspend fun setStop(
        session: AuthSession,
        handoverId: String,
        stopId: String,
        stopName: String,
        latitude: Double,
        longitude: Double,
        plannedAt: Instant? = null,
    ): HandoverSummary

    suspend fun confirm(session: AuthSession, handoverId: String): HandoverSummary

    suspend fun cancel(
        session: AuthSession,
        handoverId: String,
        reason: String? = null,
    ): HandoverSummary?

    suspend fun activeForMe(session: AuthSession): HandoverEngagement?
}
