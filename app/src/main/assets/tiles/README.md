# Tuiles et index du réseau

Trois fichiers, **copiés** et jamais édités à la main.

| Fichier | Taille | Ce que c'est |
|---|---|---|
| `transit-lines-index.json` | 23 Ko | L'inventaire des lignes : indice, couleur GTFS, mode, réseau, terminus, cadre du tracé. |
| `transit.pmtiles` | 3,4 Mo | La géométrie des tracés, en archive PMTiles — 2 715 tronçons. |
| `voirie.pmtiles` | 1,6 Mo | Le référentiel de voirie de Nantes Métropole — 40 496 tronçons, avec la largeur mesurée de chaque chaussée et son statut. |

## D'où ils viennent

`dashboard/tools/tiles/build-transit.sh` produit les deux premiers **d'un seul geste**, depuis
le GTFS et OpenStreetMap ; `build-voirie.sh` produit le troisième depuis le jeu ouvert
« Tronçons des voies de Nantes Métropole » (data.nantesmetropole.fr, ODbL). La source de vérité est `dashboard/public/tiles/` ; l'app iOS en garde la même
copie dans `Native/Aule/Resources/`.

Trois copies peuvent diverger en silence. Les empreintes au moment de la copie :

```
0f34c58fb02a0d108bd2e1e13a1b8a8082e92104385bf6ace46a3020963a4a66  transit-lines-index.json
0159163e21027331d7014be562f979fe7c3f99811bcb563c621f52dba9e982ef  transit.pmtiles
b10bb5cea28749d1c06b25045f2d02f8f27e19c1b48ca99d69a8cc192fb8fb5d  voirie.pmtiles
```

Elles sont identiques à celles de `Native/Aule/Resources/` — vérifiées à la copie, le
23/08/2026. `npm run check:production` compare celles du web et de l'iOS. Pour `voirie.pmtiles`, il
compare **aussi celle d'Android** : la garde a été posée en même temps que l'archive, faute de
quoi la troisième copie aurait dérivé sans que rien ne le dise — c'est déjà ce que ce relevé
écrit à la main compensait pour les deux autres fichiers.

## Pourquoi embarqués plutôt que téléchargés

Pour que la carte se peigne **sans réseau**. C'est la même raison que les styles MapLibre
d'`assets/map/` : dans un tunnel comme en zone blanche, un agent doit voir son réseau. L'index
évite en plus un aller-retour pour peindre une pastille de ligne, puisque la flotte n'annonce
pas de couleur — elle envoie un `routeId` et rien d'autre.

## Ne pas les renommer

Une copie qui change de nom est une copie qu'on ne retrouve plus dans l'autre dépôt.
