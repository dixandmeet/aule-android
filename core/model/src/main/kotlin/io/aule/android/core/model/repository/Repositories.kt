package io.aule.android.core.model.repository

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.AgentAccess
import io.aule.android.core.model.AppearanceMode
import io.aule.android.core.model.AuthPkceFlow
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.HubBootstrap
import io.aule.android.core.model.HubChannel
import io.aule.android.core.model.HubColleague
import io.aule.android.core.model.HubException
import io.aule.android.core.model.HubFile
import io.aule.android.core.model.HubFileKind
import io.aule.android.core.model.HubMember
import io.aule.android.core.model.HubMessage
import io.aule.android.core.model.HubMessagePage
import io.aule.android.core.model.HubPendingMessage
import io.aule.android.core.model.HubUnread
import io.aule.android.core.model.OAuthProvider
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
import io.aule.android.core.model.ServiceNote
import io.aule.android.core.model.ServiceStartRequest
import io.aule.android.core.model.TransportNetwork
import io.aule.android.core.model.Place
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.Timetable
import io.aule.android.core.model.TransitLine
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.RoadManeuver
import io.aule.android.core.model.RouteMode
import io.aule.android.core.model.RoutePlan
import io.aule.android.core.model.RoutePreferences
import io.aule.android.core.model.SavedPlace
import java.time.Instant
import java.time.LocalDate

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
 * La grille horaire théorique — catalogue GTFS, via PostgREST.
 *
 * À part de [StopRepository], et pas par goût de la symétrie : ce n'est ni la
 * même source (le catalogue, pas le flux temps réel), ni la même fraîcheur (une
 * grille change à la fréquence d'un GTFS), ni le même droit d'accès — les
 * tables GTFS exigent une session, comme tout ce qui vit dans Supabase.
 *
 * **Lève** en cas de panne, comme les autres contrats. Une journée sans passage
 * n'est pas une panne : c'est une grille vide, et l'écran doit pouvoir dire la
 * différence entre « rien ne circule ce jour-là » et « on n'a pas pu demander ».
 */
interface TimetableRepository {
    suspend fun timetable(
        session: AuthSession,
        stopName: String,
        line: String,
        destination: String,
        date: LocalDate,
    ): Timetable
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
     *
     * ⚠️ **Elle lève un [io.aule.android.core.model.AuthException] de genre
     * [io.aule.android.core.model.AuthFailureKind.NETWORK] quand la question
     * n'a pas pu être posée** — transport coupé, 5xx, 429. C'est la seule
     * façon pour l'écran, qui ne voit pas la couche réseau, de distinguer
     * « le serveur dit non » de « je n'ai pas pu demander ». Les confondre
     * faisait perdre son habilitation à un conducteur garé en sous-sol.
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
     * Prépare une inscription par fournisseur externe et rend l'URL à ouvrir
     * dans le navigateur.
     *
     * Rien ne part sur le réseau ici : GoTrue ne demande qu'une URL bien formée,
     * et c'est le navigateur qui la suit. Ce que fait cet appel, c'est écrire le
     * vérifieur PKCE marqué [io.aule.android.core.model.AuthPkceFlow.OAUTH_SIGN_UP]
     * — sans quoi le retour ne pourrait pas être échangé, et surtout ne saurait
     * pas qu'il doit poser les métadonnées d'onboarding.
     *
     * L'écran ouvre l'URL dans un onglet du navigateur, **jamais** dans une
     * WebView : Google refuse d'y authentifier (RFC 8252, `disallowed_useragent`),
     * et une WebView verrait passer le mot de passe du compte Google.
     *
     * Le brouillon doit être complet ([ProRegistrationDraft.professionalDataComplete])
     * avant l'appel : c'est lui qu'on posera au retour, et un brouillon
     * incomplet donnerait un compte sans demande d'habilitation.
     */
    suspend fun beginOAuthSignUp(provider: OAuthProvider): String

    /**
     * Envoie le lien « mot de passe oublié », avec le même redirect PKCE que
     * l'inscription.
     *
     * **Ne dit pas si le compte existe** : GoTrue répond 200 pour une adresse
     * inconnue, et c'est ce qui empêche de découvrir qui est inscrit. L'écran
     * annonce donc « si un compte existe », jamais « c'est envoyé ».
     */
    suspend fun sendPasswordRecovery(email: String)

    /**
     * Pose un nouveau mot de passe sur la session ouverte.
     *
     * N'a de sens que derrière un lien de récupération ([AuthPkceFlow.RECOVERY])
     * ou depuis un compte déjà connecté : dans les deux cas c'est le jeton de la
     * session qui autorise, pas l'ancien mot de passe — GoTrue ne le redemande pas.
     */
    suspend fun updatePassword(newPassword: String)

    /**
     * Échange le `code` du deep link `io.aule.pro://login-callback/` contre
     * une session, grâce au vérifieur PKCE conservé depuis l'inscription.
     */
    suspend fun exchangeAuthCode(code: String): AuthSession

    /**
     * Ce que l'échange PKCE en attente était venu faire, ou `null` si aucun
     * n'attend.
     *
     * À lire **avant** [exchangeAuthCode], qui consomme le vérifieur et efface
     * du même geste le genre.
     */
    suspend fun pendingAuthFlow(): AuthPkceFlow?

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
 *
 * Le genre ([AuthPkceFlow]) s'écrit **avec** le vérifieur, d'un seul geste : il
 * dit ce que le lien attendu a le droit d'ouvrir, et deux écritures séparées
 * laisseraient une fenêtre où le vérifieur existe sans son genre.
 */
interface AuthPkceStore {
    suspend fun writeVerifier(verifier: String, flow: AuthPkceFlow = AuthPkceFlow.SIGN_UP)
    suspend fun readVerifier(): String?

    /** Le genre écrit avec le vérifieur courant, ou `null` si aucun n'attend. */
    suspend fun readFlow(): AuthPkceFlow?

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
 * Les huit dernières destinations retenues, la plus récente en tête.
 *
 * **Ce qui est retenu, c'est le lieu choisi, pas la frappe.** Garder les
 * requêtes tapées remplirait la liste de « beau », « beauj », « beaujoi » — les
 * états intermédiaires d'une seule recherche — et un historique qui répète la
 * même destination sous trois orthographes n'aide personne. Un lieu n'entre donc
 * ici qu'au moment où on le retient.
 *
 * Lu de façon synchrone comme [AppearanceStore] : la barre de recherche affiche
 * l'historique à l'instant où elle s'ouvre, et un aller-retour asynchrone la
 * ferait s'ouvrir vide avant de se remplir sous le doigt.
 *
 * L'implémentation Android vit dans `:app` ; les tests utilisent une mémoire.
 */
interface SearchHistoryStore {
    fun read(): List<Place>

    /** Place [place] en tête, sans doublon, et tronque à huit entrées. */
    fun remember(place: Place): List<Place>

    fun clear()
}

/**
 * Les adresses favorites, **sur l'appareil**.
 *
 * Distinct de [SearchHistoryStore], et ce n'est pas une symétrie de plus : un
 * historique tourne, un favori ne tourne pas. Le domicile disparaîtrait des huit
 * dernières destinations dès la cinquième course, alors que c'est précisément
 * celui qu'on veut toucher sans lire.
 *
 * Lu de façon **synchrone**, comme [AppearanceStore] et [SearchHistoryStore] :
 * la recherche montre ses raccourcis à l'instant où elle s'ouvre, et un
 * aller-retour asynchrone la ferait s'ouvrir vide avant de se remplir sous le
 * doigt.
 *
 * Il porte les pierres tombales telles quelles : c'est
 * [io.aule.android.core.model.mergeSavedPlaces] qui décide ce qui vit, pas le
 * dépôt. L'implémentation Android vit dans `:app` ; les tests utilisent une
 * mémoire.
 */
interface SavedPlacesStore {
    /**
     * Tout ce qui est écrit pour ce compte, suppressions comprises.
     *
     * ⚠️ **Le propriétaire fait partie de la clé, il n'est pas une commodité.**
     * Un dépôt commun à tous les comptes ferait pousser le domicile du premier
     * agent sur le compte du second dès qu'un téléphone de service change de
     * main — et l'afficherait dans sa recherche en attendant. Ce n'est pas un
     * cas d'école : un poste de conduite se partage.
     *
     * `null` quand personne n'est connecté. La carte vit derrière une session,
     * donc cela ne se produit qu'avant l'ouverture — et il n'y a alors rien à
     * lire. Hors ligne, la session est restaurée du disque : les favoris sont
     * bien là sans réseau.
     */
    fun read(owner: String?): List<SavedPlace>

    fun write(owner: String?, places: List<SavedPlace>)
}

/**
 * Les adresses favorites, **sur le compte** — table `user_saved_places`.
 *
 * C'est ce qui permet de retrouver son domicile après avoir changé de
 * téléphone. La source de vérité reste l'appareil : le réseau rattrape, il ne
 * commande pas. Un client qui attendrait le serveur pour afficher « Domicile »
 * ouvrirait sur une liste vide dans un parking souterrain.
 *
 * **Lève** en cas de panne, comme les autres contrats réseau. C'est l'appelant
 * qui décide qu'une synchronisation ratée n'est pas un incident à montrer :
 * les favoris locaux, eux, sont déjà à l'écran.
 */
interface SavedPlaceRepository {
    suspend fun fetch(session: AuthSession): List<SavedPlace>

    /**
     * Écrit ces favoris sur le compte — pierres tombales comprises, c'est par
     * elles que l'autre appareil apprendra une suppression.
     *
     * Un upsert sur `(user_id, id)` : rejouer la même poussée ne crée pas de
     * doublon, ce qui rend l'appel sûr à répéter après une coupure.
     */
    suspend fun push(session: AuthSession, places: List<SavedPlace>)
}

/**
 * Un fichier de cache, sans connaître Android.
 *
 * Même raison qu'[AssetBytes] : `:data` est du JVM pur, et le dossier de cache
 * lui arrive par ce contrat plutôt que par un `Context`. L'implémentation vit
 * dans `:app`, sur `cacheDir` — ce que le système peut vider quand la place
 * manque, ce qui est exactement le bon endroit pour une donnée retéléchargeable.
 *
 * Rien ne lève : un cache est une optimisation, et une optimisation qui fait
 * planter est un défaut net.
 */
interface CacheStore {
    /** Le contenu, ou `null` s'il n'y en a pas — ou s'il est illisible. */
    fun read(name: String): String?

    /** Écrit, ou ne fait rien si le disque refuse. */
    fun write(name: String, content: String)

    fun clear(name: String)
}

/**
 * L'inventaire des lignes du réseau — **hors ligne, sans requête**.
 *
 * Il vient de `assets/tiles/transit-lines-index.json`, 23 Ko embarqués. C'est le
 * seul endroit de l'application qui sache *quelles lignes existent* : le
 * catalogue d'arrêts dit ce qui est desservi, la flotte dit ce qui roule.
 *
 * **Ne lève pas.** Un index absent ou illisible rend une liste vide : sans lui
 * les badges restent gris et lisibles, là où une exception au premier véhicule
 * peint viderait la carte. Le contrat est donc plus faible que celui des dépôts
 * réseau, et c'est délibéré.
 */
interface NetworkLineRepository {
    /** Toutes les lignes, dans l'ordre du fichier. Lu une fois, gardé ensuite. */
    suspend fun allLines(): List<TransitLine>

    /**
     * La ligne portant cet indice, ou `null`. La recherche est canonique — « c6 »
     * et « C6 » désignent la même ligne.
     */
    suspend fun line(named: String): TransitLine?
}

/**
 * De quoi lire un asset, sans connaître Android.
 *
 * `:data` est un module **JVM pur** : il n'a pas de `Context`, et lui en donner
 * un pour trois fichiers embarqués le rendrait non testable sans émulateur. Il
 * reçoit donc ses octets par ce contrat, dont l'implémentation vit dans `:app` —
 * même discipline que les dépôts de préférences.
 *
 * `null` quand l'asset n'existe pas : c'est une réponse, pas une panne, et
 * l'appelant décide ce qu'elle vaut.
 */
interface AssetBytes {
    /** @param path chemin relatif au dossier `assets`, par exemple `tiles/x.json`. */
    fun readText(path: String): String?
}

/**
 * Les réglages du Guet, persistés.
 *
 * Le type qu'il porte vit dans `:core:guet` — module pur — et son encodage aussi :
 * ce dépôt ne sait que ranger et relire **une chaîne**, comme celui de la relève.
 * C'est ce qui permet de vérifier la tolérance du décodage sans disque, et c'est
 * cette tolérance qui compte : un décodage strict éteindrait le Guet en silence
 * chez quelqu'un qui vient de mettre à jour.
 *
 * Lu de façon synchrone : le moteur les relit à chaque calcul, et un aller-retour
 * asynchrone par sondage coûterait plus que la lecture qu'il évite.
 */
interface GuetPreferencesStore {
    /** Le JSON tel qu'il a été écrit, ou `null` si rien n'a jamais été enregistré. */
    fun read(): String?

    fun write(encoded: String)
}

/**
 * L'accueil a-t-il déjà été vu.
 *
 * **Distinct de « la permission a été demandée »**, que porte déjà
 * `LocationProvider`. Les deux se ressemblent et ne disent pas la même chose :
 * qui a répondu « Continuer sans ma position » n'a jamais vu le dialogue
 * système, et le compter comme une demande faite ferait afficher « la
 * localisation est refusée » à quelqu'un à qui on n'a rien demandé.
 *
 * Lu de façon synchrone comme [AppearanceStore] : c'est la première image du
 * lancement qui en dépend, et un aller-retour asynchrone ferait apparaître la
 * carte avant que l'accueil la recouvre.
 */
interface WelcomeStore {
    fun hasSeenWelcome(): Boolean
    fun markWelcomeSeen()
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

    /**
     * Referme la trace. Ne suspend pas, **et c'est le contrat qui compte** :
     * un guidage se termine aussi quand l'écran disparaît, et à cet instant il
     * n'y a plus de portée de coroutine pour attendre quoi que ce soit. Une
     * fermeture qui aurait exigé d'attendre n'aurait tout simplement jamais eu
     * lieu dans ce cas-là — et c'est le cas où l'on tient le plus à son
     * fichier.
     */
    fun close()
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
 * La dernière habilitation **accordée**, gardée sur l'appareil.
 *
 * ## Pourquoi elle existe
 *
 * L'accès à Aule Pro se décide en interrogeant `user_profiles` et la fiche
 * `drivers`. Sans réseau, la question ne peut pas être posée — et la seule
 * réponse honnête n'est ni « oui » ni « non », c'est « je ne sais pas ».
 * Traiter ce silence comme un refus fermait la session d'un conducteur garé
 * en sous-sol ; le traiter comme un accord ouvrirait l'application à
 * n'importe qui en mode avion.
 *
 * On garde donc ce que le serveur a **déjà accordé à ce compte, sur cet
 * appareil**. C'est la même règle que les favoris (ADR-012) : le local d'abord,
 * le compte rattrape. Un compte jamais vérifié ici n'a rien en réserve et reste
 * dehors — la porte ne s'ouvre pas sur une absence de donnée.
 *
 * L'habilitation est rangée **par identifiant d'utilisateur** : deux comptes
 * sur le même téléphone ne se prêtent pas leurs droits.
 */
interface AgentAccessStore {
    /** Ce que le serveur avait accordé à [userId], ou `null` s'il n'a jamais répondu ici. */
    suspend fun read(userId: String): AgentAccess?

    suspend fun write(userId: String, access: AgentAccess)

    /** Efface tout. Appelé sur une déconnexion et sur un refus explicite. */
    suspend fun clear()
}

/**
 * Le secret scellé par la clé biométrique du Keystore.
 *
 * Ce n'est **pas** le jeton de session : c'est un marqueur opaque, tiré au sort
 * à l'activation, qui ne vaut rien pour qui le lirait. Ce qui compte n'est pas
 * son contenu mais le fait de pouvoir le **rouvrir** — le déchiffrer exige un
 * `Cipher` que le Keystore ne délivre qu'après une empreinte reconnue, et c'est
 * cette impossibilité matérielle qui fait la preuve. Un `BiometricPrompt` sans
 * chiffrement rendrait, lui, un simple booléen « c'est bon », que rien
 * n'empêche de contourner sur un appareil ouvert.
 */
data class BiometricEnrollment(val cipherText: String, val iv: String)

/**
 * L'activation de la biométrie, **par compte**.
 *
 * ## Ce que ce contrat garde, et ce qu'il ne garde pas
 *
 * La biométrie d'Aule est un **verrou local devant une session qui existe
 * déjà**, jamais un second système d'authentification : Supabase reste seul à
 * dire qui entre, et l'empreinte ne fait qu'autoriser la restauration d'une
 * session déjà valide. D'où un fichier distinct d'[AuthSessionStore] et
 * d'[AgentAccessStore] — effacer l'un ne doit pas effacer les autres par
 * mégarde.
 *
 * ## Pourquoi l'identifiant garde la lecture
 *
 * ⚠️ **Un poste de conduite se partage**, et c'est la même raison qui range les
 * favoris par propriétaire ([SavedPlacesStore]) et l'habilitation par compte
 * ([AgentAccessStore]). Fermer l'application n'est pas se déconnecter : un
 * conducteur qui rend le téléphone en fin de service laisse derrière lui une
 * session ouverte et, s'il l'avait activée, une empreinte qui la déverrouille.
 *
 * Ce qui protège le suivant n'est donc pas l'impossibilité de changer de
 * compte — il n'y en a aucune — mais cette garde-ci : [read] ne rend rien si
 * l'entrée stockée n'appartient pas à [userId]. Le collègue qui reprend
 * l'appareil peut refuser le dialogue, se connecter normalement, et l'empreinte
 * du précédent ne lui ouvrira jamais rien. L'entrée orpheline, elle, sera
 * balayée à la première déconnexion explicite.
 *
 * ## Pourquoi la proposition survit à [clear]
 *
 * [hasBeenOffered] et [markOffered] vivent à part et **survivent** à [clear] :
 * qui a désactivé la biométrie dans les réglages ne doit pas se la voir
 * reproposer à la connexion suivante. Une proposition qu'on a déjà déclinée et
 * qui revient n'est plus une proposition, c'est une insistance.
 */
interface BiometricEnrollmentStore {
    /** Le secret scellé pour [userId], ou `null` — y compris s'il appartient à un autre compte. */
    suspend fun read(userId: String): BiometricEnrollment?

    suspend fun write(userId: String, enrollment: BiometricEnrollment)

    /**
     * Efface l'activation, **sans** toucher à [hasBeenOffered].
     *
     * Appelé sur une déconnexion, un refus d'habilitation, une suppression de
     * compte, une clé invalidée, et sur une session absente au démarrage.
     */
    suspend fun clear()

    suspend fun hasBeenOffered(userId: String): Boolean

    suspend fun markOffered(userId: String)
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
     * Le parcours de **référence** d'une ligne dans un sens.
     *
     * C'est le premier de [fetchJourneyProfiles] : celui qui décrit le trajet
     * ordinaire. La relève n'a pas de branche à proposer — elle cherche un
     * point où se relayer, pas un choix à offrir.
     *
     * @param expectedTerminus le terminus que le référentiel **annonce** pour ce
     *   sens — [ServiceDirection.terminus]. Vide quand l'appelant ne l'a pas.
     *   Voir [fetchJourneyProfiles] : c'est le seul repère extérieur aux courses,
     *   et il ne décide que là où il nomme un arrêt.
     */
    suspend fun fetchJourney(
        session: AuthSession,
        lineId: String,
        directionId: Int,
        expectedTerminus: String = "",
    ): LineJourney

    /**
     * **Tous** les parcours d'une ligne dans un sens, le référence en tête.
     *
     * L'ordre porte le sens : le premier est celui qu'on affiche sans rien
     * demander, les suivants sont ceux qu'on propose au choix. Une liste vide
     * n'arrive pas — l'implémentation lève plutôt que de rendre le vide, parce
     * qu'une fiche sans desserte se lit « cette ligne ne dessert rien ».
     *
     * ## Pourquoi c'est celle-ci qui a un défaut, et non [fetchJourney]
     *
     * Une source qui ne connaît qu'un parcours par sens en rend **un**, et c'est
     * une réponse juste : la fiche n'affichera pas de menu, ce qui est le cas de
     * cent trente lignes sur cent trente-huit. Le défaut évite d'imposer ce
     * repli à chaque doublure de test qui n'a rien à dire des branches.
     *
     * ## Le terminus annoncé, et pourquoi il faut le passer
     *
     * ⚠️ **Les courses ne suffisent pas à dire où finit la ligne.** L'implémenta-
     * tion GTFS déduit les bouts du parcours le plus long, ce qui suppose que le
     * plus long relie les vrais terminus. Une course qui pousse jusqu'au dépôt
     * renverse cette supposition, et **rien dans sa forme ne la distingue** d'un
     * trajet ordinaire : elle contient l'autre, exactement comme un parcours
     * complet contient une course partielle.
     *
     * Le repère doit donc venir d'ailleurs, et il existe déjà :
     * [ServiceLine.directions] le tire de `route_long_name`. L'appelant qui l'a
     * sous la main le passe ; celui qui ne l'a pas laisse le défaut, et la règle
     * d'origine reprend la main.
     *
     * @param expectedTerminus le terminus annoncé du sens. Vide, ou ne nommant
     *   aucun arrêt desservi — « Beaujoire / Babinière » nomme une paire —, il
     *   ne décide de rien : un repère qu'on ne sait pas lire ne doit pas trancher.
     */
    suspend fun fetchJourneyProfiles(
        session: AuthSession,
        lineId: String,
        directionId: Int,
        expectedTerminus: String = "",
    ): List<LineJourney> = listOf(fetchJourney(session, lineId, directionId, expectedTerminus))

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

/**
 * Les notes de service en vigueur — ce que l'exploitant a décidé, et qui vaut
 * aujourd'hui.
 *
 * ## Pourquoi une interface à part de [DriverServiceRepository]
 *
 * Parce qu'une note **ne s'écrit pas depuis le téléphone**, et que sa durée de vie
 * n'a rien à voir avec celle d'un service. Ce contrat n'a qu'un verbe, en lecture
 * seule ; le glisser dans celui du service donnerait une interface de dix verbes
 * dont un seul n'écrit rien, et ferait relire les notes à chaque heartbeat.
 */
interface ServiceNoteRepository {
    /**
     * Les notes affichées ce jour, la plus récemment applicable d'abord.
     *
     * @param line l'indice public — « 1 », « C3 ». `null` demande tout le réseau.
     *
     * ⚠️ **Filtrer sur une ligne ne retire pas les notes générales** : une note sans
     * ligne vaut pour tout le réseau, et le serveur la garde dans la réponse. Voir
     * [ServiceNote.concerns], qui applique la même règle côté client.
     *
     * @return une liste éventuellement vide. **Aucune note n'est pas une erreur** —
     * il n'y a pas tous les jours une consigne neuve, et lever pour un réseau calme
     * ferait un bandeau rouge chaque matin.
     */
    suspend fun fetchNotes(session: AuthSession, line: String? = null): List<ServiceNote>
}

/**
 * La messagerie Aule Pro — les routes `api/hub` du BFF (contrat §12).
 *
 * ## Deux lectures qui n'ont rien en commun
 *
 * [messages] remonte l'historique. [delta] rend **ce qui a changé** : les
 * messages neufs, mais aussi les éditions, les suppressions et les réactions sur
 * un message déjà affiché. La base compare une date d'activité, et non la date
 * de création — qui, elle, ne bouge jamais.
 *
 * ## Ce qu'une liste vide veut dire
 *
 * Rien. Un réseau calme n'est pas une panne : toutes les lectures aplatissent un
 * 404 en vide, comme le reste du dépôt. Les **écritures**, elles, lèvent un
 * [HubException] — et une réponse vide sur une écriture n'est jamais un succès.
 */
interface HubRepository {
    /**
     * Crée les canaux Réseau et Dépôt, et y inscrit l'agent.
     *
     * ⚠️ À l'ouverture de l'écran, et au retour au premier plan après une heure.
     * **Pas à chaque tour de boucle** : c'est une écriture.
     */
    suspend fun bootstrap(session: AuthSession): HubBootstrap

    suspend fun channels(session: AuthSession): List<HubChannel>

    suspend fun messages(
        session: AuthSession,
        channelId: String,
        before: Instant? = null,
    ): HubMessagePage

    suspend fun delta(session: AuthSession, channelId: String, after: Instant): HubMessagePage

    /**
     * [clientId] porte l'idempotence : le même identifiant rejoué ne poste
     * qu'une fois, et la base rend le premier message.
     */
    suspend fun send(
        session: AuthSession,
        channelId: String,
        body: String,
        clientId: String,
        replyTo: String? = null,
        fileId: String? = null,
    ): HubMessage

    suspend fun edit(session: AuthSession, messageId: String, body: String): HubMessage

    suspend fun delete(session: AuthSession, messageId: String): HubMessage

    suspend fun toggleReaction(session: AuthSession, messageId: String, emoji: String): HubMessage

    suspend fun markRead(session: AuthSession, channelId: String, at: Instant? = null): HubChannel

    suspend fun updateMembership(
        session: AuthSession,
        channelId: String,
        muted: Boolean? = null,
        favorite: Boolean? = null,
        archived: Boolean? = null,
    ): HubChannel

    suspend fun createGroup(
        session: AuthSession,
        name: String,
        memberIds: List<String> = emptyList(),
    ): HubChannel

    suspend fun rename(session: AuthSession, channelId: String, name: String): HubChannel

    suspend fun addMembers(
        session: AuthSession,
        channelId: String,
        userIds: List<String>,
    ): HubChannel

    suspend fun removeMember(session: AuthSession, channelId: String, userId: String): HubChannel

    suspend fun leave(session: AuthSession, channelId: String)

    suspend fun closeGroup(session: AuthSession, channelId: String): HubChannel

    suspend fun members(session: AuthSession, channelId: String): List<HubMember>

    suspend fun openDirect(session: AuthSession, userId: String): HubChannel

    /**
     * Rend une liste vide sous deux caractères : on ne montre pas l'annuaire du
     * réseau, et la base refuserait de toute façon de le servir.
     */
    suspend fun searchColleagues(session: AuthSession, query: String): List<HubColleague>

    suspend fun unread(session: AuthSession): HubUnread

    /**
     * Téléverse une pièce jointe **en trois temps** : URL signée, envoi direct
     * vers Storage, déclaration. Le fichier ne traverse pas le BFF — Vercel
     * plafonne un corps de requête à 4,5 Mo, et un planning photographié le
     * dépasse.
     */
    suspend fun upload(
        session: AuthSession,
        channelId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        kind: HubFileKind,
    ): HubFile

    /**
     * Le jeton d'appareil.
     *
     * ⚠️ [appEnv] vient du **binaire**, pas du BFF : il décide de l'hôte APNs
     * côté serveur, et un jeton de build de développement envoyé en production
     * serait marqué mort à tort.
     */
    suspend fun registerPushToken(session: AuthSession, token: String, appEnv: String)

    suspend fun unregisterPushToken(session: AuthSession, token: String)
}

/**
 * Ce qui attend le réseau, sur le disque.
 *
 * Un tunnel, un dépôt en sous-sol, une zone blanche : ce sont les endroits d'où
 * un conducteur écrit. Une file en mémoire meurt avec le processus, et le
 * message avec elle — sans que personne ne le sache.
 */
interface HubOutboxStore {
    suspend fun load(): List<HubPendingMessage>
    suspend fun save(pending: List<HubPendingMessage>)
}
