# Plan technique — le mode Padeur

> **Statut** : proposition, avant toute implémentation · **Date** : 18/08/2026
>
> Ce document répond à une commande : bâtir dans Aule Pro un assistant de
> déplacement réseau pour les équipes MSR, en réutilisant ce qui existe et sans
> rien dupliquer. Il commence donc par ce qui existe.
>
> **Deux dépôts.** Ce qui vit en `core/`, `data/`, `feature/` et `app/` est dans
> *ce* dépôt, et chaque affirmation le concernant a été relue dans le code. Ce
> qui vit en `supabase/migrations/`, en RPC, ou dans `docs/CONTRAT-BFF.md`
> appartient au dépôt **BFF/Supabase**, qui n'est pas celui-ci : ces références
> sont reprises de la connaissance qu'on en a, **non vérifiées d'ici**, et
> chacune est à confirmer à sa source avant qu'un lot ne s'appuie dessus. Les
> endroits concernés le disent (§ 2, § 11, § 16, § 17).

---

## 1. La question à laquelle le mode répond

Ce n'est **pas** « comment aller de A à B ». C'est :

> « Depuis là où je suis, maintenant, quelles lignes et quelles correspondances
> valent le coup, sans sortir de ma zone ? »

Deux conséquences structurantes, à poser avant d'écrire une ligne de code :

1. **`/api/route` n'est pas le moteur du mode Padeur.** Il résout une origine et
   une destination connues. Ici la destination est justement ce qu'on cherche.
   Il restera utile en complément (tracé à peindre, temps de parcours), jamais
   comme classement.
2. **Le classement n'est pas le temps de trajet.** C'est un score métier, et un
   score métier qui ne s'explique pas ne sera pas suivi. Le moteur doit rendre
   *pourquoi*, sous forme de valeurs — jamais de phrases (ADR-011).

---

## 2. Ce qui existe déjà, et qu'on ne réécrit pas

L'inventaire est la partie utile de ce plan. Presque tout est là.

| Besoin du mode Padeur | Ce qui le couvre aujourd'hui |
|---|---|
| Position de l'équipe | `:core:location` — `FusedLocationProvider`, `LocationPurpose`, `NavigatingForegroundService`, `HeadingStabilizer` |
| Arrêts autour, un lieu par entrée | `NearbyDigestBuilder` / `NearbyDigest` — tri par distance, fusion des quais, `walkMinutes` jamais nul |
| Catalogue d'arrêts | `StopRepository.allStops()` — ~2 600 arrêts déjà chargés une fois par `MapViewModel` |
| Prochains passages | `StopRepository.departures(nom)` → `StopDepartures.grouped()` → `DepartureRow` (`nextWait`, `followingWaits`, horizon 60 min) |
| 404 ≠ 502 | `DeparturesOutcome.NOTHING_ANNOUNCED` / `PROVIDER_SILENT` — déjà distingués |
| Lignes desservant un lieu, hors horaire | `StopRepository.servingLines(nom)` → `ServingLine` |
| Bus / Tram / Navibus | `TransportMode.BUS / TRAM / BOAT` (« navibus » est déjà décodé dans `fromApiValue`) |
| Couleur et badge de ligne | `LinePaletteRepository`, `LineColor`, `LineBadge`, `RealtimeDot` |
| Desserte ordonnée d'une ligne | `DriverServiceRepository.fetchJourney(session, lineId, directionId)` → `LineJourney` |
| Course du jour horodatée | `DriverServiceRepository.nearestActiveTrip(...)` → `ScheduledTrip` + `ScheduledTripPath` |
| Catalogue des lignes (`route_id` ↔ libellé, terminus) | `DriverServiceRepository.fetchLines(session)` → `ServiceLine` / `ServiceDirection` — **toutes ces méthodes exigent une session** (§ 4) |
| Arrêts restants devant le véhicule | `remainingReliefStops`, `matchReliefStop`, `distinctStops` |
| Progression sur un tracé | `RouteProgress`, `PolylineProjection`, `journeyProgressAt` |
| Alerte à l'approche (arrêts / minutes / arrivée) | `HandoverAlertEngine` + `HandoverProgress` — loquet, hystérésis, refus d'alerter sur position périmée |
| Son et notification d'alerte | `AlertTone`, `AlertTonePolicy`, `HandoverAlertNotifier` |
| Carte, couches, rechargement de style | `MapController`, `MapLayerRegistry`, `StopsLayer`, `RouteLayer`, `VehiclesLayer`, `HandoverLayer`, `MapAmbiance` |
| Volets et HUD | `BottomSheetScaffold` de `MapScreen`, `NearbySheet`, `StopDetailSheet`, et la grammaire commune de `SheetChrome.kt` (`SheetTitle`, `SheetCard`, `SheetSectionLabel`, `SheetRowDivider`). **`internal` à `:feature:map`, comme le poller** (§ 4) |
| Barre d'actions | `MapActionBar` / `MapActionItem` |
| Surface posée sur la carte sans la démonter | le motif de `AuleRoot` (`showingHandover`, `showingPrise`…) |
| Habilitation MSR | `resolveAgentAccess` → `AccountModes.CONTROLE` / `MIXTE`, exposé par `AuthUiState.access` |
| Budget réseau tenu | `NearbyStopsModel` — plafond à 3 lieux, cadence 30 s, recul doublé, gigue. **La discipline est réutilisable ; la classe non** : elle est `internal` à `:feature:map` et porte son état dans un `mutableStateOf` (§ 4) |

Et côté serveur, la mission MSR **existe déjà**. Ce tableau décrit l'autre
dépôt : rien n'y a été relu depuis celui-ci, et chaque ligne est à reconfirmer
avant qu'un lot ne la tienne pour acquise.

| Objet | Où |
|---|---|
| `msr_missions` — `zone_geom GEOMETRY(Polygon,4326)`, `zone_type`, `zone_config`, `scheduled_start/end`, `depot_return_at`, `status`, `intervention_started_at` | `supabase/migrations/004_msr_missions.sql`, `020`, `048` |
| `msr_sectors` — secteurs nommés et géométriques | `004_msr_missions.sql` |
| Contexte de mission de l'agent | RPC `control_mission_workspace_context(p_mission_id)`, `my_control_plan_context()` |
| Journal de mission | RPC `log_mission_event(plan_id, type, actor, payload)`, tables `mission_events` / `mission_audit_log` |
| Habilitation « padeur » | `driver_has_pad_mention(team_id, driver_id)` — la mention **PAD** sur le membre d'équipe |
| Propositions de patrouille (structure prête, planificateur en attente) | `msr_patrol_proposals`, `msr_plan_patrol` (stub) |
| RLS | `msr_missions_access` : `agent_id = auth.uid()` ou rôle superviseur |

