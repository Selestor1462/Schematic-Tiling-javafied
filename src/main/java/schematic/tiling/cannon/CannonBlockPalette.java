package schematic.tiling.cannon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GlazedTerracottaBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.PushReaction;


/**
 * Manages customizable block replacement palette for Pearl Return Cannon schematics.
 * Allows replacing:
 *  - Glass (black stained glass) -> Any glass type (plain glass or any 16 stained glass colors)
 *  - Solid decorative block (gray concrete) -> Any solid block (excluding red concrete used for tnt)
 *  - Stairs (stone brick stairs) -> Any stairs type
 *  - Leaves (azalea leaves) -> Any leaves type
 *  - Glazed terracotta (gray glazed terracotta) -> Any of the 16 glazed terracotta blocks
 *  - Warped fence gates & trapdoor -> Warped or Crimson fence gates & trapdoors
 */
public class CannonBlockPalette {

    public enum Category {
        GLASS("schematic_tiling.gui.cannon.palette.cat.glass", "Glass"),
        SOLID("schematic_tiling.gui.cannon.palette.cat.solid", "Solid Block"),
        STAIRS("schematic_tiling.gui.cannon.palette.cat.stairs", "Stairs"),
        LEAVES("schematic_tiling.gui.cannon.palette.cat.leaves", "Leaves"),
        TERRACOTTA("schematic_tiling.gui.cannon.palette.cat.terracotta", "Glazed Terracotta"),
        NETHER_WOOD("schematic_tiling.gui.cannon.palette.cat.nether_wood", "Nether Wood");

        public final String langKey;
        public final String defaultName;

        Category(String langKey, String defaultName) {
            this.langKey = langKey;
            this.defaultName = defaultName;
        }
    }

    public enum NetherWoodType {
        WARPED("Warped", Blocks.WARPED_FENCE_GATE, Blocks.WARPED_TRAPDOOR),
        CRIMSON("Crimson", Blocks.CRIMSON_FENCE_GATE, Blocks.CRIMSON_TRAPDOOR);

        public final String displayName;
        public final Block fenceGate;
        public final Block trapdoor;

        NetherWoodType(String displayName, Block fenceGate, Block trapdoor) {
            this.displayName = displayName;
            this.fenceGate = fenceGate;
            this.trapdoor = trapdoor;
        }
    }

    private Block selectedGlass = Blocks.BLACK_STAINED_GLASS;
    private Block selectedSolid = Blocks.GRAY_CONCRETE;
    private Block selectedStairs = Blocks.STONE_BRICK_STAIRS;
    private Block selectedLeaves = Blocks.AZALEA_LEAVES;
    private Block selectedTerracotta = Blocks.GRAY_GLAZED_TERRACOTTA;
    private NetherWoodType selectedNetherWood = NetherWoodType.WARPED;

    private static List<Block> sAvailableGlass = null;
    private static List<Block> sAvailableSolid = null;
    private static List<Block> sAvailableStairs = null;
    private static List<Block> sAvailableLeaves = null;
    private static List<Block> sAvailableTerracotta = null;

    public CannonBlockPalette() {}

    public CannonBlockPalette copy() {
        CannonBlockPalette cp = new CannonBlockPalette();
        cp.selectedGlass = this.selectedGlass;
        cp.selectedSolid = this.selectedSolid;
        cp.selectedStairs = this.selectedStairs;
        cp.selectedLeaves = this.selectedLeaves;
        cp.selectedTerracotta = this.selectedTerracotta;
        cp.selectedNetherWood = this.selectedNetherWood;
        return cp;
    }

    public void copyFrom(CannonBlockPalette other) {
        if (other == null) return;
        this.selectedGlass = other.selectedGlass;
        this.selectedSolid = other.selectedSolid;
        this.selectedStairs = other.selectedStairs;
        this.selectedLeaves = other.selectedLeaves;
        this.selectedTerracotta = other.selectedTerracotta;
        this.selectedNetherWood = other.selectedNetherWood;
    }

    public Block getSelectedGlass() { return this.selectedGlass; }
    public void setSelectedGlass(Block b) { if (b != null) this.selectedGlass = b; }

