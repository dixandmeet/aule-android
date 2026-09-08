# La messagerie — ce que l'agent lit, écrit, et ne perd pas

> **Statut** : lot 5 écrit et éprouvé le 05/09/2026. Répertoire et joignabilité le 06/09/2026.
> **Cible** : ce dépôt. Le SQL, le BFF, le push et l'application iOS sont faits —
> voir `../docs/PLAN-MESSAGERIE.md` et `../docs/CONTRAT-BFF.md` §12 pour les vingt et une routes.
> **Ce qui reste** : les pièces jointes côté écran, les membres et la création de groupe,
> le câblage de FCM — qui attend un projet Firebase —, et **le déploiement du BFF** :
> `/api/hub/**` répond 404 sur `www.aule.fr` au 06/09/2026, si bien que rien de la messagerie
> ne se vérifie encore sur l'appareil.

---

## 0. Les trois règles que rien ne doit contredire

### `canWrite` décide du composeur, jamais `kind`

Un canal Réseau est en lecture seule pour un conducteur et **ouvert à un régulateur**. Les
deux voient le même type, un seul doit voir la barre de saisie. Décider sur le type cacherait
le composeur au second, ou le montrerait au premier — qui serait refusé **après** avoir tapé
son message.

C'est la base qui calcule ce droit ; l'application le lit. La recalculer donnerait deux
réponses au même agent selon l'écran qu'il regarde.

### Un message tapé s'affiche avant d'être parti

Attendre le réseau pour montrer ce qu'on vient de taper est ce qui fait taper deux fois. La
bulle apparaît immédiatement, en attente ; la file la porte jusqu'au réseau, et l'écho du
serveur prend sa place — apparié par `clientId`, jamais par identifiant.

⚠️ **L'identifiant client se tire à la saisie, pas à l'envoi.** Le tirer à l'envoi ferait deux
messages d'un réessai : la base ne pourrait pas reconnaître le doublon.

### On n'écrit qu'à qui a accepté d'être contacté

Le répertoire montre les agents du **réseau courant**, et eux seuls — la base s'y arrête, un
client ne peut pas en sortir. Mais paraître au répertoire n'est pas être joignable : un
tête-à-tête ne s'ouvre qu'avec qui a coché « Être joignable ».

⚠️ **Ce consentement ne peut pas vivre ici.** Une garde posée dans cet écran laisserait l'iOS
ouvrir la conversation, et un appel direct au BFF avec un jeton porteur la contournerait sans
effort. C'est `hub_open_direct` qui refuse, en base
(`../supabase/migrations/20260913090000_hub_joignabilite.sql`) ; l'application ne fait que
l'annoncer, par `HubColleague.acceptsDirect`, pour ne pas laisser essayer.

⚠️ **La joignabilité garde la porte, pas le fil.** Une discussion déjà ouverte se retrouve même
si le collègue a refermé sa porte depuis : `isContactable` recopie cette règle du serveur —
non par dédoublement, mais pour ne pas griser une rangée que le geste aurait acceptée.

⚠️ **Fermé par défaut.** Au lancement, le répertoire montre donc un réseau que personne ne peut
contacter. C'est pour cela que l'interrupteur vit **dans le répertoire**, et non dans un écran
de réglages : c'est le seul endroit où un agent constate qu'il peut écrire à tout le monde sans
que personne ne puisse lui répondre. Un consentement qu'il faut aller chercher dans un menu est
un consentement que personne ne donne.

Et un collègue non joignable **reste affiché**, avec sa raison écrite. Le faire disparaître
laisserait croire qu'il n'existe pas : l'agent conclurait qu'il s'est trompé de nom, puis
appellerait la régulation.

### Ce qui attend le réseau se voit

Un tunnel, un dépôt en sous-sol, une zone blanche : ce sont les endroits d'où un conducteur
écrit. Un message muet dans une file que rien ne montre est pire qu'un échec affiché : personne
ne le renvoie, et son auteur croit avoir écrit. La liste porte donc un compteur, et chaque
bulle son état.

---

## 1. Ce qui a été écrit

| Couche | Fichiers |
|---|---|
| Modèles purs | `core/model/.../Hub.kt`, `HubMessageMerge.kt` |
| Contrats | `HubRepository`, `HubOutboxStore` dans `core/model/.../repository/Repositories.kt` |
| Réseau | `AuleEndpoints.hub*`, `AuleHttpClient.putBytes` |
| Dépôt | `data/.../aule/BffHubRepository.kt` |
| Feature | nouveau module `:feature:hub` — `HubViewModel`, `HubText`, `HubScreen`, `HubConversation`, `HubHost` |
| Application | `PreferencesHubOutboxStore`, `AuleGraph.hub`/`hubOutbox`, branche de `AuleRoot` |
| Entrées | `MapScreen.onOpenHub` (menu de la carte), `AccountMenuSheet.onOpenHub` (menu du compte) |
| Épreuves | `HubMessageMergeTest` (12), `BffHubRepositoryTest` (15), `HubViewModelTest` (11) |

Un ajout au socle réseau, réclamé par la messagerie et utile au-delà :
**`AuleHttpClient.putBytes`** — le seul appel de l'application qui ne va pas au BFF. Une pièce
jointe part **en direct vers Storage** : Vercel plafonne un corps de requête à 4,5 Mo, et un
planning photographié le dépasse.

