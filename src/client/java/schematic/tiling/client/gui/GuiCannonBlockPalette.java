package schematic.tiling.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiScrollBar;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.GuiGraphics;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.cannon.CannonBlockPalette;
import schematic.tiling.cannon.CannonBlockPalette.Category;
import schematic.tiling.cannon.CannonBlockPalette.NetherWoodType;
import schematic.tiling.cannon.CannonBlockPalette.Preset;

/**
 * Dialog to configure block palette replacements for Pearl Return Cannon generation.
 */
public class GuiCannonBlockPalette extends GuiBase {

    private static final int DIALOG_WIDTH = 470;
    private static final int DIALOG_HEIGHT = 330;

    private final GuiReturnCannon parentCannonGui;
    private final CannonBlockPalette palette;
    private final CannonBlockPalette backupPalette;

    private Category selectedCategory = Category.GLASS;
    private GuiTextFieldGeneric searchField;
    private final GuiScrollBar scrollBar = new GuiScrollBar();

    private List<Block> filteredBlocks = new ArrayList<>();
    private static final int GRID_COLS = 11;
    private static final int SLOT_SIZE = 20;
    private static final int VISIBLE_ROWS = 7;

    public GuiCannonBlockPalette(GuiReturnCannon parent, CannonBlockPalette palette) {
        this.parentCannonGui = parent;
        this.palette = palette;
        this.backupPalette = palette.copy();
        this.setParent(parent);
        this.title = StringUtils.translate("schematic_tiling.gui.cannon.palette.title");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearElements();

        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        // 1. Minimalistic Category Icon Tabs (Row 1, Top Left)
        int tabX = startX + 16;
        int tabY = startY + 24;
        int tabSize = 24;
        int tabGap = 3;

        for (Category cat : Category.values()) {
            boolean isSelected = (this.selectedCategory == cat);
            Block iconBlock = getCategoryIconBlock(cat);
            String catName = StringUtils.translate(cat.langKey);

            CategoryTabButton tabBtn = new CategoryTabButton(tabX, tabY, tabSize, tabSize, cat, iconBlock, isSelected, catName);
            this.addButton(tabBtn, (btn, mouse) -> {
                this.selectedCategory = cat;
                if (this.searchField != null) {
                    this.searchField.setTextWrapper("");
                }
                this.scrollBar.setValue(0);
                this.updateFilteredBlocks();
                this.initGui();
            });
            tabX += tabSize + tabGap;
        }

        // 2. Search Bar (Row 1, Top Right, only when category != NETHER_WOOD)
        if (this.selectedCategory != Category.NETHER_WOOD) {
            int searchW = 140;
            int searchX = startX + dialogWidth - 16 - searchW;
            int searchY = startY + 28;
            String curSearch = (this.searchField != null) ? this.searchField.getTextWrapper() : "";

            this.searchField = new GuiTextFieldGeneric(searchX, searchY, searchW, 16, this.font);
            this.searchField.setMaxLengthWrapper(64);
            this.searchField.setTextWrapper(curSearch);
            this.searchField.setResponder(val -> {
                this.scrollBar.setValue(0);
                this.updateFilteredBlocks();
            });
            this.addTextField(this.searchField, null);
        } else {
            this.searchField = null;
        }

        // 3. Preset Buttons (Row 2: Default + all 16 Minecraft colors)
        int presetStartX = startX + 68;
        int presetY = startY + 52;
        int presetSize = 18;
        int presetGap = 3;

        for (Preset p : Preset.values()) {
            PresetButton pBtn = new PresetButton(presetStartX, presetY, presetSize, presetSize, p);
            this.addButton(pBtn, (btn, mouse) -> {
                this.palette.applyPreset(p);
                this.scrollBar.setValue(0);
                this.updateFilteredBlocks();
                this.initGui();
            });
            presetStartX += presetSize + presetGap;
        }

        // 4. Bottom action buttons: Done, Cancel
        int btnY = startY + dialogHeight - 26;
        ButtonGeneric doneBtn = new ButtonGeneric(startX + dialogWidth - 165, btnY, 70, 20, StringUtils.translate("schematic_tiling.gui.cannon.palette.button.done"));
        this.addButton(doneBtn, (btn, mouse) -> {
            GuiBase.openGui(this.parentCannonGui);
        });

        ButtonGeneric cancelBtn = new ButtonGeneric(startX + dialogWidth - 85, btnY, 70, 20, StringUtils.translate("schematic_tiling.gui.cannon.palette.button.cancel"));
        this.addButton(cancelBtn, (btn, mouse) -> {
            this.palette.copyFrom(this.backupPalette);
            GuiBase.openGui(this.parentCannonGui);
        });

        this.updateFilteredBlocks();
    }

