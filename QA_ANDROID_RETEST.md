# Requalification Android — Aule Pro

Campagne de **revalidation et de non-régression** menée le 28/08/2026 sur
`~/Aule/Kotlin`, branche `cursor/handover-done-notify-df65`, `HEAD` à `734c3c6`
**plus le chantier non commité du volet d'itinéraire** (voir § 1.2).

Elle fait suite à la campagne de recette consignée dans
[`QA_ANDROID_AULE.md`](QA_ANDROID_AULE.md), qui avait relevé 19 anomalies, en
avait corrigé les quatre `P1` et six des huit `P2`, et s'était arrêtée sur un
**`GO AVEC RÉSERVES`** dont la réserve tenait en un mot : **rouler**.

---

## Ce que cette campagne apporte de nouveau

La campagne précédente n'a pas pu jouer la moitié de sa mission : « la position
simulée n'est pas accessible par `adb` sur un appareil non rooté ; il faut soit
une application de position factice, soit un véhicule ».

**Cette application a été écrite pour la campagne.** Un injecteur de positions
factices de 270 lignes de Java (`io.aule.qa.mockgps`, hors dépôt, dans le
répertoire de travail de la session) rejoue une polyligne à une vitesse donnée
en écrivant dans les providers de test du système ; Play Services relaie ces
positions au client fusionné, celui qu'Aule écoute.

```
adb shell appops set io.aule.qa.mockgps android:mock_location allow
adb shell am start -n io.aule.qa.mockgps/.Boot \
  --es route '47.21265,-1.55831;…' --es speed '30' --es acc '5' --es only 'gps'
```

Il accepte une trace au format `lat,lon;…` ou une polyligne encodée, une vitesse
en km/h, une précision annoncée, un bruit gaussien en mètres, une cadence
d'émission et le choix du provider. Il interpole entre les points, calcule le
cap, et tient la vitesse demandée.

**Conséquence : les sections 4 à 8 et 18 de la mission — GPS en mouvement,
caméra, navigation automobile, instructions, tracé, trajet complet — ont pu être
jouées.** Elles étaient toutes `BLOCKED` en août.

### Fidélité des traces

Une trace qui ne suit pas le tracé que l'application peint ne teste pas la
navigation : elle teste le recalcul. Les traces de cette campagne sont donc
**celles du BFF lui-même**, obtenues sur le même point d'entrée que l'application
et avec la même requête (`GET /api/route?v=28&mode=car&from=lng,lat&to=lng,lat` —
pour le mode voiture, la requête ne porte aucun autre paramètre), et la
destination est celle que le géocodeur de l'application rend pour le texte saisi
(`GET /api/geocode?q=…`).

Vérification : le tracé récupéré pour « Gare de Nantes » mesure **1 832 m**, très
exactement la distance que l'écran annonce — `1,8 km`.

---

## 1. Ce qui a changé depuis la dernière campagne

### 1.1 Les correctifs à revalider

| Commit | Objet | Anomalie visée |
|---|---|---|
| `7fd90a5` `815ba7a` `7a13301` | Clé publiable, URL Supabase et hôte du routeur sortis du build vers `local.properties` | **AND-BUG-004** |
| `58c5b7a` | Le trajet voiture rendu à la réponse muette | — |
| `73a3648` | Session conservée quand le réseau manque | **AND-BUG-002** |
| `7f76aad` | Le guidage ne tourne plus derrière l'écran quitté | **AND-BUG-001**, **009** |
| `3757e5e` | Recalcul d'itinéraire quand on quitte le tracé | **AND-BUG-003** |
| `c3f9fe3` | Manœuvres d'un autre trajet écartées | **AND-BUG-005** |
| `e3fe606` | Numéro de sortie des ronds-points | **AND-BUG-006** |
| `afe5465` | Vitesse rendue au conducteur | **AND-BUG-007** |
| `855544c` | Avertissements de compilation éteints | **AND-BUG-015..018** |
| `613838b` | Les deux régressions d'interface trouvées à l'écran | — |

Zones touchées, et donc zones à surveiller pour la non-régression :
`MapViewModel` (recalcul, manœuvres, vitesse), `MapScreen` (retour, cycle de
vie), `MapHud` (vitesse), `NavigatingForegroundService` (balayage), `Auth` et
`AuleGraph` (session), `Maneuvers` / `OsrmDto` / `DomainText` (ronds-points),
`AppConfig` (configuration).

### 1.2 Le chantier non commité — refonte du volet d'itinéraire

Quatre fichiers modifiés et non commités, **733 insertions**, jamais passés par
une campagne :

| Fichier | Ampleur |
|---|---|
| `feature/map/…/RouteSheet.kt` | 942 lignes changées — refonte complète |
| `core/designsystem/…/AuleButtonGroup.kt` | 59 lignes — le groupe accepte une icône et un libellé parlé |
| `feature/map/res/values/strings.xml` et `values-en/` | 2 clés retirées, 6 ajoutées, dans les deux catalogues |

L'intention, documentée dans l'en-tête du fichier : le volet occupait **82 % de
l'écran** une fois déployé ; le titre disparaît, les extrémités tiennent sur deux
lignes tenues par un rail dessiné, le sélecteur de modes passe sur une ligne
(glyphe + durée), et « Démarrer » sort du défilement.

**Ce chantier est dans l'APK testé.** Il fait donc partie du périmètre de
requalification, et les tests d'interface ci-dessous portent d'abord sur lui.

#### Le périmètre a bougé pendant la campagne

Il faut le dire, parce que cela conditionne la portée de plusieurs `PASS` :
**le chantier a continué d'évoluer pendant que la campagne se jouait.**

| Horodatage | Événement |
|---|---|
| 12:24 | APK installé sur le S21 — celui de tous les essais de la journée |
| 12:29 | `MapScreen.kt` modifié — ajout de `ROUTE_PEEK_FRACTION = 0.60f` |
| 12:37 | `RouteSheet.kt` modifié |
| 12:38 | APK reconstruit sur disque, **mais non réinstallé** |
| 17:59 | APK à jour installé, tests d'interface du volet **rejoués** |

Le changement en cause fait passer le palier du volet d'itinéraire de 45 % à
**60 %** de la fenêtre — précisément la dimension que les tests multi-écrans
mesurent. Les essais d'interface du volet ont donc été **rejoués sur la version
à jour** :

| Rejoué à 18:00 | Résultat |
|---|---|
| Volet en mode Voiture, variante unique | `PASS` — palier mesuré sur le contenu, 51 % de la hauteur |
| Volet en mode Transports, avec chaîne de trajet | `PASS` — 56 %, « 🚶 — ① — 🚶 », « La plus rapide · 5 min à pied · Sans changement » |
| Petit écran 720 × 1560 @ 320 **et police ×1,5** | `PASS` — 68 % de la hauteur, barre « Démarrer · 21 min » entière et visible, rien de coupé |

**Les autres tests de ce rapport — GPS, caméra, navigation, arrière-plan,
réseau, endurance — portent sur l'APK de 12:24.** Aucun d'eux ne dépend du palier
du volet ni du rendu de `RouteSheet` : ils s'exercent sur la carte, le guidage et
le cycle de vie, que ces deux commits ne touchent pas. Les cinq anomalies
`NEW-*` sont donc valables pour les deux versions.

---

## 2. Conventions du rapport

**Statuts** — `PASS`, `FAIL`, `REGRESSION`, `PARTIAL`, `BLOCKED`,
`NOT_IMPLEMENTED`.

**Criticité** — `P0` bloquant, `P1` critique, `P2` majeur, `P3` mineur,
`P4` cosmétique.

**Origine** — *ancien bug*, *nouvelle anomalie*, *régression*, *comportement à
surveiller*.

Une correction n'est **jamais** tenue pour validée sur la seule lecture du code.
Chaque ligne `PASS` ci-dessous renvoie à une observation faite sur le Samsung S21
(`SM-G991B`, Android 15, 1080 × 2400, densité 480) : une capture d'écran, une
ligne de journal, une sortie de `dumpsys`, ou une mesure.

## 3. Revalidation des anciens bugs

### AND-BUG-001 — le geste de retour pendant un guidage

**Correction identifiée** — `7f76aad`. `MapScreen` intercepte le retour pendant
un guidage et ouvre un dialogue de confirmation au lieu de laisser l'activité se
terminer ; `NavigatingForegroundService` gagne un `onTaskRemoved`.

**Ancien comportement** — le retour fermait l'application, perdait la navigation
et laissait tourner le service, le wake lock et le GPS haute précision.

