# HomeLink Energy — développement 0.5.0

Minecraft **1.21.1**, **NeoForge 21.1.251**, **Java 21**. Production solaire, éolienne et hydraulique renouvelable en **HE**, stockage et transfert par le réseau énergétique commun. Les appareils exposent leurs métriques et événements via l'API publique HomeCore.

## Wind Energy — énergie éolienne

Trois éoliennes HomeLink complètent les panneaux : **3 000 / 12 000 / 28 000 HE nominaux par 24 000 ticks**. Elles produisent jour et nuit dans l’Overworld. Ce nominal correspond à un vent à 100 %, une météo claire, un hub entre Y80 et Y119, un rotor libre et une sortie capable de recevoir la production. La production moyenne n’est pas garantie.

Le vent évolue progressivement et se sauvegarde par dimension. `/time set` ne modifie ni son horloge ni les périodes de production. Démarrage à 16 %, arrêt à 13 %. La pluie multiplie le rendement par 1,25 et l’orage par 1,60 ; l’altitude du hub ajoute son multiplicateur, avec un plafond final de 150 %. Le solaire reste compact et prévisible, l’éolien complète la nuit et le mauvais temps, et les batteries amortissent les variations.

Les socles occupent **1×1 / 2×1 / 2×2 blocs**, comme les panneaux solaires du même niveau. Le placement nécessite toute cette emprise libre et centre le mât sur le socle. Chaque partie possède une collision et ouvre la même interface ; casser une partie démonte toute l’éolienne et rend un seul objet. La tour et le rotor restent des modèles étendus. Garder libre le rotor de diamètre nominal **3 / 5 / 7 blocs** et dégager le ciel au-dessus du hub, à **baseY + 4 / 7 / 11**. Le volume exact à libérer tient compte du centrage entre les blocs : **Zone du rotor** l’affiche pendant dix secondes. L’orientation choisie au placement ne change pas le rendement. Clic droit pour lire vent, météo, rendement, HE/t, production de la période et tampon ; **Localiser l’obstacle** indique le bloc gênant.

Le tampon de 1 000 HE expose le **même port HE et le même réseau** que Solar. Une batterie placée directement sous le bloc principal reçoit automatiquement l’énergie ; les câbles cuivre se raccordent aux côtés et au dessous de chaque partie du socle. Toutes les connexions partagent le même tampon et le même producteur. L’excédent est perdu si le tampon est plein. Le cassage perd le tampon et rend uniquement l’objet physique ; les niveaux II et III exigent le niveau précédent dans leur recette. Aucun rattrapage au rechargement, aucune conversion FE/RF et aucun chunk loader.

Les anciennes éoliennes agrandissent leur socle au chargement si les cases nécessaires sont vides. Sinon, elles indiquent **Socle incomplet** et attendent que l’emprise soit libérée, sans écraser les blocs voisins ni perdre leur tampon ou leur identité. Voir la [validation des socles](docs/WIND_FOOTPRINT.md).

Configuration : section `[wind]` du fichier serveur existant. Les bornes sont validées ; des seuils incohérents entre eux activent les paramètres de vent par défaut avec un avertissement dans le journal. Pour les opérateurs de niveau 2 : `/homelink_wind 0.75` fixe temporairement le vent de la dimension courante pendant l’intervalle de cible configuré.

Les métriques HomeCore utilisent un seul type `homelink_energy:wind_turbine`. Les batteries exposent les contributions solaire, éolienne et autres du réseau : ce sont des débits livrés par les producteurs, pas une seconde génération. Ni Dashboard ni Tasks ne sont des dépendances. Les événements d’obstruction et de déconnexion peuvent être consommés par ces mods ; un vent faible n’est pas une alerte.

Résultats et limites : [validation éolienne](docs/WIND_VALIDATION.md), [équilibrage](docs/ENERGY_BALANCE.md), [fichiers changés](docs/WIND_FILES.md). La validation multijoueur visuelle reste manuelle.

## Hydro — pompes, conduites et turbine

La branche Hydro ajoute trois **Pompes Hydro** (1×1×1, 2×1×1, 2×1×2), la **Conduite Hydro** (gros conduit 10/16 à brides 14/16) et une seule **Turbine Hydro** de **2×2×2 blocs**. Chaîne : eau du monde → pompe → conduites → turbine → HE → câbles et batteries existants. Les pompes ne consomment ni HE ni carburant et **ne retirent jamais d'eau** : l'eau est une condition de fonctionnement, comme le soleil ou le vent. Le rejet d'eau à l'avant de la turbine est purement visuel ; aucun bloc d'eau n'est posé.

