package schematic.tiling.client.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;

import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetCheckBox;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.core.TilingUnit;
import schematic.tiling.core.XDependence;

public class GuiSchematicTilingEditor extends GuiBase {

    public static class TileModel {
        private String name;
        private XDependence xDep = XDependence.TILE;
        private boolean dependsOnZ = false;
        private final String assignedBoxName;

        public TileModel(String name, String assignedBoxName) {
            this.name = name;
            this.assignedBoxName = assignedBoxName;
        }

        public TileModel(String name, String assignedBoxName, XDependence xDep, boolean dependsOnZ) {
            this.name = name;
            this.assignedBoxName = assignedBoxName;
            this.xDep = xDep;
            this.dependsOnZ = dependsOnZ;
        }

        public String getName() { return this.name; }
        public void setName(String name) { this.name = name; }
        public XDependence getXDep() { return this.xDep; }
        public void setXDep(XDependence xDep) { this.xDep = xDep; }
        public boolean dependsOnZ() { return this.dependsOnZ; }
        public void setDependsOnZ(boolean dependsOnZ) { this.dependsOnZ = dependsOnZ; }
        public String getAssignedBoxName() { return this.assignedBoxName; }
    }

    public static class SubgroupModel {
        private String name;
        private boolean dependsOnZ = false;
        private final List<TileModel> tiles = new ArrayList<>();

        public SubgroupModel(String name) { this.name = name; }
        public String getName() { return this.name; }
        public void setName(String name) { this.name = name; }
        public boolean dependsOnZ() { return this.dependsOnZ; }
        public void setDependsOnZ(boolean dependsOnZ) { this.dependsOnZ = dependsOnZ; }
        public void sortTiles() {
            this.tiles.sort((a, b) -> {
                int pa = getRoleOrder(a.getXDep());
                int pb = getRoleOrder(b.getXDep());
                if (pa != pb) return Integer.compare(pa, pb);
                return a.getName().compareToIgnoreCase(b.getName());
            });
        }

        public List<TileModel> getTiles() {
            return this.tiles;
        }

        private static int getRoleOrder(XDependence dep) {
            if (dep == null || dep == XDependence.NONE) return 0; // Static first
            if (dep == XDependence.TILE) return 1;                // Modular tiles second
            if (dep == XDependence.CAP) return 2;                 // Cap last
            return 3;
        }

        public boolean containsBox(String boxName) {
            if (boxName == null) return false;
            for (TileModel tm : this.tiles) {
                if (boxName.equals(tm.getAssignedBoxName())) return true;
            }
            return false;
        }
    }

    public static class GroupModel {
        private String name;
        private final Map<String, SubgroupModel> subgroups = new LinkedHashMap<>();
        private boolean dependsOnZ = false;

        public GroupModel(String name) { this.name = name; }
        public String getName() { return this.name; }
        public void setName(String name) { this.name = name; }
        public boolean dependsOnZ() { return this.dependsOnZ; }
        public void setDependsOnZ(boolean dependsOnZ) { this.dependsOnZ = dependsOnZ; }
        public Map<String, SubgroupModel> getSubgroups() { return this.subgroups; }
    }

    public static class ClickableRowButton extends ButtonGeneric {
        private final int normalTextColor;
        private final int hoverTextColor;
        private final int activeTextColor;
        private final int activeHoverTextColor;
        private final boolean isSelected;
        private final int bgGlowColor;

        public ClickableRowButton(int x, int y, int width, int height, String text, boolean isSelected,
                                  int normalColor, int hoverColor, int activeColor, int activeHoverColor, int bgGlowColor) {
            super(x, y, width, height, text);
            this.isSelected = isSelected;
            this.normalTextColor = normalColor;
            this.hoverTextColor = hoverColor;
            this.activeTextColor = activeColor;
            this.activeHoverTextColor = activeHoverColor;
            this.bgGlowColor = bgGlowColor;
            this.setTextCentered(false);
            this.setRenderDefaultBackground(false);
        }

        public ClickableRowButton(int x, int y, int width, int height, String text, boolean isSelected,
                                  int normalColor, int hoverColor, int activeColor, int bgGlowColor) {
            this(x, y, width, height, text, isSelected, normalColor, hoverColor, activeColor, 0xFFFFFFFF, bgGlowColor);
        }

        public ClickableRowButton(int x, int y, int width, int height, String text, boolean isSelected,
                                  int normalColor, int hoverColor) {
            this(x, y, width, height, text, isSelected, normalColor, hoverColor, 0xFF55FF55, 0xFF88FF88, 0x2255FF55);
        }

        @Override
        public void render(GuiContext ctx, int mouseX, int mouseY, boolean unused) {
            if (!this.visible) return;

            this.hovered = this.isMouseOver(mouseX, mouseY);

            if (this.isSelected && this.bgGlowColor != 0) {
                RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, this.bgGlowColor);
            } else if (this.hovered) {
                RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0x18FFFFFF);
            }

            int textY = this.y + (this.height - 8) / 2;
            int color = this.isSelected ? (this.hovered ? this.activeHoverTextColor : this.activeTextColor) : (this.hovered ? this.hoverTextColor : this.normalTextColor);

            String text = this.displayString;
            if (this.textRenderer != null && this.getStringWidth(text) > this.width - 8) {
                while (text.length() > 3 && this.getStringWidth(text + "...") > this.width - 8) {
                    text = text.substring(0, text.length() - 1);
                }
                text = text + "...";
            }

