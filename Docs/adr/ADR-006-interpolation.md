# ADR-006 — L'interpolation hors état Compose

**Statut** : acceptée · **Date** : 2026-08-16

## La question

Par où transitent la position GPS, le cap, les positions de véhicules et la caméra ?

## La décision

**Rien de ce qui change à la fréquence de l'écran ne transite par un `State` lu par
un Composable.** Ces valeurs sont écrites directement dans MapLibre, depuis un
`Choreographer` (véhicules, puck) ou un ticker à ~15 Hz (caméra).

| Ce qui change | Où il vit | Fréquence |
|---|---|---|
| positions de véhicules, pose du puck | `VehiclesLayer`, `UserPuckLayer` — **non observables** | 60–120 Hz |
| caméra | écrite par `MapController.applyCameraTarget` | ~15 Hz |
| `lastFix`, cap stabilisé | `LocationProvider` — exposé, **lu par aucun `body`** | 1 Hz |
| instantané de flotte, sélection, erreurs | `MapViewModel` — lu par Compose | 15 s / au doigt |

## Pourquoi cette ADR existe

**La règle est invisible dans le code.** Rien n'empêche d'écrire une position dans
un `StateFlow` « pour l'afficher dans le HUD » : ça compile, ça marche à l'œil, et
ça fait recomposer tout l'arbre à chaque point GPS. C'est exactement la manière
dont un contributeur bien intentionné détruira la fluidité qui justifie ce projet.

## Comment la tenir

- Une donnée à plus de ~2 Hz va dans une `MapLayer`, jamais dans un `@Composable`.
- Ce qu'il faut afficher d'une telle donnée est **dérivé et échantillonné** : la
  pastille de flotte lit `FleetStatus`, qui change toutes les 15 s, pas la liste
  des véhicules.
- En cas de doute : `dumpsys gfxinfo` sur le S21. La carte MapLibre rend hors du
  pipeline Compose ; zéro image Compose pendant un déplacement est le signal
  qu'on cherche.

## Ce qu'on accepte en échange

Deux mondes cohabitent — l'état Compose et l'état MapLibre — et la frontière doit
être tenue à la main. C'est le prix de la fluidité, et il se paie en discipline.
