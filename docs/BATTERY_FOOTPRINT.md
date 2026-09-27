# Emprises des batteries — 27 septembre 2026

| Niveau | Emprise | Capacité totale | Entrée / sortie par tick |
|---|---|---|---|
| I | 1×1 | 40 000 HE | 8 / 8 HE |
| II | 2×1 | 120 000 HE | 32 / 32 HE |
| III | 2×2 | 320 000 HE | 128 / 128 HE |

Le placement, les parties passives, l'orientation, le contrôleur unique et le démontage suivent les conventions des panneaux et des éoliennes. Les blocs réellement occupés ont une collision. Chaque partie permet le clic droit et expose le même port bidirectionnel, avec un seul stockage et les mêmes limites par tick pour l'ensemble. Le réseau résout toutes les parties vers le contrôleur pour éviter le double comptage de capacité et de batteries. Il n'existe aucun nouveau système énergétique.

Le démontage de n'importe quelle partie retire les autres parties chargées et rend un seul objet avec l'outil approprié ; l'énergie stockée est perdue comme auparavant. Les parties orphelines sont retirées après chargement. Aucune production ni recharge spontanée n'est ajoutée.

Les sauvegardes antérieures sont reconnues par l'absence de `base_version`. L'extension est atomique : toutes les cases doivent être vides ou appartenir déjà à cette batterie. Une case occupée, hors bordure ou déchargée empêche l'extension, sans chargement forcé ni écrasement. L'énergie et l'identité sont conservées. L'état « Socle incomplet », traduit FR/EN, apparaît dans l'interface et HomeCore ; les transferts restent bloqués jusqu'à l'extension complète. Nouvelle tentative une fois par seconde. Les nouvelles batteries possèdent toute leur emprise dès le placement.

Les modèles II et III sont redimensionnés sur leur emprise en conservant leur conception : doubles cellules pour II, armoire à tiroirs pour III. Les maillages extérieurs et leurs UV sont découpés en cases sans faces internes aux jointures. Les objets d'inventaire montrent l'ensemble et gardent les proportions. Aucune texture supplémentaire ; les 21 textures existantes sont conservées.

## Vérification

L'audit contrôle **70 modèles, 7 836 faces et 285 assemblages** : aucun recouvrement coplanaire détecté, y compris les trois batteries assemblées. Le parcours client ouvre aussi l'interface de Battery III depuis une partie secondaire et vérifie la capacité de 320 000 HE et l'intégrité du socle.

Six nouveaux GameTests couvrent :

- Les trois emprises dans les quatre orientations, un seul contrôleur et un port partagé.
- Le refus d'un placement obstrué, sans placement partiel ni écrasement.
- Le démontage par une partie secondaire et l'unicité du drop.
- Les budgets de transfert partagés entre toutes les parties.
- La capacité et le comptage uniques dans le réseau, avec conservation des HE transférés à un consommateur.
- L'agrandissement des anciennes sauvegardes, les obstacles, la conservation de charge/UUID, la sauvegarde/relecture et l'arrêt des transferts au déchargement.

Commandes exécutées :

```powershell
.\gradlew.bat build test --console=plain
node scripts/generate-models.cjs
node scripts/audit-models.cjs
node scripts/audit-assemblies.cjs
.\gradlew.bat runGameTestServer --console=plain
.\gradlew.bat runSmoke --console=plain
.\gradlew.bat build test releaseBundle --console=plain
```

Environnement : Windows 11, JDK 21, Minecraft 1.21.1, NeoForge 21.1.251 et HomeCore 1.7.0. Client RTX 5070 Ti sans shader ni pack externe. Les validations avec des packs/shaders externes et l'observation multijoueur restent manuelles.

## Fichiers concernés

`BatteryBlock`, `BatteryTier`, `BatteryBlockEntity`, `EnergyBlockItem`, `HomeLinkEnergy`, `EnergyNetworks`, `BatteryDevice`, `BatteryMenu`, `BatteryScreen` ; scripts `generate-models.cjs` et `audit-assemblies.cjs` ; modèles, blockstates et loot tables `battery_*` ; traductions FR/EN. Vérifications : `BatteryFootprintGameTests` (nouveau), `EnergyValidation`, `NetworkGameTests`, `ClientSmoke`. Documentation : `README.md` et ce rapport.

Les captures et journaux de ce passage sont archivés dans `docs/validation/battery-footprint/`.

## Résultats exécutés

- Compilation et **54 tests unitaires réussis**.
- Serveur dédié : **60 GameTests réussis**, dont les six nouveaux scénarios batterie ; durée des tests **11,75 s**.
- Client Minecraft français : **`ENERGY_SMOKE_OK`**, interface Battery III ouverte depuis une partie secondaire, capacité et état du socle validés.
- Captures inspectées : [les trois batteries](validation/battery-footprint/energy-battery-tiers-fr_fr.png), [vue rapprochée](validation/battery-footprint/energy-material-view-6-fr_fr.png), [interface Battery III](validation/battery-footprint/energy-battery-footprint-gui-fr_fr.png). Aucun défaut de jointure ni texture manquante repéré.
- Audits géométriques : **70 modèles / 285 assemblages**, aucun recouvrement coplanaire détecté. Les textures sont également vérifiées par la suite JUnit.
