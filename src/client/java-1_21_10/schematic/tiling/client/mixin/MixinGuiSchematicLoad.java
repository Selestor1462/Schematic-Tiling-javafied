package schematic.tiling.client.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.gui.GuiSchematicLoad;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.SchematicTilingJavafied;
import schematic.tiling.client.gui.GuiSchematicTiler;
import schematic.tiling.client.gui.ISchematicLoadRefreshable;
import schematic.tiling.core.SchematicTilerUtil;

@Mixin(GuiSchematicLoad.class)
public abstract class MixinGuiSchematicLoad extends GuiSchematicBrowserBase implements ISchematicLoadRefreshable {

    protected MixinGuiSchematicLoad(int browserX, int browserY) {
        super(browserX, browserY);
    }

    @Override
    public void schematicTiling_refresh() {
        if (this.getListWidget() != null) {
            this.getListWidget().refreshEntries();
        }
    }

    @Inject(method = "createButtons", at = @At("RETURN"))
    private void schematicTiling_injectTileButton(CallbackInfo ci) {
        if (this.getListWidget() == null) {
            return;
        }

        WidgetFileBrowserBase.DirectoryEntry selected = this.getListWidget().getLastSelectedEntry();
        if (selected == null || !SchematicTilerUtil.isPartsSchematic(selected)) {
            return;
        }

        SchematicTilingJavafied.LOGGER.info("Parts schematic selected: '{}'. Adding Tile button.", selected.getName());

        List<ButtonBase> buttons = ((GuiBaseAccessor) this).getButtons();

        // Find the Material List button to place the Tile button right next to it
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

        // Shift existing buttons that are located to the right of tileX (except Main Menu which is anchored to right edge)
        int shiftAmount = tileWidth + 4;
        for (ButtonBase btn : buttons) {
            if (btn.getX() >= tileX && btn.getX() < this.getScreenWidth() - 120) {
                btn.setX(btn.getX() + shiftAmount);
            }
        }

        // Create and add the Tile button
        ButtonGeneric tileButton = new ButtonGeneric(tileX, y, tileWidth, 20, tileLabel);
        tileButton.setHoverStrings(StringUtils.translate("schematic_tiling.gui.button.hover.tile_schematic"));

        GuiSchematicLoad loadScreen = (GuiSchematicLoad) (Object) this;
        this.addButton(tileButton, (btn, mouseButton) -> {
            GuiBase.openGui(new GuiSchematicTiler(selected, loadScreen));
        });

        SchematicTilingJavafied.LOGGER.info("Tile button successfully added at ({}, {}).", tileX, y);
    }
}