    private Block getCategoryIconBlock(Category cat) {
        return switch (cat) {
            case GLASS -> this.palette.getSelectedGlass();
            case SOLID -> this.palette.getSelectedSolid();
            case STAIRS -> this.palette.getSelectedStairs();
            case LEAVES -> this.palette.getSelectedLeaves();
            case TERRACOTTA -> this.palette.getSelectedTerracotta();
            case NETHER_WOOD -> this.palette.getSelectedNetherWood().fenceGate;
        };
    }

    private Block getCurrentSelectedBlock() {
        return switch (this.selectedCategory) {
            case GLASS -> this.palette.getSelectedGlass();
            case SOLID -> this.palette.getSelectedSolid();
            case STAIRS -> this.palette.getSelectedStairs();
            case LEAVES -> this.palette.getSelectedLeaves();
            case TERRACOTTA -> this.palette.getSelectedTerracotta();
            case NETHER_WOOD -> this.palette.getSelectedNetherWood().fenceGate;
        };
    }

    private void updateFilteredBlocks() {
        List<Block> src = switch (this.selectedCategory) {
            case GLASS -> CannonBlockPalette.getAvailableGlass();
            case SOLID -> CannonBlockPalette.getAvailableSolid();
            case STAIRS -> CannonBlockPalette.getAvailableStairs();
            case LEAVES -> CannonBlockPalette.getAvailableLeaves();
            case TERRACOTTA -> CannonBlockPalette.getAvailableTerracotta();
            case NETHER_WOOD -> Collections.emptyList();
        };

        String query = (this.searchField != null) ? this.searchField.getTextWrapper().trim().toLowerCase(Locale.ROOT) : "";
        if (query.isEmpty()) {
            this.filteredBlocks = new ArrayList<>(src);
        } else {
            this.filteredBlocks = new ArrayList<>();
            for (Block b : src) {
                String name = BuiltInRegistries.BLOCK.getKey(b).getPath().toLowerCase(Locale.ROOT);
                String localized = b.getName().getString().toLowerCase(Locale.ROOT);
                if (name.contains(query) || localized.contains(query)) {
                    this.filteredBlocks.add(b);
                }
            }
        }

        int totalRows = (int) Math.ceil((double) this.filteredBlocks.size() / GRID_COLS);
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        this.scrollBar.setMaxValue(maxScroll);
        if (this.scrollBar.getValue() > maxScroll) {
            this.scrollBar.setValue(maxScroll);
        }
    }

