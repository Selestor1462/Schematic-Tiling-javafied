Release 1.2.0 introduces the Return Cannon Generator, incorporates upstream knapsack optimization improvements from the latest version of Schematic-Tiling, and resolves several schematic tiling editor persistence bugs.

The Return Cannon Generator allows players to calculate TNT requirements and assemble complete return pearl cannon schematics directly in-game. To open the Return Cannon menu, create or select an area selection or schematic placement with a name ending in cannon (such as Return_Cannon), then open Litematica's Area Selection Editor and click the Return Cannon button in the top right corner. Please use pearl cannon creation with caution as it has not been extensively tested across every edge case.

This release also updates the core tiling algorithm with upstream changes from EQUENOS-2 that optimize knapsack solving for uncapped groups, and fixes editor issues where empty groups and subgroups were removed on menu close, group Z-dependence was not saved between menu reopens, and the normalize origin Y level checkbox state was lost.

Credits go to EQUENOS-2 for the original Schematic-Tiling design and algorithm, Pearl Calculator for trajectory simulation math, and garlic bread for designing the pearl cannon as showcased in the Return Cannon Tutorial (How to make your own return pearl cannons) on YouTube at https://www.youtube.com/watch?v=MkEJpBAtin8.

