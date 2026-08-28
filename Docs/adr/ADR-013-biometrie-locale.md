# ADR-013 — La biométrie est un verrou local, pas une authentification

**Statut** : acceptée · **Date** : 2026-08-28

## La question

Un conducteur ressaisit son mot de passe à chaque lancement, alors que sa
session, elle, survit très bien à une fermeture. Comment raccourcir ce geste
sans déplacer la frontière de ce qui autorise l'accès ?

## La décision

**La biométrie autorise la *restauration* d'une session déjà valide. Elle
n'authentifie rien.**

Supabase reste seul à dire qui entre. Une empreinte reconnue ne produit ni
jeton, ni session, ni droit : elle lève un verrou posé sur cet appareil, devant
une session que `restore()` a déjà rendue. Trois conséquences, et ce sont elles
la décision :

1. **L'ordre est : restaurer, puis demander l'empreinte.** `restore()` sait
   distinguer une session révoquée d'une panne de réseau (`REVOKING_FAILURES`,
   dans `SupabaseAuthRepository`). L'inverse — l'empreinte d'abord — ferait
   poser un doigt pour, parfois, éjecter aussitôt vers l'écran de connexion.
2. **Aucune branche ne mène à une impasse.** Refus, annulation, verrou du
   capteur, clé invalidée : toutes retombent sur le formulaire. C'est ce que
   garantit `isSignedIn = false` pendant l'attente — la chaîne de `AuleRoot` y
   revient d'elle-même, sans qu'aucune sortie ait à être écrite.
3. **Ce qu'on déchiffre n'a aucune valeur.** Un marqueur de trente-deux octets
   tiré au sort. Ce qui compte n'est pas son contenu mais l'impossibilité de le
   rouvrir sans le matériel.

## Pourquoi un `CryptoObject`, et pas un dialogue seul

`BiometricPrompt` sans chiffrement rappelle « c'est bon » — une promesse qui
n'engage que le code qui l'écoute. Avec un `CryptoObject`, la clé AES-GCM vit
dans le Keystore, naît avec `setUserAuthenticationRequired(true)`, et le
`Cipher` n'existe qu'après une empreinte reconnue. Le déchiffrement **est** la
preuve, et elle est matérielle.

D'où le fait que la vraie vérification soit `SecureBlob.open(...)` et non le
rappel `onAuthenticationSucceeded` : c'est l'appel qui échouerait si la
biométrie n'avait pas réellement eu lieu.

## Ce qui n'est pas durci, et pourquoi c'est délibéré

**Le jeton de session continue de vivre en clair** dans
`PreferencesAuthSessionStore`, exactement comme avant. Seul le marqueur
biométrique est chiffré.

Ce n'est pas un oubli, c'est un périmètre. Coupler la session à la clé
biométrique la ferait disparaître au premier changement d'empreinte —
`setInvalidatedByBiometricEnrollment(true)` détruit la clé — et transformerait
un confort en cause de déconnexion. Durcir le dépôt de session est un chantier
distinct, qui ne doit pas dépendre de celui-ci.

## `BIOMETRIC_STRONG` seul, jamais `DEVICE_CREDENTIAL`

Trois raisons, dans l'ordre où elles pèsent :

- `DEVICE_CREDENTIAL` remplacerait l'empreinte par le code de verrouillage du
  téléphone — précisément ce que voit par-dessus l'épaule quiconque regarde son
  voisin déverrouiller son écran ;
- lui seul peut porter une clé Keystore : sans lui, plus de `CryptoObject`,
  donc plus de preuve ;
- Android refuse un bouton négatif personnalisé quand `DEVICE_CREDENTIAL` est
  demandé. Or c'est ce bouton — « Se connecter autrement » — qui porte tout le
  repli.

## Un alias de clé fixe, une garde par compte

La clé du Keystore porte un alias **unique** (`…security.biometric-gate`), et
non un par utilisateur : il n'y a jamais qu'une session locale à la fois, et un
nom de clé n'est pas un contrôle d'accès.

Ce qui garde l'identité, c'est `BiometricEnrollmentStore.read(userId)`, qui ne
rend rien si l'entrée appartient à un autre compte. **Ce point mérite d'être lu
deux fois** : un poste de conduite se partage, et fermer l'application n'est pas
se déconnecter. Un conducteur qui rend le téléphone en fin de service laisse
derrière lui une session ouverte. Ce n'est donc pas une impossibilité de changer
de compte qui protège son collègue — il n'y en a aucune — c'est cette
comparaison-là. Même raisonnement que les favoris (ADR-012) et que
`AgentAccessStore`.

## Un module `core/security`, et non `feature/auth`

`BiometricPrompt` et le Keystore sont de l'Android pur, sans une ligne
d'interface. Les mettre dans `:feature:auth` mélangerait un sous-système de
sécurité aux écrans qui l'emploient, et le rendrait invisible depuis ailleurs le
jour où un second appelant se présente.

`:core:security` ne dépend que de `:core:model` et `:core:common`. Il ne voit ni
le réseau ni `:data` : la règle qui rend impossible un appel réseau depuis un
`@Composable` reste entière. Même découpage que `:core:location`, pour la même
raison.

**Aucune de ses classes ne retient d'`Activity`.** Le coffre et le dialogue
vivent sur `AuleGraph` et sont consommés par les Composables ; seul le dépôt —
qui ne touche que des préférences — entre dans `AuthViewModel`. Un `ViewModel`
survit aux recréations de configuration : lui confier une activité ferait fuir
une fenêtre entière à chaque rotation.

## Ce que ça coûte

`MainActivity` hérite désormais de `FragmentActivity` et non de
`ComponentActivity` — `BiometricPrompt` l'exige comme hôte. La première hérite
de la seconde, donc `setContent`, `enableEdgeToEdge` et `onNewIntent` ne
changent pas. **Aucun fragment n'est créé pour autant**, et il ne faut pas y
lire une ouverture : l'application reste une activité unique et du Compose
(ADR-001).

`androidx.biometric` traîne par ailleurs `appcompat` en dépendance d'exécution.
Rien ne l'importe ; elle ne pèse que dans l'APK.

## Une clé invalidée n'est pas une erreur du dialogue

`KeyPermanentlyInvalidatedException` est levée par `Cipher.init`, donc **avant**
que le dialogue s'affiche. L'attendre dans le rappel de `BiometricPrompt`
reviendrait à l'attendre là où elle ne passe jamais, et l'écran resterait devant
un dialogue qui ne s'ouvre pas. C'est pour cela que `BiometricFailureKind`
possède un genre `KEY_INVALIDATED` qu'aucun code d'erreur Android ne produit —
un test le fige.

## Où c'est écrit

| | |
|---|---|
| Le schéma cryptographique | `core/security/…/BiometricKeyVault.kt` |
| Le scellage, vérifiable sans Keystore | `core/security/…/SecureBlob.kt` |
| La séquence d'activation, partagée | `core/security/…/BiometricEnrollmentFlow.kt` |
| La table des refus, pure | `core/security/…/BiometricFailureKind.kt` |
| Le contrat et la garde d'identité | `core/model/…/repository/Repositories.kt` |
| Le dépôt | `app/…/auth/PreferencesBiometricEnrollmentStore.kt` |
| L'état, et ce qu'il efface | `feature/auth/…/AuthViewModel.kt` |
| Les deux écrans | `feature/auth/…/BiometricGate.kt` |
