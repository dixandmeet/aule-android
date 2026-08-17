# Prompt pour Claude Design — refonte page par page d'Aule Pro (Android, Material 3)

> À coller tel quel dans un projet **Claude Design**. Le bloc 1 est le brief
> permanent (à envoyer en premier, une seule fois). Le bloc 2 est le gabarit à
> réutiliser page après page. Les valeurs chiffrées viennent de
> `:core:designsystem` — elles ne sont pas indicatives, elles sont le contrat.

---

## BLOC 1 — Le brief permanent

Tu es designer produit sur **Aule Pro**, l'application Android des conducteurs et
agents d'un réseau de transport public (Nantes / Naolib). L'app existe déjà, elle
tourne en production sur un Samsung Galaxy S21. **Je ne te demande pas d'inventer
une application : je te demande de redessiner celle-ci, écran par écran, sans
toucher à son architecture d'information.**

### Qui l'utilise, et comment

Un conducteur de bus ou de tram, debout ou assis au poste de conduite, souvent
**à une main**, gants possibles, plein soleil ou nuit noire, véhicule qui vibre,
parfois 20 secondes entre deux manœuvres. Chaque écran doit se lire d'un coup
d'œil et se manipuler sans viser. Ce n'est pas une app grand public : la densité
d'information prime sur la respiration, mais jamais sur la lisibilité.

### Le cadre technique, non négociable

- **Jetpack Compose + Material 3** (`androidx.compose.material3`). Les composants
  officiels M3 sont le vocabulaire. Pas de Material 2, pas de composant maison
  qui redit un composant M3.
- **Couleurs dynamiques désactivées** : l'identité ne dépend pas du fond d'écran.
- Appareil de référence : **360 × 800 dp**, densité 3x, 120 Hz. Bord-à-bord
  (`safeDrawing`), barre d'état et barre de navigation transparentes.
- `minSdk 26`. Français source, anglais complet — **prévois +35 % de longueur de
  chaîne** sur chaque libellé.
- Trois ambiances : **Clair / Sombre / Auto** (défaut : clair).
- La carte est une `MapView` MapLibre plein écran : tout ce qui la surplombe
  flotte au-dessus d'un fond imprévisible.

---

### Le système de design (à utiliser tel quel, aucune couleur inventée)

#### Rôles Material 3 — ambiance claire

| Rôle | Hex | Rôle | Hex |
|---|---|---|---|
| `primary` | `#0D595E` | `onPrimary` | `#FFFFFF` |
| `primaryContainer` | `#B8F0F6` | `onPrimaryContainer` | `#00201F` |
| `inversePrimary` | `#96E0E7` | | |
| `secondary` | `#006D3B` | `onSecondary` | `#FFFFFF` |
| `secondaryContainer` | `#D1F4E6` | `onSecondaryContainer` | `#005132` |
| `tertiary` | `#875300` | `onTertiary` | `#FFFFFF` |
| `tertiaryContainer` | `#FFE3C2` | `onTertiaryContainer` | `#593600` |
| `background` / `surface` | `#FFFFFF` | `onBackground` / `onSurface` | `#171717` |
| `surfaceVariant` | `#ECF1EF` | `onSurfaceVariant` | `#4A4A4A` |
| `surfaceTint` | `#0D595E` | `surfaceBright` | `#FFFFFF` |
| `surfaceDim` | `#DCE4E1` | `surfaceContainerLowest` | `#FFFFFF` |
| `surfaceContainerLow` | `#F7FAF9` | `surfaceContainer` | `#F2F6F4` |
| `surfaceContainerHigh` | `#ECF1EF` | `surfaceContainerHighest` | `#E6EBE9` |
| `inverseSurface` | `#2D3532` | `inverseOnSurface` | `#F2F6F4` |
| `error` | `#B03038` | `onError` | `#FFFFFF` |
| `errorContainer` | `#FFDAD6` | `onErrorContainer` | `#410008` |
| `outline` | `#75807D` | `outlineVariant` | `#171717` à 8 % |
| `scrim` | `#000000` | | |

#### Rôles Material 3 — ambiance sombre

