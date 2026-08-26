---
name: run-app
description: Installe et lance l'application Android Aule sur le Samsung S21 de référence, puis vérifie le résultat à l'écran par capture adb. À utiliser dès qu'il s'agit de lancer, exécuter, démarrer, relancer, installer, essayer, montrer ou capturer l'app Android de ce projet, ou de vérifier qu'un changement fonctionne vraiment plutôt que de se fier aux tests.
---

# Lancer Aule Android

Valeurs par défaut : flavor **development**, buildType **debug**, applicationId
`io.aule.android.development`.

**Appareil de référence : Samsung S21** (`SM-G991B`, Android 15, 120 Hz), identifiant
`R3CRA0WV55H`. **Pas d'émulateur dans ce projet** — la fluidité se juge sur cet écran, et le
`minSdk` est 26.

Le panneau simulateur de Claude Code ne s'applique pas ici : il ne pilote ni ne diffuse un
appareil physique. La vérification passe par des captures `adb`, et c'est suffisant.

## 1. Vérifier que l'appareil répond

    adb devices -l

Attendu : une ligne `R3CRA0WV55H … device`. Si elle affiche `unauthorized`, l'écran du
téléphone attend une confirmation de débogage USB — le dire à l'utilisateur plutôt que de
réessayer en boucle. Si rien n'apparaît, l'appareil est débranché ou verrouillé : le dire aussi.

`adb` vit dans `~/Library/Android/sdk/platform-tools/`, ajouté au `PATH` par `~/.zshrc`.

## 2. Installer

    ./gradlew installDevelopmentDebug

La tâche compile puis pousse l'APK sur l'appareil connecté. Le premier build est long ; les
suivants profitent du cache de configuration, actif dans `gradle.properties`.

**Ne pas lancer `./gradlew clean` pour débloquer un build.** Le cache de configuration et le
cache de build sont actifs : un `clean` coûte plusieurs minutes et ne répare presque jamais.
Lire l'erreur d'abord.

Un build **release** échoue volontairement si la signature n'est pas renseignée dans
`local.properties` — la clé de debug est refusée. C'est une garde délibérée, pas un bug : ne
pas la contourner avec `-PallowDebugReleaseSigning=true` sans que l'utilisateur le demande.

## 3. Lancer

    adb -s R3CRA0WV55H shell am start -n io.aule.android.development/io.aule.android.MainActivity

L'`applicationId` porte le suffixe du flavor (`.development`), **la classe d'activité non** :
elle reste `io.aule.android.MainActivity`. Les inverser donne un `Activity does not exist`.

Pour repartir d'un état propre sans réinstaller :

    adb -s R3CRA0WV55H shell am force-stop io.aule.android.development

## 4. Vérifier soi-même

Ne pas demander à l'utilisateur si ça marche — le vérifier. Capturer l'écran dans le répertoire
scratchpad de la session, puis lire l'image :

    adb -s R3CRA0WV55H exec-out screencap -p > <scratchpad>/aule-android.png

Journaux de l'app seule, sans le bruit du système :

    adb -s R3CRA0WV55H logcat --pid=$(adb -s R3CRA0WV55H shell pidof io.aule.android.development)

Une réserve propre à ce projet : **une capture ne dit rien de la fluidité.** L'interpolation des
véhicules vise 120 Hz via `Choreographer`, en écriture directe dans la source MapLibre. Un
rendu correct sur une image fixe peut recouvrir une recomposition Compose qui ruine la cadence
— si le doute porte sur l'animation, c'est l'œil sur l'appareil qui tranche, pas la capture.

## Si ça échoue

| Symptôme | Cause habituelle |
|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK déjà installé avec une autre signature — désinstaller d'abord (demander à l'utilisateur) |
| `Activity does not exist` | suffixe de flavor ajouté à la classe d'activité (voir §3) |
| Écran noir après le lancement | style MapLibre pas remonté après `setStyle` ; lire logcat |
| Arrêts visibles mais intouchables | `iconIgnorePlacement` réapparu — ils sortent de l'index de collision |
| Carte vide, aucun arrêt | souvent le réseau, pas le code ; le flavor `development` est le seul où les fixtures existent |
| Build qui échoue sur la garde design system | mesure chiffrée à la main, ombre hors design system, ou caractère en guise d'icône dans `app/` ou `feature/` |

La dernière ligne n'est pas une erreur à contourner : la garde qui balaie `app/` et `feature/`
est le seul moyen tenu de garder une échelle après la revue de code. Corriger le code, passer
par les jetons du design system.
