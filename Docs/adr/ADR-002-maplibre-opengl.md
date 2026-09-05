# ADR-002 — `android-sdk-opengl`, pas `android-sdk`

**Statut** : acceptée · **Date** : 2026-08-16

## La question

Quel artefact MapLibre Native Android 13.5.0 : `android-sdk`, `android-sdk-opengl`
ou `android-sdk-vulkan` ?

## La décision

**`org.maplibre.gl:android-sdk-opengl:13.5.0`.**

Tranché par inspection de l'AAR, pas par réputation. `android-sdk` embarque le
backend **Vulkan** (62 symboles `vkCreate*`, aucun GLES). `android-sdk-opengl`
embarque OpenGL ES. L'API Java est identique ; la bascule est une ligne dans
`gradle/libs.versions.toml`.

## Pourquoi OpenGL

Dix ans de production derrière lui, et une app de service ne doit pas risquer un
écran noir en tournée. Vulkan est plus récent, moins éprouvé sur le parc qu'un
conducteur croise — y compris des S21 sous Android 15, qui tiennent OpenGL sans
histoire.

## Ce que l'ADR-015 a changé au coût d'une bascule

Depuis la [couche native des véhicules](ADR-015-couche-native-vehicules.md), OpenGL n'est
plus seulement un choix de compatibilité : **l'application écrit du GL**. Le nuancier des
modèles, leurs tampons et leur état graphique vivent dans `:core:map3d`, en GLSL ES 1.00.

Basculer sur Vulkan ne serait donc plus une ligne dans `libs.versions.toml` — il faudrait
réécrire ce rendu contre les en-têtes `vulkan/` du prefab, que l'AAR livre par ailleurs.

À noter, mesuré : MapLibre demande un contexte `EGL_CONTEXT_CLIENT_VERSION 2`, mais le
Mali-G78 du S21 rend un contexte **ES 3.2**. On écrit quand même en ES 2.0 — rien n'oblige
un pilote à cette générosité.

## Quand la reconsidérer

Si MapLibre documente clairement que `android-sdk` n'est plus Vulkan, ou si un
appareil de la flotte Aule exige Vulkan pour tenir 120 Hz. Mesurer alors, ne pas
supposer — en sachant que le coût inclut désormais le rendu des véhicules.