**La mention PAD existe côté serveur — et rien ne la lit côté client.**
`driver_has_pad_mention(team_id, driver_id)` est une fonction SQL ; aucune RPC
connue ne l'expose à l'agent. Ce que l'app sait lire, c'est `resolveAgentAccess`,
qui dérive `CONTROLE` de `msrControl || msrIntervention || staffRole ∈
{msr_agent, msr_supervisor, regulator, admin}` — **beaucoup plus large** que
« porte la mention PAD ». Le mode ne peut donc pas « lire le rôle » : il doit
choisir sa porte, et le dire. Le § 11 B tranche.

---

## 3. Ce qui manque vraiment

Six manques. Le premier est le socle réel du mode, et c'est celui qu'on avait
sous-estimé.

1. **Rien ne joint le monde temps réel au monde GTFS.** `DepartureRow` porte un
   libellé (`"C3"`) et une destination en clair ; `ServiceLine` porte un
   `route_id` ; `LineJourneyStop` porte un nom d'arrêt et `TransitStop` une clé
   `departuresKey`. Aucun identifiant commun, trois appariements par chaîne de
   caractères, et c'est de leur résultat que dépendent la descente conseillée,
   les correspondances et le score. Détail en § 5.
2. **Aucune géométrie de polygone dans `:core:geo`.** Pas de point-dans-polygone,
   pas de distance à une frontière. C'est le socle de la contrainte de zone.
3. **Aucun accès Kotlin à la mission MSR.** Il n'existe ni contrat ni
   implémentation ; tout est à écrire, mais sur des tables déjà en place.
4. **`zone_geom` n'est pas exposée.** `control_mission_full_json` rend `zone_id`,
   `zone_label` et `zone_config` — **pas la géométrie**. Point serveur bloquant,
   et dans un autre dépôt (§ 11 A).
5. **Aucune notion de pôle côté client.** *Combien de lignes* à Commerce ne se
   déduit pas du catalogue : il faut le lire par `servingLines`, un lieu à la
   fois, et le mettre en cache pour la mission. Le catalogue donne en revanche
   *combien de quais*, gratuitement — assez pour une estimation de pôle tant que
   la mesure n'est pas revenue, et c'est ce qui évite au moteur de démarrer
   aveugle (§ 5).
6. **Aucun historique de mission.** Lignes empruntées, pôles visités, durée par
   ligne : rien n'est conservé, et c'est ce qui nourrit l'anti-répétition.

Tout le reste est de l'assemblage — à une réserve près, qui n'est pas un manque
mais une dette : la discipline réseau existe, et elle est enfermée dans
`:feature:map` (§ 4).

---

## 4. Architecture

### Modules

```
core/geo          + GeoPolygon, pointInPolygon, distanceToBoundary   (pur, testé JVM)
core/model        + package `padeur/` : domaine + moteur             (pur, testé JVM)
                  + NetworkJoin — le pont temps réel ↔ GTFS          (pur, testé JVM)
                  + StopDetailPoller — extrait de :feature:map
                  + contrat MsrMissionRepository
core/map          + MissionZoneLayer, PadeurOptionLayer
data              + SupabaseMsrMissionRepository (PostgREST)
feature/padeur    ← NOUVEAU module : écran, ViewModel, formulations
app               + câblage AuleGraph, ouverture depuis AuleRoot
```

**Pourquoi le moteur va dans `:core:model` et pas dans un module `:core:msr`.**
Il a besoin de `TransitStop`, `StopDeparture`, `TransportMode`, `LineJourney`,
`ScheduledTrip`, `Coordinate`. Un module séparé dépendrait de `:core:model` de
toute façon : on paierait un nœud de graphe de plus pour zéro isolation réelle.
C'est aussi là que vivent déjà les moteurs purs comparables —
`NearbyDigestBuilder`, `StopSearch`, `HandoverAlertEngine`, `journeyProgressAt`.
Le sous-package `padeur/` suffit à marquer la frontière ; si le moteur dépasse
un millier de lignes, l'extraire restera mécanique puisque tout y est pur.

**Pourquoi `:feature:padeur` est un module et pas un dossier de `:feature:map`.**
`:feature:map` fait déjà 12 000 lignes et porte quatre ViewModels. Le mode Padeur
s'adresse à une autre population d'agents, a ses propres chaînes et son propre
cycle. `:feature:map` ne gagne qu'un paramètre optionnel — `onOpenPadeur: (() -> Unit)?`
— et un item de barre, exactement comme `onOpenHandover`.

**Ce que `:feature:map` doit tout de même céder.** La discipline réseau du § 13
— plafond, cadence, recul doublé, gigue — vit dans `NearbyStopsModel`, qui est
`internal` à `:feature:map` et expose son état par `mutableStateOf`. Elle n'est
donc pas importable depuis `:feature:padeur`, et `:core:model` ne peut pas
l'accueillir telle quelle : c'est une bibliothèque JVM pure, `api(:core:geo)` et
la sérialisation, sans Compose ni coroutines. Trois issues, une seule tenable :

| Issue | Coût | Verdict |
|---|---|---|
| Réécrire la discipline dans `:feature:padeur` | nul | **non** — ce serait la *troisième* copie (`StopDetailModel` est déjà la deuxième), et c'est la contrainte dure du mode qui divergerait en silence |
| Rendre `NearbyStopsModel` public | une ligne | **non** — `:feature:padeur` dépendrait de `:feature:map`, arête que le graphe n'a nulle part |
| **Extraire** en `StopDetailPoller` dans `:core:model` | ~150 lignes déplacées ; `:core:model` gagne `coroutines-core` et `:core:common` — deux modules JVM purs, aucun cycle ; l'état passe en `StateFlow` ; le plafond devient un paramètre au lieu d'une constante | **oui**, au lot 0 |

`NearbyStopsModel` et `StopDetailModel` deviennent alors deux usages du même
poller, et `:feature:padeur` un troisième. **Il n'existe aujourd'hui aucun test
sur `NearbyStopsModel`** — seul `JitterTest` couvre la gigue : l'extraction
commence donc par écrire ceux qui manquent, puis déplace. C'est du travail réel,
pas un `git mv`, et le lot 0 le porte.

