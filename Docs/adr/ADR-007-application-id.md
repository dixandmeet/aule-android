# ADR-007 — `applicationId io.aule.android`

**Statut** : acceptée · **Date** : 2026-08-16

## La question

Quel identifiant Play : `io.aule.pro` (le Flutter existant) ou un id nouveau ?

## La décision

**`io.aule.android`**, avec suffixes `.development` et `.staging`.

Les trois APK cohabitent entre eux **et** avec `io.aule.pro` / `io.aule.app` sur
le même appareil. C'est ce qui permet de comparer, côte à côte, le natif et le
Flutter pendant la transition.

## Ce que coûterait la bascule vers `io.aule.pro`

Play identifie une application par son `applicationId`. Reprendre `io.aule.pro`
ferait de cette app **la même fiche** que le Flutter Pro : mêmes avis, mêmes
installations, même signature à honorer. C'est une décision produit — remplacer
ou cohabiter — pas une décision de build.

Tant que le Flutter Pro est en production, le natif reste à côté. Le jour du
remplacement, on republie sous `io.aule.pro` avec la **même clé** que SAE, ou
on laisse `io.aule.android` et on déprécie l'ancien. Les deux sont possibles ;
aucun n'est gratuit.
