# Socles éoliens — 27 septembre 2026

| Niveau | Emprise occupée | Parties | Contrôleurs / tampons |
|---|---|---|---|
| I | 1×1 | 1 | 1 |
| II | 2×1 | 2 | 1 |
| III | 2×2 | 4 | 1 |

Les dimensions et conventions d'orientation suivent les panneaux solaires : largeur vers la droite de la face avant, profondeur vers l'arrière. Le placement vérifie toute l'emprise, les chunks déjà chargés, la bordure du monde et les collisions. Les parties supplémentaires sont passives, sans block entity ni tick de production. Le mât et le rotor sont centrés sur le socle complet. Les formes de collision sont précalculées par état et suivent la plateforme, le carter et le collier central.

Cliquer une partie ouvre l'interface du contrôleur. Les sorties HE de toutes les parties résolvent le même port ; le réseau déduplique les connexions par position du contrôleur. La sortie automatique vers une batterie directement dessous reste située sous le contrôleur, comme pour les panneaux solaires. Les autres parties se raccordent aux câbles. Le démontage retire toutes les parties chargées, laisse un seul drop en survie avec l'outil approprié et perd le tampon ; les parties orphelines sont retirées lorsque leur chunk est chargé.

Les sauvegardes antérieures, sans `base_version`, sont agrandies en conservant buffer et UUID, uniquement lorsque toutes les cases supplémentaires sont vides. Une emprise bloquée ou partiellement déchargée ne produit pas et n'exporte pas d'énergie. Aucun bloc existant n'est remplacé. L'état `INCOMPLETE_BASE` est traduit FR/EN et exposé comme avertissement HomeCore. Après libération de l'emprise, une ancienne turbine retente l'agrandissement une fois par seconde. Les turbines nouvellement placées sont complètes dès le placement.

La zone de clearance suit le nouveau centre du rotor. Lorsque ce centre est entre deux cases, le volume de blocs à garder libre s'arrondit vers l'extérieur pour couvrir les pales et le moyeu : il peut être plus large que le diamètre visuel nominal. L'overlay utilise exactement ce volume serveur. Le déplacement ne change pas les productions nominales, la météo, les seuils de vent ou l'altitude du hub.

## Modèles et scintillement

Les plateformes mesurent visuellement 16×16, 32×16 et 32×32 unités de modèle, avec carter, plaque de niveau, pieds et collier. Le maillage extérieur commun est découpé en modèles de cases, avec UV conservés et sans faces internes aux jointures. Les modèles d'inventaire représentent aussi les trois emprises. Les 21 textures restent inchangées.

Audits : **55 modèles / 5 670 faces / aucun recouvrement coplanaire**, et **282 assemblages sans échec**, dont les socles complets, mâts et rotors à tous les cinq degrés. Les tests géométriques contrôlent également que l'enveloppe d'obstruction contient le rotor centré dans les quatre orientations.

## Commandes exécutées

```powershell
.\gradlew.bat build test --console=plain
node scripts/wind-models.cjs
node scripts/audit-models.cjs
node scripts/audit-assemblies.cjs
.\gradlew.bat runGameTestServer --console=plain
.\gradlew.bat runSmoke --console=plain
.\gradlew.bat build test runGameTestServer --console=plain
.\gradlew.bat build test releaseBundle --console=plain
```

La compilation, les audits, les GameTests et le client ont été relancés après les ajustements. Environnement inchangé : Windows 11, JDK 21, Minecraft 1.21.1, NeoForge 21.1.251, HomeCore 1.7.0 ; client RTX 5070 Ti sans shader externe.

## Fichiers

- `WindTier`, `WindTurbineBlock` : dimensions, occupation, collision, placement, interaction, démontage et agrandissement des anciennes sauvegardes.
- `WindTurbineBlockEntity`, `WindTurbineCore`, `WindStatus` : intégrité du socle, persistance et ports.
- `HomeLinkEnergy`, `EnergyNetworks`, `EnergyBlockItem` : capacités de toutes les parties, déduplication et dimensions dans l'infobulle.
- `RotorArea`, `WindTurbineRenderer` : centre du mât/rotor, clearance et limites de rendu.
- `scripts/wind-models.cjs`, `scripts/audit-assemblies.cjs` : socles découpés, modèles d'items et contrôle des jointures.
- Ressources `wind_turbine_*` : modèles, blockstates et loot conditionné au contrôleur ; langues FR/EN.
- `WindFootprintGameTests` (nouveau), `WindGameTests`, `EnergyValidation`, `ClientSmoke` : tests et fixtures adaptés aux emprises réelles.
- `README.md` et ce rapport.

Les rapports précédents décrivent leurs passages historiques ; les nouvelles preuves sont conservées dans `docs/validation/footprint/`. Le rendu avec d'autres shaders/packs et l'observation multijoueur restent des vérifications manuelles.

## Résultats

- **54 tests unitaires réussis**, dont les vérifications des textures et des faces statiques.
- **54 GameTests réussis sur serveur dédié**, dont cinq nouveaux scénarios : emprises et quatre orientations avec un seul contrôleur et enveloppe de rotor correcte ; placement refusé devant un obstacle ; démontage depuis une partie secondaire avec un seul drop ; connexions de plusieurs parties au même réseau sans duplication ; agrandissement d'une ancienne sauvegarde sans écrasement, avec conservation du buffer et de l'UUID.
- Client français : **`ENERGY_SMOKE_OK`**, modèles et rotation, menus, zone du rotor et obstruction. L'interface éolienne est maintenant ouverte depuis une partie secondaire pendant ce parcours.
- Inspection des captures : [socle I](validation/footprint/energy-material-view-1-fr_fr.png), [socle II](validation/footprint/energy-material-view-3-fr_fr.png), [socle III](validation/footprint/energy-material-view-5-fr_fr.png), [ensemble](validation/footprint/energy-wind-models-fr_fr.png).
- Dernier passage serveur : **54/54**, durée des tests **12,43 s**. Build final **`build test releaseBundle` réussi**. JAR : `build/release/homelink_energy-0.1.0.jar`.