**Même remarque, en plus léger, pour la grammaire des volets.** `SheetChrome.kt`
porte ce que les six volets de la carte ont en commun — titre, cartouche,
intitulé de section, filet — et ses quatre composables sont `internal` eux aussi.
La différence avec le poller est le coût : ce sont cent lignes d'habillage
au-dessus des jetons de `:core:designsystem`, dont `:feature:padeur` dépendra de
toute façon. On les descend dans `:core:designsystem` si le volet padeur veut
exactement la même grammaire — ce qui est souhaitable, un volet posé sur la même
carte ne devrait pas avoir un autre titre — sinon on s'en passe. Décision au lot
1, quand l'écran existe ; ce n'est pas un préalable comme l'est le poller.

**La session voyage avec les appels.** Toutes les méthodes de
`DriverServiceRepository` — `fetchLines`, `fetchJourney`, `nearestActiveTrip` —
prennent un `session: AuthSession` en premier paramètre. Le mode est donc inerte
sans session pro : `PadeurContext` la porte, `AuleGraph` et `AuleRoot` la
fournissent au `PadeurViewModel` comme ils le font déjà pour les écrans qui
appellent ce repository, et l'absence de session **ferme le mode** au lieu de le
laisser échouer appel par appel.

### Contraintes du dépôt à respecter

- **ADR-003** — pas de Hilt : tout se pose à la main dans `AuleGraph`.
- **ADR-011** — aucune phrase dans un modèle. Le moteur rend des `enum` de
  raisons ; `PadeurText.kt` (dans le feature) les formule via `strings.xml`,
  avec le catalogue `values-en/` **complet**.
- **ADR-006** — rien de haute fréquence dans l'état Compose. La position suivie
  et le tracé vont dans les sources MapLibre, pas dans un `State`.
- `:feature:*` ne dépend jamais de `:data`. L'écran ne voit que des interfaces.
- **Les repositories lèvent.** Aucune liste vide pour masquer une panne. Deux
  exceptions déjà établies : `RoadRouter` rend `null` (complément optionnel), et
  `departures` porte 404/502 dans `outcome`.
- Toute nouvelle couche carte s'enregistre dans `MapLayerRegistry` **et republie
  sa donnée dans `mount()`** : un rechargement de style vide tout, en silence.
- La garde du design system balaie `feature/` : aucune mesure chiffrée à la
  main, aucune ombre hors design system, aucun caractère en guise d'icône.

---

## 5. Le domaine

### Géométrie (`:core:geo`)

```kotlin
data class GeoRing(val points: List<Coordinate>)

data class GeoPolygon(
    val outer: GeoRing,
    val holes: List<GeoRing> = emptyList(),
) {
    val bounds: GeoBounds
    fun contains(point: Coordinate): Boolean          // lancer de rayon, borné par bounds
    fun distanceToBoundaryMeters(point: Coordinate): Double
}
```

`GeoBounds` et `coordinatesBounds` vivent aujourd'hui dans
`core/model/Route.kt` alors qu'ils sont de la géométrie pure et n'ont que trois
usages, tous internes au fichier. **Les déplacer dans `:core:geo`** est un
changement d'import mécanique, et `GeoPolygon` en a besoin.

Le test de contenance se fait en degrés après un rejet par boîte englobante :
à l'échelle d'une zone urbaine, la déformation ne change aucune décision, et
projeter chaque point coûterait cher pour un test appelé des milliers de fois.

### Mission (`:core:model/padeur`)

```kotlin
enum class MissionZoneKind { SECTOR, LINE_BUFFER, CUSTOM_POLYGON }

data class MissionZone(
    val id: String?,
    val label: String?,
    val kind: MissionZoneKind,
    /** `null` = zone déclarée sans géométrie. Le flux manuel en produit. */
    val polygon: GeoPolygon?,
)

data class PadeurMission(
    val id: String,
    val status: MissionStatus,
    val zone: MissionZone?,
    val startedAt: Instant?,
    val scheduledEnd: Instant?,
    val depotReturnAt: Instant?,
) {
    fun remaining(now: Instant): Duration?   // borne la plus proche : fin ou retour dépôt
}
```

`polygon == null` est un **cas réel** : la migration `048` a rendu la géométrie
optionnelle pour le flux manuel. Le moteur retombe alors sur « aucune contrainte
de zone » et l'écran le **dit**. Il n'invente pas de zone — c'est la même
discipline que « ne pas fabriquer d'arrêt de relève » dans `matchReliefStop`.

### Le réseau, vu du padeur

```kotlin
/** Un lieu et ce qu'on y trouve. La clé est `TransitStop.departuresKey`. */
data class TransitPlace(
    val key: String,
    val name: String,
    val coordinate: Coordinate,
    /** Quais du même lieu dans `allStops()`. Connu **sans une requête**. */
    val quays: Int,
    val modes: Set<TransportMode>,
    /** Renseigné à la demande par `servingLines`, mis en cache pour la mission. */
    val lines: List<ServingLine>,
) {
    /** Ce qu'on croit du lieu — et à quel titre on le croit. */
    val hub: HubEstimate
        get() = if (lines.isNotEmpty()) {
            HubEstimate(lines.map { it.line }.distinct().size, HubSource.MEASURED)
        } else {
            HubEstimate(quays, HubSource.QUAY_COUNT)
        }
    // Deux unités, jamais mélangées : `HubEstimate` porte laquelle.
}

/**
 * Combien de choses convergent ici — et de quoi on a compté.
 *
 * [count] n'a pas la même unité selon [source] : des lignes lues quand c'est
 * `MEASURED`, des quais quand c'est `QUAY_COUNT`. D'où deux seuils, et non un
 * seul : sept quais et sept lignes ne veulent pas dire la même chose.
 */
data class HubEstimate(val count: Int, val source: HubSource) {
    val isHub: Boolean
        get() = count >= when (source) {
            HubSource.MEASURED -> HUB_LINE_COUNT
            HubSource.QUAY_COUNT -> HUB_QUAY_COUNT
        }
}

enum class HubSource { MEASURED, QUAY_COUNT }
```

**Pourquoi l'estimation par quais, et pas seulement `servingLines`.** Un `isHub`
qui ne se calcule que sur `lines` vaut `false` partout au premier appel, puisque
`lines` vient de `servingLines(lieu)` — une requête par lieu. Le moteur
démarrerait donc avec `hubQuality` à zéro sur tous les candidats, et la stratégie
« Correspondances » rendrait exactement le même classement que « Mobilité
rapide » — silencieusement, sans qu'aucune panne ne soit signalée. C'est le pire
genre de défaut : le mode a l'air de marcher.

Le nombre de quais lève ça pour zéro requête. Il est déjà dans `allStops()`,
chargé une fois par `MapViewModel` : le compte de quais de **tous** les lieux du
réseau s'en déduit sans une requête, et un pôle d'échange se reconnaît à ses sept
quais bien avant qu'on ait lu ses lignes.

