# ADR-003 — Racine de composition manuelle

**Statut** : acceptée · **Date** : 2026-08-16

## La question

Hilt, Koin, ou un graphe écrit à la main ?

## La décision

**`AuleGraph`, assemblé à la main.** C'est le seul endroit qui décide quelle
implémentation répond à quelle interface. Les écrans reçoivent des interfaces ;
aucun ne sait si la donnée vient du BFF, de Supabase ou d'une fixture.

## Pourquoi pas Hilt aujourd'hui

Le graphe tient en quelques dizaines de lignes. Tout passe déjà par constructeur :
basculer vers Hilt plus tard ne toucherait aucun appelant. Introduire Hilt maintenant
ajouterait une annotation, un plugin KSP et un temps de compilation pour un graphe
qu'on lit en une page.

## À quelles conditions on basculerait

Quand le graphe ne tient plus en une page : authentification, plusieurs bases,
plusieurs processus. Pas avant. Hilt n'est pas une dette, c'est une option datée.