| Rôle | Hex | Rôle | Hex |
|---|---|---|---|
| `primary` | `#8AC79B` | `onPrimary` | `#0D1512` |
| `primaryContainer` | `#1A5C47` | `onPrimaryContainer` | `#F1F6F3` |
| `inversePrimary` | `#0D595E` | | |
| `secondary` | `#41C895` | `onSecondary` | `#003822` |
| `secondaryContainer` | `#005234` | `onSecondaryContainer` | `#67E5B0` |
| `tertiary` | `#F0B45C` | `onTertiary` | `#432C00` |
| `tertiaryContainer` | `#5E4000` | `onTertiaryContainer` | `#FFDCB3` |
| `background` / `surface` | `#0D1512` | `onBackground` / `onSurface` | `#F3F5F7` |
| `surfaceVariant` | `#3F4946` | `onSurfaceVariant` | `#BFC7C3` |
| `surfaceTint` | `#8AC79B` | `surfaceBright` | `#333B38` |
| `surfaceDim` | `#0D1512` | `surfaceContainerLowest` | `#070D0B` |
| `surfaceContainerLow` | `#141C19` | `surfaceContainer` | `#18201D` |
| `surfaceContainerHigh` | `#222A27` | `surfaceContainerHighest` | `#2D3532` |
| `inverseSurface` | `#E6EBE9` | `inverseOnSurface` | `#1A211F` |
| `error` | `#E86060` | `onError` | `#410008` |
| `errorContainer` | `#8C1D24` | `onErrorContainer` | `#FFDAD6` |
| `outline` | `#899390` | `outlineVariant` | `#FFFFFF` à 20 % |
| `scrim` | `#000000` | | |

**Deux pièges à ne jamais confondre.** De nuit, `#1A5C47` est un **aplat**
(c'est `primaryContainer`, celui du FAB) et `#8AC79B` est une **encre** (c'est
`primary`, celle d'un titre ou d'une icône sur la surface). Inverser les deux
donne un texte illisible qu'aucune maquette statique ne trahit. De jour, l'aplat
d'erreur `#D64545` ne tient pas 4,5:1 sous du blanc : le texte d'erreur descend
à `#B03038`.

#### Rôles métier — ce ne sont PAS des rôles Material

« Temps réel » et « retard » sont des faits transport, pas de la hiérarchie
visuelle. Ils ont leur propre couple aplat / encre et ne doivent jamais être
remplacés par `secondary` ou `tertiary` dans ton raisonnement, même si les hex
coïncident.

| Rôle | Clair (couleur / encre / conteneur / encre conteneur) | Sombre |
|---|---|---|
| Temps réel | `#19B37B` / `#FFFFFF` / `#D1F4E6` / `#005132` | `#41C895` / `#003822` / `#005234` / `#67E5B0` |
| Retard | `#E8A13C` / `#FFFFFF` / `#FFE3C2` / `#593600` | `#F0B45C` / `#432C00` / `#5E4000` / `#FFDCB3` |
| Alerte HUD | `#D64545` / `#FFFFFF` | `#E86060` / `#601410` |
| Marque Aule | `#0D595E` — **identique jour et nuit**, c'est le vert de la carte |

Couleurs de mode sur la carte : tram `#0D595E` (nuit `#8AC79B`), bateau `#2E7D9A`
(nuit `#5FA8C4`), bus `#55665F` (nuit `#979FAE`).

Badge de ligne : la couleur vient du GTFS. Au-dessus de 0,65 de luminance perçue,
l'encre bascule en `#171717`, sinon en blanc — le réseau de nuit a une couleur
GTFS **blanche**, et « N1 » s'écrivait en blanc sur blanc.

#### Le « verre »

`#FFFFFF` à 90 % (jour) / `#0D1512` à 95 % (nuit) est le seul aplat translucide
autorisé, et **uniquement** pour un panneau qui flotte au-dessus du fond de
carte (la barre de recherche, les pastilles). Un volet qui prend une part
d'écran, un menu, une barre de navigation sont **opaques** : sinon un bâtiment
qui défile derrière salit le texte.

#### Typographie — échelle Material 3 en Roboto

Les quinze slots M3 sont renseignés (rien ne doit retomber sur le sans-serif
système). Cinq portent un nom Aule :

| Nom Aule | Slot M3 | Taille / interligne | Graisse | Chiffres |
|---|---|---|---|---|
| `KICKER` | `labelSmall` | 11 / 16, tracking 0,5 | Medium | — |
| `BODY` | `bodyMedium` | 14 / 20, tracking 0,2 | Regular | — |
| `TITLE` | `titleMedium` | 16 / 24, tracking 0,2 | Medium | — |
| `DATA` | `titleLarge` | 22 / 28 | Regular | **tabulaires** |
| `HERO` | `headlineMedium` | 28 / 36 | Regular | **tabulaires** |

Les dix autres : `displayLarge` 57/64 · `displayMedium` 45/52 ·
`displaySmall` 36/44 · `headlineLarge` 32/40 · `headlineSmall` 24/32 ·
`titleSmall` 14/20 Medium · `bodyLarge` 16/24 · `bodySmall` 12/16 ·
`labelLarge` 14/20 Medium · `labelMedium` 12/16 Medium.

