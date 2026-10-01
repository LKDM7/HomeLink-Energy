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

## 0.4.1 — reconstruction ciblée des réseaux (1er octobre 2026)

Un changement (câble posé, cassé, chargé ou déchargé, machine ajoutée ou retirée) ne
reconstruit plus que les réseaux situés à deux blocs au plus de la position modifiée,
ainsi que tout réseau qu'une nouvelle liaison fusionne avec eux. Les autres réseaux de la
dimension gardent leur identité et leurs statistiques de production : avant, poser un câble
n'importe où remettait à zéro les moyennes de tous les réseaux. `gradlew build` et
`runGameTestServer` réussissent (73 GameTests, dont deux nouveaux : réseau voisin intact,
fusion puis séparation sans câble fantôme).

## Hydro 0.4.0 — passage du 1er octobre 2026

Commandes exécutées dans ce workspace (Windows 11, Ryzen 16 threads, JDK 21.0.11, HomeCore 1.12.0 en build composite depuis `../HomeCore`, qui contient des modifications locales non commitées) :

| Commande | Résultat |
|---|---|
| `gradlew build test` avant toute modification | réussi (référence) |
| `gradlew build test --rerun-tasks` | réussi, **81 tests JUnit, 0 échec**, dont 25 Hydro |
| `gradlew runGameTestServer` | **70/70 GameTests requis réussis**, dont 9 Hydro (serveur dédié, sans classes client) |
| `gradlew runSmoke` (`fr_fr`) et `runSmoke -PsmokeLanguage=en_us` | réussis, marqueur `ENERGY_SMOKE_OK … hydroFlow=true hydroMenus=true hydroWaterZone=true hydroOutlet=true` |
| `node scripts/audit-models.cjs` / `audit-assemblies.cjs` | 100 modèles, 0 face coplanaire superposée ; 388 assemblages (dont pompes, turbine à 36 phases rotor/volets, 64 combinaisons de conduites), 0 défaut |
| `gradlew releaseBundle` | `build/release/homelink_energy-0.4.0.jar` (SHA-256 `d0236bd8…2eaf`) + `homecore-1.12.0.jar` |

Couverture :

- **JUnit** : 4 000 / 12 000 / 24 000 HE par période (Pompe I : 3 999, reliquat < 1 HE), seuils 0 / 24 / 25 / 50 / 100 %, plafond à 6 DH/t avec 2 et 4 Pompes III, dix périodes sans dérive, indépendance de l'heure de départ, pas de rattrapage après un trou de 50 000 ticks, allocation unique par tick quel que soit l'ordre, tampon plein = pertes comptées, turbine coupée qui se vide sans générer, configuration aberrante (NaN, zéros, négatifs) assainie ; fenêtres d'eau (tailles 18/50/147, centrage pair/impair, quatre orientations, source isolée, mur, admission obstruée, lac plus grand que la fenêtre compté une fois, zone déchargée = inconnue, retrait/remise) ; graphe (pompe → conduites → turbine dans les deux sens, mauvaise face, branches et boucle, 5 pompes, 700 conduites, deux turbines, sans pompe, cellule déchargée, deux circuits voisins indépendants, budget de 128 visites repris sur plusieurs ticks) ; réseau HE Solar + Wind + Hydro + batterie + consommateur sur 48 000 ticks avec conservation exacte et somme des catégories = débit livré.
- **GameTests** : quatre machines × quatre orientations à cheval sur quatre chunks, un seul maître et un seul port HE, casse de chacune des 8 parties (un seul item), pose refusée sur case occupée et au-dessus de la hauteur maximale sans consommer l'item, vraie eau (147 sources) → Pompe III → 16 conduites → turbine → consommateur, aucune source retirée, aucun bloc posé devant, devices HomeCore `hydro_pump`/`hydro_turbine`, `Switchable` → `DISABLED` et arrêt de la génération, rejet bloqué puis reprise automatique, deux turbines arrêtées puis circuit restauré en coupant la branche, conduites jamais reliées aux câbles ni aux faces non-ports, sauvegarde NBT (UUID, tampon, fraction, compteurs, période), explosion sans demi-turbine ni duplication.
- **Client réel** : captures `docs/validation/hydro/` (façade avec volets ouverts et nappe, arrière avec grille et rotor, pompes, GUI turbine/pompe, zone d'eau, rejet bloqué et marqueur d'obstruction), en français et en anglais ; valeurs des menus vérifiées par le script (6 DH/t, 1 HE/t, 147 sources).
- **Charge logique** (`build/reports/hydro-logical-load.txt`) : 100 circuits, 400 pompes, 5 300 conduites construits en 12,9 ms (5 300 visites) ; allocation + production de 100 circuits pendant 24 000 ticks : 371 ms, soit ≈ 0,15 µs par circuit et par tick, préchauffage JVM compris, sans monde. Ce n'est ni un TPS ni une mesure en partie.

Correctif après retour en jeu : le réseau de câbles interrogeait le port HE sur le bloc maître de chaque machine ; la turbine n'expose son port que sur la case (1, 0, 1), donc seuls les blocs collés au raccord recevaient de l'énergie. `EnergyNetworks` interroge désormais la case et la face réellement touchées par le câble (le maître reste la clé de dédoublonnage). La GameTest `turbineFeedsCopperCables` (turbine → 4 câbles → consommateur) échoue avec l'ancien code (0 HE reçu) et passe avec le correctif.

Non réalisé / restant manuel :

- Essai en jeu de charge (nombreuses turbines réelles avec tick times) : non mesuré.
- Deux clients simultanés sur un serveur dédié installé depuis les JAR : non réalisé (synchronisation testée par un seul client intégré).
- Déchargement réel d'un chunk au milieu d'un circuit et redémarrage du serveur en cours de production : couverts par la logique (`HydroGraphTest`, `HydroModelTest`, sauvegarde NBT), pas par une fixture de chunk déchargé dédiée à Hydro.
- Permissions HomeCore révoquées et rattachement par le HomeLink Connector : chemins communs `EnergyDevice`/`EnergyHomeCore` déjà testés pour Solar/Wind/Battery, non rejoués spécifiquement pour Hydro.
- Intégration Dashboard agrégée : non validée, le checkout local ne contient pas `EnergyBalance.java` (voir `docs/HYDRO.md`).
- Sons : sons vanilla réutilisés, écoutés uniquement via le déroulé automatique, pas de revue sonore manuelle.
- CI GitHub : les nouveaux tests s'exécutent via les étapes existantes (`build test`, `runGameTestServer`) ; aucune exécution distante n'a eu lieu. La CI résout HomeCore publié (`-PuseLocalDependencies=false`) : la version 1.12.0 doit être publiée pour qu'elle passe.