**Nouveau comportement observé** — le retour ouvre « **Arrêter le guidage ?** »
(« L'itinéraire en cours sera abandonné, et la carte reviendra sur le trajet »)
avec « Continuer » et « Arrêter ». `mCurrentFocus` reste `MainActivity` :
l'activité ne se termine pas.

**Test effectué** — guidage engagé, `input keyevent KEYCODE_BACK`, lecture de
l'arbre d'accessibilité puis `dumpsys window`. Puis « Arrêter » et relevé des
ressources.

```
mCurrentFocus=Window{… io.aule.android.development/io.aule.android.MainActivity}
après « Arrêter » : service de premier plan = 0
                    PARTIAL_WAKE_LOCK 'aule:navigating' — absent des verrous détenus
                    Profil de localisation : NAVIGATING → READY
```

**Résultat — `PASS`** · Régression détectée : **non**.

**Commentaires** — le dialogue est bien rendu au thème Aule (teal), la
régression d'interface trouvée en août ne réapparaît pas. Un `SCREEN_BRIGHT_WAKE_LOCK`
posé par le `WindowManager` au nom de l'application reste détenu après l'arrêt du
guidage — voir `SURV-02`.

---

### AND-BUG-006 — le numéro de sortie des ronds-points

**Correction identifiée** — `e3fe606`. `maneuver.exit` décodé dans `OsrmDto`,
porté par `Maneuvers`, formulé par `DomainText`.

**Ancien comportement** — « Prendre le rond-point », sans dire laquelle des
sorties.

**Nouveau comportement observé** — le bandeau annonce « **Prendre la 1re
sortie** », et plus loin « 350 m · Pont de Tbilissi · Prendre la 1re sortie ».

**Test effectué** — trajet automobile simulé de 1 832 m comportant **huit
ronds-points et un rond-point à sens giratoire**, parcouru à 30 km/h, captures
d'écran toutes les 16 s. Manœuvres de référence obtenues séparément sur le même
routeur.

**Résultat — `PASS`** · Régression détectée : **non**.

**Commentaires** — vérifié cette fois **en mouvement**, ce que la campagne d'août
n'avait pu faire qu'à l'arrêt.

---

### AND-BUG-007 — la vitesse du conducteur

**Correction identifiée** — `afe5465`. `TripSummary` porte la vitesse, `MapHud`
affiche un cadran.

**Ancien comportement** — aucune vitesse affichée.

**Nouveau comportement observé** — cadran « **30** km/h » pendant le trajet,
« **0** km/h » à l'arrêt, sans disparaître. La valeur affichée correspond
exactement à la vitesse injectée.

**Test effectué** — trajet à 30 km/h puis immobilisation de 150 s. Lecture des
captures et de l'arbre d'accessibilité (« Vitesse : 0 kilomètres-heure »).

**Résultat — `PASS`** · Régression détectée : **non**.

**Commentaires** — le cadran ne passe plus sous la pastille ⓘ : la seconde
régression d'interface d'août ne réapparaît pas.

---

### AND-BUG-003 — le recalcul d'itinéraire

**Correction identifiée** — `3757e5e`. `MapViewModel` détecte la sortie
(`OffRoute`, seuil 32 m, 3 mesures) et relance `routingRepository.plan` depuis la
position courante, avec une temporisation `RECALC_COOLDOWN_MS = 12 000`.

**Ancien comportement** — un bandeau « Rejoignez le tracé » et rien d'autre.

**Nouveau comportement observé** — sortie volontaire perpendiculaire au tracé, à
40 km/h :

```
13:10:51.584  Aule.Net: Hors itinéraire — recalcul vers Gare de Nantes.
13:10:51.742  Aule.Map: Itinéraire recalculé.          ← 158 ms
```

**Test effectué** — trajet nominal pendant 336 m, puis déviation plein nord de
400 m à 40 km/h. Mesure des trois délais demandés par la mission.

| Mesure | Valeur |
|---|---|
| Détection de la sortie | ≤ 12 s, plafonnée par la temporisation |
| Calcul du nouvel itinéraire | **≈ 150 ms** |
| Affichage du nouveau tracé | dans la même image que le calcul |
| Tracés simultanés à l'écran | **un seul**, à tout instant |
| Retour à un ancien tracé | jamais observé |

**Résultat — `PASS`** · Régression détectée : **non**.

**Commentaires** — `AUTO-005` (sorties répétées) est couvert par le même essai :
huit recalculs successifs sur 200 s n'ont produit ni blocage, ni empilement de
calculs, ni tracé fantôme. `adopt()` remet les manœuvres à zéro et incrémente un
compteur de génération qui fait tomber les réponses encore en vol.
**Mais `adopt()` appelle `routeProgress.reset()` sans repère** — voir `SURV-01`.

---

### AND-BUG-008 — l'arrivée qui ne rend rien

**Correction identifiée** — `MapViewModel` arrête le guidage quand
`JourneyProgress.arrived` passe à vrai, c'est-à-dire quand
`routeT ≥ JOURNEY_ARRIVED_T` (0,999).

**Ancien comportement** — l'arrivée n'arrêtait pas le guidage : FGS, wake lock
6 h et GPS haute précision continuaient.

**Nouveau comportement observé** — la correction ne se déclenchait que si le
véhicule atteignait la destination à ~2 m près, ce qui n'arrive jamais en
conduite (voir `NEW-01`). **Le seuil a été corrigé pendant cette campagne**, et
l'arrivée est maintenant déclarée sur un trajet mené au bout de sa voirie.

**Test effectué** — deux essais.

1. Trajet complet jusqu'au dernier point du tracé (1 832 m) :
   `service de premier plan = 1`, wake lock détenu, GPS `HIGH_ACCURACY @+1s`
   **toujours actifs deux heures après**.
2. Position posée **exactement** sur la destination géocodée :
   « Vous êtes arrivé à Gare de Nantes », `READY`, service arrêté — la
   correction fonctionne, mais seulement là.

**Résultat — `PASS` après correction** (`PARTIAL` au constat) · Régression
détectée au constat : **oui, fonctionnelle** — refermée par le correctif de
`NEW-01`.

---

### AND-BUG-009 — le service fantôme après balayage

**Correction identifiée** — `onTaskRemoved` dans `NavigatingForegroundService`.

**Test effectué** — non rejoué à l'identique cette campagne ; couvert
indirectement par les dix cycles accueil/relance de `RETEST-AND-002` et par les
trois bascules d'applications de `RETEST-AND-BG-005`, qui n'ont laissé ni
service ni verrou orphelins.

**Résultat — `PASS` (par couverture indirecte)** · Régression : **non**.

---

### AND-BUG-005 — les manœuvres d'un autre trajet

**Correction identifiée** — `c3f9fe3`. `roadRouteDescribesLeg` compare la
longueur rendue par le routeur de manœuvres à celle de la jambe peinte et écarte
l'écart aberrant, en le journalisant.

**Nouveau comportement observé** — la garde **a tiré en conditions réelles**, sur
un trajet nominal :

```
Aule.Net: Manœuvres écartées : le routeur rend 1689 m pour une jambe de 266 m.
```

**Résultat — `PASS` pour la garde** · Régression : **non**.

**Commentaires** — la garde protégeait, mais le conducteur perdait alors toutes
les manœuvres de la jambe concernée. La cause tenait à l'architecture à deux
routeurs, et **elle a été retirée** : depuis `NEW-03`, les manœuvres viennent du
même moteur que le tracé, donc décrivent forcément la bonne jambe. La garde n'a
plus rien écarté sur les trajets d'après-correction (`Manœuvres écartées` : 0).
Elle reste en place pour le repli.

---

### AND-BUG-004 — la dépendance au serveur de démonstration OSRM

**Correction identifiée** — `7a13301`. L'hôte passe par `local.properties`
(`aule.osrmOrigin`), et `AppConfig.usesPublicDemoRouter` le dit au démarrage.

**Nouveau comportement observé** — l'avertissement est bien émis :

```
Aule.App: Manœuvres servies par le serveur de démonstration public d'OSRM
          (https://router.project-osrm.org) : sans garantie de service.
          Poser aule.osrmOrigin dans local.properties.
```

**Résultat — `PASS`.** Le mécanisme de configuration fonctionne, et depuis la
correction de `NEW-03` la dépendance n'est plus sur le chemin normal du
guidage : les modes porte-à-porte n'appellent plus ce serveur du tout —
**zéro appel** mesuré sur un trajet complet. `OsrmRoadRouter` ne subsiste qu'en
repli, pour les jambes qui arriveraient sans manœuvres.

**Commentaires** — l'avertissement au démarrage reste utile : il dit qu'un repli
mal configuré pointe encore sur la démonstration publique.

---

### AND-BUG-002 — la session perdue sur panne réseau

**Correction identifiée** — `73a3648`. Un refus non définitif (réseau) ne vide
plus la session ; une réserve d'habilitations est conservée.

**Test effectué** — voir § 5 (`RETEST-AND-NET-*`).

---

### AND-BUG-011 — les tests instrumentés

```
$ find core data feature app -path "*/src/androidTest/*" -name "*.kt" | wc -l
0
```

**Résultat — `NOT_IMPLEMENTED`**, inchangé. La refonte du volet d'itinéraire
(733 insertions) n'a **ajouté aucun test**. Le total est passé de 908 à **922**,
et les quatorze ajoutés le sont par les correctifs de cette campagne, pas par la
refonte.

---

## 4. Nouvelles anomalies découvertes

### NEW-01 — `P1` · le guidage ne s'arrête jamais — **CORRIGÉ**

**Origine** — nouvelle anomalie, révélée par le premier trajet complet jamais
joué sur cet appareil. Elle **annule en pratique la correction d'AND-BUG-008**.

**Ce qui se passe.** Le routeur arrête son tracé sur la voirie ; la destination,
elle, est le point rendu par le géocodeur — l'entrée du bâtiment. Pour « Gare de
Nantes », l'écart mesuré entre le dernier point du tracé et la destination est de
**33,9 m**. Or :

- `JourneyProgress.arrived` demande `routeT ≥ JOURNEY_ARRIVED_T` (**0,999**),
  soit — le dernier segment étant compté dans le plan — **moins de deux mètres**
  de la destination sur ce trajet ;
- `OffRoute.baseThresholdMeters` vaut **32 m**.

Un véhicule qui va jusqu'au bout de la route praticable n'est donc **jamais
arrivé**.

> **Rectificatif.** La première rédaction de cette fiche attribuait aussi le
> bandeau « Vous avez quitté l'itinéraire » à ce même écart de 33,9 m, en le
> comparant au seuil de 32 m d'`OffRoute`. **C'était faux** : la déviation se
> mesure par rapport au **tracé**, pas à la destination, et elle vaut zéro quand
> on est sur le dernier point. La vérification menée après correction a établi
> la vraie cause — voir `NEW-06`.

**Ce qui a été observé**, trois fois sur trois, dont une fois sans le moindre
recalcul parasite :

```
position finale = 47.218000,-1.541780   ← dernier point du tracé, exactement
bandeau de tête : « 0 m · Boulevard de Stalingrad · Vous êtes arrivé »
bandeau bas     : « Distance 40 m » — « Temps restant 13 min »
service de premier plan = 1
```

**L'impact, mesuré deux heures après l'arrivée :**

```
isForeground=true types=0x00000008 (location)
PARTIAL_WAKE_LOCK 'aule:navigating' — détenu
ProviderRequest[@+1s0ms, HIGH_ACCURACY, WorkSource{… io.aule.android.development}]
température de l'appareil : 41,9 °C
```

Deux heures de GPS haute précision à une position par seconde, de wake lock et de
service de premier plan, **après l'arrivée**, sans que rien à l'écran ne le
signale autrement que par un bandeau rouge trompeur.

**Contre-épreuve** — position posée **exactement** sur la destination géocodée :
« Vous êtes arrivé à Gare de Nantes », profil `READY`, service arrêté en 700 ms.
La correction d'AND-BUG-008 fonctionne donc ; elle n'est simplement **jamais
atteignable en conduite réelle**, où l'on s'arrête là où la route s'arrête.

**Test nominal** — trajet de 1 832 m suivi exactement, jusqu'au bout : `FAIL`.
**Test limite** — position posée sur la destination au mètre près : `PASS`.
**Test de non-régression** — arrêt manuel par le geste de retour : `PASS`, les
ressources sont bien rendues.

**Correction appliquée** — `JourneyProgress.kt`. Le seuil `JOURNEY_ARRIVED_T =
0.999` est remplacé par une **distance en mètres**, `JOURNEY_ARRIVED_M = 50.0`,
assortie d'une fraction plancher (`JOURNEY_ARRIVED_T_FLOOR = 0.5`) pour qu'un
itinéraire plus court que le rayon ne soit pas « abouti » avant d'avoir commencé.

```kotlin
arrived = remainingMeters <= JOURNEY_ARRIVED_M && t >= JOURNEY_ARRIVED_T_FLOOR
```

**Vérification sur l'appareil, après correction** — trajet de 1 832 m parcouru à
40 km/h jusqu'au bout du tracé, puis même essai sur la portion finale seule :

```
18:16  Vous êtes arrivé à Gare de Nantes
       Aule.Gps: Profil de localisation : READY
       Aule.Gps: Service de premier plan arrêté.
       service = 0     verrou = 0
```

Le palier retombe, le service et le `PARTIAL_WAKE_LOCK` sont rendus, le GPS
quitte la haute précision — et l'écran garde sa fiche d'arrivée, ce qui est le
comportement voulu et documenté dans `MapScreen`.

**Une facette, et ce n'en est pas un défaut.** Quand l'arrivée est déclarée, le
service s'arrête mais **l'interface reste en mode navigation** : le bandeau, le
cadran et la barre du bas subsistent, et le geste de retour rouvre « Arrêter le
guidage ? ». C'est **délibéré**, et `MapScreen` l'écrit : « On ne coupe pas le
guidage pour autant : la fiche d'arrivée et le tracé restent, parce qu'on veut
encore les regarder. Seul le palier retombe, et avec lui ce qui coûte. » La
première rédaction de ce rapport le comptait à tort comme une anomalie.

---

### NEW-02 — `P2` · le temps restant et l'heure d'arrivée contredisent la distance — **CORRIGÉ**

**Origine** — nouvelle anomalie.

**Ce qui a été observé**, à trois moments différents du même guidage :

| Heure | Distance | Temps restant | Heure d'arrivée |
|---|---|---|---|
| 13:05 | 40 m | **13 min** | 13:18 |
| 13:07 | 0 m | **11 min** | 13:18 |
| 13:20 | 0 m | **8 min** | 13:29 |
| 15:07 | 100 m | **0 min** | 13:29 — figée depuis presque deux heures |

Le trajet entier dure **5 minutes** et fait 1,8 km. Aucune de ces lignes n'est
cohérente, ni avec la distance affichée à côté, ni avec le trajet engagé.
Au démarrage, en revanche, le bandeau est juste : « Arrivée estimée 12:41 · Temps
restant 5 min · Distance 1,8 km », et il le reste pendant le trajet
(« 12:47 · 4 min · 1,5 km »). **La dérive n'apparaît qu'en fin de parcours**,
c'est-à-dire exactement là où `NEW-01` fausse `routeT`.

**Test effectué** — relevés successifs sur captures d'écran et arbre
d'accessibilité (« Arrivée 13:18. Temps restant 11 min. Distance 0 m. »).

**Résultat — `FAIL`** · Origine : nouvelle anomalie · Régression : **non**.

**Cause** — dans `tripSummary`, la branche `plan.arrivalAt != null` **précède**
celle qui calcule `plan.duration × (1 − routeT)`. Or `plan.arrivalAt` est une
heure figée au moment du calcul : sur une voiture elle vieillit à chaque minute,
et c'est elle qu'on lisait, indépendamment de la distance restante.

**Correction appliquée** — `TripSummary.kt` et `Journey.kt`. Cette branche est
désormais réservée aux trajets dont l'heure d'arrivée est **imposée de
l'extérieur** :

```kotlin
plan.arrivalAt != null && plan.hasSchedule -> …          // TripSummary.kt
val hasSchedule: Boolean get() = legs.any { it.mode == LegMode.TRANSIT }  // Journey.kt
```

Un tram arrive à 18 h 25 parce que le réseau l'a décidé, et rouler plus vite n'y
change rien : là, l'horaire garde autorité. Une voiture arrive quand elle arrive,
et son heure se recalcule à mesure qu'on avance.

**Vérification sur l'appareil, après correction** — même trajet, relevé à
l'arrivée :

```
Arrivée estimée 18:16 · Temps restant 0 min · Distance 0 m
```

Les trois chiffres concordent enfin. En cours de route, le bandeau descend
régulièrement (« Temps restant 1 min » à cent mètres de la fin) au lieu de rester
accroché à une valeur figée.

---

### NEW-03 — `P2` · le BFF rend déjà les manœuvres, et l'application les ignorait — **CORRIGÉ**

**Origine** — nouvelle anomalie, trouvée en interrogeant le BFF pour construire
les traces de la campagne.

La réponse de `GET /api/route` contient un tableau **`maneuvers`** complet, au
format même du modèle interne :

```json
{"location": [-1.559122, 47.212], "type": "turn", "modifier": "left",
 "street": "Rue Jean-Jacques Rousseau"}
{"location": [-1.558969, 47.211489], "type": "roundabout", "modifier": "slight right",
 "street": "Rue Félix Éboué", "exit": 2}
```

**34 manœuvres** pour un trajet de 5,8 km, **21** pour celui de 1,8 km — avec le
type, le modificateur, le nom de rue **et le numéro de sortie**, c'est-à-dire
exactement ce que `AND-BUG-006` est allé chercher ailleurs.

Or `RoutePayloadDto` ne déclare aucun champ `maneuvers` : la donnée arrive et est
**jetée au décodage**. L'application émet alors un second appel, vers
`https://router.project-osrm.org` — le serveur de démonstration public dont les
conditions d'usage excluent la production — pour reconstituer une information
qu'elle avait déjà.

**Ce que cela coûte, observé :**

- la dépendance d'`AND-BUG-004`, qui n'est plus qu'une variable de configuration
  mais reste une dépendance de production sur le chemin du guidage ;
- le risque d'`AND-BUG-005` — deux routeurs, deux tracés, des manœuvres agrafées
  sur une géométrie qui n'est pas la leur ;
- et le symptôme correspondant, relevé pendant un trajet nominal de cette
  campagne : `Manœuvres écartées : le routeur rend 1689 m pour une jambe de
  266 m.` La garde a fait son travail — **et le conducteur a perdu les manœuvres
  de cette jambe** ;
- un appel réseau de plus par jambe, sur le chemin le plus sensible de
  l'application.

**Test effectué** — requête directe sur le même point d'entrée que
l'application, comparaison du contenu avec `RouteDto.kt`, puis lecture du journal
en trajet.

**Résultat — `FAIL`** · Origine : nouvelle anomalie · Régression : **non**.

**Correction appliquée** — et elle est plus courte qu'attendu, parce que
**le mécanisme existait déjà**. `loadManeuversAround` teste `leg.maneuvers` avant
de sortir sur le réseau :

```kotlin
if (leg.maneuvers.isNotEmpty()) {
    maneuversByLeg[index] = pinManeuvers(painted = plan.points, raw = leg.maneuvers, …)
    continue                                  // ← pas d'appel au second routeur
}
```

Il ne manquait que de remplir ce champ. Trois gestes :

| Fichier | Ce qui change |
|---|---|
| `RouteDto.kt` | un `RouteManeuverDto` (`type`, `modifier`, `street`, `location`, `exit`) et son mapping vers `RoadManeuver` ; le champ est déclaré sur la charge utile **et** sur les variantes |
| `Route.kt` | `RouteCandidate` porte `maneuvers` |
| `Journey.kt` | un porte-à-porte est **une** jambe : ses manœuvres y sont posées |

Le mode transports n'est pas concerné — `/api/route?mode=transit` ne rend aucune
manœuvre, et un tram ne tourne pas. `OsrmRoadRouter` reste donc en place, en
repli, pour les jambes qui arrivent nues.

**Vérifications sur l'appareil.** La première est décisive : itinéraire calculé
**avec** réseau, réseau **coupé**, puis guidage démarré.

```
>>> itinéraire calculé ; coupure du réseau avant de démarrer
bandeau : « 100 m. Rue Jean-Jacques Rousseau. Tourner à gauche »
```

La consigne s'affiche sans réseau : elle vient du plan. Et le journal ne porte
**aucun** `Transport en échec sur /route/v1/driving` — la preuve directe que
l'appel n'est plus émis, là où une coupure l'aurait fait échouer bruyamment.

Puis le trajet complet, réseau rétabli, à 40 km/h :

| | Avant | Après |
|---|---|---|
| Appels au routeur de manœuvres | 1 par jambe | **0** |
| `Manœuvres écartées` (garde d'AND-BUG-005) | déclenchée en trajet réel | **0** |
| Consignes affichées | « Prendre la 1re sortie » | « 20 m · **Boulevard Jean Philippot** · Prendre la 2e sortie » |
| Hors-piste | 0 | 0 |

Les consignes sont **plus riches** qu'avant : le BFF nomme la voie de sortie.

**Et le profil est enfin respecté.** Le même trajet en mode piéton annonce
« 90 m. **Tourner à droite** », là où la voiture disait « Tourner à gauche, Rue
Jean-Jacques Rousseau » — deux itinéraires différents, deux jeux de consignes
différents. C'est très exactement ce qu'`AND-BUG-005` reprochait au serveur
public, qui servait du profil voiture même pour la marche.

---

### NEW-04 — `P3` · `/api/route` et `/api/geocode` répondent sans authentification

**Origine** — nouvelle anomalie, constatée en montant le protocole de test.

```
$ curl -s -w "%{http_code}" "https://www.aule.fr/api/route?v=28&mode=car&from=…&to=…"
200
```

Les deux points d'entrée répondent à une requête anonyme depuis une machine de
développement. C'est ce qui a permis de construire des traces fidèles — donc,
pour cette campagne, une commodité — mais un service de calcul d'itinéraire
ouvert est consommable par un tiers, et il est payé par le projet.

**Résultat — `FAIL`** · hors périmètre applicatif : la correction, s'il faut en
faire une, est côté BFF. Signalé pour décision.

---

### NEW-05 — `P2` · la mémoire native et GPU croît de 60 % en trente minutes — **PARTIELLEMENT CORRIGÉ**

**Origine** — nouvelle anomalie, révélée par le premier stress de trente minutes
jamais mené sur cet appareil.

Sous charge continue (guidage, carte 3D, flotte live, gestes, bascules, coupures
réseau), le PSS passe de **597 Mo à 952 Mo**, avec un pic à 1 037 Mo.

**La cause est identifiée, et ce n'est pas une fuite d'objets Java :**

```
Activities: 1     Views: 29     WebViews: 0
Dalvik Heap:    9,8 Mo   ← le tas Java ne bouge pas
Native Heap:  410   Mo
Graphics:     377   Mo   (GL mtrack 349 Mo + EGL mtrack 27 Mo)
```

Une seule activité et 29 vues après trente minutes : le profil est sain, et les
dix cycles de la section 15 le confirment (threads stables, aucun service
orphelin). **Tout est dans le cache natif et GPU de MapLibre** — tuiles
vectorielles et textures accumulées à mesure que la carte couvre du terrain. Un
`send-trim-memory RUNNING_CRITICAL` ne le rend pas.

**Pourquoi cela compte quand même.** Le S21 a 8 Go ; un milieu de gamme à 4 Go
verrait le système commencer à tuer des processus d'arrière-plan, puis
l'application elle-même — et une application de guidage tuée en route, c'est le
trajet perdu. Aucun plafond de cache n'est visible côté application.

**Test nominal** — trente minutes de charge continue : croissance de 60 %.
**Test limite** — non joué : aucun appareil à faible mémoire n'était disponible.
**Test de non-régression** — dix cycles complets : threads, activités et
services stables, aucune fuite Java.

---

#### Ce qui a été corrigé, et ce qui ne l'est pas

**Le manque, réel :** MapLibre expose `MapView.onLowMemory()` et **ne
s'enregistre pas** comme `ComponentCallbacks2` — vérifié dans l'AAR 13.5.0 :
aucun `registerComponentCallbacks`. C'est donc à l'application de l'appeler, et
**aucun code du projet ne le faisait** : ni `onLowMemory`, ni `onTrimMemory`,
nulle part. Quand Android réclamait de la mémoire à une application au premier
plan, le cache de tuiles ne bougeait pas.

**Correction appliquée** — `MapMemory.kt` (neuf), `MapController.kt`,
`AuleMap.kt` :

```kotlin
fun shouldReleaseGraphics(level: Int): Boolean =
    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
```

L'hôte Compose enregistre un `ComponentCallbacks2` sur le contexte applicatif et
relaie vers `MapController.releaseGraphics()`, qui journalise puis appelle
`MapView.onLowMemory()`. Le seuil n'est pas à zéro : rendre les textures fait
recharger les tuiles, et le premier palier arrive sur un appareil qui respire
encore. Trois tests couvrent le seuil.

**Ce que la mesure dit — et il faut le dire en entier.**

La vérification a d'abord semblé spectaculaire : au passage en arrière-plan,
`Graphics` tombe de **192 Mo à 77 Mo**. Un témoin compilé **sans** le relais
donne… **190 Mo → 73 Mo**. La baisse ne vient donc pas de la correction, mais de
`MapView.onStop()`. Même chose pour un `send-trim-memory RUNNING_CRITICAL`
appliqué en arrière-plan : 77 → 4,5 Mo avec le relais, 73 → 4,5 Mo sans.

Le cas que la correction vise — **application au premier plan, système sous
pression** — n'a pas pu être reproduit sur cet appareil :
`am send-trim-memory` refuse un niveau élevé sur un processus au premier plan
(`Unable to set a higher trim level than current level`), et
`am memory-factor set CRITICAL` n'a déclenché aucun trim observable sur un S21
qui dispose de 8 Go.

**Conséquence, sans détour :**

| | |
|---|---|
| La croissance de 597 à 952 Mo en usage normal | **inchangée** — c'est un cache sans plafond exposé, il grossit tant que rien ne le réclame |
| La capacité à répondre quand le système réclame | **corrigée par construction**, non démontrée sur cet appareil |
| Le risque identifié — être tué sur un appareil à 4 Go | **réduit**, puisque l'application sait désormais rendre son cache au lieu de l'ignorer |

**Pourquoi ne pas plafonner franchement.** Le seul autre levier de l'API est
`MapLibreMap.setTileCacheEnabled(false)`, un interrupteur sans nuance : plus de
cache du tout, donc rechargement permanent des tuiles. La fluidité est un
objectif structurant de ce projet — l'ADR-006 lui consacre une architecture
entière, et la campagne la mesure à 119 Hz. Couper le cache pour gagner de la
mémoire sur un appareil qui n'en manque pas est un arbitrage qui ne se prend pas
au détour d'un correctif.

**Ce qui reste à faire** — mesurer sur un appareil à 4 Go, avec et sans le
relais. C'est le seul endroit où la différence se verra, et il n'était pas
disponible.

---

### NEW-06 — `P2` · « Vous avez quitté l'itinéraire » dans les ronds-points — **CORRIGÉ**

**Origine** — nouvelle anomalie, isolée en vérifiant la correction de `NEW-01`.
Elle remplace l'explication erronée que la première rédaction donnait au bandeau
rouge.

**Le fait.** Trace injectée **identique au tracé peint** — vérification :
289 points contre 289, écart médian **0,4 m**, maximum **0,7 m**, aucun point
au-delà de 32 m. Le véhicule ne quitte donc jamais son itinéraire. L'application
l'annonce pourtant :

```
18:21:27  Aule.Net: Hors itinéraire — recalcul vers Gare de Nantes.
18:21:41  Aule.Net: Hors itinéraire — recalcul vers Gare de Nantes.
18:22:08  Aule.Net: Hors itinéraire — recalcul vers Gare de Nantes.
18:22:23  Aule.Net: Hors itinéraire — recalcul vers Gare de Nantes.
```

**La mesure qui tranche.** Le trajet se coupe en deux : un premier tiers qui
enchaîne **huit ronds-points**, puis une portion droite (Allée Baco, boulevard de
Stalingrad). Les deux moitiés ont été rejouées séparément, à la même vitesse,
avec la même trace :

| Portion | Géométrie | Hors-piste |
|---|---|---|
| Trajet entier | huit ronds-points puis ligne droite | **2 à 4** |
| Portion finale seule (119 points) | aucune boucle | **0** |

La portion droite se parcourt sans un seul recalcul, et l'arrivée s'y déclare
proprement. **La cause est donc géométrique.**

**Le mécanisme.** `RouteProgress` documente exactement ce piège : « projeter au
plus proche saute d'un brin à l'autre sur un corridor emprunté à l'aller et au
retour ; la parade est une fenêtre ». Mais cette fenêtre est exprimée en
**fraction du tracé** : `FORWARD_WINDOW = 0.12`, soit **220 m** sur ce trajet de
1 832 m. Dans un rond-point, la branche d'entrée et la branche de sortie sont
distantes de quelques dizaines de mètres — largement dans la fenêtre. La
projection peut donc se poser sur la mauvaise branche, la déviation calculée
dépasse alors les 32 m d'`OffRoute`, et le recalcul part.

Le choix de la fraction se retourne aux deux bouts : sur un trajet de 50 km la
fenêtre vaudrait 6 km — elle ne protège plus de rien ; sur un trajet de 500 m
elle vaut 60 m — à 130 km/h, deux positions espacées de 2 s la franchissent.

**Ce que cela coûte** — un bandeau rouge injustifié en pleine manœuvre, là où le
conducteur a le plus besoin de sa consigne, et un appel de recalcul au BFF toutes
les douze secondes tant que dure la figure.

**Correction appliquée** — deux gestes, dans `PolylineProjection.kt` et
`RouteProgress.kt`.

**1. Les fenêtres passent en mètres.**

```kotlin
const val BACK_WINDOW_M = 40.0       // était 1,8 % du tracé
const val FORWARD_WINDOW_M = 150.0   // était 12 % — soit 220 m ici, 6 km sur 50 km
```

Ce qu'on borne, c'est le chemin parcouru depuis la position précédente, et cela
se compte en mètres. Cent cinquante mètres, c'est treize secondes à 40 km/h et
quatre à 130 : de quoi absorber un trou de signal sans offrir à la projection la
moitié d'un giratoire.

**2. Un rattrapage, parce qu'une fenêtre qui a laissé passer un saut enferme.**

L'avancement est alors trop en avant, la fenêtre arrière ne fait que quarante
mètres, et plus rien ne ramène au brin qu'on suit. `RouteProgress` consulte donc
le tracé entier — mais sous **trois** conditions, et la troisième est la moins
évidente :

```kotlin
if (windowed.deviationMeters <= RECOVERY_DEVIATION_M) return windowed   // 25 m
val global = project(position, onto = points) ?: return windowed
if (global.deviationMeters > RECOVERY_DEVIATION_M) return windowed
return if (abs(global.t - t) * total <= RECOVERY_SPAN_M) global else windowed   // 400 m
```

Sans cette borne de 400 m, le rattrapage **annulerait la fenêtre qu'il
complète** : un brin parallèle pris pour l'autre présente exactement les mêmes
symptômes qu'un décrochage — position sur le tracé, fenêtre qui ne l'y trouve
pas. Seule la distance les sépare. On rattrape un décrochage local, jamais une
téléportation. Le test « la fenêtre empêche le saut », qui vise un point à
1,2 km, reste vert pour cette raison.

**Le mode Guet n'est pas touché.** `ScheduledTrip` gardait ses propres fenêtres
en fraction (1 % en arrière, 18 % en avant) pour suivre une course entière sur
son tracé de ligne : il passe désormais par `projectWithin`, qui prend des
fractions, et son comportement est inchangé. Un réglage en mètres y demanderait
sa propre campagne.

**Vérification sur l'appareil** — même trajet, mêmes huit ronds-points, même
trace, à 40 km/h, **trois fois** :

| | Avant | Après |
|---|---|---|
| Hors-piste sur le trajet complet | 2 à 4 | **0**, **0**, **0** |
| Arrivée déclarée | oui (depuis `NEW-01`) | oui |
| Service et verrou en fin de course | 0 | 0 |

**Trois tests neufs** encadrent la correction : la fenêtre vaut le même nombre de
mètres sur un tracé dix fois plus long ; un décrochage sur le brin d'en face se
rattrape ; une sortie réelle reste une sortie.

---

## 5. Fiches de test

### 5.1 Démarrage

#### RETEST-AND-001 — Cold start · `PASS` · `P0` si échec

Quatre arrêts forcés suivis d'un lancement mesuré.

| Essai | `LaunchState` | `TotalTime` | Plantage |
|---|---|---|---|
| 1 | COLD | **942 ms** | 0 |
| 2 | COLD | 1 037 ms | 0 |
| 3 | COLD | 1 046 ms | 0 |
| 4 | COLD | 1 020 ms | 0 |

Journal du premier lancement, dans l'ordre :

```
Aule.App: Démarrage — Développement · 0.1.0-dev (1) · production
Aule.Gps: Autorisation : GRANTED
Aule.Gps: Flux démarré (ready).
Aule.Net: 2635 arrêts servis depuis le disque.
Aule.Map: Style light chargé, 8 couche(s) posée(s).
Aule.Map: Arrêts publiés : 1118 lieu(x), 2635 quai(s).
Aule.Net: Flotte : 79 véhicule(s) … sur 2500 m
Aule.Net: Catalogue revalidé : 2635 arrêts.
```

Vérifications demandées : pas d'écran blanc (capture à l'appui : carte 3D,
bâtiments en volume, puck et son cône), carte initialisée, GPS initialisé,
session tenue, données utilisateur servies, véhicules et arrêts présents.

**Absence de double requête** — comptage sur les quatre lancements :

| | essai 2 | essai 3 | essai 4 |
|---|---|---|---|
| arrêts servis depuis le disque | 1 | 1 | 1 |
| catalogue revalidé | 1 | 1 | 1 |
| sondage de flotte | 1 | 1 | 1 |
| style chargé | 1 | 1 | 1 |
| flux GPS démarré | 1 | 1 | 1 |

#### RETEST-AND-002 — Relance rapide · `PASS` · `P1` si échec

Dix cycles accueil/relance enchaînés à une seconde d'intervalle.

| | avant | après |
|---|---|---|
| PID | 16234 | **16234** — pas de recréation |
| threads | 71 | **71** |
| `TOTAL PSS` | 471 Mo | **433 Mo** — décroissant |
| `ActivityRecord` de l'application | 1 | **1** |
| demande de localisation active | 1 | **1** |

**Pas de subscription Supabase multiple** — le projet n'utilise pas Realtime : la
flotte est sondée en HTTP toutes les 15 s. Comptage après les dix cycles, sur
65 secondes d'observation : **5 sondages**, soit la cadence nominale. Une boucle
dupliquée aurait doublé ce nombre.

Aucun ralentissement progressif : les temps de retour au premier plan restent
constants, et l'application est ramenée au premier plan
(`Activity not started, its current task has been brought to the front`) au lieu
d'être reconstruite.

### 5.2 Géolocalisation

#### RETEST-AND-GPS-001 — Déplacement lent · `PASS`

Marche à 4 km/h le long d'un tracé réel. Positions successives, cap recalculé à
chaque segment (204° → 203° → 218°), vitesse tenue à 1,11 m/s. Le puck avance
régulièrement et prend sa forme orientée dès que la vitesse dépasse le seuil de
gel du cap ; à l'arrêt il redevient un disque. Aucun mouvement parasite observé.

#### RETEST-AND-GPS-002 — Déplacement rapide · `PASS`

Trajet automobile à 30 km/h sur 1 832 m, puis déviation à 40 km/h.

- **Fréquence d'actualisation** : `ProviderRequest[@+1s0ms, HIGH_ACCURACY]` —
  une position par seconde, tenue y compris écran éteint ;
- **suivi caméra** : la caméra reste en mode navigation, inclinée, orientée dans
  le sens de la marche, le puck ancré dans le tiers bas de l'écran ;
- **orientation** : cap calculé segment par segment, cohérent avec le tracé ;
- **position sur la bonne route** : le puck reste sur le ruban peint pendant tout
  le trajet — vérifié sur treize captures.

#### RETEST-AND-GPS-003 — Immobilisation · `PASS`

Arrêt de 150 s après un trajet.

```
position à t+20 s  : 47.218000,-1.541780
position à t+150 s : 47.218000,-1.541780
dérive mesurée     : 0,00 m
cadran             : « 0 km/h », lu aussi dans l'arbre d'accessibilité
                     (« Vitesse : 0 kilomètres-heure »)
```

Le puck ne dérive pas, la vitesse revient bien à zéro et le cadran ne disparaît
pas.

#### RETEST-AND-GPS-004 — Reprise après arrêt · `PASS`

Reprise à 6 km/h après immobilisation : le mouvement est repris dès la première
position, le puck reprend sa forme orientée, la caméra reprend le suivi, et la
progression reprend le long du tracé — vérifié sur les relevés minute par minute
de `RETEST-AND-BG-002`, où la position avance de façon continue pendant dix
minutes.

#### RETEST-AND-GPS-005 — GPS imprécis · `PASS`

Précision annoncée 40 m, bruit gaussien de 25 m, à l'arrêt. Positions relevées :

```
47.212670,-1.558070   47.212457,-1.558230   47.212386,-1.557589
47.212686,-1.558392   47.212678,-1.557957
```

soit une dispersion de l'ordre de 30 à 60 m. À l'écran, **le puck ne téléporte
pas** : il reste au centre d'un halo de précision élargi, qui dit honnêtement
l'incertitude. La vitesse reste à 0 km/h — le bruit n'est pas pris pour du
mouvement.

### 5.3 Caméra

#### RETEST-AND-CAM-001 — Virages successifs · `PASS`

Trajet de 1 832 m enchaînant **huit ronds-points**, dont quatre séparés de moins
de 30 m, parcouru à 30 km/h. Sur les treize captures :

- la caméra suit sans retard visible et se réoriente à chaque changement d'axe ;
- elle ne reste jamais tournée vers l'ancienne direction — le ruban peint part
  toujours vers le haut de l'écran ;
- aucune rotation excessive ni sur-oscillation : la caméra tourne du même angle
  que la route, pas davantage.

L'inclinaison est plafonnée par le moteur, comme prévu par l'ADR-009 :

```
Aule.Map: Inclinaison plafonnée par le moteur : 59.99999999999999° (demandé 60.0°).
          Le cadrage de navigation reposera sur le zoom.
```

#### RETEST-AND-CAM-002 — Rond-point · `PASS`

Les ronds-points de ce trajet sont abordés, contournés et quittés sans à-coup de
caméra : la rotation est progressive, l'axe de sortie est pris avant que le
bandeau ne passe à la manœuvre suivante.

### 5.4 Navigation automobile et instructions

#### RETEST-AND-AUTO-001 — Itinéraire simple · `PASS`

Trajet « Ma position → Gare de Nantes », mode Voiture : **1,8 km, 5 min**.
Comparé au calcul indépendant du même BFF (1 832 m, 289 s) et au routeur OSRM
public sur les mêmes extrémités (1 618 m sur une destination voisine) : la
cohérence est bonne, le tracé emprunte la voirie et respecte les sens de
circulation du réseau nantais.

#### RETEST-AND-AUTO-002 — Route piétonne à proximité · `PASS` (par lecture)

Le mode est un paramètre de la requête (`mode=car`), et le tracé rendu par le
BFF est celui d'un profil voiture. Sur le trajet de recette, le tracé automobile
(1 832 m) est **plus long** que le trajet à pied annoncé (20 min pour 1,4 km),
et ne l'emprunte pas : il contourne par le boulevard. Aucun segment piéton n'est
apparu dans un tracé voiture.

Réserve : `AND-BUG-005` rappelle que le **routeur de manœuvres** ignore le profil
demandé et sert du voiture même pour la marche — c'est le sens inverse, et la
garde le rattrape.

#### RETEST-AND-AUTO-003 — Sens interdit · `PASS` (par cohérence)

Le tracé suit les sens de circulation du boulevard Jean-Philippot et du cours
John-Kennedy, tous deux à sens unique sur la portion empruntée, et le trajet
retour n'est pas le symétrique de l'aller — ce qui est la signature d'un routeur
qui respecte les sens.

#### RETEST-AND-AUTO-004 — Mauvaise route volontaire · `PASS`

Voir la fiche `AND-BUG-003` : détection ≤ 12 s (plafond de temporisation),
recalcul et affichage en **158 ms**, ancien tracé remplacé d'un bloc.

#### RETEST-AND-AUTO-005 — Erreurs successives · `PASS`

Huit sorties d'itinéraire successives sur 200 s. Aucun blocage, aucun empilement
de calculs — la temporisation de 12 s les espace —, **un seul tracé à l'écran à
tout instant**, aucun retour à un ancien trajet.

#### RETEST-AND-NAV-002 — Virage gauche/droite · `PASS`

Première manœuvre du trajet, annoncée par le bandeau :

> **100 m** · Rue Jean-Jacques Rousseau · **Tourner à gauche**

à comparer à la manœuvre de référence n° 1 :
`turn / left / Rue Jean-Jacques Rousseau / 96 m`. Le libellé, la rue et le sens
concordent ; l'écart de distance (100 m contre 96 m) est l'arrondi d'affichage.

#### RETEST-AND-NAV-004 — Rond-point complexe · `PARTIAL`

Le numéro de sortie est bien annoncé (« Prendre la 1re sortie », « Prendre la 2e
sortie »), avec le nom de la voie de sortie (« Pont de Tbilissi ») et une
distance qui décroît correctement (350 m → 20 m). La flèche et le texte sont
cohérents.

**Ce qui reste `PARTIAL`** : la correspondance manœuvre par manœuvre entre les
21 manœuvres de référence et les bandeaux affichés n'a pas pu être établie
exhaustivement — les captures sont espacées de 16 s et le `uiautomator` ne rend
rien pendant que la carte s'anime (voir § 8, limites). Les quatre bandeaux
relevés sont conformes ; les dix-sept autres n'ont pas été échantillonnés.

#### RETEST-AND-NAV-001 et NAV-003 — `BLOCKED`

« Tout droit sur plusieurs intersections » et « route légèrement courbe sans
virage réel » demandent un trajet choisi pour ces figures et un relevé continu
des bandeaux. Le relevé continu n'est pas disponible (voir § 8).

### 5.5 Tracé d'itinéraire

#### RETEST-AND-ROUTE-001 — Visibilité de jour · `PASS`

Ruban teal saturé, largeur constante, liseré plus sombre sur les bords, posé
au-dessus du bâti et sous les étiquettes. Lisible sur fond clair, sur la
végétation, sur l'eau et au-dessus des faisceaux de voies ferrées — les quatre
fonds traversés par le trajet de recette.

#### RETEST-AND-ROUTE-003 — Zone très dense · `PASS`

Le trajet traverse l'hypercentre nantais (Decré, cours des 50-Otages, gare) :
bâti en volume, huit ronds-points, faisceau ferroviaire, canal. Le ruban reste
distinguable partout et les arrêts restent lisibles par-dessus.

### 5.6 Arrière-plan Android

| Test | Durée | Service | Wake lock | GPS | Position | Résultat |
|---|---|---|---|---|---|---|
| **BG-001** accueil | 1 min | actif | détenu | `HIGH_ACCURACY @+1s` | avance | `PASS` |
| **BG-002** accueil | **10 min** | actif à chaque minute | détenu | idem | avance sans trou | `PASS` |
| **BG-003** écran éteint | 90 s | actif | détenu | idem | avance | `PASS` |
| **BG-005** trois applications | ~20 s | actif | détenu | idem | avance | `PASS` |
| **BG-004** économie d'énergie | — | — | — | — | — | `BLOCKED` |

Relevé minute par minute de `BG-002`, application à l'accueil, guidage engagé :

```
min 1  service=1 verrou=1 position=47.212033,-1.555262
min 5  service=1 verrou=1 position=47.213785,-1.551101
min 10 service=1 verrou=1 position=47.216265,-1.545845
```

La navigation reste active, la position suit, le service de premier plan de type
`location` et le `PARTIAL_WAKE_LOCK 'aule:navigating'` tiennent, et l'écran
éteint ne change rien : `mWakefulness=Dozing` avec la demande de position
maintenue à une seconde en haute précision. Au retour, l'activité est **ramenée**
au premier plan, jamais reconstruite.

**`BG-004` est `BLOCKED`** et non `PASS` : `settings put global low_power 1`
n'active pas le mode sur un appareil branché — `dumpsys power` répond
`mSettingBatterySaverEnabled=false`. Le relevé pris pendant cet essai ne mesure
donc rien et n'est pas retenu. Rejouable en simulant le débranchement
(`dumpsys battery unplug`).

**Comportement à surveiller** — au retour de l'arrière-plan, deux ou trois
recalculs s'enchaînent (`15:22:50`, `15:23:06`, `15:23:22`). Ils surviennent en
fin de trajet, là où `NEW-01` fausse déjà la progression : la cause est
vraisemblablement la même, mais elle n'a pas été isolée.

### 5.7 Réseau

Coupures par `svc wifi disable` / `svc data disable`, bascules par activation
sélective. L'appareil porte une carte SIM active, ce qui a permis de jouer les
deux bascules.

| Test | Scénario | Résultat |
|---|---|---|
| **NET-001** | itinéraire demandé sans réseau | `PASS` |
| **NET-002** | coupure pendant la navigation | `PASS` |
| **NET-003** | rétablissement, application au premier plan | `PASS` |
| **NET-004** | Wi-Fi → données mobiles | `PASS` |
| **NET-005** | données mobiles → Wi-Fi | `PASS` |
| **NET-006** | trois coupures successives | `PASS` |

#### NET-002 — coupure pendant la navigation

`ping` confirme la coupure (`unknown host www.aule.fr`). Le journal :

```
Aule.Net: Hors itinéraire — recalcul vers Gare de Nantes.
Aule.Net: Transport en échec sur /api/route
Aule.Net: Recalcul en échec.
```

Le guidage **reste actif**, l'échec est journalisé sans exception remontée,
l'application ne plante pas et ne perd pas le trajet engagé.

#### NET-001 — itinéraire demandé sans réseau

Le volet passe à son état d'erreur, et c'est **le chantier non commité qui est
exercé sur son cas le plus délicat** :

- les trois segments du sélecteur **se réduisent à leur seul glyphe**, sans
  texte ni place réservée — exactement le comportement que documente
  `AuleConnectedButtonGroup` (« un libellé vide n'émet rien ») ;
- TalkBack les annonce néanmoins « **Transports, durée inconnue** », « À pied,
  durée inconnue », « Voiture, durée inconnue » — la chaîne
  `route_mode_unknown_a11y`, l'une des six ajoutées par le chantier, fait
  précisément le travail pour lequel elle a été écrite : une icône seule serait
  muette ;
- le corps affiche « **Itinéraire indisponible** — Le calcul n'a pas abouti.
  Réessayez dans un instant. »

La fiche d'arrêt, dans le même état, dit « **Horaires indisponibles** — Le réseau
n'a pas répondu. Les passages réapparaîtront dès qu'il redonnera signe », et le
bandeau de flotte passe à « **Positions non rafraîchies** ». Les trois messages
distinguent correctement la panne du vide.

**La recherche d'arrêts, elle, fonctionne hors ligne** — « Ranzay, Station de
tram · 5 quais, à 5,0 km, accessible en fauteuil » est servi depuis le cache
disque de 2 635 arrêts.

**Réserve, `P3`** — la recherche d'**adresses** hors ligne répond « Aucun
résultat · Aucun arrêt ni adresse ne correspond à "Gare de Nantes" ». C'est vrai
au sens strict (aucun arrêt de ce nom en cache) mais trompeur : le géocodeur est
simplement injoignable, et l'utilisateur conclut que le lieu n'existe pas. Les
trois autres messages d'erreur du même écran, eux, nomment la panne.

#### NET-003 et NET-006 — pas de multiplication après reconnexion

C'est le point que la mission demande de surveiller. Le projet n'utilise ni
Realtime ni WebSocket : la flotte est sondée toutes les 15 s. Comptage du nombre
de sondages par fenêtre d'observation :

| Situation | Fenêtre | Sondages | Nominal |
|---|---|---|---|
| Après un rétablissement | 70 s | **4** | 4 à 5 |
| Après **trois** coupures successives | 65 s | **4** | 4 à 5 |
| Après bascule Wi-Fi → données | 65 s | **4** | 4 à 5 |
| Après bascule données → Wi-Fi | 65 s | **3** | 4 à 5 |

Horodatage du dernier relevé : `17:00:07`, `17:00:23`, `17:00:38` — **15,4 s
d'écart**, la cadence nominale. Aucune boucle dupliquée, aucun appel en rafale
après reconnexion. Les trois coupures ont produit exactement trois
`Transport en échec`.

**Piège méthodologique à consigner** — un premier relevé de `NET-003` donnait
« 0 sondage sur 150 s », ce qui ressemblait à une anomalie sérieuse. Vérification
faite, l'appareil s'était verrouillé entre-temps (`mCurrentFocus=Bouncer`) :
application masquée, sondage suspendu, comportement correct. Le test a été
rejoué écran déverrouillé. **Aucune conclusion de ce rapport ne repose sur un
relevé pris écran verrouillé.**

### 5.8 Apparence, Material 3 et multi-écrans

#### RETEST-AND-ROUTE-002 — Visibilité de nuit · `PASS`

Bascule par Profil → Préférences → Sombre (`ButtonGroup` Clair / Sombre / Auto).

```
Aule.Map: Style dark chargé, 8 couche(s) posée(s).
```

Le ruban d'itinéraire passe au **vert menthe clair** sur fond sombre : le
contraste est plus fort que de jour. Bâti gris sombre, végétation vert profond,
eau bleu nuit, voies ferrées lisibles. Tout le chrome bascule avec la carte —
bandeau de manœuvre teal foncé, cadran de vitesse et bandeau bas en surface
sombre, textes blancs. Le puck devient un disque blanc lumineux, bien détaché.

Réserve de méthode : la bascule système seule (`cmd uimode night yes`) **ne
suffit pas** — l'application suit son propre réglage, et le style est resté
`light`. Il faut passer par ses préférences.

#### Material 3 après corrections — `PASS`

Les quatre gardes automatiques du design system sont des tests JUnit qui
balaient `app/` et `feature/`. Elles ont été **rejouées de force**
(`--rerun-tasks`, sans quoi Gradle les croit à jour) et passent, y compris sur le
`RouteSheet` réécrit : `0 ignoré` sur les 922 tests, donc les racines de sources
ont bien été trouvées.

Composants vérifiés à l'écran, dans les deux thèmes :

| Composant | Où | État |
|---|---|---|
| Volets (bottom sheets) | itinéraire, arrêt, recherche, profil, menu | `PASS` — poignée, paliers, `paneTitle` annoncé |
| Dialogue | « Arrêter le guidage ? » | `PASS` — thème Aule, deux actions |
| Cartes | variantes de trajet, favoris, passages | `PASS` — cerne sur la variante retenue |
| Boutons | « Démarrer », « Y aller », « Arrêter » | `PASS` |
| `ButtonGroup` | modes d'itinéraire, apparence, filtres | `PASS` — voir ci-dessous |
| FAB | « Ouvrir les actions » | `PASS` |
| États de chargement | « Itinéraire… » | `PASS` |
| États d'erreur | itinéraire, horaires, flotte | `PASS` |
| États vides | « Aucun résultat » | `PASS` avec la réserve `P3` ci-dessus |

Le `ButtonGroup` modifié par le chantier est le composant le plus exposé : il
sert désormais **quatre** appelants (`RouteSheet`, `LineStopsSheet`,
`GuetSettingsScreen`, `ReportSheet`). Les trois anciens n'ont pas régressé —
`icon` et `spoken` sont des paramètres optionnels, et les appels existants
compilent et rendent comme avant.

#### Tests multi-écrans — `PASS`

Volet d'itinéraire ouvert, puis reconfiguration à chaud (le manifeste déclare
`screenSize|density|fontScale` dans `configChanges` : l'activité est
reconfigurée, pas reconstruite).

| Configuration | Taille | Densité | Résultat |
|---|---|---|---|
| Référence S21 | 1080 × 2400 | 480 | `PASS` |
| Petit smartphone | 720 × 1560 | 320 | `PASS` |
| Grand smartphone | 1440 × 3200 | 560 | `PASS` |
| Densité basse | 1080 × 2400 | 420 | `PASS` |
| Petit + police ×1,3 | 720 × 1560 | 320 | `PASS` |
| Petit + police ×1,5 | 720 × 1560 | 320 | `PASS` |

Aucun débordement, aucun texte coupé, aucun bouton hors écran, la carte n'est
jamais masquée : le volet occupe environ 40 % de la hauteur sur grand écran,
50 % sur la référence, 58 % en petit écran avec police ×1,5.

**Observation `P4`** — à police ×1,5 sur petit écran, le sélecteur de modes
repasse **sur deux lignes** (« 20 / min »). C'est le repli prévu
(`maxLines`, ellipsis) et rien n'est tronqué, mais le gain de hauteur que le
chantier est allé chercher s'annule dans ce cas.

#### Localisation — `PASS`

Le chantier retire deux clés et en ajoute six, dans les deux catalogues.
Vérification de la complétude, tous modules confondus :

```
clés françaises : 819   clés anglaises : 819   écarts : aucun
```

Aucune clé sans traduction, aucune clé orpheline côté anglais — et 819 contre
803 en août, soit exactement les seize entrées nettes ajoutées depuis. L'ADR-011
tient : aucune des nouvelles formulations n'est portée par un modèle.

#### Vérifications automatiques

| | Août | Cette campagne |
|---|---|---|
| Tests JVM | 908, 0 échec | **922, 0 échec, 0 ignoré** |
| Android Lint | 0 erreur, 41 avertissements | **0 erreur, 17 avertissements** |
| Gardes du design system | dettes vides | **dettes vides**, rejouées de force |
| Tests instrumentés | 0 | **0** |

Les 24 avertissements de lint retirés depuis août sont `UseKtx` (14),
`LogNotTimber` (4), `UnusedResources`, `UnusedAttribute`, `ObsoleteSdkInt` (2) et
le couple `LockedOrientationActivity` / `DiscouragedApi` — ce dernier **par
`tools:ignore`**, non par correction (voir `SURV-05`). Les 17 restants sont des
versions de dépendances en retard d'un cran et `OldTargetApi`.

### 5.9 Véhicules temps réel, arrêts et horaires

#### RETEST-AND-LIVE-001 — Plusieurs véhicules · `PASS`

La flotte observée pendant la campagne va de **70 à 189 véhicules** simultanés
selon l'heure, dans un rayon de 2 500 m. Le bandeau de tête les compte
(« 115 à l'horaire ») et sert de porte d'entrée à la liste d'accessibilité
(« Liste les arrêts et véhicules autour de vous »).

Fluidité mesurée par l'application elle-même, en régime établi :

```
Aule.Map: Interpolation : 119 Hz · rendu : 99 ips ·
          coût moyen 355 µs, pire 1977 µs (budget 8333 µs à 120 Hz)
```

L'interpolation tient ses 119 Hz. Le coût moyen relevé varie de **355 µs à
3 600 µs** selon les phases, pour un budget de 8 333 µs — c'est-à-dire entre 4 %
et 43 % du budget d'image. Les valeurs hautes sont relevées pendant les phases
de chargement (style, arrêts, flotte) ; en régime établi, on retombe dans les
centaines de microsecondes. L'ADR-006 tient sur l'appareil.

**Comparaison avec la campagne d'août** — le rapport précédent relevait
« 325 à 528 µs ». Les pointes à 3 600 µs de cette campagne sont mesurées avec
79 à 189 véhicules et pendant les chargements, là où le relevé d'août ne précise
pas la charge. L'écart n'est donc **pas comparable en l'état** et n'est pas
retenu comme une régression ; il justifie un relevé dédié à charge égale.

#### RETEST-AND-STOP-002 — Sélection d'un arrêt · `PASS`

Fiche « École Centrale - Audencia » ouverte depuis la recherche :

- identité et attributs — nom, **Ligne 2**, direction « Gare de Pont Rousseau » ;
- deux actions — « **M'alerter** » et « **Focus** », avec l'explication de leur
  indisponibilité quand elle s'applique : « Véhicule pas encore repéré sur la
  carte : le focus attendra » ;
- **Prochains passages** — `12:55 · Temps réel · 2 min`, `13:02 · Temps réel ·
  9 min`, `13:14 · Temps réel · 21 min`, `13:20 · Horaire · 27 min`,
  `13:26 · Horaire · 33 min`.

Fiche « Ranzay » ouverte hors ligne : attributs (Bus, Accessible, code quai
`RAZA5`), bouton « **Y aller** », et la section horaires dans son état d'erreur.

#### Section 11 — Horaires · `PARTIAL`

Ce qui est vérifié, sur la fiche de la ligne 23 à Ranzay (relevé à 17:23) :

| Heure | Source | Reste |
|---|---|---|
| 17:25 | **Temps réel** — pastille pleine | 1 min |
| 17:40 | **Temps réel** | 16 min |
| 17:55 | **Temps réel** | 31 min |
| 18:13 | *Horaire* — pastille creuse | 49 min |
| 18:34 | *Horaire* | 70 min |
| 18:55 | *Horaire* | 91 min |

**Le prochain passage**, la **succession** jusqu'à une heure et demie, et surtout
la **bascule du mesuré au théorique dans la même liste** : les trois premiers
passages sont temps réel, les suivants sont l'horaire. Les deux états se
distinguent par la pastille *et* par le mot, pas par la couleur seule.

Ce qui n'est **pas** vérifié : journée complète, demain, jour sans service,
dernier passage de la journée, passage après minuit, et les erreurs de
changement de date. Ces cas demandent soit de déplacer l'horloge de l'appareil
— ce qui invaliderait les jetons de session et la flotte temps réel pour le
reste de la campagne —, soit un jeu de données de test côté BFF. **`BLOCKED`,
et c'est la lacune la plus nette de cette campagne.**

#### RETEST-AND-LIVE-002, LIVE-003, STOP-001, STOP-003 — `BLOCKED`

Apparition et disparition d'un véhicule des données live, orientation d'un
véhicule en virage, arrêts très rapprochés, restauration de la position de
caméra au retour : tous demandent d'observer un véhicule réel choisi au bon
moment, ou une zone d'arrêts serrés repérée à l'avance. Non joués.

### 5.10 Endurance — répétition

#### Section 15 — dix cycles complets · `PASS`

Dix fois de suite : ouvrir une destination, calculer un trajet, démarrer le
guidage, l'abandonner par le geste de retour, puis recommencer avec **une autre
destination** (alternance « Gare de Nantes » / « Ranzay »).

| Cycle | Threads | `ActivityRecord` | Services de premier plan |
|---|---|---|---|
| 1 | 83 | 1 | 0 |
| 2 | 73 | 1 | 0 |
| 3 à 10 | **72** | **1** | **0** |

Relevés à la fin des dix cycles :

```
PID          5155 — le même qu'au premier cycle, aucun redémarrage
plantages    0 (aucun FATAL EXCEPTION, aucun ANR)
activités    1
images       38 176 rendues, 460 saccadées — 1,20 %
```

- **pas d'accumulation de listeners** : les threads décroissent de 83 à 72 puis
  se stabilisent ;
- **pas d'activités empilées** : une seule, du premier au dixième cycle ;
- **pas de service orphelin** : zéro après chaque abandon ;
- **pas de tracé fantôme** : la carte revient à son état sans itinéraire entre
  deux cycles ;
- **pas de plantage**, et **pas de dégradation** : 1,20 % d'images saccadées sur
  38 176, contre 3,98 % relevés en août.

Une réserve de mesure : le relevé de PSS par cycle a été perdu (l'extraction de
`dumpsys meminfo` était fautive dans le script). La mémoire est mesurée à la
place minute par minute sur trente minutes — voir ci-dessous.

---

### 5.11 Endurance — stress de trente minutes

#### Section 14 — `PASS` avec une réserve

Trente minutes d'affilée, guidage engagé, avec toutes les charges de la mission
menées **en même temps** : navigation active, carte 3D, véhicules live, GPS à
1 Hz, panoramiques et gestes de caméra toutes les minutes, bascules
premier-plan / arrière-plan, et coupures réseau suivies de rétablissements —
cinq de chaque sur la durée.

| Minute | PSS | RSS | Threads | Temp. | CPU | Service |
|---|---|---|---|---|---|---|
| 1 | 597 Mo | 721 Mo | 74 | 43,2 °C | 113 % | 1 |
| 5 | 587 Mo | 712 Mo | 71 | 41,4 °C | 170 % | 1 |
| 10 | 550 Mo | 673 Mo | 76 | 41,3 °C | 170 % | 1 |
| 15 | 704 Mo | 818 Mo | 72 | 41,2 °C | 143 % | 1 |
| 20 | 811 Mo | 925 Mo | 73 | 41,3 °C | 116 % | 1 |
| 25 | 806 Mo | 920 Mo | 71 | 41,4 °C | 203 % | 1 |
| 30 | **952 Mo** | 986 Mo | 71 | 41,7 °C | 187 % | 1 |

**Ce qui tient :**

- **aucun plantage, aucun ANR** sur les trente minutes ;
- **aucun gel** : le service de premier plan est resté à 1 à chaque relevé, le
  guidage n'a jamais décroché ;
- **threads stables** — 71 à 80, sans dérive ;
- **température stable** — 43,2 °C au départ (l'appareil sortait déjà d'une
  longue session), 41,7 °C à l'arrivée. Elle **descend** au lieu de monter ;
- **CPU** entre 105 % et 300 % d'un cœur, c'est-à-dire 1 à 3 cœurs sur huit.

**La réserve, et c'est `NEW-05` :** le PSS passe de **597 à 952 Mo**, avec un pic
à 1 037 Mo. La croissance n'est pas monotone — elle oscille, donc le
ramasse-miettes travaille — mais la tendance est nette : **+ 60 % en trente
minutes**.

**Ce n'est pas une fuite Java.** Le détail, relevé à la fin :

```
Activities: 1     Views: 29     ViewRootImpl: 1     WebViews: 0
Dalvik Heap:    9,8 Mo
Native Heap:  410   Mo
Graphics:     377   Mo   (dont GL mtrack 349 Mo, EGL mtrack 27 Mo)
```

Une seule activité, 29 vues — le même profil sain qu'en août (28 vues), et celui
que les dix cycles de la section 15 confirment. **La croissance est entière dans
le tas natif et la mémoire GPU de MapLibre** : tuiles vectorielles et textures
accumulées à mesure que la carte parcourt du terrain. Un
`send-trim-memory RUNNING_CRITICAL` ne la rend pas (935 → 957 Mo), ce qui est
attendu : ce cache n'est pas sous le contrôle du ramasse-miettes Java.

**Fluidité sous charge** — `gfxinfo` remis à zéro au début des trente minutes :

```
13 653 images rendues, 2 405 saccadées — 17,62 %
50e centile 15 ms · 90e 29 ms · 95e 36 ms · 99e 48 ms · 203 vsync manqués
```

À comparer aux **1,20 %** relevés pendant les dix cycles de la section 15, et aux
3,98 % d'août. La dégradation est réelle mais elle mesure un cas volontairement
défavorable : gestes de carte, bascules d'application et coupures réseau toutes
les minutes, sur trente minutes. Le 50e centile à 15 ms reste sous le seuil des
60 Hz ; c'est le 90e (29 ms) qui décroche.

#### Section 18 — Test final terrain · `PASS` par composition

La mission demande un trajet de 20 à 30 minutes comprenant virages, rond-point,
erreur volontaire, recalcul, passage en arrière-plan, coupure réseau et retour
réseau. Aucune session unique ne les a tous enchaînés ; **tous ont été joués**,
répartis sur trois trajets :

| Élément demandé | Où il a été joué |
|---|---|
| plusieurs virages | trajet de 1 832 m, huit ronds-points et un virage à gauche |
| un rond-point | idem — « Prendre la 1re sortie », en mouvement |
| une erreur volontaire | déviation de 400 m plein nord à 40 km/h |
| un recalcul | 158 ms entre la détection et le nouveau tracé |
| un passage en arrière-plan | `BG-002`, dix minutes, et cinq bascules pendant le stress |
| une coupure réseau | `NET-002` et cinq coupures pendant le stress |
| un retour réseau | `NET-003`, `NET-006`, cadence nominale retrouvée |
| durée ≥ 20 min | stress de trente minutes, guidage engagé |

Ce qu'aucun de ces essais ne remplace : un véhicule réel, avec ses pertes de
signal et ses réflexions urbaines (voir § 6).


## 6. Ce que cette campagne n'a pas pu mesurer, et pourquoi

Trois limites, nommées pour que personne ne prenne un `PASS` pour plus qu'il ne
vaut.

**1. Le relevé continu des bandeaux de navigation.** `uiautomator dump` exige
que l'interface soit *idle*. La carte s'anime en permanence (interpolation à
120 Hz), donc pendant un guidage le dump **ne rend rien** — les six premiers
échantillons d'un premier essai sont revenus vides pour cette raison, et non
parce que l'écran était vide. Le relevé se fait donc par captures d'écran
espacées, ce qui échantillonne les bandeaux au lieu de les suivre. C'est ce qui
laisse `NAV-004` en `PARTIAL` et `NAV-001` / `NAV-003` en `BLOCKED`.

**2. La batterie.** L'appareil est resté branché en USB pendant toute la
campagne — c'est ce qui permet `adb`. Le niveau n'est pas descendu sous 96 %, et
`status: 5` (plein). Aucune mesure de consommation n'est donc possible, et le
mode économie d'énergie refuse de s'activer sur secteur, ce qui laisse
`BG-004` `BLOCKED`. La seule grandeur thermique exploitable est la température :
**41,9 °C** après deux heures de guidage non arrêté, contre 35,5 °C au repos en
août.

**3. Le trajet en véhicule réel.** Les traces sont fidèles au tracé du BFF, mais
elles sont **parfaites** : pas de perte de signal sous un pont, pas de
multitrajet urbain, pas de tunnel, pas de dérive d'horloge. `GPS-005` simule une
précision dégradée, mais un canyon urbain réel produit des erreurs corrélées que
le bruit gaussien ne reproduit pas.

### Une méthode qui a failli produire un faux positif

Les premiers essais injectaient les positions dans **trois** providers à la fois
(`gps`, `network`, `fused`). L'application déclarait alors « Vous avez quitté
l'itinéraire » toutes les 15 à 25 secondes sur un trajet pourtant suivi
exactement — huit recalculs sur 200 s.

Il aurait été facile d'en conclure à une détection de sortie trop sensible.
Contre-épreuve : le **même trajet, la même trace, un seul provider** —

```
guidage armé à 13:13:51
hors-piste : 0
```

Zéro. Les sorties venaient de la fusion de trois sources concurrentes, c'est-à-dire
du protocole de test, pas de l'application. **Toutes les mesures de navigation
retenues dans ce rapport sont postérieures à cette correction**, et les huit
recalculs du premier essai ne servent qu'à une chose : montrer que huit sorties
successives ne font ni s'empiler les calculs, ni apparaître deux tracés.

---

## 7. Régressions

**Aucune régression de code** n'a été trouvée : les huit correctifs d'août
tiennent, et la refonte du volet d'itinéraire n'a cassé aucun de ses trois
co-usagers du `ButtonGroup`. Les 922 tests passent — dont quatorze écrits
pendant cette campagne pour les correctifs qu'elle a livrés —, les quatre gardes
du design system sont vertes, le lint est passé de 41 à 17 avertissements sans
erreur.

Une seule entrée, et c'est une **régression fonctionnelle**, pas une régression
de code :

| | |
|---|---|
| **`NEW-01`** | La correction d'`AND-BUG-008` (l'arrivée arrête le guidage) existait et fonctionnait, mais son seuil la rendait **inatteignable en conduite réelle** : le défaut qu'elle devait fermer se reproduisait intégralement. `P1`. **Corrigée pendant cette campagne** (seuil en mètres), et vérifiée sur l'appareil. |

Deux comportements sont classés **à surveiller** plutôt que régressions, faute
d'avoir été isolés :

| | |
|---|---|
| **`SURV-01`** | `adopt()` — le remplacement de tracé après recalcul — appelle `routeProgress.reset()` **sans repère**, puis `advance()`. La première projection se fait donc sur **tout** le tracé, sans fenêtre. Moins grave depuis `NEW-06` : le rattrapage borné limite désormais ce qu'un mauvais départ peut coûter, mais la remise à zéro sans repère reste à revoir. |
| **`SURV-02`** | Un `SCREEN_BRIGHT_WAKE_LOCK` détenu par le `WindowManager` au nom de l'application reste acquis après l'arrêt du guidage (`ACQ=-1h54m` relevé). Il s'agit vraisemblablement du maintien d'écran d'une application de carte, légitime au premier plan ; sa durée de vie n'a pas été tracée jusqu'au bout. |
| **`SURV-03`** | Au retour d'arrière-plan, deux à trois recalculs s'enchaînent. Observé en fin de trajet, là où `NEW-01` fausse déjà la progression. |
| **`SURV-04`** | `Aule.Gps: Service de premier plan démarré.` est journalisé **deux fois** à chaque démarrage. `P4`, cosmétique, mais c'est le genre de doublon qui fait douter d'une fuite quand on lit un journal. |
| **`SURV-06`** | **Battement à l'arrivée.** Le service claque trois fois en cinq secondes avant de se stabiliser (`18:47:17` arrêté, `:18` démarré, `:22` arrêté), donc la notification apparaît et disparaît deux fois. Le motif **préexiste** aux correctifs de cette campagne — il figure au journal du 28/08 à `12:57:37`, `:38`, `:44`, avant toute modification —, et il converge seul. Piste : rendre `arrived` collant, une fois franchi. Non corrigé : hors du périmètre demandé, et cela demande de décider ce qu'on veut à l'écran, pas seulement dans le modèle. |
| **`SURV-05`** | Le verrou portrait n'a pas été corrigé : l'avertissement `LockedOrientationActivity` / `DiscouragedApi` a disparu du lint parce que `855544c` a ajouté `tools:ignore` dans le manifeste. Choix assumé et documenté — mais Android 16 continuera d'ignorer le verrou sur grand écran, et le signal qui le rappelait est éteint. |

## 8. Corrections validées

| Anomalie | Vérifiée par | Statut |
|---|---|---|
| **AND-BUG-001** — retour pendant le guidage | dialogue à l'écran, `mCurrentFocus`, ressources rendues | `PASS` |
| **AND-BUG-002** — session hors réseau | mécanisme éprouvé en août ; coupures rejouées sans déconnexion | `PASS` |
| **AND-BUG-003** — recalcul d'itinéraire | huit sorties réelles, 158 ms d'affichage, un seul tracé | `PASS` |
| **AND-BUG-005** — manœuvres d'un autre trajet | **garde vue tirer en trajet réel** | `PASS` |
| **AND-BUG-006** — sortie de rond-point | « Prendre la 1re sortie », **en mouvement** | `PASS` |
| **AND-BUG-007** — vitesse | 30 km/h exacts en roulant, 0 km/h à l'arrêt | `PASS` |
| **AND-BUG-009** — service fantôme | dix cycles, trois bascules : rien d'orphelin | `PASS` |
| **AND-BUG-015..018** — avertissements | lint 41 → **17**, 0 erreur | `PASS` |
| Régression d'interface n° 1 (dialogue violet) | dialogue au thème Aule | `PASS` |
| Régression d'interface n° 2 (cadran sous la pastille) | cadran centré, lisible | `PASS` |

**Et le chantier non commité** — le volet d'itinéraire réécrit fonctionne, y
compris sur ses cas limites : variante unique sans rôle radio, durées absentes
réduisant les segments à leur glyphe, libellés parlés pour TalkBack, barre
d'action hors défilement tenue jusqu'à une police ×1,5 sur écran de 720 px.

## 9. Corrections non validées

| Anomalie | Pourquoi |
|---|---|
| **AND-BUG-008** — l'arrivée qui ne rend rien | `PARTIAL`. Corrigée dans le code, inatteignable en conduite — voir `NEW-01`. |
| **AND-BUG-004** — dépendance OSRM | `PASS` pour le mécanisme de configuration, mais la dépendance est **toujours active** en exécution, et `NEW-03` établit qu'elle est évitable. |
| **AND-BUG-010** — tuiles non configurables | non traité, inchangé depuis août. |
| **AND-BUG-011** — aucun test instrumenté | `NOT_IMPLEMENTED`. Zéro test ajouté, y compris par la refonte du volet. |
| **AND-BUG-014** — Material 3 en alpha | inchangé, choix assumé par ADR. |
| **AND-BUG-019** — verrou portrait | non corrigé, désormais **masqué** au lint (`SURV-05`). |

---

## 10. Recommandation release

### Ce que cette campagne a changé par rapport à août

La réserve d'août tenait en un mot : **rouler**. Elle est levée, non par un
véhicule mais par un injecteur de positions écrit pour l'occasion, et par des
traces prises **sur le BFF lui-même** pour qu'elles suivent exactement le tracé
peint. Les sections GPS en mouvement, caméra, navigation automobile,
instructions, tracé et trajet long — toutes `BLOCKED` en août — ont été jouées.

Ce que cela a rapporté : **cinq anomalies nouvelles, dont quatre qu'aucune
lecture de code n'aurait données**, parce qu'elles ne se voient qu'à la fin d'un
trajet ou après trente minutes de charge.

Et ce que cela a confirmé : les huit correctifs d'août tiennent. Les
ronds-points disent quelle sortie prendre **en roulant**, la vitesse est juste au
kilomètre-heure près, le recalcul remplace le tracé en 158 ms sans jamais en
laisser deux, le geste de retour rend le service et le verrou, l'arrière-plan
tient dix minutes et l'écran éteint ne coupe rien.

### Les réserves, par ordre

**Quatre des six anomalies sont corrigées** — `NEW-01`, `NEW-02`, `NEW-03`,
`NEW-06` —, chacune vérifiée sur l'appareil. `NEW-05` l'est **partiellement**, et
la nuance est importante : le relais de pression mémoire qui manquait a été
ajouté, mais la croissance du cache en usage normal, elle, ne bouge pas.

| # | Anomalie | Criticité | Où cela en est |
|---|---|---|---|
| 1 | **NEW-05** — mémoire native et GPU | `P2` | Le relais manquant est posé et couvre le risque identifié — se faire tuer sur un appareil à 4 Go. La croissance de 597 à 952 Mo reste : c'est un cache sans plafond exposé. Reste à mesurer sur un 4 Go, avec et sans le relais ; c'est le seul endroit où la différence se verra. |
| 2 | **NEW-04** — points d'entrée BFF ouverts | `P3` | Hors application. Décision côté serveur. |
| 3 | `SURV-06` — battement du service à l'arrivée | `P3` | Trois transitions en cinq secondes, donc la notification clignote une fois. Préexistant, convergent, sans effet sur le guidage. |
| 4 | `AND-BUG-010` — tuiles non configurables | `P2` | Inchangé depuis août. Hors ligne, il n'y a pas de fond de carte. |
| 5 | `AND-BUG-011` — aucun test instrumenté | `P2` | Inchangé. Les six anomalies de cette campagne ont toutes été trouvées par l'écran ou la mesure, aucune par les tests. |

### La marche à suivre

1. **Mesurer `NEW-05` sur un appareil à 4 Go**, avec et sans le relais. Si la
   croissance y devient gênante malgré lui, l'arbitrage suivant est
   `setTileCacheEnabled` — mais c'est un interrupteur sans nuance, et il se
   décide contre l'ADR-006, pas au détour d'un correctif.
2. **Rejouer sur batterie** ce que le câble a interdit : `BG-004`, la
   consommation, la thermique en usage réel.
3. Revoir `SURV-01` — la remise à zéro sans repère de `adopt()` — maintenant que
   le rattrapage de `NEW-06` en limite les conséquences.
4. Et, quand un véhicule sera disponible, **rouler pour de vrai** — les traces de
   cette campagne sont fidèles mais parfaites ; elles ne connaissent ni les
   tunnels, ni les réflexions urbaines.

### Décision finale

> ## `GO AVEC RÉSERVES`
> ### — sans réserve bloquante

**Les quatre anomalies qui touchaient la conduite sont refermées.** L'application
sait finir une course — service, verrou et haute précision rendus à l'arrivée,
et « 0 min » en face de « 0 m ». Elle ne dit plus « Vous avez quitté
l'itinéraire » à qui suit sa route : trois passages sur le trajet aux huit
ronds-points, **zéro** fausse alerte contre deux à quatre avant. Et elle ne sort
plus chercher chez un tiers ce qu'elle a déjà reçu : **zéro appel** au serveur de
démonstration public, des consignes plus riches, un profil piéton enfin distinct
du profil voiture.

Les correctifs tiennent en environ cent cinquante lignes de modèle, de géométrie
et de décodage, couverts par **quatorze tests neufs**, et chacun a été rejoué sur
le S21. Deux anomalies d'août tombent avec eux : `AND-BUG-004` n'est plus une
dépendance de production mais un repli, `AND-BUG-005` n'a plus d'objet sur les
modes porte-à-porte.

Ce qui reste ne s'oppose pas à une mise en production, à condition de ne pas se
raconter d'histoire sur `NEW-05` : la mémoire monte toujours, et le seul appareil
où cela compte n'a pas été testé.

**Ce qui reste vrai, enfin** : aucune des six anomalies de cette campagne n'a été
trouvée par les 922 tests. Toutes l'ont été par un écran, un journal ou une
mesure — et quatre d'entre elles seulement parce qu'un trajet a été mené jusqu'à
son terme. C'est l'argument le plus net en faveur d'`AND-BUG-011`.

---

*Campagne menée le 28/08/2026 sur Samsung S21 `SM-G991B` (Android 15,
1080 × 2400, densité 480), flavor `development`, `0.1.0-dev (1)`.
**922** tests JVM, 0 échec (908 au constat, +14 écrits pour les cinq correctifs) ·
Lint 0 erreur, 17 avertissements · 819 clés de localisation dans les deux
catalogues, aucun écart.*
