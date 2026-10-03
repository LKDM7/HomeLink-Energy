## État actuel — UI Kit

Energy 0.5.0 dépend désormais de HomeCore 1.14.0 / API 1.9.0. Le chrome partagé vient de l'API client publique ; voir [UI_MIGRATION.md](UI_MIGRATION.md). Les audits ci-dessous sont historiques et leurs anciennes versions de dépendance ne décrivent pas l'installation actuelle.

# Audit de la dépendance

Inspection du 26 septembre 2026, avant adaptation du code.

- Dépôt demandé : https://github.com/LKDM7/HomeCore. L'ouverture web anonyme retournait 404 ; `git ls-remote origin HEAD` a réussi avec l'environnement Git local.
- Commit local HEAD et HEAD distant identiques : `fecc72b70cbe31d8a4b667f70ff866e400925385` (« Add HomeLink Communication and Control modules »).
- `git show HEAD:gradle.properties` : Minecraft 1.21.1, NeoForge 21.1.250, Java 21 dans le build, mod HomeCore 1.7.0. Compatible avec le NeoForge 21.1.251 utilisé par Energy.
- Copie voisine préexistante : version changée à 1.8.0, API_VERSION changée de 1.3.0 à 1.4.0, répertoire `api/energy` non suivi. Ces modifications n'ont pas été réalisées ni annulées pendant cette tâche.
- Le prototype Energy dépendait de ces classes non commitées. Verrouiller uniquement « 1.8.0 » n'aurait pas permis de reproduire le build.
- Solution : clone local isolé sans hardlinks, checkout détaché du commit vérifié, origine GitHub conservée. Vérification du commit, de l'absence de modifications et de la version avant chaque configuration Gradle ; manifest du JAR portant le commit.

## API réellement disponible

API publique `fr.lkdm.homecore.api` : DashboardAPI, appareils, schémas, métriques, actions, événements, HomeNetworks, permissions et registres/providers. Les quatre composants requis sont enregistrés sous les IDs documentés dans le README. Leur registre a été lu pour vérification, sans import depuis Energy. Les recettes Energy référencent uniquement leurs IDs.

Le commit ne contient **aucun port énergétique**. Le record public `api.metric.Energy` est décrit en **FE**. Energy ne l'emploie pas pour présenter des HE : stockage/capacité utilisent `MetricTypes.LONG` avec un `Unit` HE propre au namespace Energy ; les débits utilisent `DOUBLE` et HE/t. Aucun changement de référence 4 000 HE/charbon et aucune conversion HE↔FE.

L'abstraction locale `HePort` et sa capability `homelink_energy:he_port` appartiennent à HomeLink Energy. Les rôles et directions servent au réseau local. Aucun fichier n'est créé sous le package HomeCore, aucun package interne HomeCore n'est importé, aucun JAR HomeCore n'est embarqué dans le JAR Energy.

## Intégrations voisines

Inspection en lecture seule de HomeLinkQuarry et FarmLink : un brouillon `QuarryPowerMode` mentionne HE mais aucun fournisseur de port HE utilisable n'a été trouvé dans l'implémentation de la Quarry. FarmBot utilise une batterie interne `double` et sa recharge propre. Ces mécanismes ne sont pas automatiquement assimilés à HE. Les consommateurs réels nécessitent un contrat d'intégration et leurs règles de consommation, à définir. Les widgets Dashboard et règles Tasks ne sont pas modifiés.

Le build livré accepte HomeCore **exactement 1.7.0** ; il ne prétend pas supporter la copie expérimentale 1.8.0. Ne pas remplacer HomeCore dans un modpack exigeant 1.8.0 sans vérifier ses autres dépendances.

## Extension éolienne — audit du 27 septembre 2026

Le checkout réellement lié `.dependencies/HomeCore` reste propre au commit fecc72b70cbe31d8a4b667f70ff866e400925385. API publique inspectée : DashboardDevice/DeviceSchema/DeviceStatus, DeviceMetric/MetricTypes/Unit/UpdatePolicy, DeviceEvent/DeviceEventBus, DeviceProviderRegistry/DeviceRegistry, Capabilities et HomeNetworkManager/permissions. Registre des quatre composants vérifié dans les sources et de nouveau dans les GameTests de recettes. Aucun import interne HomeCore ajouté. Le dépôt GitHub officiel retourne encore 404 à la lecture web anonyme ; les sources du checkout fixé et compilé font autorité.

HomeCore Percentage est limité à [0,100] : le rendement éolien et le pourcentage de production utilisent donc DOUBLE + Unit.PERCENT. Le test serveur contrôle réellement une valeur de 150 %. Le nominal utilise un Unit HE / 24 000 ticks propre au namespace Energy. Aucun Energy FE de HomeCore n'est réutilisé pour des HE. Les politiques STATIC, NORMAL et ON_CHANGE sont des métadonnées de planification ; la fréquence réelle est pilotée par le cycle existant des appareils.
