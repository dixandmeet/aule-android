# Aule Android — l'application native

La version officielle d'Aule Pro sur Android : Kotlin, Jetpack Compose avec
Material 3 thémé Aule, MapLibre Native OpenGL, cliente du backend Aule existant.

**Ce n'est plus un prototype.** Aule part sur deux applications natives : celle-ci
et l'iOS (`../Native`). L'app Flutter (`../SAE`) reste la source de référence pour
les règles métier et les modèles de données, mais elle ne contraint plus les choix
d'UX ni d'architecture : quand le natif sait faire mieux, c'est le natif qui gagne.

Le contrat du BFF et ses pièges — que les deux clients natifs réimplémentent —
sont consignés une seule fois dans [`docs/CONTRAT-BFF.md`](../docs/CONTRAT-BFF.md).
À lire avant de toucher au réseau.

## Lancer

```bash
cd Kotlin
./gradlew installDevelopmentDebug
adb -s R3CRA0WV55H shell am start -n io.aule.android.development/io.aule.android.MainActivity
```

Appareil de référence : **Samsung S21** (`SM-G991B`, Android 15, 120 Hz). Pas
d'émulateur dans le flux de travail — la fluidité se juge sur cet écran.

| | |
|---|---|
| Cible | `minSdk 26`, `targetSdk 36` |
| Carte | MapLibre Native Android 13.5.0, artefact **OpenGL** |
| UI | Compose Material 3 + Animation, jetons Aule |

## Les trois environnements

Un environnement est un **flavor Gradle**, pas un réglage d'exécution.

| Flavor | applicationId | Nom | Source par défaut |
|---|---|---|---|
| `development` | `io.aule.android.development` | Aule Pro (dev) | BFF production |
| `staging` | `io.aule.android.staging` | Aule Pro (recette) | BFF production |
| `production` | `io.aule.android` | Aule Pro | BFF production |

Les trois identifiants sont distincts pour que les trois applications cohabitent
sur un même appareil — et à côté de `io.aule.pro` (Flutter). Les URL et clés
propres à la machine vivent dans `local.properties`, non versionné.

**Le mock est impossible en production**, et ce n'est pas une convention : voir
[ADR-005](Docs/adr/ADR-005-mocks.md).

**Le client ne parle pas à Supabase** pour la carte. C'est la décision d'Aule,
déjà tenue côté Flutter et iOS : on consomme les routes du BFF
(`https://www.aule.fr`). Le SDK reviendra le jour des comptes.

## Décisions d'architecture

