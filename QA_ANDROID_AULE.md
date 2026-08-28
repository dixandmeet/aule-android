# Recette Android — Aule Pro

Campagne de recette produit sur l'application native Android (`~/Aule/Kotlin`),
branche `cursor/handover-done-notify-df65`, commit `58c5b7a`, le 28/08/2026.

> **Correctifs appliqués le 28/08/2026 — les quatre P1 et six des huit P2.**
> Voir [§ 6](#6-correctifs-appliqués) et [§ 6 bis](#6-bis-les-p2-corrigés).
> **908 tests passent, 0 échec** (882 au constat, +26 écrits pour ces
> correctifs) ; Lint reste à 0 erreur et 41 avertissements, le décompte exact
> d'avant. Le corps du rapport garde le constat
> **tel qu'il a été fait**, avant correction : c'est ce qui permet de relire
> plus tard ce qui avait été observé et pourquoi. La décision de la § 5 est
> révisée en fin de document.

---

## Avertissement liminaire — la moitié de la campagne n'a pas pu être jouée

La mission demande une recette **en conditions réelles** : trajet automobile
prolongé, virages, rond-points, arrière-plan, écran verrouillé, réseau qui
tombe, profilage CPU/GPU/batterie/température.

**Aucun appareil n'était joignable pendant la campagne.**

```
$ adb kill-server && adb start-server && adb devices -l
* daemon started successfully
List of devices attached
          ← vide
```

- Le Samsung S21 de référence (`R3CRA0WV55H`) n'est pas branché.
- `~/.android/avd/` n'existe pas et `~/Library/Android/sdk/system-images/` est
  vide : **aucun émulateur n'est installable sans téléchargement d'image
  système**, et le flux de travail du projet l'exclut de toute façon
  (`CLAUDE.md` : « Pas d'émulateur dans le flux de travail »).

Tout ce qui exige un écran, un GPS, une batterie ou un thermomètre est donc
`BLOCKED`. Ce n'est pas une réserve de forme : les sections 6 à 9, 12 à 28, 31
et 33 de la mission portent précisément là-dessus, et **aucune conclusion de ce
rapport ne prétend les couvrir**.

Ce qui a réellement été exécuté :

| Vérification | Résultat |
|---|---|
| Suite de tests JVM, exécution forcée (`test --rerun-tasks`) | **882 tests, 0 échec**, 4 min 05 |
| Assemblage `assembleDevelopmentDebug` | **SUCCESS**, APK 65 Mo |
| Assemblage minifié `minifyProductionReleaseWithR8` | **BLOCKED** — garde de signature |
| Android Lint (`lintDevelopmentDebug`) | **SUCCESS — 0 erreur, 41 avertissements** |
| Profils du routeur OSRM public | **testé en réel** — voir AND-AUTO-003 |
| Complétude des catalogues `values` / `values-en` | **803 clés, aucun écart** |
| Gardes Material 3 et design system | **dettes vides** — migration acquise |
| Audit statique de l'architecture, du guidage, du GPS, du réseau, de l'auth | fait |

Les anomalies ci-dessous sont donc de deux natures, et le rapport les distingue
toujours : celles **constatées** (exécution, mesure, requête réelle) et celles
**établies par lecture du code** — dont la reproduction à l'écran reste à faire.

---

## 1. Audit initial du projet

| # | Point | Constat |
|---|---|---|
| 1 | Architecture | Multi-module Gradle, 12 modules. `:app` seul voit `:data` ; `:feature:map` n'en dépend pas — un `@Composable` ne **peut pas** compiler un appel réseau. Configuration partagée dans `build-logic/` (4 plugins de convention), pas d'`allprojects`. |
| 2 | UI | **Jetpack Compose**, activité unique (`MainActivity`), `enableEdgeToEdge`, portrait verrouillé. |
| 3 | Material 3 | `androidx.compose.material3:1.5.0-alpha26` épinglé **hors BOM** (l'expressivité — `MaterialExpressiveTheme`, `MaterialShapes`, `ButtonGroup`, `LoadingIndicator` — est `internal` en 1.4.0). Thème Aule par jetons. Quatre gardes automatiques interdisent Material 2, `BasicText`, les enveloppes redondantes et les formes locales : **les quatre dettes sont vides**. |
| 4 | Moteur cartographique | **MapLibre Native Android 13.5.0**, artefact `android-sdk-opengl` (ADR-002 — l'artefact `android-sdk` embarque Vulkan). Styles locaux (`assets/map/style-{light,dark}.json`, 41 couches, `building-3d` en `fill-extrusion`). Tuiles vectorielles : `https://tiles.openfreemap.org/planet`. Réseau de lignes hors ligne en PMTiles (3,3 Mo). |
| 5 | Géolocalisation | `FusedLocationProviderClient` (Play Services), enveloppé par `FusedLocationProvider`. Trois traitements purs et testés : `HeadingStabilizer` (gel du cap sous 0,7 m/s), `HeadingFusion` (mélange boussole / cap de route par la vitesse), `MotionAnchor` (lissage). Boussole via `DeviceCompass`. Rejet des mesures de plus de 30 s (2 min pour le dernier connu). |
| 6 | Moteur d'itinéraire | **Deux moteurs, et c'est le point sensible.** Le tracé et les durées viennent du BFF `www.aule.fr` (`GET /api/route`, `v=28`). Les **manœuvres** viennent d'un second appel à `https://router.project-osrm.org` — le serveur de démonstration public d'OSRM — puis sont agrafées sur le tracé peint avec une tolérance de 25 m (`pinManeuvers`). Ce qui ne tombe pas dessus est **écarté en silence**. |
| 7 | Supabase | Pas de SDK. Appels HTTP directs : GoTrue (`/auth/v1/token`, `/logout`, `/user`, `/signup`, `/recover`) et PostgREST (`drivers`, `user_profiles`, `user_saved_places`, `driver_reports`, `gtfs_*`, RPC `publish_position_with_state`, `handover_*`, `delete_my_account`). Storage pour `driver-avatars`. **La carte ne parle jamais à Supabase** — tout passe par le BFF. |
| 8 | Temps réel | **Aucun Realtime, aucun WebSocket.** La flotte est sondée en HTTP toutes les 15 s (`POLL_INTERVAL_MS`), avec repli exponentiel jusqu'à 120 s en cas d'échec. Le lissage à 120 Hz est **local** : `Choreographer` écrit directement dans la source MapLibre sans recomposer Compose (ADR-006). |
| 9 | Caches | `CachedStopRepository` (disque, `FileCacheStore`, revalidation sur un scope propre), `PreferencesSavedPlacesStore` (favoris lus **synchrones** au démarrage, ADR-012), `PreferencesSearchHistoryStore`, `PreferencesAuthSessionStore`, `TransitArchive` (PMTiles), `AssetNetworkLineRepository`. Un seul `OkHttpClient` partagé avec MapLibre (un pool, un délai, un journal). |
| 10 | Arrière-plan | Aucun `WorkManager`, aucun `JobScheduler`, aucun `BroadcastReceiver` de démarrage. Le seul travail de fond est le service ci-dessous. |
| 11 | Foreground Service | `NavigatingForegroundService`, type `location`, `START_STICKY`, notification permanente `CATEGORY_NAVIGATION`, `PARTIAL_WAKE_LOCK` de **6 h**. Démarré par `FusedLocationProvider.syncForegroundService()` pour les paliers `NAVIGATING` et `ON_DUTY` uniquement. |
| 12 | Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA` dans `:app` ; `ACCESS_{COARSE,FINE}_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `VIBRATE` fusionnées depuis `:core:location`. **Pas d'`ACCESS_BACKGROUND_LOCATION`** — le choix est correct : le FGS suffit et évite la revue Play la plus lourde. La position est demandée par `WelcomeScreen` **après explication**, pas à froid. |
| 13 | Diagnostic | `AuleLogger` avec domaines (`APP`, `NET`, `GPS`, `MAP`, `AUTH`). Traces GPS de guidage écrites sur disque (`FileGpsTraceCatalog`), listées et exportables depuis Profil → Préférences. Pas de Crashlytics, pas de télémétrie. |

---

## 2. Tests automatiques

### 2.1 Suite JVM — `PASS`

`./gradlew test` seul répondait `BUILD SUCCESSFUL in 8s / 192 up-to-date` sans
rien exécuter. La campagne a donc forcé l'exécution :

```
./gradlew test --rerun-tasks
BUILD SUCCESSFUL in 4m 5s
192 actionable tasks: 192 executed
```

Dépouillement des 104 rapports JUnit :

| Module | Tests |
|---|---|
| `core/model` | 259 |
| `core/guet` | 122 |
| `feature/map` | 121 |
| `data` | 102 |
| `core/designsystem` | 99 |
| `core/map` | 99 |
| `core/geo` | 30 |
| `feature/auth` | 23 |
| `core/location` | 21 |
| `core/common` | 6 |
| **Total** | **882 — 0 échec, 0 erreur, 0 ignoré**, 54,9 s cumulés |

### 2.2 Tests instrumentés — `NOT_IMPLEMENTED`

```
$ find core data feature app -path "*/src/androidTest/*" -name "*.kt" | wc -l
0
```

Aucun test Compose UI, aucun test Espresso, aucun test instrumenté. Un APK
`app-development-debug-androidTest.apk` de 35 Ko traîne dans `build/outputs` :
c'est une coquille vide.

Conséquence directe : **rien de ce que ce rapport classe `BLOCKED` ne serait
rattrapé par la CI**. Les 882 tests couvrent les modules purs et le décodage —
c'est la doctrine assumée du projet (`README.md` : « Pas les vues, pas le
`MapController` ») — mais elle laisse l'écran, la caméra, le GPS et le cycle de
vie sans filet automatique.

### 2.3 Android Lint

`./gradlew :app:lintDevelopmentDebug` — **BUILD SUCCESSFUL, 0 erreur, 41
avertissements.** Rapport : `app/build/reports/lint-results-developmentDebug.html`.

| Règle | Nb | Lecture |
|---|---|---|
| `UseKtx` | 14 | Cosmétique — `SharedPreferences.edit` en KTX. |
| `GradleDependency` | 7 | Versions AndroidX en retard d'un cran (core 1.18→1.19, activity 1.12→1.13, lifecycle 2.10→2.11…). |
| `AndroidGradlePluginVersion` | 4 | Gradle 9.4.1 → 9.7.1, AGP 9.2.1 → 9.3.2. **Écart assumé et argumenté** dans le catalogue de versions. |
| `NewerVersionAvailable` | 4 | OkHttp 5.4.0 → 5.5.0, **MapLibre 13.5.0 → 13.5.1**. |
| `LogNotTimber` | 4 | Faux positif : `AndroidLogger` est l'implémentation voulue de `AuleLogger`. |
| `ObsoleteSdkInt` | 3 | Gardes `SDK_INT >= 26` inutiles depuis `minSdk 26` ; `mipmap-anydpi-v26` redondant. |
| `OldTargetApi` | 1 | `targetSdk 36` n'est plus le dernier. |
| `UnusedAttribute` | 1 | `enableOnBackInvokedCallback` — API 33+, sans effet sous. Normal. |
| **`LockedOrientationActivity`** | 1 | **Voir AND-BUG-019 ci-dessous.** |
| **`DiscouragedApi`** | 1 | **« Fixed screen orientations will be ignored in most cases, starting from Android 16 »** — et `targetSdk` vaut 36. |
| `UnusedResources` | 1 | `R.string.permission_location_rationale` n'est employée nulle part (la justification affichée est celle de `WelcomeScreen`). |

Le seul signalement à conséquence produit est le couple
`LockedOrientationActivity` / `DiscouragedApi` : le verrou portrait du manifeste
est un **choix structurant** — « tout le cadrage de la carte se calcule sur la
hauteur » — et Android 16 cesse de l'honorer sur les grands écrans. Sur le S21
rien ne change ; sur une tablette ou un pliable déplié, l'activité tournerait en
paysage.

> **Correction, après vérification** — la première rédaction affirmait que
> « toute l'arithmétique de cadrage tournerait avec elle ». C'est faux :
> `MapController.viewportHeightDp` lit la hauteur **mesurée** de la vue, et les
> détentes du volet sont des fractions de `BoxWithConstraints`. Le cadrage
> serait donc dégradé en paysage — bande plus basse, moins de route devant — et
> non faux. Voir § 6 ter.

### 2.4 Avertissements de compilation

> **Ce paragraphe a d'abord été faux, et la correction vaut d'être lue.** Le
> premier relevé annonçait « 14 avertissements » : il venait d'un `tail` sur une
> sortie de build filtrée, qui n'en montrait que la fin. Le compte réel, pris sur
> une compilation complète forcée, est de **22**. Les huit manquants vivaient
> dans `:feature:auth` et `:core:network`, en amont du fragment lu.

Inventaire réel, avant correction :

| Avertissement | Nb | Où |
|---|---|---|
| Surcharge `ListItem(headlineContent = …)` dépréciée | 13 | `HandoverScreen` ×5, `ProfileScreen` ×4, `RouteSheet` ×2, `AccountMenuSheet`, `LineDepartureSheet`, `StopDetailSheet`, `TripSheet` |
| `rememberModalBottomSheetState` déprécié | 6 | 5 volets de `:feature:map`, 1 de `:feature:auth` |
| Appel sûr inutile sur `ResponseBody` | 3 | `AuleHttpClient` — `Response.body` n'est plus nullable en OkHttp 5 |
| Appel sûr inutile sur `JourneyLeg` | 3 | `NextAction` — le compilateur a déjà déduit la non-nullité |
| `!!` inutile | 1 | `MapScreen` — `menuOpen` porte déjà le test |
| Nom de paramètre divergent du supertype | 1 | `AuthViewModelRecoveryTest` |

Après la passe du § 6 ter, il en reste **15**, tous la même dépréciation de
`ListItem` — laissée délibérément, pour la raison qui y est donnée.

## 3. Fiches de test

### 3.1 Installation et lancement

#### [AND-001] Installation propre
- **Préconditions** — appareil vierge, flavor `development`.
- **Étapes** — `./gradlew installDevelopmentDebug` puis `am start`.
- **Résultat attendu** — pas de plantage, écran d'accueil, temps de lancement mesuré.
- **Résultat obtenu** — **non exécuté, aucun appareil joignable.** Ce qui a pu être vérifié : `assembleDevelopmentDebug` réussit, l'APK fait 65 Mo (debug, non minifié), le manifeste fusionné est cohérent (activité unique exportée, service `location` non exporté, `FileProvider` en `${applicationId}.files`).
- **Statut** — `BLOCKED`
- **Criticité** — n/a
- **Fichiers** — [app/build.gradle.kts](app/build.gradle.kts), [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml)

#### [AND-002] Relancement
- **Résultat obtenu** — non exécuté. Lecture du code : la session est relue par `AuthRepository.restore()`, les favoris par `PreferencesSavedPlacesStore` **de façon synchrone** (donc à l'écran avant la première image), le thème par `sae.theme_mode`. Une seule `AuleGraph` par processus, construite paresseusement — pas de double initialisation possible. **Mais `restore()` porte l'anomalie AND-AUTH-005 ci-dessous**, qui se déclenche précisément à un relancement.
- **Statut** — `BLOCKED`

#### [AND-003] Mise à jour par-dessus une ancienne version
- **Résultat obtenu** — non exécuté. `android:allowBackup="false"` et `dataExtractionRules` posés : rien ne transite par la sauvegarde système. Les données utilisateur vivent en `SharedPreferences` et fichiers du `filesDir`, préservés par une mise à jour normale. Aucune migration de schéma à jouer (pas de base SQL).
- **Statut** — `BLOCKED`

### 3.2 Permissions Android

#### [AND-PERM-001] Refus, précision réduite, retour depuis les Réglages
- **Résultat obtenu** — non exécuté à l'écran. Lecture : `readAuthorization()` distingue cinq états (`SERVICES_DISABLED`, `GRANTED`, `REDUCED_ACCURACY`, `UNKNOWN`, `DENIED`), le passage `UNKNOWN → DENIED` étant mémorisé par un drapeau `has_requested` — un refus est donc distingué d'un « pas encore demandé ». `requestUpdates()` rattrape `SecurityException` et arrête proprement ; `openSettings()` ouvre la fiche système. Le dialogue est posé par `WelcomeScreen`, **après** avoir expliqué à quoi sert la position, et non à froid.
- **Statut** — `BLOCKED` (comportement à l'écran) — la structure est correcte.

#### [AND-PERM-002] Notifications refusées / acceptées
- **Résultat obtenu** — `PASS` à la lecture. Elle est demandée à deux endroits : à l'ouverture d'un **service** (`LaunchedEffect(serviceActive)`) et au **démarrage d'un guidage** — `startGuidance` reçoit un `requestNotifications` que le site d'appel branche sur le lanceur. Un refus n'interrompt rien, et c'est le bon choix : le service de premier plan tourne quand même.
- **Statut** — `PASS` (lecture) / `BLOCKED` (à l'écran)

### 3.3 Géolocalisation — `BLOCKED`

`AND-GPS-001` (position initiale, précision, temps d'acquisition, puck,
orientation, centrage), `AND-GPS-002` (déplacement, fluidité, téléportation,
basse vitesse) et `AND-GPS-003` (tunnel, parking, GPS coupé) **exigent un
appareil et un déplacement réel.** Non exécutés.

Ce que le code garantit, et qui est couvert par 21 tests dans `:core:location` :

- le cap est **gelé** sous 0,7 m/s (`HEADING_MIN_SPEED_MPS`) — c'est ce qui
  empêche le puck de tournoyer à l'arrêt ;
- la fusion boussole / cap de route se fait **par la vitesse**, en continu et
  non par bascule, avec zone morte proportionnelle ;
- `MotionAnchor` retient la position tant qu'on n'est pas sorti du rayon
  d'incertitude, ce qui absorbe la dérive à l'arrêt ;
- une mesure de plus de 30 s est **jetée** — c'est ce qui produit le bandeau
  « Signal GPS perdu » plutôt qu'une position fantôme.

La récupération après perte de signal n'a **aucun test** : `signalLost` est posé
par `onGuidanceFix(null)` et levé au premier point utilisable, mais le
comportement de `FusedLocationProviderClient` en sortie de tunnel ne se juge
qu'en roulant.

### 3.4 Carte, carte 3D, mode jour/nuit — `BLOCKED`

Zoom, rotation, pitch, artefacts, clipping, FPS, surcharge GPU : rien de tout
cela ne se lit dans du code. Ce qui est établi :

- l'inclinaison est **plafonnée par le zoom** (`maxPitchForZoom`, rampe linéaire
  entre les zooms 13,5 et 15,5), avec une « dette d'inclinaison » qui rend
  exactement ce qu'elle a pris — 99 tests dans `:core:map` verrouillent cette
  fonction ;
- MapLibre Android plafonne à 60° dans son cœur ; on demande 67° et on
  journalise la valeur obtenue (ADR-009) ;
- le recentrage automatique **n'existe pas** : passer en `FREE_EXPLORE` n'annule
  rien et attend « Recentrer ». C'est exactement ce que la mission demande (§ 7) ;
- le mode jour/nuit est un tri-état (`Clair` / `Sombre` / `Auto`, `AppearanceMode`),
  par défaut clair comme le Flutter, et le rechargement de style **repeint
  l'ambiance après avoir remonté sources, couches et images** — c'est le piège
  n° 1 du projet, et le registre l'adresse.

- **Statut** — `BLOCKED`

### 3.5 Recherche, favoris, arrêts, horaires — `BLOCKED` à l'écran, structure vérifiée

- **Recherche** — `MapSearchViewModel`, débounce 320 ms, historique des lieux, classement testé (`StopSearchTest`). Le géocodeur est partagé avec l'éditeur de favoris **sans partager son état** (`PlacePickerModel`) : chercher une adresse à enregistrer ne défait pas l'itinéraire affiché.
- **Favoris** — Domicile / Travail affichés **avant d'exister** (« À définir »), lecture disque synchrone, fusion à l'horodatage **avant** écriture, suppression laissant une pierre tombale datée. `SavedPlaceTest` couvre remplacement, fusion et pierres tombales. Une synchronisation ratée est journalisée et rien d'autre.
- **Arrêts** — hit-test à **22 dp** convertis en pixels ; `iconAllowOverlap` oui, `iconIgnorePlacement` **non** (sinon les arrêts sortent de l'index de collision et deviennent intouchables).
- **Horaires** — date arbitraire (`TimetableModel.setDate(LocalDate)`), donc aujourd'hui / demain / samedi / dimanche sont le même chemin. Le décalage GTFS après minuit est traité (`Timetable.kt`).
- **404 ≠ 502** sur les passages : les deux mènent à un écran vide mais portent des messages distincts.

### 3.6 Véhicules temps réel et suivi — `BLOCKED`

Sondage 15 s + repli exponentiel plafonné à 120 s ; interpolation `Choreographer`
hors état Compose ; un véhicule théorique remplacé par sa mesure est rattrapé par
`twinId` pour que la caméra ne suive pas un identifiant que plus personne ne
publie. Le profil caméra `FOLLOW_VEHICLE` existe et est distinct.

La mission demande explicitement (§ 15) que **la caméra ne revienne pas
brutalement sur l'utilisateur** à l'arrêt du suivi : `CameraMode.FREE_EXPLORE`
est bien l'état de sortie et n'annule rien, mais **la transition elle-même ne se
juge qu'à l'écran.**

### 3.7 Navigation piéton

#### [AND-PIE-001] Manœuvres piétonnes
- **Étapes** — itinéraire piéton, lecture du bandeau de guidage.
- **Résultat attendu** — des consignes qui suivent le tracé piéton peint.
- **Résultat obtenu** — **FAIL établi par mesure.** Voir AND-AUTO-003 : le profil piéton envoyé au routeur de manœuvres est ignoré par le serveur, qui répond **en voiture**. Les manœuvres décrivent donc un autre trajet que celui qui est peint ; celles qui s'écartent de plus de 25 m sont écartées en silence par `pinManeuvers`, les autres restent et sont fausses.
- **Statut** — `FAIL`
- **Criticité** — `P2`

Le reste (tracé, distance, durée, progression, mauvais trajet volontaire) est
`BLOCKED`.

### 3.8 Navigation automobile — la partie prioritaire

#### [AND-AUTO-001] Activation du mode voiture
- **Résultat obtenu** — non exécuté. Lecture : `RouteMode.CAR` part bien en `mode=car` sur le fil ; `travelStyleOf(LegMode.CAR) = TravelStyle.DRIVE` ; le profil `NAVIGATION/DRIVE` est distinct du piéton — inclinaison 55°→67°, sujet à 20 % de la bande visible sous le centre, zoom 16,2 lancé / 16,9 à l'arrêt, croisière 18 m/s, rapprochement de 0,8 niveau et redressement de 10° à l'approche d'un carrefour. Le cap retombe sur le **cap du segment d'itinéraire** quand le GPS n'en donne pas — c'est ce qui évite une première seconde de guidage dans une direction arbitraire. Aucun comportement piéton résiduel n'est visible dans le code.
- **Statut** — `BLOCKED` (structure correcte)

#### [AND-AUTO-002] Restrictions routières (rue piétonne, sens interdit, voie privée…)
- **Résultat attendu** — Aule ne propose jamais une route non autorisée.
- **Résultat obtenu** — **NOT_APPLICABLE côté client, et c'est le problème.** Le tracé automobile est **entièrement décidé par le BFF** (`GET /api/route?mode=car`) ; l'application ne connaît ni le graphe routier, ni les restrictions, et ne peut donc ni les vérifier ni les corriger. Aucun test, aucune garde, aucun garde-fou client. La conformité de ce point ne peut être établie qu'en recettant le BFF, hors du périmètre de ce dépôt.
- **Statut** — `NOT_APPLICABLE` (périmètre client) / `BLOCKED` (recette réelle)
- **Criticité** — la mission la fixe à `P0/P1` ; elle reste **non démontrée**, dans un sens comme dans l'autre.

#### [AND-AUTO-003] Origine des manœuvres — **anomalie constatée**
- **Étapes** — lecture de `OsrmRoadRouter` puis interrogation réelle du serveur.
- **Résultat obtenu** — deux défauts, dont un mesuré :

  1. `RoadProfile.PEDESTRIAN` s'écrit `walking` dans l'URL, mais le serveur de
     démonstration public ne charge **que le profil voiture** et ignore le
     segment de chemin. Vérifié le 28/08/2026 sur une paire de points nantais :

     ```
     https://router.project-osrm.org/route/v1/{driving|walking|foot}/-1.5534,47.2173;-1.5410,47.2260
     → code Ok, distance 1912.9 m, durée 283.2 s — les trois, à l'identique
     ```

     C'est exactement le défaut que `Route.kt` documente pour `walk` vs `foot`
     côté BFF, réapparu une couche plus bas.

  2. Le flavor `production` pointe sur `https://router.project-osrm.org`
     ([OsrmRoadRouter.kt:52](data/src/main/kotlin/io/aule/android/data/aule/OsrmRoadRouter.kt:52),
     [AuleGraph.kt:210](app/src/main/kotlin/io/aule/android/AuleGraph.kt:210)). Le
     commentaire prévoit un hôte injectable « pour en changer sans toucher au
     code » — mais **aucun `buildConfigField`, aucune entrée `local.properties`,
     aucune valeur de flavor ne l'injecte** : le défaut part en production.
     Ce serveur est une démonstration sans garantie de service, dont les
     conditions d'usage excluent la production.

- **Statut** — `FAIL`
- **Criticité** — `P1`

#### [AND-AUTO-004] Virages
- **Résultat obtenu** — non exécuté. `ManeuverKind` couvre les neuf familles et `CameraDynamics.maneuverAim` rapproche le cadre entre 170 m et 45 m en voiture, puis le rend. Le réalignement de la caméra derrière le véhicule est piloté par le cap lissé, à 4 Hz (`CAMERA_TICK_MS`) contre 1 Hz pour le guidage.
- **Statut** — `BLOCKED`

#### [AND-AUTO-005] Rond-points — **anomalie constatée**
- **Résultat attendu** — entrée, sortie, **numéro de sortie**, direction, représentation.
- **Résultat obtenu** — **FAIL.** `OsrmManeuverDto` ne décode que `type`, `modifier` et `location` ([OsrmDto.kt:39-44](data/src/main/kotlin/io/aule/android/data/dto/OsrmDto.kt:39)) : le champ `exit` d'OSRM, qui **porte le numéro de sortie**, est ignoré. `ManeuverKind.ROUNDABOUT` n'a aucun champ pour le transporter, et la formulation est un texte fixe :

  ```xml
  <string name="maneuver_roundabout">Prendre le rond-point</string>
  ```

  Le conducteur n'apprend donc ni la sortie à prendre, ni la direction. Sur un
  rond-point à cinq branches, c'est une consigne qui ne guide pas.
- **Statut** — `FAIL`
- **Criticité** — `P2`
- **Correction** — ajouter `exit: Int?` au DTO et à `RoadManeuver`/`PinnedManeuver`, puis une formulation `maneuver_roundabout_exit` avec quantité (« Prendre la %1$se sortie »), en gardant le texte actuel comme repli quand `exit` est absent.

#### [AND-AUTO-006] Continuer tout droit
- **Résultat obtenu** — **PASS (statique).** `maneuverKindOf` rend `STRAIGHT` pour `turn`/`new name`/`continue`/`end of road`/`notification` sans modificateur ou avec `straight`, et la formulation est « Continuer tout droit ». Aucun chemin ne produit gauche/droite à partir d'un modificateur absent. Couvert par `ManeuversTest`.
- **Statut** — `PASS` (vérification statique ; le rendu à l'écran reste `BLOCKED`)

#### [AND-AUTO-007] Recalcul — **anomalie constatée**
- **Étapes** — prendre volontairement une mauvaise direction.
- **Résultat attendu** — détection, délai, animation, nouveau tracé, suppression de l'ancien.
- **Résultat obtenu** — **NOT_IMPLEMENTED, et c'est délibéré.** La détection existe et est bonne : `OffRouteDetector` demande 3 mesures consécutives au-delà d'un seuil de 32 m élargi jusqu'à 70 m selon la précision, avec hystérésis de retour à 0,7× le seuil. Mais elle ne déclenche **qu'un booléen d'affichage** :

  ```kotlin
  // GuidanceBanner.kt
  // Pas de « Recalcul » : ce jalon n'en fait pas, et un bandeau qui l'annonce mentirait.
  state.offRoute -> { title = "Vous avez quitté l'itinéraire"; detail = "Rejoignez le tracé." }
  ```

  `MapViewModel` ne rappelle jamais `routing` sur sortie de parcours — un seul
  `stopGuidance()` existe dans tout le code, et il vient d'un bouton.
  Concrètement : **on rate une sortie, et Aule demande de faire demi-tour
  jusqu'à ce qu'on abandonne.**
- **Statut** — `NOT_IMPLEMENTED`
- **Criticité** — `P1` pour l'usage visé (trajet automobile prolongé)
- **Fichiers** — [MapViewModel.kt:1341](feature/map/src/main/kotlin/io/aule/android/feature/map/MapViewModel.kt:1341), [OffRoute.kt](core/model/src/main/kotlin/io/aule/android/core/model/OffRoute.kt), [GuidanceBanner.kt:30](feature/map/src/main/kotlin/io/aule/android/feature/map/GuidanceBanner.kt:30)

#### [AND-AUTO-008] Vitesse affichée
- **Résultat attendu** — comparer à une référence, à l'arrêt / 30 / 50 / variable.
- **Résultat obtenu** — **NOT_IMPLEMENTED.** Aucune vitesse n'est affichée au conducteur. `speedMetersPerSecond` n'alimente que la courbe de zoom de la caméra et la fusion de cap ; la seule vitesse à l'écran (`vehicle_speed`, « %1$d km/h ») appartient à la fiche d'un **véhicule du réseau**, pas à l'utilisateur. Il n'y a par conséquent ni limitation de vitesse, ni alerte.
- **Statut** — `NOT_IMPLEMENTED`
- **Criticité** — `P2`

#### [AND-AUTO-009] Arrivée
- **Résultat obtenu** — **PARTIAL.** `JourneyProgress.arrived` bascule à `t ≥ 0,999` et `NextAction` affiche « Vous êtes arrivé ». Mais **rien n'arrête le guidage** : le seul appelant de `stopGuidance()` est le bouton « Arrêter ». Le service de premier plan, le `PARTIAL_WAKE_LOCK` de 6 h et le GPS en `PRIORITY_HIGH_ACCURACY` continuent après l'arrivée, jusqu'à ce que quelqu'un y pense.
- **Statut** — `PARTIAL`
- **Criticité** — `P2`

### 3.9 Arrière-plan, écran verrouillé, multitâche

#### [AND-BG-001] Arrière-plan et écran verrouillé (5 / 15 / 30 min)
- **Résultat obtenu** — non exécuté. Lecture : le FGS `location` + `PARTIAL_WAKE_LOCK` tient le processus ; la boucle de guidage est un `LaunchedEffect(state.isNavigating)` — donc liée à la **composition**, qui survit à `onStop` — et continue de battre à 1 Hz en arrière-plan ; la boucle caméra et le soleil sont eux `repeatOnLifecycle(RESUMED)` et se suspendent, ce qui est le bon partage. Le sondage de flotte s'arrête en arrière-plan. La conception est cohérente ; **la tenue réelle sur 30 minutes, écran éteint, ne se juge qu'en roulant.**
- **Statut** — `BLOCKED`

#### [AND-BG-002] Geste de retour pendant un guidage — **anomalie constatée**
- **Étapes** — guidage en cours, aucun volet ouvert, geste de retour.
- **Résultat attendu** — soit rien, soit une confirmation ; en aucun cas un guidage perdu et un service orphelin.
- **Résultat obtenu** — **FAIL.** `PredictiveBackHandler(enabled = sheetPresented || searchOpen)` ([MapScreen.kt:630](feature/map/src/main/kotlin/io/aule/android/feature/map/MapScreen.kt:630)) : en guidage sans volet, les deux sont faux, le retour n'est pas intercepté et **l'activité se termine**. Alors :
  - le `MapViewModel` est vidé, la navigation est **perdue** ;
  - mais `onPauseOrDispose` n'appelle `location.stop()` que `if (!isNavigating && !serviceActive)` ([MapScreen.kt:386](feature/map/src/main/kotlin/io/aule/android/feature/map/MapScreen.kt:386)) — donc **pas** ici ;
  - et `MainActivity` n'a aucun `onDestroy` de rattrapage.

  Résultat : notification « Navigation en cours » permanente, `WAKE_LOCK` de 6 h
  tenu, GPS en haute précision, pour un guidage qui n'existe plus. Toucher la
  notification rouvre l'application sur une carte sans itinéraire.

  C'est la sortie la plus banale au volant, et elle est silencieuse.
- **Statut** — `FAIL`
- **Criticité** — `P1`
- **Correction** — soit intercepter le retour pendant le guidage (confirmation, comme la déconnexion), soit arrêter le flux et le service quand la composition est détruite avec un guidage actif.

#### [AND-BG-003] Application retirée du multitâche
- **Résultat obtenu** — **PARTIAL.** Le service est `START_STICKY` et **n'implémente pas `onTaskRemoved`**. Android relance donc le service avec un `intent` nul : `onDuty` retombe à `false`, la notification se reconstruit en « Navigation en cours », le `WAKE_LOCK` est repris — sans aucune navigation derrière, et sans lecteur de position (le `FusedLocationProvider` est mort avec le processus, le service « ne lit aucune position lui-même »). C'est un service zombie qui consomme sans rien produire.
- **Statut** — `PARTIAL`
- **Criticité** — `P2`
- **Correction** — `onTaskRemoved { stopSelf() }`, ou `START_NOT_STICKY` puisque le service n'a de sens qu'avec un lecteur vivant.

#### [AND-BG-004] Optimisation batterie, économie d'énergie, constructeurs
- **Résultat obtenu** — **BLOCKED.** Aucun appel à `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, aucun contournement constructeur, aucun écran expliquant à l'utilisateur qu'il doit exempter l'application. Sur les surcouches agressives (Samsung « Mettre en veille les applis inutilisées », Xiaomi, Huawei), un FGS `location` survit généralement — mais **cela ne se démontre pas sans les appareils**, et la mission demande trois marques.
- **Statut** — `BLOCKED`

### 3.10 Navigation Android (gestes, trois boutons, retour)
- **Résultat obtenu** — non exécuté. `enableOnBackInvokedCallback="true"` posé ; `PredictiveBackHandler` sur cinq écrans (inscription, profil, relève, prise de service, carte) avec une hiérarchie de fermeture explicite (menu → recherche → trajet → ligne → volet). Le seul trou est celui d'AND-BG-002.
- **Statut** — `PARTIAL` (par AND-BG-002)

### 3.11 Réseau
- **Résultat obtenu** — non exécuté à l'écran. Un seul `OkHttpClient`, partagé avec MapLibre : `callTimeout` 20 s, `connectTimeout` 10 s, `readTimeout` 20 s, `retryOnConnectionFailure(true)` — c'est ce dernier qui rattrape une bascule Wi-Fi → mobile en cours de requête. Le client **lève** plutôt que de rendre une liste vide (leçon prise du Flutter : une carte d'apparence normale, sans véhicules et sans message, pendant une panne). Repli exponentiel sur la flotte. Cache disque pour les arrêts, PMTiles embarqués pour les lignes.

  **Deux limites réelles :**
  - le fond de carte vient de `tiles.openfreemap.org` — service public gratuit, sans clé et sans garantie. Hors ligne, il n'y a **pas de fond de carte** ;
  - le mode avion pendant un guidage n'a pas de conséquence immédiate (le tracé est déjà en mémoire), mais **les manœuvres des jambes suivantes ne se chargeront pas** — `loadManeuversAround` rend `emptyList()` en silence sur échec.
- **Statut** — `BLOCKED` (comportement) / `PARTIAL` (structure)

### 3.12 Authentification

#### [AND-AUTH-001..004] Connexion, déconnexion, mauvais identifiants, PKCE
- **Résultat obtenu** — 23 tests dans `:feature:auth`, 102 dans `:data` (dont `SupabaseAuthRepositoryTest` et `PkceTest`). Les codes d'erreur GoTrue sont repris un à un. La déconnexion est **seule, en bas du menu, avec confirmation** — « un doigt qui dérape au volant ne doit pas renvoyer à la saisie du mot de passe ». Les habilitations (`user_profiles.role` + fiche `drivers`) ferment la session par `resolveAgentAccess` ; une fiche illisible ne déconnecte pas si un rôle staff suffit.
- **Statut** — `PASS` (couverture automatique) / `BLOCKED` (parcours à l'écran)

#### [AND-AUTH-005] Expiration de session hors réseau — **anomalie constatée**
- **Préconditions** — session ouverte, jeton d'accès expiré (durée GoTrue par défaut : 1 h), pas de réseau (dépôt souterrain, parking, mode avion).
- **Étapes** — relancer l'application.
- **Résultat attendu** — garder la session locale, réessayer plus tard, et **ne pas** demander le mot de passe.
- **Résultat obtenu** — **FAIL.** `restore()` rattrape `AuthException` **et** `Throwable`, et dans les deux cas efface le dépôt :

  ```kotlin
  // SupabaseAuthRepository.kt:73-85
  return try { refresh(stored.refreshToken).also { session = it } }
  catch (failure: AuthException) { store.clear(); session = null; null }   // ← NETWORK passe ici
  catch (failure: Throwable)     { store.clear(); session = null; null }
  ```

  Or une panne réseau **est** une `AuthException` : `requestToken` convertit
  toute `ApiException` en `AuthException(AuthFailureKind.NETWORK)`
  ([SupabaseAuthRepository.kt:384-388](data/src/main/kotlin/io/aule/android/data/aule/SupabaseAuthRepository.kt:384)).
  Un jeton de rafraîchissement parfaitement valide est donc **jeté parce que le
  réseau était absent**, et l'application retombe sur l'écran de connexion —
  écran qui, sans réseau, ne peut pas aboutir. Le conducteur est enfermé dehors.

  Aucun test ne couvre ce chemin : `SupabaseAuthRepositoryTest` teste la session
  valide et le rafraîchissement réussi, pas le rafraîchissement injoignable.
- **Statut** — `FAIL`
- **Criticité** — `P1`
- **Correction** — ne purger que sur un refus **métier** (`INVALID_CREDENTIALS`, `invalid_grant`, 400/401 de GoTrue). Sur `NETWORK`, garder la session en mémoire et sur disque, marquer le jeton comme à rafraîchir, et réessayer au prochain appel. C'est aussi la seule façon de tenir la promesse déjà écrite dans le `README` sur la déconnexion.

#### [AND-AUTH-006] Marge d'expiration
- **Résultat obtenu** — `isExpired` compare sans marge (`now >= expiresAt`). Un jeton qui expire dans deux secondes est jugé valide, et le premier appel PostgREST qui suit prend un 401 non rattrapé (aucun intercepteur de rafraîchissement sur 401). Fenêtre étroite, mais réelle sur un lancement lent.
- **Statut** — `PARTIAL`
- **Criticité** — `P3`

### 3.13 Supabase
- **Résultat obtenu** — pas de doublon de souscription possible : **il n'y a aucune souscription.** Tout est en HTTP à la demande, sans Realtime. Le seul sondage périodique est la flotte (15 s, via le BFF, pas Supabase), avec repli exponentiel. Les dépôts Supabase sont instanciés **une fois** dans `AuleGraph`, partagent le client OkHttp, et lèvent plutôt que de rendre vide. Consommation réseau non mesurée (nécessite un appareil).
- **Statut** — `PASS` (structure) / `BLOCKED` (mesure)

### 3.14 Performance (30 à 60 min), CPU, GPU, RAM, batterie, température, FPS
- **Résultat obtenu** — **BLOCKED.** Rien de tout cela ne se mesure sans appareil ni Profiler. Les décisions de conception vont dans le bon sens (interpolation hors état Compose, caméra à 4 Hz, guidage à 1 Hz, soleil à 1/60 Hz, boucles suspendues hors `RESUMED`), et le `README` cite des relevés faits sur le S21 — mais **aucun relevé n'a été refait pendant cette campagne**, et deux anomalies ci-dessus (AND-BG-002, AND-BG-003, AND-AUTO-009) sont précisément des fuites de batterie.
- **Statut** — `BLOCKED`

### 3.15 Material 3
- **Résultat obtenu** — **PASS.** Quatre gardes automatiques, **dettes toutes vides** : plus aucun import Material 2, plus aucun `BasicText`/`BasicTextField`, plus aucune enveloppe `AuleButton`/`AuleCard`/… redondante, plus aucune `RoundedCornerShape` écrite dans un écran. Une seconde garde interdit les mesures chiffrées à la main, les ombres hors design system et les caractères en guise d'icône dans `app/` et `feature/`. 99 tests dans `:core:designsystem` couvrent contraste des jetons, échelle typographique, élévations et mesures.

  Réserve : Material 3 est en **1.5.0-alpha26**, épinglé hors BOM et assumé
  (ADR + commentaire du catalogue). Une alpha en production est un risque de
  régression au premier passage de version. Et 13 appels dépréciés
  (`rememberModalBottomSheetState`, surcharge `ListItem`) traînent déjà.
- **Statut** — `PASS`
- **Réserve** — `P3`

### 3.16 Accessibilité
- **Résultat obtenu** — le chemin TalkBack sur la carte est bien là : MapLibre rend un tampon opaque, donc une **action personnalisée** « Autour de vous » ouvre une liste d'arrêts et de véhicules plafonnée à 12 ([MapScreen.kt:1407](feature/map/src/main/kotlin/io/aule/android/feature/map/MapScreen.kt:1407)). Le bandeau de guidage est une `liveRegion` qui énonce une **phrase** (« lead. titre. détail »), pas une composition. Les volets portent des libellés de panneau et des actions de fermeture. Le contraste des jetons est verrouillé par test. `fontScale` est dans `configChanges`, donc la grande taille de texte ne recrée pas l'activité.

  Non vérifiable ici : le rendu réel sous TalkBack, les zones tactiles à 48 dp
  mesurées, la lisibilité aux grandes tailles.
- **Statut** — `PARTIAL` / `BLOCKED`

### 3.17 Scénario réel principal (§ 35)
- **Résultat obtenu** — **BLOCKED en totalité.** Les quatorze étapes exigent un appareil, un véhicule et un réseau qu'on coupe. Sur la seule lecture du code, trois des quatorze étapes sont déjà connues comme défaillantes : l'étape 8 (recalcul) n'existe pas, l'étape 9 (passer en arrière-plan) est sûre **sauf** si l'utilisateur emploie le geste de retour, et l'étape 12/13 (couper puis rétablir Internet) déclenchera AND-AUTH-005 si le trajet dure plus longtemps que le jeton d'accès.
- **Statut** — `BLOCKED`

---

## 4. Anomalies

### P0 — bloquant
Aucune anomalie P0 **constatée**. Mention obligatoire : AND-AUTO-002
(restrictions routières) est la seule candidate P0 de la mission et elle est
**indéterminée** — elle relève du BFF, hors de ce dépôt, et n'a pas pu être
recettée.

### P1 — critique

| ID | Titre | Fichiers |
|---|---|---|
| **AND-BUG-001** | Le geste de retour pendant un guidage ferme l'application, perd la navigation et laisse le service, le wake lock et le GPS haute précision tourner | [MapScreen.kt:630](feature/map/src/main/kotlin/io/aule/android/feature/map/MapScreen.kt:630), [MapScreen.kt:386](feature/map/src/main/kotlin/io/aule/android/feature/map/MapScreen.kt:386), [MainActivity.kt](app/src/main/kotlin/io/aule/android/MainActivity.kt) |
| **AND-BUG-002** | Une panne réseau au rafraîchissement de session **déconnecte** l'utilisateur et efface son jeton de rafraîchissement valide | [SupabaseAuthRepository.kt:73](data/src/main/kotlin/io/aule/android/data/aule/SupabaseAuthRepository.kt:73) |
| **AND-BUG-003** | Aucun recalcul d'itinéraire : sortir du tracé n'affiche qu'un bandeau « Rejoignez le tracé » | [MapViewModel.kt:1341](feature/map/src/main/kotlin/io/aule/android/feature/map/MapViewModel.kt:1341), [GuidanceBanner.kt:66](feature/map/src/main/kotlin/io/aule/android/feature/map/GuidanceBanner.kt:66) |
| **AND-BUG-004** | La production dépend du serveur de démonstration public d'OSRM pour **toutes** les manœuvres, sans hôte configurable | [OsrmRoadRouter.kt:52](data/src/main/kotlin/io/aule/android/data/aule/OsrmRoadRouter.kt:52), [AuleGraph.kt:210](app/src/main/kotlin/io/aule/android/AuleGraph.kt:210) |

### P2 — majeur

| ID | Titre | Fichiers |
|---|---|---|
| **AND-BUG-005** | Le profil piéton du routeur de manœuvres est ignoré par le serveur : les consignes piétonnes sont des consignes **voiture** (mesuré) | [Repositories.kt:136](core/model/src/main/kotlin/io/aule/android/core/model/repository/Repositories.kt:136) |
| **AND-BUG-006** | Rond-points : le numéro de sortie d'OSRM (`maneuver.exit`) n'est pas décodé ; la consigne est « Prendre le rond-point », sans plus | [OsrmDto.kt:39](data/src/main/kotlin/io/aule/android/data/dto/OsrmDto.kt:39), [Maneuvers.kt](core/model/src/main/kotlin/io/aule/android/core/model/Maneuvers.kt) |
| **AND-BUG-007** | Aucune vitesse affichée au conducteur, ni limitation, ni alerte | [MapHud.kt](feature/map/src/main/kotlin/io/aule/android/feature/map/MapHud.kt) |
| **AND-BUG-008** | L'arrivée n'arrête pas le guidage : FGS, wake lock 6 h et GPS haute précision continuent | [MapViewModel.kt:1314](feature/map/src/main/kotlin/io/aule/android/feature/map/MapViewModel.kt:1314), [JourneyProgress.kt:49](core/model/src/main/kotlin/io/aule/android/core/model/JourneyProgress.kt:49) |
| **AND-BUG-009** | `START_STICKY` sans `onTaskRemoved` : après un balayage du multitâche, Android relance un service zombie avec notification et wake lock, sans lecteur de position | [NavigatingForegroundService.kt:50](core/location/src/main/kotlin/io/aule/android/core/location/NavigatingForegroundService.kt:50) |
| **AND-BUG-010** | Le fond de carte dépend d'un service public gratuit sans clé ni garantie (`tiles.openfreemap.org`) ; hors ligne, il n'y a pas de fond de carte | [style-light.json](app/src/main/assets/map/style-light.json) |
| **AND-BUG-019** | Verrou portrait `android:screenOrientation="portrait"` : Android 16 l'ignore sur les grands écrans, et **tout le cadrage de la carte est calculé sur la hauteur** | [AndroidManifest.xml:38](app/src/main/AndroidManifest.xml:38), [MapCamera.kt](feature/map/src/main/kotlin/io/aule/android/feature/map/MapCamera.kt) |
| **AND-BUG-011** | **Zéro test instrumenté** : aucun écran, aucune caméra, aucun cycle de vie n'est couvert automatiquement — donc aucune des anomalies P1 ci-dessus n'aurait été rattrapée par la CI | `*/src/androidTest/` (vide) |

### P3 — mineur

| ID | Titre |
|---|---|
| ~~**AND-BUG-012**~~ | **Retiré — constat erroné.** `POST_NOTIFICATIONS` **est** demandée au démarrage d'un guidage : `startGuidance` reçoit un `requestNotifications` que le site d'appel branche sur le lanceur. Le premier relevé ne regardait que le `LaunchedEffect(serviceActive)` et avait manqué ce chemin. |
| **AND-BUG-013** | `AuthSession.isExpired` sans marge de sécurité : un jeton expirant dans deux secondes est jugé valide, et aucun intercepteur ne rattrape le 401 qui suit ([Auth.kt:21](core/model/src/main/kotlin/io/aule/android/core/model/Auth.kt:21)) |
| **AND-BUG-014** | Material 3 en `1.5.0-alpha26` en production (choix assumé, ADR à l'appui — la réserve reste) |
| **AND-BUG-015** | 13 appels à des API Material 3 dépréciées (`rememberModalBottomSheetState` ×5, surcharge `ListItem` ×8) |

### P4 — cosmétique

| ID | Titre |
|---|---|
| **AND-BUG-016** | `!!` inutile sur un receveur non nul ([MapScreen.kt:1141](feature/map/src/main/kotlin/io/aule/android/feature/map/MapScreen.kt:1141)) |
| **AND-BUG-017** | Nom de paramètre divergent du supertype dans un test (`AuthViewModelRecoveryTest.kt:256`) |
| **AND-BUG-018** | Le plugin `org.jetbrains.kotlin.android` est signalé déprécié par AGP 9.2.1 (choix assumé : `android.builtInKotlin=false`) |

---

## 5. Conclusion

### Décompte

| Statut | Nombre |
|---|---|
| `PASS` | 5 |
| `FAIL` | 5 |
| `PARTIAL` | 6 |
| `BLOCKED` | 17 |
| `NOT_IMPLEMENTED` | 3 |
| `NOT_APPLICABLE` | 1 |

| Criticité | Nombre |
|---|---|
| `P0` | **0 constaté** (1 indéterminé, hors périmètre client) |
| `P1` | **4** |
| `P2` | **8** |
| `P3` | **3** |
| `P4` | **3** (+ 1 chaîne morte, `permission_location_rationale`) |

### Décision : **NO GO** pour un trajet automobile prolongé

Et **GO AVEC RÉSERVES** pour l'usage consultation (carte, arrêts, horaires,
véhicules, favoris, relève, prise de service).

### Justification

La question posée est précise : *Aule Android est-elle assez stable pour une
utilisation réelle, notamment pendant un trajet automobile prolongé ?*

**Sur le fond du produit, la base est saine, et il faut le dire nettement.**
882 tests passent, la séparation des modules est tenue par le graphe de
dépendances et non par la relecture, la migration Material 3 est **acquise et
gardée**, l'internationalisation est complète au mot près, l'interpolation
cartographique est correctement sortie de l'état Compose, la détection de sortie
de parcours a une vraie hystérésis, et la caméra de navigation est l'une des
pièces les mieux pensées et les mieux testées que porte ce dépôt. Ce n'est pas
un prototype, et la documentation ne ment pas.

**Mais le trajet automobile prolongé bute sur quatre défauts P1, et trois
d'entre eux se déclenchent dans le cours normal d'un trajet :**

1. **Rater une sortie n'est pas rattrapé.** Il n'y a pas de recalcul. Le produit
   le sait et l'assume pour ce jalon — mais un GPS qui ne recalcule pas n'est pas
   un GPS de trajet prolongé, c'est un afficheur d'itinéraire. Sur une heure de
   route, la probabilité de ne jamais s'écarter du tracé est faible.

2. **Le geste de retour, au volant, casse tout en silence.** Pas de
   confirmation, navigation perdue, et — le pire — service de premier plan,
   wake lock de six heures et GPS haute précision qui continuent de tourner pour
   rien. C'est à la fois une perte de fonction et une fuite de batterie, sur le
   geste le plus banal d'Android.

3. **Une coupure réseau au mauvais moment déconnecte le conducteur.** Un jeton
   d'accès GoTrue vit une heure. Passé ce délai, tout relancement sans réseau —
   dépôt souterrain, parking, tunnel prolongé — efface un jeton de
   rafraîchissement pourtant valide et renvoie à un écran de connexion qui, sans
   réseau, ne peut pas aboutir. Le dépôt écrit lui-même qu'il ne faut jamais
   renvoyer un conducteur à la saisie de son mot de passe ; ce chemin le fait.

4. **Les manœuvres dépendent d'un serveur de démonstration public.** Sans SLA,
   sans clé, avec un usage en production exclu par ses conditions. Le jour où il
   limite le débit ou tombe, le bandeau de guidage retombe silencieusement sur
   le libellé de la jambe — l'application ne plante pas, elle cesse simplement de
   guider.

**S'y ajoutent deux manques qui, pris ensemble, disqualifient la conduite :**
pas de vitesse affichée, et des rond-points sans numéro de sortie. Un guidage
automobile qui dit « Prendre le rond-point » sans dire laquelle des cinq
branches n'aide pas à conduire.

**Enfin, et c'est ce qui pèse le plus dans la décision : rien de ce qui précède
n'a pu être vu à l'écran.** Aucun appareil n'était joignable, aucun émulateur
n'est installé, et le projet n'a **aucun test instrumenté**. Les quatre P1 ont
été établis par lecture du code, ce qui est solide pour des chemins de contrôle
aussi courts — mais la fluidité, les FPS, la 3D, la tenue thermique, la
consommation sur trente minutes, la récupération après tunnel, le comportement
Samsung/Pixel : tout cela est **entièrement inconnu**. On ne peut pas prononcer
un GO sur une application de conduite dont personne n'a mesuré le comportement
sur la route.

### Ce qu'il faut pour lever le NO GO

**Avant toute mise en production automobile :**

1. corriger AND-BUG-002 (déconnexion sur panne réseau) — quelques lignes, et
   c'est le défaut le plus injuste pour l'utilisateur ;
2. corriger AND-BUG-001 (retour pendant le guidage) — confirmation, ou arrêt
   propre du flux et du service ;
3. corriger AND-BUG-008 et AND-BUG-009 (fuites de wake lock) ;
4. héberger ou configurer un OSRM de production (AND-BUG-004), et le rendre
   injectable comme le commentaire le promet déjà ;
5. implémenter le recalcul (AND-BUG-003), ou décider explicitement que ce jalon
   ne vise pas la conduite — auquel cas le mode voiture devrait le dire.

**Puis, et sans quoi aucun rapport ne pourra conclure :**

6. brancher le S21 et rejouer intégralement les sections 6 à 9, 12 à 28, 31 et
   33 de la mission, avec Profiler sur un trajet de 30 à 60 minutes ;
7. poser un premier lot de tests instrumentés sur les chemins qui viennent de
   produire quatre P1 : cycle de vie du guidage, retour système, service de
   premier plan, restauration de session hors réseau.

**Sur ces quatre derniers points — 6 et 7 —, la campagne s'arrête faute
d'appareil, et non faute de temps.**


---

## 6. Correctifs appliqués

Les quatre P1 ont été corrigés le 28/08/2026, dans la foulée de la recette.
**897 tests passent, 0 échec** (882 avant, +15 écrits pour ces correctifs).
`assembleDevelopmentDebug` réussit, Lint reste à **0 erreur et 41
avertissements** — exactement le décompte d'avant, aucune alerte ajoutée —, les
catalogues `values` / `values-en` restent alignés au mot près, et les gardes
Material 3 et design system passent toutes avec leurs dettes vides.

Ce qui reste `BLOCKED` le reste : **aucun de ces correctifs n'a été vu tourner
sur un appareil.** Ils sont couverts par des tests JVM sur les modules purs et
les modèles d'écran — ce que ce dépôt sait tester — pas par un trajet.

### AND-BUG-001 — le geste de retour pendant un guidage

**Ce qui se passait.** `PredictiveBackHandler(enabled = sheetPresented || searchOpen)` :
en guidage sans volet, les deux étaient faux, le geste n'était pas intercepté et
l'activité se terminait. Le `MapViewModel` partait avec elle — donc
l'itinéraire — pendant que le flux de positions, le service de premier plan et
son verrou de six heures continuaient de tourner.

**Ce qui a été fait**, en deux temps :

1. **Le geste demande confirmation.** Le gestionnaire est maintenant armé aussi
   pendant le guidage, et le retour ouvre `StopGuidanceDialog` — le même
   dialogue que la déconnexion, pour la même raison : ce sont les deux seules
   actions qu'un doigt qui dérape au volant peut déclencher et qu'on ne peut pas
   défaire. Confirmer appelle `stopGuidance(…)`, qui referme proprement le
   modèle, **rend le palier de localisation** (donc arrête le service) et
   recadre la carte sur le trajet.
2. **Un dernier filet.** Un `DisposableEffect` arrête le guidage et le flux si
   la composition disparaît pour de bon. `onPauseOrDispose` ne pouvait pas s'en
   charger : il ne distingue pas une mise en arrière-plan — où le guidage doit
   **continuer** — d'un écran détruit. Le filet ne se déclenche pas sur un
   changement de configuration (`isChangingConfigurations`), sans quoi un
   basculement clair/sombre coûterait le trajet.

Fichiers : [MapScreen.kt](feature/map/src/main/kotlin/io/aule/android/feature/map/MapScreen.kt),
`nav_stop_confirm_*` dans les deux catalogues.

### AND-BUG-002 — la déconnexion par absence de réseau

**Ce qui se passait.** Deux endroits, pas un seul, et il fallait les deux pour
enfermer le conducteur dehors :

- `restore()` traitait **toute** panne comme un refus et effaçait le dépôt ;
- même en gardant la session, `loadAccount()` fermait la porte dès que
  `fetchStaffRole` échouait, sans distinguer un refus d'une injoignabilité.

**Ce qui a été fait :**

1. `restore()` ne purge plus que sur ce que le serveur a **explicitement**
   condamné (`REVOKING_FAILURES` : `invalid_grant` / `invalid_credentials`,
   e-mail non confirmé). Transport coupé, 5xx, 429, code inconnu : la session
   reste sur le disque et en mémoire, avec son jeton d'accès périmé. La liste
   est **positive** — le doute profite à la session, parce que la sanction est
   asymétrique : se tromper en gardant coûte un 401, se tromper en effaçant
   coûte un conducteur qui ne peut plus entrer.
2. `fetchStaffRole` lève désormais `AuthException(NETWORK)` sur transport, 5xx
   et 429. C'est la seule façon pour l'écran — qui ne voit pas `:core:network` —
   de distinguer « le serveur dit non » de « je n'ai pas pu demander ». Le
   contrat le dit maintenant noir sur blanc.
3. Nouveau contrat `AgentAccessStore` (`:core:model`) et son implémentation
   `PreferencesAgentAccessStore` (`:app`) : la dernière habilitation
   **accordée** est gardée sur l'appareil, rangée par identifiant de compte.
   `loadAccount` s'en sert quand la vérification est injoignable. C'est la règle
   d'ADR-012 appliquée aux droits : le local d'abord, le compte rattrape.

**Ce que ce correctif ne fait pas** — et c'est délibéré : un compte **jamais
vérifié sur cet appareil** reste dehors. La porte ne s'ouvre pas sur une absence
de donnée, elle s'ouvre sur un « oui » déjà prononcé ici. Un refus explicite
(`NO_HABILITATION`) et une déconnexion effacent la réserve.

Fichiers : [SupabaseAuthRepository.kt](data/src/main/kotlin/io/aule/android/data/aule/SupabaseAuthRepository.kt),
[AuthViewModel.kt](feature/auth/src/main/kotlin/io/aule/android/feature/auth/AuthViewModel.kt),
[Repositories.kt](core/model/src/main/kotlin/io/aule/android/core/model/repository/Repositories.kt),
[PreferencesAgentAccessStore.kt](app/src/main/kotlin/io/aule/android/auth/PreferencesAgentAccessStore.kt).

Tests : 4 dans `SupabaseAuthRepositoryTest` (503 garde, 429 garde,
`invalid_grant` efface, rôle injoignable → `NETWORK`), 4 dans
`AuthViewModelOfflineAccessTest` (ouvre sur la réserve, refuse sans réserve,
efface sur refus, constitue la réserve puis la rend à la déconnexion).

### AND-BUG-003 — le recalcul d'itinéraire

**Ce qui se passait.** La sortie de tracé était détectée depuis toujours — trois
mesures au-delà du seuil, avec hystérésis — et ne servait qu'à écrire
« Rejoignez le tracé ».

**Ce qui a été fait.** `OffRouteDetector` déclenche maintenant un vrai calcul,
depuis la position courante vers la destination retenue, dans le mode courant.

Trois décisions valent d'être dites :

1. **Le trajet d'avant reste à l'écran pendant le calcul.** On ne vide rien
   avant d'avoir mieux : l'ancien tracé est faux, mais il est *orienté*, et un
   écran nu à quatre-vingt-dix à l'heure est pire qu'un tracé périmé. Si le
   moteur ne répond pas, on n'a rien perdu — le bandeau retombe sur « Vous avez
   quitté l'itinéraire ».
2. **Une temporisation de 12 s en plus de l'hystérésis.** `OffRouteDetector`
   remet son compteur à zéro en déclenchant : trois secondes plus tard il peut
   redéclencher, et un moteur en panne se ferait appeler toutes les trois
   secondes pendant une heure. La temporisation se compte sur l'**horodatage
   GPS**, pas sur l'horloge de l'application : la boucle ne connaît que des
   mesures, et un test n'a alors pas d'horloge à injecter.
3. **Revenir sur le tracé annule le calcul en vol.** Un conducteur qui se
   rattrape tout seul ne doit pas voir son tracé sauter une seconde plus tard.

Le bandeau porte un troisième état, `nav_recalculating`, qui passe **devant** la
sortie de tracé : les deux sont vrais en même temps, mais « on s'en occupe » est
plus utile au volant que « vous vous êtes trompé ». Le commentaire de
`GuidanceBanner` qui affirmait « Pas de « Recalcul » : ce jalon n'en fait pas »
a été réécrit.

Fichiers : [MapViewModel.kt](feature/map/src/main/kotlin/io/aule/android/feature/map/MapViewModel.kt),
[GuidanceBanner.kt](feature/map/src/main/kotlin/io/aule/android/feature/map/GuidanceBanner.kt),
`nav_recalculating*` dans les deux catalogues.

**Un mode reste volontairement à l'écart : le transport.** En bus ou en tram, un
écart de trente mètres n'est pas une erreur de trajet — c'est une géométrie de
ligne approximative, ou une contre-allée. Rendre alors un autre itinéraire, avec
une autre correspondance, à quelqu'un **assis dans le bon véhicule** serait bien
pire que le bandeau. Le recalcul ne vaut donc que pour la voiture et la marche,
où « refaire le trajet depuis ici » veut dire quelque chose.

Tests : 5 dans `MapGuidanceViewModelTest` (le recalcul part et prend la place, un
moteur muet laisse le trajet, deux sorties rapprochées ne lancent qu'un calcul,
revenir sur le tracé annule, un trajet en transport ne se recalcule pas).

> **Un piège rencontré en écrivant ces tests, et qui vaut d'être noté.**
> La progression n'avance que de `PolylineProjection.FORWARD_WINDOW` — 12 % du
> tracé — par mesure. Un point d'écart choisi au milieu du trajet est donc
> **hors fenêtre** au premier coup : la projection se colle au bord et rend une
> déviation d'un kilomètre, qui n'est pas celle qu'on croit mesurer. Le premier
> jet du test passait pour la mauvaise raison. C'est écrit dans le test.

### AND-BUG-004 — la dépendance au serveur de démonstration OSRM

**Ce qui se passait.** `OsrmRoadRouter` portait `router.project-osrm.org` en
paramètre **par défaut**, et rien ne l'injectait : les trois flavors partaient
dessus. Le commentaire promettait un hôte configurable ; il ne l'était pas.

**Ce qui a été fait :**

1. Le paramètre `origin` n'a plus de défaut. Un défaut est une décision que
   personne ne prend.
2. L'adresse remonte jusqu'à `AppConfig.roadRouterOrigin`, alimentée par un
   `buildConfigField` qui lit `aule.osrmOrigin` dans `local.properties` — donc
   par machine et par flavor, sans toucher au code. Elle est **refusée si elle
   n'est pas en HTTPS**, comme l'API : elle porte la destination du conducteur.
3. `AppConfig.usesPublicDemoRouter` reconnaît le repli, et `AuleGraph` le
   **journalise en avertissement au démarrage**. La dépendance qu'on ne maîtrise
   pas est désormais visible dans `logcat` plutôt que découverte en roulant.

**Ce que ce correctif ne fait pas.** Il ne fournit pas de serveur : le repli
reste `router.project-osrm.org` tant qu'Aule n'héberge pas le sien. Héberger ou
souscrire un OSRM est une tâche d'exploitation, hors de ce dépôt — mais le jour
où elle est faite, il n'y a plus une ligne de code à écrire.

Fichiers : [AppConfig.kt](core/common/src/main/kotlin/io/aule/android/core/common/config/AppConfig.kt),
[OsrmRoadRouter.kt](data/src/main/kotlin/io/aule/android/data/aule/OsrmRoadRouter.kt),
[AuleGraph.kt](app/src/main/kotlin/io/aule/android/AuleGraph.kt),
[app/build.gradle.kts](app/build.gradle.kts).

Tests : 2 dans `AppConfigTest` (un hôte en clair est refusé, le repli se
reconnaît — barre finale comprise).

---

---

## 6 bis. Les P2, corrigés

Reprise le 28/08/2026, après les P1. Six des huit P2 sont traités dans le code ;
les deux autres ne relèvent pas de ce dépôt et sont dits comme tels. Le total
passe de 897 à **908 tests, 0 échec**, et Lint reste à **0 erreur, 41
avertissements** — pas une alerte ajoutée.

### AND-BUG-005 — les manœuvres d'un autre trajet

**Ce qui se passait.** Les manœuvres viennent d'un second serveur, interrogé
pour la même paire de points — mais rien ne vérifiait qu'il réponde pour le même
**trajet**. Sur le serveur public, `driving`, `walking` et `foot` rendent la même
réponse : une jambe à pied recevait des consignes de voiture. Le symptôme était
silencieux, et c'est ce qui le rendait grave — l'agrafage écarte ce qui tombe à
plus de 25 m du tracé, donc la plupart des manœuvres disparaissaient sans un
mot, **et celles qui coïncidaient par hasard restaient, fausses**.

**Ce qui a été fait.** `roadRouteDescribesLeg(roadMeters, paintedMeters)` :
avant d'agrafer quoi que ce soit, on compare la longueur rendue par le routeur à
celle de la jambe qu'on peint. Deux routeurs qui décrivent le même chemin
s'accordent sur sa longueur ; deux routeurs qui décrivent des chemins différents
s'en écartent tout de suite — 713 m contre 1 199 m sur le relevé nantais de
`Route.kt`. Au-delà de 25 % d'écart (avec une marge absolue de 50 m pour les
jambes courtes, où deux routeurs ont le droit de ne pas être d'accord sur le
côté de la rue), le lot entier est écarté et **journalisé**.

C'est un test à un nombre, sans géométrie à comparer, et il vaut aussi pour la
voiture : un OSRM qui contourne un chantier que le BFF ignore décrit lui aussi
un autre trajet.

Fichiers : [Maneuvers.kt](core/model/src/main/kotlin/io/aule/android/core/model/Maneuvers.kt),
[MapViewModel.kt](feature/map/src/main/kotlin/io/aule/android/feature/map/MapViewModel.kt).
Tests : 4 dans `ManeuversTest`, 1 dans `MapGuidanceViewModelTest`.

### AND-BUG-006 — le numéro de sortie des rond-points

`exit` est décodé du DTO OSRM, porté par `RoadManeuver` → `PinnedManeuver` →
`NextAction`, et formulé à l'écran : « Prendre la 3e sortie ».

**Les ordinaux sont énumérés dans les ressources, pas calculés en Kotlin**, et
c'est délibéré : `1re` est féminin (une sortie) là où ICU rend `1er`, et
l'anglais change de suffixe jusqu'au quatrième. Cinq formes par langue couvrent
tout ce qui existe ; au-delà de vingt sorties on retombe sur la phrase nue,
parce que ce n'est plus un rond-point mais une donnée aberrante.

Fichiers : [OsrmDto.kt](data/src/main/kotlin/io/aule/android/data/dto/OsrmDto.kt),
[Maneuvers.kt](core/model/src/main/kotlin/io/aule/android/core/model/Maneuvers.kt),
[NextAction.kt](core/model/src/main/kotlin/io/aule/android/core/model/NextAction.kt),
[DomainText.kt](feature/map/src/main/kotlin/io/aule/android/feature/map/DomainText.kt).
Tests : 1 dans `OsrmRoadRouterTest` (nouvelle fixture `osrm-roundabout.json`),
1 dans `ManeuversTest`.

### AND-BUG-007 — la vitesse

Un cadran compact dans la bande basse, sous le bandeau de consigne et au-dessus
de la barre d'arrivée : c'est un chiffre qu'on prend en vision périphérique, pas
qu'on cherche.

Deux décisions à noter :

- **Zéro, jamais rien.** `TransportVehicle.speedKmh` rend `null` sous 1 m/s —
  sur la fiche d'un bus, « À l'arrêt » dit mieux la chose que « 2 km/h ». Ici
  c'est l'inverse : un cadran qui disparaît au feu rouge est un cadran cassé.
  `drivingSpeedKmh` rend donc 0, et le seuil est celui du cap (0,7 m/s), en
  dessous duquel le GPS ne mesure plus une vitesse mais sa propre dérive.
- **La bande basse est mesurée entière**, cadran compris : c'est elle qui dit à
  la caméra quelle hauteur d'écran est masquée. Ne remonter que la barre ferait
  poser le puck derrière le cadran.

Il n'y a **pas** de limitation de vitesse ni d'alerte : la donnée n'existe ni
dans le BFF ni dans le style de carte. C'est une fonction à part entière, pas la
correction d'un défaut.

Fichiers : [TripSummary.kt](core/model/src/main/kotlin/io/aule/android/core/model/TripSummary.kt),
[MapViewModel.kt](feature/map/src/main/kotlin/io/aule/android/feature/map/MapViewModel.kt),
[MapHud.kt](feature/map/src/main/kotlin/io/aule/android/feature/map/MapHud.kt).
Tests : 3 dans `TripSummaryTest`, 1 dans `MapGuidanceViewModelTest`.

### AND-BUG-008 — l'arrivée qui ne rendait rien

À l'arrivée, le **palier** de localisation retombe : le service de premier plan
s'arrête, le verrou de six heures est rendu, l'arrière-plan cesse.

Le guidage, lui, **n'est pas coupé** : la fiche d'arrivée et le tracé restent,
parce qu'on veut encore les regarder. Seul ce qui coûte s'en va. C'est la
différence entre « c'est fini » et « ça continue de consommer ».

Fichier : [MapScreen.kt](feature/map/src/main/kotlin/io/aule/android/feature/map/MapScreen.kt).

### AND-BUG-009 — le service fantôme

`START_NOT_STICKY` au lieu de `START_STICKY`, plus `onTaskRemoved { stopSelf() }`.

Ce service ne lit aucune position : il ne fait que tenir le processus vivant
pour `FusedLocationProvider`. Relancé seul après que le système a tué le
processus, il n'a plus rien à garder en vie — mais il reconstruisait sa
notification et reprenait son verrou. Android relance de surcroît avec un
`intent` nul, ce qui faisait retomber `onDuty` à faux : la notification mentait
aussi sur ce qu'elle gardait.

Fichier : [NavigatingForegroundService.kt](core/location/src/main/kotlin/io/aule/android/core/location/NavigatingForegroundService.kt).

### AND-BUG-019 — le verrou portrait : **re-noté P3, après vérification**

Le premier relevé disait que « toute l'arithmétique de cadrage » tournerait avec
l'écran. **C'est inexact, et il faut le corriger.** `MapController.viewportHeightDp`
lit la hauteur **mesurée** de la vue (`view.height / density`), et la hauteur du
volet vient de la même mesure. Le cadrage est donc déjà piloté par le viewport
réel, pas par une constante portrait ; les détentes du volet sont elles aussi
des fractions de `BoxWithConstraints`.

En paysage sur un grand écran, le cadrage serait donc **dégradé** — une bande
plus basse, un décalage avant plus court — mais pas faux. Cela ne justifie pas
de restructurer la caméra à l'aveugle, sans appareil pour juger du résultat.
**Requalifié P3**, à trancher sur tablette le jour où il y en a une.

### AND-BUG-010 — les tuiles : **non corrigé, et volontairement**

Le fond de carte vient de `tiles.openfreemap.org`, écrit en dur dans les deux
styles d'`assets`. Le rendre configurable comme l'hôte OSRM demanderait de lire
le style, d'y remplacer l'URL de la source et de le poser par `fromJson` plutôt
que `fromUri` — c'est-à-dire de **toucher au chemin de chargement du style**,
celui dont le projet documente en premier qu'un rechargement y vide sources,
couches et images en silence.

Je ne fais pas cette modification sans un appareil pour la voir tourner. Le
changement tient en quelques lignes le jour où le S21 est branché ; le risque de
le faire à l'aveugle ne vaut pas le gain. **Reste ouvert, P2.**

### AND-BUG-011 — les tests instrumentés : **partiellement adressé**

Les chemins corrigés sont couverts par des tests JVM — c'est ce que ce dépôt
sait exécuter, et c'est ce qui a permis de vérifier les onze correctifs. Mais un
test Compose UI reste hors de portée ici : il exige soit un appareil
(`connectedAndroidTest`, et il n'y en a pas), soit Robolectric — donc JUnit 4 à
côté de JUnit 5, une dépendance et un choix d'architecture qui méritent un ADR,
pas une décision prise au fil d'une recette. **Reste ouvert, P2.**

### Au passage — AND-BUG-013 (P3)

`AuthSession.isExpired` gagne une marge d'une minute. Sans elle, un jeton
expirant dans deux secondes était jugé valide au démarrage et le premier appel
PostgREST prenait un 401 que rien ne rattrape. La marge est directement liée aux
correctifs d'authentification ci-dessus, d'où sa présence ici.


---

## 6 ter. Les P3 et P4

Dernière passe, le 28/08/2026. Deux chiffres la résument : **Android Lint passe
de 41 à 17 avertissements**, et les **avertissements de compilation de 22 à 15**.

Ce qui reste tient en deux lignes. Côté Lint, seize des dix-sept sont « une
version plus récente existe » — des décisions de montée de version, pas des
défauts — et le dix-septième est un faux positif démontré. Côté compilation, les
quinze sont **la même** dépréciation de `ListItem`, laissée délibérément.

### Corrigé

| Anomalie | Correction |
|---|---|
| `UseKtx` ×14 | Les huit dépôts `SharedPreferences` passent à `prefs.edit { … }`. |
| **AND-BUG-015** — `rememberModalBottomSheetState` déprécié ×6 | Migré vers `rememberBottomSheetState(initialValue = Hidden, enabledValues = setOf(Hidden, Expanded))`. Cinq volets dans `:feature:map`, un dans `:feature:auth` — ce dernier absent du premier relevé. |
| Appel sûr inutile ×6 | `Response.body` n'est plus nullable en OkHttp 5 (`AuleHttpClient` ×3) ; `next` est déjà déduit non nul par `isTransfer` (`NextAction` ×3). |
| `ObsoleteSdkInt` ×2 | Deux gardes `SDK_INT < O` toujours fausses depuis `minSdk 26`, retirées avec leur import. |
| `UnusedResources` | `permission_location_rationale` supprimée des deux catalogues : la justification affichée est celle de `WelcomeScreen`. |
| `LogNotTimber` ×4 | `@Suppress` sur `AndroidLogger`, avec la raison dans son KDoc. |
| `LockedOrientationActivity`, `DiscouragedApi`, `UnusedAttribute` | `tools:ignore` dans le manifeste, chacun avec le commentaire qui dit pourquoi. |
| **AND-BUG-016** | `menuSheet!!()` — `menuOpen` porte déjà le test de nullité. |
| **AND-BUG-017** | `profileId` renommé `driverId`, comme le supertype. |

Sur la migration des volets, un mot : elle est **prouvée à comportement
identique**, et pas seulement annoncée par le `ReplaceWith` de la bibliothèque.
`SheetState.skipPartiallyExpanded` dérive de `enabledValues`, et les trois
fonctions qui lisent le drapeau `isBottomSheetPartiallyExpandedDeterministicEnabled`
— dont la valeur par défaut change entre les deux API — court-circuitent toutes
sur l'absence du palier `PartiallyExpanded`. Avec `setOf(Hidden, Expanded)`, ce
drapeau ne peut pas mordre. Vérifié dans les sources de `material3-1.5.0-alpha26`,
fonction par fonction.

Les trois `tools:ignore` méritent aussi une phrase, parce qu'ils ne « corrigent »
rien : un avertissement qu'on laisse s'accumuler est un avertissement que tout le
monde apprend à ne plus lire. Le taire **avec sa raison, à l'endroit exact où la
décision s'applique**, c'est la consigner ; la laisser traîner dans un rapport,
c'est l'oublier.

### Non corrigé, et pourquoi

**`ListItem(headlineContent = …)` déprécié ×8 — laissé.** Le message de
dépréciation dit « surcharge où `headlineContent` devient un lambda final », ce
qui laisse croire à un simple déplacement de paramètre. C'est faux : la nouvelle
surcharge appelle `InteractiveListItem` et prend `shapes`, `elevation`,
`contentPadding`, `verticalAlignment` là où l'ancienne posait `tonalElevation` et
`shadowElevation` sur une `Surface`. C'est un autre composant, avec d'autres
défauts visuels, sur huit listes que je ne peux pas regarder. À faire avec
l'appareil sous les yeux.

**`ObsoleteSdkInt` sur `mipmap-anydpi-v26` — laissé, et Lint a tort.** Lint
demande de fusionner le dossier dans `mipmap-anydpi` puisque `minSdk` vaut 26.
Essayé, au propre, cache de ressources vidé :

```
AAPT: error: resource mipmap/ic_launcher (aka io.aule.android.development:mipmap/ic_launcher) not found.
```

`anydpi` seul ne fournit aucune configuration par défaut pour AAPT — il n'y a
pas d'autre dossier `mipmap-*` dans ce projet. Le qualificateur `-v26` reste, et
il est nécessaire. C'est le seul des dix-sept avertissements restants qui ne soit
pas une montée de version.

**Les seize montées de version — laissées, et ce n'est pas de la paresse.**

- `AndroidGradlePluginVersion` ×4 : le catalogue de versions **explique** pourquoi
  AGP reste en 9.2.1 — la 9.3.x exige une distribution Gradle absente de cette
  machine. Passer outre casserait le build de quelqu'un d'autre.
- `NewerVersionAvailable` ×4 : dont **MapLibre 13.5.0 → 13.5.1**. Le moteur
  cartographique est l'endroit du projet où une régression ne se voit pas dans
  les tests. Un correctif de patch se prend avec un écran devant soi.
- `GradleDependency` ×7 : AndroidX en retard d'un cran. Routine, mais une montée
  de version n'est pas la correction d'un défaut, et aucune ne se valide ici.
- `OldTargetApi` ×1 : monter `targetSdk` change le comportement du système
  vis-à-vis de l'application. C'est une campagne à soi seule.

**AND-BUG-014 — Material 3 en `1.5.0-alpha26` : non corrigeable, et c'est écrit
dans le catalogue.** En sortir voudrait dire renoncer à `MaterialExpressiveTheme`,
`MaterialShapes`, `ButtonGroup`, `LoadingIndicator` et aux slots typographiques
appuyés, tous `internal` ou absents en 1.4.0 stable — c'est-à-dire réécrire à la
main ce que le kit fait déjà. La réserve reste entière : une alpha en production
est un risque de régression à chaque montée.

**AND-BUG-018 — le plugin Kotlin déprécié par AGP 9 : non corrigé, sur consigne.**
`CLAUDE.md` est explicite : « `android.newDsl=false`, `android.builtInKotlin=false`
[…] c'est la combinaison éprouvée sur cette machine — ne pas basculer sans
raison. » Une ligne d'avertissement au démarrage du build n'est pas une raison.


---

## 6 quater. La campagne sur appareil

Le S21 a été branché le 28/08/2026 à 09h28. `adb devices` ne le voyait pas au
premier essai — le démon n'était pas démarré ; un `adb kill-server` / `start-server`
a suffi.

### Ce que l'appareil a permis de vérifier

| | Résultat |
|---|---|
| **AND-001** — lancement à froid | `TotalTime: 908 ms`, `LaunchState: COLD`, aucun plantage. |
| **Carte, 3D, interpolation** | Bâtiments en volume, eau, végétation, labels, arrêts, puck avec son cône. Journal : **interpolation 119 Hz, rendu 116 à 185 ips, coût moyen 325-528 µs pour un budget de 8 333 µs à 120 Hz.** L'ADR-006 tient sur l'appareil. |
| **Favoris, arrêts, recherche** | Domicile / Travail persistés, « À proximité » avec Ranzay 150 m et Terray 200 m et leurs lignes. Le volet d'itinéraire porte bien **les trois durées à la fois** — Transports 16, À pied 56, Voiture 9. |
| **Mode voiture** | Tracé en ruban plein sur la voirie, caméra `NAVIGATION`, inclinaison plafonnée à 59,99° par le moteur et journalisée comme prévu (ADR-009). |
| **AND-BUG-002** — session hors réseau | **Vérifié.** Voir ci-dessous. |
| **AND-BUG-001** — retour pendant le guidage | **Vérifié.** |
| **AND-BUG-009** — balayage du multitâche | **Vérifié.** |
| **AND-BUG-006** — sortie de rond-point | **Vérifié à l'écran** : « Prendre la 2e sortie ». |
| **AND-BUG-007** — vitesse | **Vérifié à l'écran** : « 0 km/h », à l'arrêt, sans disparaître. |
| **§ 23** — arrière-plan et écran verrouillé | **Vérifié.** |

### AND-BUG-002, en conditions réelles

L'appareil portait une session dont le jeton d'accès était **périmé depuis
7 h 24** — exactement le cas du rapport. Le protocole, après sauvegarde de la
session sur l'appareil :

1. lancement avec Wi-Fi → le jeton se rafraîchit, la carte s'ouvre, et la
   **réserve d'habilitation se constitue** (`io.aule.android.auth.access.xml`
   apparaît) ;
2. `expires_at` remis dans le passé, mode avion, relancement.

`ping` confirme l'absence totale de réseau (`unknown host www.aule.fr`). Le
journal, mot pour mot :

```
W Aule.Auth: Session conservée sans vérification — refus non définitif (NETWORK).
W Aule.Auth: Fiche agent illisible.
W Aule.Auth: Habilitations injoignables — ouverture sur la dernière connue (MIXTE).
```

Les deux moitiés du correctif ont tiré. **La carte s'est ouverte**, avec ses
2 635 arrêts servis depuis le disque, le bandeau disant « Positions non
rafraîchies » et l'avatar retombé sur les initiales — la signature visible de
`profileFailed`. Les deux jetons étaient toujours sur le disque.

Réseau rétabli, relancement : `expires_at` reprend une valeur fraîche. **La
session s'est réparée seule, sans que personne retape quoi que ce soit** —
ce qui est tout l'objet de la garder plutôt que de l'effacer.

### AND-BUG-001 et AND-BUG-009

Guidage engagé vers Travail (4,8 km, 9 min), le service et le verrou sont bien
là : `isForeground=true types=0x00000008` (location), notification sur
`aule_navigating_v1`, et `PARTIAL_WAKE_LOCK 'aule:navigating' ACQ=-7s978ms`.

**Retour** → le dialogue « Arrêter le guidage ? » s'ouvre, et
`mCurrentFocus` reste `MainActivity` : l'activité ne se termine plus. « Arrêter »
la referme proprement — service à **0**, verrou à **0**, caméra recadrée sur le
trajet. « Continuer » la laisse tourner.

**Balayage du multitâche**, guidage relancé → la carte Aule disparaît des
récents, le bandeau Samsung « 1 application active » avec elle, et service et
verrou retombent à zéro. `onTaskRemoved` fait son travail.

### § 23 — arrière-plan et écran verrouillé

Accueil puis extinction de l'écran, guidage engagé : `mWakefulness=Dozing`, et
le service comme le verrou **tiennent**. Surtout, `dumpsys location` montre la
demande toujours active :

```
ProviderRequest[@+1s0ms, HIGH_ACCURACY, WorkSource{10432 io.aule.android.development}]
```

Une position par seconde, en haute précision, écran éteint. C'est ce que le
rapport ne pouvait qu'espérer.

### Deux défauts que seul l'écran pouvait montrer

La campagne a trouvé **deux régressions dans les correctifs eux-mêmes**, toutes
deux invisibles aux 908 tests :

1. **Le dialogue d'arrêt rendait en violet Material**, pas en teal Aule : il
   était composé juste avant `AuleTheme` au lieu d'être dedans. Au milieu d'une
   application entièrement teal, il ressemblait à un dialogue système.
2. **Le cadran de vitesse passait sous la pastille ⓘ** — le « 0 » disparaissait
   derrière elle. Les deux extrémités de la bande basse sont prises ; le cadran
   est passé au milieu, par alignement et non par une marge chiffrée.

Les deux sont corrigées et revérifiées à l'écran. C'est l'argument le plus net
de tout ce rapport en faveur des tests instrumentés (AND-BUG-011) : deux défauts
d'interface, dans du code écrit et relu le jour même, qu'aucune suite JVM ne
pouvait attraper.

### Ce qui reste bloqué, et pourquoi

**Le déplacement réel.** Les sections 6 (GPS en mouvement), 16 à 22 (virages,
rond-points en situation, recalcul déclenché, vitesse à 30 et 50 km/h) et 35
(scénario complet) exigent de rouler. La position simulée n'est pas accessible
par `adb` sur un appareil non rooté : il faut une application de position
factice désignée dans les options de développement.

**L'écran, à partir de 09h41.** Le téléphone s'est verrouillé pendant le test
d'arrière-plan et demande un code (`mCurrentFocus=Bouncer`, `deviceLocked=1`).
Je n'en saisis pas. Tout ce qui suit — mode sombre à l'écran, grande taille de
texte, TalkBack, profilage sur trente minutes, § 26 constructeurs — attend que
l'appareil soit déverrouillé.

L'appareil a été rendu dans l'état où il a été trouvé : mode nuit remis à `no`,
mode avion à `0`, sauvegarde de test effacée, application arrêtée, service et
verrou libérés.


## 7. Décision révisée

**GO AVEC RÉSERVES** pour un trajet automobile prolongé — au lieu du `NO GO` de
la § 5 — **sous une condition qui n'est pas négociable : une campagne sur
appareil avant toute mise en production.**

### Ce qui a changé

**Les quatre P1 sont traités**, et les trois qui se déclenchaient dans le cours
normal d'un trajet ne se déclenchent plus : rater une sortie est rattrapé, le
geste de retour ne coûte plus le trajet ni la batterie, une coupure réseau ne
déconnecte plus. Le quatrième — la dépendance OSRM — reste une dépendance, mais
elle est devenue un **choix de configuration visible** au lieu d'un défaut
oublié.

**Six des huit P2 aussi.** Les deux qui pesaient sur la conduite sont comblés :
la vitesse s'affiche, les rond-points disent quelle sortie prendre. Les deux
fuites de veille — à l'arrivée, au balayage du multitâche — sont fermées. Et le
défaut le plus insidieux de la liste, les manœuvres d'un autre trajet posées en
silence sur le tracé, a maintenant une garde chiffrée et journalisée.

### Ce qui reste, et pourquoi

| | |
|---|---|
| **AND-BUG-010** — tuiles non configurables | `P2`. Corrigeable en quelques lignes, mais il faut toucher au chargement du style — le seul endroit du projet dont la documentation dit qu'un changement anodin y détruit tout en silence. **Pas à l'aveugle.** |
| **AND-BUG-011** — aucun test instrumenté | `P2`. Exige un appareil, ou Robolectric — donc JUnit 4 à côté de JUnit 5, ce qui est une décision d'ADR. |
| **AND-BUG-019** — verrou portrait | Requalifié `P3` : le cadrage lit la hauteur mesurée de la vue, pas une constante. Dégradé en paysage, pas faux. |
| P3 / P4 restants | Material 3 en alpha, appels dépréciés, chaîne morte, avertissements de compilation. Aucun n'a d'effet à l'exécution. |

### Ce qui empêche toujours un `GO` sec

**Le trajet lui-même.** La campagne du § 6 quater a levé la première moitié de
la réserve : l'application a été vue tourner, les quatre correctifs les plus
lourds sont vérifiés à l'écran, la fluidité est mesurée (119 Hz d'interpolation,
116-185 ips, 325-528 µs pour un budget de 8 333), et le guidage tient écran
éteint avec une position par seconde. Ce n'est plus une inconnue.

Reste ce qui exige de **rouler** : GPS en mouvement, virages, rond-points en
situation, recalcul déclenché pour de vrai, vitesse à 30 et 50 km/h, et le
scénario complet du § 35. La position simulée n'est pas accessible par `adb` sur
un appareil non rooté ; il faut soit une application de position factice, soit
un véhicule.

Et la campagne a rappelé pourquoi c'est nécessaire : elle a trouvé **deux
défauts d'interface dans les correctifs eux-mêmes**, écrits et relus le jour
même, qu'aucun des 908 tests ne pouvait voir.

### La marche à suivre pour lever la réserve

1. **Rouler.** C'est la seule chose qui manque encore : sortie de tracé
   volontaire pour voir le recalcul, plusieurs virages et rond-points, vitesse
   comparée à une référence, arrivée réelle (la notification doit disparaître),
   et le scénario complet du § 35 sur 30 à 60 minutes, Profiler ouvert.
2. **Déverrouiller le téléphone** pour la poignée de vérifications d'écran
   restées en suspens : mode sombre, grande taille de texte, TalkBack.
3. Décider d'AND-BUG-010 et d'AND-BUG-011 — les deux seuls P2 restants. Le
   second a maintenant un argument de terrain : deux régressions d'interface
   trouvées par une capture, aucune par les tests.
