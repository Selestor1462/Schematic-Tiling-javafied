package schematic.tiling.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiAreaSelectionEditorNormal;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.SelectionMode;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.client.gui.GuiSchematicTilingEditor;
import schematic.tiling.core.TilingUnit;

@Mixin(GuiAreaSelectionEditorNormal.class)
public abstract class MixinGuiAreaSelectionEditorNormal extends GuiBase {

    @Shadow
    @Final
    protected AreaSelection selection;

    @Shadow
    protected String selectionId;

    @Unique
    @SuppressWarnings("unchecked")
    private List<ButtonBase> schematicTiling_getButtons() {
        try {
            java.lang.reflect.Field f = GuiBase.class.getDeclaredField("buttons");
            f.setAccessible(true);
            return (List<ButtonBase>) f.get(this);
        } catch (Throwable t) {
            return java.util.Collections.emptyList();
        }
    }

    @Unique
    private int schematicTiling_findAvailableY(int btnWidth, int startY) {
        int y = startY;
        List<ButtonBase> currentButtons = this.schematicTiling_getButtons();
        while (y < this.getScreenHeight() - 30) {
            int maxRightOnRow = 0;
            for (ButtonBase btn : currentButtons) {
                if (btn.getY() + btn.getHeight() > y && btn.getY() < y + 20) {
                    maxRightOnRow = Math.max(maxRightOnRow, btn.getX() + btn.getWidth());
                }
            }
            int preferredX = this.getScreenWidth() - btnWidth - 10;
            if (preferredX >= maxRightOnRow + 6) {
                return y;
            }
            y += 24;
        }
        return startY;
    }

    @Inject(method = "initGui", at = @At("RETURN"))
    private void schematicTiling_injectTilingEditorButton(CallbackInfo ci) {
        if (this.selection == null) {
            return;
        }

        int nextStartY = 24;

        // 1. Tiling Setup Button (Only in Normal selection mode for parts schematics)
        if (DataManager.getSelectionManager().getSelectionMode() == SelectionMode.NORMAL
                && this.schematicTiling_isPartsSelection()) {
            String btnLabel = StringUtils.translate("schematic_tiling.gui.button.tiling_setup");
            int btnWidth = Math.max(90, this.getStringWidth(btnLabel) + 14);
            int btnHeight = 20;
            int y = this.schematicTiling_findAvailableY(btnWidth, nextStartY);
            int x = Math.max(10, this.getScreenWidth() - btnWidth - 10);

            ButtonGeneric tilingBtn = new ButtonGeneric(x, y, btnWidth, btnHeight, btnLabel);
            tilingBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.button.hover.tiling_setup"));

            this.addButton(tilingBtn, (btn, mouseBtn) -> {
                GuiBase.openGui(new GuiSchematicTilingEditor(this.selection, this));
            });
            nextStartY = y + 24;
        }
    }

    @Unique
    private boolean schematicTiling_isPartsSelection() {
        if (this.selection == null) {
            return false;
        }

        // 1. Check selection name
        String selName = this.selection.getName();
        if (selName != null && selName.toLowerCase().contains("parts")) {
            return true;
        }

        // 2. Check selection ID (file path or entry name)
        if (this.selectionId != null && this.selectionId.toLowerCase().contains("parts")) {
            return true;
        }

        // 3. Check if any subregion box name already has tiling syntax (" // ")
        for (String boxName : this.selection.getAllSubRegionNames()) {
            if (boxName.contains(TilingUnit.SEP)) {
                return true;
            }
        }

        // 4. Check currently selected schematic placement, if any
        SchematicPlacement placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        if (placement != null) {
            String pName = placement.getName();
            if (pName != null && pName.toLowerCase().contains("parts")) {
                return true;
            }
            if (placement.getSchematic() != null && placement.getSchematic().getMetadata() != null) {
                String metaName = placement.getSchematic().getMetadata().getName();
                if (metaName != null && metaName.toLowerCase().contains("parts")) {
                    return true;
                }
            }
        }

        return false;
    }
}