    @Override
    public boolean onMouseClicked(net.minecraft.client.input.MouseButtonEvent mouse, boolean doubleClick) {
        if (super.onMouseClicked(mouse, doubleClick)) {
            return true;
        }

        int mouseX = (int) mouse.x();
        int mouseY = (int) mouse.y();

        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        if (this.selectedCategory == Category.NETHER_WOOD) {
            // Centered Warped vs Crimson click targets
            int boxW = 190;
            int boxH = 55;
            int gap = 20;
            int totalBoxesW = boxW * 2 + gap;
            int optX1 = startX + (dialogWidth - totalBoxesW) / 2;
            int optX2 = optX1 + boxW + gap;
            int optY = startY + 110;

            if (mouseX >= optX1 && mouseX <= optX1 + boxW && mouseY >= optY && mouseY <= optY + boxH) {
                this.palette.setSelectedNetherWood(NetherWoodType.WARPED);
                this.initGui();
                return true;
            }
            if (mouseX >= optX2 && mouseX <= optX2 + boxW && mouseY >= optY && mouseY <= optY + boxH) {
                this.palette.setSelectedNetherWood(NetherWoodType.CRIMSON);
                this.initGui();
                return true;
            }
            return false;
        }

        // Check grid clicks
        int gridX = startX + 16;
        int gridY = startY + 96;
        int scrollRow = this.scrollBar.getValue();

        for (int r = 0; r < VISIBLE_ROWS; r++) {
            for (int c = 0; c < GRID_COLS; c++) {
                int itemIdx = (scrollRow + r) * GRID_COLS + c;
                if (itemIdx >= this.filteredBlocks.size()) break;

                int sx = gridX + c * (SLOT_SIZE + 2);
                int sy = gridY + r * (SLOT_SIZE + 2);

                if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                    Block clicked = this.filteredBlocks.get(itemIdx);
                    switch (this.selectedCategory) {
                        case GLASS -> this.palette.setSelectedGlass(clicked);
                        case SOLID -> this.palette.setSelectedSolid(clicked);
                        case STAIRS -> this.palette.setSelectedStairs(clicked);
                        case LEAVES -> this.palette.setSelectedLeaves(clicked);
                        case TERRACOTTA -> this.palette.setSelectedTerracotta(clicked);
                        case NETHER_WOOD -> {}
                    }
                    this.initGui();
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.selectedCategory != Category.NETHER_WOOD) {
            if (verticalAmount != 0) {
                int delta = (verticalAmount > 0) ? -1 : 1;
                this.scrollBar.offsetValue(delta);
                return true;
            }
        }
        return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void drawScreenBackground(GuiGraphics ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);
        RenderUtils.drawRect(ctx, 0, 0, this.getScreenWidth(), this.getScreenHeight(), 0x80000000);

        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        RenderUtils.drawOutlinedBox(ctx, startX, startY, dialogWidth, dialogHeight, 0xFF1C1C1C, COLOR_HORIZONTAL_BAR);
    }

    @Override
    protected void drawContents(GuiGraphics ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        // Title
        this.drawStringWithShadow(ctx, this.title, startX + 16, startY + 10, COLOR_WHITE);

        // Search label (Row 1, only when searchField is active)
        if (this.selectedCategory != Category.NETHER_WOOD && this.searchField != null) {
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.palette.label.search"),
                    this.searchField.getX() - 44, this.searchField.getY() + 4, 0xFFAAAAAA);
        }

        // Presets label (Row 2) - centered vertically with preset buttons (y = startY + 52, h = 18)
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.palette.label.presets"),
                startX + 16, startY + 57, 0xFFAAAAAA);

        // Current selection summary label and preview icon (Row 3)
        String currentSelName = (this.selectedCategory == Category.NETHER_WOOD)
                ? this.palette.getSelectedNetherWood().displayName
                : getCurrentSelectedBlock().getName().getString();

