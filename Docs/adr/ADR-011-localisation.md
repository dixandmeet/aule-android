# ADR-011 — Un modèle ne contient pas de phrase

**Statut** : acceptée · **Date** : 2026-08-16

## La question

Où vivent les mots que l'application adresse à l'usager ?

## La décision

**Le modèle dit *ce qui est*, la vue dit *comment on le formule*.** Toutes les
formulations du domaine passent par `stringResource` et `res/values/strings.xml`.
`DomainText.kt` est le seul endroit qui relie un `FleetStatus`, un `Wait` ou un
`DeparturesOutcome` à une phrase.

Avant — fautif :

```kotlin
val label: String get() = if (isStale) "Positions non rafraîchies" else …
```

Après — le modèle rend une valeur, la vue la formule :

```kotlin
val status: FleetStatus get() = if (isStale) FleetStatus.Stale else …
```

## Pourquoi

Un modèle qui contient une phrase française est un modèle qu'il faut **rouvrir
pour traduire l'application**. Et rouvrir un modèle pour un mot est exactement
ce qui finit par en changer le sens : la formulation et la règle se retrouvent
dans le même `when`, et la seconde se fait modifier en croyant retoucher la
première.

Le gain est immédiat sur les tests. Vérifier
`statusLabel == "Positions non rafraîchies"` rougirait à la première traduction,
sans qu'aucune règle n'ait bougé. Vérifier `status == FleetStatus.Stale`, c'est
vérifier la règle métier — laquelle doit continuer de tenir le jour de la
traduction.

## Ce qui reste dans les modèles

Les décisions sur **le monde**, pas sur le vocabulaire :

- « périmé » se teste **avant** « aucun » ;
- zéro minute vaut `Wait.Approaching` et non `Wait.Minutes(0)` ;
- `DeparturesOutcome` distingue `NOTHING_ANNOUNCED` de `PROVIDER_SILENT`.

`GeoMath.formatDistance` prend le séparateur décimal en paramètre : ce module
ne connaît pas la langue de l'écran, l'appelant, lui, la connaît.

## Traduire

Le français est la langue source (`values/`). L'anglais est un catalogue
**complet** (`values-en/`), pas une phrase isolée : Android retombe chaîne par
chaîne sur la source, et une seule entrée anglaise parmi soixante donnerait un
écran mi-anglais mi-français, ce qui est pire que pas de traduction du tout.
