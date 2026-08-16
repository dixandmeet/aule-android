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

## Quand la reconsidérer

Si MapLibre documente clairement que `android-sdk` n'est plus Vulkan, ou si un
appareil de la flotte Aule exige Vulkan pour tenir 120 Hz. Mesurer alors, ne pas
supposer.