- Poser la pompe en regardant l'eau : l'admission (grille) fait face au bassin. Fenêtre d'eau devant l'admission : **3×3×2 / 5×5×2 / 7×7×3** sources pour 100 %, minimum 25 %. **Voir la zone d'eau** l'affiche.
- Débits : **1 / 3 / 12 DH/t** (débit abstrait). La turbine utilise au plus **12 DH/t** et produit au plus **48 000 HE par 24 000 ticks (2 HE/t)** ; Pompe I : 0,167 HE/t, Pompe II : 0,5 HE/t, Pompe III : 2 HE/t, jour et nuit, sans bonus de météo ni de hauteur.
- Ports : sortie hydraulique **sur le dessus de la case maître** de la pompe ; entrée Hydro **en haut à l'arrière gauche** de la turbine ; sortie HE sur le **raccord cuivre** du côté bas arrière droit ; les deux cases devant le bas de la façade doivent rester libres (air ou eau), sinon **Évacuation obstruée**.
- Circuit : 1 à 4 pompes, **une seule turbine**, 512 conduites au plus. Deux turbines sur un circuit : les deux s'arrêtent. Les conduites ne se relient jamais aux câbles HE ni à l'irrigation Farm.
- HomeCore : types `homelink_energy:hydro_pump` et `homelink_energy:hydro_turbine`, interrupteur, renommage, rattachement HomeNetwork, métriques et événements de transition. Réseau hydraulique, réseau HE et HomeNetwork restent trois choses distinctes.

