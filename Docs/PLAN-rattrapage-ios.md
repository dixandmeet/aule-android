# Rattraper l'iOS — plan de mise à niveau d'Aule Android

## Contexte

L'app iOS (`../Native`) a pris de l'avance sur des pans entiers du produit : le Guet,
le mode Padeur, le réseau hors ligne, et une poignée de finitions de compte et de carte.
Android, de son côté, a de l'avance ailleurs — relève (`handover`), prise de service,
signalements, guidage piéton avec traces GPS, ~560 tests exécutés contre 450. Ce plan ne touche
pas à cette avance : il comble l'écart dans l'autre sens, dans l'ordre choisi,
avec une seule inversion argumentée (§ Ordre).

Tout ce qui est affirmé sur `Kotlin/` a été relu dans le code. Tout ce qui est affirmé
sur `Native/` vient de la lecture des sources iOS voisines, et sert de source de portage.
Le support PMTiles du binaire MapLibre (lot 2) a été vérifié indépendamment : le
`libmaplibre.so` de l'AAR `android-sdk-opengl:13.5.0` contient `PMTilesFileSource`
et la chaîne `pmtiles://`.

## L'écart, en un tableau

| Fonction iOS | État Android |
|---|---|
| Mot de passe oublié (`PasswordRecoveryViews.swift`) | **absent** — `AuthRepository` n'a ni `recover` ni changement de mot de passe |
| Mentions légales OSM/ODbL (`LegalNoticeSheet.swift`) | **absent** — aucune attribution nulle part (obligation ODbL) |
| Historique des 8 derniers lieux (`SearchHistory.swift`) | **absent** |
| Trois modes de déplacement (`TravelMode.swift`) | **partiel** — `RouteMode` n'a que `TRANSIT` et `CAR` |
| Accueil qui explique la localisation (`OnboardingView.swift`) | **absent** — la permission est demandée à froid ([MapScreen.kt](feature/map/src/main/kotlin/io/aule/android/feature/map/MapScreen.kt)) |
| Tracés du réseau PMTiles hors ligne (`TransitLinesLayer.swift`) | **absent** |
| Inventaire des lignes hors ligne (`TransitLineIndex`, `NetworkLinesSheet`, `LineDetailSheet`) | **absent** — `LineDepartureSheet` est la fiche d'une ligne *à un arrêt*, autre chose |
| Cache disque arrêts et lignes (`CachedStopRepository.swift`) | **absent** — `allStops()` retombe sur le réseau à chaque lancement |
| Le Guet (13 fichiers purs + 10 vues + widget + intents) | **~15 %** — `DepartureWatch` couvre l'armement manuel d'une veille sur une ligne, et sa notification |
| Mission / mode Padeur (7 fichiers purs + 6 vues) | **absent** — plan technique écrit ([Docs/PLAN-mode-padeur.md](PLAN-mode-padeur.md)), aucun code |

Déjà à parité, à ne pas rouvrir : carte jour/nuit + relief, flotte interpolée, arrêts
lieux/quais, 5 modes caméra (`CameraMode`), fiche d'arrêt, grille horaire, fiche véhicule
et plan de course, « Autour de vous », recherche arrêts-d'abord + géocodeur, itinéraires,
inscription en sept étapes, habilitations, trois environnements.