⚠️ **`:feature:hub` ne dépend pas de `:data`.** Comme `:feature:map`, il ne connaît que les
interfaces de repository : un `@Composable` ne peut donc pas atteindre le réseau. Ce n'est pas
une règle de revue, c'est une erreur de compilation.

---

## 2. Un seul poller, deux cadences

Les faire tourner ensemble interrogerait le BFF deux fois par tour pour afficher la même
chose : une conversation ouverte porte déjà les messages que la liste résume.

| Ce qui est à l'écran | Cadence |
|---|---|
| Une conversation | 10 s |
| La liste | 15 s |

Le double en arrière-plan, comme le heartbeat du service : un téléphone en poche n'a pas
d'écran à rafraîchir. Un échec double le délai jusqu'à deux minutes, avec la gigue de
`jittered` — sans elle, toutes les instances qui traversent la même panne repartent **en
phase**, et le serveur qui se relève reçoit d'un coup tout ce qui l'attendait.

---

## 3. Le curseur du delta, et pourquoi il ne se devine pas

`?after=` rend **ce qui a changé** : les messages neufs, mais aussi les éditions, les
suppressions et les réactions sur un message déjà affiché. La base compare une date d'activité,
et non la date de création — qui, elle, ne bouge jamais.

Deux cas, et les confondre perd des messages :

1. **Tant que la page est tronquée**, on repart de `nextAfter`. C'est la seule façon d'atteindre
   ce qu'elle n'a pas rendu.
2. **Une fois complète**, on repart de l'heure du **serveur** moins cinq secondes. Ce
   recouvrement rattrape les transactions qui ont commité après avoir daté leur écriture.

⚠️ L'horloge du téléphone n'entre jamais dans ce calcul. Une date locale en avance sauterait
des messages ; en retard, elle en redemanderait des milliers.

⚠️ **Une pierre tombale retire.** Le serveur ne rend un message supprimé que pour dire qu'il
l'est : c'est le seul moyen qu'a un téléphone d'apprendre qu'il doit le retirer de l'écran.

Tout cela vit dans `HubMessageMerge`, un objet pur de `:core:model` — c'est la règle la plus
facile à casser, et la seule dont l'erreur ne se voie pas tout de suite : un doublon apparaît
une fois sur dix, à la sortie d'un tunnel.

---

## 4. L'amorçage écrit, donc il ne se rejoue pas

`POST /api/hub/bootstrap` crée les canaux Réseau et Dépôt et y inscrit l'agent. C'est la seule
route de la messagerie qui écrit sans qu'on le lui demande.

Elle est appelée **à l'ouverture de l'écran** et au retour au premier plan après une heure — le
seul moment où un changement de dépôt a pu se produire.

⚠️ Un amorçage raté **n'empêche pas de lire** : les canaux existent peut-être déjà. Le montrer
comme une erreur rendrait la messagerie inutilisable hors réseau.

---

## 5. Ce qui crie et ce qui ne crie pas

Un chargement **automatique** ne crie pas : il vieillit la liste, et l'écran montre ce qu'il a
avec un bandeau discret. Ouvrir la messagerie dans un tunnel et recevoir une alerte rouge
apprendrait à ne plus l'ouvrir.

Seul un geste **délibéré** — la traction pour rafraîchir, l'envoi, le renommage — rapporte une
erreur.

Et deux refus **retirent** la discussion de la liste au lieu d'afficher une erreur dedans :
« vous n'êtes plus membre » et « cette discussion n'existe plus ». Un agent retiré d'un groupe
pendant qu'il le lisait ne doit pas rester devant une conversation morte avec un bouton
« réessayer ». C'est `HubException.invalidatesChannel` qui fait ce partage.

---

## 6. Ce qui reste à faire

- **Les pièces jointes côté écran.** Le dépôt sait téléverser en trois temps (URL signée, envoi
  direct, déclaration) et l'épreuve le vérifie ; l'écran n'a ni sélecteur ni visionneuse.
- **Les membres et la création de groupe.** Le ViewModel porte les gestes et les épreuves les
  couvrent ; les écrans manquent. Le répertoire, lui, a le sien depuis le 06/09/2026 —
  `HubDirectory.kt` — et c'est aujourd'hui le seul chemin vers une première conversation.
- **Le répertoire contre un vrai serveur.** Tout est éprouvé sauf ce qui demande une base : la
  liste peuplée, l'ouverture d'un tête-à-tête et le refus d'un collègue injoignable ne se
  voient pas tant que `/api/hub/**` répond 404. Ce que l'appareil a montré, c'est l'écran de
  refus — et le fait qu'il ne prétend pas que le réseau est désert.
- **La joignabilité côté iOS.** `../Native` ignore encore `acceptsDirect` : un agent y verra
  ses tête-à-tête refusés sans qu'aucune phrase ne l'explique, jusqu'à ce que `HubColleague` et
  l'écran de recherche lisent le champ.
- **Le push.** Le serveur est prêt (lot 3), et le jeton d'appareil a sa route. Côté Android il
  manque le projet Firebase, `google-services.json` par flavor, `AuleMessagingService`,
  `HubNotifier` et l'ouverture du canal au tap. Voir `adr/ADR-016-notifications-poussees.md`.
- **La vérification sur le S21.** Rien de ce qui suit ne se juge sur un émulateur, et le flux de
  travail n'en utilise pas : `adb shell dumpsys notification | grep aule_pro_hub_v1`, le tap
  d'une bannière application fermée, et la rotation qui doit garder le canal ouvert.
