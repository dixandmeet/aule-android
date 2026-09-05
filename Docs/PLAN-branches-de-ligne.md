# Plan technique — le sélecteur de branche

> **Statut** : **implémenté et vérifié à l'écran** · **Cadré** le 04/09/2026,
> **livré** le 05/09/2026
>
> Ce document répond à une commande : offrir dans la fiche d'une ligne le choix
> de la **branche**, comme le fait déjà l'application iOS. Tout ce qui concerne
> ce dépôt a été relu dans le code ; ce qui concerne le BFF vient de
> `../docs/CONTRAT-BFF.md` et de `../Native`, et est signalé comme tel.

---

## 1. Ce que le sélecteur répond, et pourquoi il manque

La fiche annonce aujourd'hui « Vers Beaujoire / Babinière » et n'en montre
qu'une. Le libellé est celui du **terminus annoncé**, qui nomme la paire ; la
liste, elle, est celle d'un parcours unique. Rien à l'écran ne dit lequel.

Mesuré sur l'appareil de référence le 04/09/2026, journal à l'appui :

```
1 sens 0 : 1-0=15 (Beaujoire → Commerce), 1-2=15 (Babinière → Commerce)  — retenu 1-0
1 sens 1 : 1-14=15 (Commerce → Beaujoire), 1-16=15 (Commerce → Babinière),
           1-19=8 (Bd de Doulon → Beaujoire)                             — retenu 1-14
```

Deux branches de longueur égale, départagées par leur identifiant. Babinière
n'est **jamais** affichée, et la carte peint la branche que la liste montre —
donc la bonne, mais pas forcément celle qu'on cherchait.

C'est la même maille que celle qui vient d'être corrigée pour la C1, vue par
l'autre bout : le client ne sait rendre qu'**un** parcours par sens là où le
réseau en publie plusieurs.

---

## 2. Ce que fait iOS, et ce qu'on en garde

Source : `../Native/Aule/Features/Lines/LineStopsModel.swift` et
`LineDetailSheet.swift`.

| Décision iOS | On la reprend | Pourquoi |
|---|---|---|
| La maille est le **profil**, pas le sens | **oui** | « La séquence d'arrêts de la ligne 1 » n'existe pas. Le sens est une propriété du profil, pas un niveau au-dessus. |
| Le libellé d'un profil est **« premier arrêt → dernier arrêt »** | **oui** | Le `headsign` ne distingue pas les branches : « Beaujoire / Babinière » nomme la paire. Un menu bâti dessus proposerait deux fois la même entrée. |
| Un **menu**, pas une barre segmentée | **oui** | Les libellés sont des noms de terminus — « Chantrerie - Grandes Écoles » —, qu'aucune barre segmentée ne tient. |
| Le choix se propage **jusqu'à la carte** | **oui** | Déjà le cas ici : `MapScreen` republie `lineStopLayer` sur `lineStopsState.selected`. |
| Le défaut est le profil **le plus desservi** | **non** — voir §6 | C'est exactement la règle qu'on vient de renverser : entre deux mêmes bouts, des arrêts en plus sont un détour. |
| La source est `api/carte-immersive/line-profiles` | **non** — voir §5 | Cette route rend 404 en production, et son repli est daté. |

---

## 3. Ce qui existe déjà ici, et qu'on ne réécrit pas

L'essentiel est en place. Le travail est un **déplacement de maille**, pas une
construction.

| Besoin | Ce qui le couvre aujourd'hui |
|---|---|
| Charger tous les parcours d'un sens | `SupabaseDriverServiceRepository.fetchDirectJourney` — les charge déjà **tous** (`served`), et en jette tous sauf un |
| Choisir le parcours de référence | même méthode : le plus court parmi ceux qui relient les mêmes bouts que le plus complet |
| Effacer ce qui vient de la ligne précédente | `LineStopsModel.open` |
| Rendre la caméra au changement de choix | `LineStopsModel.selectDirection` — à généraliser au profil |
| Peindre la desserte affichée | `MapScreen` → `LineStopLayer.setStops` |
| Correspondances par nom d'arrêt | `LineStopsUiState.connectionsAt` — indépendant du profil |
| Un sélecteur exclusif de la maison | `AuleConnectedButtonGroup` — garde le **sens** (§7) |

---

## 4. Le modèle : du sens au profil

`LineDesserte(directionId, terminus, stops)` devient un profil identifié :

