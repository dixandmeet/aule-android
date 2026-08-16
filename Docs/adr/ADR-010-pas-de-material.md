# ADR-010 — Pas de Material 3

**Statut** : acceptée · **Date** : 2026-08-16

## La question

S'appuyer sur Material 3, ou dessiner Aule ?

## La décision

**Aucune dépendance Material 3.** Les fondations viennent de
`androidx.compose.foundation`. Couleurs, rôles typographiques, rayons, durées
et composants (`AuleButton`, `LineBadge`, `AuleSheet`) vivent dans
`:core:designsystem`.

Le thème XML de la fenêtre hérite de `Theme.Material.Light.NoActionBar` **du
framework**, pas d'AppCompat : c'est le parent le plus mince qui donne une
activité sans barre, pas une invitation à tirer Material Compose.

## Pourquoi

Material apporterait une palette, des formes et des ondulations qui donneraient
au produit l'air d'une démonstration. L'identité Aule — le vert de la carte en
production, cinq rôles typo, 44 dp de cible tactile — passe avant.

Le volet n'est pas un `ModalBottomSheet` : trois détentes (0,30 / 0,55 / 0,90),
carte restée vivante derrière, hauteur publiée à la caméra. Material ne sait
pas ça, et l'apprendre coûterait plus cher que l'écrire.

## Quand la reconsidérer

Jamais pour « aller plus vite ». Seulement si une exigence Play ou d'accessibilité
système impose un composant que Foundation ne peut pas porter — et alors, un
composant, pas le kit entier.