**Mais pas en groupant sur `departuresKey` seule.** Le dépôt a déjà tranché la
question, et dans l'autre sens : `StopSearch.cluster` groupe sur la clé **et**
une distance inférieure à `SAME_PLACE_METERS` (200 m), parce que « deux lieux du
même nom éloignés restent deux lieux ». Sur le seul nom, deux arrêts homonymes
distants fusionneraient en un faux pôle à six quais — et un faux pôle est
précisément ce que cette estimation doit éviter de fabriquer. Le comptage reprend
donc la règle de `cluster`, pas celle de `NearbyDigestBuilder` : cette dernière
groupe bien sur la seule clé, mais elle ne voit que des arrêts déjà proches de
l'agent, où la question ne se pose pas.

Un compte de quais n'est pas un compte de lignes — d'où `HubSource`, qui dit d'où
vient le chiffre, et deux seuils au lieu d'un. La mesure remplace l'estimation
dès que `servingLines` a répondu, l'écran ne promet jamais un décompte de lignes
qu'il n'a pas lu, et le score sait qu'il pondère une estimation (§ 7).

Dans tous les cas la règle du projet tient : **un lieu, une entrée**.

### La jointure des mondes

**C'est le socle réel du mode — davantage que la géométrie de zone.** Le moteur
fait travailler ensemble deux mondes qui ne partagent aucun identifiant : le
temps réel (`DepartureRow`, `ServingLine`, `TransitStop`) et le GTFS de la prise
de service (`ServiceLine`, `ServiceDirection`, `LineJourney`). Trois jointures,
toutes par chaîne de caractères :

| De | Vers | Ce qu'elle décide | Si elle rate |
|---|---|---|---|
| `DepartureRow.line` (`"C3"`) | `ServiceLine.id` (`route_id`) | quelle ligne on interroge | aucune descente proposée |
| `DepartureRow.destination` | `ServiceDirection` (`key`, `terminus`) | **quel sens** | on envoie l'équipe dans l'autre sens |
| `LineJourneyStop.name` | `TransitStop.departuresKey` | ce qu'on sait du lieu de descente | `hubQuality` s'effondre sans le dire |

La deuxième est la dangereuse. Une ligne non appariée ne produit rien ; un sens
mal apparié produit une recommandation **inversée, et qui a l'air normale**. Or
`ServiceDirection.id` vaut aujourd'hui `key.toIntOrNull() ?: 0` : un repli
silencieux vers le sens 0, exactement ce dont ce moteur ne veut pas.

D'où un composant unique, nommé et testé, dans `core/model/padeur/` :

```kotlin
/**
 * Le pont entre le temps réel et le GTFS.
 *
 * Rend `null` plutôt que de deviner : une option non proposée est un manque,
 * une option à l'envers est une faute.
 */
class NetworkJoin(
    private val lines: List<ServiceLine>,   // fetchLines(session), 1 par mission
    private val stops: List<TransitStop>,   // allStops(), déjà en mémoire
) {
    fun matchLine(row: DepartureRow): ServiceLine?
    fun matchDirection(line: ServiceLine, destination: String): ServiceDirection?
    fun matchPlace(stop: LineJourneyStop): TransitPlace?
}
```

Trois règles, dans cet ordre, pour chacune :

1. l'identifiant exact quand il existe ;
2. le nom normalisé par `normalizeStopName` — le normaliseur du dépôt, déjà
   employé par `matchReliefStop` et `distinctStops` ;
3. la proximité géographique, pour les lieux seulement, bornée comme
   `HANDOVER_RELIEF_MATCH_METERS` la borne déjà.

Puis **`null`**. Pas de meilleur effort, pas de sens 0 par défaut. C'est la
discipline de `matchReliefStop`, appliquée à trois jointures au lieu d'une.

Le taux d'appariement est une donnée d'exploitation, pas un détail interne : le
moteur compte ses échecs et les remonte dans `PadeurAdvice.degraded`. Un
renommage GTFS, un libellé de ligne qui change, et la jointure se dégrade — il
faut que cela **se voie**, au lieu de vider les options en silence.

### L'option proposée

```kotlin
data class PadeurOption(
    val id: String,
    val boarding: TransitPlace,
    val line: String,
    val lineColor: String?,
    val mode: TransportMode,
    val direction: String,
    val nextWait: Wait,                 // réutilise le type existant
    val followingWaits: List<Int>,
    val dropOff: TransitPlace,
    val rideStops: Int,
    val rideMinutes: Int?,              // null quand aucune course n'est calée
    val transfers: List<PadeurTransfer>,
    val newLines: List<String>,
    val zoneFit: ZoneFit,
    val score: Double,
    val reasons: List<PadeurReason>,    // 2 à 3, les plus contributives
)

enum class ZoneFit { IN_ZONE, TEMPORARY_EXIT, OUT_OF_ZONE, NO_ZONE }

enum class PadeurReason {
    LOW_WAIT, HUB_AHEAD, NEW_LINES, NEW_SECTOR, UNVISITED_LINE,
    FITS_REMAINING_TIME, STAYS_IN_ZONE, TEMPORARY_EXIT, TIGHT_TRANSFER,
}

data class PadeurAdvice(
    val recommended: PadeurOption?,
    val alternatives: List<PadeurOption>,   // 2 ou 3
    val computedAt: Instant,
    val degraded: Set<PadeurDegradation>,   // PROVIDER_SILENT, NO_ZONE, NO_SCHEDULE,
                                            // JOIN_INCOMPLETE, NO_SESSION…
)
```

`PadeurReason` est un `enum`, pas une phrase : `PadeurText.kt` le traduit.
« Recommandé : faible attente, 2 nouvelles lignes accessibles et secteur encore
non parcouru » se compose de trois raisons et de deux nombres.

---

## 6. Le moteur, en cinq étapes

Toutes pures. Les entrées arrivent d'un `PadeurContext` que le ViewModel remplit.

**1 — Ancrage.** Position, zone, heure, temps restant.
`NearbyDigestBuilder.build(stops, vehicles, around)` rend les lieux proches, un
par lieu, déjà triés. On écarte ceux dont la marche dépasse un plafond, et on
étiquette chacun par sa `ZoneFit`.

**2 — Candidats de montée.** Pour les `K` premiers lieux (`K = 3`, le même budget
que `NEARBY_DETAIL_LIMIT`), `departures` et `servingLines` en parallèle. Chaque
`DepartureRow` retenu par les filtres devient un candidat : ligne, direction,
prochaines attentes. Le filtre mode et le filtre ligne s'appliquent **ici**, en
dur — ce ne sont pas des poids.

