# ADR-012 — Les adresses favorites vivent sur l'appareil, le compte les rattrape

**Statut** : acceptée · **Date** : 2026-08-26

## La question

Un domicile enregistré doit se retrouver sur le téléphone d'à côté. Qui, du
disque ou du serveur, dit la vérité ?

## La décision

**L'appareil décide, le compte rattrape.**

Les favoris sont lus de `SharedPreferences`, de façon **synchrone**, à la
construction de `SavedPlacesModel` : ils sont à l'écran avant la première image.
La table `user_saved_places` (Supabase) sert à les retrouver ailleurs, jamais à
autoriser leur affichage.

Trois conséquences, et ce sont elles la décision :

1. **Rien n'attend le réseau.** Sans session, sans connexion, au premier
   lancement après une réinstallation : la rangée de raccourcis fonctionne.
2. **La fusion précède l'écriture, toujours** — `mergeSavedPlaces`, à
   l'horodatage. Pousser le local tel quel écraserait ce que l'autre appareil a
   enregistré pendant qu'on était hors ligne.
3. **Une synchronisation ratée n'est pas un incident.** Elle est journalisée, et
   rien d'autre : les favoris locaux sont déjà corrects à l'écran, et un bandeau
   rouge par-dessus n'apprendrait rien.

## Pourquoi, et contre quoi

Le Flutter avait tranché l'inverse, et l'argument était bon : *« Un domicile est
une donnée personnelle qui n'a rien à faire dans une base d'exploitation »*
(`SAE/lib/services/saved_places_store.dart`). Tout restait sur l'appareil, et
rien ne suivait le compte.

Ce qui a changé n'est pas l'argument mais sa portée. Le domicile ne descend
**pas** dans les tables d'exploitation : `user_saved_places` est une table à
part, dont aucune vue, aucun rôle de régulation et aucune fonction
`SECURITY DEFINER` ne s'approche — quatre politiques RLS sur `auth.uid()`, et
c'est tout. La séparation se constate en lisant la migration, elle ne se promet
pas.

Le choix inverse — le serveur comme source de vérité — a été écarté sur un cas
précis : un parking souterrain de dépôt. Aucune donnée, donc aucun raccourci,
donc la fonctionnalité disparaît à l'endroit exact où l'on prend son service.

## Les pierres tombales

Une suppression **laisse une trace datée** (`deleted_at`) au lieu de retirer la
ligne, des deux côtés. Sans elle, un favori effacé revient au prochain
démarrage : le serveur, qui n'a rien appris, le renvoie comme une nouveauté.

L'entrée est vidée de ce qu'elle portait — nom, adresse, coordonnées. Garder
l'adresse d'un domicile qu'on vient d'effacer serait garder précisément ce qu'on
a demandé d'oublier. Elle disparaît une fois la suppression connue des deux
côtés (`pruneSavedTombstones`), et le serveur purge les siennes à trente jours.

## Ce que la base tient, et que le client ne peut pas tenir

L'unicité de `home` et de `work`. Deux appareils hors ligne peuvent chacun
enregistrer un domicile ; c'est un index partiel qui refuse le second, et non une
règle client que le second appareil n'aurait aucun moyen d'appliquer. Le
perdant **devient un lieu personnalisé** plutôt que d'être écarté : quelqu'un a
saisi cette adresse à la main.

## Ce que ça coûte

Deux horodatages par entrée (`created_at` fixe l'ordre, `updated_at` arbitre la
fusion) et un identifiant créé par le **client**, avant tout aller-retour —
c'est la clé sur laquelle les deux côtés s'apparient. Un `bigserial` posé par la
base ne pourrait pas jouer ce rôle : deux appareils ne sauraient pas qu'ils
parlent de la même adresse.

## Où c'est écrit

| | |
|---|---|
| Les règles, pures | `core/model/…/SavedPlace.kt` — vérifiées sans disque ni réseau |
| L'ordre des opérations | `feature/map/…/SavedPlacesModel.kt` |
| Le disque | `app/…/search/PreferencesSavedPlacesStore.kt` |
| Le compte | `data/…/aule/SupabaseSavedPlaceRepository.kt` |
| La table | `~/Aule/supabase/migrations/20260826090000_user_saved_places.sql` |
