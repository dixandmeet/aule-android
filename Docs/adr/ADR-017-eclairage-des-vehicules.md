# ADR-017 — Le nuancier des véhicules éclaire

**Statut** : acceptée · **Date** : 2026-09-12

## La question

Les modèles de l'ADR-015 étaient rendus **sans lumière** : un ombrage fixe cuit dans les
sommets (`0,72 + 0,24 · n_z + 0,06 · n_x`), multiplié par la teinte de la ligne. Le volume
se lisait, mais la flotte avait l'air de jouets en plastique mat posés sur la carte : pas de
reflet sur les vitres, pas d'ombre au sol, des roues de bus peintes en vert de ligne, et une
caisse dont l'éclairage ne changeait jamais, quelle que soit la façade voisine.

Comment rendre ces véhicules plus réels sans changer de modèles ?

## La décision

**Le nuancier éclaire, avec la lumière du style.** Le sommet emporte sa normale de face, et
le fragment calcule un éclairage à trois termes — ambiante hémisphérique, soleil de
Lambert, reflet de Blinn-Phong — sur quatre matières : carrosserie satinée, vitrage qui
reflète le ciel, châssis mat, feux émissifs. Une ombre de contact, dessinée avant les
caisses, pose chaque véhicule sur la chaussée.

La lumière n'est pas la nôtre : c'est le `light` de `style-light.json` et `style-dark.json`,
celui qui ombre les bâtiments en `fill-extrusion`. Un bus et la façade devant laquelle il
passe reçoivent le jour du même côté.

## Pourquoi on revient sur « aucune lumière »

L'ADR-015 reprenait du web une interdiction : « l'éclairage du web dépendait de l'ordre de
chargement et de l'espace colorimétrique actif, et rendait la flotte sombre une fois sur
deux ». C'était un défaut de three.js — `ColorManagement` est un drapeau global que le
premier `onAdd` bascule — pas un défaut de l'éclairage.

Ici le nuancier est écrit à la main, en GLSL ES 1.00, avec des uniformes que Kotlin publie
avec chaque trame. Il n'y a ni ordre de chargement ni espace colorimétrique : la lumière
est déterministe, et la nuit se règle dans un seul fichier, `VehicleLighting.kt`.

## Ce que le style ne pouvait pas nous dire

`Light.getPosition()` de MapLibre Android rend une `Position` dont les trois angles sont
**privés, sans accesseur**. Les valeurs sont donc recopiées, et un test relit les deux
fichiers de style et échoue à la première divergence (`VehicleLightingTest`).

Et la direction suit **la formule du moteur, pas la prose de la spécification**. Celle-ci
dit qu'un azimut de 0° désigne « une lumière venant du nord » ; le nuancier `fill-extrusion`
calcule `x = cos(a + 90°) · sin(p)`, `y = sin(a + 90°) · sin(p)` dans le repère des tuiles,
où `y` croît vers le sud — à `a = 0`, ce sont les faces tournées vers le **sud** qui
s'éclairent. Le style clair, à 175°, éclaire par le nord, et c'est ce que montrent ses
façades. Reprendre la prose aurait mis les bus à contre-jour de la ville.

## Ce qu'on a mesuré sur l'appareil

| Question | Réponse |
|---|---|
| Deux programmes GLSL compilent-ils sur le Mali-G78 ? | Oui, ES 3.2 — `initialize` les journalise |
| Le vitrage reflète-t-il selon l'angle ? | Oui : l'œil se tire de la matrice (point où `x = y = w = 0`), sans lire `bearing` ni `pitch` |
| Les roues du bus ? | Elles portent le matériau générique `Material` ; seul le nom du maillage (`FrontWheels`) les distingue — le web les peint en vert de ligne |
| `Black` du tram ? | C'est le bogie, pas une vitre — en glace, il aurait reflété le ciel |

Un piège de vérification, sans rapport avec le rendu mais qui a coûté une heure : **le
sondage de la flotte ne suit que les gestes** (`settleRegion` sur `onMoveEnd` et
`onScaleEnd`). Un cadrage programmé — une recherche d'arrêt — laisse la fenêtre
d'interrogation là où l'usager se trouve. À cinq kilomètres de la position, la carte
montre une ville sans un seul véhicule, et rien ne le dit. Un glissement d'un doigt suffit à
déplacer la fenêtre.

## Deux pièges payés à l'écran le 12/09

### Un éclairage multiplicatif ne pardonne pas les aplats sombres

L'ombrage cuit qu'on remplace allait de 0,72 à 1,02 : **jamais plus bas**. Le premier jeu de
valeurs d'ambiance livrait 0,48 sur un flanc au sud, et 0,30 une fois l'occlusion de contact
appliquée. Un bogie à 0,23 d'aplat y tombait à 0,07 — du noir.

À l'écran, un bus devenait une coque noire surmontée d'une verrière. Les valeurs tiennent
désormais la même plage que l'ombrage cuit, et `VehicleLightingTest` échoue si un réglage
la quitte.

### Les noms de matériaux du pack mentent, et on peut le mesurer