            this.drawStringWithShadow(ctx, this.x + 4, textY, color, text);
        }
    }

    // Persistent user preferences across GUI re-opens
    private static String sLastSelectedGroup = "Main";
    private static String sLastSelectedSubgroup = null;
    private static String sLastFormGroup = "Main";
    private static String sLastFormSubgroup = null;
    private static String sLastTileName = null;
    private static XDependence sLastRole = XDependence.TILE;
    private static boolean sLastDependsOnZ = false;
    private static String sLastEditingBoxName = null;
    private static boolean sLastFineTuning = false;
    private static boolean sLastWidthIsBigger = true;
    private static boolean sLastLengthIsBigger = false;
    private static String sLastTuneWidth = "0";
    private static String sLastTuneLength = "0";
    private static boolean sLastNormalizeOriginY = false;

    // Persistent empty groups & subgroups across GUI reopens
    private static final Map<String, Map<String, Set<String>>> sEmptyStructureBySelection = new LinkedHashMap<>();
    private static final Map<String, Map<String, Boolean>> sGroupDependsOnZBySelection = new LinkedHashMap<>();
    private static final Map<String, Map<String, Boolean>> sSubgroupDependsOnZBySelection = new LinkedHashMap<>();
    private static final Map<AreaSelection, Map<String, Set<String>>> sEmptyStructureByRef = new java.util.WeakHashMap<>();
    private static final Map<AreaSelection, Map<String, Boolean>> sGroupDependsOnZByRef = new java.util.WeakHashMap<>();
    private static final Map<AreaSelection, Map<String, Boolean>> sSubgroupDependsOnZByRef = new java.util.WeakHashMap<>();

    private final AreaSelection selection;
    private final Map<String, GroupModel> groups = new LinkedHashMap<>();
    private final List<String> unassignedBoxes = new ArrayList<>();
    private Set<String> lastKnownSubRegionNames = new LinkedHashSet<>();
    // Track empty groups/subgroups (those without tiles) during session
    private final Map<String, Set<String>> emptyStructure = new LinkedHashMap<>();
    private final Map<String, Boolean> groupDependsOnZ = new LinkedHashMap<>();
    private final Map<String, Boolean> subgroupDependsOnZ = new LinkedHashMap<>();

    private String getSelectionKey() {
        return (this.selection != null && this.selection.getName() != null) ? this.selection.getName() : "default";
    }

    private void loadSessionState() {
        this.emptyStructure.clear();
        this.groupDependsOnZ.clear();
        this.subgroupDependsOnZ.clear();

        Map<String, Set<String>> savedEmpty = null;
        Map<String, Boolean> savedGroupZ = null;
        Map<String, Boolean> savedSubgroupZ = null;

        if (this.selection != null) {
            savedEmpty = sEmptyStructureByRef.get(this.selection);
            savedGroupZ = sGroupDependsOnZByRef.get(this.selection);
            savedSubgroupZ = sSubgroupDependsOnZByRef.get(this.selection);
        }

        String key = this.getSelectionKey();
        if (savedEmpty == null) savedEmpty = sEmptyStructureBySelection.get(key);
        if (savedGroupZ == null) savedGroupZ = sGroupDependsOnZBySelection.get(key);
        if (savedSubgroupZ == null) savedSubgroupZ = sSubgroupDependsOnZBySelection.get(key);

        if (savedEmpty != null) {
            for (Map.Entry<String, Set<String>> e : savedEmpty.entrySet()) {
                this.emptyStructure.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
            }
        }
        if (savedGroupZ != null) {
            this.groupDependsOnZ.putAll(savedGroupZ);
        }
        if (savedSubgroupZ != null) {
            this.subgroupDependsOnZ.putAll(savedSubgroupZ);
        }
    }

    private String selectedGroupName = null;
    private String selectedSubgroupName = null;

    // Currently configuring box
    private String editingBoxName = null;

    // Config form values
    private String formGroup = null;
    private String formSubgroup = null;
    private String formTileName = null;
    private boolean isCustomGroup = false;
    private boolean isCustomSubgroup = false;

    // Right Panel Group/Subgroup settings inputs
    private GuiTextFieldGeneric inputGroupNameField;
    private GuiTextFieldGeneric inputSubgroupNameField;

    // Renaming in Right Panel Box Config
    private boolean isRenamingGroupRight = false;
    private GuiTextFieldGeneric inputRenameGroupRight;

    private boolean isRenamingSubgroupRight = false;
    private GuiTextFieldGeneric inputRenameSubgroupRight;

    // Fine-Tuning Manual Origin
    private boolean isFineTuning = false;
    private boolean widthIsBigger = true;
    private boolean lengthIsBigger = false;
    private String tuneWidthText = "0";
    private String tuneLengthText = "0";
    private boolean normalizeOriginY = false;
    private GuiTextFieldGeneric inputTuneWidth;
    private GuiTextFieldGeneric inputTuneLength;
    private ButtonGeneric buttonWidthComp;
    private ButtonGeneric buttonLengthComp;
    private WidgetCheckBox checkboxNormalizeOriginY;

    private GuiTextFieldGeneric inputCustomGroup;
    private GuiTextFieldGeneric inputCustomSubgroup;
    private GuiTextFieldGeneric inputNameField;
    private WidgetCheckBox checkboxDependsOnZ;
    private ButtonGeneric buttonGroupSelect;
    private ButtonGeneric buttonSubgroupSelect;
    private ButtonGeneric buttonRole;

    private XDependence currentRole = XDependence.TILE;
    private boolean currentDependsOnZ = false;

    private String statusMessage = null;
    private boolean statusIsError = false;

    private String toastNotification = null;
    private long toastNotificationTime = 0L;
    private static final long TOAST_DURATION_MS = 3500L;

    private int yLeftTreeHeader = 100;
    private int yLeftSubgroupsHeader = 140;
    private int yLeftUnassignedHeader = 200;

    public GuiSchematicTilingEditor(AreaSelection selection, GuiBase parentScreen) {
        this.selection = selection;
        this.setParent(parentScreen);
        this.useTitleHierarchy = false;
        this.title = StringUtils.translate("schematic_tiling.gui.editor.title", selection.getName());

        this.selectedGroupName = sLastSelectedGroup;
        this.selectedSubgroupName = sLastSelectedSubgroup;
        this.formGroup = sLastFormGroup;
        this.formSubgroup = sLastFormSubgroup;
        this.formTileName = sLastTileName;
        this.currentRole = sLastRole;
        this.currentDependsOnZ = sLastDependsOnZ;
        this.isFineTuning = sLastFineTuning;
        this.widthIsBigger = sLastWidthIsBigger;
        this.lengthIsBigger = sLastLengthIsBigger;
        this.tuneWidthText = sLastTuneWidth;
        this.tuneLengthText = sLastTuneLength;
        this.normalizeOriginY = sLastNormalizeOriginY;

        this.lastKnownSubRegionNames = new LinkedHashSet<>(this.selection.getAllSubRegionNames());
        this.loadSessionState();
        this.parseSelectionBoxes();

        // Default to last edited box if still valid, otherwise active selection box from Minecraft, otherwise first unassigned
        if (sLastEditingBoxName != null && this.selection.getSubRegionBox(sLastEditingBoxName) != null) {
            this.openBoxForConfig(sLastEditingBoxName);
        } else {
            String active = this.selection.getCurrentSubRegionBoxName();
            if (active != null && this.selection.getSubRegionBox(active) != null) {
                this.openBoxForConfig(active);
            } else if (!this.unassignedBoxes.isEmpty()) {
                this.openBoxForConfig(this.unassignedBoxes.get(0));
            }
        }
    }

    private void parseSelectionBoxes() {
        // Collect existing empty subgroups before clearing
        Map<String, Set<String>> existingEmpty = new LinkedHashMap<>();
        // 1. Seed from emptyStructure so empty groups & subgroups are preserved across GUI reopens
        for (Map.Entry<String, Set<String>> entry : this.emptyStructure.entrySet()) {
            existingEmpty.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).addAll(entry.getValue());
        }
        // 2. Also track any groups/subgroups currently in this.groups that are empty
        for (Map.Entry<String, GroupModel> gEntry : this.groups.entrySet()) {
            Set<String> emptySgs = existingEmpty.computeIfAbsent(gEntry.getKey(), k -> new LinkedHashSet<>());
            for (SubgroupModel sg : gEntry.getValue().getSubgroups().values()) {
                if (sg.getTiles().isEmpty()) {
                    emptySgs.add(sg.getName());
                }
            }
        }

        this.groups.clear();
        this.unassignedBoxes.clear();

        Collection<String> allBoxes = this.selection.getAllSubRegionNames();
        this.lastKnownSubRegionNames = new LinkedHashSet<>(allBoxes);
        for (String boxName : allBoxes) {
            if (boxName.contains(TilingUnit.SEP)) {
                String[] parts = boxName.split(TilingUnit.SEP);
                String groupName = parts.length > 0 ? parts[0].trim() : "Main";
                String subgroupId = parts.length > 1 ? parts[1].trim() : "";
                XDependence xDep = parts.length > 2 ? XDependence.fromCode(parts[2].trim()) : XDependence.NONE;
                boolean zDep = false;
                if (parts.length > 3) {
                    try { zDep = Integer.parseInt(parts[3].trim()) != 0; } catch (Exception ignored) {}
                }

                GroupModel group = this.groups.computeIfAbsent(groupName, GroupModel::new);
                String sgName = null;
                String unitName = subgroupId;
                if (subgroupId.contains("::")) {
                    String[] subParts = subgroupId.split("::", 2);
                    sgName = subParts[0].trim();
                    unitName = subParts[1].trim();
                }

                if (sgName != null) {
                    SubgroupModel sg = group.getSubgroups().computeIfAbsent(sgName, SubgroupModel::new);
                    TileModel tm = new TileModel(unitName.isEmpty() ? boxName : unitName, boxName);
                    tm.setXDep(xDep);
                    tm.setDependsOnZ(zDep);
                    sg.getTiles().add(tm);
                } else {
                    // Box without subgroup - add directly to group (shouldn't happen with new format but handle for compatibility)
                    this.unassignedBoxes.add(boxName);
                }
            } else {
                this.unassignedBoxes.add(boxName);
            }
        }

        // Restore empty groups/subgroups
        for (Map.Entry<String, Set<String>> entry : existingEmpty.entrySet()) {
            GroupModel gm = this.groups.computeIfAbsent(entry.getKey(), GroupModel::new);
            // Restore group Z-dependence
            if (this.groupDependsOnZ.containsKey(entry.getKey())) {
                gm.setDependsOnZ(this.groupDependsOnZ.get(entry.getKey()));
            }
            // Add to emptyStructure to preserve group even if it has no subgroups
            this.emptyStructure.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            // Restore empty subgroups
            for (String sgName : entry.getValue()) {
                if (!gm.getSubgroups().containsKey(sgName)) {
                    SubgroupModel sg = new SubgroupModel(sgName);
                    String sgKey = gm.getName() + " // " + sgName;
                    if (this.subgroupDependsOnZ.containsKey(sgKey)) {
                        sg.setDependsOnZ(this.subgroupDependsOnZ.get(sgKey));
                    } else if (gm.dependsOnZ()) {
                        sg.setDependsOnZ(true);
                    }
                    gm.getSubgroups().put(sgName, sg);
                }
            }
        }

        for (GroupModel gm : this.groups.values()) {
            for (SubgroupModel sgm : gm.getSubgroups().values()) {
                String sgKey = gm.getName() + " // " + sgm.getName();
                if (sgm.getTiles().isEmpty()) {
                    if (this.subgroupDependsOnZ.containsKey(sgKey)) {
                        sgm.setDependsOnZ(this.subgroupDependsOnZ.get(sgKey));
                    } else if (gm.dependsOnZ()) {
                        sgm.setDependsOnZ(true);
                        this.subgroupDependsOnZ.put(sgKey, true);
                    }
                } else if (!this.hasMixedZDependence(sgm.getTiles())) {
                    boolean allZ = sgm.getTiles().get(0).dependsOnZ();
                    sgm.setDependsOnZ(allZ);
                    this.subgroupDependsOnZ.put(sgKey, allZ);
                } else if (this.subgroupDependsOnZ.containsKey(sgKey)) {
                    sgm.setDependsOnZ(this.subgroupDependsOnZ.get(sgKey));
                } else {
                    sgm.setDependsOnZ(false);
                    this.subgroupDependsOnZ.put(sgKey, false);
                }
                sgm.sortTiles();
            }

            // Group-level Z-dependence reconciliation
            if (!gm.getSubgroups().isEmpty()) {
                boolean allSgZ = true;
                for (SubgroupModel sgm : gm.getSubgroups().values()) {
                    if (!sgm.dependsOnZ()) {
                        allSgZ = false;
                        break;
                    }
                }
                if (allSgZ) {
                    gm.setDependsOnZ(true);
                } else if (this.groupDependsOnZ.containsKey(gm.getName())) {
                    gm.setDependsOnZ(this.groupDependsOnZ.get(gm.getName()));
                } else {
                    gm.setDependsOnZ(false);
                }
            } else if (this.groupDependsOnZ.containsKey(gm.getName())) {
                gm.setDependsOnZ(this.groupDependsOnZ.get(gm.getName()));
            }
            this.groupDependsOnZ.put(gm.getName(), gm.dependsOnZ());
        }

        if (this.selectedGroupName == null || !this.groups.containsKey(this.selectedGroupName)) {
            if (!this.groups.isEmpty()) {
                this.selectedGroupName = this.groups.keySet().iterator().next();
            } else {
                this.selectedGroupName = null;
            }
        }
        GroupModel activeGroup = this.groups.get(this.selectedGroupName);
        if (this.selectedSubgroupName != null && activeGroup != null && !activeGroup.getSubgroups().containsKey(this.selectedSubgroupName)) {
            this.selectedSubgroupName = null;
        }
    }

    public static String formatSubregionName(String group, String subgroup, String tileName, XDependence xDep, boolean zDep) {
        String sg = (subgroup != null && !subgroup.trim().isEmpty() && !subgroup.equalsIgnoreCase("(None)") && !subgroup.equalsIgnoreCase("None"))
                ? subgroup.trim() : null;
        String unitId = (sg != null) ? sg + "::" + tileName.trim() : tileName.trim();
        return group.trim() + " // " + unitId + " // " + xDep.getCode() + " // " + (zDep ? "1" : "0") + " // 0";
    }

    private String getBoxSubgroupName(String boxName) {
        if (boxName == null || !boxName.contains(TilingUnit.SEP)) {
            return null;
        }
        String[] parts = boxName.split(TilingUnit.SEP);
        if (parts.length < 2) {
            return null;
        }
        String subUnit = parts[1].trim();
        if (subUnit.contains("::")) {
            return subUnit.split("::", 2)[0].trim();
        }
        return null;
    }

    private boolean resolveSubgroupDependsOnZ(String groupName, String sgName) {
        if (groupName == null || sgName == null) {
            return false;
        }
        // First check the subgroupDependsOnZ map (for empty subgroups)
        Boolean zVal = this.subgroupDependsOnZ.get(groupName + " // " + sgName);
        if (zVal != null) {
            return zVal;
        }
        // Then check the subgroup model (for subgroups with tiles)
        GroupModel gm = this.groups.get(groupName);
        SubgroupModel sgm = (gm != null) ? gm.getSubgroups().get(sgName) : null;
        if (sgm != null) {
            return sgm.dependsOnZ();
        }
        if (gm != null && gm.dependsOnZ()) {
            return true;
        }
        return false;
    }

    private void syncDependsOnZFromForm() {
        this.currentDependsOnZ = this.resolveSubgroupDependsOnZ(this.formGroup, this.formSubgroup);
    }

    private String rebuildTiledBoxName(String[] parts, String newZ) {
        String group = parts.length > 0 ? parts[0].trim() : "Main";
        String subUnit = parts.length > 1 ? parts[1].trim() : "Tile";
        String xDep = parts.length > 2 ? parts[2].trim() : "t";
        String outline = parts.length > 4 ? parts[4].trim() : "0";
        return group + " // " + subUnit + " // " + xDep + " // " + newZ + " // " + outline;
    }

    private int[] getFineTuneRowXs(int rx) {
        int labelW = this.getStringWidth(StringUtils.translate("schematic_tiling.gui.editor.label.schematic_is"));
        int btnX = rx + labelW + 6;
        int fieldX = btnX + 24 + 4;
        int blocksX = fieldX + 30 + 6;
        return new int[] { btnX, fieldX, blocksX };
    }

    private int drawWrappedError(GuiContext ctx, String err, int x, int y, int maxW, int screenBottom) {
        if (err == null || err.isEmpty() || y > screenBottom) {
            return y;
        }
        int markerW = this.getStringWidth("[!] ");
        List<String> wrapped = this.wrapText(err, Math.max(8, maxW - markerW));
        for (int i = 0; i < wrapped.size(); i++) {
            if (y > screenBottom) {
                break;
            }
            if (i == 0) {
                this.drawString(ctx, "§c[!] " + wrapped.get(i), x, y, 0xFFFF4444);
            } else {
                this.drawString(ctx, "§c" + wrapped.get(i), x + markerW, y, 0xFFFF4444);
            }
            y += 12;
        }
        return y;
    }

    public List<String> wrapText(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String test = current.length() == 0 ? word : current + " " + word;
            if (this.getStringWidth(test) <= maxW) {
                current = new StringBuilder(test);
            } else {
                if (current.length() > 0) {
                    lines.add(current.toString());
                }
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private Integer getLowestBlockY() {
        Integer lowestY = null;
        for (String boxName : this.selection.getAllSubRegionNames()) {
            Box b = this.selection.getSubRegionBox(boxName);
            if (b != null) {
                int minY = Math.min(b.getPos1().getY(), b.getPos2().getY());
                if (lowestY == null || minY < lowestY) {
                    lowestY = minY;
                }
            }
        }
        return lowestY;
    }

    private void showNotification(String msg) {
        this.toastNotification = msg;
        this.toastNotificationTime = System.currentTimeMillis();
    }

    private String getNextUnnamedBoxName() {
        Collection<String> existing = this.selection.getAllSubRegionNames();
        if (!existing.contains("Unnamed")) {
            return "Unnamed";
        }
        int count = 1;
        while (existing.contains("Unnamed(" + count + ")")) {
            count++;
        }
        return "Unnamed(" + count + ")";
    }

    private void createUnnamedBox() {
        // Ensure at least one group exists
        if (this.groups.isEmpty()) {
            this.groups.put("Main", new GroupModel("Main"));
            this.selectedGroupName = "Main";
            this.formGroup = "Main";
        }

        // Preserve all groups before parsing
        Map<String, GroupModel> preservedGroups = new LinkedHashMap<>(this.groups);
        Map<String, Boolean> preservedGroupZ = new LinkedHashMap<>(groupDependsOnZ);
        Map<String, Set<String>> preservedEmptyStructure = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : emptyStructure.entrySet()) {
            preservedEmptyStructure.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }

        BlockPos pos = (this.mc.player != null) ? this.mc.player.blockPosition() : BlockPos.ZERO;
        String desiredName = this.getNextUnnamedBoxName();
        String createdName = this.selection.createNewSubRegionBox(pos, desiredName);
        String actualName = (createdName != null && !createdName.isEmpty()) ? createdName : desiredName;

        this.selection.setSelectedSubRegionBox(actualName);
        this.parseSelectionBoxes();
        
        // Restore all preserved groups
        for (Map.Entry<String, GroupModel> entry : preservedGroups.entrySet()) {
            if (!this.groups.containsKey(entry.getKey())) {
                this.groups.put(entry.getKey(), entry.getValue());
            }
        }
        // Restore Z-dependence maps
        groupDependsOnZ.putAll(preservedGroupZ);
        emptyStructure.clear();
        emptyStructure.putAll(preservedEmptyStructure);
        
        this.openBoxForConfig(actualName);
        this.showNotification(StringUtils.translate("schematic_tiling.gui.editor.message.created_box", actualName));
        this.initGui();
    }

    private String getDefaultTileNameForRole(XDependence role, String boxName) {
        if (role == XDependence.TILE) {
            Box b = this.selection.getSubRegionBox(boxName);
            int width = (b != null) ? Math.abs(b.getSize().getX()) : 8;
            return width + " wide";
        } else if (role == XDependence.CAP) {
            return "Cap";
        } else {
            return "Static";
        }
    }

    private void openBoxForConfig(String boxName) {
        this.commitOpenInputs();

        boolean isSameBox = boxName != null && boxName.equals(sLastEditingBoxName);
        this.editingBoxName = boxName;
        this.selectedSubgroupName = null;
        sLastEditingBoxName = boxName;
        this.selection.setSelectedSubRegionBox(boxName);

        if (boxName.contains(TilingUnit.SEP)) {
            String[] parts = boxName.split(TilingUnit.SEP);
            this.formGroup = parts.length > 0 ? parts[0].trim() : this.selectedGroupName;
            String subgroupId = parts.length > 1 ? parts[1].trim() : "";
            this.currentRole = parts.length > 2 ? XDependence.fromCode(parts[2].trim()) : XDependence.TILE;
            this.selectedGroupName = this.formGroup;
            this.formSubgroup = null;
            String tName = subgroupId;
            if (subgroupId.contains("::")) {
                String[] subParts = subgroupId.split("::", 2);
                this.formSubgroup = subParts[0].trim();
                tName = subParts[1].trim();
            }

            boolean boxZ = false;
            if (parts.length > 3) {
                try { boxZ = Integer.parseInt(parts[3].trim()) != 0; } catch (Exception ignored) {}
            }
            this.currentDependsOnZ = boxZ;

            this.formTileName = tName;
            sLastTileName = tName;
            if (this.inputNameField != null) this.inputNameField.setTextWrapper(tName);
        } else {
            // For unassigned box: preserve the last chosen configuration values
            this.currentRole = sLastRole;
            this.formGroup = sLastFormGroup;
            this.selectedGroupName = sLastFormGroup;

            GroupModel gm = this.groups.get(this.selectedGroupName);
            if (gm != null && !gm.getSubgroups().isEmpty()) {
                if (gm.getSubgroups().containsKey(sLastFormSubgroup)) {
                    this.formSubgroup = sLastFormSubgroup;
                } else {
                    this.formSubgroup = gm.getSubgroups().keySet().iterator().next();
                }
            } else {
                this.formSubgroup = sLastFormSubgroup;
            }
            this.syncDependsOnZFromForm();

            if (isSameBox && sLastTileName != null && !sLastTileName.trim().isEmpty()) {
                this.formTileName = sLastTileName;
            } else if (!isSameBox) {
                this.formTileName = this.getDefaultTileNameForRole(this.currentRole, boxName);
                sLastTileName = this.formTileName;
            } else if (sLastTileName != null && !sLastTileName.trim().isEmpty()) {
                this.formTileName = sLastTileName;
            } else {
                this.formTileName = this.getDefaultTileNameForRole(this.currentRole, boxName);
                sLastTileName = this.formTileName;
            }

            if (this.inputNameField != null) this.inputNameField.setTextWrapper(this.formTileName);
        }
        this.saveSessionState();
        this.isFineTuning = false;
        this.isCustomGroup = false;
        this.isCustomSubgroup = false;
        this.isRenamingGroupRight = false;
        this.isRenamingSubgroupRight = false;
    }

    private void commitOpenInputs() {
        if (this.checkboxNormalizeOriginY != null) {
            this.normalizeOriginY = this.checkboxNormalizeOriginY.isChecked();
        }
        if (this.inputTuneWidth != null) this.tuneWidthText = this.inputTuneWidth.getTextWrapper().trim();
        if (this.inputTuneLength != null) this.tuneLengthText = this.inputTuneLength.getTextWrapper().trim();
        if (this.inputNameField != null) {
            String typed = this.inputNameField.getTextWrapper().trim();
            if (!typed.isEmpty()) {
                this.formTileName = typed;
                sLastTileName = typed;
            }
        }
        if (this.isRenamingGroupRight && this.inputRenameGroupRight != null) {
            String typed = this.inputRenameGroupRight.getTextWrapper().trim();
            if (!typed.isEmpty()) {
                this.formGroup = typed;
            }
        }
        if (this.isRenamingSubgroupRight && this.inputRenameSubgroupRight != null) {
            String typed = this.inputRenameSubgroupRight.getTextWrapper().trim();
            if (!typed.isEmpty()) {
                this.formSubgroup = typed;
            }
        }
        if (this.isCustomGroup && this.inputCustomGroup != null) {
            String typed = this.inputCustomGroup.getTextWrapper().trim();
            if (!typed.isEmpty()) {
                this.groups.computeIfAbsent(typed, GroupModel::new);
                this.formGroup = typed;
                this.selectedGroupName = typed;
            }
        }
        if (this.isCustomSubgroup && this.inputCustomSubgroup != null) {
            String typed = this.inputCustomSubgroup.getTextWrapper().trim();
            if (!typed.isEmpty()) {
                GroupModel currentGm = this.groups.computeIfAbsent(this.formGroup, GroupModel::new);
                currentGm.getSubgroups().computeIfAbsent(typed, SubgroupModel::new);
                this.formSubgroup = typed;
            }
        }
        this.saveSessionState();
    }

    private void saveSessionState() {
        sLastSelectedGroup = this.selectedGroupName;
        sLastSelectedSubgroup = this.selectedSubgroupName;
        sLastFormGroup = this.formGroup;
        sLastFormSubgroup = this.formSubgroup;
        sLastTileName = this.formTileName;
        sLastRole = this.currentRole;
        sLastDependsOnZ = this.currentDependsOnZ;
        sLastEditingBoxName = this.editingBoxName;
        sLastFineTuning = this.isFineTuning;
        sLastWidthIsBigger = this.widthIsBigger;
        sLastLengthIsBigger = this.lengthIsBigger;
        sLastTuneWidth = this.tuneWidthText;
        sLastTuneLength = this.tuneLengthText;
        sLastNormalizeOriginY = this.normalizeOriginY;

        for (GroupModel gm : this.groups.values()) {
            Set<String> emptySgs = this.emptyStructure.computeIfAbsent(gm.getName(), k -> new LinkedHashSet<>());
            for (SubgroupModel sgm : gm.getSubgroups().values()) {
                if (sgm.getTiles().isEmpty()) {
                    emptySgs.add(sgm.getName());
                } else {
                    emptySgs.remove(sgm.getName());
                }
                this.subgroupDependsOnZ.put(gm.getName() + " // " + sgm.getName(), sgm.dependsOnZ());
            }
            this.groupDependsOnZ.put(gm.getName(), gm.dependsOnZ());
        }

        String key = this.getSelectionKey();
        Map<String, Set<String>> copyEmpty = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : this.emptyStructure.entrySet()) {
            copyEmpty.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
        }
        sEmptyStructureBySelection.put(key, copyEmpty);
        sGroupDependsOnZBySelection.put(key, new LinkedHashMap<>(this.groupDependsOnZ));
        sSubgroupDependsOnZBySelection.put(key, new LinkedHashMap<>(this.subgroupDependsOnZ));

        if (this.selection != null) {
            sEmptyStructureByRef.put(this.selection, copyEmpty);
            sGroupDependsOnZByRef.put(this.selection, new LinkedHashMap<>(this.groupDependsOnZ));
            sSubgroupDependsOnZByRef.put(this.selection, new LinkedHashMap<>(this.subgroupDependsOnZ));
        }
    }

    @Override
    protected void closeGui(boolean showParent) {
        this.commitOpenInputs();
        this.saveSessionState();
        super.closeGui(showParent);
    }

    @Override
    public void removed() {
        this.commitOpenInputs();
        this.saveSessionState();
        super.removed();
    }

    @Override
    public void initGui() {
        this.commitOpenInputs();
        super.initGui();
        this.clearButtons();
        this.clearTextFields();
        this.clearWidgets();

        int screenW = this.getScreenWidth();
        int screenH = this.getScreenHeight();

        int leftW = Math.max(260, (screenW - 30) * 52 / 100);
        int rightX = 15 + leftW + 10;
        int rightW = screenW - rightX - 15;
        int leftRightEdge = 10 + leftW;

        int y = 46;

        // --- 1. GROUPS LIST ---
        for (String gName : new ArrayList<>(this.groups.keySet())) {
            boolean isGroupActive = gName.equals(this.selectedGroupName);
            boolean isGroupSettingsOpen = isGroupActive && this.editingBoxName == null && this.selectedSubgroupName == null;
            int btnDelX = leftRightEdge - 22;

            GroupModel currentGm = this.groups.get(gName);
            boolean isGZ = currentGm != null && currentGm.dependsOnZ();
            boolean gHasError = false;
            List<String> allGroupErrors = new ArrayList<>();
            if (currentGm != null) {
                for (SubgroupModel sm : currentGm.getSubgroups().values()) {
                    List<String> sErrs = this.getSubgroupErrors(sm);
                    if (!sErrs.isEmpty()) {
                        gHasError = true;
                        for (String e : sErrs) {
                            String p = sm.getName().equals("(None)") ? "" : sm.getName() + ": ";
                            if (!allGroupErrors.contains(p + e)) allGroupErrors.add(p + e);
                        }
                    }
                }
            }

            int rowW = btnDelX - 18 - 4;

            ButtonGeneric delGBtn = new ButtonGeneric(btnDelX, y, 18, 16, "X");
            delGBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.delete_group", gName));
            this.addButton(delGBtn, (btn, mBtn) -> {
                if (!GuiBase.isShiftDown()) {
                    InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, StringUtils.translate("schematic_tiling.gui.editor.warning.shift_delete_group", gName));
                    return;
                }
                this.deleteGroup(gName);
            });

            String gLabel = (isGroupActive ? "▶ " : "  ") + (gHasError ? "! " : "") + gName + (isGZ ? " [Z]" : "");
            ClickableRowButton gRowBtn;
            if (gHasError && !isGroupSettingsOpen) {
                gRowBtn = new ClickableRowButton(
                        18, y, rowW, 16, gLabel, false,
                        0xFFFF5555, 0xFFFF8888,
                        0xFFFF5555, 0xFFFF8888,
                        0x25FF5555
                );
                List<String> hovers = new ArrayList<>();
                for (String err : allGroupErrors) {
                    hovers.add("§c[!] " + err);
                }
                hovers.add(StringUtils.translate("schematic_tiling.gui.editor.hover.group_settings_click", gName));
                gRowBtn.setHoverStrings(String.join("\n", hovers));
            } else {
                gRowBtn = new ClickableRowButton(
                        18, y, rowW, 16, gLabel, isGroupSettingsOpen,
                        isGroupActive ? 0xFFFFFFFF : 0xFFAAAAAA, 0xFFFFFFFF,
                        0xFF55FF55, 0xFF88FF88,
                        0x2255FF55
                );
                gRowBtn.setHoverStrings(isGroupSettingsOpen ?
                        StringUtils.translate("schematic_tiling.gui.editor.hover.group_settings_open") :
                        StringUtils.translate("schematic_tiling.gui.editor.hover.group_settings_click", gName));
            }
            this.addButton(gRowBtn, (btn, mBtn) -> {
                this.selectedGroupName = gName;
                this.selectedSubgroupName = null;
                this.editingBoxName = null;
                this.selection.setSelectedSubRegionBox(null);
                this.formGroup = gName;
                this.saveSessionState();
                this.initGui();
            });

            y += 18;
        }

        ButtonGeneric addGBtn = new ButtonGeneric(18, y, 95, 16, StringUtils.translate("schematic_tiling.gui.editor.button.new_group"));
        addGBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.new_group"));
        this.addButton(addGBtn, (btn, mBtn) -> {
            String newGName = "Group_" + (this.groups.size() + 1);
            this.groups.put(newGName, new GroupModel(newGName));
            this.emptyStructure.computeIfAbsent(newGName, k -> new LinkedHashSet<>());
            this.selectedGroupName = newGName;
            this.selectedSubgroupName = null;
            this.editingBoxName = null;
            this.selection.setSelectedSubRegionBox(null);
            this.formGroup = newGName;
            this.saveSessionState();
            this.initGui();
        });

        y += 24;
        this.yLeftTreeHeader = y;
        y += 18;

        // --- 2. SUBGROUPS & TILES TREE ---
        GroupModel activeGroup = this.groups.get(this.selectedGroupName);
        if (activeGroup != null) {
            this.yLeftSubgroupsHeader = y;
            int addSgW = 105;
            ButtonGeneric addSGBtn = new ButtonGeneric(leftRightEdge - addSgW, y, addSgW, 16, StringUtils.translate("schematic_tiling.gui.editor.button.new_subgroup"));
            addSGBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.new_subgroup", this.selectedGroupName));
            this.addButton(addSGBtn, (btn, mBtn) -> {
                String newSG = "Subgroup_" + (activeGroup.getSubgroups().size() + 1);
                SubgroupModel newSgModel = new SubgroupModel(newSG);
                newSgModel.setDependsOnZ(activeGroup.dependsOnZ());
                activeGroup.getSubgroups().put(newSG, newSgModel);
                if (activeGroup.dependsOnZ()) {
                    subgroupDependsOnZ.put(this.selectedGroupName + " // " + newSG, true);
                }
                // Track as empty subgroup
                emptyStructure.computeIfAbsent(this.selectedGroupName, k -> new LinkedHashSet<>()).add(newSG);
                this.selectedSubgroupName = newSG;
                this.formSubgroup = newSG;
                this.editingBoxName = null;
                this.selection.setSelectedSubRegionBox(null);
                this.saveSessionState();
                this.initGui();
            });
            y += 18;

            List<String> namedSgs = new ArrayList<>(activeGroup.getSubgroups().keySet());
            if (namedSgs.isEmpty()) {
                y += 16;
            } else {
                for (String sgName : namedSgs) {
                    SubgroupModel sg = activeGroup.getSubgroups().computeIfAbsent(sgName, SubgroupModel::new);

                    int btnDelX = leftRightEdge - 22;
                    int btnAddTileX = leftRightEdge - 44;

                    ButtonGeneric addTileBtn = new ButtonGeneric(btnAddTileX, y, 18, 16, "+");
                    addTileBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.add_tile_to_subgroup", sgName));
                    this.addButton(addTileBtn, (btn, mBtn) -> {
                        if (!this.unassignedBoxes.isEmpty()) {
                            this.openBoxForConfig(this.unassignedBoxes.get(0));
                            this.formGroup = this.selectedGroupName;
                            this.formSubgroup = sgName;
                            this.syncDependsOnZFromForm();
                            this.selectedSubgroupName = null;
                            this.saveSessionState();
                            this.initGui();
                        } else {
                            // Preserve all groups before parsing
                            Map<String, GroupModel> preservedGroups = new LinkedHashMap<>(this.groups);
                            Map<String, Boolean> preservedGroupZ = new LinkedHashMap<>(groupDependsOnZ);
                            Map<String, Set<String>> preservedEmptyStructure = new LinkedHashMap<>();
                            for (Map.Entry<String, Set<String>> entry : emptyStructure.entrySet()) {
                                preservedEmptyStructure.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
                            }

                            BlockPos pos = (this.mc.player != null) ? this.mc.player.blockPosition() : BlockPos.ZERO;
                            String desiredName = this.getNextUnnamedBoxName();
                            String createdName = this.selection.createNewSubRegionBox(pos, desiredName);
                            String actualName = (createdName != null && !createdName.isEmpty()) ? createdName : desiredName;

                            this.selection.setSelectedSubRegionBox(actualName);
                            this.parseSelectionBoxes();
                            
                            // Restore all preserved groups
                            for (Map.Entry<String, GroupModel> entry : preservedGroups.entrySet()) {
                                if (!this.groups.containsKey(entry.getKey())) {
                                    this.groups.put(entry.getKey(), entry.getValue());
                                }
                            }
                            // Restore Z-dependence maps
                            groupDependsOnZ.putAll(preservedGroupZ);
                            emptyStructure.clear();
                            emptyStructure.putAll(preservedEmptyStructure);
                            
                            this.openBoxForConfig(actualName);
                            this.formGroup = this.selectedGroupName;
                            this.formSubgroup = sgName;
                            this.syncDependsOnZFromForm();
                            this.selectedSubgroupName = null;
                            
                            // Remove from emptyStructure since it will now have a tile
                            Set<String> groupEmptySgs = emptyStructure.get(this.selectedGroupName);
                            if (groupEmptySgs != null) {
                                groupEmptySgs.remove(sgName);
                            }
                            
                            this.showNotification(StringUtils.translate("schematic_tiling.gui.editor.message.created_box", actualName));
                            this.saveSessionState();
                            this.initGui();
                        }
                    });

                    ButtonGeneric delSGBtn = new ButtonGeneric(btnDelX, y, 18, 16, "X");
                    delSGBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.delete_subgroup", sgName));
                    this.addButton(delSGBtn, (btn, mBtn) -> {
                        if (!GuiBase.isShiftDown()) {
                            InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, StringUtils.translate("schematic_tiling.gui.editor.warning.shift_delete_subgroup", sgName));
                            return;
                        }
                        this.deleteSubgroup(this.selectedGroupName, sgName);
                    });

                    List<String> sgErrors = this.getSubgroupErrors(sg);
                    boolean sgHasError = !sgErrors.isEmpty();

                    int rowW = btnAddTileX - 18 - 4;
                    boolean isSGSel = sgName != null && sgName.equals(this.selectedSubgroupName) && this.editingBoxName == null;
                    boolean isParentOfEditing = this.editingBoxName != null &&
                            (sg.containsBox(this.editingBoxName) || (sgName != null && sgName.equals(this.formSubgroup)));

                    boolean isSgZ = sg.dependsOnZ();
                    String sgLabel = (isSGSel ? "▶ " : "▼ ") + (sgHasError ? "! " : "") + sg.getName() + (isSgZ ? " [Z]" : "") + " (" + sg.getTiles().size() + ")";

                    ClickableRowButton sgRowBtn;
                    if (isSGSel) {
                        // Subgroup explicitly selected for renaming on right pane: Gold / Amber glow
                        sgRowBtn = new ClickableRowButton(
                                18, y, rowW, 16, sgLabel, true,
                                0xFFFFCC00, 0xFFFFEE77,
                                0xFFFFCC00, 0xFFFFEE77,
                                0x25FFCC00
                        );
                    } else if (sgHasError) {
                        // Subgroup with inconsistency error: Red text
                        sgRowBtn = new ClickableRowButton(
                                18, y, rowW, 16, sgLabel, false,
                                0xFFFF5555, 0xFFFF8888,
                                0xFFFF5555, 0xFFFF8888,
                                0x18FF0000
                        );
                    } else if (isParentOfEditing) {
                        // Contains actively edited tile: soft Aqua / Cyan indicator, no amber/green glow
                        sgRowBtn = new ClickableRowButton(
                                18, y, rowW, 16, sgLabel, false,
                                0xFF55FFFF, 0xFFAAFFff,
                                0xFF55FFFF, 0xFFAAFFff,
                                0
                        );
                    } else {
                        // Inactive, unselected subgroup: Muted slate text, NO glowing
                        sgRowBtn = new ClickableRowButton(
                                18, y, rowW, 16, sgLabel, false,
                                0xFF90A4AE, 0xFFDDF0FF,
                                0xFF90A4AE, 0xFFDDF0FF,
                                0
                        );
                    }

                    List<String> sgHoverList = new ArrayList<>();
                    if (sgHasError) {
                        for (String err : sgErrors) {
                            sgHoverList.add("§c[!] " + err);
                        }
                    }
                    if (isSGSel) {
                        sgHoverList.add(StringUtils.translate("schematic_tiling.gui.editor.hover.subgroup_selected"));
                    } else if (isParentOfEditing) {
                        sgHoverList.add(StringUtils.translate("schematic_tiling.gui.editor.hover.subgroup_contains_editing"));
                    } else {
                        sgHoverList.add(StringUtils.translate("schematic_tiling.gui.editor.hover.subgroup_click_rename", sgName));
                    }
                    sgRowBtn.setHoverStrings(sgHoverList.toArray(String[]::new));

                    this.addButton(sgRowBtn, (btn, mBtn) -> {
                        this.selectedSubgroupName = sgName;
                        this.formSubgroup = sgName;
                        this.editingBoxName = null;
                        this.selection.setSelectedSubRegionBox(null);
                        this.saveSessionState();
                        this.initGui();
                    });

                    y += 18;

                    // Tiles list in Subgroup
                    for (TileModel tm : new ArrayList<>(sg.getTiles())) {
                        boolean isEditingThis = tm.getAssignedBoxName().equals(this.editingBoxName);
                        int tBtnDelX = leftRightEdge - 22;

                        List<String> errors = this.getTileErrors(sg, tm);
                        boolean hasError = !errors.isEmpty();

                        ButtonGeneric delTBtn = new ButtonGeneric(tBtnDelX, y, 18, 15, "X");
                        delTBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.delete_tile_box"));
                        this.addButton(delTBtn, (btn, mBtn) -> {
                            if (!GuiBase.isShiftDown()) {
                                InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, StringUtils.translate("schematic_tiling.gui.editor.warning.shift_delete_tile_box", tm.getName()));
                                return;
                            }
                            // Check if subgroup will become empty after deletion
                            String groupName = this.selectedGroupName;
                            String subgroupName = sg.getName();
                            boolean willBecomeEmpty = sg.getTiles().size() == 1;
                            
                            // Remove tile from model
                            sg.getTiles().remove(tm);
                            
                            this.selection.removeSubRegionBox(tm.getAssignedBoxName());
                            
                            // If subgroup will become empty, track it in emptyStructure
                            if (willBecomeEmpty && groupName != null && subgroupName != null) {
                                emptyStructure.computeIfAbsent(groupName, k -> new LinkedHashSet<>()).add(subgroupName);
                            }
                            
                            // Don't call parseSelectionBoxes - we've already manually updated the model and selection
                            if (tm.getAssignedBoxName().equals(this.editingBoxName)) {
                                this.editingBoxName = null;
                            }
                            this.saveSessionState();
                            this.initGui();
                        });

                        int tileRowW = tBtnDelX - 24 - 4;
                        String roleStr = tm.getXDep() == XDependence.CAP ? StringUtils.translate("schematic_tiling.gui.editor.role_tag.cap") : (tm.getXDep() == XDependence.TILE ? StringUtils.translate("schematic_tiling.gui.editor.role_tag.tile") : StringUtils.translate("schematic_tiling.gui.editor.role_tag.static"));
                        String tileLabel = (hasError ? "! " : "") + (isEditingThis ? "▶ " : "• ") + tm.getName() + " (" + roleStr + ")";

                        ClickableRowButton tileRowBtn;
                        if (hasError) {
                            tileRowBtn = new ClickableRowButton(
                                    24, y, tileRowW, 15, tileLabel, isEditingThis,
                                    0xFFFF4444, 0xFFFF7777,
                                    0xFFFF4444, 0xFFFF7777,
                                    isEditingThis ? 0x35FF2222 : 0x18FF0000
                            );
                        } else {
                            tileRowBtn = new ClickableRowButton(
                                    24, y, tileRowW, 15, tileLabel, isEditingThis,
                                    0xFFCCCCCC, 0xFFFFFFFF,
                                    0xFF55FF55, 0xFF88FF88,
                                    0x2555FF55
                            );
                        }

                        List<String> hoverList = new ArrayList<>();
                        if (hasError) {
                            for (String err : errors) {
                                hoverList.add("§c[!] " + err);
                            }
                        }
                        hoverList.add(isEditingThis ? StringUtils.translate("schematic_tiling.gui.editor.hover.editing_tile", tm.getName()) : StringUtils.translate("schematic_tiling.gui.editor.hover.edit_tile", tm.getName()));
                        tileRowBtn.setHoverStrings(hoverList.toArray(String[]::new));
                        this.addButton(tileRowBtn, (btn, mBtn) -> {
                            this.openBoxForConfig(tm.getAssignedBoxName());
                            this.initGui();
                        });

                        y += 17;
                    }
                }
            }
        }

        // --- 3. UNASSIGNED BOXES SECTION ---
        y += 6;
        this.yLeftUnassignedHeader = y;

        int btnCreateX = this.unassignedBoxes.isEmpty() ? (leftRightEdge - 76) : (leftRightEdge - 124);
        ButtonGeneric createBoxBtn = new ButtonGeneric(btnCreateX, y - 2, 72, 16, StringUtils.translate("schematic_tiling.gui.editor.button.create_box"));
        createBoxBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.create_box"));
        this.addButton(createBoxBtn, (btn, mBtn) -> {
            this.createUnnamedBox();
        });

        if (!this.unassignedBoxes.isEmpty()) {
            ButtonGeneric clearAllBtn = new ButtonGeneric(leftRightEdge - 48, y - 2, 44, 16, StringUtils.translate("schematic_tiling.gui.editor.button.clear"));
            clearAllBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.clear_unassigned"));
            this.addButton(clearAllBtn, (btn, mBtn) -> {
                if (!GuiBase.isShiftDown()) {
                    InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, StringUtils.translate("schematic_tiling.gui.editor.warning.shift_clear_unassigned"));
                    return;
                }
                for (String uName : new ArrayList<>(this.unassignedBoxes)) {
                    this.selection.removeSubRegionBox(uName);
                }
                this.unassignedBoxes.clear();
                if (this.unassignedBoxes.contains(this.editingBoxName)) {
                    this.editingBoxName = null;
                }
                this.showNotification(StringUtils.translate("schematic_tiling.gui.editor.message.deleted_all_unassigned"));
                this.initGui();
            });
        }

        y += 18;

        if (this.unassignedBoxes.isEmpty()) {
            y += 14;
        } else {
            for (String boxName : new ArrayList<>(this.unassignedBoxes)) {
                boolean isEditingThis = boxName.equals(this.editingBoxName);
                int btnDelX = leftRightEdge - 22;

                ButtonGeneric delBtn = new ButtonGeneric(btnDelX, y, 18, 16, "X");
                delBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.delete_box"));
                this.addButton(delBtn, (btn, mBtn) -> {
                    if (!GuiBase.isShiftDown()) {
                        InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, StringUtils.translate("schematic_tiling.gui.editor.warning.shift_delete_box", boxName));
                        return;
                    }
                    this.selection.removeSubRegionBox(boxName);
                    if (boxName.equals(this.editingBoxName)) {
                        this.editingBoxName = null;
                    }
                    this.parseSelectionBoxes();
                    this.showNotification(StringUtils.translate("schematic_tiling.gui.editor.message.deleted_box", boxName));
                    this.initGui();
                });

                Box b = this.selection.getSubRegionBox(boxName);
                String dims = "";
                if (b != null) {
                    dims = " (" + Math.abs(b.getSize().getX()) + "x" + Math.abs(b.getSize().getZ()) + ")";
                }
                int rowW = btnDelX - 18 - 4;
                String boxLabel = (isEditingThis ? "▶ " : "• ") + boxName + dims;

                ClickableRowButton boxRowBtn = new ClickableRowButton(
                        18, y, rowW, 16, boxLabel, isEditingThis,
                        0xFFCCCCCC, 0xFFFFFFFF,
                        0xFF55FF55, 0xFF88FF88,
                        0x2555FF55
                );
                boxRowBtn.setHoverStrings(isEditingThis ? StringUtils.translate("schematic_tiling.gui.editor.hover.selected_box_config", boxName) : StringUtils.translate("schematic_tiling.gui.editor.hover.configure_box", boxName));
                this.addButton(boxRowBtn, (btn, mBtn) -> {
                    this.openBoxForConfig(boxName);
                    this.initGui();
                });

                y += 18;
            }
        }

        // --- RIGHT PANE ---
        if (this.isFineTuning) {
            this.initRightPaneFineTuning(rightX, rightW);
        } else if (this.editingBoxName != null) {
            this.initRightPaneBoxConfig(rightX, rightW);
        } else if (this.selectedSubgroupName != null) {
            this.initRightPaneSubgroupSettings(rightX, rightW);
        } else {
            this.initRightPaneGroupSettings(rightX, rightW);
        }

        // Bottom Navigation & Done Buttons
        int doneW = 80;
        int doneX = screenW - doneW - 15;
        int doneY = screenH - 26;

        ButtonGeneric tabEditorBtn = new ButtonGeneric(10, doneY, 90, 20, StringUtils.translate("schematic_tiling.gui.editor.button.box_editor"));
        tabEditorBtn.setEnabled(this.isFineTuning);
        this.addButton(tabEditorBtn, (btn, mBtn) -> {
            this.isFineTuning = false;
            this.saveSessionState();
            this.initGui();
        });

        ButtonGeneric tabTuneBtn = new ButtonGeneric(105, doneY, 110, 20, StringUtils.translate("schematic_tiling.gui.editor.button.fine_tune_origin"));
        tabTuneBtn.setEnabled(!this.isFineTuning);
        this.addButton(tabTuneBtn, (btn, mBtn) -> {
            this.isFineTuning = true;
            this.saveSessionState();
            this.initGui();
        });

        ButtonGeneric doneBtn = new ButtonGeneric(doneX, doneY, doneW, 20, StringUtils.translate("schematic_tiling.gui.editor.button.done"));
        this.addButton(doneBtn, (btn, mBtn) -> this.closeGui(true));
    }

    private void initRightPaneGroupSettings(int rx, int rw) {
        GroupModel gm = this.groups.get(this.selectedGroupName);
        if (gm == null) return;

        // Group Name field at y = 74
        int tfW = rw - 74;
        this.inputGroupNameField = new GuiTextFieldGeneric(rx, 74, tfW, 18, this.font);
        this.inputGroupNameField.setTextWrapper(this.selectedGroupName);
        this.addTextField(this.inputGroupNameField, null);

        ButtonGeneric renBtn = new ButtonGeneric(rx + tfW + 4, 74, 60, 18, StringUtils.translate("schematic_tiling.gui.editor.button.rename"));
        renBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.rename_group"));
        this.addButton(renBtn, (btn, mBtn) -> {
            String newG = this.inputGroupNameField.getTextWrapper().trim();
            if (!newG.isEmpty() && !newG.equals(this.selectedGroupName)) {
                this.renameGroup(this.selectedGroupName, newG);
                this.selectedGroupName = newG;
                this.formGroup = newG;
                this.saveSessionState();
                this.initGui();
            }
        });

        // Group Depends on Z (Length) checkbox at y = 104
        boolean gz = gm.dependsOnZ();
        this.checkboxDependsOnZ = new WidgetCheckBox(
                rx, 104,
                Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED,
                StringUtils.translate("schematic_tiling.gui.editor.checkbox.group_depends_on_z")
        );
        this.checkboxDependsOnZ.setChecked(gz, false);
        this.checkboxDependsOnZ.setListener(cb -> {
            this.setGroupDependsOnZ(this.selectedGroupName, cb.isChecked());
        });
        this.addWidget(this.checkboxDependsOnZ);

        // Buttons row at y = 136
        ButtonGeneric newSGBtn = new ButtonGeneric(rx, 136, 105, 18, StringUtils.translate("schematic_tiling.gui.editor.button.new_subgroup"));
        newSGBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.new_subgroup", this.selectedGroupName));
        this.addButton(newSGBtn, (btn, mBtn) -> {
            String newSG = "Subgroup_" + (gm.getSubgroups().size() + 1);
            SubgroupModel newSgModel = new SubgroupModel(newSG);
            newSgModel.setDependsOnZ(gm.dependsOnZ());
            gm.getSubgroups().put(newSG, newSgModel);
            if (gm.dependsOnZ()) {
                subgroupDependsOnZ.put(this.selectedGroupName + " // " + newSG, true);
            }
            // Track as empty subgroup
            emptyStructure.computeIfAbsent(this.selectedGroupName, k -> new LinkedHashSet<>()).add(newSG);
            this.selectedSubgroupName = newSG;
            this.formSubgroup = newSG;
            this.saveSessionState();
            this.initGui();
        });

        // Delete group button at y = 168
        ButtonGeneric delBtn = new ButtonGeneric(rx, 168, 95, 18, StringUtils.translate("schematic_tiling.gui.editor.button.delete_group"));
        delBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.delete_group_untile", this.selectedGroupName));
        this.addButton(delBtn, (btn, mBtn) -> {
            if (!GuiBase.isShiftDown()) {
                InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, StringUtils.translate("schematic_tiling.gui.editor.warning.shift_delete_group", this.selectedGroupName));
                return;
            }
            this.deleteGroup(this.selectedGroupName);
        });
    }

    private void initRightPaneSubgroupSettings(int rx, int rw) {
        GroupModel gm = this.groups.get(this.selectedGroupName);
        if (gm == null || this.selectedSubgroupName == null) return;
        SubgroupModel sg = gm.getSubgroups().get(this.selectedSubgroupName);
        if (sg == null) return;

        // Subgroup Name field at y = 74
        int tfW = rw - 74;
        this.inputSubgroupNameField = new GuiTextFieldGeneric(rx, 74, tfW, 18, this.font);
        this.inputSubgroupNameField.setTextWrapper(this.selectedSubgroupName);
        this.addTextField(this.inputSubgroupNameField, null);

        ButtonGeneric renBtn = new ButtonGeneric(rx + tfW + 4, 74, 60, 18, StringUtils.translate("schematic_tiling.gui.editor.button.rename"));
        renBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.rename_subgroup"));
        renBtn.setEnabled(true);
        this.addButton(renBtn, (btn, mBtn) -> {
            String newSG = this.inputSubgroupNameField.getTextWrapper().trim();
            if (!newSG.isEmpty() && !newSG.equals(this.selectedSubgroupName)) {
                this.renameSubgroup(this.selectedGroupName, this.selectedSubgroupName, newSG);
                this.selectedSubgroupName = newSG;
                this.formSubgroup = newSG;
                this.saveSessionState();
                this.initGui();
            }
        });

        // Subgroup Depends on Z (Length) checkbox at y = 104
        boolean sgZ = sg.dependsOnZ();
        WidgetCheckBox checkboxSubgroupZ = new WidgetCheckBox(
                rx, 104,
                Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED,
                StringUtils.translate("schematic_tiling.gui.editor.checkbox.subgroup_depends_on_z")
        );
        checkboxSubgroupZ.setChecked(sgZ, false);
        checkboxSubgroupZ.setListener(cb -> {
            this.setSubgroupDependsOnZ(this.selectedGroupName, this.selectedSubgroupName, cb.isChecked());
            this.initGui();
        });
        this.addWidget(checkboxSubgroupZ);

        // Buttons row at y = 136
        ButtonGeneric addTileBtn = new ButtonGeneric(rx, 136, 95, 18, StringUtils.translate("schematic_tiling.gui.editor.button.add_tile"));
        addTileBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.tile_box_into_subgroup", this.selectedSubgroupName));
        this.addButton(addTileBtn, (btn, mBtn) -> {
            if (this.selectedSubgroupName == null) {
                this.showNotification(StringUtils.translate("schematic_tiling.gui.editor.message.select_subgroup_first"));
                return;
            }
            String targetSg = this.selectedSubgroupName;
            String targetGroup = this.selectedGroupName != null ? this.selectedGroupName : "Main";
            if (!this.unassignedBoxes.isEmpty()) {
                this.openBoxForConfig(this.unassignedBoxes.get(0));
                this.formGroup = targetGroup;
                this.formSubgroup = targetSg;
                this.syncDependsOnZFromForm();
                this.selectedSubgroupName = null;
                this.saveSessionState();
                this.initGui();
            } else {
                // Preserve all groups before parsing
                Map<String, GroupModel> preservedGroups = new LinkedHashMap<>(this.groups);
                Map<String, Boolean> preservedGroupZ = new LinkedHashMap<>(groupDependsOnZ);
                Map<String, Set<String>> preservedEmptyStructure = new LinkedHashMap<>();
                for (Map.Entry<String, Set<String>> entry : emptyStructure.entrySet()) {
                    preservedEmptyStructure.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
                }

                BlockPos pos = (this.mc.player != null) ? this.mc.player.blockPosition() : BlockPos.ZERO;
                String desiredName = this.getNextUnnamedBoxName();
                String createdName = this.selection.createNewSubRegionBox(pos, desiredName);
                String actualName = (createdName != null && !createdName.isEmpty()) ? createdName : desiredName;

                this.selection.setSelectedSubRegionBox(actualName);
                this.parseSelectionBoxes();
                
                // Restore all preserved groups
                for (Map.Entry<String, GroupModel> entry : preservedGroups.entrySet()) {
                    if (!this.groups.containsKey(entry.getKey())) {
                        this.groups.put(entry.getKey(), entry.getValue());
                    }
                }
                // Restore Z-dependence maps
                groupDependsOnZ.putAll(preservedGroupZ);
                emptyStructure.clear();
                emptyStructure.putAll(preservedEmptyStructure);
                
                this.openBoxForConfig(actualName);
                this.formGroup = targetGroup;
                this.formSubgroup = targetSg;
                this.syncDependsOnZFromForm();
                this.selectedSubgroupName = null;
                
                // Remove from emptyStructure since it will now have a tile
                Set<String> groupEmptySgs = emptyStructure.get(targetGroup);
                if (groupEmptySgs != null) {
                    groupEmptySgs.remove(targetSg);
                }
                
                this.showNotification(StringUtils.translate("schematic_tiling.gui.editor.message.created_box", actualName));
                this.saveSessionState();
                this.initGui();
            }
        });

        ButtonGeneric delSGBtn = new ButtonGeneric(rx + 102, 136, 110, 18, StringUtils.translate("schematic_tiling.gui.editor.button.delete_subgroup"));
        delSGBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.delete_subgroup_untile"));
        delSGBtn.setEnabled(true);
        this.addButton(delSGBtn, (btn, mBtn) -> {
            if (!GuiBase.isShiftDown()) {
                InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, StringUtils.translate("schematic_tiling.gui.editor.warning.shift_delete_subgroup", this.selectedSubgroupName));
                return;
            }
            this.deleteSubgroup(this.selectedGroupName, this.selectedSubgroupName);
        });
    }

    private void initRightPaneBoxConfig(int rx, int rw) {
        // 1. Group Selector / Inputs at y = 74
        int ryGroup = 74;
        List<String> groupNames = new ArrayList<>(this.groups.keySet());
        if (this.isRenamingGroupRight) {
            this.inputRenameGroupRight = new GuiTextFieldGeneric(rx, ryGroup, rw - 55, 18, this.font);
            this.inputRenameGroupRight.setTextWrapper(this.formGroup);
            this.addTextField(this.inputRenameGroupRight, null);
            this.inputRenameGroupRight.setFocusedWrapper(true);

            ButtonGeneric okGBtn = new ButtonGeneric(rx + rw - 50, ryGroup, 24, 18, StringUtils.translate("schematic_tiling.gui.editor.button.ok"));
            this.addButton(okGBtn, (btn, mBtn) -> {
                String newG = this.inputRenameGroupRight.getTextWrapper().trim();
                if (!newG.isEmpty()) {
                    this.renameGroup(this.formGroup, newG);
                }
                this.isRenamingGroupRight = false;
                this.initGui();
            });

            ButtonGeneric cancelGBtn = new ButtonGeneric(rx + rw - 24, ryGroup, 20, 18, "X");
            this.addButton(cancelGBtn, (btn, mBtn) -> {
                this.isRenamingGroupRight = false;
                this.saveSessionState();
                this.initGui();
            });
        } else if (this.isCustomGroup) {
            this.inputCustomGroup = new GuiTextFieldGeneric(rx, ryGroup, rw - 55, 18, this.font);
            this.inputCustomGroup.setTextWrapper(this.formGroup);
            this.addTextField(this.inputCustomGroup, null);
            this.inputCustomGroup.setFocusedWrapper(true);
            this.inputCustomGroup.setResponder(val -> {
                String trimmed = val.trim();
                if (!trimmed.isEmpty()) {
                    this.formGroup = trimmed;
                    this.selectedGroupName = trimmed;
                    this.groups.computeIfAbsent(trimmed, GroupModel::new);
                }
            });

            ButtonGeneric okGBtn = new ButtonGeneric(rx + rw - 50, ryGroup, 24, 18, StringUtils.translate("schematic_tiling.gui.editor.button.ok"));
            this.addButton(okGBtn, (btn, mBtn) -> {
                String newG = this.inputCustomGroup.getTextWrapper().trim();
                if (!newG.isEmpty()) {
                    this.groups.computeIfAbsent(newG, GroupModel::new);
                    this.emptyStructure.computeIfAbsent(newG, k -> new LinkedHashSet<>());
                    this.formGroup = newG;
                    this.selectedGroupName = newG;
                    // Reset subgroup when creating/selecting new group
                    this.formSubgroup = null;
                    this.isCustomSubgroup = false;
                }
                this.isCustomGroup = false;
                this.saveSessionState();
                this.initGui();
            });

            ButtonGeneric cancelGBtn = new ButtonGeneric(rx + rw - 24, ryGroup, 20, 18, "X");
            this.addButton(cancelGBtn, (btn, mBtn) -> {
                this.isCustomGroup = false;
                this.initGui();
            });
        } else {
            int cycleBtnW = rw - 94;
            this.buttonGroupSelect = new ButtonGeneric(rx, ryGroup, cycleBtnW, 18, StringUtils.translate("schematic_tiling.gui.editor.button.group_label", this.formGroup));
            this.buttonGroupSelect.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.cycle_group"));
            this.addButton(this.buttonGroupSelect, (btn, mBtn) -> {
                int idx = groupNames.indexOf(this.formGroup);
                int next = (idx + 1) % groupNames.size();
                this.formGroup = groupNames.get(next);
                // Reset subgroup when switching groups
                this.formSubgroup = null;
                this.isCustomSubgroup = false;
                this.syncDependsOnZFromForm();
                this.buttonGroupSelect.setDisplayString(StringUtils.translate("schematic_tiling.gui.editor.button.group_label", this.formGroup));
                this.saveSessionState();
                this.initGui();
            });

            ButtonGeneric renGBtn = new ButtonGeneric(rx + rw - 90, ryGroup, 46, 18, StringUtils.translate("schematic_tiling.gui.editor.button.rename"));
            renGBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.rename_group"));
            this.addButton(renGBtn, (btn, mBtn) -> {
                this.isRenamingGroupRight = true;
                this.initGui();
            });

            ButtonGeneric newGBtn = new ButtonGeneric(rx + rw - 40, ryGroup, 36, 18, StringUtils.translate("schematic_tiling.gui.editor.button.new"));
            newGBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.new_group"));
            this.addButton(newGBtn, (btn, mBtn) -> {
                this.isCustomGroup = true;
                String newG = "Group_" + (this.groups.size() + 1);
                this.groups.computeIfAbsent(newG, GroupModel::new);
                this.emptyStructure.computeIfAbsent(newG, k -> new LinkedHashSet<>());
                this.formGroup = newG;
                this.selectedGroupName = newG;
                this.saveSessionState();
                this.initGui();
            });
        }

        // 2. Subgroup Selector / Inputs at y = 110
        int rySubgroup = 110;
        GroupModel gm = this.groups.get(this.formGroup);
        List<String> sgNames = new ArrayList<>();
        if (gm != null) {
            for (String s : gm.getSubgroups().keySet()) {
                if (!sgNames.contains(s)) {
                    sgNames.add(s);
                }
            }
        }

        if (this.isRenamingSubgroupRight) {
            this.inputRenameSubgroupRight = new GuiTextFieldGeneric(rx, rySubgroup, rw - 55, 18, this.font);
            this.inputRenameSubgroupRight.setTextWrapper(this.formSubgroup);
            this.addTextField(this.inputRenameSubgroupRight, null);
            this.inputRenameSubgroupRight.setFocusedWrapper(true);

            ButtonGeneric okSGBtn = new ButtonGeneric(rx + rw - 50, rySubgroup, 24, 18, StringUtils.translate("schematic_tiling.gui.editor.button.ok"));
            this.addButton(okSGBtn, (btn, mBtn) -> {
                String newSG = this.inputRenameSubgroupRight.getTextWrapper().trim();
                if (!newSG.isEmpty()) {
                    this.renameSubgroup(this.formGroup, this.formSubgroup, newSG);
                }
                this.isRenamingSubgroupRight = false;
                this.initGui();
            });

            ButtonGeneric cancelSGBtn = new ButtonGeneric(rx + rw - 24, rySubgroup, 20, 18, "X");
            this.addButton(cancelSGBtn, (btn, mBtn) -> {
                this.isRenamingSubgroupRight = false;
                this.saveSessionState();
                this.initGui();
            });
        } else if (this.isCustomSubgroup) {
            this.inputCustomSubgroup = new GuiTextFieldGeneric(rx, rySubgroup, rw - 55, 18, this.font);
            this.inputCustomSubgroup.setTextWrapper(this.formSubgroup);
            this.addTextField(this.inputCustomSubgroup, null);
            this.inputCustomSubgroup.setFocusedWrapper(true);
            this.inputCustomSubgroup.setResponder(val -> {
                String trimmed = val.trim();
                if (!trimmed.isEmpty()) {
                    this.formSubgroup = trimmed;
                    GroupModel currentGm = this.groups.computeIfAbsent(this.formGroup, GroupModel::new);
                    currentGm.getSubgroups().computeIfAbsent(trimmed, SubgroupModel::new);
                }
            });

            ButtonGeneric okSGBtn = new ButtonGeneric(rx + rw - 50, rySubgroup, 24, 18, StringUtils.translate("schematic_tiling.gui.editor.button.ok"));
            this.addButton(okSGBtn, (btn, mBtn) -> {
                String newSG = this.inputCustomSubgroup.getTextWrapper().trim();
                if (!newSG.isEmpty()) {
                    GroupModel currentGm = this.groups.computeIfAbsent(this.formGroup, GroupModel::new);
                    SubgroupModel sg = currentGm.getSubgroups().computeIfAbsent(newSG, SubgroupModel::new);
                    sg.setDependsOnZ(currentGm.dependsOnZ());
                    if (currentGm.dependsOnZ()) {
                        subgroupDependsOnZ.put(this.formGroup + " // " + newSG, true);
                    }
                    // Track as empty subgroup
                    emptyStructure.computeIfAbsent(this.formGroup, k -> new LinkedHashSet<>()).add(newSG);
                    this.formSubgroup = newSG;
                    this.selectedGroupName = this.formGroup;
                }
                this.isCustomSubgroup = false;
                this.saveSessionState();
                this.initGui();
            });

            ButtonGeneric cancelSGBtn = new ButtonGeneric(rx + rw - 24, rySubgroup, 20, 18, "X");
            this.addButton(cancelSGBtn, (btn, mBtn) -> {
                this.isCustomSubgroup = false;
                this.initGui();
            });
        } else {
            // Only auto-select first subgroup if subgroups exist and current selection is invalid
            if (!sgNames.isEmpty() && (this.formSubgroup == null || !sgNames.contains(this.formSubgroup))) {
                this.formSubgroup = sgNames.get(0);
                this.syncDependsOnZFromForm();
            } else if (sgNames.isEmpty()) {
                // No subgroups exist, clear formSubgroup
                this.formSubgroup = null;
            }
            int cycleBtnW = rw - 94;

            ButtonGeneric renSGBtn = new ButtonGeneric(rx + rw - 90, rySubgroup, 46, 18, StringUtils.translate("schematic_tiling.gui.editor.button.rename_compact"));
            renSGBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.rename_subgroup"));
            renSGBtn.setEnabled(this.formSubgroup != null);
            this.addButton(renSGBtn, (btn, mBtn) -> {
                this.isRenamingSubgroupRight = true;
                this.initGui();
            });

            ButtonGeneric newSGBtn = new ButtonGeneric(rx + rw - 40, rySubgroup, 36, 18, StringUtils.translate("schematic_tiling.gui.editor.button.new"));
            newSGBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.assign_new_subgroup"));
            this.addButton(newSGBtn, (btn, mBtn) -> {
                this.isCustomSubgroup = true;
                int sgCount = (gm != null) ? gm.getSubgroups().size() + 1 : 1;
                String newSG = "Subgroup_" + sgCount;
                this.formSubgroup = newSG;
                GroupModel currentGm = this.groups.computeIfAbsent(this.formGroup, GroupModel::new);
                SubgroupModel newSgModel = currentGm.getSubgroups().computeIfAbsent(newSG, SubgroupModel::new);
                newSgModel.setDependsOnZ(currentGm.dependsOnZ());
                if (currentGm.dependsOnZ()) {
                    subgroupDependsOnZ.put(this.formGroup + " // " + newSG, true);
                }
                this.saveSessionState();
                this.initGui();
            });

            this.buttonSubgroupSelect = new ButtonGeneric(rx, rySubgroup, cycleBtnW, 18, StringUtils.translate("schematic_tiling.gui.editor.button.subgroup_label", this.formSubgroup != null ? this.formSubgroup : "(None)"));
            this.buttonSubgroupSelect.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.cycle_subgroup"));
            this.buttonSubgroupSelect.setEnabled(!sgNames.isEmpty());
            this.addButton(this.buttonSubgroupSelect, (btn, mBtn) -> {
                if (sgNames.isEmpty()) return;
                int idx = sgNames.indexOf(this.formSubgroup);
                int next = (idx + 1) % sgNames.size();
                this.formSubgroup = sgNames.get(next);
                this.syncDependsOnZFromForm();
                this.buttonSubgroupSelect.setDisplayString(StringUtils.translate("schematic_tiling.gui.editor.button.subgroup_label", this.formSubgroup));
                this.saveSessionState();
                this.initGui();
            });
        }

        // 3. Tile Name field at y = 146
        int ryTile = 146;
        String curTileName = this.formTileName;
        if (curTileName == null || curTileName.trim().isEmpty()) {
            if (this.editingBoxName != null && this.editingBoxName.contains(TilingUnit.SEP)) {
                String[] parts = this.editingBoxName.split(TilingUnit.SEP);
                if (parts.length > 1) {
                    String sub = parts[1].trim();
                    if (sub.contains("::")) {
                        curTileName = sub.split("::", 2)[1].trim();
                    } else {
                        curTileName = sub;
                    }
                }
            } else if (sLastTileName != null && !sLastTileName.trim().isEmpty()) {
                curTileName = sLastTileName;
            } else {
                curTileName = this.getDefaultTileNameForRole(this.currentRole, this.editingBoxName);
            }
            this.formTileName = curTileName;
            sLastTileName = curTileName;
        }

        this.inputNameField = new GuiTextFieldGeneric(rx, ryTile, rw - 10, 18, this.font);
        this.inputNameField.setTextWrapper(this.formTileName);
        this.inputNameField.setResponder(val -> {
            this.formTileName = val;
            sLastTileName = val;
        });
        this.addTextField(this.inputNameField, tf -> {
            String val = tf.getTextWrapper();
            this.formTileName = val;
            sLastTileName = val;
            return false;
        });

        // 4. Role cycle button at y = 172
        int ryRole = 172;
        String roleLabel = StringUtils.translate("schematic_tiling.gui.editor.button.role_label", this.getRoleName(this.currentRole));
        this.buttonRole = new ButtonGeneric(rx, ryRole, rw - 10, 18, roleLabel);
        this.addButton(this.buttonRole, (btn, mBtn) -> {
            this.cycleRole();
            this.buttonRole.setDisplayString(StringUtils.translate("schematic_tiling.gui.editor.button.role_label", this.getRoleName(this.currentRole)));
        });

        // 5. Save and Cancel Buttons at y = 206
        int ryActions = 206;
        ButtonGeneric saveBtn = new ButtonGeneric(rx, ryActions, 70, 20, StringUtils.translate("schematic_tiling.gui.editor.button.save"));
        saveBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.save_box"));
        // Disable save if no subgroup is selected or subgroup doesn't exist in current group
        boolean subgroupExists = gm != null && this.formSubgroup != null && gm.getSubgroups().containsKey(this.formSubgroup);
        saveBtn.setEnabled(subgroupExists);
        this.addButton(saveBtn, (btn, mBtn) -> {
            this.applyTilingToCurrentBox();
        });

        ButtonGeneric cancelBtn = new ButtonGeneric(rx + 76, ryActions, 60, 20, StringUtils.translate("schematic_tiling.gui.editor.button.cancel"));
        cancelBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.cancel_box"));
        this.addButton(cancelBtn, (btn, mBtn) -> {
            this.editingBoxName = null;
            this.saveSessionState();
            this.initGui();
        });
    }

    private void initRightPaneFineTuning(int rx, int rw) {
        if (this.inputTuneWidth != null) this.tuneWidthText = this.inputTuneWidth.getTextWrapper().trim();
        if (this.inputTuneLength != null) this.tuneLengthText = this.inputTuneLength.getTextWrapper().trim();

        int[] tuneXs = this.getFineTuneRowXs(rx);

        // Width row at y = 78
        this.buttonWidthComp = new ButtonGeneric(tuneXs[0], 78, 24, 18, this.widthIsBigger ? ">" : "<");
        this.buttonWidthComp.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.comp_width"));
        this.addButton(this.buttonWidthComp, (btn, mBtn) -> {
            this.widthIsBigger = !this.widthIsBigger;
            this.saveSessionState();
            this.initGui();
        });

        this.inputTuneWidth = new GuiTextFieldGeneric(tuneXs[1], 78, 30, 18, this.font);
        this.inputTuneWidth.setTextWrapper(this.tuneWidthText);
        this.addTextField(this.inputTuneWidth, null);

        // Length row at y = 122
        this.buttonLengthComp = new ButtonGeneric(tuneXs[0], 122, 24, 18, this.lengthIsBigger ? ">" : "<");
        this.buttonLengthComp.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.comp_length"));
        this.addButton(this.buttonLengthComp, (btn, mBtn) -> {
            this.lengthIsBigger = !this.lengthIsBigger;
            this.saveSessionState();
            this.initGui();
        });

        this.inputTuneLength = new GuiTextFieldGeneric(tuneXs[1], 122, 30, 18, this.font);
        this.inputTuneLength.setTextWrapper(this.tuneLengthText);
        this.addTextField(this.inputTuneLength, null);

        // Checkbox after preview text (preview occupies ~148-178)
        this.checkboxNormalizeOriginY = new WidgetCheckBox(
                rx, 192,
                Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED,
                StringUtils.translate("schematic_tiling.gui.editor.checkbox.normalize_origin_y")
        );
        this.checkboxNormalizeOriginY.setChecked(this.normalizeOriginY, false);
        this.checkboxNormalizeOriginY.setListener(cb -> {
            this.normalizeOriginY = cb.isChecked();
            this.saveSessionState();
        });
        this.addWidget(this.checkboxNormalizeOriginY);

        // Actions at y = 214
        ButtonGeneric applyTuneBtn = new ButtonGeneric(rx, 214, 100, 20, StringUtils.translate("schematic_tiling.gui.editor.button.apply_origin"));
        applyTuneBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.apply_origin"));
        this.addButton(applyTuneBtn, (btn, mBtn) -> {
            this.applyFineTuning();
        });

        ButtonGeneric resetOrigBtn = new ButtonGeneric(rx + 106, 214, 110, 20, StringUtils.translate("schematic_tiling.gui.editor.button.reset_auto"));
        resetOrigBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.editor.hover.reset_auto"));
        this.addButton(resetOrigBtn, (btn, mBtn) -> {
            this.resetOrigin();
        });
    }

    private void applyFineTuning() {
        if (this.inputTuneWidth != null) this.tuneWidthText = this.inputTuneWidth.getTextWrapper().trim();
        if (this.inputTuneLength != null) this.tuneLengthText = this.inputTuneLength.getTextWrapper().trim();

        int nWidth = this.parseNonNegativeInt(this.tuneWidthText);
        int nLength = this.parseNonNegativeInt(this.tuneLengthText);

        if (nWidth == 0 && nLength == 0 && !this.normalizeOriginY) {
            this.statusMessage = StringUtils.translate("schematic_tiling.gui.editor.message.fine_tune_zero");
            this.statusIsError = true;
            return;
        }

        BlockPos current = this.selection.getExplicitOrigin();
        if (current == null) {
            current = this.selection.getEffectiveOrigin();
        }

        // Rule from docs:
        // Wider than intended (>) -> Move origin towards negative X (-N)
        // Smaller than intended (<) -> Move origin towards positive X (+N)
        // Length bigger (>) -> Move origin towards negative Z (-N)
        // Length smaller (<) -> Move origin towards positive Z (+N)
        int dx = this.widthIsBigger ? -nWidth : +nWidth;
        int dz = this.lengthIsBigger ? -nLength : +nLength;

        Integer lowestY = this.normalizeOriginY ? this.getLowestBlockY() : null;
        int newY = (lowestY != null) ? lowestY : current.getY();

        BlockPos newOrigin = new BlockPos(current.getX() + dx, newY, current.getZ() + dz);
        this.selection.setExplicitOrigin(newOrigin);
        this.selection.setOriginSelected(true);

        this.showNotification(StringUtils.translate("schematic_tiling.gui.editor.message.origin_adjusted",
                dx, dz, newOrigin.getX(), newOrigin.getY(), newOrigin.getZ()));

        this.tuneWidthText = "0";
        this.tuneLengthText = "0";
        this.initGui();
    }

    private void resetOrigin() {
        this.selection.setExplicitOrigin(null);
        this.showNotification(StringUtils.translate("schematic_tiling.gui.editor.message.origin_reset"));
        this.initGui();
    }

    private int parseNonNegativeInt(String text) {
        if (text == null) return 0;
        try {
            int val = Integer.parseInt(text.trim());
            return Math.max(0, val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int getTileWidth(TileModel tm) {
        if (tm == null || tm.getAssignedBoxName() == null) return 0;
        Box b = this.selection.getSubRegionBox(tm.getAssignedBoxName());
        return (b != null) ? Math.abs(b.getSize().getX()) : 0;
    }

    public List<String> getTileErrors(SubgroupModel sg, TileModel tm) {
        List<String> errors = new ArrayList<>();
        if (tm == null) return errors;

        int myWidth = this.getTileWidth(tm);
        if (myWidth <= 0) {
            errors.add(StringUtils.translate("schematic_tiling.gui.editor.error.tile_zero_width"));
        }

        if (sg != null) {
            List<TileModel> sgTiles = new ArrayList<>(sg.getTiles());

            // 1. Ambiguous tile width among modular tiles
            if (tm.getXDep() == XDependence.TILE && myWidth > 0) {
                for (TileModel other : sgTiles) {
                    if (!other.getAssignedBoxName().equals(tm.getAssignedBoxName()) && other.getXDep() == XDependence.TILE) {
                        int otherW = this.getTileWidth(other);
                        if (myWidth == otherW) {
                            errors.add(StringUtils.translate("schematic_tiling.gui.editor.error.same_width_conflict", myWidth, other.getName()));
                        }
                    }
                }
            }

            // 2. Multiple caps
            if (tm.getXDep() == XDependence.CAP) {
                List<String> otherCapNames = new ArrayList<>();
                for (TileModel other : sgTiles) {
                    if (!other.getAssignedBoxName().equals(tm.getAssignedBoxName()) && other.getXDep() == XDependence.CAP) {
                        otherCapNames.add(other.getName());
                    }
                }
                if (!otherCapNames.isEmpty()) {
                    errors.add(StringUtils.translate("schematic_tiling.gui.editor.error.multiple_caps_conflict", String.join(", ", otherCapNames)));
                }

                // 3. Cap without modular tiles
                boolean hasModularTiles = sgTiles.stream().anyMatch(t -> !t.getAssignedBoxName().equals(tm.getAssignedBoxName()) && t.getXDep() == XDependence.TILE);
                if (!hasModularTiles) {
                    errors.add(StringUtils.translate("schematic_tiling.gui.editor.error.cap_without_tiles"));
                }
            }

            // 4. Mixed Z-dependence among saved tiles in this subgroup.
            // The tile being configured inherits the subgroup Z flag on save, so do not
            // compare a stale per-box Z against siblings.
            if (this.hasMixedZDependence(sgTiles)) {
                errors.add(StringUtils.translate("schematic_tiling.gui.editor.error.mixed_z_dependence"));
            }
        }

        return errors;
    }

    public List<String> getSubgroupErrors(SubgroupModel sg) {
        List<String> errors = new ArrayList<>();
        if (sg == null || sg.getTiles().isEmpty()) return errors;

        List<TileModel> sgTiles = new ArrayList<>(sg.getTiles());

        // Multiple caps
        List<TileModel> caps = sgTiles.stream().filter(t -> t.getXDep() == XDependence.CAP).toList();
        if (caps.size() > 1) {
            List<String> names = caps.stream().map(TileModel::getName).toList();
            errors.add(StringUtils.translate("schematic_tiling.gui.editor.error.sg_multiple_caps", caps.size(), String.join(", ", names)));
        }

        // Cap without modular tiles
        long tileCount = sgTiles.stream().filter(t -> t.getXDep() == XDependence.TILE).count();
        if (!caps.isEmpty() && tileCount == 0) {
            errors.add(StringUtils.translate("schematic_tiling.gui.editor.error.cap_without_tiles"));
        }

        // Duplicate widths among modular tiles
        Map<Integer, List<String>> widthMap = new LinkedHashMap<>();
        for (TileModel tm : sgTiles) {
            if (tm.getXDep() == XDependence.TILE) {
                int w = this.getTileWidth(tm);
                if (w > 0) {
                    widthMap.computeIfAbsent(w, k -> new ArrayList<>()).add(tm.getName());
                }
            }
        }
        for (Map.Entry<Integer, List<String>> entry : widthMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                errors.add(StringUtils.translate("schematic_tiling.gui.editor.error.sg_duplicate_widths", entry.getKey(), String.join(", ", entry.getValue())));
            }
        }

        if (this.hasMixedZDependence(sgTiles)) {
            errors.add(StringUtils.translate("schematic_tiling.gui.editor.error.mixed_z_dependence"));
        }

        return errors;
    }

    private boolean hasMixedZDependence(List<TileModel> tiles) {
        if (tiles == null || tiles.size() < 2) {
            return false;
        }
        boolean first = tiles.get(0).dependsOnZ();
        for (int i = 1; i < tiles.size(); i++) {
            if (tiles.get(i).dependsOnZ() != first) {
                return true;
            }
        }
        return false;
    }

    private String getRoleName(XDependence dep) {
        return switch (dep) {
            case TILE -> StringUtils.translate("schematic_tiling.gui.editor.role.tile");
            case CAP -> StringUtils.translate("schematic_tiling.gui.editor.role.cap");
            default -> StringUtils.translate("schematic_tiling.gui.editor.role.static");
        };
    }

    private void cycleRole() {
        if (this.currentRole == XDependence.TILE) {
            this.currentRole = XDependence.CAP;
            if (this.inputNameField != null && (this.inputNameField.getTextWrapper().contains("wide") || this.inputNameField.getTextWrapper().equals("Tile"))) {
                this.inputNameField.setTextWrapper("Cap");
                this.formTileName = "Cap";
                sLastTileName = "Cap";
            }
        } else if (this.currentRole == XDependence.CAP) {
            this.currentRole = XDependence.NONE;
            if (this.inputNameField != null && (this.inputNameField.getTextWrapper().equals("Cap") || this.inputNameField.getTextWrapper().contains("wide"))) {
                this.inputNameField.setTextWrapper("Static");
                this.formTileName = "Static";
                sLastTileName = "Static";
            }
        } else {
            this.currentRole = XDependence.TILE;
            if (this.inputNameField != null && (this.inputNameField.getTextWrapper().equals("Static") || this.inputNameField.getTextWrapper().equals("Cap"))) {
                String defName = this.getDefaultTileNameForRole(XDependence.TILE, this.editingBoxName);
                this.inputNameField.setTextWrapper(defName);
                this.formTileName = defName;
                sLastTileName = this.formTileName;
            }
        }
        this.saveSessionState();
    }

    private void applyTilingToCurrentBox() {
        if (this.editingBoxName == null) {
            this.statusMessage = StringUtils.translate("schematic_tiling.gui.editor.error.no_box_selected");
            this.statusIsError = true;
            return;
        }

        Box targetBox = this.selection.getSubRegionBox(this.editingBoxName);
        if (targetBox == null) {
            this.statusMessage = StringUtils.translate("schematic_tiling.gui.editor.error.box_not_exist", this.editingBoxName);
            this.statusIsError = true;
            return;
        }

        String group = this.isCustomGroup && this.inputCustomGroup != null ?
                this.inputCustomGroup.getTextWrapper().trim() : this.formGroup;
        String subgroup = this.isCustomSubgroup && this.inputCustomSubgroup != null ?
                this.inputCustomSubgroup.getTextWrapper().trim() : this.formSubgroup;
        String tileName = (this.inputNameField != null) ? this.inputNameField.getTextWrapper().trim() :
                (this.formTileName != null ? this.formTileName : "Tile");

        if (group == null || group.isEmpty()) {
            this.groups.put("Main", new GroupModel("Main"));
            group = "Main";
        }
        if (subgroup != null && subgroup.isEmpty()) subgroup = null;
        if (tileName.isEmpty()) tileName = (this.formTileName != null && !this.formTileName.isEmpty()) ? this.formTileName : "Tile";

        // Check if subgroup exists in the group - if not, return without saving
        if (subgroup == null || subgroup.isEmpty()) {
            return;
        }
        GroupModel gm = this.groups.get(group);
        if (gm == null || !gm.getSubgroups().containsKey(subgroup)) {
            return;
        }

        if (this.currentRole == XDependence.CAP) {
            GroupModel targetGm = this.groups.get(group);
            if (targetGm != null) {
                SubgroupModel targetSg = targetGm.getSubgroups().get(subgroup);
                if (targetSg != null) {
                    for (TileModel tm : targetSg.getTiles()) {
                        if (tm.getXDep() == XDependence.CAP && !tm.getAssignedBoxName().equals(this.editingBoxName)) {
                            String err = StringUtils.translate("schematic_tiling.gui.editor.error.cap_already_exists", subgroup, tm.getName());
                            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, err);
                            return;
                        }
                    }
                }
            }
        } else if (this.currentRole == XDependence.TILE) {
            GroupModel targetGm = this.groups.get(group);
            if (targetGm != null) {
                SubgroupModel targetSg = targetGm.getSubgroups().get(subgroup);
                if (targetSg != null) {
                    int myWidth = (targetBox != null) ? Math.abs(targetBox.getSize().getX()) : 0;
                    for (TileModel tm : targetSg.getTiles()) {
                        if (tm.getXDep() == XDependence.TILE && !tm.getAssignedBoxName().equals(this.editingBoxName)) {
                            int otherW = this.getTileWidth(tm);
                            if (myWidth == otherW && myWidth > 0) {
                                String err = StringUtils.translate("schematic_tiling.gui.editor.error.same_width_conflict", myWidth, tm.getName());
                                InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, err);
                                return;
                            }
                        }
                    }
                }
            }
        }

        boolean subDependsOnZ = this.resolveSubgroupDependsOnZ(group, subgroup);
        this.currentDependsOnZ = subDependsOnZ;

        String generatedName = formatSubregionName(group, subgroup, tileName, this.currentRole, subDependsOnZ);

        if (!this.editingBoxName.equals(generatedName)) {
            boolean renamed = this.selection.renameSubRegionBox(this.editingBoxName, generatedName);
            if (!renamed) {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.editor.error.box_rename_exists", generatedName);
                this.statusIsError = true;
                return;
            }
        }

        this.selection.setSelectedSubRegionBox(generatedName);
        this.showNotification(StringUtils.translate("schematic_tiling.gui.editor.message.saved_box", generatedName));

        // Explicitly register group and subgroup in our model
        SubgroupModel sg = gm.getSubgroups().get(subgroup);
        
        // Remove from emptyStructure since it will now have a tile
        Set<String> groupEmptySgs = emptyStructure.get(group);
        if (groupEmptySgs != null) {
            groupEmptySgs.remove(subgroup);
        }

        // Remove old box from model if it exists
        for (GroupModel g : this.groups.values()) {
            for (SubgroupModel s : g.getSubgroups().values()) {
                s.getTiles().removeIf(tm -> tm.getAssignedBoxName().equals(this.editingBoxName));
            }
        }

        // Add new tile to model
        TileModel newTm = new TileModel(tileName, generatedName);
        newTm.setXDep(this.currentRole);
        newTm.setDependsOnZ(this.currentDependsOnZ);
        sg.getTiles().add(newTm);
        sg.setDependsOnZ(this.currentDependsOnZ);
        this.subgroupDependsOnZ.put(group + " // " + subgroup, this.currentDependsOnZ);

        boolean allZ = !gm.getSubgroups().isEmpty();
        for (SubgroupModel s : gm.getSubgroups().values()) {
            if (!s.dependsOnZ()) {
                allZ = false;
                break;
            }
        }
        gm.setDependsOnZ(allZ);
        this.groupDependsOnZ.put(group, allZ);

        this.formGroup = group;
        this.formSubgroup = subgroup;
        this.formTileName = tileName;
        sLastTileName = tileName;
        this.selectedGroupName = group;
        this.isCustomGroup = false;
        this.isCustomSubgroup = false;

        this.editingBoxName = null;
        sLastEditingBoxName = null;
        this.saveSessionState();
        this.initGui();
    }

    private void setGroupDependsOnZ(String groupName, boolean dependsOnZ) {
        if (groupName == null || groupName.isEmpty()) return;

        GroupModel gm = this.groups.computeIfAbsent(groupName, GroupModel::new);
        gm.setDependsOnZ(dependsOnZ);
        this.groupDependsOnZ.put(groupName, dependsOnZ);
        for (String sgName : gm.getSubgroups().keySet()) {
            this.subgroupDependsOnZ.put(groupName + " // " + sgName, dependsOnZ);
        }
        for (SubgroupModel sg : gm.getSubgroups().values()) {
            sg.setDependsOnZ(dependsOnZ);
            for (TileModel tm : sg.getTiles()) {
                tm.setDependsOnZ(dependsOnZ);
            }
        }
        if (groupName.equals(this.formGroup)) {
            this.currentDependsOnZ = dependsOnZ;
        }

        String newZ = dependsOnZ ? "1" : "0";
        List<String> allBoxes = new ArrayList<>(this.selection.getAllSubRegionNames());
        for (String boxName : allBoxes) {
            if (boxName.contains(TilingUnit.SEP)) {
                String[] parts = boxName.split(TilingUnit.SEP);
                if (parts.length >= 1 && parts[0].trim().equals(groupName)) {
                    String newBoxName = this.rebuildTiledBoxName(parts, newZ);
                    if (!boxName.equals(newBoxName)) {
                        this.selection.renameSubRegionBox(boxName, newBoxName);
                        if (boxName.equals(this.editingBoxName)) {
                            this.editingBoxName = newBoxName;
                            sLastEditingBoxName = newBoxName;
                        }
                    }
                }
            }
        }

        // Don't call parseSelectionBoxes - we've already manually updated the model and selection
        this.saveSessionState();
        this.initGui();
    }

    private void setSubgroupDependsOnZ(String groupName, String sgName, boolean dependsOnZ) {
        if (groupName == null || groupName.isEmpty() || sgName == null || sgName.isEmpty()) return;

        GroupModel gm = this.groups.computeIfAbsent(groupName, GroupModel::new);
        SubgroupModel sg = gm.getSubgroups().computeIfAbsent(sgName, SubgroupModel::new);
        sg.setDependsOnZ(dependsOnZ);
        for (TileModel tm : sg.getTiles()) {
            tm.setDependsOnZ(dependsOnZ);
        }
        this.subgroupDependsOnZ.put(groupName + " // " + sgName, dependsOnZ);

        boolean allZ = !gm.getSubgroups().isEmpty();
        for (SubgroupModel s : gm.getSubgroups().values()) {
            if (!s.dependsOnZ()) {
                allZ = false;
                break;
            }
        }
        gm.setDependsOnZ(allZ);
        this.groupDependsOnZ.put(groupName, allZ);

        if (groupName.equals(this.formGroup) && sgName.equals(this.formSubgroup)) {
            this.currentDependsOnZ = dependsOnZ;
        }

        String newZ = dependsOnZ ? "1" : "0";
        List<String> allBoxes = new ArrayList<>(this.selection.getAllSubRegionNames());
        for (String boxName : allBoxes) {
            if (boxName.contains(TilingUnit.SEP)) {
                String[] parts = boxName.split(TilingUnit.SEP);
                if (parts.length >= 1 && parts[0].trim().equals(groupName) && sgName.equals(this.getBoxSubgroupName(boxName))) {
                    String newBoxName = this.rebuildTiledBoxName(parts, newZ);
                    if (!boxName.equals(newBoxName)) {
                        this.selection.renameSubRegionBox(boxName, newBoxName);
                        if (boxName.equals(this.editingBoxName)) {
                            this.editingBoxName = newBoxName;
                            sLastEditingBoxName = newBoxName;
                        }
                    }
                }
            }
        }

        // Don't call parseSelectionBoxes - we've already manually updated the model and selection
        this.saveSessionState();
    }

    private void renameGroup(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) return;
        if (this.groups.containsKey(newName)) {
            this.statusMessage = StringUtils.translate("schematic_tiling.gui.editor.error.group_exists", newName);
            this.statusIsError = true;
            return;
        }

        List<String> allBoxes = new ArrayList<>(this.selection.getAllSubRegionNames());
        for (String boxName : allBoxes) {
            if (boxName.contains(TilingUnit.SEP)) {
                String[] parts = boxName.split(TilingUnit.SEP);
                if (parts.length >= 1 && parts[0].trim().equals(oldName)) {
                    StringBuilder newBoxName = new StringBuilder(newName);
                    for (int i = 1; i < parts.length; i++) {
                        newBoxName.append(" // ").append(parts[i].trim());
                    }
                    this.selection.renameSubRegionBox(boxName, newBoxName.toString());
                    if (boxName.equals(this.editingBoxName)) {
                        this.editingBoxName = newBoxName.toString();
                    }
                }
            }
        }

        // Remove old group from map (if it exists)
        GroupModel gm = this.groups.remove(oldName);
        
        // If group was empty (only tracked in emptyStructure), create it
        if (gm == null) {
            gm = new GroupModel(newName);
        } else {
            gm.setName(newName);
        }
        this.groups.put(newName, gm);

        Boolean z = groupDependsOnZ.remove(oldName);
        if (z != null) {
            groupDependsOnZ.put(newName, z);
        }
        Map<String, Boolean> migratedSgZ = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : new ArrayList<>(subgroupDependsOnZ.entrySet())) {
            if (entry.getKey().startsWith(oldName + " // ")) {
                subgroupDependsOnZ.remove(entry.getKey());
                migratedSgZ.put(newName + entry.getKey().substring(oldName.length()), entry.getValue());
            }
        }
        subgroupDependsOnZ.putAll(migratedSgZ);

        // Update emptyStructure for empty groups
        Set<String> emptySgs = emptyStructure.remove(oldName);
        if (emptySgs != null) {
            emptyStructure.put(newName, emptySgs);
        }

        if (oldName.equals(this.selectedGroupName)) {
            this.selectedGroupName = newName;
        }
        if (oldName.equals(this.formGroup)) {
            this.formGroup = newName;
        }

        // Don't call parseSelectionBoxes for empty groups - they're already in the model
        // Only parse if the group has tiles in the selection
        boolean hasTilesInSelection = false;
        for (String boxName : this.selection.getAllSubRegionNames()) {
            if (boxName.contains(TilingUnit.SEP)) {
                String[] parts = boxName.split(TilingUnit.SEP);
                if (parts.length >= 1 && parts[0].trim().equals(newName)) {
                    hasTilesInSelection = true;
                    break;
                }
            }
        }
        
        if (hasTilesInSelection) {
            this.parseSelectionBoxes();
        }
        
        this.saveSessionState();
        this.showNotification(StringUtils.translate("schematic_tiling.gui.editor.message.renamed_group", newName));
        this.initGui();
    }

    private void renameSubgroup(String groupName, String oldSG, String newSG) {
        if (oldSG == null || newSG == null || oldSG.equals(newSG)) return;
        GroupModel gm = this.groups.get(groupName);
        if (gm == null) return;

        if (gm.getSubgroups().containsKey(newSG)) {
            this.statusMessage = StringUtils.translate("schematic_tiling.gui.editor.error.subgroup_exists", newSG, groupName);
            this.statusIsError = true;
            return;
        }

        List<String> allBoxes = new ArrayList<>(this.selection.getAllSubRegionNames());
        for (String boxName : allBoxes) {
            if (boxName.contains(TilingUnit.SEP)) {
                String[] parts = boxName.split(TilingUnit.SEP);
                if (parts.length >= 2 && parts[0].trim().equals(groupName)) {
                    String subgroupId = parts[1].trim();
                    String newSubgroupId = null;
                    if (subgroupId.equals(oldSG)) {
                        newSubgroupId = newSG;
                    } else if (subgroupId.startsWith(oldSG + "::")) {
                        String unitName = subgroupId.substring((oldSG + "::").length());
                        newSubgroupId = newSG + "::" + unitName;
                    }

                    if (newSubgroupId != null) {
                        StringBuilder newBoxName = new StringBuilder(parts[0].trim() + " // " + newSubgroupId);
                        for (int i = 2; i < parts.length; i++) {
                            newBoxName.append(" // ").append(parts[i].trim());
                        }
                        this.selection.renameSubRegionBox(boxName, newBoxName.toString());
                        if (boxName.equals(this.editingBoxName)) {
                            this.editingBoxName = newBoxName.toString();
                        }
                    }
                }
            }
        }

        SubgroupModel sg = gm.getSubgroups().remove(oldSG);
        if (sg != null) {
            sg.setName(newSG);
            gm.getSubgroups().put(newSG, sg);
        } else {
            // Subgroup might be empty (no tiles), create it
            sg = new SubgroupModel(newSG);
            gm.getSubgroups().put(newSG, sg);
        }

        Boolean sgZVal = subgroupDependsOnZ.remove(groupName + " // " + oldSG);
        if (sgZVal != null) {
            subgroupDependsOnZ.put(groupName + " // " + newSG, sgZVal);
        }

        // Update emptyStructure for empty subgroups
        Set<String> groupEmptySgs = emptyStructure.get(groupName);
        if (groupEmptySgs != null && groupEmptySgs.contains(oldSG)) {
            groupEmptySgs.remove(oldSG);
            groupEmptySgs.add(newSG);
        }

        if (oldSG.equals(this.formSubgroup)) {
            this.formSubgroup = newSG;
        }

        // Don't call parseSelectionBoxes for empty subgroups - they're already in the model
        // Only parse if the subgroup has tiles in the selection
        boolean hasTilesInSelection = false;
        for (String boxName : this.selection.getAllSubRegionNames()) {
            if (boxName.contains(TilingUnit.SEP)) {
                String[] parts = boxName.split(TilingUnit.SEP);
                if (parts.length >= 2 && parts[0].trim().equals(groupName)) {
                    String subgroupId = parts[1].trim();
                    String sgName = null;
                    if (subgroupId.contains("::")) {
                        sgName = subgroupId.split("::", 2)[0].trim();
                    }
                    if (sgName != null && sgName.equals(newSG)) {
                        hasTilesInSelection = true;
                        break;
                    }
                }
            }
        }
        
        if (hasTilesInSelection) {
            this.parseSelectionBoxes();
        }
        
        this.saveSessionState();
        this.showNotification(StringUtils.translate("schematic_tiling.gui.editor.message.renamed_subgroup", newSG));
        this.initGui();
    }

    private void deleteGroup(String gName) {
        GroupModel gm = this.groups.get(gName);
        if (gm != null) {
            for (SubgroupModel sg : gm.getSubgroups().values()) {
                for (TileModel tm : new ArrayList<>(sg.getTiles())) {
                    this.selection.removeSubRegionBox(tm.getAssignedBoxName());
                    if (tm.getAssignedBoxName().equals(this.editingBoxName)) {
                        this.editingBoxName = null;
                    }
                }
                sg.getTiles().clear();
            }
            gm.getSubgroups().clear();
        }
        this.groups.remove(gName);
        emptyStructure.remove(gName);
        groupDependsOnZ.remove(gName);
        for (Map.Entry<String, Boolean> entry : new ArrayList<>(subgroupDependsOnZ.entrySet())) {
            if (entry.getKey().startsWith(gName + " // ")) {
                subgroupDependsOnZ.remove(entry.getKey());
            }
        }

        if (gName.equals(this.selectedGroupName)) {
            this.selectedGroupName = this.groups.isEmpty() ? null : this.groups.keySet().iterator().next();
        }
        if (gName.equals(this.formGroup)) {
            this.formGroup = this.selectedGroupName;
        }
        // Don't call parseSelectionBoxes - we've already manually updated the model and selection
        this.saveSessionState();
        this.initGui();
    }

    private void deleteSubgroup(String groupName, String sgName) {
        if (groupName == null || sgName == null) return;
        GroupModel gm = this.groups.get(groupName);
        if (gm != null) {
            SubgroupModel sg = gm.getSubgroups().get(sgName);
            if (sg != null) {
                for (TileModel tm : new ArrayList<>(sg.getTiles())) {
                    this.selection.removeSubRegionBox(tm.getAssignedBoxName());
                    if (tm.getAssignedBoxName().equals(this.editingBoxName)) {
                        this.editingBoxName = null;
                    }
                }
                sg.getTiles().clear();
            }
            gm.getSubgroups().remove(sgName);
        }
        this.subgroupDependsOnZ.remove(groupName + " // " + sgName);
        // Remove from emptyStructure
        Set<String> groupEmptySgs = this.emptyStructure.get(groupName);
        if (groupEmptySgs != null) {
            groupEmptySgs.remove(sgName);
        }
        if (sgName.equals(this.selectedSubgroupName)) {
            this.selectedSubgroupName = null;
        }
        if (sgName.equals(this.formSubgroup)) {
            this.formSubgroup = (gm != null && !gm.getSubgroups().isEmpty()) ? gm.getSubgroups().keySet().iterator().next() : null;
        }
        // Don't call parseSelectionBoxes - we've already manually updated the model and selection
        this.saveSessionState();
        this.initGui();
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);
        int screenW = this.getScreenWidth();
        int screenH = this.getScreenHeight();

        // Dark background
        RenderUtils.drawRect(ctx, 0, 0, screenW, screenH, 0xEE141418);

        int leftW = Math.max(260, (screenW - 30) * 52 / 100);
        int rightX = 15 + leftW + 10;
        int rightW = screenW - rightX - 15;
        int panelH = screenH - 64;

        // Outlined panels
        RenderUtils.drawOutlinedBox(ctx, 10, 24, leftW + 10, panelH, 0xFF1C1C22, 0xFF444455);
        RenderUtils.drawOutlinedBox(ctx, rightX - 5, 24, rightW + 10, panelH, 0xFF1C1C22, 0xFF444455);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        // Check if subregion names have changed (e.g., renamed via Litematica menu)
        Collection<String> currentSubRegionNames = this.selection.getAllSubRegionNames();
        if (!currentSubRegionNames.equals(this.lastKnownSubRegionNames)) {
            this.parseSelectionBoxes();
            this.initGui();
            return;
        }

        int screenW = this.getScreenWidth();
        int screenH = this.getScreenHeight();

        // --- LEFT HEADERS & LABELS ---
        this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.regions_header"), 15, 32, 0xFFFFAA00);

        String gName = this.selectedGroupName != null ? this.selectedGroupName : StringUtils.translate("schematic_tiling.gui.editor.label.none");
        GroupModel activeGroup = this.groups.get(this.selectedGroupName);
        boolean activeGroupZ = activeGroup != null && activeGroup.dependsOnZ();
        this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.tiles_group_header", gName, activeGroupZ ? StringUtils.translate("schematic_tiling.gui.editor.label.depends_on_z_tag") : ""), 15, this.yLeftTreeHeader, 0xFF55FFFF);

        if (activeGroup != null) {
            // Subgroups header
            this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.subgroups_header", gName), 18, this.yLeftSubgroupsHeader + 4, 0xFF55FFFF);

            if (activeGroup.getSubgroups().isEmpty()) {
                this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.no_subgroups"), 22, this.yLeftSubgroupsHeader + 18 + 4, 0xFF888888);
            }
        }

        // Unassigned Boxes Header
        this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.unassigned_header", this.unassignedBoxes.size()), 15, this.yLeftUnassignedHeader, 0xFFFFAA00);
        if (this.unassignedBoxes.isEmpty()) {
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.no_unassigned"), 18, this.yLeftUnassignedHeader + 18 + 4, 0xFF888888);
        }

        // --- RIGHT PANEL ---
        int leftW = Math.max(260, (screenW - 30) * 52 / 100);
        int rightX = 15 + leftW + 10;

        if (this.isFineTuning) {
            this.drawRightPaneFineTuning(ctx, rightX, mouseX, mouseY);
        } else if (this.editingBoxName != null) {
            this.drawRightPaneBoxConfig(ctx, rightX);
        } else if (this.selectedSubgroupName != null) {
            this.drawRightPaneSubgroupSettings(ctx, rightX);
        } else if (this.selectedGroupName != null) {
            this.drawRightPaneGroupSettings(ctx, rightX);
        } else {
            // No groups exist - show create group option
            this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.title.no_groups"), rightX, 32, 0xFF55FF55);
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.message.create_group_first"), rightX, 46, 0xFFCCCCCC);
        }

        // --- FLOATING TOAST NOTIFICATION ---
        if (this.toastNotification != null) {
            long elapsed = System.currentTimeMillis() - this.toastNotificationTime;
            if (elapsed < TOAST_DURATION_MS) {
                float alpha = 1.0f;
                if (elapsed > TOAST_DURATION_MS - 1000L) {
                    alpha = 1.0f - (float) (elapsed - (TOAST_DURATION_MS - 1000L)) / 1000.0f;
                }
                int alphaByte = (int) (alpha * 255.0f);
                if (alphaByte > 4) {
                    int textW = this.getStringWidth(this.toastNotification);
                    int pillW = textW + 16;
                    int pillH = 18;
                    int pillX = (screenW - pillW) / 2;
                    int pillY = screenH - 52;
                    int bgCol = (alphaByte << 24) | 0x1A231A;
                    int borderCol = (alphaByte << 24) | 0x44AA44;
                    int textCol = (alphaByte << 24) | 0x77FF77;
                    RenderUtils.drawOutlinedBox(ctx, pillX, pillY, pillW, pillH, bgCol, borderCol);
                    this.drawString(ctx, this.toastNotification, pillX + 8, pillY + 5, textCol);
                }
            } else {
                this.toastNotification = null;
            }
        }
    }

    private void drawRightPaneFineTuning(GuiContext ctx, int rightX, int mouseX, int mouseY) {
        this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.title.fine_tune_origin"), rightX, 32, 0xFF55FF55);

        BlockPos explicit = this.selection.getExplicitOrigin();
        BlockPos effective = this.selection.getEffectiveOrigin();
        String origStr = (explicit != null)
                ? StringUtils.translate("schematic_tiling.gui.editor.label.origin_manual", explicit.getX(), explicit.getY(), explicit.getZ())
                : StringUtils.translate("schematic_tiling.gui.editor.label.origin_auto", effective.getX(), effective.getY(), effective.getZ());
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.current_origin", origStr), rightX, 46, 0xFFCCCCCC);

        int[] tuneXs = this.getFineTuneRowXs(rightX);

        this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.width_fine_tuning"), rightX, 64, 0xFFFFAA00);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.schematic_is"), rightX, 82, COLOR_WHITE);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.blocks"), tuneXs[2], 82, COLOR_WHITE);

        this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.length_fine_tuning"), rightX, 108, 0xFFFFAA00);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.schematic_is"), rightX, 126, COLOR_WHITE);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.blocks"), tuneXs[2], 126, COLOR_WHITE);

        this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.preview_adjustment"), rightX, 148, 0xFF55FFFF);

        String wText = (this.inputTuneWidth != null) ? this.inputTuneWidth.getTextWrapper() : this.tuneWidthText;
        String lText = (this.inputTuneLength != null) ? this.inputTuneLength.getTextWrapper() : this.tuneLengthText;
        int nW = this.parseNonNegativeInt(wText);
        int nL = this.parseNonNegativeInt(lText);
        int pDx = this.widthIsBigger ? -nW : +nW;
        int pDz = this.lengthIsBigger ? -nL : +nL;

        BlockPos baseOrig = (explicit != null) ? explicit : effective;
        Integer lowestY = this.normalizeOriginY ? this.getLowestBlockY() : null;
        int previewY = (lowestY != null) ? lowestY : baseOrig.getY();
        BlockPos previewOrig = new BlockPos(baseOrig.getX() + pDx, previewY, baseOrig.getZ() + pDz);

        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.origin_shift", pDx, pDz), rightX, 162, COLOR_WHITE);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.new_origin", previewOrig.getX(), previewOrig.getY(), previewOrig.getZ()), rightX, 174, 0xFF55FF55);

        // Hover text for normalize origin Y checkbox - only show when hovering
        if (this.checkboxNormalizeOriginY != null && this.checkboxNormalizeOriginY.isMouseOver(mouseX, mouseY)) {
            String hoverText = StringUtils.translate("schematic_tiling.gui.editor.hover.normalize_origin_y");
            int textWidth = this.getStringWidth(hoverText);
            int tooltipX = mouseX + 12;
            int tooltipY = mouseY + 16;
            int tooltipWidth = textWidth + 16;
            int tooltipHeight = 20;

            // Ensure tooltip stays on screen
            if (tooltipX + tooltipWidth > this.getScreenWidth()) {
                tooltipX = mouseX - tooltipWidth - 12;
            }
            if (tooltipY + tooltipHeight > this.getScreenHeight()) {
                tooltipY = mouseY - tooltipHeight - 12;
            }

            RenderUtils.drawOutlinedBox(ctx, tooltipX, tooltipY, tooltipWidth, tooltipHeight, 0xFF000000, 0xFFFFFFFF);
            this.drawString(ctx, hoverText, tooltipX + 8, tooltipY + 6, 0xFFFFFFFF);
        }

        if (this.statusMessage != null && this.statusIsError) {
            this.drawWrappedError(ctx, this.statusMessage, rightX, 238, this.getScreenWidth() - rightX - 20, this.getScreenHeight() - 36);
        }
    }

    private void drawRightPaneBoxConfig(GuiContext ctx, int rightX) {
        this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.title.box_config"), rightX, 32, 0xFF55FF55);

        Box b = this.selection.getSubRegionBox(this.editingBoxName);
        String dimStr = (b != null) ? String.format("%dx%dx%d", Math.abs(b.getSize().getX()), Math.abs(b.getSize().getY()), Math.abs(b.getSize().getZ())) : "?";
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.target_box", this.editingBoxName, dimStr), rightX, 46, 0xFFCCCCCC);

        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.field_group"), rightX, 62, COLOR_WHITE);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.field_subgroup"), rightX, 98, COLOR_WHITE);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.field_tile_name"), rightX, 134, COLOR_WHITE);

        String curG = this.isCustomGroup && this.inputCustomGroup != null ?
                this.inputCustomGroup.getTextWrapper().trim() : (this.formGroup != null ? this.formGroup : "Main");
        String curSG = this.isCustomSubgroup && this.inputCustomSubgroup != null ?
                this.inputCustomSubgroup.getTextWrapper().trim() : (this.formSubgroup != null ? this.formSubgroup : "Sweepers");
        String curTN = (this.inputNameField != null && !this.inputNameField.getTextWrapper().isEmpty()) ?
                this.inputNameField.getTextWrapper().trim() : (this.formTileName != null ? this.formTileName : "Tile");
        GroupModel curGm = this.groups.get(curG);
        SubgroupModel curSgm = (curGm != null) ? curGm.getSubgroups().get(curSG) : null;
        boolean curSubZ = this.resolveSubgroupDependsOnZ(curG, curSG);
        String previewName = formatSubregionName(
                (curG == null || curG.isEmpty()) ? "Main" : curG,
                (curSG == null || curSG.isEmpty()) ? "Sweepers" : curSG,
                (curTN == null || curTN.isEmpty()) ? "Tile" : curTN,
                this.currentRole,
                curSubZ
        );
        this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.generated_name"), rightX, 236, 0xFFFFAA00);
        this.drawString(ctx, previewName, rightX, 248, COLOR_WHITE);

        TileModel activeTm = new TileModel(curTN, this.editingBoxName, this.currentRole, curSubZ);
        List<String> tileErrors = this.getTileErrors(curSgm, activeTm);

        int errY = 264;
        int screenBottom = this.getScreenHeight() - 36;
        int maxErrW = this.getScreenWidth() - rightX - 20;

        if (this.statusMessage != null && this.statusIsError) {
            errY = this.drawWrappedError(ctx, this.statusMessage, rightX, errY, maxErrW, screenBottom);
        }

        if (!tileErrors.isEmpty()) {
            for (String err : tileErrors) {
                if (errY > screenBottom) break;
                errY = this.drawWrappedError(ctx, err, rightX, errY, maxErrW, screenBottom);
            }
        }
    }

    private void drawRightPaneGroupSettings(GuiContext ctx, int rightX) {
        this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.title.group_settings", this.selectedGroupName), rightX, 32, 0xFF55FF55);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.group_settings_desc"), rightX, 46, 0xFFCCCCCC);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.field_group_name"), rightX, 62, COLOR_WHITE);

        GroupModel gm = this.groups.get(this.selectedGroupName);
        if (gm != null) {
            int namedSgCount = gm.getSubgroups().size();
            int totalTiles = 0;
            for (SubgroupModel sg : gm.getSubgroups().values()) {
                totalTiles += sg.getTiles().size();
            }
            this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.group_stats"), rightX, 202, 0xFFFFAA00);
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.stats_subgroups", namedSgCount), rightX + 6, 218, COLOR_WHITE);
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.stats_tiles", totalTiles), rightX + 6, 232, COLOR_WHITE);
            boolean gz = gm.dependsOnZ();
            boolean anyZ = false;
            for (SubgroupModel sm : gm.getSubgroups().values()) {
                if (sm.dependsOnZ()) { anyZ = true; break; }
            }
            String zStatusStr = gz ? StringUtils.translate("schematic_tiling.gui.editor.z_status.on") : (anyZ ? StringUtils.translate("schematic_tiling.gui.editor.z_status.partial") : StringUtils.translate("schematic_tiling.gui.editor.z_status.off"));
            int zColor = gz ? 0xFF55FF55 : (anyZ ? 0xFFFFCC00 : 0xFFCCCCCC);
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.stats_z_dep", zStatusStr), rightX + 6, 246, zColor);

            List<String> groupInconsistencies = new ArrayList<>();
            for (SubgroupModel sm : gm.getSubgroups().values()) {
                List<String> sErrs = this.getSubgroupErrors(sm);
                for (String e : sErrs) {
                    String prefix = sm.getName() + ": ";
                    if (!groupInconsistencies.contains(prefix + e)) {
                        groupInconsistencies.add(prefix + e);
                    }
                }
            }

            int gErrY = 264;
            int maxErrW = this.getScreenWidth() - rightX - 25;
            int screenBottom = this.getScreenHeight() - 36;
            if (!groupInconsistencies.isEmpty()) {
                this.drawStringWithShadow(ctx, "§c" + StringUtils.translate("schematic_tiling.gui.editor.label.inconsistencies"), rightX, gErrY, 0xFFFF4444);
                gErrY += 14;
                for (String err : groupInconsistencies) {
                    if (gErrY > screenBottom) break;
                    gErrY = this.drawWrappedError(ctx, err, rightX + 6, gErrY, maxErrW, screenBottom);
                }
            }
        }
    }

    private void drawRightPaneSubgroupSettings(GuiContext ctx, int rightX) {
        this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.title.subgroup_settings", this.selectedSubgroupName), rightX, 32, 0xFFFFCC00);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.parent_group", this.selectedGroupName), rightX, 46, 0xFFCCCCCC);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.field_subgroup_name"), rightX, 62, COLOR_WHITE);

        int endY = 184;
        GroupModel gm = this.groups.get(this.selectedGroupName);
        if (gm != null) {
            SubgroupModel sg = gm.getSubgroups().get(this.selectedSubgroupName);
            if (sg != null) {
                this.drawStringWithShadow(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.tiles_in_subgroup", sg.getTiles().size()), rightX, 168, 0xFFFFAA00);
                if (sg.getTiles().isEmpty()) {
                    this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.editor.label.no_tiles_in_subgroup"), rightX, 184, 0xFF888888);
                    endY = 200;
                } else {
                    int ty = 184;
                    for (TileModel tm : new ArrayList<>(sg.getTiles())) {
                        List<String> tErrs = this.getTileErrors(sg, tm);
                        boolean hasErr = !tErrs.isEmpty();
                        String roleStr = tm.getXDep() == XDependence.CAP ? StringUtils.translate("schematic_tiling.gui.editor.role_tag.cap") : (tm.getXDep() == XDependence.TILE ? StringUtils.translate("schematic_tiling.gui.editor.role_tag.tile") : StringUtils.translate("schematic_tiling.gui.editor.role_tag.static"));
                        String line = (hasErr ? "§c! • " : "• ") + tm.getName() + " (" + roleStr + ")";
                        this.drawString(ctx, line, rightX + 6, ty, hasErr ? 0xFFFF4444 : COLOR_WHITE);
                        ty += 14;
                        if (ty > this.getScreenHeight() - 60) break;
                    }
                    endY = ty;
                }

                List<String> sgErrors = this.getSubgroupErrors(sg);
                if (!sgErrors.isEmpty()) {
                    endY += 6;
                    this.drawStringWithShadow(ctx, "§c" + StringUtils.translate("schematic_tiling.gui.editor.label.inconsistencies"), rightX, endY, 0xFFFF4444);
                    endY += 14;
                    int maxErrW = this.getScreenWidth() - rightX - 25;
                    int screenBottom = this.getScreenHeight() - 36;
                    for (String err : sgErrors) {
                        if (endY > screenBottom) break;
                        endY = this.drawWrappedError(ctx, err, rightX + 6, endY, maxErrW, screenBottom);
                    }
                }
            }
        }
    }
}
