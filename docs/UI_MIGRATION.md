# Interfaces Energy et HomeCore UI Kit

## Français

HomeLink Energy 0.5.0 utilise HomeCore **1.14.0 / API 1.9.0** pour ses interfaces.
`EnergyTheme` et `EnergyButton` sont supprimés. Battery, Solar, Wind, Hydro Pump et
Hydro Turbine utilisent directement `HomeLinkTheme`, `HomeLinkUi` et
`HomeLinkButton`, sans installer Dashboard.

Le header mesure 29 px et les boutons 18 px. Les tailles préférées restent
304 × 210 / 224 / 286 / 262 / 300 selon la machine. Les menus étant sans slots,
`HomeLinkScreenLayout.fit` réduit la surface lorsque le GUI scale ou la fenêtre
l'exige. La liaison HomeNetwork et les actions d'overlay restent fixes au bas de
l'écran. Seule la télémétrie défile : molette dans le panneau, PageUp/PageDown,
avec un indicateur de position et clipping dans des bornes absolues.

Les libellés et valeurs trop longs utilisent l'ellipsis du kit et une tooltip
complète sur leur ligne visible. Les boutons gardent narration vanilla et focus
clavier cuivre. Les états sont toujours accompagnés de texte. Les jauges d'eau
conservent leur couleur métier `0xFF6FA8B8`, et les overlays monde restent locaux.
Aucun comportement de production, réseau ou machine n'est transféré dans le kit.

Pour un nouvel écran, étendre `EnergyScreen` pour la télémétrie locale ou utiliser
directement le package client public HomeCore. Ne pas créer une nouvelle palette.
Les imports UI restent limités aux classes client ; la dépendance commune
HomeCore reste un JAR séparé et un composite utilise ses sources locales exactes.

Le smoke existant ajoute une revue distincte après les scénarios Hydro : les
cinq screens sont recréés depuis les menus réellement synchronisés, puis vérifiés
à 640 × 360, 320 × 240 et 427 × 240 pixels GUI. Il contrôle présence/bornes des
actions, hauteur officielle, focus/tabulation, molette/PageUp/PageDown et un pixel
du cadre réellement rendu, avant d'écrire les captures PNG. Le choix de langue
reste `-PsmokeLanguage=fr_fr` ou `en_us`.

```powershell
.\gradlew.bat build test
.\gradlew.bat runGameTestServer
.\gradlew.bat runSmoke -PsmokeLanguage=fr_fr
.\gradlew.bat runSmoke -PsmokeLanguage=en_us
```

Ces commandes décrivent les vérifications prévues et ne constituent pas un
résultat d'exécution. La nouvelle revue exige `ENERGY_GUI_SMOKE_OK` avant le
marqueur global `ENERGY_SMOKE_OK`. Les anciens rapports de validation décrivent
leurs versions et dates historiques.

## English

Energy 0.5.0 now consumes HomeCore **1.14.0 / API 1.9.0** directly for its UI.
The local `EnergyTheme` and `EnergyButton` are removed. All five machine screens
use the shared palette, frame/panels, gauges and vanilla-backed buttons; Dashboard
is not required. The header is 29 GUI pixels and controls are 18 pixels high.

The preferred width remains 304 with per-machine heights 210, 224, 286, 262 and
300. These menus have no inventory slots, so their surfaces fit the scaled window.
Network and overlay actions stay visible at the bottom; only telemetry scrolls
through the mouse wheel or PageUp/PageDown, with clipping and a position marker.
Clipped labels/values have ellipsis and a full tooltip on the visible row.
Native narration and visible keyboard focus are retained.

Water gauges and world overlays preserve their domain colors and behavior.
Production, transport, synchronization and machine logic are unchanged. New Energy
screens use the public HomeCore client kit rather than copying another theme.
HomeCore remains a separate dependency, resolved explicitly or through a local
composite; the build does not publish an artifact.

The existing in-world smoke now ends with a separate five-screen review using real
synchronized menu snapshots at three GUI sizes. It checks bounds, controls,
focus/tab traversal, scrolling and a rendered framebuffer pixel before saving PNGs.
Use the commands above for French and English. `ENERGY_GUI_SMOKE_OK` must precede
the overall success marker. Documentation alone does not claim these commands ran;
historical reports apply only to their recorded versions and dates.
