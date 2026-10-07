package schematic.tiling.client.gui;

import java.nio.file.Path;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.gui.GuiSchematicLoad;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetCheckBox;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextFieldInteger;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.core.KnapsackSolver;
import schematic.tiling.core.SchematicTilerUtil;
import schematic.tiling.core.TilingTree;

public class GuiSchematicTiler extends GuiBase {

    private final WidgetFileBrowserBase.DirectoryEntry selectedEntry;
    private final GuiSchematicLoad loadScreen;

    private TilingTree tilingTree;
    private String initError = null;
    private String statusMessage = null;
    private boolean statusIsError = false;

    private GuiTextFieldInteger widthField;
    private GuiTextFieldInteger lengthField;
    private GuiTextFieldGeneric authorField;

    private WidgetCheckBox checkboxLoadToMemory;
    private WidgetCheckBox checkboxCreatePlacement;
    private ButtonGeneric applyButton;

    public GuiSchematicTiler(WidgetFileBrowserBase.DirectoryEntry selectedEntry, GuiSchematicLoad loadScreen) {
        this.selectedEntry = selectedEntry;
        this.loadScreen = loadScreen;
        this.setParent(loadScreen);

        try {
            this.tilingTree = SchematicTilerUtil.loadTilingTree(selectedEntry.getFullPath());
            this.title = StringUtils.translate("schematic_tiling.gui.title.tiler", this.tilingTree.getName());
        } catch (Throwable t) {
            this.tilingTree = null;
            this.initError = t.getMessage();
            this.title = StringUtils.translate("schematic_tiling.gui.title.tiler_error");
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        boolean needsLength = this.tilingTree != null && this.tilingTree.needsLength();
        int dialogWidth = 320;
        int dialogHeight = needsLength ? 230 : 204;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        int labelX = startX + 20;
        int inputX = startX + 110;
        int inputWidth = 180;
        int y = startY + 40;

        // Width field
        this.widthField = new GuiTextFieldInteger(inputX, y, inputWidth, 20, this.textRenderer);
        this.widthField.setTextWrapper("16");
        this.addTextField(this.widthField, null);
        y += 26;

        // Length field (only if tree needs length)
        if (needsLength) {
            this.lengthField = new GuiTextFieldInteger(inputX, y, inputWidth, 20, this.textRenderer);
            this.lengthField.setTextWrapper("16");
            this.addTextField(this.lengthField, null);
            y += 26;
        } else {
            this.lengthField = null;
        }

        // Author field
        this.authorField = new GuiTextFieldGeneric(inputX, y, inputWidth, 20, this.textRenderer);
        String defaultAuthor = (this.mc.player != null) ? this.mc.player.getName().getString() : "Player";
        if (this.tilingTree != null && this.tilingTree.getSourceSchematic().getMetadata().getAuthor() != null) {
            String origAuthor = this.tilingTree.getSourceSchematic().getMetadata().getAuthor();
            if (!origAuthor.isBlank()) {
                defaultAuthor = origAuthor;
            }
        }
        this.authorField.setTextWrapper(defaultAuthor);
        this.addTextField(this.authorField, null);
        y += 28;

        // Checkbox: Load to memory
        String loadLabel = StringUtils.translate("schematic_tiling.gui.checkbox.load_to_memory");
        this.checkboxLoadToMemory = new WidgetCheckBox(
                labelX, y,
                Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED,
                loadLabel
        );
        this.checkboxLoadToMemory.setChecked(true, false);
        this.addWidget(this.checkboxLoadToMemory);
        y += 18;

        // Checkbox: Create placement
        String placementLabel = StringUtils.translate("schematic_tiling.gui.checkbox.create_placement");
        this.checkboxCreatePlacement = new WidgetCheckBox(
                labelX, y,
                Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED,
                placementLabel
        );
        this.checkboxCreatePlacement.setChecked(DataManager.getCreatePlacementOnLoad(), false);
        this.addWidget(this.checkboxCreatePlacement);

        // Buttons
        int buttonWidth = 90;
        int buttonY = startY + dialogHeight - 34;
        int applyX = startX + 55;
        int cancelX = startX + dialogWidth - 55 - buttonWidth;

        this.applyButton = new ButtonGeneric(
                applyX, buttonY, buttonWidth, 20,
                StringUtils.translate("schematic_tiling.gui.button.apply")
        );
        this.addButton(this.applyButton, (button, mouseButton) -> this.applyValues());

        ButtonGeneric cancelButton = new ButtonGeneric(
                cancelX, buttonY, buttonWidth, 20,
                StringUtils.translate("schematic_tiling.gui.button.cancel")
        );
        this.addButton(cancelButton, (button, mouseButton) -> this.closeGui(true));

        if (this.initError != null) {
            this.widthField.setEditable(false);
            if (this.lengthField != null) {
                this.lengthField.setEditable(false);
            }
            this.authorField.setEditable(false);
            this.applyButton.setEnabled(false);
            this.statusMessage = this.initError;
            this.statusIsError = true;
        }
    }

    private void applyValues() {
        if (this.tilingTree == null) {
            return;
        }

        String widthStr = this.widthField.getTextWrapper().trim();
        String lengthStr = (this.lengthField != null) ? this.lengthField.getTextWrapper().trim() : "0";
        String authorStr = this.authorField.getTextWrapper().trim();

        int widthVal;
        int lengthVal = 0;

        try {
            widthVal = Integer.parseInt(widthStr);
            if (this.lengthField != null) {
                lengthVal = Integer.parseInt(lengthStr);
            }
            if (widthVal <= 0 || (this.lengthField != null && lengthVal <= 0)) {
                throw new IllegalArgumentException(StringUtils.translate("schematic_tiling.gui.error.positive_numbers"));
            }
        } catch (NumberFormatException e) {
            this.statusMessage = StringUtils.translate("schematic_tiling.gui.error.positive_numbers");
            this.statusIsError = true;
            return;
        } catch (IllegalArgumentException e) {
            this.statusMessage = e.getMessage();
            this.statusIsError = true;
            return;
        }

        Path outputDir = this.selectedEntry.getDirectory();
        if (outputDir == null) {
            outputDir = DataManager.getSchematicsBaseDirectory();
        }

        try {
            SchematicTilerUtil.GenerationResult result = SchematicTilerUtil.generateAndSaveSchematic(
                    this.tilingTree,
                    widthVal,
                    lengthVal,
                    authorStr,
                    outputDir
            );

            // Load to memory if selected
            if (this.checkboxLoadToMemory.isChecked()) {
                SchematicHolder.getInstance().addSchematic(result.schematic(), true);
            }

            // Create placement in world if selected
            if (this.checkboxCreatePlacement.isChecked() && this.mc.player != null) {
                BlockPos pos = BlockPos.containing(this.mc.player.position());
                String name = result.schematic().getMetadata().getName();
                boolean enabled = !GuiBase.isShiftDown();

                SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
                SchematicPlacement placement = SchematicPlacement.createFor(result.schematic(), pos, name, enabled, enabled);
                manager.addSchematicPlacement(placement, true);
                manager.setSelectedSchematicPlacement(placement);
            }

            // Refresh parent list
            if (this.loadScreen instanceof ISchematicLoadRefreshable refreshable) {
                refreshable.schematicTiling_refresh();
            }

            String successMsg = StringUtils.translate("schematic_tiling.gui.message.generated", result.schematic().getMetadata().getName());
            InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS, successMsg);

            this.closeGui(true);

        } catch (KnapsackSolver.KnapsackSolutionNotFound knapsackErr) {
            this.statusIsError = true;
            int suggestedWidth = widthVal - knapsackErr.getRemainder();
            this.statusMessage = knapsackErr.getMessage() + "\n" +
                    StringUtils.translate("schematic_tiling.gui.error.try_width", suggestedWidth, widthVal);
        } catch (Throwable t) {
            this.statusIsError = true;
            this.statusMessage = t.getMessage();
        }
    }