**Divergences assumées, hors périmètre** : maillages 3D Metal (Android peint des symboles),
Live Activity et App Intents/Siri (pas d'équivalent identique — traités en § Lot 3.5),
photo de profil (iOS la garde locale, Android la publie déjà dans Storage — Android reste).

## Ordre

Ordre retenu : petits manques, hors-ligne, Guet, Padeur. Une seule inversion par rapport
au souhait initial : **le hors-ligne passe avant le Guet**, parce que les réglages du Guet
demandent la liste des lignes suivies, et que cette liste est précisément l'inventaire
hors ligne (`TransitLineIndex`). Le faire après obligerait à câbler les réglages deux fois.

---

## Lot 1 — Les petits manques

### 1.1 Mot de passe oublié
- `AuthRepository` ([Repositories.kt](../core/model/src/main/kotlin/io/aule/android/core/model/repository/Repositories.kt)) : ajouter `sendPasswordRecovery(email)` et `updatePassword(session, newPassword)`.
- `SupabaseAuthRepository` : `POST /auth/v1/recover` avec le même `redirect_to` PKCE que `signUpProfessional`, puis `PUT /auth/v1/user`. Réutiliser `Pkce` et `AuthException`.
- Deep link : `AuleGraph.offerAuthCallback` reçoit déjà l'URI ; distinguer `type=recovery` pour router vers l'écran « nouveau mot de passe » au lieu de la carte.
- Vues : `feature/auth/PasswordRecoveryScreen.kt` (demande + nouveau mot de passe), entrée depuis `AuthScreen.kt`. Port de `PasswordRecoveryViews.swift`.
- Tests : `SupabaseAuthRepositoryTest` (MockWebServer, 2 cas + refus), `AuthViewModel`.

### 1.2 Mentions légales
- `feature/map/LegalNoticeSheet.kt` + pastille ⓘ dans [MapHud.kt](../feature/map/src/main/kotlin/io/aule/android/feature/map/MapHud.kt), grammaire `SheetChrome.kt`.
- Textes OSM/ODbL en `res/values/` et `res/values-en/` (ADR-011). Port de `LegalNoticeSheet.swift`.

### 1.3 Historique de recherche
- Logique pure dans `:core:model` (`Search.kt`) : ajout en tête, dédup par identité de lieu, cap à 8. Testée.
- Contrat `SearchHistoryStore` dans `Repositories.kt`, implémentation `app/search/PreferencesSearchHistoryStore.kt` sur le patron de [PreferencesHandoverAlertStore.kt](../app/src/main/kotlin/io/aule/android/handover/PreferencesHandoverAlertStore.kt).
- Branchement : `MapViewModel.select(place)` / `select(hit)` écrivent ; `MapSearchBar` affiche l'historique quand la requête est vide.

### 1.4 Mode marche
- `RouteMode.WALK` + **`"foot"`** dans `RouteApi.query` ([Route.kt](../core/model/src/main/kotlin/io/aule/android/core/model/Route.kt)).
  **Jamais `"walk"`** : le moteur ne connaît que `foot`/`car`/`transit`, et tout autre mot
  retombe silencieusement sur le profil voiture — un 200 d'apparence normale, une géométrie
  de voirie, une durée de véhicule affichée comme une marche. L'iOS l'a mesuré le 19/08/2026
  (`RouteCandidate.swift` : 1 198 m / 191 s en `walk` contre 713 m / 528 s en `foot` sur la
  même paire de points) et grave `case walk = "foot"` avec un avertissement. Un test
  verrouille la valeur sur le fil.
- Sélecteur à trois modes dans `RouteSheet` / `MapSearchBar` ; vérifier que les préférences transit ne partent pas en mode marche (le `if (mode == TRANSIT)` couvre déjà).
- Tests : `RouteTest` (query — dont le verrou `foot`), `MapRouteViewModelTest` (bascule de mode).

### 1.5 Accueil et localisation
- `feature/map/OnboardingScreen.kt` (une page, ce que la localisation sert, un bouton) affichée tant que la permission n'a jamais été demandée ; port de `OnboardingView.swift`.
- Remplace le `permissionLauncher.launch(LOCATION_PERMISSIONS)` à froid de `MapScreen`. `location.markPermissionRequested()` existe déjà ; ajouter le drapeau « accueil vu » aux préférences.

---

## Lot 2 — Le hors-ligne

**Bonne nouvelle vérifiée** : `libmaplibre.so` de l'AAR `android-sdk-opengl:13.5.0` embarque
`PMTilesFileSource` et le schéma `pmtiles://`. Le format iOS est donc reprenable tel quel.

### 2.1 L'index des lignes
- Copier `Native/Aule/Resources/transit-lines-index.json` (23 Ko) dans `app/src/main/assets/tiles/`. C'est une **copie** d'un fichier produit par `dashboard/tools/tiles/build-transit.sh` : le noter dans un `README` d'asset, comme iOS le fait.
- Modèle `TransitLine` dans `:core:model`, contrat `NetworkLineRepository` dans `Repositories.kt`.
- `:data` est un module **JVM pur** (pas de `Context`) : l'implémentation décode le JSON et reçoit ses octets par une petite interface `AssetBytes` implémentée dans `:app`. Même discipline que les stores.

### 2.2 Les tracés
- Copier `transit.pmtiles` (3,4 Mo) dans `app/src/main/assets/tiles/`.
- `core/map/layer/TransitLinesLayer.kt` : port de `TransitLinesLayer.swift` — source `pmtiles://`, couche source `transit_lines`, **trois paliers de densité repris tels quels** (ce sont eux qui font que les trois cartes d'Aule se ressemblent), masquée par défaut, mise en avant d'une ligne (halo + trait plein, réseau assourdi autour).
- L'URL : essayer `pmtiles://asset://tiles/transit.pmtiles` ; si le résolveur d'assets ne suit pas, copier le fichier vers `filesDir` au premier lancement et viser `pmtiles://file://…`. Le piège iOS (chemin percent-encodé) vaut aussi ici. À trancher **par essai sur le S21**, pas par supposition.
- Enregistrement dans le registre de `MapController` — le registre remonte tout après `setStyle`, ne pas contourner.

### 2.3 Le volet des lignes
- `feature/map/NetworkLinesSheet.kt` (inventaire complet, hors ligne, cadrage d'une ligne) et `feature/map/LineStopsSheet.kt` (fiche de ligne, tous les arrêts par desserte). Nom `LineStopsSheet` et non `LineDetailSheet` : `LineDepartureSheet` occupe déjà le voisinage sémantique.
- Les arrêts d'une ligne viennent de `DriverServiceRepository.fetchJourney` (exige une session, c'est déjà le cas) ; les tracés s'allument avec le volet et s'éteignent avec lui.

### 2.4 Le cache disque
- `data/caching/CachedStopRepository.kt` et `CachedNetworkLineRepository.kt` : décorateurs, port des fichiers iOS de même nom, mêmes règles de péremption. Écriture par une interface de fichier fournie par `:app` (`cacheDir`), pour la même raison qu'en 2.1.
- Câblage dans `AuleGraph.create` : le décorateur enveloppe `AuleStopRepository`, rien d'autre ne change.

---

## Lot 3 — Le Guet

L'existant Android (`DepartureWatch*`, `DepartureWatchNotifier`, `AlertTone`,
`AlertTonePolicy`, `NavigatingForegroundService`) reste : **la veille armée à la main ne
disparaît pas**, le Guet la rejoint par la clé de passage.

### 3.1 Le socle pur — nouveau module `:core:guet` (JVM pur, comme `:core:geo`)
Port 1:1, fichier par fichier, des purs iOS : `PassageKey`, `GuetTiming`, `GuetLevel` +
`GuetLevelAxes` (deux axes, phase et faisabilité — ne pas les fusionner), `GuetScoring`
(six critères, table de poids unique, **la règle du neutre** : un critère non évaluable
vaut 0,5, jamais 0), `GuetEngine` (horizon 30 min, 8 candidats), **`GuetContext`** (le
contrat d'entrée du moteur — immuable, sans E/S, et porteur de la subtilité de portée
quai/pôle du tamis `quaySieve` : ne tamiser que là où la desserte est de portée quai),
`GuetHabits` (compteurs amortis, aucune trace GPS), `GuetLedger`, `GuetPreferences`,
`GuetSchedule` (plan + `dropped` jamais tu), `GuetEscort` (quatre sorties dont le délai
de garde), `GuetVehicleMatch`.
≈ 1 500 lignes, 13 fichiers. Les deux fichiers iOS restants de `Core/Guet/`
(`GuetActivityAttributes`, `GuetActivityMapping`) sont du ressort de la Live Activity —
§ 3.5, pas ici. Les tests iOS correspondants (`GuetEngineTests`, `GuetScoringTests`,
`GuetLevelTests`, `GuetScheduleTests`, `GuetEscortTests`, `GuetLedgerTests`,
`GuetTimingTests`, `GuetVehicleMatchTests`) se portent avec — c'est le seul filet.

### 3.2 Préférences et réglages
- Store préférences dans `:app`, écran `feature/map/GuetSettingsScreen.kt` (port de
  `GuetSettingsView.swift`) atteint depuis `AccountMenuScreen` : activation, préparation,
  marge au quai, allure de marche (dont automatique mesurée), modes, lignes suivies
  (**consomme l'inventaire du lot 2**), son/vibration/notifications, trois intensités.

### 3.3 Veille de fond et notifications programmées
- `androidx.work` à ajouter au catalogue de versions, avec sa raison, pour le
  rafraîchissement périodique (l'équivalent de `BGAppRefreshTask`).
- Les instants d'alerte : `AlarmManager` exact + canal dédié, notification par
  `DepartureWatchNotifier` étendu. Android n'a pas le plafond de 64 d'iOS mais il a
  `SCHEDULE_EXACT_ALARM` : le déclarer, et prévoir le cas où l'utilisateur le refuse —
  la troncature s'annonce, comme `dropped`.

### 3.4 L'écran d'alerte et l'accompagnement
- `GuetAlertActivity` plein écran (full-screen intent) — l'équivalent de la fenêtre dédiée
  iOS : trois zones, tiroir, carte piétonne + carte véhicule, accepter / refuser /
  basculer. Port de `GuetStageView` + `GuetStageLayout` + `GuetDecisionPanel`.
- **Depuis Android 14, `USE_FULL_SCREEN_INTENT` n'est accordé d'office qu'aux apps
  d'alarme et d'appel.** Pour Aule il faut demander l'autorisation
  (`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`) et prévoir le repli en notification
  tête haute quand elle est refusée — le même réflexe que pour `SCHEDULE_EXACT_ALARM`
  au § 3.3 : le rétrécissement s'annonce, il ne se tait pas.
- L'accompagnement réutilise `NavigatingForegroundService` ; les quatre sorties viennent
  du socle pur.

### 3.5 Ce qui n'a pas d'équivalent
- **Live Activity** (`GuetActivityAttributes`, `GuetActivityMapping`) → notification
  permanente enrichie (`ProgressStyle` sur Android 16, repli notification ordinaire).
  Portée réduite, à assumer par écrit.
- **App Intents / Siri** → pas d'équivalent direct. Raccourcis dynamiques + deep links
  au minimum ; l'intégration assistant est une décision produit à prendre à part, pas
  un portage. **Hors de ce lot.**

---

## Lot 4 — Mission et mode Padeur

[Docs/PLAN-mode-padeur.md](PLAN-mode-padeur.md) est antérieur à l'implémentation iOS :
il planifie des pièces (`NetworkJoin`, géométrie de polygone, historique de mission) que
`Native` a depuis écrites et éprouvées (`PadeurEngine`, `PadeurScoring`, `PadeurFilters`,
`PadeurContext`, `PadeurOption`, `PadeurWatch`, `MissionTrace`, `Mission`,
`MissionSchedule`, `Zone`, `HeadsignMatch`). **Premier geste du lot : réconcilier le plan
avec le code iOS**, puis dérouler ses lots 0 → 4 en portant plutôt qu'en réinventant.
La dépendance `zone_geom` reste chez l'équipe BFF, et le lot MVP tourne en `NO_ZONE`
sans elle — cette partie du plan tient toujours.

---

## État d'avancement

**Lot 1 — fait. Lot 2 — fait. Lot 3 — socle pur (§ 3.1) et réglages (§ 3.2) faits** ;
restent les § 3.3 à 3.5, qui demandent alarmes, veille de fond et écran plein.
Suite verte : **755 cas, 0 échec**, dont **131 pour le seul Guet**. APK construit, installé et lancé sur le S21
(`R3CRA0WV55H`) sans plantage.

### Lot 1

| § | Livré | Vérifié |
|---|---|---|
| 1.1 Mot de passe oublié | `AuthPkceFlow`, `sendPasswordRecovery`, `updatePassword`, `pendingAuthFlow`, `AuleHttpClient.putRaw`, `ForgotPasswordScreen` + `UpdatePasswordScreen`, routage `AuleRoot` | 6 tests dépôt (MockWebServer) + 7 tests `AuthViewModel` ; écran ouvert et refermé sur le S21 |
| 1.2 Mentions légales | `MAP_LEGAL_NOTICES` dans `:core:map`, `LegalNoticeSheet`, pastille ⓘ en bas à gauche du HUD | compile ; **écran non atteint sur l'appareil** (demande une session) |
| 1.3 Historique | `rememberPlace`/`historyKey`/`encodeHistory` purs, `SearchHistoryStore`, `PreferencesSearchHistoryStore`, section « Destinations récentes » | 9 tests purs + 3 tests `MapViewModel` |
| 1.4 Mode marche | `RouteMode(apiValue)` avec `WALK = "foot"`, sélecteur à trois modes | 2 tests `RouteTest` (dont le verrou `foot`) + 2 tests de bascule |
| 1.5 Accueil | `WelcomeScreen` + `WelcomeHost`, `WelcomeStore`, demande à froid retirée de `MapScreen` | 1 test |

### Lot 2

| § | Livré | Vérifié |
|---|---|---|
| 2.1 Index des lignes | assets copiés + `README` avec empreintes, `TransitLine`/`TransitNetwork`/`TransitLineFamily`/`NetworkLinesDigest` purs, `NetworkLineRepository`, `AssetBytes`, `AssetNetworkLineRepository` | 13 tests purs + **8 tests contre le fichier réellement livré** (138 lignes, familles, cadres) |
| 2.2 Tracés | `TransitTiles` (URL PMTiles), `TransitLinesLayer` (3 paliers, halo, réseau assourdi), `TransitArchive` (extraction), `noCompress += "pmtiles"` | 7 tests d'URL ; **peinture non vérifiée sur l'appareil** — demande une session |
| 2.3 Volet des lignes | `NetworkLinesSheet` (inventaire, filtre, œil qui montre) + `LineStopsSheet` et `LineStopsModel` (desserte par sens), entrée dans le menu flottant, cadrage sur la ligne désignée | 9 tests `MapViewModel` + 10 tests `LineStopsModel` ; **écrans non atteints sur l'appareil** |
| 2.4 Cache disque | `CacheStore`, `encodeCatalog`/`decodeStopCatalog`, `CachedStopRepository`, `CachedNetworkLineRepository`, `FileCacheStore`, câblé dans `AuleGraph` | 9 tests, dont « un catalogue vide ne s'écrit jamais » |

### Lot 3 — le socle du Guet

Module `:core:guet` créé, JVM pur, dépendant de `:core:model` et `:core:geo` et de rien
d'autre. **Onze fichiers portés avec leurs tests iOS** : c'est le seul filet de ce lot, et il
se porte en même temps que le code, pas après.

| Fichier | Ce qu'il tient |
|---|---|
| `PassageKey` | L'identité d'un passage, **un seul constructeur**. Exacte à la seconde — quantifier par minute ferait tomber 18:31:59 et 18:32:01 dans deux tranches. |
| `GuetTiming` | La soustraction, et rien d'autre. `leaveAt` **contient déjà la marge de quai**. |
| `GuetLevel` + les deux axes | Phase et faisabilité, **jamais fusionnées**. Plus le suivi, où seule une dégradation imputée à la **position** se confirme. |
| `GuetLedger` + `PassageStatus` | Un refus vaut pour ce passage et non pour sa ligne ; ignorer n'est pas refuser ; le registre est borné. Réappariement à ±180 s. |
| `GuetScoring` + `GuetScore` | Six critères, une seule table de poids, **la règle du neutre**. |
| `GuetVehicleMatch` | **Unique ou rien** : désigner le mauvais véhicule ferait suivre un bus qui va ailleurs. |
| `GuetHabits` | Des compteurs amortis, **jamais une trace** : aucune coordonnée, aucun horodatage de trajet, demi-vie de trente jours. |
| `GuetPreferences` | Éteint par défaut ; **décodage tolérant** — un décodage strict éteindrait le Guet en silence après une mise à jour. |
| `GuetSchedule` | **Loin dans le temps, le théorique bat le temps réel.** La troncature s'annonce, jamais tue. |
| `GuetEscort` | Quatre sorties, dont **le délai de garde** : sans lui, un accompagnement oublié tient le GPS allumé. |
| `GuetContext` + `GuetEngine` | La déduplication **après** le classement ; le passage suivi reste trouvable même devenu inatteignable ; l'alerte demande phase **et** faisabilité **et** score. |

Deux pièces ont été posées **hors** du module, là où le lot 4 les retrouvera :
`HeadsignMatch` dans `:core:model` (la relève et le plan de ligne posent la même question),
et `ApproachDetector` + `PositionSample` dans `:core:geo` (le Padeur s'en sert aussi).

### Un écart assumé sur `GuetContext` : pas de tamis de quai

Le contexte iOS porte un **tamis de quai** — la desserte d'un quai précis, qui écarte les
passages du pôle qui ne partent pas de là. Il n'est pas porté, parce que le modèle Android
n'a pas la notion de **portée** d'une desserte : `ServingLine` ne dit pas si elle vaut pour un
quai ou pour un pôle entier, et tamiser avec une desserte de portée « pôle » retirerait des
passages parfaitement légitimes en affirmant qu'ils partent d'ailleurs.

Le comportement obtenu est **exactement celui de l'iOS quand le tamis est absent** — on garde
les passages du pôle entier —, c'est-à-dire la branche que l'iOS qualifie de « seule réponse
honnête à une absence ». Ce n'est pas une régression, c'est une donnée qui manque.

### § 3.2 — les réglages, faits

`GuetPreferencesStore` (contrat) + `PreferencesGuetStore` (`:app`), `GuetSettingsModel` et
`GuetSettingsScreen` dans `:feature:map`, atteints depuis `AccountMenuScreen`. Un écran plein
et non un volet : les volets répondent à un geste qu'on vient de faire, régler une veille est
autre chose — on y descend, on lit, on revient.

Deux décisions qui se lisent dans les tests :

- **Chaque geste écrit.** Un écran de réglages n'a pas de bouton « Enregistrer ». Enregistrer
  à la fermeture perdrait tout si le système tue l'écran — ce qui arrive précisément quand on
  part chercher une autorisation dans les paramètres.
- **Un mode ne se décoche pas jusqu'au vide.** Sans cette garde, la veille resterait allumée et
  ne proposerait plus jamais rien, sans qu'aucun mot ne dise pourquoi. Un réglage qui rend le
  service muet doit être l'interrupteur principal, pas l'effet de bord de trois cases.

Éteint, les autres réglages restent **visibles mais grisés** : les cacher ferait croire qu'ils
n'existent pas, et laisser choisir une allure de marche quand rien ne surveille serait un écran
qui ment.

### Ce qui reste du lot 3

La veille de fond et les alarmes exactes (§ 3.3), l'écran d'alerte plein écran et
l'accompagnement (§ 3.4), la notification permanente (§ 3.5). C'est là que se posent
`WorkManager`, `SCHEDULE_EXACT_ALARM` et `USE_FULL_SCREEN_INTENT` — les trois autorisations que
le plan demande de déclarer **avec leur repli**, parce qu'un rétrécissement se dit et ne se
tait pas.

### Deux gestes sur un rang de ligne, et pourquoi

Toucher le rang **ouvre la fiche** — par où passe la ligne, arrêt par arrêt. Toucher l'œil
**la montre sur la carte**, sans rien ouvrir. Les fondre coûterait le second geste : les
vingt-neuf cars interurbains n'ont **pas de desserte publiée** — l'app ne suit ni leur flotte
ni leurs horaires — et voir leur tracé est la seule chose qu'on puisse en faire.

### Ce qui reste à vérifier à la main

Tout ce qui vit derrière la session — c'est-à-dire **toute la carte**. La connexion et
l'écran de récupération sont les deux seuls écrans atteignables sans compte, et ils l'ont
été. Restent : la pastille ⓘ en jour et en nuit ; l'historique après huit lieux ;
l'itinéraire à pied **et sa durée** (le symptôme du bug `walk` est « 1 min » là où Plan dit
8) ; l'accueil au premier lancement (`adb uninstall io.aule.android.development` avant,
sinon le drapeau « accueil vu » est déjà posé) ; **et surtout, en mode avion : les tracés se
peignent-ils, l'inventaire s'ouvre-t-il, les arrêts reviennent-ils du cache.**

L'envoi réel d'un lien de récupération n'a pas été déclenché : il part sur une vraie boîte.

### La question ouverte du lot 2 : `asset://` ou fichier recopié

L'archive est **recopiée** dans `filesDir` au premier lancement, et visée par
`pmtiles://file://…` percent-encodé — la forme que l'iOS a prouvée et que le lecteur de
fichiers local garantit. La forme courte `pmtiles://asset://tiles/transit.pmtiles` éviterait
la copie de 3,4 Mo, mais **on ne sait pas si le lecteur PMTiles du binaire délègue son URL
interne au lecteur d'assets** : le `.so` embarque bien `PMTilesFileSource` et
`AssetManagerFileSource`, et rien dans ses symboles ne relie les deux. L'archive étant
désormais stockée non compressée (`noCompress`), l'essai est possible — il demande une
session sur l'appareil. À reprendre le jour où quelqu'un peut se connecter sur le S21.

### Trois écarts assumés par rapport au plan initial

1. **Le genre du lien ne se lit pas dans l'URL de retour.** Le § 1.1 prévoyait de distinguer
   `type=recovery` sur le deep link. GoTrue ne promet pas de reposer ce paramètre sur un
   retour **PKCE** — le SDK iOS ne sait d'ailleurs le lire que sur le flux implicite, qu'Aule
   n'utilise pas, si bien que la phase `resettingPassword` d'iOS n'est probablement jamais
   atteinte. Le genre est donc écrit **avec le vérifieur PKCE au moment de la demande**
   (`AuthPkceFlow`), et relu avant l'échange. Une distinction de sécurité ne se fonde pas sur
   un paramètre facultatif : sans elle, un vieux lien de réinitialisation ouvrirait la carte.
2. **`WelcomeScreen` et non `OnboardingScreen`.** Le nom du plan venait d'iOS ; le dépôt parle
   français, et « accueil » est ce que le fichier contient.
3. **`CachedNetworkLineRepository` ne cache rien sur le disque.** Son homonyme iOS met en
   cache une réponse réseau ; l'inventaire Android vient déjà d'un asset embarqué, donc d'un
   fichier local que rien ne peut rendre indisponible. Le recopier dans `cacheDir` reviendrait
   à cacher un fichier avec un fichier. Le décorateur ne garde donc qu'en mémoire, et il le
   dit dans son KDoc plutôt que de faire semblant d'être son homonyme.

---

## Vérification

À chaque lot, dans cet ordre :

1. `./gradlew test` — la suite JVM complète doit rester verte : 69 fichiers de test,
   503 `@Test` déclarés, **563 cas exécutés** (les tests paramétrés en produisent
   plus qu'ils n'en déclarent). Compter à la main sans exclure `.claude/worktrees`
   donne 911 : ce sont les copies des worktrees, pas des tests du dépôt.
   chaque lot ajoute ses propres tests, portés des `AuleTests` correspondants.
2. `./gradlew installDevelopmentDebug` puis lancement sur le **S21** (`R3CRA0WV55H`) —
   pas d'émulateur, c'est la règle du dépôt.
3. Contrôles manuels par lot :
   - **1** : lien de récupération reçu et ouvert dans l'app, pastille ⓘ lisible en jour et
     en nuit, historique après huit lieux, itinéraire à pied **dont la durée est bien une
     durée de piéton** (le symptôme du bug `walk` : « 1 min » là où Plan dit 8), premier
     lancement sur une app fraîchement installée (`adb uninstall` avant).
   - **2** : mode avion — les tracés se peignent, l'inventaire des lignes s'ouvre, les
     arrêts reviennent du cache.
   - **3** : veille armée puis téléphone verrouillé, alerte programmée qui tombe à l'heure,
     accompagnement qui coupe le GPS au délai de garde (vérifier à l'`adb shell dumpsys location`).
   - **4** : selon les lots du plan Padeur.
4. `ADR` : le lot 2 (PMTiles) et le lot 3 (module `:core:guet`, WorkManager, alarmes
   exactes) méritent chacun une ADR — ce sont des décisions dont on redemandera la raison.