Les décisions structurantes sont dans [`Docs/adr/`](Docs/adr/README.md). À lire
en premier si vous touchez à la carte :
[ADR-006 — l'interpolation hors état Compose](Docs/adr/ADR-006-interpolation.md),
la seule règle du projet qui soit invisible dans le code et qu'un changement
anodin suffise à détruire.

## Architecture

```
app/                 activité unique, AuleGraph, flavors
core/
  common/            config, journal, dispatchers
  geo/               Coordinate, GeoMath, projection sur polyligne
  model/             TransportVehicle, TransitStop, Auth, contrats de repository
  network/           OkHttp, ApiException, endpoints
  designsystem/      jetons Aule + thème et composants Material 3
  location/          Fused, HeadingStabilizer, MotionAnchor, FGS, AlertTone
  map/               MapController, couches, caméra, icônes
data/                implémentations BFF + GoTrue — seul module qui voie OkHttp
feature/
  map/               écran carte, HUD, volets
  auth/              connexion, menu du compte, déconnexion
```

Séparation tenue : **aucune vue ne connaît autre chose qu'une interface de
repository**, et `:feature:map` ne dépend pas de `:data`. Un `@Composable` ne
peut donc pas atteindre le réseau — erreur de compilation, pas règle de revue.

## Ce qui est mesuré

- **Inclinaison** : MapLibre Android plafonne à **60°** dans son cœur. Demandé
  67° (valeur iOS), obtenu ~60°, journalisé au montage. Le sentiment de vitesse
  en navigation repose sur le zoom (ADR-009).
- **Interpolation** : le serveur parle toutes les 15 s ; les véhicules glissent
  à 120 Hz via `Choreographer`, écriture directe dans la source MapLibre, sans
  jamais recomposer Compose.
- **Hit-test** : 22 **dp**, convertis en pixels. Confondre les deux donne une
  zone quatre fois trop petite sur un écran dense.

## Accessibilité

MapLibre rend un tampon opaque : TalkBack n'y trouve aucun marqueur. Le chemin
d'accès est une **action personnalisée** posée sur la carte — « Autour de vous »
— qui ouvre une liste d'arrêts et de véhicules, plafonnée à 12, un lieu par
entrée. Le volet se ferme au geste de retour prédictif.

Les formulations du domaine vivent dans `res/values/` (français source) et
`res/values-en/` (anglais, catalogue complet). Un modèle ne porte aucune phrase
(ADR-011).

## La marque

Un seul dessin pour les trois plateformes, et c'est celui du web :
`../dashboard/public/aule-logo.png`, fond translucide retiré — le fichier que
l'icône iOS pose sur son aplat (`../Native/Aule/Resources/AppIcon.icon`). Il y
a eu ici un chevron de trois traits dans le design system et un « A » vectoriel
dans l'icône de lancement : trois dessins pour un produit, et cela se voyait à
l'écran d'accueil, un logo par application.

Il vit donc deux fois dans le dépôt, aux cinq densités, et jamais autrement :

| Où | Quoi |
|---|---|
| `core/designsystem/src/main/res/drawable-*/aule_logo.png` | la marque à l'écran, 76 dp — la plus grande tuile d'`AuleBrandMark` |
| `app/src/main/res/mipmap-*/ic_launcher_foreground.png` | l'icône, canevas de 108 dp, master posé sur les **72 dp** que le masque du lanceur laisse voir |

Le master porte sa propre réserve — le dessin n'occupe que 63 % du carré —
donc l'étaler sur les 108 dp du canevas le ferait déborder du masque. Le fond
de l'icône est `aule_brand_teal`, soit le `fill` de l'icône iOS au bit près.

**L'aplat n'est posé que de jour.** Le dessin est clair sur transparent : de
nuit l'écran lui donne déjà son fond, et une pastille teal sur du presque noir
n'est plus une pastille, c'est un carré. C'est la règle du web au mot près
(`hud/map-header.tsx`).

Ne pas le retracer en vecteur : le A est un ruban à dégradés, et un tracé en
donne un autre dessin — c'est exactement ce qui vient d'être défait.

## Pièges portés depuis iOS (et deux propres à Android)

1. **Un rechargement de style vide sources, couches et images, en silence.**
   Le registre remonte tout après `setStyle`, puis repeint l'ambiance — dans
   cet ordre.
2. **Jamais `iconIgnorePlacement` sur les arrêts** : ils sortent de l'index de
   collision, donc des requêtes de features — visibles et intouchables.
3. **`iconAllowOverlap` oui, `ignorePlacement` non.** Sans le premier, un arrêt
   disparaît dès qu'un nom de rue passe dessous. Relevé sur le S21 : « Hôtel de
   Ville » s'effaçait derrière son propre nom de rue.
4. **Poser la caméra après création de la vue**, jamais via une pose initiale :
   à la construction la vue n'a pas de taille, le cadrage est faux.
5. **404 ≠ 502** sur les passages : « rien ne circule » et « le fournisseur est
   muet » mènent au même écran vide mais n'appellent pas la même réaction.
6. **`android-sdk` n'est pas OpenGL.** L'AAR 13.5.0 embarque Vulkan. On prend
   `android-sdk-opengl` (ADR-002).
7. **Un teal de marque en transparence n'est plus une couleur, c'est une
   ombre.** Le teal Aule est un cran 30, très sombre : posé à 0,44 d'opacité
   sur la chaussée beige de la carte claire, le cône de direction du puck
   rendait un gris que rien ne distinguait de l'ombre d'un immeuble. La couche
   était pourtant bien là, à la bonne taille et au bon cap — vérifié en la
   peignant en rouge opaque. Le halo respirait du même défaut, en pire : une
   aura qui assombrit ne se lit pas comme une lueur mais comme une tache
   d'humidité sous le puck. Les objets translucides prennent `accentGlow`, le
   cran de chroma maximale que le design system réserve aux lueurs ; les
   aplats — disque et flèche — gardent le teal de marque, qui s'y tient très
   bien.
8. **`zoom()` ne se met pas où l'on veut dans une expression.** MapLibre la
   refuse si elle n'est pas l'entrée directe d'un `interpolate` ou d'un `step`
   de premier niveau. Glissée sous un `product`, elle échoue à la pose — « zoom
   expression may only be used as input to a top-level step or interpolate »,
   dans `logcat` et nulle part ailleurs — et la propriété retombe sur son
   défaut sans que rien ne manque à l'écran. La multiplication descend dans les
   sorties des paliers.
9. **`circle-radius` se compte en pixels logiques, le `Canvas` en pixels
   physiques.** Sur le S21 le facteur est trois : raisonner sur le mauvais
   rend inoffensif tout seuil qui compare un rayon de couche à une icône
   peinte. Voir `MapScale`, dont la conversion est verrouillée par un relevé de
   `Projection.getMetersPerPixelAtLatitude` pris sur l'appareil.
10. **Une couche du puck doit sortir du halo pour exister.** Le halo faisait
   trente-cinq points de rayon : le cône, dessiné à vingt-huit, tenait
   entièrement dessous, et l'anneau d'incertitude s'y noyait tant qu'il
   annonçait moins de vingt mètres — le seul moment où il se voyait était donc
   celui où plus rien n'était fiable. Le cône est monté à quarante-six, le halo
   est redescendu à vingt-deux, soit une douzaine de mètres ; les trois
   tiennent maintenant leur place, mais c'est un budget commun et toute couche
   ajoutée là s'y mesure.

## Tests

```bash
./gradlew test
```

Les tests portent sur les modules purs et le décodage : profils de caméra,
fenêtres de progression, stabilisation du cap, fusion boussole / cap de route,
ancre de mouvement, échelle métrique de la carte, contraste des tokens,
échelle typographique, échelles d'élévation et de mesure,
classement de la recherche d'arrêts, règles des adresses favorites
(remplacement, fusion, pierres tombales), et décodage des **captures réelles**
des points d'entrée du BFF. Pas les vues, pas le `MapController`.

Une garde à part balaie `app/` et `feature/` : aucune mesure chiffrée à la
main, aucune ombre posée hors du design system, aucun caractère en guise
d'icône. Elle échoue sur la première réapparition, comme celle du HUD web —
c'est la seule façon tenue de garder une échelle après la revue de code.

## Session

La carte est derrière une session : sans jeton valide, `AuleRoot` montre la
connexion. Le menu du compte s'ouvre **en volet**, depuis l'avatar du socle de
recherche, et la déconnexion y est **seule, en bas, avec confirmation** —
un doigt qui dérape au volant ne doit pas renvoyer à la saisie du mot de passe
(reprise de `../SAE/docs/carte-app/REPRISE.md`, 09/08/2026). La carte
d'identité du menu lit la fiche `drivers` (nom, matricule, dépôt) ; sans
fiche, elle retombe sur l'adresse de session. Le profil s'ouvre depuis cette
carte, en lecture **et en écriture** : une barre Annuler / Enregistrer
n'apparaît que si la saisie s'écarte de ce que le serveur a. S'y déconnecter
ne redemande pas confirmation. La photo de profil s'envoie vers le bucket
Storage `driver-avatars` (chemin `{uid}/avatar.jpg`) puis dans
`drivers.avatar_url` ; le portrait du menu et du profil retombe sur les
initiales si l'image manque.

Les habilitations ferment la session : `user_profiles.role` et la fiche
`drivers` passent par `resolveAgentAccess`. Un compte voyageur, ou une
vérification impossible, revient à l'écran de connexion avec le message
Flutter, mot pour mot. Une fiche illisible **ne** déconnecte **pas** si un
rôle staff suffit à ouvrir. Un compte mixte (conduite + MSR) a les deux
habilitations ; l'espace de travail reste unique — tout est combiné.

`:feature:map` n'en sait rien : il reçoit un `onOpenMenu` optionnel, et c'est
la racine qui pose le menu et le profil par-dessus la carte. La carte n'est
pas démontée pendant ce temps — MapLibre garde son style et sa position.

L'inscription professionnelle reprend l'assistant Flutter (métiers cumulables,
Naolib, identité, mode de conduite, compte). GoTrue reçoit les métadonnées
web v2 et un défi PKCE ; le lien `io.aule.pro://login-callback/` échange le
code contre une session. Le mot de passe ne survit pas dans le brouillon
local. Un compte tout juste créé reste soumis à la validation du réseau : sans
habilitation, la porte d'accès ramène à la connexion.

La dernière étape offre aussi **l'inscription par compte Google**
([ADR-014](Docs/adr/ADR-014-inscription-par-fournisseur.md)) : même PKCE, même
adresse de retour, dans un onglet de navigateur — jamais une WebView, que
Google refuse. `/authorize` ne transportant pas de métadonnées, le métier, le
réseau et le matricule sont posés au retour par `PUT /user`, depuis le brouillon
relu sur le disque : l'écran, lui, peut avoir été tué pendant l'aller-retour.
Google fournit une identité, pas une habilitation — la validation par le réseau
est la même que par e-mail. Quatre réglages serveur conditionnent le parcours et
ne se lisent nulle part dans le code ; l'ADR les énumère. Deux méritent d'être
sus d'avance : l'application Google est en état *Test*, donc réservée à cent
comptes inscrits à la main, et un compte e-mail existant qui porte la même
adresse **se voit lier** l'identité Google plutôt que dupliquer — les
métadonnées d'onboarding s'écrivent alors sur ce compte-là.

Le profil a deux onglets. **Préférences** mémorise Clair / Sombre / Auto
(`sae.theme_mode`, défaut clair comme Flutter) et liste les traces GPS de
diagnostic. **Profil** porte la suppression définitive (`delete_my_account`) :
un échec réseau laisse la session ouverte pour réessayer.

## Les adresses favorites

Le socle de recherche ouvre sur **Domicile** et **Travail**, puis sur les
adresses qu'on y a ajoutées — crèche, salle de sport, famille. Un appui lance
l'itinéraire depuis la position : ouvrir, toucher, rouler, sans une lettre
tapée. Les deux emplacements nommés s'affichent **avant d'exister**, en « À
définir » : un raccourci absent ne se découvre jamais, et celui-là ouvre
l'éditeur au lieu de ne rien faire.

L'éditeur demande l'adresse d'abord — c'est ce qu'on est venu faire — et propose
le nom à partir d'elle. Il partage le géocodeur de la carte sans partager son
état : chercher une adresse à enregistrer ne doit pas défaire l'itinéraire qu'on
regardait (`PlacePickerModel`).

**Les favoris vivent sur l'appareil ; le compte les rattrape** ([ADR-012](Docs/adr/ADR-012-favoris-locaux-d-abord.md)).
Lus du disque de façon synchrone, ils sont à l'écran avant la première image, et
la rangée fonctionne dans un parking souterrain. La synchronisation
(`user_saved_places`, RLS sur `auth.uid()`) fusionne à l'horodatage **avant**
d'écrire, et une suppression laisse une pierre tombale datée — sans elle, un
favori effacé revient au prochain démarrage, renvoyé par un serveur qui n'a rien
appris. Une synchronisation ratée est journalisée et rien d'autre : la liste
locale est déjà correcte.

Le sélecteur de mode du volet d'itinéraire porte désormais **les trois durées**.
Elles coûtent deux appels de plus par destination — le mode demandé répond déjà —
et aucun de plus quand on bascule d'un mode à l'autre : ce sont les durées d'une
destination, pas d'un calcul. Sans elles, comparer imposait de toucher un onglet,
donc de relancer un calcul et de perdre le trajet affiché. Chaque segment tient
sur **une ligne** — un pictogramme, la durée — et le nom du mode est passé à
TalkBack : le mot coûtait vingt points de hauteur pour redire ce que l'icône dit
plus vite.

Le volet d'itinéraire est le seul dont le palier **ne vaut pas 45 %** de la
fenêtre mais 60 % (`ROUTE_PEEK_FRACTION`). Les fiches répondent à « qu'est-ce que
c'est ? » et ce qui dépasse est du détail ; l'itinéraire pose une **décision** —
d'où l'on part, par quel mode, sur quel trajet, et le bouton qui l'engage. Coupé
à 45 %, il montrait la question sans jamais la réponse, et il fallait le déplier
à chaque fois. À 60 %, un trajet à une variante tient entier et la deuxième
dépasse sous la barre d'action, ce qui est tout ce qui dit qu'elle existe ; un
trajet à pied, plus court, s'arrête à 47 % et rend le reste à la ville — le
palier reste mesuré sur le contenu.

« Démarrer » vit **hors du volet**, posé au bord de la fenêtre. Un
`BottomSheetScaffold` a deux crans, et le bas de son contenu ne tombe au bas de
l'écran qu'à l'un des deux : épinglé dedans, le bouton se retrouvait coupé par
les touches de navigation du système dès que le volet était au palier. Le volet
lui réserve sa hauteur en pied (`RouteActionBarHeight`) pour que la dernière
variante puisse toujours être amenée au-dessus de lui.

## Reste à faire (hors jalon 1)

La relève est sur le rail gauche. La position du service est publiée
(`publish_position_with_state`) : un collègue Android apparaît dans Relève,
et le sortant voit la relève arriver. Le suivi live (`handover_track`) pose
le véhicule du collègue sur la carte dès que la relève est engagée. Sans
conducteur connecté, Relève bascule sur la grille horaire : sens, arrêt,
passage, puis ouverture du service. Le choix d'arrêt sur une relève live
(`handover_set_stop`) pose le point de rendez-vous sur la carte. Les
alertes de rapprochement (arrêts, minutes, arrivée) se règlent après
le choix d'arrêt et sonnent pendant le suivi. Le suivi affiche la
distance, les arrêts restants, l'arrivée estimée et le départ conseillé
(temps de trajet OSRM). Les arrêts de relève live montrent les passages
de la ligne, et l'heure retenue part avec `handover_set_stop`. Le choix
de ligne propose d'abord **En service maintenant** (flotte certifiée) et
**Relevées récemment**, le reste via la recherche (y compris « T1 » pour
le tram 1). Le suivi cale la course GTFS du jour près du collègue
(`gtfs_trip_profiles` / départs actifs) : retard et ETA restent justes
quand le véhicule est à l'arrêt ; sans course, le repli distance/vitesse
reste en place. À la reprise d'une relève engagée, l'arrêt se rattache
par id, nom, puis position (≤ 120 m) — sinon on redemande le point plutôt
que d'en inventer un. Après confirmation (ou repli horaire), l'écran DONE
affiche « Service repris » et une notification locale confirme la
passation ; une relève annulée pendant le suivi se solde aussi en DONE,
sans passation. Le signalement de terrain part du même rail
(`driver_reports`).
