# Validation éolienne — 27 septembre 2026

Les deux parties du cahier des charges ont été traitées dans le même projet. Minecraft 1.21.1, NeoForge 21.1.251, Java 21 et HomeCore 1.7.0 restent inchangés. Le checkout HomeCore fixé est intact. Le dépôt de travail contenait déjà de nombreux fichiers non suivis et des modifications indexées : aucune réinitialisation, aucun commit ni suppression de ces travaux.

## Architecture livrée

- `WindState` : un état par ServerLevel, cible pseudo-aléatoire déterministe, approche bornée et tendance dérivée ; horloge de ticks simulés indépendante de `dayTime`. `WindSavedData` persiste courant, cible, horloge, prochain changement et état du générateur aléatoire.
- `WindTurbineCore` : calcul de production et hystérésis, compteurs exacts des HE acceptés/rejetés, période du vent. Le solaire et l'éolien partagent `FractionalEnergy`, `EnergyStore`, `HePort`, `EnergyTransfer`, les câbles, batteries et `EnergyNetworks` existants. Aucun réseau éolien séparé.
- `WindTurbineBlockEntity` : autorité serveur, sorties latérales/inférieure, export direct sous le bloc, caches et sauvegarde. Ni production déchargée, ni rattrapage, ni NBT énergétique dans l'objet cassé. L'identité HomeCore est héritée de `EnergyDeviceBlockEntity`.
- `RotorArea` / `WindClearance` : rectangle vertical 3/5/7 blocs et un bloc d'épaisseur, à une case devant la tour. Les buckets de chunks indexent les turbines concernées par une modification ; pose/cassage/notifications et changements de chunks invalident le cache. Contrôle périodique de sécurité de 100 ticks, décalage initial réparti. Un chunk de rotor absent bloque la production, sans le charger. Le ciel utilise la heightmap WORLD_SURFACE au hub, avec exposition stricte, verre compris.
- `WindDevice` : un type `homelink_energy:wind_turbine`, métriques typées publiques HomeCore, unités HE / HE/t / % / HE par 24 000 ticks, événements sur transitions. La reprise d'un appareil initialise sa référence d'événements sans réémettre un état déjà présent. Le vent insuffisant reste ONLINE et son arrêt est INFO. Aucun abonnement de turbine à nettoyer : publication uniquement, désinscription du device au déchargement.
- `EnergySourceType` : SOLAR / WIND / OTHER. La production réseau existante mesure le débit réellement extrait des producteurs ; les contributions par catégorie le décomposent sans ajout de production fictive. Les batteries exposent ces mesures à HomeCore. Les compteurs individuels mesurent la génération acceptée dans leurs buffers : ils peuvent donc différer temporairement du débit livré.
- Client : modèles JSON natifs et textures vanilla existantes, trois nacelles et largeurs de mâts, rotor à trois pales, interpolation exponentielle accélération/freinage. Les packets ne transportent qu'une vitesse cible, à cadence normale d'une seconde si elle change sensiblement, ou lors d'une transition. Aucun angle réseau. Renderer limité à 96 blocs avec bounding box englobant tour et rotor, cache faible de mouvement ; les turbines hors frustum ne sont pas animées par le renderer.
- GUI dans le thème existant, menus validés côté serveur, contours temporaires de 10 secondes. Le premier obstacle vient uniquement du menu serveur ; aucune requête permettant au client de choisir une position serveur ou une valeur HE.

## Commandes réellement exécutées

Depuis `C:\Users\LKDM-\HomeLinkEnergy` avec PowerShell :

```powershell
.\gradlew.bat build test --console=plain
.\gradlew.bat test --rerun-tasks --console=plain
.\gradlew.bat runGameTestServer --console=plain
.\gradlew.bat build test --console=plain
.\gradlew.bat runSmoke --console=plain
.\gradlew.bat runSmoke -PsmokeLanguage=en_us --console=plain
.\gradlew.bat build test releaseBundle --console=plain
```

La commande `build test` a été répétée après les lots successifs ; ses journaux sont `build/wind-phase-1.log` à `wind-phase-6.log`, puis `wind-simulations.log` et `wind-release.log`. Les lots ont intégré ensemble certaines phases dépendantes, notamment les registres, entités et appareils HomeCore. Les GameTests ont été relancés après les nouveaux scénarios (`wind-gametest.log`, `wind-final-gametest.log`). Le client FR a été relancé après les dernières modifications de modèles/libellés (`wind-final-client-fr.log`).

Inspection et vérification : `git status --short`, `git -C .dependencies/HomeCore status --short`, `rg --files`, `rg -n`, lectures PowerShell `Get-Content` des sources/API/build, `Get-FileHash`, lecture des sources NeoForge/Minecraft dans leurs JAR avec `System.IO.Compression.ZipFile`, inspection des PNG via l'outil de visualisation. L'ouverture anonyme du dépôt officiel HomeCore a retourné 404 ; l'audit utilise les sources réellement liées. `python --version` n'était pas disponible ; aucun traitement ne repose sur Python. Une lecture PowerShell utilisant une syntaxe d'accolades de shell Unix et une substitution contenant une apostrophe typographique ont échoué ; les lectures et modifications concernées ont ensuite été effectuées correctement.

## Résultats automatisés