    public Block getSelectedSolid() { return this.selectedSolid; }
    public void setSelectedSolid(Block b) { if (b != null) this.selectedSolid = b; }

    public Block getSelectedStairs() { return this.selectedStairs; }
    public void setSelectedStairs(Block b) { if (b != null) this.selectedStairs = b; }

    public Block getSelectedLeaves() { return this.selectedLeaves; }
    public void setSelectedLeaves(Block b) { if (b != null) this.selectedLeaves = b; }

    public Block getSelectedTerracotta() { return this.selectedTerracotta; }
    public void setSelectedTerracotta(Block b) { if (b != null) this.selectedTerracotta = b; }

    public NetherWoodType getSelectedNetherWood() { return this.selectedNetherWood; }
    public void setSelectedNetherWood(NetherWoodType type) { if (type != null) this.selectedNetherWood = type; }

    public void resetToDefaults() {
        this.selectedGlass = Blocks.BLACK_STAINED_GLASS;
        this.selectedSolid = Blocks.GRAY_CONCRETE;
        this.selectedStairs = Blocks.STONE_BRICK_STAIRS;
        this.selectedLeaves = Blocks.AZALEA_LEAVES;
        this.selectedTerracotta = Blocks.GRAY_GLAZED_TERRACOTTA;
        this.selectedNetherWood = NetherWoodType.WARPED;
    }

    public enum Preset {
        WHITE("schematic_tiling.gui.cannon.palette.preset.white", "White", 0xFFFFFFFF),
        LIGHT_GRAY("schematic_tiling.gui.cannon.palette.preset.light_gray", "Light Gray", 0xFF9D9D97),
        GRAY("schematic_tiling.gui.cannon.palette.preset.gray", "Gray", 0xFF474F52),
        BLACK("schematic_tiling.gui.cannon.palette.preset.black", "Black", 0xFF1D1D21),
        BROWN("schematic_tiling.gui.cannon.palette.preset.brown", "Brown", 0xFF835432),
        RED("schematic_tiling.gui.cannon.palette.preset.red", "Red", 0xFFB02E26),
        ORANGE("schematic_tiling.gui.cannon.palette.preset.orange", "Orange", 0xFFF9801D),
        YELLOW("schematic_tiling.gui.cannon.palette.preset.yellow", "Yellow", 0xFFFED83D),
        LIME("schematic_tiling.gui.cannon.palette.preset.lime", "Lime", 0xFF80C71F),
        GREEN("schematic_tiling.gui.cannon.palette.preset.green", "Green", 0xFF5E7C16),
        CYAN("schematic_tiling.gui.cannon.palette.preset.cyan", "Cyan", 0xFF169C9C),
        LIGHT_BLUE("schematic_tiling.gui.cannon.palette.preset.light_blue", "Light Blue", 0xFF3AB3DA),
        BLUE("schematic_tiling.gui.cannon.palette.preset.blue", "Blue", 0xFF3C44AA),
        PURPLE("schematic_tiling.gui.cannon.palette.preset.purple", "Purple", 0xFF8932B8),
        MAGENTA("schematic_tiling.gui.cannon.palette.preset.magenta", "Magenta", 0xFFC74EBD),
        PINK("schematic_tiling.gui.cannon.palette.preset.pink", "Pink", 0xFFF38BAA);

        public final String langKey;
        public final String displayName;
        public final int color;

        Preset(String langKey, String displayName, int color) {
            this.langKey = langKey;
            this.displayName = displayName;
            this.color = color;
        }
    }

