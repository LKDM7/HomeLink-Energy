# Fichiers de la livraison éolienne

Comparaison SHA-256 avec la capture effectuée avant les modifications de cette session. Les fichiers antérieurement non suivis sont distingués des fichiers réellement créés ici. Aucun fichier HomeCore modifié.

## Créés

- `docs/WIND_FILES.md`
- `docs/WIND_VALIDATION.md`
- `src/main/java/fr/lkdm/homelink/energy/block/WindTier.java`
- `src/main/java/fr/lkdm/homelink/energy/block/WindTurbineBlock.java`
- `src/main/java/fr/lkdm/homelink/energy/blockentity/WindTurbineBlockEntity.java`
- `src/main/java/fr/lkdm/homelink/energy/client/WindOverlay.java`
- `src/main/java/fr/lkdm/homelink/energy/client/WindTurbineRenderer.java`
- `src/main/java/fr/lkdm/homelink/energy/client/WindTurbineScreen.java`
- `src/main/java/fr/lkdm/homelink/energy/config/WindConfig.java`
- `src/main/java/fr/lkdm/homelink/energy/energy/EnergySourceType.java`
- `src/main/java/fr/lkdm/homelink/energy/energy/FractionalEnergy.java`
- `src/main/java/fr/lkdm/homelink/energy/homelink/WindDevice.java`
- `src/main/java/fr/lkdm/homelink/energy/menu/WindTurbineMenu.java`
- `src/main/java/fr/lkdm/homelink/energy/wind/RotorArea.java`
- `src/main/java/fr/lkdm/homelink/energy/wind/WindClearance.java`
- `src/main/java/fr/lkdm/homelink/energy/wind/WindCommands.java`
- `src/main/java/fr/lkdm/homelink/energy/wind/WindParameters.java`
- `src/main/java/fr/lkdm/homelink/energy/wind/WindSavedData.java`
- `src/main/java/fr/lkdm/homelink/energy/wind/WindState.java`
- `src/main/java/fr/lkdm/homelink/energy/wind/WindStatus.java`
- `src/main/java/fr/lkdm/homelink/energy/wind/WindTurbineCore.java`
- `src/main/resources/assets/homelink_energy/blockstates/wind_turbine_1.json`
- `src/main/resources/assets/homelink_energy/blockstates/wind_turbine_2.json`
- `src/main/resources/assets/homelink_energy/blockstates/wind_turbine_3.json`
- `src/main/resources/assets/homelink_energy/models/block/wind_blade.json`
- `src/main/resources/assets/homelink_energy/models/block/wind_hub.json`
- `src/main/resources/assets/homelink_energy/models/block/wind_nacelle.json`
- `src/main/resources/assets/homelink_energy/models/block/wind_nacelle_2.json`
- `src/main/resources/assets/homelink_energy/models/block/wind_nacelle_3.json`
- `src/main/resources/assets/homelink_energy/models/block/wind_tower.json`
- `src/main/resources/assets/homelink_energy/models/block/wind_turbine_1.json`
- `src/main/resources/assets/homelink_energy/models/block/wind_turbine_2.json`
- `src/main/resources/assets/homelink_energy/models/block/wind_turbine_3.json`
- `src/main/resources/assets/homelink_energy/models/item/wind_turbine_1.json`
- `src/main/resources/assets/homelink_energy/models/item/wind_turbine_2.json`
- `src/main/resources/assets/homelink_energy/models/item/wind_turbine_3.json`
- `src/main/resources/data/homelink_energy/loot_table/blocks/wind_turbine_1.json`
- `src/main/resources/data/homelink_energy/loot_table/blocks/wind_turbine_2.json`
- `src/main/resources/data/homelink_energy/loot_table/blocks/wind_turbine_3.json`
- `src/main/resources/data/homelink_energy/recipe/wind_turbine_1.json`
- `src/main/resources/data/homelink_energy/recipe/wind_turbine_2.json`
- `src/main/resources/data/homelink_energy/recipe/wind_turbine_3.json`
- `src/test/java/fr/lkdm/homelink/energy/energy/WindModelTest.java`
- `src/test/java/fr/lkdm/homelink/energy/energy/WindSimulationTest.java`
- `src/test/java/fr/lkdm/homelink/energy/energy/WindStateTest.java`
- `src/test/java/fr/lkdm/homelink/energy/network/WindNetworkTest.java`
- `src/verification/java/fr/lkdm/homelink/energy/verification/WindGameTests.java`

## Modifiés

- `README.md`
- `build.gradle`
- `docs/ENERGY_BALANCE.md`
- `docs/HOMECORE_AUDIT.md`
- `src/main/java/fr/lkdm/homelink/energy/block/EnergyBlockItem.java`
- `src/main/java/fr/lkdm/homelink/energy/blockentity/SolarPanelBlockEntity.java`
- `src/main/java/fr/lkdm/homelink/energy/client/EnergyClient.java`
- `src/main/java/fr/lkdm/homelink/energy/client/EnergyScreen.java`
- `src/main/java/fr/lkdm/homelink/energy/config/EnergyConfig.java`
- `src/main/java/fr/lkdm/homelink/energy/energy/HePort.java`
- `src/main/java/fr/lkdm/homelink/energy/energy/SolarGenerator.java`
- `src/main/java/fr/lkdm/homelink/energy/homelink/BatteryDevice.java`
- `src/main/java/fr/lkdm/homelink/energy/homelink/EnergyHomeCore.java`
- `src/main/java/fr/lkdm/homelink/energy/HomeLinkEnergy.java`
- `src/main/java/fr/lkdm/homelink/energy/network/EnergyNetwork.java`
- `src/main/java/fr/lkdm/homelink/energy/network/NetworkEvents.java`
- `src/main/java/fr/lkdm/homelink/energy/registry/EnergyRegistries.java`
- `src/main/resources/assets/homelink_energy/lang/en_us.json`
- `src/main/resources/assets/homelink_energy/lang/fr_fr.json`
- `src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json`
- `src/test/java/fr/lkdm/homelink/energy/ResourcesTest.java`
- `src/verification/java/fr/lkdm/homelink/energy/verification/ClientSmoke.java`
- `src/verification/java/fr/lkdm/homelink/energy/verification/ReliabilityGameTests.java`

## Preuves et artefacts

- `docs/validation/wind/` : journaux de build, GameTests, clients FR/EN, résultats XML, simulations et captures des modèles/GUI/overlays.
- `build/libs/homelink_energy-0.1.0.jar` : mod compilé.
- `build/release/` : mod, HomeCore séparé, licence et documentation.
- Les logs intermédiaires restent sous `build/wind-*.log`.