Détails, repère local des ports, états, configuration `[hydro]` et dépannage : [docs/HYDRO.md](docs/HYDRO.md). Bilan chiffré : [équilibrage](docs/ENERGY_BALANCE.md#hydro--valeurs-de-départ-et-comparaison-1er-octobre-2026). Le widget agrégé du Dashboard n'est pas modifié (voir les limites).

## JEI et REI

Avec JEI (19.0 ou plus récent) ou REI (16.0 ou plus récent), chaque objet du mod a une page d'information (onglet « i » de JEI, « Information » de REI) qui résume production, capacités et règles de pose, en français et en anglais. Les recettes de fabrication s'y affichent comme les autres. Ces deux mods restent facultatifs et côté client.

## Installer

Copier `homelink_energy-0.5.0.jar` et `homecore-1.13.0.jar` du dossier `build/release/` dans `mods/`, côté client et serveur. Installer NeoForge pour Minecraft 1.21.1 et utiliser Java 21. Ne pas installer deux versions de HomeCore.

HomeCore **1.13.0** (API publique **1.8.0**) est obligatoire et reste un JAR séparé. La dépendance publiée est explicite dans `gradle.properties`. Pour travailler sur les deux projets, activer le composite avec `-PuseLocalDependencies=true` et un checkout voisin de même version ; aucun `publishToMavenLocal` n'est nécessaire.

## Utiliser

Poser une batterie, puis un panneau directement dessus, dans l'Overworld. Le panneau charge automatiquement la batterie par le bas. Clic droit : état, débit potentiel en HE/t, météo, exposition, tampon ou charge de la batterie. Un panneau I produit lentement : ses fractions s'additionnent avant de former un HE entier. Le voyant en haut à droite des interfaces indique la liaison au réseau HomeLink : vert « Relié », rouge « Non relié » ; la charge, la production et le raccordement par câbles restent dans les lignes de l’interface.

Les panneaux occupent désormais **1×1 bloc (I), 2×1 blocs (II) et 2×2 blocs (III)** au sol. Leur largeur suit l'orientation du joueur au placement. Toute l'emprise doit être libre ; un obstacle annule la pose. Les parties d'un ensemble partagent un seul contrôleur et une seule production : clic droit sur n'importe quelle partie pour ouvrir son interface, cassage d'une partie pour démonter l'ensemble. Un seul panneau est récupérable avec l'outil adapté. Toutes les cellules doivent voir le ciel. La sortie automatique vers une batterie se trouve sous la case d'origine ; les sorties par câble sont accessibles sur les côtés et le dessous des autres cases.

Les batteries occupent elles aussi **1×1 bloc (I), 2×1 blocs (II) et 2×2 blocs (III)**. Elles conservent leurs silhouettes : accumulateur avec poignée, deux cellules reliées par un pont cuivre, puis armoire industrielle à trois tiroirs et dissipateurs. Toute l'emprise doit être libre au placement. Chaque partie ouvre la même interface et expose le même stockage HE ; casser une partie démonte l'ensemble et rend un seul objet avec l'outil adapté. Les maillages sont découpés aux limites des cases sans faces superposées aux jointures. Les interfaces reprennent le thème de HomeLink-Farm : cadre métallique, panneaux en creux, vis et accents beige doré.

Les anciennes batteries s'agrandissent automatiquement si l'emprise est vide, en conservant leur charge et leur UUID HomeCore. Un obstacle suspend cet agrandissement et les transferts, avec l'état **Socle incomplet**, jusqu'à ce que l'espace soit dégagé. Aucun bloc voisin n'est écrasé. Voir la [validation des batteries](docs/BATTERY_FOOTPRINT.md).

**Mondes du prototype :** les anciens panneaux II/III posés sur une seule case doivent être cassés puis reposés pour obtenir leur nouvelle emprise. Leur énergie est perdue au cassage comme auparavant. Reposer également les anciens câbles suspendus : le nouveau câble exige un support.

| Bloc | Production par cycle clair complet | Capacité | Entrée / sortie maximale |
|---|---:|---:|---:|
| Solar Panel I | 2 000 HE | 1 000 HE | export automatique du tampon |
| Solar Panel II | 8 000 HE | 1 000 HE | export automatique du tampon |
| Solar Panel III | 20 000 HE | 1 000 HE | export automatique du tampon |
| Battery I | aucune | 40 000 HE | 8 HE/t dans chaque sens |
| Battery II | aucune | 120 000 HE | 32 HE/t dans chaque sens |
| Battery III | aucune | 320 000 HE | 128 HE/t dans chaque sens |

Les plafonds des batteries s'appliquent à l'ensemble des faces et des parties : une seule capacité et un seul budget de charge/décharge par batterie. Le réseau compte une batterie une seule fois, même si plusieurs parties sont raccordées. Une batterie commence vide et ne s'autodécharge pas. L'onglet créatif HomeLink Energy regroupe les quinze blocs.

### Soleil et temps

- Cycle de référence : 24 000 ticks, nuit déjà comprise dans le total. Courbe sinus de 0 à 12 000 ; production nulle ensuite. Les valeurs supposent une sortie capable d'accepter toute l'énergie.
- Clair 100 %, pluie 40 %, orage 15 %. L'orage remplace la pluie.
- Exposition **stricte** : tout bloc non-air au-dessus bloque, même verre, eau ou autre panneau. Seul le panneau supérieur d'une pile est exposé. C'est une règle volontaire de gameplay.
- La hauteur de surface du monde fournit le cache de colonne. Une notification voisine invalide l'exposition ; une vérification périodique intervient aussi, toutes les 40 ticks par défaut, configurable de 1 à 1 200. Une modification éloignée peut donc prendre ce délai avant d'être détectée. Aucune colonne n'est parcourue à chaque tick.
- Nether, End et autres dimensions : `UNSUPPORTED_DIMENSION`, sans production.
- Seuls les ticks réellement exécutés produisent. Sommeil, `/time`, fermeture du serveur et chunks déchargés ne déclenchent aucun rattrapage. Aucun chunk n'est chargé de force par le mod.
- Avec `doDaylightCycle=false`, chaque tick exécuté utilise l'heure figée : midi produit continuellement, nuit ne produit rien. Le total « par cycle » n'est alors plus un quota journalier.
- Tampon plein : les HE entiers non acceptés sont perdus ; le reliquat reste strictement inférieur à 1 HE. Le compteur de production compte uniquement les HE acceptés, jamais les transferts.

### Cassage, repose, amélioration et sauvegarde

L'énergie et les fractions restent dans la sauvegarde du bloc, **jamais dans l'item lâché**. Casser une machine détruit son énergie ; la reposer ou l'améliorer à la table de craft donne une machine vide, avec nouveaux compteurs. Les recettes de niveaux supérieurs consomment l'ancien panneau ou l'ancienne batterie. Aucun upgrade sur place n'est ajouté. Les blocs sauvegardent énergie, fraction, compteurs, identité et liaison HomeNetwork ; les paramètres de production sont ceux du fichier de configuration serveur du monde.

### Câbles et HomeCore déjà présents

Les câbles sont des pistes fines, posées comme de la redstone sur le sol, les murs et le plafond. Une face solide ou un port HE sert de support. On peut ajouter plusieurs faces dans une même case pour former une jonction intérieure ; chaque face consomme un câble. Les pistes se raccordent aussi autour d'une arête extérieure. Retirer un support détache la face correspondante et rend son câble. Le câble ne fournit aucun signal redstone. Un câble posé sur une face d'un bloc plein alimente aussi la machine posée sur la face opposée de ce bloc : par exemple, un câble sous un bloc de roche alimente la station posée dessus. Un seul bloc ordinaire est traversé (jamais une machine ni un câble).

Les câbles relient les sorties latérales/inférieures des panneaux et toutes les faces des batteries. Le sommet solaire n'est pas un port. Les câbles ne stockent rien et ne convertissent aucune unité. Les faces d'une même case forment une jonction commune ; deux pistes adjacentes sur des plans distincts ne se raccordent pas automatiquement. Le graphe est recalculé lorsqu'un câble ou un voisin change ; les tronçons déchargés sont retirés.

Dans chaque réseau : sorties des producteurs vers consommateurs, surplus vers batteries, puis batteries vers consommateurs encore demandeurs. Jamais batterie vers batterie. L'export direct vers la batterie sous un panneau intervient avant la distribution par câbles. La limite configurable est de 1 024 nœuds (câbles + machines) ; au-delà, `NETWORK_TOO_LARGE` arrête ce réseau.

Le bouton HomeLink permet de rattacher une machine à un HomeNetwork géré par le joueur. Le serveur vérifie le menu ouvert, sa position, sa dimension, sa distance et les permissions HomeCore. Les appareils exposent métriques HE et HE/t, état et événements de transition (production, obstruction, batterie basse/vide/pleine, connexion). Les alarmes ont une hystérésis et ne sont pas émises à chaque tick.

**Intégration :** les machines de HomeLink Farm, Quarry, Storage et Dashboard consomment des HE à travers le contrat énergie de HomeCore 1.10.0 (`EnergyApi.BLOCK`) et déclarent HomeLink Energy comme dépendance obligatoire. Energy ne dépend ni du Dashboard ni de Tasks.

**Rattachement depuis le Dashboard :** les panneaux, éoliennes et batteries implémentent `NetworkMember` (HomeCore 1.10.0). Le Dashboard peut donc les lister et les ajouter à un réseau ; le bloc garde le même rattachement qu'avec son propre bouton HomeLink. Un nom donné à l'enclume est conservé par le bloc, affiché dans son menu et dans le Dashboard, et rendu avec l'objet quand on le casse.

**Marche/arrêt et renommage depuis le Dashboard :** tous les blocs Energy implémentent `Renamable` et se renomment depuis l'écran Actions du Dashboard (50 caractères au plus ; un nom vide rétablit le nom du bloc). Les panneaux solaires et les éoliennes implémentent aussi `Switchable` : éteints, ils ne produisent plus, affichent l'état « Éteint » et restent pilotables pour être rallumés. Les batteries n'ont pas d'interrupteur. HomeCore 1.11.0.

## Recettes exactes

Toutes sont façonnées à la table vanilla, avec les composants HomeCore fabriqués à l'Electronics Workbench. Aucune recette ne recrée ou ne contourne leur fabrication. Les quantités ci-dessous sont les ajouts de chaque étape, hors coût récursif du niveau précédent.

| Résultat | Grille (3 lignes) | Légende et quantités |
|---|---|---|
| 1 panneau I | `GGG / QRQ / CBC` | G verre ×3 ; Q quartz ×2 ; R redstone ×1 ; C lingot cuivre ×2 ; B Circuit Board ×1 |
| 1 panneau II | `OMO / CPC / OXO` | O lingot or ×4 ; M Microprocessor ×1 ; C lingot cuivre ×2 ; P panneau I ×1 ; X Communication Module ×1 |
| 1 panneau III | `DMD / MPM / DKD` | D diamant ×4 ; M Microprocessor ×3 ; P panneau II ×1 ; K Control Module ×1 |
| 1 batterie I | `ICI / RBR / ICI` | I lingot fer ×4 ; C lingot cuivre ×2 ; R redstone ×2 ; B Circuit Board ×1 |
| 1 batterie II | `OMO / CBC / ORO` | O lingot or ×4 ; M Microprocessor ×1 ; C lingot cuivre ×2 ; B batterie I ×1 ; R bloc redstone ×1 |
| 1 batterie III | `DOD / MBM / CKC` | D diamant ×2 ; O bloc or ×1 ; M Microprocessor ×2 ; B batterie II ×1 ; C bloc cuivre ×2 ; K Control Module ×1 |
| 8 câbles cuivre | `CCC / WRW / CCC` | C lingot cuivre ×6 ; W laine (toute couleur) ×2 ; R redstone ×1 |
| 4 conduites Hydro | `ICI / I.I / ICI` | I lingot fer ×6 ; C lingot cuivre ×2 |
| 1 pompe Hydro I | `IBI / CPC / IRI` | I lingot fer ×4 ; B Circuit Board ×1 ; C lingot cuivre ×2 ; P piston ×1 ; R redstone ×1 |
| 1 pompe Hydro II | `GMG / CTC / IKI` | G lingot or ×2 ; M Microprocessor ×1 ; C lingot cuivre ×2 ; T pompe I ×1 ; I lingot fer ×2 ; K Control Module ×1 |
| 1 pompe Hydro III | `DKD / GTG / IOI` | D diamant ×2 ; K Control Module ×1 ; G lingot or ×2 ; T pompe II ×1 ; I lingot fer ×2 ; O Communication Module ×1 |
| 1 turbine Hydro | `IKI / PMP / COC` | I bloc fer ×2 ; K Control Module ×1 ; P piston ×2 ; M Microprocessor ×1 ; C bloc cuivre ×2 ; O Communication Module ×1 |

IDs HomeCore vérifiés : `homecore:homelink_circuit_board`, `homecore:homelink_microprocessor`, `homecore:homelink_communication_module`, `homecore:homelink_control_module`.

## Construire et vérifier

Pour développer localement, placer HomeCore 1.13.0 dans `../HomeCore` ou préciser `-Phomecore_dir=<chemin>`. Le composite utilise ces sources avec contrôle de version. Après publication du package, `-PuseLocalDependencies=false` utilise la dépendance Maven exacte, avec les identifiants GitHub Packages décrits dans la documentation HomeCore. Aucun `publishToMavenLocal` n'est nécessaire.

```powershell
# Depuis la racine ; JAVA_HOME doit désigner un JDK 21.
# Facultatif : vérifier le checkout local sans le modifier.
.\scripts\bootstrap-homecore.ps1
.\gradlew.bat build --console=plain
.\gradlew.bat runGameTestServer --console=plain
.\gradlew.bat runSmoke --console=plain
.\gradlew.bat runSmoke -PsmokeLanguage=en_us --console=plain
.\gradlew.bat releaseBundle --console=plain
```

Le script facultatif vérifie seulement la présence et la version du checkout voisin ; il ne clone aucun dépôt, ne change aucun commit et ne publie rien. Des sources locales modifiées peuvent être testées par composite. La version doit correspondre à `homecore_version` dans `gradle.properties`. Pour forcer les tests unitaires : `gradlew test --rerun-tasks --console=plain`.

Configuration : `serverconfig/homelink_energy-server.toml` dans le monde ; NeoForge peut utiliser `config/` pour les exécutions de vérification. Les valeurs sont bornées. Java trouvé directement dans le PATH était Java 8 sur ce poste ; les compilations et jeux de cette livraison ont utilisé la toolchain Java 21.

Les textures 16×16 anthracite/gris/cuivre et cellules bleu sombre proviennent de `scripts/GenerateTextures.java`. Les neuf matériaux éoliens 32×32 (acier peint, graphite, cuivre, laiton, pales, grilles et plaques) proviennent de `scripts/GenerateWindTextures.java`. Les maillages détaillés, modèles d'items complets et variantes orientées sont générés par `node scripts/wind-models.cjs` puis `node scripts/generate-models.cjs`. Les audits `scripts/audit-models.cjs` et `scripts/audit-assemblies.cjs` contrôlent les surfaces superposées susceptibles de scintiller. Voir le [rapport visuel et les captures](docs/VISUAL_VALIDATION.md). Traductions `fr_fr` et `en_us` incluses.

Voir [équilibrage](docs/ENERGY_BALANCE.md), [validation et limites](docs/VALIDATION.md), [audit HomeCore](docs/HOMECORE_AUDIT.md) et [commandes exécutées](docs/WORK_LOG.md). `archive/pre-part1-workspace.zip` conserve le prototype initial avant adaptation.