    public void applyPreset(Preset preset) {
        if (preset == null) return;
        switch (preset) {
            case WHITE -> {
                this.selectedGlass = Blocks.WHITE_STAINED_GLASS;
                this.selectedSolid = Blocks.WHITE_CONCRETE;
                this.selectedStairs = Blocks.QUARTZ_STAIRS;
                this.selectedLeaves = Blocks.BIRCH_LEAVES;
                this.selectedTerracotta = Blocks.WHITE_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case LIGHT_GRAY -> {
                this.selectedGlass = Blocks.LIGHT_GRAY_STAINED_GLASS;
                this.selectedSolid = Blocks.LIGHT_GRAY_CONCRETE;
                this.selectedStairs = Blocks.STONE_BRICK_STAIRS;
                this.selectedLeaves = Blocks.AZALEA_LEAVES;
                this.selectedTerracotta = Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case GRAY -> {
                this.selectedGlass = Blocks.BLACK_STAINED_GLASS;
                this.selectedSolid = Blocks.GRAY_CONCRETE;
                this.selectedStairs = Blocks.STONE_BRICK_STAIRS;
                this.selectedLeaves = Blocks.AZALEA_LEAVES;
                this.selectedTerracotta = Blocks.GRAY_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case BLACK -> {
                this.selectedGlass = Blocks.BLACK_STAINED_GLASS;
                this.selectedSolid = Blocks.BLACK_CONCRETE;
                this.selectedStairs = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
                this.selectedLeaves = Blocks.DARK_OAK_LEAVES;
                this.selectedTerracotta = Blocks.BLACK_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case BROWN -> {
                this.selectedGlass = Blocks.BROWN_STAINED_GLASS;
                this.selectedSolid = Blocks.BROWN_CONCRETE;
                this.selectedStairs = Blocks.MUD_BRICK_STAIRS;
                this.selectedLeaves = Blocks.JUNGLE_LEAVES;
                this.selectedTerracotta = Blocks.BROWN_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case RED -> {
                this.selectedGlass = Blocks.RED_STAINED_GLASS;
                this.selectedSolid = Blocks.RED_CONCRETE;
                this.selectedStairs = Blocks.RED_NETHER_BRICK_STAIRS;
                this.selectedLeaves = Blocks.MANGROVE_LEAVES;
                this.selectedTerracotta = Blocks.RED_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.CRIMSON;
            }
            case ORANGE -> {
                this.selectedGlass = Blocks.ORANGE_STAINED_GLASS;
                this.selectedSolid = Blocks.ORANGE_CONCRETE;
                this.selectedStairs = Blocks.ACACIA_STAIRS;
                this.selectedLeaves = Blocks.ACACIA_LEAVES;
                this.selectedTerracotta = Blocks.ORANGE_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case YELLOW -> {
                this.selectedGlass = Blocks.YELLOW_STAINED_GLASS;
                this.selectedSolid = Blocks.YELLOW_CONCRETE;
                this.selectedStairs = Blocks.SMOOTH_SANDSTONE_STAIRS;
                this.selectedLeaves = Blocks.BIRCH_LEAVES;
                this.selectedTerracotta = Blocks.YELLOW_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case LIME -> {
                this.selectedGlass = Blocks.LIME_STAINED_GLASS;
                this.selectedSolid = Blocks.LIME_CONCRETE;
                this.selectedStairs = Blocks.BAMBOO_STAIRS;
                this.selectedLeaves = Blocks.BIRCH_LEAVES;
                this.selectedTerracotta = Blocks.LIME_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case GREEN -> {
                this.selectedGlass = Blocks.GREEN_STAINED_GLASS;
                this.selectedSolid = Blocks.GREEN_CONCRETE;
                this.selectedStairs = Blocks.MOSSY_STONE_BRICK_STAIRS;
                this.selectedLeaves = Blocks.OAK_LEAVES;
                this.selectedTerracotta = Blocks.GREEN_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case CYAN -> {
                this.selectedGlass = Blocks.CYAN_STAINED_GLASS;
                this.selectedSolid = Blocks.CYAN_CONCRETE;
                this.selectedStairs = Blocks.WARPED_STAIRS;
                this.selectedLeaves = Blocks.AZALEA_LEAVES;
                this.selectedTerracotta = Blocks.CYAN_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case LIGHT_BLUE -> {
                this.selectedGlass = Blocks.LIGHT_BLUE_STAINED_GLASS;
                this.selectedSolid = Blocks.LIGHT_BLUE_CONCRETE;
                this.selectedStairs = Blocks.PRISMARINE_STAIRS;
                this.selectedLeaves = Blocks.BIRCH_LEAVES;
                this.selectedTerracotta = Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case BLUE -> {
                this.selectedGlass = Blocks.BLUE_STAINED_GLASS;
                this.selectedSolid = Blocks.BLUE_CONCRETE;
                this.selectedStairs = Blocks.DARK_PRISMARINE_STAIRS;
                this.selectedLeaves = Blocks.SPRUCE_LEAVES;
                this.selectedTerracotta = Blocks.BLUE_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.WARPED;
            }
            case PURPLE -> {
                this.selectedGlass = Blocks.PURPLE_STAINED_GLASS;
                this.selectedSolid = Blocks.PURPLE_CONCRETE;
                this.selectedStairs = Blocks.PURPUR_STAIRS;
                this.selectedLeaves = Blocks.CHERRY_LEAVES;
                this.selectedTerracotta = Blocks.PURPLE_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.CRIMSON;
            }
            case MAGENTA -> {
                this.selectedGlass = Blocks.MAGENTA_STAINED_GLASS;
                this.selectedSolid = Blocks.MAGENTA_CONCRETE;
                this.selectedStairs = Blocks.PURPUR_STAIRS;
                this.selectedLeaves = Blocks.CHERRY_LEAVES;
                this.selectedTerracotta = Blocks.MAGENTA_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.CRIMSON;
            }
            case PINK -> {
                this.selectedGlass = Blocks.PINK_STAINED_GLASS;
                this.selectedSolid = Blocks.PINK_CONCRETE;
                this.selectedStairs = Blocks.CHERRY_STAIRS;
                this.selectedLeaves = Blocks.CHERRY_LEAVES;
                this.selectedTerracotta = Blocks.PINK_GLAZED_TERRACOTTA;
                this.selectedNetherWood = NetherWoodType.CRIMSON;
            }
        }
    }

    public boolean isDefault() {
        return this.selectedGlass == Blocks.BLACK_STAINED_GLASS &&
               this.selectedSolid == Blocks.GRAY_CONCRETE &&
               this.selectedStairs == Blocks.STONE_BRICK_STAIRS &&
               this.selectedLeaves == Blocks.AZALEA_LEAVES &&
               this.selectedTerracotta == Blocks.GRAY_GLAZED_TERRACOTTA &&
               this.selectedNetherWood == NetherWoodType.WARPED;
    }

    /**
     * Replaces block states according to user-selected palette while preserving state properties.
     */
    public BlockState applyReplacement(BlockState state) {
        if (state == null) return null;
        Block block = state.getBlock();

        // 1. Black Stained Glass -> selected glass
        if (block == Blocks.BLACK_STAINED_GLASS || isOriginalGlass(block)) {
            if (this.selectedGlass != block) {
                return copyProperties(state, this.selectedGlass.defaultBlockState());
            }
            return state;
        }

        // 2. Gray Concrete -> selected solid
        if (block == Blocks.GRAY_CONCRETE) {
            if (this.selectedSolid != block) {
                return copyProperties(state, this.selectedSolid.defaultBlockState());
            }
            return state;
        }

        // 3. Stone Brick Stairs -> selected stairs
        if (block == Blocks.STONE_BRICK_STAIRS) {
            if (this.selectedStairs != block) {
                return copyProperties(state, this.selectedStairs.defaultBlockState());
            }
            return state;
        }

        // 4. Azalea Leaves -> selected leaves
        if (block == Blocks.AZALEA_LEAVES) {
            if (this.selectedLeaves != block) {
                return copyProperties(state, this.selectedLeaves.defaultBlockState());
            }
            return state;
        }

        // 5. Gray Glazed Terracotta -> selected glazed terracotta
        if (block == Blocks.GRAY_GLAZED_TERRACOTTA) {
            if (this.selectedTerracotta != block) {
                return copyProperties(state, this.selectedTerracotta.defaultBlockState());
            }
            return state;
        }

        // 6. Warped Fence Gate -> Crimson or Warped Fence Gate
        if (block == Blocks.WARPED_FENCE_GATE) {
            if (this.selectedNetherWood.fenceGate != block) {
                return copyProperties(state, this.selectedNetherWood.fenceGate.defaultBlockState());
            }
            return state;
        }

        // 7. Warped Trapdoor -> Crimson or Warped Trapdoor
        if (block == Blocks.WARPED_TRAPDOOR) {
            if (this.selectedNetherWood.trapdoor != block) {
                return copyProperties(state, this.selectedNetherWood.trapdoor.defaultBlockState());
            }
            return state;
        }

        return state;
    }

    private static boolean isOriginalGlass(Block block) {
        return block == Blocks.BLACK_STAINED_GLASS;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setVal(BlockState target, Property<T> prop, Comparable<?> val) {
        return target.setValue(prop, (T) val);
    }

    private static BlockState copyProperties(BlockState from, BlockState to) {
        BlockState res = to;
        for (Property<?> p : from.getProperties()) {
            if (res.hasProperty(p)) {
                res = setVal(res, p, from.getValue(p));
            }
        }
        return res;
    }

    private static final List<String> DYE_COLOR_ORDER = List.of(
        "white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
        "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"
    );

    private static final List<String> CANONICAL_LEAVES_ORDER = List.of(
        "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves",
        "dark_oak_leaves", "mangrove_leaves", "cherry_leaves", "azalea_leaves", "flowering_azalea_leaves"
    );

    private static final Set<Block> IMMOVABLE_OR_UNOBTAINABLE = Set.of(
        Blocks.BEDROCK,
        Blocks.REINFORCED_DEEPSLATE,
        Blocks.OBSIDIAN,
        Blocks.CRYING_OBSIDIAN,
        Blocks.RESPAWN_ANCHOR,
        Blocks.BARRIER,
        Blocks.STRUCTURE_BLOCK,
        Blocks.STRUCTURE_VOID,
        Blocks.JIGSAW,
        Blocks.LIGHT,
        Blocks.COMMAND_BLOCK,
        Blocks.CHAIN_COMMAND_BLOCK,
        Blocks.REPEATING_COMMAND_BLOCK,
        Blocks.SPAWNER,
        Blocks.TRIAL_SPAWNER,
        Blocks.VAULT,
        Blocks.END_PORTAL_FRAME,
        Blocks.BUDDING_AMETHYST
    );

    private static final Set<Block> REDSTONE_INTERACTING = Set.of(
        Blocks.REDSTONE_BLOCK,
        Blocks.TARGET,
        Blocks.NOTE_BLOCK,
        Blocks.TNT,
        Blocks.REDSTONE_LAMP,
        Blocks.DROPPER,
        Blocks.DISPENSER,
        Blocks.CRAFTER,
        Blocks.LIGHTNING_ROD,
        Blocks.BELL,
        Blocks.PISTON,
        Blocks.STICKY_PISTON,
        Blocks.SLIME_BLOCK,
        Blocks.HONEY_BLOCK,
        Blocks.REDSTONE_ORE,
        Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.SCULK_SENSOR,
        Blocks.CALIBRATED_SCULK_SENSOR,
        Blocks.SCULK_SHRIEKER,
        Blocks.TRIPWIRE_HOOK,
        Blocks.LEVER
    );

    private static final List<String> CANONICAL_SOLID_ORDER = List.of(
        // 0. Concretes (16)
        "white_concrete", "light_gray_concrete", "gray_concrete", "black_concrete", "brown_concrete",
        "red_concrete", "orange_concrete", "yellow_concrete", "lime_concrete", "green_concrete",
        "cyan_concrete", "light_blue_concrete", "blue_concrete", "purple_concrete", "magenta_concrete", "pink_concrete",

        // 1. Terracotta (Plain + 16)
        "terracotta", "white_terracotta", "light_gray_terracotta", "gray_terracotta", "black_terracotta", "brown_terracotta",
        "red_terracotta", "orange_terracotta", "yellow_terracotta", "lime_terracotta", "green_terracotta",
        "cyan_terracotta", "light_blue_terracotta", "blue_terracotta", "purple_terracotta", "magenta_terracotta", "pink_terracotta",

        // 2. Deepslate family
        "deepslate", "cobbled_deepslate", "polished_deepslate", "chiseled_deepslate",
        "deepslate_bricks", "cracked_deepslate_bricks", "deepslate_tiles", "cracked_deepslate_tiles",

        // 3. Stone & Rocks & Bricks
        "stone", "smooth_stone", "cobblestone", "mossy_cobblestone",
        "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks",
        "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite",
        "calcite", "dripstone_block", "bricks",

        // 4. Tuff & Mud
        "tuff", "polished_tuff", "tuff_bricks", "chiseled_tuff", "chiseled_tuff_bricks",
        "packed_mud", "mud_bricks",

        // 5. Blackstone & Basalt
        "blackstone", "polished_blackstone", "chiseled_polished_blackstone",
        "polished_blackstone_bricks", "cracked_polished_blackstone_bricks",
        "basalt", "polished_basalt", "smooth_basalt",

        // 6. Nether & End
        "nether_bricks", "red_nether_bricks", "cracked_nether_bricks", "chiseled_nether_bricks",
        "netherrack", "end_stone", "end_stone_bricks", "purpur_block", "purpur_pillar",

        // 7. Quartz & Prismarine
        "quartz_block", "smooth_quartz", "chiseled_quartz", "quartz_pillar", "quartz_bricks",
        "prismarine", "prismarine_bricks", "dark_prismarine",

        // 8. Sandstone
        "sandstone", "cut_sandstone", "smooth_sandstone", "chiseled_sandstone",
        "red_sandstone", "cut_red_sandstone", "smooth_red_sandstone", "chiseled_red_sandstone",

        // 9. Wood (Nether / fireproof)
        "crimson_planks", "warped_planks",
        "crimson_stem", "warped_stem",
        "stripped_crimson_stem", "stripped_warped_stem",
        "crimson_hyphae", "warped_hyphae",
        "stripped_crimson_hyphae", "stripped_warped_hyphae",

        // 10. Ores & Minerals (grouped together with raw ores)
        "coal_ore", "deepslate_coal_ore", "coal_block",
        "iron_ore", "deepslate_iron_ore", "raw_iron_block", "iron_block",
        "copper_ore", "deepslate_copper_ore", "raw_copper_block",
        "gold_ore", "deepslate_gold_ore", "nether_gold_ore", "raw_gold_block", "gold_block",
        "lapis_ore", "deepslate_lapis_ore", "lapis_block",
        "diamond_ore", "deepslate_diamond_ore", "diamond_block",
        "emerald_ore", "deepslate_emerald_ore", "emerald_block",
        "nether_quartz_ore",
        "netherite_block", "amethyst_block",

        // 11. Waxed Copper
        "waxed_copper_block", "waxed_cut_copper", "waxed_chiseled_copper",
        "waxed_exposed_copper", "waxed_exposed_cut_copper", "waxed_exposed_chiseled_copper",
        "waxed_weathered_copper", "waxed_weathered_cut_copper", "waxed_weathered_chiseled_copper",
        "waxed_oxidized_copper", "waxed_oxidized_cut_copper", "waxed_oxidized_chiseled_copper"
    );

    private static final List<String> CANONICAL_STAIRS_ORDER = List.of(
        // 0. Wood Stairs
        "oak_stairs", "spruce_stairs", "birch_stairs", "jungle_stairs", "acacia_stairs",
        "dark_oak_stairs", "mangrove_stairs", "cherry_stairs", "bamboo_stairs",
        "crimson_stairs", "warped_stairs",

        // 1. Stone & Rocks & Bricks
        "stone_stairs", "cobblestone_stairs", "mossy_cobblestone_stairs",
        "stone_brick_stairs", "mossy_stone_brick_stairs",
        "granite_stairs", "polished_granite_stairs",
        "diorite_stairs", "polished_diorite_stairs",
        "andesite_stairs", "polished_andesite_stairs",
        "brick_stairs",

        // 2. Deepslate Stairs
        "cobbled_deepslate_stairs", "polished_deepslate_stairs",
        "deepslate_brick_stairs", "deepslate_tile_stairs",

        // 3. Tuff & Mud Stairs
        "tuff_stairs", "polished_tuff_stairs", "tuff_brick_stairs", "mud_brick_stairs",

        // 4. Blackstone Stairs
        "blackstone_stairs", "polished_blackstone_stairs", "polished_blackstone_brick_stairs",

        // 5. Nether & End Stairs
        "nether_brick_stairs", "red_nether_brick_stairs", "end_stone_brick_stairs", "purpur_stairs",

        // 6. Quartz & Prismarine Stairs
        "quartz_stairs", "smooth_quartz_stairs",
        "prismarine_stairs", "prismarine_brick_stairs", "dark_prismarine_stairs",

        // 7. Sandstone Stairs
        "sandstone_stairs", "smooth_sandstone_stairs",
        "red_sandstone_stairs", "smooth_red_sandstone_stairs",

        // 8. Waxed Copper Stairs
        "waxed_cut_copper_stairs", "waxed_exposed_cut_copper_stairs",
        "waxed_weathered_cut_copper_stairs", "waxed_oxidized_cut_copper_stairs"
    );

    public static synchronized List<Block> getAvailableGlass() {
        if (sAvailableGlass == null) {
            List<Block> list = new ArrayList<>();
            list.add(Blocks.GLASS);
            for (String color : DYE_COLOR_ORDER) {
                String targetPath = color + "_stained_glass";
                for (Block b : BuiltInRegistries.BLOCK) {
                    if (BuiltInRegistries.BLOCK.getKey(b).getPath().equals(targetPath)) {
                        if (!list.contains(b)) list.add(b);
                        break;
                    }
                }
            }
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b instanceof StainedGlassBlock || BuiltInRegistries.BLOCK.getKey(b).getPath().endsWith("_stained_glass")) {
                    if (!list.contains(b)) list.add(b);
                }
            }
            sAvailableGlass = Collections.unmodifiableList(list);
        }
        return sAvailableGlass;
    }

    private static boolean isValidSolidBlock(Block b) {
        if (b == null || b == Blocks.AIR || b.asItem() == Items.AIR) {
            return false;
        }
        if (IMMOVABLE_OR_UNOBTAINABLE.contains(b) || REDSTONE_INTERACTING.contains(b)) {
            return false;
        }

        BlockState def = b.defaultBlockState();

        // Must be breakable in survival (destroySpeed >= 0.0F)
        if (def.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < 0.0F) {
            return false;
        }

        // Must have normal piston push reaction and no block entity
        if (def.getPistonPushReaction() != PushReaction.NORMAL || def.hasBlockEntity()) {
            return false;
        }

        // Must be redstone conductor and solid render
        if (!def.isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) || !def.isSolidRender() || !def.canOcclude()) {
            return false;
        }

        // Must not interact with redstone or emit signals
        if (def.isSignalSource() || def.hasAnalogOutputSignal()) {
            return false;
        }

        // Falling blocks
        if (b instanceof FallingBlock) {
            return false;
        }

        // Lava / burnable checks
        if (def.ignitedByLava() || def.is(BlockTags.WOOL)) {
            return false;
        }

        String path = BuiltInRegistries.BLOCK.getKey(b).getPath();

        // Falling blocks string safety check
        if (path.contains("concrete_powder") || path.contains("sand") || path.contains("gravel") || path.contains("anvil")) {
            return false;
        }

        // Redstone interaction string safety check
        if (path.contains("bulb") || path.contains("pressure_plate") || path.contains("button")
                || path.contains("door") || path.contains("trapdoor") || path.contains("fence_gate")) {
            return false;
        }

        // Alive corals die without water
        if (path.contains("coral") && !path.startsWith("dead_")) {
            return false;
        }

        // Infested blocks
        if (path.contains("infested")) {
            return false;
        }

        // Soil / organic blocks
        if (path.equals("mycelium") || path.contains("nylium") || path.equals("podzol")) {
            return false;
        }

        // Copper: only waxed copper allowed (raw copper block and ores do not weather)
        if (path.contains("copper")) {
            if (!path.startsWith("waxed_") && !path.equals("raw_copper_block") && !path.contains("ore")) {
                return false;
            }
            if (path.contains("grate")) {
                return false;
            }
        }

        return true;
    }

    public static synchronized List<Block> getAvailableSolid() {
        if (sAvailableSolid == null) {
            List<Block> list = new ArrayList<>();
            for (Block b : BuiltInRegistries.BLOCK) {
                if (isValidSolidBlock(b)) {
                    list.add(b);
                }
            }
            sortSolid(list);
            sAvailableSolid = Collections.unmodifiableList(list);
        }
        return sAvailableSolid;
    }

    public static synchronized List<Block> getAvailableStairs() {
        if (sAvailableStairs == null) {
            List<Block> list = new ArrayList<>();
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b.asItem() == Items.AIR) continue;
                if (b instanceof StairBlock || b.defaultBlockState().is(BlockTags.STAIRS)) {
                    String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
                    if (path.contains("copper") && !path.startsWith("waxed_")) {
                        continue;
                    }
                    if (!list.contains(b)) list.add(b);
                }
            }
            sortStairs(list);
            sAvailableStairs = Collections.unmodifiableList(list);
        }
        return sAvailableStairs;
    }

    public static synchronized List<Block> getAvailableLeaves() {
        if (sAvailableLeaves == null) {
            List<Block> list = new ArrayList<>();
            for (String leafName : CANONICAL_LEAVES_ORDER) {
                for (Block b : BuiltInRegistries.BLOCK) {
                    if (BuiltInRegistries.BLOCK.getKey(b).getPath().equals(leafName)) {
                        if (!list.contains(b)) list.add(b);
                        break;
                    }
                }
            }
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b instanceof LeavesBlock || b.defaultBlockState().is(BlockTags.LEAVES)) {
                    if (!list.contains(b)) list.add(b);
                }
            }
            sAvailableLeaves = Collections.unmodifiableList(list);
        }
        return sAvailableLeaves;
    }

    public static synchronized List<Block> getAvailableTerracotta() {
        if (sAvailableTerracotta == null) {
            List<Block> list = new ArrayList<>();
            for (String color : DYE_COLOR_ORDER) {
                String targetPath = color + "_glazed_terracotta";
                for (Block b : BuiltInRegistries.BLOCK) {
                    if (BuiltInRegistries.BLOCK.getKey(b).getPath().equals(targetPath)) {
                        if (!list.contains(b)) list.add(b);
                        break;
                    }
                }
            }
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b instanceof GlazedTerracottaBlock || BuiltInRegistries.BLOCK.getKey(b).getPath().endsWith("_glazed_terracotta")) {
                    if (!list.contains(b)) list.add(b);
                }
            }
            sAvailableTerracotta = Collections.unmodifiableList(list);
        }
        return sAvailableTerracotta;
    }

    private static void sortSolid(List<Block> list) {
        list.sort((a, b) -> {
            String pathA = BuiltInRegistries.BLOCK.getKey(a).getPath();
            String pathB = BuiltInRegistries.BLOCK.getKey(b).getPath();

            int idxA = CANONICAL_SOLID_ORDER.indexOf(pathA);
            int idxB = CANONICAL_SOLID_ORDER.indexOf(pathB);

            if (idxA != -1 && idxB != -1) {
                return Integer.compare(idxA, idxB);
            }
            if (idxA != -1) return -1;
            if (idxB != -1) return 1;

            return pathA.compareToIgnoreCase(pathB);
        });
    }

    private static void sortStairs(List<Block> list) {
        list.sort((a, b) -> {
            String pathA = BuiltInRegistries.BLOCK.getKey(a).getPath();
            String pathB = BuiltInRegistries.BLOCK.getKey(b).getPath();

            int idxA = CANONICAL_STAIRS_ORDER.indexOf(pathA);
            int idxB = CANONICAL_STAIRS_ORDER.indexOf(pathB);

            if (idxA != -1 && idxB != -1) {
                return Integer.compare(idxA, idxB);
            }
            if (idxA != -1) return -1;
            if (idxB != -1) return 1;

            return pathA.compareToIgnoreCase(pathB);
        });
    }
}