```kotlin
internal data class LineProfile(
    /** L'identifiant GTFS du parcours — le `shape_id`, ou le `trip_id` à défaut. */
    val id: String,
    val directionId: Int,
    /** Le terminus annoncé. Nomme la paire sur une ligne à branches — d'où [label]. */
    val terminus: String,
    val stops: List<LineJourneyStop>,
) {
    /** Ce qui nomme un parcours sans ambiguïté : ses deux bouts. */
    val label: String get() = "${stops.first().name} → ${stops.last().name}"
}
```

`LineStopsUiState` porte alors `profiles: List<LineProfile>` et
`selectedProfileId: String?`. `hasChoice` reste le nombre de **sens** distincts
(le groupe de boutons), et un `hasBranches` s'y ajoute pour le menu.

⚠️ **`selectedProfileId` et non l'objet** — même raison que pour
`focusedStopId` : changer de ligne reconstruit la liste, et un objet retenu
désignerait un parcours qui n'est plus proposé.

---

## 5. La source : Android garde GTFS

iOS lit `api/carte-immersive/line-profiles`. **On ne le suit pas**, et le
contrat dit pourquoi (`../docs/CONTRAT-BFF.md` §5) :

- la route **rend 404 en production** depuis son écriture ;
- le repli iOS passe par `line.schedules[]` + `trip-plan`, c'est-à-dire les
  **courses en circulation à l'instant de la requête**. À une heure du matin, la
  ligne 1 y devient « Beaujoire → Commerce ». iOS porte cette provenance jusqu'à
  l'écran, qui doit l'annoncer — « D'après les courses en circulation à cette
  heure-ci ».

Android lit le GTFS par PostgREST, qui décrit la ligne **à toute heure**. C'est
la meilleure des deux sources pour cette question, et elle est déjà branchée :
`fetchDirectJourney` charge les `stop_times` de six parcours candidats en une
requête. Il n'y a rien à demander de plus — seulement à cesser d'en jeter cinq.

**Conséquence heureuse** : pas de notion de provenance à porter, pas de bandeau
à écrire, pas de cache à interdire.

---

## 6. Le défaut reste le parcours de référence

iOS présélectionne le plus desservi. On ne le suit pas : c'est la règle qui
peignait neuf arrêts hors du tracé de la C1, corrigée le 04/09/2026. Le défaut
reste **le plus court parmi ceux qui relient les mêmes bouts que le plus
complet**, et les autres restent à un geste — ce qui est exactement la promesse
du menu.

Les deux règles se complètent : celle du défaut protège l'ouverture de la fiche,
celle du menu rend le reste atteignable.

### Et d'où viennent « les bouts » — amendé le 05/09/2026

La règle enchaîne deux pas, et le second seul était sûr. Les bouts venaient du
parcours **le plus long**, ce qui suppose que le plus long relie les vrais
terminus. Une course qui pousse au-delà — un dépôt, une antenne de service —
renverse la supposition, et **rien dans sa forme ne la distingue** d'un trajet
ordinaire : elle contient l'autre, exactement comme un parcours complet contient
une course partielle. Aucun critère tiré des courses ne peut les départager.

Le repère vient donc du terminus que le référentiel **annonce** pour ce sens —
`route_long_name`, déjà lu par `fetchLines` et porté par
`ServiceLine.directions`. `fetchJourneyProfiles` le reçoit en paramètre
(`expectedTerminus`, vide par défaut pour les doublures de test) et restreint la
recherche des bouts aux parcours qui finissent là.

⚠️ **Il ne décide que là où il nomme un arrêt.** La ligne 1 annonce
« Beaujoire / Babinière », qui nomme une paire et aucun arrêt : la comparaison
échoue et la règle d'origine reprend la main. Un repère qu'on ne sait pas lire
ne doit rien trancher — c'est ce qui rend l'ajout sans régression possible.

Deux tests le figent (`une course prolongee au depot ne donne pas les bouts de
la ligne`, `un terminus annonce qui ne nomme aucun arret ne change rien`), et
ils ont été vus **échouer** sans la correction.

### Trois troncatures qui se disent maintenant

Aucune n'est une affirmation sur le réseau, et toutes se lisaient comme telle :
l'échantillon de courses plafonné, les parcours au-delà du plafond de six, et
les candidats dont la desserte n'a pas pu être lue. `fetchDirectProfiles` les
journalise en `warn`. L'ordre des courses est aussi **demandé** désormais
(`order=trip_id`) : sans lui, PostgREST n'en garantit aucun, et tout le soin pris
à départager les égalités ne servait à rien si l'échantillon d'entrée variait.