**3 — Descente conseillée.** Le candidat passe d'abord par `NetworkJoin` (§ 5) :
ligne, puis **sens**. Sans les deux, il est écarté — on ne propose pas de
descente sur une ligne dont on ignore la direction, et un candidat écarté pour
cette raison incrémente le compteur qui alimente `JOIN_INCOMPLETE`.

La desserte vient ensuite de `fetchJourney(session, route_id, direction_id)`,
mise en cache pour toute la mission. On avance depuis l'arrêt de montée et on
évalue les arrêts d'aval : au plus 8, et en priorité ceux que `HubEstimate`
place en tête — mesure quand `servingLines` a répondu pour ce lieu, nombre de
quais sinon (§ 5), jamais « rien » faute d'avoir demandé.

Le temps de parcours vient de `ScheduledTrip` quand une course est calée, sinon
d'une vitesse commerciale moyenne par mode — et dans ce cas le champ est
**estimé**, l'écran le dit.

**4 — Correspondances à la descente.** `servingLines(descente)` donne les lignes
disponibles ; les horaires ne sont fiables que si les passages de ce lieu ont été
lus. Quand ils ne l'ont pas été, on annonce les **lignes** sans promettre de
minute, et on recalcule à l'approche — ce que la commande demande explicitement.
Annoncer « C3 dans 2 min » calculé sur une extrapolation serait exactement le
défaut que le projet a déjà corrigé sur les passages Aléop.

**5 — Score et classement.** § 7.

---

## 7. Le score opérationnel

```kotlin
data class PadeurWeights(
    val wait: Double,             // pénalité par minute d'attente
    val hubQuality: Double,       // bonus par convergence au lieu de descente
    val hubEstimated: Double,     // abattement quand la convergence est estimée
                                  // en quais faute d'avoir lu les lignes
    val newLines: Double,         // bonus par ligne jamais empruntée dans la mission
    val newSector: Double,        // bonus secteur non parcouru
    val zoneFit: Double,          // bonus rester en zone
    val repetition: Double,       // pénalité ligne / pôle déjà vus
    val timeFit: Double,          // pénalité si l'option déborde le temps restant
    val targeting: Double,        // bonus lignes / zones ciblées par le padeur
)
```

Quatre préréglages, un par stratégie :

| Stratégie | Ce qui domine |
|---|---|
| **Mobilité rapide** | `wait` fort, `hubQuality` faible — on part vite |
| **Correspondances** | `hubQuality` fort — on vise les pôles |
| **Couverture réseau** | `newLines` + `newSector` forts, `repetition` sévère |
| **Ciblage** | `targeting` fort, le reste en retrait |

La stratégie choisit les poids. Elle ne change **jamais** la contrainte de zone.
`PadeurWeights.zoneFit` n'est pas cette contrainte : il arbitre entre `IN_ZONE` et
`TEMPORARY_EXIT`, deux options qu'on garde toutes les deux. Le retrait des
options `OUT_OF_ZONE`, lui, se décide avant le score et ne se pondère pas — c'est
tout l'objet du § 8.

**Un pôle estimé pèse moins qu'un pôle mesuré.** Quand `HubEstimate.source` vaut
`QUAY_COUNT`, la composante `hubQuality` est multipliée par `hubEstimated`
(0,6 au départ) : le moteur s'en sert — c'est mieux que zéro, et ça évite la
dégénérescence décrite au § 5 — sans traiter un comptage de quais comme un
comptage de lignes lu. Et la `reason` `HUB_AHEAD` ne se déclenche que sur
`MEASURED` : on ne dit pas « pôle à la descente » sur une déduction.

Le score est normalisé par composante avant pondération, pour qu'un temps
d'attente en minutes et un nombre de lignes ne se comparent pas à l'aveugle. Les
`reasons` rendues sont les composantes dont la contribution dépasse un seuil,
triées par contribution décroissante — **le classement et l'explication sortent
du même calcul**, ce qui rend impossible une explication qui ne correspond pas au
rang.

---

## 8. La zone MSR, dans le moteur

Trois niveaux, décidés avant le score :

| `ZoneFit` | Condition | Traitement |
|---|---|---|
| `IN_ZONE` | montée et descente dans le polygone | normal |
| `TEMPORARY_EXIT` | la descente est hors zone mais à moins de `ZONE_TOLERANCE_M` de la frontière, ou le trajet y revient | conservé, **fortement pénalisé**, et **étiqueté** dans l'option |
| `OUT_OF_ZONE` | descente hors zone, sans retour | **écarté**, pas classé bas |
| `NO_ZONE` | mission sans géométrie | aucune contrainte, et l'écran le dit |

L'écart de `OUT_OF_ZONE` est un **retrait**, pas une pénalité : une pénalité
finit toujours par être compensée par trois bonus de couverture, et le jour où
elle l'est, l'équipe sort de sa zone sans que personne n'ait décidé quoi que ce
soit. Si le retrait vide la liste, l'écran annonce que la zone ne laisse pas
d'option — il ne propose pas une sortie en silence.

C'est ce que veut dire « intégrée au moteur et pas seulement affichée » : la zone
décide de l'ensemble des candidats, la carte ne fait que la montrer.

Côté carte, `MissionZoneLayer` peint le polygone (remplissage discret + trait),
sous les arrêts, au-dessus du fond — et republie sa géométrie dans `mount()`.

---

## 9. L'historique de mission et l'anti-répétition

```kotlin
data class MissionCoverage(
    val lines: Map<String, LineVisit>,     // dernier passage + durée cumulée
    val places: Map<String, Instant>,      // pôles visités
    val sectors: Map<String, Instant>,
) {
    fun repetitionPenalty(lineId: String, now: Instant): Double
}
```

La pénalité **décroît avec le temps** : `exp(-écoulé / demi-vie)`. Une ligne
empruntée il y a quarante minutes redevient presque neutre. Sans cette
décroissance, une mission de quatre heures finirait par n'avoir plus aucune ligne
« acceptable », et le moteur proposerait n'importe quoi plutôt que rien.

**Persistance.** D'abord locale — `PadeurMissionLogStore` dans `:core:model`,
implémentation `Preferences…` dans `:app`, exactement le motif de
`HandoverAlertPrefsStore` / `PreferencesHandoverAlertStore`. Elle doit survivre à
la mort du processus : une mission dure plus longtemps qu'un processus Android en
arrière-plan.

Ensuite serveur, en V2, **sans nouvelle table** :
`log_mission_event(plan_id, event_type, actor, payload)` existe déjà et alimente
`mission_events` + `mission_audit_log`. « ligne empruntée », « pôle visité » y
entrent tels quels.