Tout compteur, minutage, distance ou compte à rebours utilise `DATA` ou `HERO`
en **chiffres tabulaires** : une ligne qui danse à chaque seconde est un défaut,
pas un détail.

#### Mesures

- **Espacements** (base 4) : 4 · 8 · 12 · 16 · 24 · 32. Rien entre.
- **Rayons**, qui alimentent les cinq formes M3 dans l'ordre :
  `extraSmall` 8 · `small` 12 · `medium` 18 · `large` 24 · `extraLarge` 28 ·
  pilule 999. Aucune forme n'est écrite ailleurs que dans le thème.
- **Cible tactile plancher : 48 dp**, partout, sans exception.
- **Hauteurs de contrôle** : bouton principal et barre de recherche 52 ·
  champ à libellé flottant 60 · grille d'icône 24 · portrait 52 · pastille
  collée au portrait 22 · case à cocher 22.
- **Traits** : filet 1 · contour appuyé (champ actif ou en erreur) 1,4 ·
  trait de la famille d'icônes 1,75 sur la grille de 24.
- **Élévations**, cinq crans, jour / nuit — la nuit monte parce qu'une ombre
  noire disparaît sur fond sombre : posé 0/0 · appuyé sur un bord 8/10 ·
  flottant au-dessus de la carte 10/14 · volet 16/20 · plein écran 22/28.
- **Opacités nommées** : désactivé 0,45 · aplat teinté 0,12 · lavis de marque
  0,14 · contour d'aplat 0,30 · voile sur la carte 0,72 · poignée et
  attribution 0,35.
- **Durées** : déplacement ordinaire 300 ms · réponse au doigt 220 ms · entrée
  en mode caméra 550 ms · pulsation temps réel 1800 ms · tour d'attente 900 ms.

#### Icônes

Material Symbols **outlined** au repos, **filled** pour l'état sélectionné — et
c'est toujours la même icône, jamais une deuxième. Sept icônes métier maison
complètent le jeu : bus, tram, ticket, arrêt, cap, itinéraire, destination —
arrêt, cap et destination existant aussi en version pleine. Grille 24 dp, trait 1,75 dp. **Aucun caractère typographique en
guise d'icône** (pas de « › », pas d'émoji, pas de puce).

---

### Les cinq règles qui font échouer un build

Le dépôt a des gardes automatisées. Une maquette qui les viole est
inapplicable — c'est du travail perdu :

1. **Aucune mesure chiffrée à la main.** Tout espacement, rayon, hauteur vient
   des échelles ci-dessus.
2. **Aucune ombre posée hors de l'échelle d'élévation.**
3. **Aucune opacité inventée** : les six valeurs nommées, pas une septième.
4. **Aucune couleur en dur.** Si une teinte te manque, dis-le — c'est un trou
   dans la palette, pas une exception à s'accorder.
5. **Aucune forme écrite hors du thème**, aucun composant maison qui redit un
   composant M3, aucun texte hors du système typographique.

Les seuls composants non-Material tolérés sont ceux qui n'ont pas d'équivalent :
bandeau d'alerte de service, badge de ligne, point temps réel pulsant, marque
Aule, portrait d'identité. Ils consomment le thème comme les autres.

---

### Ce que tu ne dois PAS changer

- L'**architecture d'information** : aucune page ajoutée, supprimée, fusionnée
  ou déplacée dans un autre parcours.
- L'**ordre des étapes** des trois assistants.
- Les **libellés** : ce sont des formulations métier validées, souvent reprises
  mot pour mot de l'app Flutter de référence. Tu peux signaler qu'un libellé te
  gêne ; tu ne le réécris pas.
- Les **règles de sécurité d'usage** déjà tenues : la déconnexion est seule, en
  bas de page, avec confirmation — un doigt qui dérape au volant ne doit pas
  renvoyer à la saisie du mot de passe.

### Ce que je te demande, précisément

Pour **chaque page**, reprendre la structure actuelle décrite ci-dessous et en
livrer la version M3 aboutie : hiérarchie visuelle, choix du composant M3 juste
pour chaque zone, densité, rythme vertical, états, accessibilité. La question à
laquelle tu réponds n'est pas « à quoi pourrait ressembler cette app », c'est
**« comment cette page-ci, avec ce contenu-ci, devient du Material 3 exemplaire
et lisible au volant »**.

---

### L'inventaire des pages

#### 1. Démarrage (`BootScreen`)
Écran de secours quand la configuration est invalide. Fond neutre, message,
détail technique. Rare, mais c'est la première chose que voit un conducteur dont
l'installation est cassée.