L'heuristique du web range au châssis tout ce qui s'appelle `Bottom`, `Bumper`, `Black`.
Mesuré dans `bus.glb` :

| matériau | hauteur | longueur | aire |
|---|---|---|---|
| `Bottom` | 0,23 → 1,65 m | 0,2 → 10,9 m | 3,9 |
| `Bumper` | 0,23 → 2,73 m | 0,1 → 11,0 m | 8,1 |
| `Top` | 0,96 → 3,20 m | 0,2 → 10,9 m | 12,4 |

`Bottom` n'est pas un bas de caisse : c'est **le panneau latéral inférieur sur toute la
longueur**. `Bumper` n'est pas un pare-chocs : c'est la deuxième surface du modèle. Les deux
au châssis, c'est la moitié du bus peinte en presque noir.

La règle qui en sort : **la carrosserie doit couvrir la majorité de la surface**, et un test
le vérifie sur les vrais fichiers. Une heuristique par nom se contrôle par la géométrie, pas
par la lecture.

## Ce qu'il a fallu de plus pour que ça paraisse vrai

Un éclairage juste ne suffisait pas : la flotte restait « présentée », pas vue. Trois
décisions, toutes prises en regardant l'écran comme on regarderait une prise de vue par drone.

**Les roues sont le seul noir.** Une pièce sombre neutre au milieu d'une carrosserie se lit
comme une carcasse apparente. Jupes, pare-chocs et bogies prennent donc la **livrée
assombrie** — `MeshPart.SKIRT`, la teinte de la ligne multipliée par 0,78 — parce qu'une jupe
de tram n'est pas d'une autre matière que sa caisse : elle est à l'ombre d'elle-même. Un test
échoue si le noir dépasse 12 % de la surface d'un modèle.

Le facteur compte autant que l'idée : à 0,48 le bas de caisse du tram tombait à RGB (26,44,37)
— la barre noire qu'on venait d'enlever, revenue sous un autre nom. Une livrée déjà sombre ne
supporte pas d'être assombrie deux fois.

**Le vitrage réfléchit.** Les flancs d'un tram sont vitrés aux deux tiers de leur bande
médiane ; un vitrage traité comme un aplat sombre ne donne pas des vitres sombres, il donne un
tram noir. La part réfléchie de base pèse donc autant que l'albédo. Vu d'en haut, une baie
verticale renvoie la chaussée — le vecteur réfléchi pointe vers le bas —, et c'est bien ce que
montre n'importe quelle vue aérienne : des bandes claires, jamais des trous.

**La flotte est opaque.** Elle était à 0,6 puis 0,85. Rien ne trahit plus vite un décor qu'un
objet qu'on traverse du regard : à z18, on lisait les rails à travers un tram de vingt-huit
mètres. Le véhicule choisi se distingue par son anneau, qui est de toute façon ce qu'on
regarde. On perd la rue sous la caisse ; l'ombre de contact dit déjà où le véhicule se pose.

Ce qui reste hors de portée du rendu : les modèles eux-mêmes. Le pack Quaternius est
bas-poly et le restera. Le pas suivant en réalisme est un choix d'assets — un Citadis, un bus
articulé — pas un réglage de nuancier.

## L'oubli qui ne se voyait pas : `VehicleModelLayer`

L'application voyageur (`../voyageur/Android`) créait bien la scène et lui publiait ses poses,
mais **n'enregistrait jamais `VehicleModelLayer`**. Sans cette couche, aucune `CustomLayer`
n'est posée, le rendu natif n'est jamais initialisé, son statut reste `NEEDS_INIT` — et
`VehiclesLayer` se rabat sur le volume extrudé.

Le repli est exactement l'écran d'avant la 3D. **C'est ce qui l'a caché** : rien ne manque à
l'image, rien n'est en erreur, et la carte a l'air finie. Le refus de la scène se journalise
désormais côté voyageur, et le compte de couches au chargement du style (« 9 couche(s)
posée(s) ») suffit à trancher.

Corollaire de vérification : voyageur porte des flavors, donc sa tâche d'installation est
`installDevelopmentDebug`. `installDebug` résout des tâches des modules bibliothèque, répond
« Installed on 1 device. » et **n'installe pas l'application**.

## Ce qu'on accepte en échange

- **Dix flottants par sommet** au lieu de sept : 4 578 sommets pour le bus, 2 136 pour le
  tram, soit 270 Ko en tout. Rien.
- Un calcul par fragment avec deux `pow` et un `normalize` — invisible dans le budget, les
  véhicules couvrant quelques milliers de pixels.
- **Une divergence avec iOS**, qui cuit encore son ombrage (`MeshAsset.swift`). L'ADR-015
  demandait que la prochaine divergence se voie : la voici. Le portage est le même travail,
  en Metal.

## Ce qui n'est pas dans cette décision

Les modèles restent ceux du pack Quaternius, bas-poly et CC0. Le pas suivant en réalisme
serait de meilleurs modèles — un Citadis et un bus articulé — et c'est un choix d'assets,
pas de rendu.
