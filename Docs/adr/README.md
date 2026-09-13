# Décisions d'architecture

Une ADR par décision **structurante** — celle dont on se demandera, dans six mois, pourquoi
elle a été prise ainsi. Pas de journal de bord : si une décision se relit dans le code sans
effort, elle n'a pas besoin d'ADR.

| | Décision | Tranche |
|---|---|---|
| [001](ADR-001-compose.md) | Kotlin + Compose, carte exceptée | L'UI est Compose ; la `MapView` reste une View |
| [002](ADR-002-maplibre-opengl.md) | `android-sdk-opengl`, pas `android-sdk` | Quel artefact MapLibre, et pourquoi OpenGL |
| [003](ADR-003-composition-root.md) | Racine de composition manuelle | Hilt plus tard, et à quelles conditions |
| [004](ADR-004-okhttp.md) | OkHttp + kotlinx.serialization | Pas Retrofit, pas Ktor |
| [005](ADR-005-mocks.md) | Le mock est impossible en production | Découpage de source set, pas garde d'exécution |
| [006](ADR-006-interpolation.md) | L'interpolation hors état Compose | `Choreographer` écrit dans MapLibre |
| [007](ADR-007-application-id.md) | `applicationId io.aule.android` | Cohabiter avec Flutter Pro, et le coût d'une bascule Play |
| [008](ADR-008-minsdk.md) | `minSdk 26` | La raison, et le coût de revenir à 24 |
| [009](ADR-009-inclinaison.md) | L'inclinaison plafonne à 60°, pas 67° | Mesure, pas supposition |
| [010](ADR-010-pas-de-material.md) | Material 3 sous identité Aule | Le comportement Android, les jetons Aule |
| [011](ADR-011-localisation.md) | Un modèle ne contient pas de phrase | Où vivent les mots adressés à l'usager |
| [012](ADR-012-favoris-locaux-d-abord.md) | Les favoris vivent sur l'appareil | Le compte rattrape, il ne commande pas |
| [013](ADR-013-biometrie-locale.md) | La biométrie est un verrou local | Elle restaure une session, elle n'authentifie pas |
| [014](ADR-014-inscription-par-fournisseur.md) | Google inscrit, il n'habilite pas | OAuth PKCE, métadonnées posées après coup |
| [015](ADR-015-couche-native-vehicules.md) | Une couche native pour les véhicules | Du C++ dans un projet Kotlin, et le repère qu'attend MapLibre |
| [018](ADR-018-conseil-deterministe.md) | Le conseil est déterministe ; Claude ne fait que le dire | Qui décide d'un itinéraire, et ce qu'un modèle de langage a le droit d'y faire |
| [019](ADR-019-contribution-affluence.md) | Quatre gardes, et pas une de moins | À quelles conditions on a le droit de collecter la position d'un voyageur |

La 006 est celle qu'il faut lire en premier si l'on touche à la carte : c'est la seule règle
du projet qui soit **invisible dans le code** et qu'un changement anodin suffise à détruire.
