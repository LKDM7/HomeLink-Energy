# Équilibrage solaire et stockage

La référence **1 charbon = 4 000 HE** est une règle de conception. Minecraft ne fournit pas cette équivalence électrique ; elle n'est pas une conversion FE/RF. Aucun générateur à charbon n'est présent.

## Formule

Pour `t = floorMod(dayTime, 24000)` :

```text
f(t) = sin(π × t / 12000) si 0 <= t < 12000 ; sinon 0
S = Σ f(t), pour t de 0 à 23999 = 7639.437225…
potentiel(t) = nominalParCycle × f(t) / S × météo
```

La table et S sont initialisés une fois pour tous les panneaux. Hors Overworld, sans exposition ou avec nominal nul : potentiel nul. Le pic arrive à 6 000 ticks. La nuit fait déjà partie du cycle : aucune deuxième division par deux.

L'accumulateur ajoute les fractions des ticks exécutés. La partie entière est proposée au tampon ; seul le montant accepté incrémente `generated` et `generated_today`. Le rejet incrémente `lost`, sans réserve cachée ; le reste fractionnaire est dans `[0,1)`. Les transferts incrémentent `exported` et débitent exactement ce qu'ils créditent pour les ports conformes au contrat serveur. Stockage en `long`, capacités bornées, compteurs saturants. Les ports tiers mensongers ne sont pas pris en charge : une violation du contrat de simulation déclenche une erreur.

## Résultats numériques exécutés

24 000 appels au modèle par cas, tampon de 1 000 HE vidé chaque tick, sortie non limitante, exposition et dimension autorisées. Rapport généré : `build/reports/solar-cycles.txt`.

| Niveau | Météo | Cible HE | Obtenu HE | Pic HE/t |
|---|---|---:|---:|---:|
| I | clair | 2 000 | 2 000 | 0,2618 |
| I | pluie | 800 | 800 | 0,1047 |
| I | orage | 300 | 300 | 0,0393 |
| II | clair | 8 000 | 8 000 | 1,0472 |
| II | pluie | 3 200 | 3 200 | 0,4189 |
| II | orage | 1 200 | 1 200 | 0,1571 |
| III | clair | 20 000 | 20 000 | 2,6180 |
| III | pluie | 8 000 | 8 000 | 1,0472 |
| III | orage | 3 000 | 3 000 | 0,3927 |

Tolérance testée : **1 HE maximum** par cycle, liée au reliquat et à l'arithmétique flottante ; les neuf cas ci-dessus tombent exactement sur leur cible lors du passage consigné. Le test de dix cycles du panneau I vérifie aussi la dérive cumulée. Ce sont des essais du modèle, pas plusieurs journées attendues dans un client.

## Capacité et pertes

Tampon par défaut : 1 000 HE pour chaque panneau. Batterie I : 40 000 HE, soit 10 charbons de référence ; II : 120 000 (30) ; III : 320 000 (80). Les valeurs II/III et les limites 8/32/128 HE/t proviennent du prototype de partie 2 préexistant et restent provisoires.

Un panneau III plafonne à environ 2,618 HE/t : la limite par défaut de Battery I suffit donc à recevoir toute sa production directe. Plusieurs producteurs, une configuration modifiée ou une batterie pleine peuvent limiter l'acceptation. Aucun rendement gratuit par transfert ; les câbles ne stockent pas d'énergie. Les débits réseau décrivent l'énergie transférée depuis les producteurs, pas une seconde génération.

## Mesure de charge

Essai serveur GameTest : 100 panneaux III au-dessus de 100 Battery I, grille 10×10, clair, midi figé, ciel ouvert, API HomeCore active, 600 ticks par panneau. Depuis la refonte, chaque panneau occupe 2×2 cases et les origines sont espacées de trois blocs. Chaque panneau conserve une seule block entity active. La fixture de test charge ses chunks ; le mod de production ne le fait pas. La mesure ci-dessous est celle du prototype ; la mesure du passage actuel est consignée dans `validation/results.json`.

Passage du 26/09/2026 à 23:28 : **60 000 ticks**, **157 000 HE dans les batteries**, conservation pour chacune des 100 paires, **154,3134 ms** cumulées dans les ticks de panneaux, soit **2,57189 µs/panneau/tick** ; durée murale de la fenêtre **423,516 ms**. Windows 11, Ryzen 7 5800X, Java 21.0.11. GameTest exécute les ticks accélérés : ce chiffre n'est ni un TPS en partie normale ni une mesure du temps total du serveur. Les mesures incluent le coût du chronométrage, activé seulement par `energy.measureTicks=true`.

