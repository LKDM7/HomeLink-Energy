# Branche Hydro — HomeLink Energy 0.4.0

Hydro est une branche de HomeLink Energy (mod ID `homelink_energy`), pas un mod séparé. Chaîne de jeu :

```text
eau du monde → Pompe Hydro I/II/III → Conduites Hydro → Turbine Hydro 2×2×2 → HE → câbles et batteries existants
```

## Modèle abstrait (à lire avant tout)

- Les pompes sont des **captateurs hydrauliques abstraits** : elles ne consomment ni HE ni carburant. Leur interrupteur signifie « autoriser le captage ».
- L'eau est une **condition de fonctionnement**, comme le soleil ou le vent. Elle n'est **jamais consommée** : aucun bloc source n'est retiré, aucun lac ne se vide. Plusieurs pompes peuvent utiliser le même lac.
- Le débit est en **DH/t**, un débit hydraulique de jeu. Ce n'est ni un volume stocké, ni des mB/t, ni des HE/t. Les conduites ne contiennent rien et ne fournissent aucun fluide extractible.
- Seule la turbine produit des HE. Le rejet d'eau à l'avant est **purement visuel** (nappe translucide, particules, son) : aucun bloc d'eau n'est posé, le terrain n'est pas modifié, les entités ne sont pas poussées, et ce rejet ne peut jamais alimenter une pompe.
- Aucun bonus d'heure, de pluie, de vent, d'altitude ni de hauteur de chute. Aucune pompe électrique réelle n'est simulée.

## Trois réseaux distincts

| Réseau | Support | Ce qu'il transporte |
|---|---|---|
| Hydraulique | Conduites Hydro uniquement | un débit abstrait DH/t, de 1 à 4 pompes vers **une** turbine |
| Électrique HE | Câbles cuivre existants, batteries | l'énergie HE produite par la turbine |
| HomeNetwork | HomeLink Connector / bouton HomeLink | la supervision HomeCore (métriques, actions, événements) |

Une pompe ou une turbine fonctionne sans HomeNetwork. Une conduite Hydro ne se relie jamais à un câble HE, à une conduite d'irrigation Farm, à un coffre ou à un réservoir.

## Blocs, emprises et ports

Tailles en **largeur × hauteur × profondeur**. Un seul item pose toute la machine ; toutes les cases doivent être libres, dans la bordure du monde et sous la hauteur maximale, sinon rien n'est posé et l'item est conservé.

| Bloc | ID | Taille | Cases |
|---|---|---|---:|
| Pompe Hydro I | `homelink_energy:hydro_pump_1` | 1 × 1 × 1 | 1 |
| Pompe Hydro II | `homelink_energy:hydro_pump_2` | 2 × 1 × 1 | 2 |
| Pompe Hydro III | `homelink_energy:hydro_pump_3` | 2 × 1 × 2 | 4 |
| Conduite Hydro | `homelink_energy:hydro_pipe` | 1 bloc (corps 10/16, brides 14/16) | 1 |
| Turbine Hydro | `homelink_energy:hydro_turbine` | 2 × 2 × 2 | 8 |

**Repère local** (classe `HydroLayout`, partagé par le placement, le graphe, les capabilities, les modèles, le renderer et les tests) : une case est `(colonne, couche, rangée)`. La colonne augmente vers `FACING.getClockWise()`, la couche vers le haut, la rangée vers `FACING.getOpposite()`. Le maître est `(0, 0, 0)`.

| Machine | FACING | Port | Case locale | Face |
|---|---|---|---|---|
| Pompe | admission, tournée vers l'eau que regarde le joueur à la pose | sortie hydraulique | (0, 0, 0) | dessus |
| Turbine | façade (écran, volets, rejet), tournée vers le joueur | entrée Hydro | (0, 1, 1) — haut arrière gauche | dessus |
| Turbine | — | sortie HE (raccord cuivre) | (1, 0, 1) — bas arrière droit | côté horaire (`FACING.getClockWise()`) |
| Turbine | — | dégagement du rejet | les deux cases devant (0, 0, 0) et (1, 0, 0) | — |

La grille arrière de la turbine n'est jamais un port. Un seul port HE et un seul tampon par turbine : brancher un câble ou une batterie contre le raccord cuivre ; une batterie posée contre ce raccord est chargée directement.

Casser n'importe quelle partie démonte toute la machine et rend au plus un item (aucun en créatif). Le tampon HE est perdu, le nom donné à l'enclume est conservé. Un chunk déchargé n'est pas une destruction : les parties orphelines ne sont retirées qu'une fois le chunk du maître chargé et le maître absent.

