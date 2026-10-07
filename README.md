# Schematic Tiling (Javafied)

A **Fabric mod addon for [Litematica](https://github.com/maruohon/litematica)** that tiles modular, parametric schematics in-game using knapsack optimization.

This mod is an in-game Java port of the original Python tool [**Schematic-Tiling by EQUENOS-2**](https://github.com/EQUENOS-2/Schematic-Tiling).

---

## Features

- **Native Litematica Integration:** Seamlessly integrated into Litematica's Schematic Browser.
- **In-Game Tiling:** Generates tiled schematics on-the-fly without leaving Minecraft or running external Python scripts.
- **Dynamic Optimization:** Solves the unbounded knapsack problem for each modular sub-region to fill target dimensions (Width and/or Length) with minimal overhang or exact matches.
- **Auto-Load & Placement:** Generates the tiled `.litematic` file, automatically saves it to your schematics folder, and can directly load it into memory and create a placement in your world.
- **Multi-Version Support:** Supports **1.21.4**, **1.21.10**, **1.21.11**, **26.2**, and **26.3**.

---

## How to Create Parts Schematics

For detailed documentation, sub-region naming rules, syntax (`[dx, dy, dz]`), and guides on designing modular schematics, please refer to the original repository:
> 📖 **[EQUENOS-2 / Schematic-Tiling Documentation](https://github.com/EQUENOS-2/Schematic-Tiling)**

A parts schematic contains sub-regions named after tiling groups, subgroups, and units (e.g. `[16,0,0]`, `[0,0,16]`, `End`, `Start`, etc.).

---

## In-Game Usage

### Where to Find the "Tile" Button

1. Place your parts schematic (e.g., `MyFarm_Parts.litematic`) into your `.minecraft/schematics/` folder.
2. Open the Litematica Menu in-game (default key: `M`).
3. Click **Load Schematics** to open the schematic file browser.
4. Select your parts schematic in the list.
5. The **Tile** button will appear on the bottom button bar, right next to the **Material List** button!

```
[ Load Schematic ] [ Material List ] [ Tile ] [ Rename Schematic ] ... [ Main Menu ]
```

### Tiling a Schematic

1. Click the **Tile** button to open the **Schematic Tiler** dialog.
2. Enter your desired **Width** and **Length** (if the schematic supports 2D tiling).
3. Optionally adjust the **Author** name.
4. Configure options:
   - **Load to memory**: Loads the tiled schematic directly into Litematica upon completion.
   - **Create placement**: Creates an active schematic placement in the world ready for building.
5. Click **Apply**.
6. The new tiled `.litematic` schematic will be saved into your schematics folder and immediately ready for use!

---

## Supported Versions & Branches

| Minecraft Version | Branch | Litematica Version | MaLiLib Version |
| :--- | :--- | :--- | :--- |
| **1.21.11** | `master` | `0.26.16+` | `0.27.20+` |
| **1.21.10** | `1.21.10` | `0.24.9+` | `0.26.8+` |
| **1.21.4** | `1.21.4` | `0.21.7+` | `0.23.5+` |
| **26.2** | `26.2` | `0.28.8+` | `0.29.6+` |
| **26.3** | `26.3` | `0.29.1+` | `0.30.2+` |

---

## Building from Source

To compile the mod locally:

```bash
git clone https://github.com/Selestor1462/Schematic-Tiling-javafied.git
cd Schematic-Tiling-javafied
gradle build
```

The compiled mod jar will be in `build/libs/`.

---

## Credits

- Original Python tool, mathematical algorithm, and design by [EQUENOS-2](https://github.com/EQUENOS-2/Schematic-Tiling).
- [Litematica](https://github.com/maruohon/litematica) & [MaLiLib](https://github.com/maruohon/malilib) by [masa](https://github.com/maruohon) and [Sakura-Ryoko](https://github.com/Sakura-Ryoko).
