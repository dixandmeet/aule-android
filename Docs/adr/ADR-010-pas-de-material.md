# ADR-010 — Adoption des composants Material 3 Standards

**Statut** : révisée · **Date** : 17/08/2026

## La question

Comment assurer la maintenance et l'accessibilité de l'application tout en gardant l'identité visuelle Aule ?

## La décision

L'application utilise désormais les composants officiels de la bibliothèque `androidx.compose.material3` (`Button`, `OutlinedTextField`, `CircularProgressIndicator`, etc.).

L'identité Aule est injectée via le `MaterialTheme` qui utilise les jetons de couleurs (`AuleTokens`), de typographie et de rayons (`AuleRadius`) définis dans `:core:designsystem`.

### L'échelle de conteneurs est opaque

`surfaceContainer*` est une **échelle** : cinq crans distincts, dont Material se sert pour détacher une carte de son fond, un menu de sa page, un volet de la carte. Les cinq valent cinq tons neutres, et aucun n'est translucide.

Le verre reste l'identité d'Aule, mais il se **demande** : c'est `AuleTokens.surface`, que la barre de recherche pose sur la carte. Le servir par le thème le mettait partout — un volet de saisie laissait remonter les bâtiments sous ses puces, et la barre de navigation se salissait de ce qui passait dessous. Un panneau flottant au-dessus du fond de carte veut du verre ; un volet qui prend l'écran, non.

## Pourquoi

1.  **Accessibilité** : Les composants officiels gèrent nativement les rôles sémantiques, le focus clavier et le lecteur d'écran de manière plus robuste que les implémentations personnalisées.
2.  **Maintenance** : Moins de code "maison" à maintenir pour les comportements standards (animations de clic, libellés flottants).
3.  **Standards Android** : Les utilisateurs retrouvent les interactions habituelles du système (effet ripple, animations de transition).

## Comment la décision tient

Une décision de cette portée ne tient pas par la relecture : il suffit d'un écran pressé pour réintroduire un `BasicText`, une carte faite à la main ou un import Material 2, et l'application repart avec deux design systems en parallèle.

`MaterialGuardTest` (`:core:designsystem`) balaie donc `app/` et `feature/` sur quatre règles : pas d'import Material 2, pas de texte écrit avec Foundation, pas d'enveloppe Aule redisant un composant Material 3, pas de forme écrite hors du thème.

Chaque règle porte une **dette** : la liste nominative des fichiers qui la violent encore, la migration se faisant écran par écran. Deux tests l'encadrent, et c'est leur combinaison qui fait le travail :

- un fichier **hors dette** qui viole la règle fait échouer la garde — on ne régresse pas, et un écran neuf naît conforme ;
- un fichier **en dette** qui ne viole plus la règle fait aussi échouer la garde — l'exemption doit être retirée le jour où elle devient inutile.

Les quatre listes sont vides : la migration des écrans est terminée. Les enveloppes `AuleButton`, `AuleCard`, `AuleTextField`, `AuleIcon`, `AuleIconButton`, `AuleBusyIndicator` et `AuleSheetHandle` n'existent plus. Les gardes restent, à sec — un écran neuf qui réintroduit `BasicText` ou une de ces enveloppes échoue dès le test.

## Limites

Le volet carte n'est plus un composant maison : `BottomSheetScaffold` laisse la carte vivante sous le panneau (peek ~45 %, déployé), et `ModalBottomSheet` sert les flux qui interrompent — signalement, fin de service. On perd le cran intermédiaire à 55 %.

Le composant Material coûte trois choses que la maison rendait, et qu'il faut redonner à la main :

- **le retour.** `BottomSheetScaffold` sert un volet *persistant* : il n'installe aucun gestionnaire de retour. Sans `PredictiveBackHandler` posé par l'écran, le geste de retour sur un arrêt ouvert ne referme pas le volet — il quitte l'application ;
- **les insets.** Il n'a pas d'encoche pour les barres système : déployé, il monte jusqu'au pixel zéro et sa poignée finit dans l'heure. La hauteur du contenu se borne donc à la main, poignée comprise ;
- **le palier.** Le peek est mesuré depuis le bord bas de l'écran, barre de navigation incluse. À 30 %, le bouton d'un volet court — « Suivre ce véhicule » — passait sous cette barre. Les composants métier qui n'ont pas d'équivalent Material (`AuleBanner`, `LineBadge`, `AuleGlyph`, `realtimeInk`) restent, mais consomment le thème. `LocalAuleTokens` ne sert plus que le design system (marque, ombre d'accent, temps réel).

Le thème XML (`app/src/main/res/values/themes.xml`) n'hérite pas de `Theme.Material3` : celui-ci vit dans la bibliothèque Material Components pour les Vues, et l'application est en Compose pur. Ce thème ne sert qu'à peindre la fenêtre avant la première image de Compose ; il est aligné sur `MaterialTheme.colorScheme.surface` et transparent pour le bord-à-bord.
