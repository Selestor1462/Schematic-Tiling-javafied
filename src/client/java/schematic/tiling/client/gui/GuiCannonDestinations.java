package schematic.tiling.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldDouble;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.cannon.CannonDestination;
import schematic.tiling.cannon.CannonDestinationManager;

public class GuiCannonDestinations extends GuiBase {

    private static final int DIALOG_WIDTH = 440;
    private static final int DIALOG_HEIGHT = 300;
    private static final int ENTRIES_PER_PAGE = 4;

    public enum FilterMode {
        ALL,
        WORLD,
        GLOBAL
    }

    private final GuiReturnCannon parentCannonGui;
    private final double initialDestX;
    private final double initialDestZ;
    private final BlockPos currentStartPos;

    private FilterMode filterMode = FilterMode.ALL;
    private int page = 0;
    private boolean isSaveGlobal = false;

    private GuiTextFieldGeneric nameField;
    private GuiTextFieldDouble xField;
    private GuiTextFieldDouble zField;
    private String statusMessage = null;
    private boolean statusIsError = false;

    public GuiCannonDestinations(GuiReturnCannon parent, double initialDestX, double initialDestZ, BlockPos currentStartPos) {
        this.parentCannonGui = parent;
        this.initialDestX = initialDestX;
        this.initialDestZ = initialDestZ;
        this.currentStartPos = currentStartPos;
        this.setParent(parent);
        this.title = StringUtils.translate("schematic_tiling.gui.cannon.dests.title");
    }