**Les secteurs.** `msr_sectors` existe et porte des polygones nommés. Tant que
leur lecture n'est pas ouverte à l'agent, le MVP mesure la couverture par **lieu**
et par **ligne** — deux unités que le client possède déjà. Le secteur arrive avec
la lecture de `msr_sectors`, sans changer le moteur : c'est une troisième clé
dans `MissionCoverage`.

---

## 10. L'assistance pendant le déplacement

Une fois l'option retenue, l'état passe en suivi. Presque tout est déjà écrit :

- **« Descendre à Commerce, dans 3 arrêts — environ 7 minutes »** :
  `ScheduledTripPath` + `PolylineProjection` + `RouteProgress`, exactement ce que
  le suivi de relève fait contre le véhicule d'un collègue.
- **Alertes d'approche** : `HandoverAlertEngine` porte déjà le loquet,
  l'hystérésis à deux mesures et le refus d'alerter sur une position périmée.
  Plutôt que de le dupliquer, **extraire le mécanisme** (`ApproachLatch`, une
  trentaine de lignes) et poser dessus un `PadeurAlertEngine` avec ses propres
  genres : `DROP_OFF_IN_STOPS`, `TRANSFER_AHEAD`, `LEAVING_ZONE`. Le
  comportement de la relève ne change pas ; ses tests non plus.
- **Recalcul à l'approche** : quand `stopsRemaining <= 2` sur la descente, on
  relance `recommend` centré sur ce lieu et on annonce « correspondance
  intéressante dans 2 arrêts », puis les meilleures options **à cet arrêt**.
- **Son et notification** : `AlertTone` + `HandoverAlertNotifier`, déjà branchés.

---

## 11. Le contrat serveur

**Ce paragraphe engage un autre dépôt.** Rien de ce qui suit n'est vérifiable
depuis celui-ci : migrations, RPC et signatures sont repris de la connaissance
qu'on a du BFF/Supabase. Chacun est à confirmer — et à chiffrer — par le
propriétaire de ce dépôt **avant** que le lot correspondant ne démarre. Un plan
client ne planifie pas le travail d'une autre équipe ; il déclare ce dont il
dépend.

### A — `zone_geom` · dépendance inter-dépôt · propriétaire : équipe BFF

`control_mission_full_json` rend `zone_id`, `zone_label` et `zone_config`, mais
pas la géométrie. L'addition est additive et tient en une ligne :

```sql
'zone_geom', ST_AsGeoJSON(p_mission.zone_geom)::jsonb
```

Le reste du projet reçoit déjà les géométries PostGIS en GeoJSON — `gtfs_stops.geom`
est décodé par `JsonElement?.toCoordinate()` dans `DriverServiceDto.kt`. Le
décodage de polygone suit la même voie.

**Conséquence de planification, et c'est la vraie information :** tant que cette
ligne n'est pas livrée par une équipe qui n'est pas celle du client, le mode ne
peut tourner qu'en `NO_ZONE`. Le § 16 en tire les lots — la contrainte de zone ne
garde plus le premier incrément utile en otage.

### B — la porte du mode · décision à prendre, pas seulement une addition

Le § 2 l'a établi : `driver_has_pad_mention` existe côté serveur, et aucune RPC
connue ne l'expose à l'agent. Deux portes possibles, et il faut trancher avant le
lot 0, parce qu'elles n'ont pas le même coût :

| Porte | Ce qu'elle exige | Ce qu'elle laisse entrer |
|---|---|---|
| **`AccountModes.CONTROLE` / `MIXTE`** | rien — `resolveAgentAccess` la rend déjà | tout agent MSR (contrôle ou intervention), plus `regulator` et `admin` : **plus large que « padeur »** |
| **mention PAD** | une RPC à écrire côté BFF, donc une seconde dépendance inter-dépôt | les padeurs, et eux seuls |

**Retenu pour le MVP : la porte large, assumée comme telle.** Le mode ne fait
rien d'irréversible — il propose, le padeur décide (§ 17.1) — et l'ouvrir à un
contrôleur qui n'est pas padeur coûte une entrée de barre inutile, pas un
incident. La mention PAD reste la bonne porte à terme et rejoint la liste des
additions non bloquantes. Ce que ce plan ne fera pas, c'est **écrire qu'il lit la
mention PAD** alors qu'il lit `AccountModes`.

### Ce qu'on ne fait pas

Lire `msr_missions` en direct depuis le client. La RLS `msr_missions_access`
filtre sur `agent_id = auth.uid()`, or un padeur est rattaché à sa mission **par
son équipe** (`team_id`), pas par `agent_id` — la lecture directe rendrait zéro
ligne pour un agent parfaitement habilité. Le contexte passe donc par les RPC
`SECURITY DEFINER` prévues pour ça.

### Additions non bloquantes

Ouvrir `msr_sectors` en lecture à l'agent (couverture par secteur), exposer la
mention PAD (porte fine du B), brancher une source de perturbations (§ 14).

---

## 12. L'interface

La carte reste le socle. Le mode Padeur est une surface posée par-dessus,
montée par `AuleRoot` comme la relève l'est déjà — MapLibre garde son style et sa
position.

```
┌──────────────────────────────────┐
│  carte + zone + puck             │  la zone est peinte, pas décrite
│                                  │
│                                  │
├──────────────────────────────────┤
│  RECOMMANDÉ                      │  une seule option en avant
│    Tram 1 · dir. Beaujoire       │
│    Commerce · dans 2 min         │
│    (ic. descente)  Hôtel Dieu    │
│    (ic. corresp.)  C3, ligne 4   │  2 nouvelles lignes
│    « faible attente, secteur non parcouru »
├──────────────────────────────────┤
│  2 alternatives      [filtres]   │  repliées
├──────────────────────────────────┤
│        « Et maintenant ? »       │  action permanente
└──────────────────────────────────┘
```

**`(ic. …)` marque un emplacement d'icône, pas un caractère à écrire.** La
première version de cette maquette portait `▸ RECOMMANDÉ`, `↓ descendre` et
`↳ C3` ; transcrite telle quelle, elle aurait fait rougir la garde au premier
commit. `DesignSystemGuardTest` balaie `app/` et `feature/` — donc
`:feature:padeur` dès sa création, sans rien à configurer — et refuse hors
commentaire `✕ ✖ ← → ‹ › ▸ ▾ ⤓ 📍 🚋 🔔`. Une icône se dessine, elle ne s'écrit
pas : la flèche de descente, le chevron de repli et le marqueur de
correspondance sont trois icônes du design system.