    @Override
    protected void drawScreenBackground(GuiGraphics ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);
        ctx.fill(0, 0, this.getScreenWidth(), this.getScreenHeight(), 0x80000000);

        boolean needsLength = this.tilingTree != null && this.tilingTree.needsLength();
        int dialogWidth = 320;
        int dialogHeight = needsLength ? 230 : 204;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        // Solid opaque dialog box (no see-through, no blur, high contrast)
        ctx.fill(startX, startY, startX + dialogWidth, startY + dialogHeight, 0xFF1C1C1C);

        // Border outline using fill
        int x2 = startX + dialogWidth;
        int y2 = startY + dialogHeight;
        int borderColor = COLOR_HORIZONTAL_BAR;
        ctx.fill(startX, startY, x2, startY + 1, borderColor); // Top
        ctx.fill(startX, y2 - 1, x2, y2, borderColor); // Bottom
        ctx.fill(startX, startY, startX + 1, y2, borderColor); // Left
        ctx.fill(x2 - 1, startY, x2, y2, borderColor); // Right
    }

    @Override
    protected void drawContents(GuiGraphics ctx, int mouseX, int mouseY, float partialTicks) {
        boolean needsLength = this.tilingTree != null && this.tilingTree.needsLength();
        int dialogWidth = 320;
        int dialogHeight = needsLength ? 230 : 204;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        // Header instructions
        String subtitle = StringUtils.translate("schematic_tiling.gui.label.instructions");
        int subWidth = this.getStringWidth(subtitle);
        this.drawStringWithShadow(ctx, subtitle, startX + (dialogWidth - subWidth) / 2, startY + 14, COLOR_WHITE);

        int labelX = startX + 20;
        int y = startY + 46;

        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.label.width"), labelX, y, COLOR_WHITE);
        y += 26;

        if (this.lengthField != null) {
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.label.length"), labelX, y, COLOR_WHITE);
            y += 26;
        }

        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.label.author"), labelX, y, COLOR_WHITE);

        // Status or Error message
        if (this.statusMessage != null && !this.statusMessage.isEmpty()) {
            int color = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            String[] lines = this.statusMessage.split("\n");
            int buttonY = startY + dialogHeight - 34;
            int msgY = buttonY - 10 - (lines.length * 10);
            for (String line : lines) {
                int lineWidth = this.getStringWidth(line);
                this.drawStringWithShadow(ctx, line, startX + (dialogWidth - lineWidth) / 2, msgY, color);
                msgY += 10;
            }
        }
    }
}
