# ADR-008 — `minSdk 26`

**Statut** : acceptée · **Date** : 2026-08-16

## La question

Quel plancher Android : 24 (Nougat, encore cité), 26 (Oreo), ou plus haut ?

## La décision

**API 26.** `java.time` sans desugaring, canaux de notification, icônes
adaptatives. Le S21 de test est en 15 ; le parc conducteur Aule n'a plus de
Nougat en tournée.

`targetSdk` et `compileSdk` valent 36.

## Le coût de revenir à 24

- desugaring de `java.time` (les modèles l'utilisent partout : `Instant`,
  `Duration`) ;
- un second jeu d'icônes PNG, alors que 26 dispense des repli ;
- des tests sur un API qu'on ne peut plus mesurer sur le matériel de la maison.

On ne le fera pas sans un appareil de flotte bloqué sous 26, nommé, pas supposé.
