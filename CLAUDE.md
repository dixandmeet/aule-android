# Aule Android — application native

Application Kotlin / Jetpack Compose (Material 3 thémé Aule), MapLibre Native OpenGL, cliente
du BFF Aule. L'une des deux applications natives d'Aule, avec `../Native` (iOS). `../SAE`
(Flutter) reste la référence des règles métier et des modèles de données, mais ne contraint
plus les choix d'UX ni d'architecture.

Ce n'est pas un prototype, et la documentation existante est à jour — s'y fier plutôt que de
redécouvrir.

## À lire avant de toucher au code

| Sujet | Où |
|---|---|
| Vue d'ensemble, mesures, pièges | `README.md` |
| Décisions structurantes | `Docs/adr/` — 12 ADR |
| Contrat du BFF et ses pièges | `../docs/CONTRAT-BFF.md` |
| Plans en cours | `Docs/PLAN-*.md` |

**Avant de toucher à la carte, lire [ADR-006](Docs/adr/ADR-006-interpolation.md)** —
l'interpolation vit hors de l'état Compose. C'est la seule règle du projet qui soit invisible
dans le code et qu'un changement anodin suffise à détruire.

## Commandes

Tests — modules purs et décodage, JUnit 5 sur la JVM de l'hôte :

    ./gradlew test

Installer et lancer sur l'appareil de référence :

    ./gradlew installDevelopmentDebug
    adb -s R3CRA0WV55H shell am start -n io.aule.android.development/io.aule.android.MainActivity

Compilation seule d'un module :

    ./gradlew :core:map:compileDevelopmentDebugKotlin

**Appareil de référence : Samsung S21** (`SM-G991B`, Android 15, 120 Hz), identifiant
`R3CRA0WV55H`. Pas d'émulateur dans le flux de travail — la fluidité se juge sur cet écran.

`adb` vit dans `~/Library/Android/sdk/platform-tools/`, ajouté au `PATH` par `~/.zshrc`.

Pour installer, lancer et vérifier à l'écran, la skill **`run-app`**
(`.claude/skills/run-app/`) porte le flux complet : contrôle de l'appareil, installation,
lancement, capture, journaux filtrés sur l'app, et les pannes courantes.

Un build `release` **échoue volontairement** si la signature n'est pas renseignée dans
`local.properties` : la clé de debug est refusée. C'est une garde, pas un bug.

## Architecture

    app/                 activité unique, AuleGraph, flavors
    core/
      common/            config, journal, dispatchers
      geo/               Coordinate, GeoMath, projection sur polyligne
      model/             TransportVehicle, TransitStop, Auth, contrats de repository
      network/           OkHttp, ApiException, endpoints
      designsystem/      jetons Aule + thème et composants Material 3
      location/          Fused, HeadingStabilizer, MotionAnchor, FGS, AlertTone
      map/               MapController, couches, caméra, icônes
      guet/              moteur du mode Guet
    data/                implémentations BFF + GoTrue — seul module qui voie OkHttp
    feature/
      map/               écran carte, HUD, volets
      auth/              connexion, menu du compte, déconnexion

**Séparation tenue par le graphe de dépendances, pas par la revue de code :** `:app` est le
seul module qui voie `:data`, et `:feature:map` n'en dépend pas. Un `@Composable` ne peut donc
pas atteindre le réseau — c'est une erreur de compilation. Ne pas ajouter `implementation(projects.data)`
à un module `feature` pour se dépanner : c'est cette règle qu'on casserait.

Toute la configuration partagée vit dans les **plugins de convention** de `build-logic/`
(`aule.android.application`, `aule.android.library`, `aule.android.compose`, `aule.jvm.library`),
jamais dans un `allprojects` à la racine. Un `android { }` recopié dans dix modules, c'est dix
endroits où le `minSdk` peut diverger sans que rien ne le signale. Modifier `AuleBuild.kt`
plutôt que le `build.gradle.kts` d'un module.

