# ADR-016 — Les notifications poussées de la messagerie

**Statut** : acceptée · **Date** : 2026-09-04

## La question

La messagerie Aule Pro (plan : `Docs/PLAN-MESSAGERIE.md`) interroge le BFF toutes les
quinze secondes quand elle est ouverte, toutes les trente quand elle est fermée. Un
conducteur qui range son téléphone n'apprend donc rien tant qu'il ne le ressort pas. Un
appel à relève, une annonce de dépôt, une demande d'échange pour le lendemain : tout cela
attend qu'il regarde.

Comment prévenir un agent qui ne regarde pas ?

## La décision

**FCM sur Android, APNs sur iOS, une file en base, et un envoi côté serveur.**

Trois choix, chacun contre une alternative sérieuse.

### FCM plutôt qu'un canal maison

Un service de premier plan avec une connexion longue coûterait une notification permanente
(« Aule surveille les messages ») et de la batterie sur un métier qui roule huit heures.
Android tue les connexions en veille profonde ; FCM est la seule voie qui traverse Doze.

### Dans `:app`, pas dans `:data`

`:data` est une bibliothèque **JVM pure** (`aule.jvm.library`) : elle n'a pas de
`Context`, et un `FirebaseMessagingService` en est un composant Android. Le placer là
obligerait à androidiser un module que rien d'autre n'y oblige.

Conséquence : `AuleMessagingService` et `HubNotifier` vivent dans `:app`, comme
`HandoverAlertNotifier` — dont ils reprennent la forme, y compris la création du canal de
notification au démarrage plutôt qu'à la première alerte.

### Une file en base, et pas un envoi depuis le déclencheur

Écrire un message et prévenir les destinataires sont deux gestes. Les coudre ensemble
ferait dépendre l'écriture d'un message de la disponibilité d'Apple : une transaction qui
attend un serveur distant tient ses verrous, et si l'appel échoue, **le message n'est pas
écrit du tout**.

D'où `push_outbox` : la transaction pose des lignes — une écriture locale — et se contente
de réveiller la fonction d'envoi dans un bloc qui avale toute erreur. Le balayage d'une
minute rattrape ce que le réveil a manqué.

⚠️ **Un déclencheur d'instruction, pas de ligne.** Une annonce du staff sur un canal de
dépôt de 300 agents produit 300 notifications en un seul `INSERT`. Un déclencheur par ligne
ferait 300 appels HTTP sortants ; celui-ci en fait un.

## Ce qui voyage en clair chez Google et Apple

Le **nom du canal** et un **aperçu de 200 caractères**. Rien d'autre : aucune pièce
jointe, aucune coordonnée, aucun e-mail. Le titre d'un tête-à-tête est le libellé de
l'agent, produit par `hub_agent_label`, qui refuse tout ce qui ressemble à une adresse.

C'est le prix d'une bannière lisible sans déverrouiller. Une option « Nouveau message dans
<canal> » sans corps, pour les tête-à-tête, est notée en v2 ; la trancher demande de savoir
ce que les agents préfèrent, et personne ne le sait encore.

## Ce qu'il faudra vérifier sur l'appareil

Aucune de ces trois choses ne se mesure ailleurs que sur un téléphone :

1. **Le canal de notification existe et porte le bon nom.**
   `adb shell dumpsys notification | grep aule_pro_hub_v1` — un canal absent fait
   disparaître les bannières en silence, sans erreur ni journal.
2. **Taper la bannière ouvre la bonne discussion**, y compris application fermée : c'est
   `onNewIntent` qui reçoit l'extra, pas `onCreate`, et les deux chemins diffèrent.
3. **`POST_NOTIFICATIONS` est demandé** à la première ouverture du Hub, pas au lancement :
   demandée à froid, la permission est refusée par réflexe, et Android ne la redemande plus.

## Ce que cette décision n'inclut pas

- **Le silence par plage horaire.** Un conducteur de nuit ne veut pas des annonces du
  matin. La sourdine par canal existe déjà (`hub_update_membership`) ; une plage horaire
  demande de connaître le service, donc le planning structuré — c'est la v2.
- **Le regroupement par expéditeur.** Les bannières se regroupent par canal (`tag` côté
  Android, `thread-id` côté Apple), ce qui suffit tant qu'un canal est une équipe.

## Références

- Migration : `supabase/migrations/20260909090000_hub_push.sql`
- Envoi : `supabase/functions/push-dispatch/` et son README
- Épreuves : `supabase/tests/hub_push.test.sql`, `dashboard/test/push-dispatch.test.mjs`