    private List<CannonDestination> getFilteredDestinations() {
        CannonDestinationManager mgr = CannonDestinationManager.getInstance();
        return switch (this.filterMode) {
            case ALL -> mgr.getAllForCurrentContext();
            case WORLD -> mgr.getCurrentWorldDestinations();
            case GLOBAL -> mgr.getGlobalDestinations();
        };
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearElements();

        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        CannonDestinationManager mgr = CannonDestinationManager.getInstance();
        int allCount = mgr.getAllForCurrentContext().size();
        int worldCount = mgr.getCurrentWorldDestinations().size();
        int globalCount = mgr.getGlobalDestinations().size();

        // --- Filter Tabs ---
        int tabY = startY + 28;
        String allLabel = (this.filterMode == FilterMode.ALL ? "\u00a7e\u00a7l" : "") +
                StringUtils.translate("schematic_tiling.gui.cannon.dests.tab_all", allCount);
        ButtonGeneric tabAllBtn = new ButtonGeneric(startX + 16, tabY, 65, 18, allLabel);
        this.addButton(tabAllBtn, (btn, mouse) -> {
            this.filterMode = FilterMode.ALL;
            this.page = 0;
            this.initGui();
        });

        String worldLabel = (this.filterMode == FilterMode.WORLD ? "\u00a7e\u00a7l" : "") +
                StringUtils.translate("schematic_tiling.gui.cannon.dests.tab_world", worldCount);
        ButtonGeneric tabWorldBtn = new ButtonGeneric(startX + 85, tabY, 80, 18, worldLabel);
        this.addButton(tabWorldBtn, (btn, mouse) -> {
            this.filterMode = FilterMode.WORLD;
            this.page = 0;
            this.initGui();
        });

        String globalLabel = (this.filterMode == FilterMode.GLOBAL ? "\u00a7e\u00a7l" : "") +
                StringUtils.translate("schematic_tiling.gui.cannon.dests.tab_global", globalCount);
        ButtonGeneric tabGlobalBtn = new ButtonGeneric(startX + 169, tabY, 80, 18, globalLabel);
        this.addButton(tabGlobalBtn, (btn, mouse) -> {
            this.filterMode = FilterMode.GLOBAL;
            this.page = 0;
            this.initGui();
        });

        // --- Destination Entries List ---
        List<CannonDestination> list = this.getFilteredDestinations();
        int maxPage = Math.max(0, (list.size() - 1) / ENTRIES_PER_PAGE);
        if (this.page > maxPage) {
            this.page = maxPage;
        }

        int listY = startY + 50;
        int entryW = dialogWidth - 32;
        int entryH = 23;
        int pageStart = this.page * ENTRIES_PER_PAGE;
        int pageEnd = Math.min(list.size(), pageStart + ENTRIES_PER_PAGE);

        for (int i = pageStart; i < pageEnd; i++) {
            final CannonDestination dest = list.get(i);
            int rowY = listY + (i - pageStart) * (entryH + 3);

            // Use Button
            ButtonGeneric useBtn = new ButtonGeneric(startX + 16 + entryW - 68, rowY + 2, 42, 19,
                    StringUtils.translate("schematic_tiling.gui.cannon.dests.button_use"));
            this.addButton(useBtn, (btn, mouse) -> {
                this.parentCannonGui.setDestinationCoords(dest.x, dest.z, dest.name);
                GuiBase.openGui(this.parentCannonGui);
            });

            // Delete Button
            ButtonGeneric delBtn = new ButtonGeneric(startX + 16 + entryW - 22, rowY + 2, 20, 19, "\u00a7cX");
            this.addButton(delBtn, (btn, mouse) -> {
                CannonDestinationManager.getInstance().removeDestination(dest);
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.dests.status_deleted", dest.name);
                this.statusIsError = false;
                this.initGui();
            });
        }

        // --- Pagination Buttons ---
        int navY = listY + ENTRIES_PER_PAGE * (entryH + 3) + 1;
        if (maxPage > 0) {
            ButtonGeneric prevBtn = new ButtonGeneric(startX + 16, navY, 45, 16, "<");
            prevBtn.setEnabled(this.page > 0);
            this.addButton(prevBtn, (btn, mouse) -> {
                if (this.page > 0) {
                    this.page--;
                    this.initGui();
                }
            });

            ButtonGeneric nextBtn = new ButtonGeneric(startX + 16 + entryW - 45, navY, 45, 16, ">");
            nextBtn.setEnabled(this.page < maxPage);
            this.addButton(nextBtn, (btn, mouse) -> {
                if (this.page < maxPage) {
                    this.page++;
                    this.initGui();
                }
            });
        }

        // --- Save Preset Section (Bottom) ---
        int formY = startY + 185;

        String prevName = (this.nameField != null) ? this.nameField.getTextWrapper() : "";
        String prevX = (this.xField != null) ? this.xField.getTextWrapper() : String.format(Locale.ROOT, "%.1f", this.initialDestX);
        String prevZ = (this.zField != null) ? this.zField.getTextWrapper() : String.format(Locale.ROOT, "%.1f", this.initialDestZ);

        this.nameField = new GuiTextFieldGeneric(startX + 56, formY + 14, 98, 18, this.font);
        this.nameField.setTextWrapper(prevName);
        this.addTextField(this.nameField, null);

        this.xField = new GuiTextFieldDouble(startX + 172, formY + 14, 48, 18, this.font);
        this.xField.setTextWrapper(prevX);
        this.addTextField(this.xField, null);

        this.zField = new GuiTextFieldDouble(startX + 238, formY + 14, 48, 18, this.font);
        this.zField.setTextWrapper(prevZ);
        this.addTextField(this.zField, null);

        ButtonGeneric playerBtn = new ButtonGeneric(startX + 292, formY + 14, 52, 18,
                StringUtils.translate("schematic_tiling.gui.cannon.dests.button_player"));
        playerBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.cannon.hover.dest_to_player_desc"));
        this.addButton(playerBtn, (btn, mouse) -> {
            if (this.mc != null && this.mc.player != null) {
                this.xField.setTextWrapper(String.format(Locale.ROOT, "%.1f", this.mc.player.getX()));
                this.zField.setTextWrapper(String.format(Locale.ROOT, "%.1f", this.mc.player.getZ()));
            }
        });

        // Scope toggle button
        String currentWorld = CannonDestinationManager.getCurrentWorldOrServerName();
        String displayWorld = currentWorld.length() > 14 ? currentWorld.substring(0, 12) + "..." : currentWorld;
        String scopeLabel = this.isSaveGlobal
                ? "\u00a76" + StringUtils.translate("schematic_tiling.gui.cannon.dests.scope_global")
                : "\u00a7b" + StringUtils.translate("schematic_tiling.gui.cannon.dests.scope_world", displayWorld);
        ButtonGeneric scopeBtn = new ButtonGeneric(startX + 22, formY + 36, 235, 18, scopeLabel);
        scopeBtn.setHoverStrings(
                StringUtils.translate("schematic_tiling.gui.cannon.dests.scope_hover"),
                "Target: " + (this.isSaveGlobal ? "Global (All Worlds)" : currentWorld));
        this.addButton(scopeBtn, (btn, mouse) -> {
            this.isSaveGlobal = !this.isSaveGlobal;
            this.initGui();
        });

        // Save Button
        ButtonGeneric saveBtn = new ButtonGeneric(startX + 264, formY + 36, 154, 18,
                "\u00a7a" + StringUtils.translate("schematic_tiling.gui.cannon.dests.button_save"));
        this.addButton(saveBtn, (btn, mouse) -> {
            String name = this.nameField.getTextWrapper().trim();
            if (name.isEmpty()) {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.dests.error_name");
                this.statusIsError = true;
                return;
            }
            try {
                double sx = Double.parseDouble(this.xField.getTextWrapper().trim());
                double sz = Double.parseDouble(this.zField.getTextWrapper().trim());
                CannonDestinationManager.getInstance().addDestination(name, sx, sz, this.isSaveGlobal);
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.dests.status_saved",
                        name, this.isSaveGlobal ? "Global" : currentWorld);
                this.statusIsError = false;
                this.nameField.setTextWrapper("");
                this.initGui();
            } catch (Exception e) {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.dests.error_coords");
                this.statusIsError = true;
            }
        });

