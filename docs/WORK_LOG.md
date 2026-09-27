# Travail et commandes exécutées

Date : 26 septembre 2026. Workspace Windows / PowerShell, Java 21 par la toolchain Gradle. Sources préexistantes non commitées : elles ont été inspectées et adaptées, pas annoncées comme entièrement nouvelles.

## Étapes

1. Audit : fichiers Gradle, sources, registres, recettes, menus, tests, état Git ; inspection en lecture seule du HomeCore voisin et de son API committée. Build initial réussi ; réexécution explicite des tests.
2. Fondation reproductible : archive du prototype, clone propre isolé de HomeCore 1.7.0, verrou de commit, adaptation à une capability HE locale. Compilation et tests corrigés puis réussis.
3. Modèle et blocs : protections de tick/ports, persistance, réentrance, tests supplémentaires. Réintégration de la partie 2 après confirmation explicite de l'utilisateur : batteries II/III, câbles et pont public HomeCore. Aucune modification de HomeCore ni des autres mods.
4. Validation : GameTests dédiés, test de 100 panneaux, vrai déchargement/rechargement disque de chunk, client dans les deux langues, documentation et paquet de développement.

## Commandes réellement lancées

Les lectures et recherches ont utilisé `rg`, `Get-Content`, `Get-ChildItem`, `git status`, `git show`, `git ls-tree`, `git diff`. Principales commandes de construction et de vérification :

```powershell
git -C ..\HomeCore remote -v
git -C ..\HomeCore rev-parse HEAD
git -C ..\HomeCore status --short
git -C ..\HomeCore show HEAD:gradle.properties
git -C ..\HomeCore ls-remote origin HEAD
java -version
.\gradlew.bat build --console=plain
.\gradlew.bat test --rerun-tasks --console=plain
git clone --no-hardlinks --no-checkout ..\HomeCore .dependencies\HomeCore
git -C .dependencies\HomeCore checkout --detach fecc72b70cbe31d8a4b667f70ff866e400925385
git -C .dependencies\HomeCore remote set-url origin https://github.com/LKDM7/HomeCore.git
Compress-Archive -Path src,scripts,build.gradle,settings.gradle,gradle.properties -DestinationPath archive\pre-part1-workspace.zip -CompressionLevel Optimal
Expand-Archive -LiteralPath archive\pre-part1-workspace.zip -DestinationPath build\original-prototype -Force
node scripts/finalize-part1.cjs
node scripts/generate-test-structure.cjs
node scripts/restore-part2.cjs
.\gradlew.bat build runGameTestServer --console=plain
.\gradlew.bat runSmoke --console=plain
.\gradlew.bat runSmoke -PsmokeLanguage=en_us --console=plain
```

Les scripts de migration `finalize-part1.cjs` et `restore-part2.cjs` étaient ponctuels, puis retirés pour éviter une réapplication accidentelle. Le générateur de structure de test reste disponible. Les commandes `build` et `build runGameTestServer` ont été relancées après chaque correction pertinente ; leurs échecs intermédiaires sont consignés dans VALIDATION.md. Une première tentative `node -e` de création de NBT a échoué à cause des guillemets PowerShell : elle a été remplacée par le fichier `.cjs`. `python --version` a indiqué que Python n'était pas installé ; aucun test Python n'a été exécuté.

La collecte finale et la commande `releaseBundle` sont consignées dans `docs/validation/results.json` une fois exécutées. Les sorties JUnit, journaux GameTest et client sont conservés dans `docs/validation`. La liste de fichiers réellement modifiés/ajoutés par rapport au prototype archivé se trouve dans [CHANGED_FILES.md](CHANGED_FILES.md).

## Terminal

## Refonte des modèles, interfaces et câbles — 27 septembre 2026

Référence graphique consultée dans la copie locale `../FarmLink`, dont le remote est `https://github.com/LKDM7/HomeLink-Farm.git`. Reprise des couleurs et primitives de `FarmTheme` et `FarmButton`, sans ajout d'une dépendance à Farm. Modèles JSON détaillés générés par `scripts/generate-models.cjs` avec les textures existantes.

Emprises solaires réelles 1×1, 2×1 et 2×2, quatre orientations, un contrôleur partagé, refus des obstacles, démontage complet, ports secondaires et contrôle du ciel sur toutes les cases. Câbles fins sur les six faces, supports, jonctions dans les angles, quantités d'items conservées et graphe électrique adapté.

Commandes exécutées : `node scripts/generate-models.cjs`, `node scripts/generate-test-structure.cjs`, `gradlew.bat build`, `gradlew.bat runGameTestServer`, puis `gradlew.bat runSmoke` en français et avec `-PsmokeLanguage=en_us`. Résultat : 42 tests unitaires et 42 GameTests réussis ; assertions client et captures des modèles et interfaces dans les deux langues. Deux fixtures ont été corrigées après le premier passage : espacement des panneaux et attente du chargement effectif du chunk distant. La dernière compilation et les contrôles du JAR ont réussi. Rapport et empreintes dans `docs/validation/results.json` ; paquet local assemblé par `releaseBundle`.

Les captures françaises des modèles et des deux GUI ont été inspectées. Le dépôt ne comporte toujours aucun commit ; aucun autre mod n'a été modifié. Les anciennes installations en monde nécessitent la repose des panneaux II/III et des câbles suspendus, comme indiqué dans le README.

