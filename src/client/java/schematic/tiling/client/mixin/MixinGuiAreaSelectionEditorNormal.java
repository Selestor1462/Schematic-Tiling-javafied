package schematic.tiling.client.mixin;

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
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.client.gui.GuiReturnCannon;
import schematic.tiling.client.gui.GuiSchematicTilingEditor;
import schematic.tiling.core.TilingUnit;

@Mixin(GuiAreaSelectionEditorNormal.class)
public abstract class MixinGuiAreaSelectionEditorNormal extends GuiBase {

    @Shadow
    @Final
    protected AreaSelection selection;

    @Shadow
    protected String selectionId;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void schematicTiling_injectTilingEditorButton(CallbackInfo ci) {
        if (this.selection == null) {
            return;
        }

        boolean hasTilingBtn = false;

        // 1. Tiling Setup Button (Only in Normal selection mode for parts schematics)
        if (DataManager.getSelectionManager().getSelectionMode() == SelectionMode.NORMAL
                && this.schematicTiling_isPartsSelection()) {
            String btnLabel = StringUtils.translate("schematic_tiling.gui.button.tiling_setup");
            int btnWidth = Math.max(90, this.getStringWidth(btnLabel) + 14);
            int btnHeight = 20;
            int x = this.getScreenWidth() - btnWidth - 10;
            int y = 24;

            ButtonGeneric tilingBtn = new ButtonGeneric(x, y, btnWidth, btnHeight, btnLabel);
            tilingBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.button.hover.tiling_setup"));

            this.addButton(tilingBtn, (btn, mouseBtn) -> {
                GuiBase.openGui(new GuiSchematicTilingEditor(this.selection, this));
            });
            hasTilingBtn = true;
        }

        // 2. Return Cannon Button (In either selection mode when selection ends with "cannon")
        if (this.schematicTiling_isCannonSelection()) {
            String btnLabel = StringUtils.translate("schematic_tiling.gui.button.return_cannon");
            int btnWidth = Math.max(95, this.getStringWidth(btnLabel) + 14);
            int btnHeight = 20;
            int x = this.getScreenWidth() - btnWidth - 10;
            int y = hasTilingBtn ? 48 : 24;

            ButtonGeneric cannonBtn = new ButtonGeneric(x, y, btnWidth, btnHeight, btnLabel);
            cannonBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.button.hover.return_cannon"));

            this.addButton(cannonBtn, (btn, mouseBtn) -> {
                GuiBase.openGui(new GuiReturnCannon(this.selection, this));
            });
        }
    }

    @Unique
    private boolean schematicTiling_isCannonSelection() {
        if (this.selection == null) {
            return false;
        }

        // 1. Check selection name
        String selName = this.selection.getName();
        if (selName != null && selName.trim().toLowerCase().endsWith("cannon")) {
            return true;
        }

        // 2. Check selection ID
        if (this.selectionId != null && this.selectionId.trim().toLowerCase().endsWith("cannon")) {
            return true;
        }

        // 3. Check currently selected schematic placement, if any
        SchematicPlacement placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        if (placement != null) {
            String pName = placement.getName();
            if (pName != null && pName.trim().toLowerCase().endsWith("cannon")) {
                return true;
            }
            if (placement.getSchematic() != null && placement.getSchematic().getMetadata() != null) {
                String metaName = placement.getSchematic().getMetadata().getName();
                if (metaName != null && metaName.trim().toLowerCase().endsWith("cannon")) {
                    return true;
                }
            }
        }

        return false;
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