        // Close / Back button
        ButtonGeneric closeBtn = new ButtonGeneric(startX + dialogWidth - 76, startY + dialogHeight - 26, 60, 20,
                StringUtils.translate("schematic_tiling.gui.cannon.dests.button_back"));
        this.addButton(closeBtn, (btn, mouse) -> GuiBase.openGui(this.parentCannonGui));
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);

        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;
        int entryW = dialogWidth - 32;

        RenderUtils.drawRect(ctx, 0, 0, this.getScreenWidth(), this.getScreenHeight(), 0x80000000);
        RenderUtils.drawOutlinedBox(ctx, startX, startY, dialogWidth, dialogHeight, 0xFF1C1C1C, COLOR_HORIZONTAL_BAR);

        // List row background boxes
        List<CannonDestination> list = this.getFilteredDestinations();
        int listY = startY + 50;
        int entryH = 23;
        int pageStart = this.page * ENTRIES_PER_PAGE;
        int pageEnd = Math.min(list.size(), pageStart + ENTRIES_PER_PAGE);

        for (int i = pageStart; i < pageEnd; i++) {
            int rowY = listY + (i - pageStart) * (entryH + 3);
            RenderUtils.drawOutlinedBox(ctx, startX + 16, rowY, entryW, entryH, 0xD0252525, 0xFF444444);
        }

        // Form Section (Bottom) background box
        int formY = startY + 185;
        RenderUtils.drawOutlinedBox(ctx, startX + 16, formY, entryW, 64, 0xA0202020, 0xFF505050);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        // Title and World subtitle
        this.drawString(ctx, this.title, startX + 16, startY + 12, COLOR_WHITE);
        String currentWorld = CannonDestinationManager.getCurrentWorldOrServerName();
        String worldInfo = StringUtils.translate("schematic_tiling.gui.cannon.dests.world_info", currentWorld);
        int worldInfoW = this.getStringWidth(worldInfo);
        this.drawString(ctx, "\u00a77" + worldInfo, startX + dialogWidth - 16 - worldInfoW, startY + 12, 0xFFAAAAAA);

        // List Area
        List<CannonDestination> list = this.getFilteredDestinations();
        int listY = startY + 50;
        int entryH = 23;
        int pageStart = this.page * ENTRIES_PER_PAGE;
        int pageEnd = Math.min(list.size(), pageStart + ENTRIES_PER_PAGE);

        if (list.isEmpty()) {
            this.drawString(ctx, "\u00a77" + StringUtils.translate("schematic_tiling.gui.cannon.dests.empty"),
                    startX + 16, listY + 30, 0xFFAAAAAA);
            this.drawString(ctx, "\u00a78" + StringUtils.translate("schematic_tiling.gui.cannon.dests.empty_desc"),
                    startX + 16, listY + 44, 0xFF888888);
        } else {
            for (int i = pageStart; i < pageEnd; i++) {
                CannonDestination dest = list.get(i);
                int rowY = listY + (i - pageStart) * (entryH + 3);

                // Scope badge
                String badge = dest.isGlobal ? "\u00a76[G]" : "\u00a7b[W]";
                this.drawString(ctx, badge, startX + 22, rowY + 7, COLOR_WHITE);

                // Name
                String nameStr = "\u00a7f\u00a7l" + dest.name;
                this.drawString(ctx, nameStr, startX + 42, rowY + 7, COLOR_WHITE);

                // Coordinates
                String coordsStr = String.format(Locale.ROOT, "\u00a77X: %.1f  Z: %.1f", dest.x, dest.z);
                this.drawString(ctx, coordsStr, startX + 165, rowY + 7, 0xFFAAAAAA);

                // Distance from current cannon position (if available)
                if (this.currentStartPos != null) {
                    double dist = dest.getDistance2D(this.currentStartPos.getX(), this.currentStartPos.getZ());
                    String distStr = String.format(Locale.ROOT, "\u00a7e(~%.0fm)", dist);
                    this.drawString(ctx, distStr, startX + 285, rowY + 7, 0xFFFFFF55);
                }
            }

            // Pagination info
            int maxPage = Math.max(0, (list.size() - 1) / ENTRIES_PER_PAGE);
            if (maxPage > 0) {
                String pageStr = String.format(Locale.ROOT, "%d / %d", this.page + 1, maxPage + 1);
                int pW = this.getStringWidth(pageStr);
                int navY = listY + ENTRIES_PER_PAGE * (entryH + 3) + 4;
                this.drawString(ctx, "\u00a77" + pageStr, startX + (dialogWidth - pW) / 2, navY, 0xFFAAAAAA);
            }
        }

        // Form Section (Bottom)
        int formY = startY + 185;
        this.drawString(ctx, "\u00a7e" + StringUtils.translate("schematic_tiling.gui.cannon.dests.save_header"),
                startX + 22, formY + 3, 0xFFFFAA00);

        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.dests.name_label"),
                startX + 22, formY + 19, 0xFFAAAAAA);
        this.drawString(ctx, "X:", startX + 160, formY + 19, 0xFFAAAAAA);
        this.drawString(ctx, "Z:", startX + 226, formY + 19, 0xFFAAAAAA);

        // Status Message
        if (this.statusMessage != null) {
            int msgCol = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            this.drawString(ctx, this.statusMessage, startX + 16, startY + dialogHeight - 20, msgCol);
        }
    }
}

