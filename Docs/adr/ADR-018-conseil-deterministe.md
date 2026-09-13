# ADR-018 — Le conseil est déterministe ; Claude ne fait que le dire

**Statut** : acceptée · **Date** : 2026-09-13

## La question

Le voyageur devait gagner quatre choses : un itinéraire optimisé, une vérification continue
qu'il n'en existe pas un meilleur, la détection des anomalies du réseau, et l'affluence en temps
réel à partir de la géolocalisation des voyageurs.

Toutes les quatre s'écrivent naturellement comme « demander à un modèle de langage ». Le trajet,
les horaires, les alertes et les positions tiennent dans un contexte ; un modèle sait comparer
des durées et rédiger un conseil. La tentation est réelle, et elle est mauvaise.

## La décision

**Les règles décident, Claude formule.** Scores, seuils, statuts et causes sont du code serveur
testable. Claude reçoit des faits déjà tranchés — des lignes, des minutes, une cause — et rend
**une phrase**, facultative.

### Pourquoi pas un modèle qui décide

Trois raisons, et la troisième suffirait.

**Une décision de trajet doit être reproductible.** « Pourquoi Aule m'a fait changer à
Commerce ? » a une réponse : la marge était de 90 secondes, le seuil est à 240. Une décision de
modèle n'a pas de réponse de cette nature, et ne peut donc pas être corrigée — seulement
re-promptée, ce qui est autre chose.

**Une règle se garde par une épreuve, un prompt ne se garde pas.** Chaque seuil de ce lot a son
cas, et chaque cas a été vu rougir avec le seuil déplacé. Rien d'équivalent n'existe pour une
sortie de modèle : on peut vérifier qu'elle est plausible, pas qu'elle est juste.

**Le budget est une contrainte de guidage, pas une contrainte de coût.** La veille interroge
toutes les trente secondes pendant qu'on marche vers un quai. Une décision qui dépendrait d'un
aller-retour de plusieurs secondes ne pourrait pas être bloquante, donc devrait avoir un repli
déterministe — c'est-à-dire la règle qu'on aurait voulu éviter d'écrire, écrite quand même, mais
jamais exercée.

### Ce que Claude apporte quand même

Une phrase française qui nomme la cause et la ligne : « Le C6 accuse 8 minutes de retard, le
tram 1 vous fait arriver 6 minutes plus tôt. » Les formulations du client sont correctes mais
génériques ; celle-ci est précise, et c'est ce qui fait qu'on lit un bandeau au lieu de le
balayer.

⚠️ **Un filtre déterministe relit sa sortie.** Toute ligne citée doit appartenir aux lignes
fournies, toute minute aux minutes fournies. Une phrase qui invente est jetée. Claude ne peut
donc pas nommer une ligne qui n'existe pas dans les faits — ce n'est pas une promesse de prompt,
c'est une comparaison de chaînes.

⚠️ **Une explication absente n'est pas une panne.** Sans clé d'API, au-delà du budget de 1,5
seconde, ou sur un refus, le champ est omis et le client formule lui-même. Toute la veille
fonctionne sans clé, et rien ne manque à l'écran.

⚠️ **Claude ne reçoit jamais de coordonnée ni d'identifiant.** Des lignes, des minutes, une
cause. Ce qu'on ne lui envoie pas ne peut pas fuir par lui.

## Ce que cette décision coûte

Des seuils à calibrer à la main, et un module de plus à maintenir quand le réseau change. Et un
conseil qui ne saura jamais dire « ce quai est en travaux, prenez l'autre sortie » : il ne sait
que ce que les tables savent.

## Ce qu'elle protège

La capacité de répondre « parce que » à un utilisateur qui demande pourquoi. Et l'indépendance :
le jour où la clé manque, où le fournisseur change, ou où le budget se resserre, l'application
conseille toujours.

## Voir aussi

- [ADR-011](ADR-011-localisation.md) — un modèle ne contient pas de phrase. C'est elle qui
  impose que le serveur descende des **codes**, et que la formulation du client existe même
  quand celle du serveur est là.
- [ADR-019](ADR-019-contribution-affluence.md) — la contribution d'affluence, et pourquoi elle
  ne ressemble à rien d'autre dans cette application.
