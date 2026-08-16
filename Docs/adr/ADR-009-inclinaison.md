# ADR-009 — L'inclinaison plafonne à 60°, pas 67°

**Statut** : acceptée · **Date** : 2026-08-16

## La question

Le proto iOS incline à 67°. Android peut-il suivre ?

## La décision

**Non, pas sans forker MapLibre.** `MapLibreConstants.MAXIMUM_PITCH` vaut `60.0f`,
dans le cœur du moteur Android, pas dans un greffon. `setMaxPitchPreference(67)`
ne lève pas : il refuse en silence et laisse la valeur précédente.

On **demande** 67° — la valeur iOS — et on **relève ce qu'on obtient**, journalisé
au montage. `NavigationCamera` reste identique aux trois plateformes ; l'écrêtage
se fait à l'application.

Mesuré sur le S21 le 16/08 : `59.999…°`.

## Conséquence produit

Le profil `navigation` glisse de 58,5° à 60° au lieu de 58,5° → 67°, soit une
rampe presque plate. Le sentiment de vitesse repose donc sur le **zoom seul**
(17,3 → 18,0). Ce n'est pas un défaut à maquiller : c'est une donnée de plateforme.

Relever le plafond exigerait un fork de MapLibre Android : hors périmètre.
