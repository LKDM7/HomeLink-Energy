# Validation exécutée

## Résultat

La compilation, les tests unitaires, les GameTests et les essais client ont été exécutés dans ce workspace. Le rapport machine [results.json](validation/results.json) contient les nombres de tests, les sommes SHA-256 et la mesure de charge extraite des journaux. Les captures sont conservées dans `docs/validation/`.

| Niveau | Exécution | Ce qui est vérifié |
|---|---|---|
| JUnit | 43 tests réussis | neuf cycles solaires, nuit/obstruction/dimension interdite, fractions, 10 cycles, saturation, transfert exact, réentrance, heure figée/sautée, équité réseau, débordement, alarmes, modèles/loot/traductions, absence de faces coplanaires superposées dans les trois batteries |
| Serveur Minecraft GameTest | 42 tests requis réussis | trois niveaux réels, panneau → batterie, verre et empilement, météo, End, sauvegarde NBT, cassage/repose, recettes, ports retirés, câbles/partition/fusion, priorités, capacités II/III, HomeCore/permissions/événements, dix régressions des emprises et câbles de surface |
| Chunk réellement déchargé | GameTest `realChunkUnloadAndDiskReloadNeverCatchUp` réussi | chunk distant chargé par la fixture, production, retrait du ticket, vérification d'absence, saut de 50 jours, rechargement depuis disque, conservation exacte du compteur et de la fraction |
| Charge | 100 panneaux III + 100 batteries, 600 ticks chacun | 60 000 ticks de panneau, 157 000 HE stockés, conservation pour chaque paire ; chronométrage réel décrit dans ENERGY_BALANCE |
| Client `fr_fr` | `runSmoke`, réussi | création de monde, 7 modèles chargés, panneau III productif, menu synchronisé, batterie chargée au-delà de 39 000 HE, fermeture à distance, captures réelles |
| Client `en_us` | `runSmoke -PsmokeLanguage=en_us`, réussi | mêmes assertions et captures, langue anglaise |
| JAR | tâche `verifyReleaseJar` | metadata, dépendance obligatoire HomeCore, ressources, absence des classes HomeCore et fixtures dans l'archive |

Les tests de cycle sont accélérés dans le modèle. Les GameTests tournent dans un processus **serveur dédié sans client**, utilisant le vrai moteur Minecraft et ses block entities, mais en mode test accéléré. Les tests client utilisent un serveur intégré. Aucun passage n'est présenté comme une partie multijoueur manuelle.

Le consommateur de vérification est exclusivement dans `src/verification`. Il ne fournit pas une preuve d'intégration avec une Quarry ou une station FarmBot.

## Corrections issues des essais

- Deuxième révision des batteries : silhouettes compactes, doubles cellules et armoire à tiroirs. Le générateur `scripts/battery-models.cjs` découpe les surfaces extérieures et supprime les faces cachées ou superposées. Un test indépendant parcourt les faces JSON effectivement livrées et refuse tout chevauchement coplanaire. Compilation, 43 tests unitaires et essai client français réussis ; capture rapprochée `battery-tiers-fr_fr.png`. Les GameTests de comportement restent ceux de la refonte précédente, les changements suivants portant uniquement sur les modèles.

- Refonte du 27/09 : dix GameTests supplémentaires couvrent les trois emprises et leurs quatre orientations, l'unicité du contrôleur et des ports, le refus d'une pose obstruée, le démontage sans duplication, l'ombre sur une partie secondaire, les six faces de pose du câble, le transport mural, les coins intérieurs/extérieurs, les plans distincts, les quantités d'items et le renouvellement des ports après rechargement.
- Les fixtures ont été espacées pour accueillir les panneaux 2×2. La fixture de chunk distant attend son démarrage effectif avant de mesurer le déchargement, au lieu de supposer que la génération asynchrone se termine en 80 ticks accélérés.
- Les captures client comprennent une scène avec les sept blocs et un câble sol/mur/plafond. Les GUI utilisent les primitives visuelles de Farm, sans dépendance au mod Farm.

- Compilation initiale réussie, puis tests réellement réexécutés au lieu de se fier à `UP-TO-DATE`.
- Migration vers HomeCore commité : correction d'un import `Component` retiré trop largement et d'une collision de nom `available()` entre le port et son bloc.
- Premier GameTest interrompu : structure de charge non encore copiée dans les ressources compilées. Structure générée puis exécution relancée.
- Deux passages échouaient sur la charge : le banc GameTest ajoutait un toit de barrières. Diagnostic `SKY_BLOCKED`, hauteur au-dessus des panneaux ; correction de la fixture par `skyAccess=true`. La règle d'exposition du mod n'a pas été assouplie.
- Référence de port retiré et double appel de tick maintenant rejetés ; compteurs saturants et source réelle du menu vérifiés.
- Répartition des faibles disponibilités corrigée pour faire tourner également l'ordre des consommateurs ; somme des disponibilités et métriques protégée contre les débordements.
- Une texture manquante concernait uniquement le consommateur de test ; sa blockstate de vérification utilise maintenant un modèle vanilla.

## Vérifications manuelles restantes et limites

- Partie multijoueur avec des clients distants sur un serveur installé à partir des JAR distribués ; longue session, lag, sauvegardes d'exploitation et autres mods.
- Validation visuelle en jeu des sept blocs, de toutes leurs orientations, des interfaces à très petite résolution et avec des packs de ressources. Les deux captures de menus français ont été inspectées ; textures provisoires.
- Nether vérifié via le modèle de dimension interdite ; End vérifié dans le moteur. Une partie manuelle dans chaque dimension personnalisée n'a pas été réalisée.
- API publique HE à convenir pour les véritables Quarry/FarmBot. Aucun convertisseur FE/RF ni injection par réflexion dans les classes de ces mods.
- Publication HomeCore des ports HE, Actions demandées, widgets Dashboard, Tasks et exigences restantes de la partie 2 à préciser ; les métriques et événements publics sont déjà testés côté serveur.
- Une machine connectée à plusieurs réseaux partage bien son stockage et ses plafonds, mais le panneau de métriques affiche actuellement le premier réseau actif. Les noms HomeNetwork identiques restent ambigus dans le bouton de sélection existant.
- Exposition éloignée mise à jour au plus tard au prochain contrôle périodique. Un stockage peut donc encore accepter quelques ticks avant la détection d'un obstacle éloigné.
- La distribution reconnaît uniquement les ports HE locaux conformes au contrat de simulation. L'intégration de ports externes doit être testée avant publication.

Les avertissements vanilla sur commandes ambiguës, atlas et shader ne constituent pas des assertions réussies ou échouées. Les résultats reposent sur les compteurs de tests et les marqueurs explicites, pas sur la seule absence de crash.
