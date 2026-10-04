# ADR-010 — Adoption des composants Material 3 Standards

**Statut** : révisée · **Date** : 03/10/2026

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

`MaterialGuardTest` (`:core:designsystem`) balaie `app/` et `feature/` : pas d'import Material 2, pas de texte ou de champ Foundation, pas d'ancienne enveloppe sans contrat partagé, pas de redéfinition locale des composants communs, pas de forme écrite hors du thème.

Les composants de `core:designsystem/components` s'appuient sur Material 3 et ajoutent un contrat commun à Pro et Voyageur : dimensions par rôle, formes expressives, pression, haptique, hiérarchie des surfaces et état occupé accessible. Ils peuvent être utilisés par les features ; leur définition reste dans le socle. Les anciens `AuleButton`, `AuleTextField`, `AuleBusyIndicator` et `AuleSheetHandle` restent interdits dans les écrans Pro.

Les règles gardent une dette nominative : une nouvelle violation fait échouer les tests, et une exemption devenue inutile doit être retirée. Les listes sont actuellement vides. Un test de la garde vérifie que l'appel aux composants communs est autorisé et qu'une redéfinition locale est refusée.

### Recherche, feedback et états partagés

`components.AuleSearchField` porte la saisie, l'effacement, la demande de focus et l'action clavier. Sa variante compacte conserve le plancher de 48 dp et un padding intérieur adapté ; la hauteur augmente avec la police. Pro garde la fermeture du clavier sur « Terminé », Voyageur son ouverture explicite depuis le parcours de recherche.

`components.AuleNotice` porte les messages persistants, leur niveau sémantique, leurs actions et leur fermeture. L'API historique `AuleBanner` délègue à ce composant. `states` regroupe les chargements expressifs, états vides, erreurs récupérables et squelettes immobiles ; les anciennes API Pro de chargement et d'absence délèguent à ce même socle. Une erreur et une absence de résultat restent deux états distincts.

Les confirmations temporaires passent par `AuleMessenger` et `AuleSnackbarHost`. Le scope de composition annule les messages à la fermeture de l'écran ; les callbacks ne s'exécutent que sur l'action du snackbar. Les tests JVM vérifient l'ordre et l'annulation, et les tests instrumentés du socle vérifient recherche, focus, grande police et actions d'état sur appareil.

### Cartes, poignées et hauteur des volets

`components.AuleCard` porte la hiérarchie des surfaces, le rayon de carte et la pression.
Les cartouches Pro `SheetCard` délèguent au ton `High`. Une carte interactive conserve
le rôle et le libellé d'action fournis par son parcours ; ses boutons enfants restent accessibles.

`components.AuleSheetGrip` remplace les poignées locales des deux applications. Sa bande
mesurée de 28 dp est publiée pour le calcul du contenu. `foundation.AuleSheetLayout` borne
ce contenu sous la barre d'état, poignée comprise, et garde le palier distinct du cran déployé
lorsque la fenêtre se réduit. Les compositions repliées, les gestes et les parcours restent dans
chaque application ; la hauteur fixe de Voyageur et les mesures de contenu de Pro sont conservées.

Les supports des volets cartographiques utilisent `surfaceContainerLowest` et la forme de
volet du thème. Le socle Pro fermé conserve sa carte flottante et son fond transparent.
`auleBottomSystemPadding` réserve l'union de la barre de navigation et des gestes système,
puis consomme uniquement leur bord inférieur : un enfant ou le clavier n'ajoute pas une seconde
réserve, et la bande haute des gestes système ne déplace pas le contenu du volet. Le chrome
flottant réserve aussi la bande basse complète avant de se relever au-dessus du socle.
Le palier Pro utilise la même mesure sûre que Voyageur, y compris sur One UI.

### Les dimensions et le mouvement sont communs

`foundation.AuleLayout` définit les rôles communs : action principale 56 dp, action compacte et cible tactile 48 dp, champ 60 dp, icône 24 dp. Les API historiques `AuleControl` et `AuleTouch` délèguent à ces valeurs. Le chrome cartographique conserve ses dimensions dédiées : un bouton de formulaire ne définit pas la taille d'un contrôle sur la carte.

Le thème Pro et le thème Voyageur utilisent le même socle expressif. L'API historique `AuleSheetMotion` de Pro délègue au régime de volet de `foundation` ; elle ne porte aucun second réglage. Les parcours métier et leurs confirmations restent dans les features.

## Limites

Le volet carte n'est plus un composant maison : `BottomSheetScaffold` laisse la carte vivante sous le panneau (peek ~45 %, déployé), et `ModalBottomSheet` sert les flux qui interrompent — signalement, fin de service. On perd le cran intermédiaire à 55 %.

Le composant Material coûte trois choses que la maison rendait, et qu'il faut redonner à la main :

- **le retour.** `BottomSheetScaffold` sert un volet *persistant* : il n'installe aucun gestionnaire de retour. Sans `PredictiveBackHandler` posé par l'écran, le geste de retour sur un arrêt ouvert ne referme pas le volet — il quitte l'application ;
- **les insets.** Il n'a pas d'encoche pour les barres système : déployé, il monte jusqu'au pixel zéro et sa poignée finit dans l'heure. La hauteur du contenu se borne donc à la main, poignée comprise ;
- **le palier.** Le peek est mesuré depuis le bord bas de l'écran, barre de navigation incluse. À 30 %, le bouton d'un volet court — « Suivre ce véhicule » — passait sous cette barre. Les composants métier qui n'ont pas d'équivalent Material (`AuleBanner`, `LineBadge`, `AuleGlyph`, `realtimeInk`) restent, mais consomment le thème. `LocalAuleTokens` ne sert plus que le design system (marque, ombre d'accent, temps réel).

Le thème XML (`app/src/main/res/values/themes.xml`) n'hérite pas de `Theme.Material3` : celui-ci vit dans la bibliothèque Material Components pour les Vues, et l'application est en Compose pur. Ce thème ne sert qu'à peindre la fenêtre avant la première image de Compose ; il est aligné sur `MaterialTheme.colorScheme.surface` et transparent pour le bord-à-bord.
