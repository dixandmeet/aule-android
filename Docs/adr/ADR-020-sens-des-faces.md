# ADR-020 — Le sens des faces, et la plage du nuancier

**Statut** : acceptée · **Date** : 2026-09-16 · **Complète** : [ADR-017](ADR-017-eclairage-des-vehicules.md)

## Ce qui n'allait pas

« Les véhicules semblent ne pas se déplacer sur les rails. Ils ont l'air retournés, ou de se
déplacer sur le dos. Je n'ai pas l'impression qu'ils sont en 3D réelle. »

Le signalement était juste, et la cause n'était ni la palette, ni la lumière, ni les modèles :
**le rendu peignait l'intérieur des caisses.**

## La décision

**`glFrontFace(GL_CCW)`, et non `GL_CW`.**

Le raisonnement qui avait mené à `GL_CW` n'est vrai qu'à moitié. Notre `sceneToProjection`
retourne bien l'axe nord-sud — le nord de la scène va vers le sud de mercator —, et une
réflexion inverse le sens des triangles. Mais la matrice de MapLibre en porte une **seconde** :
le mercator descend vers le sud, l'espace de découpe monte vers le haut. Deux réflexions se
composent en une rotation, et le sens direct de glTF traverse donc les deux intact.

Sous la mauvaise convention, `glCullFace(GL_BACK)` écartait les faces **extérieures**. On voyait
la doublure : un toit dont la normale pointe vers le sol, donc éclairé à l'ambiante du bitume et
sans un rayon de soleil — plus sombre que les flancs, qui eux restaient à peu près justes. C'est
cette inversion que l'œil lit comme un véhicule couché sur le dos, et c'est aussi pourquoi les
caisses semblaient plates : l'intérieur d'une boîte n'a pas de modelé cohérent.

## Comment on l'a trouvé, puisque c'est la partie utile

Quatre hypothèses ont été éliminées par la mesure avant la bonne :

1. **Les modèles seraient retournés.** Faux : mis aux normes, la face la plus haute des deux
   fichiers porte la livrée et la plus basse le sombre. Les roues du bus sont en bas, la cabine
   du tram en avant — deux épreuves le tenaient déjà.
2. **L'ombrage serait trop plat.** Ce fut vrai un moment, et pour une autre raison — voir la
   plage, plus bas — mais l'écart toit/flanc mesuré à l'écran (1 %) ne correspondait à aucune
   valeur du nuancier.
3. **L'enroulement du pack serait incohérent.** Faux, et vérifié triangle par triangle : sur
   les 1 218 triangles des deux modèles, l'enroulement et les normales du fichier sont d'accord
   **1 218 fois sur 1 218**.
4. **Ce seraient les couleurs de pièce.** Non : aucune combinaison de palette ne rendait les
   valeurs relevées à l'écran.

Ce qui a tranché est une **sonde jetable dans le nuancier** : remplacer la couleur de sortie par
`N · 0,5 + 0,5`, puis par `vec3(ndl, ambiante, albédo)`. La première a montré une grande surface
à `N = (0, 0, −1)` — 435 pixels de « toit » regardant le sol — pendant que les flancs rendaient
`(+0,69, +0,72, 0)` et `(−0,72, +0,69, 0)`, parfaitement justes. La seconde a confirmé le
diagnostic : sur cette face, `ndl = 0` et l'ambiante était celle de la chaussée.

**Aucune épreuve JVM ne pouvait l'attraper.** Le maillage était juste ; c'est le pilote qui
choisissait l'autre côté. `MeshStandardizerTest` le dit désormais en toutes lettres, pour que
personne ne cherche là.

## Ce qui change en plus, et pourquoi

Trois réglages du nuancier avaient été choisis **en regardant l'intérieur des caisses** — où il
n'y a rien à voir sans forcer. Le sens des faces rétabli, ils forçaient :

| | avant | après | ce que ça donne |
|---|---|---|---|
| Éclat de carrosserie | `pow(ndh, 40) · 0,30` | `pow(ndh, 120) · 0,12` | la livrée garde sa teinte au lieu de virer menthe |
| Éclat de vitrage | `· 0,8` | `· 0,35` | une baie sombre au lieu d'un trou gris clair |
| Reflet de vitrage | `0,24 + 0,55·F`, ciel × 1,25 | `0,18 + 0,30·F`, ciel × 1,0 | le glissement du reflet reste visible, la caisse n'est plus percée |

Et deux corrections qui ne doivent rien à la caméra :

- **La nuance de jupe et l'occlusion de contact se multipliaient.** Deux facteurs qui disent la
  même chose — « cette pièce est à l'ombre de sa propre caisse » — donnaient 0,78 × 0,82 = 0,64
  avant l'ambiante, soit 0,45 de la livrée pour un bas de caisse quand le toit en rendait 1,00.
  À 0,88 × 0,88, il rend 0,54 : une jupe, et non un châssis noir.
- **Le retrait du théorique ne s'applique plus aux modèles.** `GHOST_MIX` vaut 0,28 de blanc :
  juste sur un aplat plat, délavant sur un volume éclairé — la livrée du tram passait à
  `#69B8A4` avant d'avoir reçu un rayon, sur une flotte du soir presque entièrement théorique.
  La distinction reste portée par le glyphe plat, par le registre de la pastille et par la fiche.

Enfin, le matériau `Black` du tram **n'est pas son bogie** : mesuré, il couvre 79 m² dont 15 m²
de toit, sur toute la largeur. Le ranger au bas de caisse appliquait à un toit une nuance
réservée aux panneaux bas. C'est de la carrosserie.

## Où c'est vérifié

| | |
|---|---|
| Sens des faces | à l'œil, sur le S21 — aucune épreuve ne peut le voir |
| Plage du nuancier | toit à `#309F85` mesuré pour `#2E9E84` calculé, en plein jour |
| Orientation du maillage | `MeshStandardizerTest.aucune face haute ne regarde le sol` |
| Cotes, sens de marche, palette | les huit autres épreuves du même fichier |