#### 2. Vérification des habilitations (`AccessCheckScreen`)
Plein écran, un `CircularProgressIndicator`, une phrase. Dure 1 à 3 secondes,
parfois 10 en 3G dans un tunnel. **Ne doit jamais donner l'impression d'un gel.**

#### 3. Connexion (`AuthScreen`)
Carte centrée sur fond de marque. Marque Aule, deux `OutlinedTextField` (courriel,
mot de passe avec bascule œil), `Button` de connexion, `TextButton` « créer un
compte », `CircularProgressIndicator` pendant la requête. États : erreur
d'identifiants, compte voyageur refusé, réseau muet.

#### 4. Inscription professionnelle (`RegistrationScreen`) — assistant 7 étapes
`LinearProgressIndicator` en tête, pied de page persistant (retour / continuer).
Étapes, dans l'ordre : **accueil** · **métiers** (cumulables, cartes de choix) ·
**réseau** (Naolib) · **identité** · **mode de conduite** · **compte**
(mot de passe + jauge de robustesse + case conditions) · **confirmation**
(courriel envoyé, renvoi possible, limite de débit). Le plus long parcours de
l'app, et le seul fait au calme — c'est le seul écran qui peut respirer.

#### 5. Carte (`MapScreen`) — l'écran principal
Une `MapView` plein écran sous un `BottomSheetScaffold` (volet en pic ~30 %,
déployé ; la carte reste vivante dessous). Par-dessus, de haut en bas :
- bandeau de service (`AuleBanner`) quand il y en a un ;
- **barre de recherche** en verre, avec bouton menu à gauche ;
- pastilles : diagnostic, état de la flotte (avec point temps réel pulsant),
  bandeau d'incident (localisation refusée, arrêts non chargés, position
  imprécise) ;
- en bas, **une seule barre d'actions** (`NavigationBar`) : prendre / voir le
  service, relève, signaler — une seule barre stabilise les cibles et évite de
  faire parcourir les deux bords de l'écran au pouce ;
- FAB de recadrage, bouton d'itinéraire, attribution MapLibre.
En guidage : la recherche cède la place au **bandeau de manœuvre**, et une
**barre de résumé de trajet** s'appuie sur le bord bas.
Contrainte propre : tout ce qui flotte doit rester lisible sur un fond de carte
clair, sombre, ou saturé de bâtiments.

#### 6. Les volets de la carte
Tous dans le même `BottomSheetScaffold`, donc même grammaire :
- **Arrêt** : nom, modes, passages temps réel (ligne, destination, attente),
  sections « prochains passages » et « lignes desservies », séparateurs.
  Trois états d'échec distincts qui ne se confondent pas : rien ne circule ·
  temps réel muet · horaires indisponibles.
- **Véhicule** : ligne, destination, état, actions (suivre, itinéraire).
- **Lieu** : nom, adresse, action.
- **Autour de vous** : la porte d'accès TalkBack à la carte — 12 entrées
  maximum, arrêts et véhicules, un lieu par entrée.
- **Itinéraire** : deux extrémités, choix de profil (`SegmentedButton`),
  candidats avec fiabilité, départ.
- **Détail du trajet** : la liste des tronçons.

#### 7. Signalement (`ReportSheet`) — `ModalBottomSheet`
Un flux qui interrompt : `TopAppBar`, `FilterChip` de type d'événement,
`FilterChip` d'urgence, champ libre, envoi. États : envoi en cours, envoyé,
échec (non connecté, réseau).

#### 8. Prise de service (`PriseServiceScreen`) — assistant 6 étapes
Couvre la carte sans la démonter. `TopAppBar` avec fermeture, cartes de choix,
recherche de ligne. Étapes : **ligne** · **sens** · **heure** (facultative) ·
**n° de train** (facultatif) · **véhicule** (facultatif) · **position et suivi**
(permission GPS, service au premier plan). État d'échec : données Naolib
indisponibles.

#### 9. Relève d'un collègue (`HandoverScreen`) — le plus dense de l'app
Un assistant à onze étapes plus trois panneaux de suivi :
- **reprise** d'une relève déjà engagée · **ligne** (sections « en service
  maintenant », « relevées récemment », puis recherche) · **véhicule** ·
  **candidats** · **sens** · **repli arrêt** · **repli horaire** · **arrêt** ·
  **alertes** · **confirmation** · **terminé**.
- Le **panneau de suivi** est l'écran vital : distance au collègue, arrêts
  restants, arrivée estimée, départ conseillé, retard — tout en chiffres
  tabulaires, lisibles à bout de bras, mis à jour en continu.
