# Révision des modèles et textures — 27 septembre 2026

## Changements

Les trois éoliennes disposent de matériaux HomeLink dédiés : acier peint blanc cassé, graphite, cuivre mat, laiton, pales, grilles et plaques de niveau. Neuf PNG opaques de 32×32 pixels, sans animation, générés de façon reproductible par `scripts/GenerateWindTextures.java`. Les textures solaires/batteries/câbles existantes sont conservées et contrôlées avec les nouvelles : 21 textures au total.

Les modèles des turbines ont été retravaillés : socles à pieds et trappes de service, mâts à sections avec raccords, nacelles distinctes selon le niveau, grilles de refroidissement, arbre et moyeu, pales à profil aminci et embouts cuivre, miniatures d'inventaire à trois pales. Les UV du mât se répètent par section au lieu d'être étirés sur toute la hauteur. L'échelle du moyeu suit le rotor pour conserver des racines de pales raccordées. Le moyeu avance de 0,1 bloc dans le volume existant du rotor pour séparer ses faces de la nacelle fixe.

Les modèles solaires, batteries, câbles et objets passent par un calcul de surfaces extérieures : suppression des faces enfouies, découpe des surfaces coplanaires qui se recouvrent, conservation des UV correspondants. Aux frontières internes des panneaux multiblocs, les faces d'extrémité sont retirées des deux côtés. Les bras de câble s'arrêtent avant leur nœud central ; dans un angle mur/sol, une seule branche occupe le coin, l'autre emploie le modèle court `cable_trace_corner`. Cela supprime les faces latérales doubles sans modifier la connexion énergétique.

## Contrôles reproductibles

```powershell
node scripts/wind-models.cjs
node scripts/generate-models.cjs
node scripts/audit-models.cjs
node scripts/audit-assemblies.cjs
.\gradlew.bat build test --console=plain
.\gradlew.bat runSmoke --console=plain
.\gradlew.bat runGameTestServer --console=plain
.\gradlew.bat build test releaseBundle --console=plain
```

Génération des textures exécutée avec le JDK 21 installé par Gradle :

```powershell
java scripts/GenerateWindTextures.java   # JDK 21 (par exemple celui provisionné par Gradle)
```

`java` dans le PATH pointe sur Java 8 ; le build utilise toujours la toolchain Java 21 du projet.

L'audit initial a compté **680 paires de faces coplanaires superposées** sur 42 modèles. Ce total comprend des surfaces internes invisibles : il ne signifie pas 680 défauts visibles. L'audit des modèles reconstruits contrôle **43 modèles** et ne trouve **aucun recouvrement coplanaire**. La vérification teste les intersections de surfaces avec aire strictement positive, y compris les rotations des pales d'inventaire ; les arêtes qui se touchent sont autorisées.

L'audit des pièces assemblées vérifie **282 configurations** : 216 états de rotor (trois niveaux, tous les cinq degrés), trois emprises solaires complètes, et les 63 combinaisons non vides des six faces de câble avec tous leurs bras. Résultat : **aucun recouvrement coplanaire**. Les transformations reproduisent celles des renderers, y compris le moyeu, la segmentation du mât et les raccords de câble.

Deux tests JUnit ajoutés empêchent le retour des faces statiques doubles et vérifient tous les PNG : dimensions compatibles mipmaps, opacité complète, lecture valide, absence de bande animée ou de métadonnées d'animation. Les tests de ressources antérieurs vérifient aussi les références de modèles et textures, les langues, les loot tables et les recettes.

## Résultats exécutés

- Compilation et suite JUnit : **54 tests, 0 échec, 0 erreur**.
- Serveur GameTest dédié : **49 tests requis réussis**, exécution des tests en **19,30 s**. La géométrie et les textures ne modifient pas la production ni la distribution HE.
- Audit des PNG : **21 textures valides**, opaques et statiques.
- Audit géométrique : **43 modèles**, **5 334 faces**, **0 recouvrement coplanaire**.
- Audit d'assemblage : **282 configurations**, **0 échec**.
- Client Minecraft français : **`ENERGY_SMOKE_OK`**, chargement des modèles, rotation synchronisée, GUI, zone du rotor et obstruction validés. Inspection des captures d'ensemble et rapprochées : aucune texture manquante ni anomalie de raccord repérée. Exécution sous Windows, Java 21, Minecraft 1.21.1 / NeoForge 21.1.251, NVIDIA GeForce RTX 5070 Ti, pilote 616.56, OpenGL 4.6, sans pack de ressources externe ni shaders ajoutés.
- Build final **`build test releaseBundle` réussi**. JAR livré : `build/release/homelink_energy-0.1.0.jar`, accompagné de HomeCore 1.7.0 et de la documentation. `releaseBundle` est exécuté de nouveau après archivage du journal final pour inclure ce rapport dans le paquet.

Captures : [trois turbines](validation/visual/energy-wind-models-fr_fr.png), [nacelle I](validation/visual/energy-material-view-0-fr_fr.png), [nacelle II](validation/visual/energy-material-view-2-fr_fr.png), [nacelle III](validation/visual/energy-material-view-4-fr_fr.png), [solaire et batteries](validation/visual/energy-material-view-6-fr_fr.png), [raccords de câble](validation/visual/energy-material-view-7-fr_fr.png).

## Fichiers concernés

Créés : `scripts/GenerateWindTextures.java`, `scripts/wind-models.cjs`, `scripts/clean-models.cjs`, `scripts/audit-models.cjs`, `scripts/audit-assemblies.cjs`, les neuf PNG `textures/block/wind_*.png`, le modèle `models/block/cable_trace_corner.json` et ce rapport.

Modifiés : `WindTurbineRenderer.java`, `CableRenderer.java`, `EnergyClient.java`, `ClientSmoke.java`, `ResourcesTest.java`, `scripts/battery-models.cjs`, `scripts/generate-models.cjs` et `README.md`. Les JSON des modèles de blocs et d'items éoliens, solaires, batteries et câbles sont régénérés. Les textures antérieures restent inchangées.

## Portée de la validation visuelle

Le client de test utilise le vrai moteur Minecraft, les textures chargées dans l'atlas et les renderers enregistrés. Le parcours comprend une vue d'ensemble, une rotation du rotor, six vues rapprochées des socles/nacelles des turbines I/II/III et deux vues des panneaux, batteries et câbles. Le test contrôle aussi les modèles supplémentaires, dont le nouveau raccord de câble.

Les audits géométriques éliminent les causes de z-fighting repérées. Ils ne garantissent pas l'absence de tout aliasing sur toutes les distances, cartes graphiques, shaders externes ou packs de ressources. Les captures fixes permettent de contrôler la géométrie et les matériaux ; elles ne constituent pas une mesure vidéo exhaustive du scintillement temporel.

Les journaux, rapports JSON et captures de cette révision sont dans `docs/validation/visual/`. Les anciens rapports éoliens restent conservés dans `docs/validation/wind/` pour distinguer les passages.
