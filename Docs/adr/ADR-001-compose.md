# ADR-001 — Kotlin + Jetpack Compose, carte exceptée

**Statut** : acceptée · **Date** : 2026-08-16

## La question

L'interface Android d'Aule est-elle Compose, Views, ou un mélange ?

## La décision

**Kotlin et Jetpack Compose** pour tout ce que l'usager lit et touche — HUD, volets,
boutons, bandeaux. La carte, elle, reste une `MapView` Android : MapLibre Native n'a
pas de backend Compose, et un interop qui recréerait la vue à chaque recomposition
détruirait exactement ce qu'on cherche à gagner.

`AuleMap` est volontairement mince : il crée la `MapView` une fois, relaie son cycle
de vie, et passe la main au `MapController`. Tout le reste — couches, caméra, gestes,
icônes — vit dans `:core:map`, qui ne connaît pas Compose.

## Pourquoi

La carte **est** l'écran. Le Flutter du dépôt a payé la leçon inverse : un échange
d'écran qui détruisait le moteur à chaque fois. Compose recomposerait de même si la
`MapView` n'était pas `remember`ée.

L'UI autour, elle, n'a aucune raison d'être en Views : Material 3 reste dans
Compose et sous les jetons Aule (voir [ADR-010](ADR-010-pas-de-material.md)),
sans fragments, dans une seule activité.

## Quand la reconsidérer

Si MapLibre publie un hôte Compose qui **conserve** le contexte de rendu d'une
recomposition à l'autre. Pas avant : un wrapping cosmétique n'est pas une migration.
