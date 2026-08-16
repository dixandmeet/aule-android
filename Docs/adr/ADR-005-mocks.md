# ADR-005 — Le mock est impossible en production

**Statut** : acceptée · **Date** : 2026-08-16

## La question

Comment empêcher qu'une fixture serve dans un binaire publié — vraiment empêcher,
pas convenir de ne pas le faire ?

## La décision

**Par le compilateur et le source set, pas par une garde d'exécution.**

Trois flavors (`development`, `staging`, `production`), trois `applicationId`.
`ALLOW_MOCK_SOURCE` n'est vrai que dans `development`. Le module qui porterait
les fixtures n'est pas sur le chemin de compilation ailleurs — il n'existe pas
encore, et le jour où il existera, il n'entrera que dans ce flavor.

Une seconde garde, dans `AuleGraph`, attrape le cas où quelqu'un changerait
`DEFAULT_DATA_SOURCE` dans le flavor production, ce que le compilateur ne peut
pas voir : le process refuse de démarrer plutôt que de servir des véhicules
qui n'existent pas.

## Pourquoi pas une variable d'environnement

C'est ce que faisait le prototype iOS, et c'est inopérant là où ça compterait :
une variable d'environnement n'existe pas dans un lancement Play. Un binaire
*est* son environnement ; rien à l'exécution ne doit pouvoir le retourner.
