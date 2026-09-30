# Tuiles et index du réseau

Trois fichiers, **copiés** et jamais édités à la main.

| Fichier | Taille | Ce que c'est |
|---|---|---|
| `transit-lines-index.json` | 23 Ko | L'inventaire des lignes : indice, couleur GTFS, mode, réseau, terminus, cadre du tracé. |
| `transit-v2.pmtiles` | 2,8 Mo | La **géométrie Aule** des tracés (couche `transit_routes`) : un axe par ligne jusqu'à z15, le tracé de chaque sens au-delà, couleurs jour et nuit — 13 567 entités. |
| `voirie.pmtiles` | 1,6 Mo | Le référentiel de voirie de Nantes Métropole — 40 496 tronçons, avec la largeur mesurée de chaque chaussée et son statut. |

## D'où ils viennent

`dashboard/tools/tiles/build-transit.sh` produit l'index, depuis le GTFS et OpenStreetMap ;
`dashboard/tools/geometry/` produit `transit-v2.pmtiles` (chapitre 14 de la carte web, qui
remplace depuis le 30/09/2026 l'ancienne `transit.pmtiles`) ; `build-voirie.sh` produit le troisième depuis le jeu ouvert
« Tronçons des voies de Nantes Métropole » (data.nantesmetropole.fr, ODbL). La source de vérité est `dashboard/public/tiles/` ; l'app iOS en garde la même
copie dans `Native/Aule/Resources/`.

Trois copies peuvent diverger en silence. Les empreintes au moment de la copie :

```
0f34c58fb02a0d108bd2e1e13a1b8a8082e92104385bf6ace46a3020963a4a66  transit-lines-index.json
9a19e532be44c542434c8a7f7873018b1f8f5a9f4fdc5280091d6d67705a60e1  transit-v2.pmtiles (30/09/2026)
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
