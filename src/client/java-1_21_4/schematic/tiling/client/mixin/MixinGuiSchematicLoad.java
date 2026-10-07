package schematic.tiling.client.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.gui.GuiSchematicLoad;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.SchematicTilingJavafied;
import schematic.tiling.client.gui.GuiSchematicTiler;
import schematic.tiling.client.gui.ISchematicLoadRefreshable;
import schematic.tiling.core.SchematicTilerUtil;

@Mixin(GuiSchematicLoad.class)
public abstract class MixinGuiSchematicLoad extends GuiSchematicBrowserBase implements ISchematicLoadRefreshable {

    @Unique
    private ButtonGeneric schematicTiling_tileButton = null;

    @Unique
    private int schematicTiling_lastShiftAmount = 0;

    protected MixinGuiSchematicLoad(int browserX, int browserY) {
        super(browserX, browserY);
    }

    @Override
    public void schematicTiling_refresh() {
        if (this.getListWidget() != null) {
            this.getListWidget().refreshEntries();
        }
    }

    @Override
    protected ISelectionListener<WidgetFileBrowserBase.DirectoryEntry> getSelectionListener() {
        return this::schematicTiling_updateTileButton;
    }

    @Inject(method = "initGui", at = @At("RETURN"))
    private void schematicTiling_onInitGui(CallbackInfo ci) {
        this.schematicTiling_tileButton = null;
        this.schematicTiling_lastShiftAmount = 0;

        if (this.getListWidget() != null) {
            WidgetFileBrowserBase.DirectoryEntry selected = this.getListWidget().getLastSelectedEntry();
            this.schematicTiling_updateTileButton(selected);
        }
    }

    @Unique
    private void schematicTiling_updateTileButton(WidgetFileBrowserBase.DirectoryEntry selected) {
        boolean isParts = selected != null && SchematicTilerUtil.isPartsSchematic(selected);
        List<ButtonBase> buttons = ((GuiBaseAccessor) this).getButtons();

        if (isParts) {
            if (this.schematicTiling_tileButton != null) {
                // Button already present, keep it
                return;
            }

            SchematicTilingJavafied.LOGGER.info("Parts schematic selected: '{}'. Adding Tile button.", selected.getName());

            // Find Material List button
            String matListLabel = StringUtils.translate("litematica.gui.button.material_list");
            ButtonBase matListBtn = null;
            for (ButtonBase btn : buttons) {
                String text = ((ButtonBaseAccessor) btn).getDisplayString();
                if (matListLabel.equals(text)) {
                    matListBtn = btn;
                    break;
                }
            }

            String tileLabel = StringUtils.translate("schematic_tiling.gui.button.tile_schematic");
            int tileWidth = this.getStringWidth(tileLabel) + 10;
            int y = this.getScreenHeight() - 26;
            int tileX;

            if (matListBtn != null) {
                tileX = matListBtn.getX() + matListBtn.getWidth() + 4;
            } else {
                tileX = 12;
                for (ButtonBase btn : buttons) {
                    if (btn.getY() == y && btn.getX() < this.getScreenWidth() - 150) {
                        tileX = Math.max(tileX, btn.getX() + btn.getWidth() + 4);
                    }
                }
            }

            // Shift buttons to the right
            int shiftAmount = tileWidth + 4;
            this.schematicTiling_lastShiftAmount = shiftAmount;
            for (ButtonBase btn : buttons) {
                if (btn.getX() >= tileX && btn.getX() < this.getScreenWidth() - 120) {
                    btn.setX(btn.getX() + shiftAmount);
                }
            }

            // Create Tile button
            this.schematicTiling_tileButton = new ButtonGeneric(tileX, y, tileWidth, 20, tileLabel);
            this.schematicTiling_tileButton.setHoverStrings(StringUtils.translate("schematic_tiling.gui.button.hover.tile_schematic"));

            GuiSchematicLoad loadScreen = (GuiSchematicLoad) (Object) this;
            this.addButton(this.schematicTiling_tileButton, (btn, mouseButton) -> {
                WidgetFileBrowserBase.DirectoryEntry current = this.getListWidget() != null ? this.getListWidget().getLastSelectedEntry() : null;
                if (current != null && SchematicTilerUtil.isPartsSchematic(current)) {
                    GuiBase.openGui(new GuiSchematicTiler(current, loadScreen));
                }
            });

            SchematicTilingJavafied.LOGGER.info("Tile button successfully added at ({}, {}).", tileX, y);
        } else {
            // If button is currently displayed, remove it and unshift other buttons
            if (this.schematicTiling_tileButton != null) {
                int tileX = this.schematicTiling_tileButton.getX();
                int shiftAmount = this.schematicTiling_lastShiftAmount;

                buttons.remove(this.schematicTiling_tileButton);
                this.schematicTiling_tileButton = null;

                for (ButtonBase btn : buttons) {
                    if (btn.getX() >= tileX + shiftAmount && btn.getX() < this.getScreenWidth() - 120) {
                        btn.setX(btn.getX() - shiftAmount);
                    }
                }

                this.schematicTiling_lastShiftAmount = 0;
            }
        }
    }
}
