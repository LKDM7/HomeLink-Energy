# ⚡ HomeLink Energy

🇬🇧 **English** · 🇫🇷 **[Français plus bas](#-français)**

---

## 🇬🇧 English

**Renewable power for your connected base.**

HomeLink Energy adds solar panels, wind turbines, a hydro branch and batteries. They all feed one shared **HE** (HomeLink Energy) network that powers the machines of the other HomeLink mods, and the batteries keep the surplus for the night and bad weather.

> ⚠️ **Requires [HomeCore](https://www.curseforge.com/minecraft/mc-mods/homecore).**

### ✨ Features

#### ☀️ Solar panels
- **Three tiers**: 1×1, 2×1 and 2×2 blocks.
- Clear-sky output per cycle: **2,000 / 8,000 / 20,000 HE**.
- They need open sky: any block overhead blocks them, even glass or water.

#### 🌬️ Wind turbines
- **Three tiers**: 1×1, 2×1 and 2×2 blocks.
- Nominal output: **3,000 / 12,000 / 28,000 HE** per Minecraft day (24,000 ticks).
- They run **day and night** in the Overworld. Rain and storms raise the output, and so does hub height (up to 150 %).
- The **Rotor area** button shows the space the rotor needs.

#### 💧 Hydro branch
- **Three pumps** (I to III), pipes and one 2×2×2 **turbine**.
- Pumps need water in front of their intake, but never consume water or fuel.
- The turbine produces up to **48,000 HE** per Minecraft day.

#### 🔋 Batteries and cables
- **Three battery tiers**: **40,000 / 120,000 / 320,000 HE**, charging and discharging at 8, 32 and 128 HE/t.
- **Cables** run along floors, walls and ceilings to connect producers, batteries and machines.
- A machine that spans several blocks has a single controller and a single energy buffer. Breaking any part dismantles the whole machine.

#### 🏠 HomeCore integration
- Link your machines to a HomeNetwork and follow their output (HE, HE/t) from the **HomeLink Dashboard**.
- Turn solar panels and wind turbines on or off, and rename any machine.
- Events: production, obstruction, battery low or full, connection.

#### 📖 Built-in help
- Every machine screen shows its status, potential HE/t, weather, exposure and charge.
- With **JEI** or **REI** (optional), every block has an information page, in English and French.

### 🧱 Blocks

| Block | Role |
| --- | --- |
| **Solar Panel** (I to III) | Produces HE under open sky |
| **Wind Turbine** (I to III) | Produces HE day and night, more in bad weather |
| **Hydro Pump** (I to III) | Feeds the hydro turbine from water |
| **Hydro Pipe** | Connects pumps to the turbine |
| **Hydro Turbine** | 2×2×2 generator for the hydro branch |
| **Battery** (I to III) | Stores the surplus HE |
| **Copper Energy Cable** | Carries HE between machines |

### 🚀 Quick start

1. Place a **battery** on the ground in the Overworld.
2. Place a **solar panel** directly on top of it. The panel charges the battery from below.
3. Right-click a machine to see its status and charge.
4. Add wind turbines or a hydro branch for power at night and in bad weather.

### 🔌 Compatible mods

HE powers the machines of the other HomeLink mods, for example:

- **HomeLink Storage**: Storage Controller and Deposit
- **HomeLink Farm**: pumps and FarmBot stations
- **HomeLink Quarry**: automatic quarries
- **HomeLink Furnace**: electric parallel furnaces
- **HomeLink Dashboard**: energy balance of the whole network

### 📝 Good to know

- Energy stays in the block, **not** in the dropped item. Breaking a machine loses its stored energy.
- Upgrade recipes consume the previous tier.
- Production only happens while the chunk is loaded. Sleeping, `/time` and server downtime are never caught up, and the mod never force-loads chunks.
- The Nether, the End and other dimensions do not produce energy.

### 📦 Installation

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251+ |
| HomeCore | 1.14.0+ (required) |
| JEI / REI | optional |

Install the mod on **both client and server**.

---

## 🇫🇷 Français

**L'énergie renouvelable de votre base connectée.**

HomeLink Energy ajoute des panneaux solaires, des éoliennes, une branche hydraulique et des batteries. Ils alimentent tous un même réseau **HE** (HomeLink Energy) qui fait tourner les machines des autres mods HomeLink, et les batteries gardent le surplus pour la nuit et le mauvais temps.

> ⚠️ **Ce mod nécessite [HomeCore](https://www.curseforge.com/minecraft/mc-mods/homecore).**

### ✨ Fonctionnalités

#### ☀️ Panneaux solaires
- **Trois niveaux** : 1×1, 2×1 et 2×2 blocs.
- Production par cycle, par ciel clair : **2 000 / 8 000 / 20 000 HE**.
- Ils ont besoin d'un ciel dégagé : tout bloc au-dessus les bloque, même le verre ou l'eau.

#### 🌬️ Éoliennes
- **Trois niveaux** : 1×1, 2×1 et 2×2 blocs.
- Production nominale : **3 000 / 12 000 / 28 000 HE** par journée Minecraft (24 000 ticks).
- Elles tournent **jour et nuit** dans l'Overworld. La pluie et l'orage augmentent le rendement, tout comme la hauteur du moyeu (jusqu'à 150 %).
- Le bouton **Zone du rotor** montre l'espace dont le rotor a besoin.

#### 💧 Branche hydraulique
- **Trois pompes** (I à III), des conduites et une **turbine** de 2×2×2 blocs.
- Les pompes ont besoin d'eau devant leur admission, mais ne consomment ni eau ni combustible.
- La turbine produit jusqu'à **48 000 HE** par journée Minecraft.

#### 🔋 Batteries et câbles
- **Trois niveaux de batterie** : **40 000 / 120 000 / 320 000 HE**, avec une charge et une décharge de 8, 32 et 128 HE/t.
- Les **câbles** suivent le sol, les murs et le plafond pour relier producteurs, batteries et machines.
- Une machine sur plusieurs blocs a un seul contrôleur et un seul tampon d'énergie. Casser une partie démonte toute la machine.

#### 🏠 Intégration HomeCore
- Rattachez vos machines à un HomeNetwork et suivez leur production (HE, HE/t) depuis le **HomeLink Dashboard**.
- Allumez ou éteignez panneaux et éoliennes, et renommez n'importe quelle machine.
- Événements : production, obstruction, batterie basse ou pleine, connexion.

#### 📖 Aide intégrée
- Chaque écran de machine affiche son état, son débit potentiel en HE/t, la météo, l'exposition et la charge.
- Avec **JEI** ou **REI** (facultatifs), chaque bloc a une page d'information, en français et en anglais.

### 🧱 Blocs

| Bloc | Rôle |
| --- | --- |
| **Panneau solaire** (I à III) | Produit des HE sous un ciel dégagé |
| **Éolienne** (I à III) | Produit des HE jour et nuit, davantage par mauvais temps |
| **Pompe Hydro** (I à III) | Alimente la turbine à partir de l'eau |
| **Conduite Hydro** | Relie les pompes à la turbine |
| **Turbine Hydro** | Générateur 2×2×2 de la branche hydraulique |
| **Batterie** (I à III) | Stocke le surplus de HE |
| **Câble énergétique en cuivre** | Transporte les HE entre les machines |

### 🚀 Démarrage rapide

1. Posez une **batterie** au sol dans l'Overworld.
2. Posez un **panneau solaire** directement dessus. Le panneau charge la batterie par le bas.
3. Faites un clic droit sur une machine pour voir son état et sa charge.
4. Ajoutez des éoliennes ou une branche hydraulique pour produire la nuit et par mauvais temps.

### 🔌 Mods compatibles

Les HE alimentent les machines des autres mods HomeLink, par exemple :

- **HomeLink Storage** : Storage Controller et Deposit
- **HomeLink Farm** : pompes et stations FarmBot
- **HomeLink Quarry** : carrières automatiques
- **HomeLink Furnace** : fours électriques parallèles
- **HomeLink Dashboard** : bilan énergétique de tout le réseau

### 📝 À savoir

- L'énergie reste dans le bloc, **pas** dans l'objet lâché. Casser une machine fait perdre l'énergie stockée.
- Les recettes d'amélioration consomment le niveau précédent.
- La production n'a lieu que lorsque le chunk est chargé. Le sommeil, `/time` et l'arrêt du serveur ne sont jamais rattrapés, et le mod ne charge jamais de chunk de force.
- Le Nether, l'End et les autres dimensions ne produisent pas d'énergie.

### 📦 Installation

| Composant | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251+ |
| HomeCore | 1.14.0+ (obligatoire) |
| JEI / REI | facultatifs |

Installez le mod sur le **client et le serveur**.

---

*Apache 2.0 License · Licence Apache 2.0 — by / par LKDM*