Cinq choses lisibles d'un coup d'œil, dans cet ordre : où est l'équipe, quelle
est la prochaine action, quelles correspondances arrivent, quelles alternatives
existent, pourquoi celle-ci. Les alternatives sont **repliées** — trois options
dépliées à égalité visuelle, c'est trois décisions à prendre au lieu d'une.

Les filtres (type, lignes, stratégie) vivent dans un volet, pas dans le bandeau :
on les règle en début de mission, on ne les relit pas à chaque proposition.

**« Et maintenant ? »** est un seul appel — `PadeurEngine.recommend(context)` —
avec un point GPS frais. Le moteur étant pur, le bouton ne coûte que ce que
coûtent les données. En régime établi, le cache de mission le rend quasi
instantané ; **au tout premier appui, non** — et c'est celui-là que l'agent
juge. Le § 13 dit ce qu'on fait pour que ça ne se voie pas.

Ouverture : un item `MapActionItem` dans la barre existante, visible si
`AuthUiState.access?.modes` vaut `CONTROLE` ou `MIXTE` — la porte large du
§ 11 B, plus permissive que « porte la mention PAD », et assumée comme telle
pour le MVP.

---

## 13. Le budget réseau

C'est la contrainte dure du mode, et elle décide plus de choses que le score.
Le BFF n'expose `stop-departures` et `stop-serving-lines` **qu'un lieu à la
fois** : garnir douze lieux coûterait vingt-quatre requêtes par recalcul.

| Appel | Fréquence | Cache |
|---|---|---|
| `allStops()` | **0 de plus** — déjà chargé par `MapViewModel` | processus |
| `fetchLines()` | 1 par mission | mission |
| `departures` + `servingLines` | ≤ 3 lieux × 2, cadence 30 s, recul doublé + gigue | 30 s |
| `fetchJourney(ligne, sens)` | 1 par couple ligne-sens réellement envisagé (~10–20 par mission, **mais jusqu'à 10 d'un coup au démarrage** — voir plus bas) | mission |
| `nearestActiveTrip` | 1 par option suivie | suivi |
| flotte | déjà en cours | — |

`NearbyStopsModel` implémente déjà exactement cette discipline — plafond, recul,
gigue de moitié. Mais il est `internal` à `:feature:map` et porte son état dans
un `mutableStateOf` : **il n'est pas réutilisable en l'état**, et `:core:model`,
JVM pur, ne peut pas l'accueillir tel quel. Le § 4 tranche — on l'extrait en
`StopDetailPoller`, sur `StateFlow`, plafond en paramètre, tests écrits avant le
déplacement. C'est du travail budgété au lot 0, pas une réutilisation gratuite ;
ce qui se réutilise ici, c'est la discipline et le raisonnement qui l'a fixée,
pas la classe.

### Le premier appel est le pire, et c'est celui qui compte

Ce tableau décrit un **régime établi**. Le démarrage ne lui ressemble pas :
trois lieux, plusieurs `DepartureRow` chacun, autant de `fetchJourney` qu'il y a
de couples ligne-sens distincts — une dizaine de requêtes peuvent séparer le
premier appui sur « Et maintenant ? » de la première proposition. Amortir sur la
mission, comme le fait la ligne du tableau, masque exactement le moment où le
mode se fait juger.

Deux mesures, toutes deux dans le moteur, aucune dans l'écran :

1. **Classer avant de chercher.** L'attente, la distance à pied, la `ZoneFit` et
   l'estimation de pôle par quais (§ 5) se calculent **sans une seule requête**.
   Le moteur ordonne les candidats là-dessus, puis ne demande la desserte que des
   `JOURNEY_FETCH_LIMIT` premiers — quatre au départ. Les autres **ne deviennent
   pas des options** : `PadeurOption.dropOff` n'est pas nullable, et une option
   sans descente conseillée n'est pas une demi-option, c'est un objet qui ment.
   Ils restent des candidats, et remontent au recalcul suivant si leur rang
   tient. C'est le même raisonnement que `NEARBY_DETAIL_LIMIT` : on garnit ce sur
   quoi on agit, pas tout ce qu'on pourrait afficher.
2. **Préchauffer à l'ouverture, pas au clic.** `fetchLines()` — nécessaire à
   `NetworkJoin`, donc à tout le reste — et les `departures` des trois lieux de
   tête partent dès l'entrée dans le mode, pendant que l'agent regarde la carte.
   Au premier « Et maintenant ? », l'essentiel est déjà en cache.

Ce qu'on ne fait **pas** : afficher une liste incomplète en la donnant pour
complète. Tant que les `JOURNEY_FETCH_LIMIT` premières dessertes ne sont pas là,
l'écran est en attente ; il ne propose pas trois options faibles pour occuper le
temps.

---

## 14. Ce que le MVP ne fera pas, et le dira

- **Les perturbations.** Le client Kotlin n'a aucune source de perturbations
  aujourd'hui — `FleetSnapshot.degraded` parle de la santé des *sources*, pas des
  lignes ; `RouteCandidate.alertCount` ne vit que dans un plan d'itinéraire. Le
  MVP n'affiche donc **rien** à ce sujet plutôt qu'un pictogramme rassurant qui
  ne serait fondé sur rien. C'est un lot à part.
- **Les boucles de patrouille automatiques.** La table `msr_patrol_proposals` et
  le stub `msr_plan_patrol` les attendent ; le moteur d'options est le
  prérequis, pas l'inverse.
- **Les correspondances chronométrées à un lieu non lu.** § 6, étape 4.

---

## 15. Les tests

Tout le moteur est pur, donc testé en JVM, dans le style du dépôt (noms français
entre accents graves) :

- `:core:geo` — `GeoPolygonTest` : contenance, trous, point sur la frontière,
  polygone dégénéré, boîte englobante rejetante.
- `:core:model` — **`NetworkJoinTest` en premier**, parce que c'est la pièce
  porteuse : les trois jointures sur des **captures réelles** de `fetchLines` et
  `allStops` ; l'appariement de sens sur des destinations réellement observées ;
  et surtout les cas qui doivent rendre `null` — libellé de ligne inconnu,
  destination qui ne colle à aucun terminus, nom d'arrêt absent du catalogue.
  Un test dédié vérifie qu'aucun chemin ne retombe sur le sens 0.
- `:core:model` — `StopDetailPollerTest`, écrit **avant** l'extraction du § 4 :
  cadence, recul doublé plafonné, plafond de lieux, conservation du connu quand
  la liste suivie change. Ces tests n'existent pas aujourd'hui — `JitterTest`
  couvre la gigue et rien d'autre — et c'est précisément ce qui rend
  l'extraction risquée tant qu'ils ne sont pas là.