        if (this.selectedCategory != Category.NETHER_WOOD) {
            ctx.renderItem(new ItemStack(getCurrentSelectedBlock()), startX + 16, startY + 74);
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.palette.label.current", currentSelName),
                    startX + 38, startY + 78, 0xFFFFAA00);
        } else {
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.palette.label.current", currentSelName),
                    startX + 16, startY + 78, 0xFFFFAA00);
        }

        if (this.selectedCategory == Category.NETHER_WOOD) {
            drawNetherWoodSelector(ctx, startX, startY, mouseX, mouseY);
            return;
        }

        // Draw block grid (Row 4)
        int gridX = startX + 16;
        int gridY = startY + 96;
        int scrollRow = this.scrollBar.getValue();

        Block curSelected = switch (this.selectedCategory) {
            case GLASS -> this.palette.getSelectedGlass();
            case SOLID -> this.palette.getSelectedSolid();
            case STAIRS -> this.palette.getSelectedStairs();
            case LEAVES -> this.palette.getSelectedLeaves();
            case TERRACOTTA -> this.palette.getSelectedTerracotta();
            case NETHER_WOOD -> null;
        };

        Block hoveredBlock = null;
        int hoverTooltipX = 0;
        int hoverTooltipY = 0;

        for (int r = 0; r < VISIBLE_ROWS; r++) {
            for (int c = 0; c < GRID_COLS; c++) {
                int itemIdx = (scrollRow + r) * GRID_COLS + c;
                int sx = gridX + c * (SLOT_SIZE + 2);
                int sy = gridY + r * (SLOT_SIZE + 2);

                if (itemIdx < this.filteredBlocks.size()) {
                    Block block = this.filteredBlocks.get(itemIdx);
                    boolean isCur = (block == curSelected);
                    boolean isHover = (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE);

                    int bgCol = isCur ? 0xD0205040 : (isHover ? 0xC0404040 : 0xA0222222);
                    int borderCol = isCur ? 0xFF00FFAA : (isHover ? 0xFFFFFFFF : 0xFF555555);
                    RenderUtils.drawOutlinedBox(ctx, sx, sy, SLOT_SIZE, SLOT_SIZE, bgCol, borderCol);

                    if (isCur) {
                        RenderUtils.drawRect(ctx, sx + 2, sy + SLOT_SIZE - 2, SLOT_SIZE - 4, 2, 0xFF00FFAA);
                    }

                    // Render block item
                    ItemStack is = new ItemStack(block);
                    ctx.renderItem(is, sx + 2, sy + 2);

                    if (isHover) {
                        hoveredBlock = block;
                        hoverTooltipX = mouseX;
                        hoverTooltipY = mouseY;
                    }
                } else {
                    RenderUtils.drawOutlinedBox(ctx, sx, sy, SLOT_SIZE, SLOT_SIZE, 0x40151515, 0x40333333);
                }
            }
        }

        // Scrollbar on right of grid
        int barX = gridX + GRID_COLS * (SLOT_SIZE + 2) + 6;
        int barY = gridY;
        int barH = VISIBLE_ROWS * (SLOT_SIZE + 2) - 2;
        this.scrollBar.render(ctx, barX, barY, partialTicks, barX, barY, 10, barH, this.scrollBar.getMaxValue());

        // Hover tooltip for grid item
        if (hoveredBlock != null) {
            String blockName = hoveredBlock.getName().getString();
            String id = BuiltInRegistries.BLOCK.getKey(hoveredBlock).toString();
            List<String> tooltips = List.of(blockName, "\u00a78" + id);
            RenderUtils.drawHoverText(ctx, hoverTooltipX, hoverTooltipY, tooltips);
        }
    }

    private void drawNetherWoodSelector(GuiGraphics ctx, int startX, int startY, int mouseX, int mouseY) {
        int dialogWidth = DIALOG_WIDTH;
        int boxW = 190;
        int boxH = 55;
        int gap = 20;
        int totalBoxesW = boxW * 2 + gap;
        int optX1 = startX + (dialogWidth - totalBoxesW) / 2;
        int optX2 = optX1 + boxW + gap;
        int optY = startY + 110;

        NetherWoodType cur = this.palette.getSelectedNetherWood();

        // Option 1: Warped
        boolean isWarped = (cur == NetherWoodType.WARPED);
        boolean hoverW = (mouseX >= optX1 && mouseX <= optX1 + boxW && mouseY >= optY && mouseY <= optY + boxH);
        int bgW = isWarped ? 0xD0154035 : (hoverW ? 0xA0253535 : 0x80202020);
        int borderW = isWarped ? 0xFF00FFAA : (hoverW ? 0xFFFFFFFF : 0xFF555555);
        RenderUtils.drawOutlinedBox(ctx, optX1, optY, boxW, boxH, bgW, borderW);

        ctx.renderItem(new ItemStack(Blocks.WARPED_FENCE_GATE), optX1 + 10, optY + 10);
        ctx.renderItem(new ItemStack(Blocks.WARPED_TRAPDOOR), optX1 + 30, optY + 10);
        this.drawStringWithShadow(ctx, "\u00a7bWarped Wood", optX1 + 56, optY + 12, COLOR_WHITE);
        this.drawString(ctx, "\u00a77Warped fence gate & trapdoor", optX1 + 10, optY + 36, 0xFFAAAAAA);

        // Option 2: Crimson
        boolean isCrimson = (cur == NetherWoodType.CRIMSON);
        boolean hoverC = (mouseX >= optX2 && mouseX <= optX2 + boxW && mouseY >= optY && mouseY <= optY + boxH);
        int bgC = isCrimson ? 0xD0451520 : (hoverC ? 0xA0352525 : 0x80202020);
        int borderC = isCrimson ? 0xFFFF5577 : (hoverC ? 0xFFFFFFFF : 0xFF555555);
        RenderUtils.drawOutlinedBox(ctx, optX2, optY, boxW, boxH, bgC, borderC);

        ctx.renderItem(new ItemStack(Blocks.CRIMSON_FENCE_GATE), optX2 + 10, optY + 10);
        ctx.renderItem(new ItemStack(Blocks.CRIMSON_TRAPDOOR), optX2 + 30, optY + 10);
        this.drawStringWithShadow(ctx, "\u00a7cCrimson Wood", optX2 + 56, optY + 12, COLOR_WHITE);
        this.drawString(ctx, "\u00a77Crimson fence gate & trapdoor", optX2 + 10, optY + 36, 0xFFAAAAAA);
    }

    /**
     * Minimalistic category tab button rendering the current block icon of the category.
     */
    public static class CategoryTabButton extends ButtonGeneric {
        private final Category category;
        private final Block iconBlock;
        private final boolean isSelected;
        private final String categoryName;

        public CategoryTabButton(int x, int y, int width, int height, Category category, Block iconBlock, boolean isSelected, String categoryName) {
            super(x, y, width, height, "");
            this.category = category;
            this.iconBlock = iconBlock;
            this.isSelected = isSelected;
            this.categoryName = categoryName;
            this.setHoverStrings(categoryName, "\u00a77" + iconBlock.getName().getString());
        }

        @Override
        public void render(GuiGraphics ctx, int mouseX, int mouseY, boolean unused) {
            if (!this.visible) return;
            this.hovered = this.isMouseOver(mouseX, mouseY);

            int bgCol = this.isSelected ? 0xD0205040 : (this.hovered ? 0xC0404040 : 0x90222222);
            int borderCol = this.isSelected ? 0xFF00FFAA : (this.hovered ? 0xFFFFFFFF : 0xFF555555);
            RenderUtils.drawOutlinedBox(ctx, this.x, this.y, this.width, this.height, bgCol, borderCol);

            ctx.renderItem(new ItemStack(this.iconBlock), this.x + (this.width - 16) / 2, this.y + (this.height - 16) / 2);

            if (this.isSelected) {
                RenderUtils.drawRect(ctx, this.x + 2, this.y + this.height - 2, this.width - 4, 2, 0xFF00FFAA);
            }
        }
    }

    /**
     * Preset button rendering a color swatch with hover tooltip.
     */
    public static class PresetButton extends ButtonGeneric {
        private final Preset preset;

        public PresetButton(int x, int y, int width, int height, Preset preset) {
            super(x, y, width, height, "");
            this.preset = preset;
            this.setHoverStrings(StringUtils.translate(preset.langKey));
        }

        @Override
        public void render(GuiGraphics ctx, int mouseX, int mouseY, boolean unused) {
            if (!this.visible) return;
            this.hovered = this.isMouseOver(mouseX, mouseY);

            int borderCol = this.hovered ? 0xFFFFFFFF : 0xFF555555;
            int bgCol = this.hovered ? 0x90404040 : 0x70202020;
            RenderUtils.drawOutlinedBox(ctx, this.x, this.y, this.width, this.height, bgCol, borderCol);

            int swSize = 10;
            int swX = this.x + (this.width - swSize) / 2;
            int swY = this.y + (this.height - swSize) / 2;
            RenderUtils.drawOutlinedBox(ctx, swX, swY, swSize, swSize, this.preset.color, 0xFF111111);
        }
    }
}