## Environnements

Un environnement est un **flavor Gradle**, pas un réglage d'exécution.

| Flavor | applicationId | Nom | Mock possible |
|---|---|---|---|
| `development` | `io.aule.android.development` | Aule Pro (dev) | oui |
| `staging` | `io.aule.android.staging` | Aule Pro (recette) | non |
| `production` | `io.aule.android` | Aule Pro | non |

Le mock est **impossible** hors `development` : le module qui porte les fixtures n'est pas sur
le chemin de compilation (ADR-005). Ce n'est pas une convention.

Les valeurs propres à la machine (URL, clé publiable, signature) vivent dans
`local.properties`, non versionné. **Ne jamais lire ni recopier ce fichier** — il porte les
mots de passe du keystore.

| | |
|---|---|
| SDK | `minSdk 26`, `targetSdk 36`, `compileSdk 37` |
| Toolchain | AGP 9.2.1, Kotlin 2.4.10, JVM target 17 |
| Carte | MapLibre Native Android 13.5.0, artefact **OpenGL** (`android-sdk-opengl`) |

DSL classique et Kotlin Gradle Plugin assumés : `android.newDsl=false`,
`android.builtInKotlin=false`. C'est la combinaison éprouvée sur cette machine — ne pas
basculer sans raison.

## Conventions

- **Le code et la documentation sont en français**, commentaires compris. Les commentaires
  expliquent *pourquoi*, souvent avec la mesure qui a mené à la décision. Écrire dans ce
  registre, ou ne rien écrire.
- **JUnit 5 partout**, `kotlin.test` pour les assertions. Les tests portent sur les modules
  purs et le décodage — pas les vues, pas le `MapController`.
- Une garde balaie `app/` et `feature/` : **aucune mesure chiffrée à la main, aucune ombre
  posée hors du design system, aucun caractère en guise d'icône.** Elle échoue sur la première
  réapparition. Passer par les jetons du design system.
- Les formulations du domaine vivent dans `res/values/` (français source) et `res/values-en/`
  (catalogue complet). **Un modèle ne porte aucune phrase** (ADR-011).
- **Le client ne parle pas à Supabase** pour la carte : tout passe par le BFF `www.aule.fr`.

## Pièges de la carte (mesurés, invisibles à la lecture)

1. Un rechargement de style vide sources, couches **et images**, en silence. Le registre
   remonte tout après `setStyle`, puis repeint l'ambiance — dans cet ordre.
2. Jamais `iconIgnorePlacement` sur les arrêts : ils sortiraient de l'index de collision, donc
   des requêtes de features — visibles et intouchables.
3. `iconAllowOverlap` oui, `ignorePlacement` non. Sans le premier, un arrêt disparaît dès
   qu'un nom de rue passe dessous.
4. Poser la caméra **après** création de la vue : à la construction elle n'a pas de taille, le
   cadrage est faux.
5. Le hit-test est en **dp** (22), converti en pixels. Confondre dp et px donne une zone quatre
   fois trop petite sur un écran dense.
6. `android-sdk` n'est pas OpenGL — l'AAR 13.5.0 embarque Vulkan (ADR-002).
7. 404 ≠ 502 sur les passages : « rien ne circule » et « le fournisseur est muet » mènent au
   même écran vide mais n'appellent pas la même réaction.

## Accessibilité

MapLibre rend un tampon opaque : TalkBack n'y trouve aucun marqueur. Le chemin d'accès est une
**action personnalisée** posée sur la carte — « Autour de vous » — qui ouvre une liste d'arrêts
et de véhicules, plafonnée à 12. Toute carte nouvelle doit garder ce chemin.

## Git

Dépôt **indépendant** de `~/Aule` (qui ne suit aucun fichier d'ici), sans remote configuré.
Branche courante au moment de l'écriture : `cursor/handover-done-notify-df65`.
