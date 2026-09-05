# ADR-014 — Google inscrit, il n'habilite pas

**Statut** : acceptée · **Date** : 2026-08-30

## La question

L'étape 5 de l'inscription demande une adresse, un mot de passe, sa
confirmation, puis attend une confirmation par e-mail. Cinq écrans en amont ont
déjà été remplis debout, souvent dans un dépôt. Comment offrir l'entrée par
compte Google sans que le compte créé perde ce que ces cinq écrans ont collecté,
ni ce qui décide de l'accès à Aule Pro ?

## La décision

**Le fournisseur externe fournit une identité. Il ne fournit ni métier, ni
réseau, ni habilitation.**

Trois conséquences, et ce sont elles la décision :

1. **Le parcours OAuth réutilise la plomberie PKCE des liens e-mail.** Même
   adresse de retour (`io.aule.pro://login-callback/`), même `exchangeAuthCode`,
   même deep link déjà déclaré. Ce qui change tient dans un genre de plus,
   [`AuthPkceFlow.OAUTH_SIGN_UP`](../../core/model/src/main/kotlin/io/aule/android/core/model/Auth.kt),
   écrit avec le vérifieur au moment de la demande — comme pour `RECOVERY`, et
   pour la même raison : ce qu'on a demandé, on le sait ; l'URL de retour ne
   fait que confirmer.
2. **Les métadonnées d'onboarding sont posées après coup**, par `PUT /user`, sur
   la session que l'échange vient d'ouvrir. `/authorize` n'accepte pas de `data`
   — c'est une limite de GoTrue, pas un choix. Sans cette seconde écriture, le
   back-office recevrait un compte muet : quelqu'un qui a rempli l'inscription
   en entier, et que personne ne peut valider parce que rien ne dit ce qu'il a
   demandé.
3. **L'habilitation ne bouge pas d'un pouce.** Un compte Google frais n'a ni
   fiche `drivers` ni rôle staff : `loadAccount` le refuse comme il refuse un
   compte e-mail fraîchement confirmé, avec le même message d'attente. Entrer
   par Google ne raccourcit que la création du compte, jamais la validation par
   le réseau.

## Où vit le brouillon, et pourquoi pas dans l'écran

Le brouillon est relu **par le dépôt**, depuis `RegistrationDraftStore`, et non
passé par l'écran qui a lancé le parcours.

C'est la conséquence directe du chemin emprunté : l'aller-retour passe par le
navigateur, donc par un autre processus, et le système a tout loisir de tuer
l'application pendant ce temps. Au retour, l'écran d'inscription peut n'avoir
jamais existé dans le processus qui reçoit le deep link. Le seul état qui
survive à coup sûr est celui qui est sur le disque — et il y était déjà, puisque
l'assistant persiste à chaque frappe.

Le `ViewModel` réécrit malgré tout le brouillon **avant** de rendre l'URL. Une
écriture différée d'une image suffirait à perdre la case des conditions qu'on
vient de cocher.

## Un échec de la pose ne referme pas la session

Le compte existe, la session est ouverte et écrite. La refuser laisserait un
compte Google lié à Aule que son propriétaire ne pourrait ni utiliser ni
recréer — l'adresse est prise.

L'échec est donc journalisé, et le brouillon **conservé** : l'inscription reste
reprenable. Le compte reste sans demande d'habilitation, ce que l'écran
d'habilitation dit déjà. La fenêtre est étroite — `PUT /user` suit `POST /token`
d'une fraction de seconde, sur un réseau que l'échange vient de prouver — mais
elle existe, et un silence y serait pire qu'un compte en attente.

## Un onglet de navigateur, jamais une WebView

Google refuse d'authentifier dans une WebView (`disallowed_useragent`), et la
RFC 8252 dit la même chose de tous les fournisseurs : une WebView appartient à
l'application qui l'héberge, qui pourrait donc lire le mot de passe du compte
Google frappé dedans.

Restait le choix entre un `ACTION_VIEW` nu et un onglet Custom Tabs. L'onglet
gagne pour une raison qui se voit à l'usage : il **se referme** au retour du
deep link. Un navigateur complet garde derrière l'application un onglet posé sur
l'URL de redirection, prêt à rejouer l'échange avec un code déjà consommé — et
l'écran de connexion annonce alors un refus que personne ne comprend. C'est la
seule raison d'être d'`androidx.browser` au catalogue.