---

## 7. L'écran : un menu **sous** le sélecteur de sens

iOS n'a qu'un contrôle, qui mélange sens et branches. Ici le sens a déjà le
sien, et il est bon : deux choix, libellés courts (« Vers Haluchère »), un
groupe connecté qui se vise sans regarder. Le fondre dans un menu ferait
régresser le cas courant — 130 lignes sur 138 n'ont pas de branche — pour servir
l'exception.

La disposition :

```
←  [C1]  [Bus]   Ligne C1
[ Vers Haluchère ][ Vers Gare de Chantenay ]     ← sens, inchangé
⤳ Beaujoire → Commerce            ⌄              ← branches, seulement si > 1
Gare de Chantenay → Haluchère          30 arrêts
```

- **Ne paraît que s'il y a un choix** : un menu à une entrée ne sélectionne
  rien. Même règle que `hasChoice` pour le groupe de boutons.
- **Et seulement ce qui finit ailleurs.** Un seul critère, figé par trois tests
  (`le menu des branches ecarte variantes et courses partielles`, `la C3 ne
  propose aucune branche`, `une vraie branche reste proposee`) :

  > **Une branche est un parcours dont au moins un des deux bouts n'est pas
  > desservi par la référence.**

  Beaujoire et Babinière ne finissent pas au même endroit, et c'est exactement
  ce qui fait d'elles deux branches. Tout le reste est un parcours **de** la
  référence, qu'elle représente déjà :
  - la **variante** — mêmes bouts, un crochet en plus. Les trois parcours de la
    C1 portent tous « Gare de Chantenay → Haluchère - Batignolles » : le menu
    les proposait trois fois à l'identique.
  - la **course partielle** — elle démarre ou s'arrête en chemin, donc entre
    deux arrêts que la référence dessert.

  ⚠️ **Corrigé le 05/09/2026.** Le critère d'origine — « au moins un arrêt que
  la référence n'ait déjà » — laissait passer les deux : il suffit d'un crochet
  d'un arrêt, ou d'une sortie de dépôt, pour qu'une troncature en profite. La C3
  proposait ainsi deux fausses branches — « Hôtel Dieu → Armor » (24 arrêts) et
  « Prairie de Mauves → Armor » (35) —, et en choisir une peignait sur la carte
  une desserte qui n'est pas la ligne. Les noms se comparent désormais
  **normalisés** : le référentiel écrit « Hôtel Dieu » et « HOTEL-DIEU » pour le
  même lieu, et un bout non reconnu ferait réapparaître le faux choix.
- **Libellé « premier → dernier »**, tronqué à une ligne. C'est aussi ce que la
  liste écrit sous « Départ » et « Terminus ».
- **Plancher tactile** `AuleTouch.minimum`, glyphe de tête du design system —
  aucun caractère en guise d'icône, la garde le refuserait.
- **`contentDescription` = « Desserte affichée »**, comme iOS.
- Le changement **rend la caméra** et efface l'arrêt visé, exactement comme
  `selectDirection` le fait déjà : l'arrêt appartenait à l'autre parcours.

Composant : `DropdownMenu` de Material 3, pas de nouveau composant de design
system tant qu'il n'a qu'un appelant.

---

## 8. Le cas de la ligne 1, et ce qu'il coûte

`mergeRelayJourneyIfApplicable` assemble la ligne 1 et le bus relais 1B pendant
les travaux — 15 + 18 − 1 = 32 arrêts, vérifié à l'écran. Avec des profils, le
montage doit se faire **profil à profil** : deux branches côté 1, trois
parcours côté 1B, et toutes les paires n'ont pas de sens.

La règle la plus simple qui reste juste : **assembler le profil retenu de 1 avec
le profil retenu de 1B**, comme aujourd'hui, et n'exposer les branches que du
côté de la ligne 1. Le relais n'a pas de branche — ses trois parcours sont des
courses partielles, déjà écartées par le filtre sur les extrémités.

⚠️ **C'est le lot à faire en dernier et à vérifier à l'écran**, ligne 1 ouverte,
dans les deux sens : c'est le seul endroit du client où deux routes GTFS
deviennent une desserte.

---

