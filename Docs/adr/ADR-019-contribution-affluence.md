# ADR-019 — La contribution d'affluence : quatre gardes, et pas une de moins

**Statut** : acceptée · **Date** : 2026-09-13

## La question

Montrer l'affluence d'un bus demande de savoir qui est dedans. Personne ne publie cette donnée
sur ce réseau : la seule source possible est **la position des voyageurs eux-mêmes**.

Une application de transport qui collecte des positions est, techniquement, une application de
suivi. La question n'est pas de savoir si l'on peut le faire ; c'est de savoir à quelles
conditions on a le droit de le faire, et comment ces conditions se prouvent plutôt que se
promettent.

## La décision

**Contribution facultative, anonyme, bornée à un trajet, purgée au quart d'heure.** Quatre
gardes, chacune écrite dans le code et gardée par une épreuve qu'on a vue rougir.

### 1. Pendant un trajet, et pas autrement

Deux activités donnent le droit de contribuer : un guidage en cours, ou une veille de course
armée. **Il n'existe aucun troisième cas** — la propriété qui les calcule rend `nil`, et la
politique refuse tout.

⚠️ C'est la ligne à ne pas franchir. Une troisième branche ajoutée là ferait d'Aule une
application de suivi, quel que soit le mot qu'on mettrait dessus. L'épreuve interroge la
propriété **directement**, et non le dépôt resté muet : un site d'appel ajouté sans garde ne se
verrait pas autrement.

### 2. Sans compte, sous un jeton qui tourne

Le jeton est tiré au hasard, ne dérive de rien — ni identifiant d'appareil, ni compte, ni numéro
de série — et **se renouvelle toutes les vingt-quatre heures**. En base, `owner_id` reste nul :
l'appareil n'appartient à personne.

⚠️ **C'est la rotation, et rien d'autre, qui empêche de relier deux trajets du même voyageur.**
Un jeton stable serait un identifiant de suivi permanent. Couper la contribution l'efface : le
garder ferait qu'une reprise six mois plus tard repartirait sous le même identifiant.

Le serveur en a besoin pour deux choses seulement : ne pas compter deux fois la même personne
dans un véhicule, et ne pas laisser un doigt nerveux signaler « bondé » dix fois.

### 3. Effacé en quinze minutes

Les positions sont purgées par un job déjà en place. Seul le comptage par véhicule survit.

⚠️ **Rien n'est journalisé** — ni position, ni jeton, ni ligne. Une trace de serveur survit des
semaines là où la table est purgée au quart d'heure : la journaliser annulerait la purge. En cas
de panne, on journalise le code d'erreur.

### 4. Jamais seul sur la carte

Un véhicule n'affiche son affluence qu'à partir de **deux contributeurs**. Seul, on serait
localisable, ce qui annulerait tout le reste.

⚠️ Le prix est réel : à faible adoption, la carte reste muette. C'est accepté, et c'est ce qui
justifie de garder les seuils bas — exiger vingt contributeurs rendrait la fonctionnalité
invisible, donc impossible à calibrer.

## Ce que ce n'est pas

**Un taux de remplissage.** Le serveur compte des contributeurs, pas des voyageurs : six
téléphones dans un tram de trois cents places ne disent pas qu'il est plein, ils disent qu'il
l'est *plus* que celui où personne ne contribue. C'est une comparaison, et c'est pourquoi la
sortie est un mot et jamais un pourcentage ni un nombre — un nombre rendrait les contributeurs
identifiables à quelques-uns près.

⚠️ **Le cran est décidé au serveur.** Ce socle-ci compte quatre paliers, celui d'iOS trois :
laisser chacun trancher ferait afficher deux mots différents du même bus sur les deux
téléphones.

## Ce que cette décision coûte

Une fonctionnalité qui ne démarre pas toute seule : sans contributeurs, pas d'affluence, et le
consentement par défaut à « non » garantit qu'il y en aura peu au début. On accepte une montée
lente plutôt qu'un consentement présumé.

## Voir aussi

- **Pourquoi l'écriture passe par le BFF** (`ADR-003` du socle iOS, règle commune aux deux
  plateformes) : la RLS réserve l'insertion à un appareil possédé par un compte, ce qu'un
  voyageur anonyme n'a pas, et ce qu'on ne veut pas lui demander pour dire qu'un tram est
  plein.
- [ADR-018](ADR-018-conseil-deterministe.md) — le conseil d'itinéraire, qui pèse cette affluence
  sans jamais l'afficher en chiffres.