## Ce qui vit hors du dépôt, et qu'aucun test ne voit

Quatre réglages serveur conditionnent ce parcours. Ils ont été posés le
30/08/2026, et aucun ne se lit dans le code :

1. **Le fournisseur activé** côté Supabase (Authentication → Providers →
   Google), avec l'identifiant et le secret d'un client OAuth Google Cloud.
   Sans cela, `/authorize` répond
   `{"error_code":"validation_failed","msg":"Unsupported provider"}` et l'onglet
   affiche cette phrase telle quelle.
2. **`https://<projet>.supabase.co/auth/v1/callback`** en URI de redirection
   autorisée côté Google, sur le client OAuth. Google ajoute alors seul le
   domaine correspondant aux *domaines autorisés* de l'écran de consentement.
3. **`io.aule.pro://login-callback/`** dans les *Redirect URLs* de Supabase.
   ⚠️ **Elle n'y était pas.** La liste était vide, et le `redirect_to` de tous
   les liens — confirmation d'inscription comprise — retombait donc en silence
   sur le *Site URL* du projet, resté à `http://localhost:3000`. Autrement dit,
   le retour dans l'application ne pouvait aboutir pour **aucun** parcours,
   bien avant qu'on parle de Google.
4. **L'écran de consentement ouvert aux comptes visés** : l'application Google
   est en état *Test*, où seuls les comptes inscrits en utilisateurs de test
   peuvent s'authentifier — cent au maximum. Ouvrir l'inscription à un réseau
   entier demande de **publier** l'application, ce qui rend obligatoires la page
   d'accueil, les règles de confidentialité et les conditions d'utilisation.

Le nom affiché sur l'écran de consentement vient de ces mêmes réglages : tant
que la page d'accueil n'est pas renseignée, Google annonce le domaine technique
du projet Supabase — `rllcdvuqduuyhdcifiwp.supabase.co` — et non « Aule Pro ».
C'est la première chose que lit quelqu'un à qui l'on demande son compte Google.

## Deux applications répondent au même lien, et le système demande

`io.aule.pro://login-callback/` est déclaré par l'application native **et** par
Flutter Pro — c'est délibéré, l'adresse est déjà dans les *Redirect URLs* du
projet et en ajouter une seconde n'aurait fait qu'une valeur de plus à tenir à
jour côté serveur. Le prix se paie au retour : Android affiche un sélecteur
« Ouvrir avec » où les deux applications portent le même nom, et se tromper
mène à celle qui n'a pas le vérifieur PKCE.

Tant que les deux cohabitent sur un même appareil, c'est un frottement réel du
parcours, et non une hypothèse : il est apparu au premier essai sur le S21.

## Ce que GoTrue fait d'un compte e-mail portant la même adresse

**Il le lie.** Mesuré le 30/08/2026 : un compte créé par mot de passe, dont
l'adresse est vérifiée, se voit rattacher l'identité Google sans qu'un second
compte apparaisse — la ligne `auth.users` porte alors `Email, Google` et garde
son identifiant et sa date de création d'origine.

Conséquence directe pour ce parcours, et elle mérite d'être connue avant d'y
revenir : **les métadonnées d'onboarding s'écrivent alors sur un compte
existant**, éventuellement déjà validé. Le rôle effectif ne bouge pas — il vit
dans `user_profiles` et `drivers`, que cette écriture ne touche pas — mais la
demande d'habilitation que lit le back-office, elle, est remplacée par celle du
brouillon.

## Ce qu'on n'a pas fait

**Aucun bouton Google sur l'écran de connexion.** Le parcours implémenté est
celui de l'inscription, et lui seul. La liaison automatique décrite ci-dessus
lève la question du doublon, mais en pose une autre : sur l'écran de connexion,
il faudrait ne **rien** poser après l'échange, alors que le même bouton, à
l'inscription, doit poser les métadonnées. Deux boutons pour un fournisseur,
donc, distingués par le genre PKCE — c'est faisable et ce n'est pas fait.