- `:core:model` — `PadeurZoneTest` (les quatre `ZoneFit`, dont le retrait qui
  vide la liste), `PadeurScoreTest` (chaque stratégie change bien le rang),
  `MissionCoverageTest` (décroissance de la pénalité), `PadeurFilterTest`
  (mode / lignes, filtres durs), `PadeurEngineTest` (option nominale, aucune
  option, fournisseur muet, mission sans zone, jointure incomplète, sans
  session). `PadeurScoreTest` porte en propre le cas du démarrage à froid :
  **« Correspondances » et « Mobilité rapide » ne rendent pas le même
  classement quand aucun `servingLines` n'a encore répondu** — c'est le test qui
  garde l'estimation par quais du § 5, et sans lui la dégénérescence repasserait
  inaperçue.
- `:data` — `SupabaseMsrMissionRepositoryTest` sur une **capture réelle** du RPC,
  comme les autres décodages.
- Les gardes existantes (`DesignSystemGuardTest`, `MaterialGuardTest`) couvrent
  le nouveau module sans rien ajouter : elles balaient `feature/`.

Pas de test de vue, pas de test du `MapController` — la règle du dépôt.

---

## 16. Les lots

Deux règles ont redécoupé les lots. **La pièce porteuse passe en premier** :
c'est `NetworkJoin`, pas `GeoPolygon` (§ 5). Et **aucun lot utile ne dépend d'un
autre dépôt** : la migration `zone_geom` appartient à l'équipe BFF (§ 11 A), donc
elle ne peut pas garder le premier incrément qui sert à quelqu'un.

| Lot | Contenu | Dépend d'un autre dépôt | Résultat visible |
|---|---|---|---|
| **0 — socle** | `NetworkJoin` + ses tests sur captures ; `StopDetailPollerTest` puis extraction ; `MsrMissionRepository` + impl (elle décode la mission **sans** `zone_geom`, donc `polygon = null`) ; `GeoPolygon` ; câblage `AuleGraph` et porte du § 11 B | **non** | Rien pour l'agent : c'est de l'infrastructure, et elle est entièrement sous notre main. |
| **1 — MVP** | Contexte, candidats, descente, score, 3 options + 1 recommandée + raisons, filtres, « Et maintenant ? » — en `NO_ZONE` | **non** | **Le mode répond à sa question**, sans rien attendre du BFF. |
| **2 — zone** | `MissionZoneLayer`, `ZoneFit` dans le moteur, retrait des options hors zone, `TEMPORARY_EXIT` | **oui** — § 11 A | La zone contraint le moteur et se peint sur la carte. |
| **3 — suivi** | Option retenue, prochaine décision, `ApproachLatch` + `PadeurAlertEngine`, recalcul à l'approche | non | Le mode accompagne le déplacement. |
| **4 — couverture** | `MissionCoverage` persisté, anti-répétition, bilan de mission | non | Le mode cesse de reproposer les mêmes lignes. |
| **5 — au-delà** | mention PAD (§ 11 B), `msr_sectors`, `mission_events`, perturbations, boucles de patrouille, adaptation au temps restant | oui | Les extensions annoncées. |

Le lot 2 n'est pas un repli : le § 5 pose déjà `NO_ZONE` comme **un cas réel**,
pas un mode dégradé — une mission au flux manuel n'a pas de géométrie, et le
moteur doit de toute façon savoir tourner sans. Livrer le MVP en `NO_ZONE`, c'est
donc exercer d'emblée un chemin qu'il faudra tenir de toute façon, au lieu de
patienter derrière une équipe qu'on ne pilote pas.

L'architecture des lots 0 à 4 est déjà celle du lot 5 : le moteur prend un
`PadeurContext` et rend un `PadeurAdvice`. Ajouter les perturbations, c'est un
champ de contexte et un poids ; ajouter les boucles, c'est enchaîner `recommend`
sur son propre résultat ; ajouter la zone, c'est un filtre sur l'ensemble des
candidats — ce que le lot 2 fait sans rien réécrire.

---

## 17. Points d'attention

1. **Le padeur garde la décision.** Aucune proposition ne s'engage seule, aucun
   recalcul ne change l'option suivie sans un geste. C'est une exigence métier,
   et c'est aussi ce qui rend acceptable un moteur qui se trompe parfois.
2. **Ne jamais inventer une minute.** Le projet a déjà payé ce défaut sur les
   passages Aléop, où six quais sur douze rendaient 502 et où la fiche écrivait
   « Aucun passage annoncé ». Une correspondance sans horaire lu s'annonce sans
   horaire.
3. **La mission sans géométrie est un cas normal**, pas une panne. L'écran le dit
   une fois, et le moteur tourne sans contrainte de zone.
4. **Le rayon de flotte n'est pas quantifié — à vérifier avant d'y toucher.**
   Le fait est établi dans ce dépôt : `AuleVehicleRepository` envoie `radius`
   arrondi au mètre (`radiusMeters.roundToInt()`). **La règle qu'il enfreindrait
   ne l'est pas** : `RADIUS_LADDER` n'apparaît nulle part ici, et
   `docs/CONTRAT-BFF.md` non plus — c'est une référence au dépôt BFF, citée de
   mémoire. Il faut donc relire le contrat à sa source avant d'en faire un
   correctif. Si l'échelle est bien imposée, l'écart est préexistant et sans lien
   avec ce plan, mais le mode Padeur empruntera ce chemin : autant le corriger
   avant d'y ajouter du trafic. Si elle ne l'est pas, il n'y a rien à corriger,
   et cette ligne disparaît.
5. **Le point de rupture est la jointure, pas la zone.** Le mode a l'air de
   dépendre de la géométrie MSR ; il dépend en réalité de trois appariements par
   chaîne de caractères (§ 5). Un renommage GTFS ne fera pas planter le moteur —
   il le rendra muet, ou pire, à l'envers. `NetworkJoin` rend `null` par défaut,
   compte ses échecs et les remonte : c'est toute la protection qu'on a, et elle
   se teste sur des captures réelles ou elle ne vaut rien.
6. **Deux dépôts, deux régimes de preuve.** Tout ce que ce plan affirme sur
   `core/`, `data/`, `feature/` et `app/` a été relu dans le code. Tout ce qu'il
   affirme sur les migrations, les RPC et le contrat BFF est repris de mémoire et
   **n'a pas été vérifié**. Les deux ne se lisent pas de la même façon : la
   seconde catégorie se confirme à la source avant qu'on planifie dessus (§ 11,
   § 16, § 17.4).
