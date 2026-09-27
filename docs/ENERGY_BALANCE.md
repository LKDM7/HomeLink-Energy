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