La consommation Quarry/FarmBot et l'équilibrage définitif restent à étudier avec leurs véritables interfaces.

## Éolien — paramètres et simulation du 27 septembre 2026

Les valeurs solaires restent 2 000 / 8 000 / 20 000 HE par cycle clair. Les valeurs éoliennes restent **3 000 / 12 000 / 28 000 HE nominaux** par période de 24 000 ticks simulés. Référence de coût uniquement : 1 charbon = 4 000 HE.

Formule : `nominal / 24000 × min(wind × weather × altitude, maxEfficiency)` si la dimension, le ciel, le rotor et le cut-in sont valides. L'hystérésis conserve son état entre 13 % et 16 %. Le buffer reçoit les unités entières ; le reliquat reste inférieur à 1 HE et les rejets sont perdus. La tolérance mathématique nominale est de 1 HE (arrondi flottant et fraction restante), sans perte cumulative arbitraire.

| Paramètre | Valeur par défaut |
|---|---:|
| Vent naturel | 10–100 % |
| Nouvelle cible | toutes les 2 400 ticks simulés |
| Variation maximale | 0,00015 par tick |
| Démarrage / arrêt | 16 % / 13 % |
| Clair / pluie / orage | 1,00 / 1,25 / 1,60 |
| Plafond final | 1,50 |
| Hub Y < 80 | ×0,80 |
| Hub Y 80–119 | ×1,00 |
| Hub Y 120–179 | ×1,15 |
| Hub Y ≥ 180 | ×1,25 |
| Tampon | 1 000 HE |

Simulation déterministe du vrai WindState et du vrai moteur : graine **20260927**, **2 400 000 ticks**, soit **100 périodes**. Ciel et rotor libres, stockage illimité, même trajectoire de vent pour tous les scénarios. Vent moyen **53,4278 %** ; temps sans production **1,1062 %**. Le scénario mixte impose huit périodes claires, une de pluie et une d'orage par groupe de dix : il ne prétend pas reproduire la fréquence météo naturelle de Minecraft.

| Scénario | I moyen HE | II moyen HE | III moyen HE | III min | III max | III écart-type |
|---|---:|---:|---:|---:|---:|---:|
| Clair, hub normal | 1 598,71 | 6 394,84 | 14 921,29 | 8 689 | 22 954 | 2 682,88 |
| Pluie, hub normal | 1 998,38 | 7 993,55 | 18 651,62 | 10 862 | 28 693 | 3 353,58 |
| Orage, hub normal | 2 556,02 | 10 224,10 | 23 856,24 | 13 903 | 36 488 | 4 274,37 |
| Clair, hub bas | 1 278,96 | 5 115,87 | 11 937,03 | 6 952 | 18 364 | 2 146,28 |
| Clair, hub Y134 | 1 838,51 | 7 354,06 | 17 159,49 | 9 993 | 26 398 | 3 085,32 |
| Clair, hub ≥180 | 1 998,38 | 7 993,55 | 18 651,62 | 10 862 | 28 693 | 3 353,58 |
| Météo mixte, hub normal | 1 738,43 | 6 953,73 | 16 225,38 | 8 689 | 36 488 | 4 391,65 |

Le rapport complet contient aussi les minima, maxima et écarts-types des niveaux I/II : `validation/wind/wind-simulation.txt`. En clair et à hauteur normale, Wind III fournit ici moins que Solar III (20 000 HE), mais continue la nuit. Sous mauvais temps, le solaire tombe à 8 000 / 3 000 HE par cycle tandis que l'éolien bénéficie du bonus. Aucune constante n'a été modifiée à partir de ces résultats. La complémentarité et l'intérêt du stockage sont conservés ; ce n'est pas une étude de tous les consommateurs d'un modpack.

## Hydro — valeurs de départ et comparaison (1er octobre 2026)

Formule : `HE/t = turbineMaxHEPerReferencePeriod / 24000 × débit utilisé / turbineMaxFlow`, avec `débit utilisé = min(Σ débits des pompes, 12 DH/t)` et `débit d'une pompe = débit max × disponibilité de l'eau` (zéro sous 25 %). Aucun facteur d'heure, de météo, d'altitude ou de chute. Les périodes sont des ticks serveur exécutés.

Essai déterministe exécuté (`HydroModelTest`, rapport `build/reports/hydro-periods.txt`) : 24 000 ticks, bassin complet, tampon vidé à chaque tick.