- État initial : build réussi, **43 tests JUnit réellement réexécutés**, **42 GameTests** réussis. Aucune régression initiale trouvée.
- Final : **52 tests JUnit**, aucune erreur/échec/ignoré. Mathématiques nominales, fractions, overflow, cut-in 12/14/16/20/13 %, six bornes d'altitude, météo exclusive, plafond 150 %, plein tampon, absence de rattrapage, simulation longue, réseau mixte, ressources FR/EN et références d'assets.
- **49 GameTests sur serveur dédié**, tous réussis. Comprend les 42 tests antérieurs et sept scénarios éoliens : trois rotors avec bloc ajouté/retiré, toit et rotation de facing ; transitions HomeCore sans répétition ; Wind III + Solar III + câble + Battery III avec conservation ; sauvegarde identité/buffer/fraction et anti-double-tick ; déchargement réel puis relecture du chunk disque ; 100 turbines actives ; Nether/End à zéro et SavedData réouvert depuis un cache de disque neuf ; orage à haute altitude et vraie métrique HomeCore à 150 %.
- Les recettes sont chargées et assemblées par le RecipeManager Minecraft avec les véritables composants HomeCore. Les recettes II et III demandent explicitement le tier précédent. Loot sans copie du buffer.
- Client Minecraft réel : scénarios automatiques FR et EN réussis. Dix modèles d'objets chargés, modèles de rotor chargés, valeurs de menus solaires/batteries/éoliens vérifiées, vitesse serveur reçue, boutons de contours actionnés, obstacle détecté avec débit nul. Captures examinées : modèles, rotation, GUI, zone et obstacle. Aucun missing model/texture détecté ; avertissements vanilla habituels de sons/shader sans lien avec les assets éoliens.
- Build final et contrôle du JAR réussis : HomeCore et code de vérification exclus du JAR Energy, dépendance et ressources requises présentes. `build/release` contient les deux JAR distincts et les documents.

## Mesures

Environnement : Windows 11, AMD Ryzen 7 5800X (8 cœurs / 16 processeurs logiques), Java 21.0.11. Mesures locales, JVM non isolée et warmup inclus ; ce ne sont ni des garanties TPS ni des mesures FPS.

GameTest de 100 turbines I au-dessus de 100 batteries I, 600 ticks chacune, vent fixé à 100 %, clair et nuit figée, HomeCore actif : **60 000 ticks turbine**, **62,5715 ms cumulées**, soit **1,04286 µs par turbine/tick** et environ **0,1043 ms pour les 100 par tick**. **600 scans** et **100 mises à jour de vitesse**, **5 900 HE** stockés, conservation contrôlée sur chaque paire. Le chronomètre couvre le tick turbine et son export direct, pas le tick serveur entier. Le compte de mises à jour n'est pas une mesure des octets réseau ou du nombre de destinataires.

Simulation logique du même moteur et du distributeur, une WindState partagée, une destination par turbine, 10 000 ticks :

| Turbines | Temps total | Temps par tick global | Octets alloués sur le thread |
|---|---:|---:|---:|
| 100 | 162,275 ms | 0,016227 ms | 19 830 640 |
| 500 | 513,789 ms | 0,051379 ms | 95 014 640 |

Les allocations incluent le contrat de transfert et son garde ThreadLocal ; le test ne mesure pas les allocations du renderer ou du serveur complet. La distribution utilise désormais un curseur continu de sources, au maximum quatre passes linéaires. Les résultats ne révèlent pas de croissance quadratique évidente ; ils ne remplacent pas un profilage de modpack.

Simulation de production : **100 périodes** par scénario et par tier, vent moyen 53,4278 %, arrêt naturel 1,1062 %. En clair au hub normal : moyennes **1 598,71 / 6 394,84 / 14 921,29 HE**. Rapports complets et interprétation dans [ENERGY_BALANCE.md](ENERGY_BALANCE.md) et `validation/wind/wind-simulation.txt`. Aucun rééquilibrage silencieux.

## Limites et vérifications manuelles restantes

- **Multijoueur visuel non exécuté** : plusieurs joueurs observant simultanément le rotor, latence et reconnexion. Tous reçoivent la même vitesse serveur ; leur angle local peut différer après arrivée ou culling, sans effet énergétique.
- **500 modèles visibles et scénario 500 présentes / 20 visibles non mesurés graphiquement**. La simulation de 500 couvre la logique et la distribution. Le renderer applique les mécanismes de culling et distance NeoForge, sans test FPS de ce scénario.
- **Arrêt complet puis redémarrage du même processus serveur non exécuté**. La persistance a été vérifiée avec un déchargement réel de chunk et relecture disque, et avec un nouveau DimensionDataStorage relisant le SavedData. Les clients de test démarrent des mondes neufs.
- Les widgets d'un véritable HomeLink Dashboard et les règles d'un véritable HomeLink Tasks n'ont pas été ouverts : schémas et métriques HomeCore ont été vérifiés, sans ajouter ces dépendances.
- Les tours sont visuelles et n'occupent pas physiquement 5/8/12 blocs. Le rotor s'arrête en douceur même lorsqu'un obstacle apparaît : il peut donc traverser visuellement cet obstacle pendant le freinage. Pas de dégâts, explosion, usure ou multibloc.
- Les changements de monde qui contournent les événements de blocs sont détectés au contrôle périodique ; au premier chargement, le délai périodique est réparti jusqu'à 199 ticks. Les mises à jour normales invalident dès la prochaine exécution de la turbine.
- Les options météo/altitude sont relues globalement toutes les 20 ticks ; le schéma STATIC du nominal est indicatif, les valeurs sont actualisées par le cycle de l'appareil après modification de configuration.
- Aucun FE/RF, minerai, générateur à charbon, direction physique du vent, yaw automatique, Hydro ni intégration directe Quarry/FarmBot ajouté. Ces fonctions ne font pas partie de cette livraison.

Fichiers créés/modifiés : [WIND_FILES.md](WIND_FILES.md). Les preuves de cette livraison sont regroupées dans `docs/validation/wind/`, séparément des anciens rapports solaires.