- Le panneau **alertes** règle des seuils (arrêts, minutes, arrivée) avec des
  bascules et des incrémenteurs, à 48 dp minimum.
- L'écran **terminé** annonce « Service repris » et double la confirmation d'une
  notification locale.
C'est ici que la lisibilité en conduite se joue : traite ce parcours comme le
cas limite du système, pas comme un écran de plus.

#### 10. Fin de service (`EndServiceSheet`) — `ModalBottomSheet`
Récapitulatif, bouton de fin, état d'envoi. Action irréversible : elle doit se
sentir comme telle sans être anxiogène.

#### 11. Menu du compte (`AccountMenuScreen`)
S'ouvre par-dessus la carte. `TopAppBar`, **carte d'identité** (portrait ou
initiales, nom, matricule, dépôt, chip d'habilitation), entrées de liste
(`ListItem`), et **la déconnexion seule, en bas, avec `AlertDialog` de
confirmation**.

#### 12. Profil (`ProfileScreen`) — deux onglets
Bascule d'onglets en tête. **Profil** : portrait avec menu photo (appareil,
galerie, suppression, permission), éditeur d'identité, éditeur d'affectation,
section compte, **barre Annuler / Enregistrer qui n'apparaît que si la saisie
s'écarte du serveur**, suppression définitive du compte (dialogue).
**Préférences** : apparence Clair / Sombre / Auto en `SegmentedButton`, traces
GPS de diagnostic (liste, partage, suppression).

---

### Le livrable attendu, par page

Pour chaque page traitée, produis :

1. **Une maquette HTML autonome**, viewport **360 × 800**, rendue en **clair et
   en sombre** (les deux visibles côte à côte). Toutes les couleurs, tailles et
   rayons passent par des variables CSS nommées d'après les jetons ci-dessus —
   aucune valeur littérale dans le corps de la feuille de style.
2. **Les variantes d'état** de cette page, dans la même maquette : chargement,
   vide, erreur réseau, saisie invalide, action en cours, hors ligne. Une page
   sans ses états n'est pas conçue.
3. **La table de correspondance** : pour chaque zone, le composant M3 exact
   (`ListItem`, `FilledTonalButton`, `ModalBottomSheet`…), le rôle de couleur, le
   slot typographique, l'espacement et le cran d'élévation employés.
4. **Le relevé des écarts** : ce qui change par rapport à la structure actuelle,
   et pourquoi. Trois lignes suffisent, mais elles sont obligatoires — un
   changement non justifié sera refusé.
5. **La note d'accessibilité** : cibles à 48 dp, ordre de parcours TalkBack,
   contrastes mesurés (4,5:1 pour le texte courant, 3:1 pour le texte large et
   les contours de contrôle), comportement à 200 % de taille de police.

### Les critères d'acceptation

- Chaque valeur employée existe dans les échelles ci-dessus. Zéro exception.
- Le texte reste lisible en plein soleil comme de nuit, dans les deux ambiances.
- Aucune cible sous 48 dp, aucune action destructive à moins de 48 dp d'une
  action courante.
- La traduction anglaise, 35 % plus longue, ne casse aucune mise en page.
- À 200 % de taille de police système, rien n'est tronqué ni superposé.
- Un conducteur trouve l'information vitale de la page **en moins de deux
  secondes** — dis-moi, pour chaque page, quelle est cette information.

### Ordre de passage

Ne traite **qu'une page à la fois**, et attends ma validation avant la suivante.
L'ordre : **5 (carte) → 9 (relève) → 8 (prise de service) → 6 (volets) → 12
(profil) → 11 (menu) → 3 (connexion) → 4 (inscription) → 7, 10, 2, 1**. La carte
et la relève posent la grammaire ; tout le reste en découle.

Commence par me confirmer ta lecture du système en une dizaine de lignes — ce
que tu as compris de la distinction aplat / encre, du verre, et des rôles
métier — puis attaque la page 5.

---

## BLOC 2 — Le gabarit par page (à réutiliser à chaque itération)

> Page **N — <nom>**.
>
> Rappel de l'existant : <coller le paragraphe de l'inventaire>.
>
> Ce qui me gêne aujourd'hui : <1 à 3 points concrets — chevauchement, densité,
> hiérarchie plate, cible trop petite, état manquant…>.
>
> Ce qui doit rester intact : <structure, libellés, ordre des étapes,
> emplacement d'une action>.
>
> Livre les cinq éléments attendus (maquette clair + sombre, variantes d'état,
> table de correspondance M3, relevé des écarts, note d'accessibilité), en
> respectant les échelles du brief. Si une valeur te manque, ne l'invente pas :
> signale le trou.