## 9. Ce que ça touche, lot par lot

| Lot | Fichiers | Nature |
|---|---|---|
| 1. Contrat | `core/model/.../Repositories.kt`, `LineJourney.kt` | Ajouter `fetchJourneyProfiles(session, lineId, directionId): List<LineJourney>`. **`fetchJourney` reste abstraite** et c'est `fetchJourneyProfiles` qui porte le défaut (`listOf(fetchJourney(…))`) : mis dans l'autre sens, les cinq doublures de test cessaient de compiler. `LineJourney` gagne `profileId` et `label`. |
| 2. Source | `data/.../SupabaseDriverServiceRepository.kt` | `fetchDirectJourney` rend la liste ordonnée : le parcours de référence d'abord, les autres ensuite. Le tri est déjà écrit, il suffit de ne plus couper. |
| 3. Modèle d'écran | `feature/map/.../LineStopsModel.kt` | `dessertes` → `profiles`, `selectedDirection` → `selectedProfileId`, `selectDirection` → `selectProfile`. `connectionsAt` ne bouge pas. |
| 4. Écran | `feature/map/.../LineStopsSheet.kt` | Le menu du §7, sous le groupe de sens. |
| 5. Carte | `feature/map/.../MapScreen.kt` | Rien, ou presque : le `LaunchedEffect` observe déjà `lineStopsState.selected`. |
| 6. Ligne 1 | `data/.../SupabaseDriverServiceRepository.kt` | §8. |
| 7. Chaînes | `feature/map/src/main/res/values{,-en}/` | Libellé du menu, description accessible. ADR-011 : aucune phrase dans un modèle. |

Cinq faux `DriverServiceRepository` dans les tests implémentent `fetchJourney` ;
garder la méthode leur évite d'être touchés.

---

## 10. Ce qu'on ne fait pas

- **Pas de sélection par branche du tracé sur la carte.** Le tracé vient des
  tuiles et agrège les tronçons OSM de la ligne ; il ne sait pas distinguer
  Beaujoire de Babinière. Les deux branches resteront peintes, seuls les arrêts
  changeront. Le dire ici évite qu'on le découvre à l'écran.
- **Pas de mémoire du choix.** Rouvrir une ligne repart du parcours de
  référence : un choix retenu d'une session à l'autre ferait ouvrir la fiche sur
  une branche qu'on ne se rappelle pas avoir demandée.
- **Pas de composant de design system.** Un menu à un seul appelant n'est pas
  une échelle.

---

## 11. Comment on vérifie

Sur le Samsung S21, skill `run-app`, en journalisant comme aujourd'hui le
parcours retenu :

| Ligne | Attendu |
|---|---|
| C1 | Pas de menu (trois parcours, un seul jeu de bouts) — 30 arrêts, inchangé ✅ |
| C6 | Pas de menu (un seul parcours) — 44 arrêts, inchangé ✅ |
| **C3** | **Pas de menu** — deux courses partielles écartées, 34 arrêts ✅ (repassé le 05/09/2026) |
| 2 | **Menu à deux entrées** — « Neustrie → Orvault Grand Val » (33) et « Gare de Pont Rousseau → Orvault Grand Val » (25). Quatre courses partielles écartées ✅ |
| 1 | **Menu à deux entrées** — « Beaujoire → François Mitterrand » (32, montage 1 + 1B) et « Babinière → Commerce » (15). Basculer change la liste **et** les pastilles ✅ |

Le cadrage annonçait « pas de menu » sur la 2 : c'était une erreur de prévision,
et la vérification l'a corrigée. Ses deux dessertes ne desservent pas les mêmes
arrêts et se nomment sans ambiguïté — les cacher aurait rendu Pont Rousseau
introuvable.

La C3 est l'inverse : le cadrage ne l'avait pas regardée, et elle proposait deux
branches qui n'existent pas. C'est elle qui a fixé le critère du §7, et c'est
elle aussi qui a montré que le **dessin** des arrêts ne tenait pas à l'échelle
d'une ligne entière — trente-quatre épingles de catalogue recouvraient le tracé
qu'elles précisent. Le KDoc de `LineStopLayer` porte cette correction et sa
mesure.

Et la garde qui compte : **les arrêts peints sont exactement ceux de la liste**,
quel que soit le profil choisi. Elle ne dépend plus d'une discipline : la liste
et la carte lisent la même construction, `LineStopsUiState.markers`.
