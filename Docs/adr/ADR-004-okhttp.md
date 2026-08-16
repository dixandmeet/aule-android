# ADR-004 — OkHttp + kotlinx.serialization

**Statut** : acceptée · **Date** : 2026-08-16

## La question

Quel client HTTP, quel décodeur JSON ?

## La décision

**OkHttp** pour le transport, **kotlinx.serialization** pour le décodage. Pas
Retrofit, pas Ktor.

Un seul client OkHttp pour toute l'application, **y compris les tuiles MapLibre**.
Un seul pool de connexions, un seul délai d'attente, un seul point de journal.

Les pannes sont des **types** (`ApiException.NotFound`, `UpstreamUnavailable`, …),
pas des codes d'état qu'un appelant pourrait oublier de lire. 404 et 502 mènent
au même écran vide et n'appellent pas la même réaction.

## Pourquoi pas Retrofit

Retrofit ajouterait une couche pour trois GET. Le client tient en un fichier, lève
toujours, et ne rend jamais une liste vide pour masquer un incident — le défaut
qui, côté Flutter, avait produit une carte d'apparence normale sans véhicules et
sans message.

## Pourquoi pas Ktor

Ktor tirerait son propre moteur. OkHttp arrive de toute façon avec MapLibre : deux
piles HTTP, deux pools, deux timeouts à tenir, pour le même fil.