| Installation | Cible HE / 24 000 ticks | Obtenu | HE/t |
|---|---:|---:|---:|
| 1 Pompe I | 4 000 | 3 999 | 0,1667 |
| 1 Pompe II | 12 000 | 12 000 | 0,5 |
| 1 Pompe III | 48 000 | 48 000 | 2,0 |
| 3 Pompes I | 12 000 | 12 000 | 0,5 |
| 2 Pompes II | 24 000 | 24 000 | 1,0 |
| 4 Pompes II (plafond atteint) | 48 000 | 48 000 | 2,0 |
| 2 Pompes III (plafond) | 48 000 | 48 000 | 2,0 |
| 4 Pompes III (plafond) | 48 000 | 48 000 | 2,0 |
| Pompe III, eau à 50 % | 24 000 | 24 000 (±1) | 1,0 |
| Pompe III, eau à 25 % | 12 000 | 12 000 (±1) | 0,5 |
| Pompe III, eau à 24 % | 0 | 0 | 0 |

Le manque d'un HE pour la Pompe I vient du reliquat flottant (1/6 HE par tick) resté dans l'accumulateur, toujours dans `[0, 1)` : tolérance documentée de 1 HE, sans dérive (dix périodes : 40 000 ± 1). Les fractions ne sont jamais arrondies tick par tick.

### Comparaison par niveau

| Source (niveau III) | HE / 24 000 ticks | Variabilité | Emprise |
|---|---:|---|---|
| Solar III | 20 000 clair / 8 000 pluie / 3 000 orage | rien la nuit | 4 cases + ciel libre |
| Wind III | 28 000 nominal ; moyenne simulée 14 921 (clair, hub normal) | vent, météo, altitude | 4 cases + mât 11 + rotor 7×7 libre |
| Hydro (Pompe III + turbine) | 48 000 | constant, jour et nuit | 4 + 8 cases + conduites + bassin 7×7×3 (147 sources) |

Coût direct de l'installation Hydro III, hors recettes HomeCore récursives : Pompe I → II → III (4 fers + 2 cuivres + piston + redstone + Circuit Board ; 2 ors + 2 cuivres + 2 fers + Microprocessor + Control Module ; 2 diamants + 2 ors + 2 fers + Control Module + Communication Module), turbine (2 blocs de fer, 2 blocs de cuivre, 2 pistons, Microprocessor, Control Module, Communication Module) et les conduites (6 fers + 2 cuivres pour 4). Le bassin de 147 sources doit être construit ou trouvé devant l'admission.

### Ajustement du 1er octobre 2026 : Pompe III deux fois plus rapide

Sur demande, la Pompe III remplit deux fois plus vite : `pumpFlow3` 6 → 12 DH/t, `turbineMaxFlow` 6 → 12 DH/t, `turbineMaxHEPerReferencePeriod` 24 000 → 48 000. Le rapport 4 000 HE par DH/t reste identique : les Pompes I et II gardent 4 000 et 12 000 HE par période. Effet secondaire assumé : une turbine plafonne désormais à 2 HE/t, atteignable aussi avec 4 Pompes II. Une Batterie I (40 000 HE) se remplit en 20 000 ticks (16 min 40) au lieu de 33 min 20. Les mondes existants gardent les anciennes valeurs dans `serverconfig/homelink_energy-server.toml` tant que ces trois lignes ne sont pas modifiées.

### Lecture et recommandation (avant l'ajustement ci-dessus)

Avec ces valeurs de départ, une installation Hydro complète fournit **plus que Solar III par cycle clair (+20 %)** et **environ 1,6 fois la moyenne simulée de Wind III**, sans interruption. Elle reste plafonnée à 1 HE/t par turbine, quel que soit le nombre de pompes, et demande une turbine de 8 blocs, un circuit et un grand bassin : sa densité par case occupée est nettement plus faible que celle d'un panneau. La batterie I (8 HE/t) absorbe sans peine la production d'une turbine.

Les valeurs Solar et Wind n'ont pas été touchées. Si les essais en partie montrent qu'Hydro efface les autres sources, l'ajustement recommandé porte sur Hydro seul : `turbineMaxHEPerReferencePeriod = 18000` (0,75 HE/t, entre Wind III moyen et Solar III clair) ou `minimumWaterAvailability` plus élevé. Les consommations réelles de Farm, Quarry et Storage restent à confronter avec leurs interfaces : ce bilan n'est pas une étude de modpack. Les anciens exemples d'interface à 34 ou 38 HE/t ne correspondent à aucun équilibrage validé et ne sont pas repris.