## Eau requise

Fenêtre devant l'admission : **largeur × distance devant × profondeur verticale** à partir du niveau de l'admission vers le bas.

| Pompe | Fenêtre | Cases = sources pour 100 % | Colonnes (0 = maître) |
|---|---|---:|---|
| I | 3 × 3 × 2 | 18 | −1 à 1 (centrée) |
| II | 5 × 5 × 2 | 50 | −1 à 3 (décalée d'un demi-bloc côté horaire) |
| III | 7 × 7 × 3 | 147 | −2 à 4 (décalée d'un demi-bloc côté horaire) |

Seuls comptent les **vrais blocs source d'eau** reliés face à face aux cases situées directement devant l'admission, à l'intérieur de la fenêtre, chacun une fois. Ne comptent pas : eau courante, blocs waterlogged, colonnes de bulles, lave, eau derrière un mur, ou bassin non relié à l'admission.

`disponibilité = sources reliées / cases de la fenêtre`. Sous **25 %** : `WATER_INSUFFICIENT`, débit nul. À partir de 25 % : débit maximal × disponibilité. Si une partie de la fenêtre est déchargée : `WATER_UNKNOWN`, débit nul, aucun chunk chargé. Le scan est mis en cache, invalidé par les changements de blocs voisins et revérifié toutes les 100 ticks (décalées entre pompes). Le bouton **Voir la zone d'eau** affiche la fenêtre calculée par le serveur pendant dix secondes.

## Circuit hydraulique

- 1 à 4 pompes distinctes, **exactement 1 turbine**, 1 à 512 conduites. Jonctions et boucles autorisées ; une boucle ou une branche morte n'augmente jamais le débit.
- Plus de 4 pompes : `TOO_MANY_PUMPS`. Plus de 512 conduites : `NETWORK_TOO_LARGE`. Deux turbines : `MULTIPLE_TURBINES`, **les deux** s'arrêtent jusqu'à séparation. Les limites de pompes et de conduites sont configurables ; la limite d'une turbine est fixe en V1.
- Connexion uniquement face à face, jamais en diagonale, jamais à distance. Les faces dessinées et les faces reconnues par le graphe utilisent la même règle (`HydroPipeBlock.connectsTo`).
- Le graphe est construit à la demande par les machines chargées, indexé par case et par chunk, et reconstruit après pose/retrait d'une conduite ou d'une machine, chargement/déchargement d'un chunk ou rechargement de la configuration. Budget : 128 visites par tick et par dimension. Un circuit en reconstruction ne produit pas (`NETWORK_PENDING`) ; une traversée par un chunk déchargé n'est jamais supposée (`NETWORK_INCOMPLETE`).
- L'allocation est calculée au plus une fois par circuit et par tick serveur ; l'ordre des tickers ne peut pas doubler les HE.

## Débit, production et tampon

| Pompe | Débit max | Production seule à 100 % d'eau |
|---|---:|---:|
| I | 1 DH/t | 4 000 HE / 24 000 ticks (≈ 0,167 HE/t) |
| II | 3 DH/t | 12 000 HE / 24 000 ticks (0,5 HE/t) |
| III | 12 DH/t | 48 000 HE / 24 000 ticks (2 HE/t) |

`débit utilisé = min(Σ débits des pompes, 12 DH/t)` si la turbine est allumée, complète, dans une dimension autorisée et avec un rejet dégagé ; sinon 0. `HE/t = 48 000 / 24 000 × débit utilisé / 12`, soit 4 000 HE par période et par DH/t pour tous les niveaux. Au-delà de 12 DH/t : information `FLOW_LIMITED` (« limité par la turbine »), le surplus n'est pas conservé.

Tampon de turbine : 1 000 HE. Accumulateur fractionnaire partagé avec Solar et Wind ; les HE refusés par un tampon plein sont comptés comme perdus, sans réserve cachée. Les périodes comptent les ticks serveur exécutés (`gameTime`) : `/time set`, le sommeil, un déchargement ou un redémarrage ne donnent aucun rattrapage. Après un chargement, la période en cours affiche « X HE sur Y ticks observés ». Interrupteur coupé : aucune nouvelle génération, le tampon peut encore se vider.

## États

Pompe : `DISABLED`, `INCOMPLETE_STRUCTURE`, `UNSUPPORTED_DIMENSION`, `NO_WATER`, `WATER_INSUFFICIENT`, `WATER_UNKNOWN`, `NO_TURBINE`, `NETWORK_PENDING`, `NETWORK_INVALID`, `STANDBY`, `PUMPING`, `CONFIG_DISABLED`.

Turbine : `DISABLED`, `INCOMPLETE_STRUCTURE`, `UNSUPPORTED_DIMENSION`, `NO_PUMP`, `NO_FLOW`, `NETWORK_PENDING`, `NETWORK_INCOMPLETE`, `TOO_MANY_PUMPS`, `MULTIPLE_TURBINES`, `NETWORK_TOO_LARGE`, `OUTLET_BLOCKED`, `GENERATING`, `BUFFER_FULL`, `CONFIG_DISABLED`. `FLOW_LIMITED` est un indicateur complémentaire.

`OUTLET_BLOCKED` : les deux cases devant le bas de la façade doivent contenir de l'air ou de l'eau. **Localiser l'obstruction** marque le premier bloc gênant ; la production reprend seule une fois dégagé.

## HomeCore

Types `homelink_energy:hydro_pump` et `homelink_energy:hydro_turbine` (un seul type de pompe ; le niveau est la métrique `hydro_pump_level`). Contrats réutilisés : `DashboardDevice`, `NetworkMember`, `Switchable`, `Renamable`, `DashboardAPI.bindDevice`. Une machine coupée mais chargée est `DISABLED`, jamais `OFFLINE`.

Métriques pompe : `hydro_pump_level`, `hydro_pump_status`, `enabled`, `water_availability`, `sources_count`, `sources_required`, `available_flow`, `allocated_flow`, `hydraulic_connected`.
Métriques turbine : `hydro_turbine_status`, `enabled`, `available_flow`, `used_flow`, `max_flow`, `flow_percentage`, `pump_count`, `outlet_clear`, `current_generation` (HE/t, `EnergyApi.HE_PER_TICK`), `generated_this_period`, `buffer_energy`, `buffer_capacity`, `lost_energy` (HE, `EnergyApi.HE`), `network_connected`. Débits en `homelink_energy:dh_per_tick` (DH/t).

Événements, uniquement sur transition, jamais au premier chargement : pompe `hydro_water_lost` / `hydro_water_restored` ; turbine `hydro_circuit_invalid` / `hydro_circuit_restored`, `hydro_outlet_blocked` / `hydro_outlet_cleared`, `hydro_generation_started` / `hydro_generation_stopped` (informatif). Données : `reason`, `source`. Un arrêt volontaire ou un `STANDBY` n'est jamais une alerte.

Batteries : nouvelle métrique `network_hydro_production` à côté de Solar, Wind et Other ; les catégories additionnées donnent le débit livré du réseau, sans seconde génération. `EnergySourceType.HYDRO` est ajouté en dernière position (aucune ordinale persistée).

### Dashboard (intégration agrégée non validée)

Le checkout local `../HomeLink-Dashboard` (1.0.0, commit `6f110fb`) ne contient pas `dashboard/widget/EnergyBalance.java`. Aucune modification Dashboard n'a donc été faite. Les machines Hydro apparaissent comme appareils génériques HomeCore. Patch nécessaire dans la version GitHub qui contient `EnergyBalance` :

1. reconnaître le type `homelink_energy:hydro_turbine` comme producteur, avec la métrique `homelink_energy:current_generation` (HE/t), comme `solar_panel` et `wind_turbine` ;
2. ne **pas** compter `homelink_energy:hydro_pump` parmi les producteurs HE (débit en DH/t, pas d'énergie) ;
3. ajouter Hydro au bilan et au classement Energy, tester Solar + Wind + Hydro sans double comptage ;
4. garder la lecture filtrée par spectateur (mode personnel/partagé) et aucune dépendance Java Dashboard → Energy.

## Configuration

Section `[hydro]` du fichier serveur `serverconfig/homelink_energy-server.toml` : `enabled`, `allowedDimensions` (Overworld par défaut), `pumpFlow1/2/3`, `waterWindow{1,2,3}{Width,Distance,Depth}`, `minimumWaterAvailability`, `waterCheckInterval`, `turbineMaxFlow`, `turbineMaxHEPerReferencePeriod`, `turbineBufferCapacity`, `maxPumpsPerCircuit`, `maxTurbinesPerCircuit` (fixé à 1), `maxPipesPerCircuit`, `graphVisitsPerTick`. Les bornes sont validées par NeoForge puis par `HydroParameters.sanitize` : une valeur dangereuse (NaN, débit nul, division par zéro) est remplacée par sa valeur par défaut avec un avertissement. Un rechargement invalide les circuits.

Fichier client `config/homelink_energy-client.toml`, section `[hydro]` : `sounds`, `volume`, `particles`, `animations` (faux = mouvements réduits), `previewRange`.

## Recettes

Machines à la table de craft vanilla ; composants HomeCore uniquement à l'Electronics Workbench. II exige I, III exige II ; aucune amélioration sur place (casser, crafter, reposer ; le nom d'enclume suit l'item cassé, pas le craft).

| Résultat | Grille | Légende |
|---|---|---|
| 4 Conduites Hydro | `ICI / I.I / ICI` | I lingot de fer, C lingot de cuivre |
| Pompe I | `IBI / CPC / IRI` | I fer, B Circuit Board, C cuivre, P piston, R redstone |
| Pompe II | `GMG / CTC / IKI` | G or, M Microprocessor, C cuivre, T Pompe I, I fer, K Control Module |
| Pompe III | `DKD / GTG / IOI` | D diamant, K Control Module, G or, T Pompe II, I fer, O Communication Module |
| Turbine | `IKI / PMP / COC` | I bloc de fer, K Control Module, P piston, M Microprocessor, C bloc de cuivre, O Communication Module |

## Dépannage

| Affiché | Cause | Correction |
|---|---|---|
| Pas d'eau devant l'admission | la case juste devant l'admission n'est pas une source reliée | tourner l'admission vers l'eau, retirer le bloc devant |
| Eau insuffisante | moins de 25 % de la fenêtre | agrandir le bassin (voir la zone d'eau) |
| Aucune turbine reliée | conduite absente ou sur une mauvaise face | conduite sur le dessus de la case maître de la pompe ; entrée turbine en haut à l'arrière gauche |
| Plusieurs turbines | deux turbines sur le même circuit | séparer les conduites |
| Évacuation obstruée | bloc solide devant le bas de la façade | « Localiser l'obstruction » puis dégager |
| Électricité HE : non connecté | aucun câble sur le raccord cuivre | câble ou batterie contre le côté cuivre bas arrière |

## Modèles et textures Hydro

La gamme Hydro reprend directement les textures existantes des batteries, câbles et éoliennes :
anthracite, cadres gris clair, cuivre et pales blanches. Les cadrans, plaques et admissions dédiés
sont en 16 × 16, avec la palette exacte de `GenerateTextures.java` et des jauges ambrées.
Les textures sont opaques, statiques et déterministes ; les matériaux partagés ne sont pas réécrits.

- Pompes I/II/III : stations intégrées sur socle lourd, tête de pompe protégée, carter moteur allongé au niveau II et banc de refroidissement arrière au niveau III.
- Conduites : jonctions massives, gros tubes octogonaux et brides boulonnées avec joints cuivre.
- Turbine : bâti à quatre montants, capot étagé, radiateur protégé, trappe à volant, tuyauterie latérale apparente et grille arrière laissant voir le rotor.
- Les emprises, ports, positions du rotor et des volets restent compatibles avec le placement existant.

Les modèles sont des maillages OBJ chargés par le chargeur natif `neoforge:obj`. Le générateur
découpe les faces selon les cases du multibloc, interpole les UV et retire les superpositions
coplanaires. Les matériaux restent ceux du mod. Les miniatures d’inventaire représentent la
machine complète, rotor inclus pour la turbine.

Régénération depuis la racine, avec Java 21 et Node.js :

```powershell
java scripts/GenerateHydroTextures.java
node scripts/hydro-models.cjs
node --test scripts/hydro-geometry.test.cjs
node scripts/audit-models.cjs
node scripts/audit-assemblies.cjs
```

Validation de la refonte : `gradlew build` réussi (82 tests, aucun échec), trois tests géométriques Node réussis, audit de 100 modèles
sans chevauchement coplanaire et de 388 assemblages sans échec. `gradlew runSmoke` en français
réussi avec `ENERGY_SMOKE_OK`, y compris débit, menus, zone d’eau et évacuation Hydro.
Captures en jeu et rapports : [validation/hydro-industrial](validation/hydro-industrial/).

## Limites connues

- Pas de partage entre turbines, pas de simulation de volume, de pression, de fuite ni d'usure.
- Sons construits à partir de sons vanilla faute d'enregistrements dédiés : pompe (bulles), ronronnement (balise), ventilateur du rotor (souffle d'élytre aigu, montée et ralentissement progressifs, hauteur liée à la vitesse, audible à 10 blocs au plus avec un fondu linéaire), évacuation (eau), démarrage/arrêt (piston).
- Dimensions autres que l'Overworld désactivées par défaut et non validées.
- Validation visuelle multijoueur (deux clients) et intégration Dashboard agrégée : manuelles / non réalisées, voir `docs/VALIDATION.md`.
