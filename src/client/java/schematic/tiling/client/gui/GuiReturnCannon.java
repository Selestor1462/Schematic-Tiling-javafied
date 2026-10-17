package schematic.tiling.client.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.gui.GuiSchematicSave;
import fi.dy.masa.litematica.gui.GuiSchematicSaveBase;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.util.FileType;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldDouble;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextFieldInteger;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetCheckBox;
import fi.dy.masa.malilib.gui.Message.MessageType;
import net.minecraft.client.gui.GuiGraphics;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.FileNameUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.cannon.CannonBlockPalette;
import schematic.tiling.cannon.PearlCalculator;
import schematic.tiling.cannon.PearlCalculator.CalculationResult;
import schematic.tiling.cannon.PearlCalculator.CannonSettings;
import schematic.tiling.cannon.ReturnCannonAssembler;
import schematic.tiling.core.SchematicTilerUtil.GenerationResult;

public class GuiReturnCannon extends GuiBase {

    private static final int DIALOG_WIDTH = 490;
    private static final int DIALOG_HEIGHT = 330;

    private static LitematicaSchematic sReferenceAlignerSchematic = null;

    public static void removeReferenceAligner() {
        try {
            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            Set<LitematicaSchematic> schemsToRemove = new HashSet<>();

            if (sReferenceAlignerSchematic != null) {
                schemsToRemove.add(sReferenceAlignerSchematic);
                sReferenceAlignerSchematic = null;
            }

            for (SchematicPlacement p : manager.getAllSchematicsPlacements()) {
                if (p.getName() != null && (p.getName().equalsIgnoreCase("aligner_reference")
                        || p.getName().equalsIgnoreCase("aligner"))) {
                    if (p.getSchematic() != null) {
                        schemsToRemove.add(p.getSchematic());
                    }
                }
            }

            for (LitematicaSchematic s : SchematicHolder.getInstance().getAllSchematics()) {
                if (s != null && s.getMetadata() != null) {
                    String name = s.getMetadata().getName();
                    String author = s.getMetadata().getAuthor();
                    if ("aligner".equalsIgnoreCase(name) && !"Return Cannon Generator".equalsIgnoreCase(author)) {
                        schemsToRemove.add(s);
                    }
                }
            }

            for (LitematicaSchematic s : schemsToRemove) {
                SchematicHolder.getInstance().removeSchematic(s);
            }

            for (SchematicPlacement p : new ArrayList<>(manager.getAllSchematicsPlacements())) {
                if (p.getName() != null && (p.getName().equalsIgnoreCase("aligner_reference")
                        || p.getName().equalsIgnoreCase("aligner"))) {
                    manager.removeSchematicPlacement(p);
                }
            }
        } catch (Throwable ignored) {}
    }

    private final AreaSelection selection;
    private final GuiBase parent;

    // Inputs
    private GuiTextFieldInteger startXField;
    private GuiTextFieldInteger startZField;

    private GuiTextFieldDouble destXField;
    private GuiTextFieldDouble destZField;

    private GuiTextFieldGeneric schematicNameField;

    private PearlCalculator.Direction alignerFacing = PearlCalculator.Direction.NORTH;
    private ButtonGeneric alignerFacingBtn;
    private ReturnCannonAssembler.MirrorMode mirrorMode = ReturnCannonAssembler.MirrorMode.OFF;
    private ButtonGeneric mirrorAlignerBtn;

    private GuiTextFieldInteger maxTntField;
    private GuiTextFieldInteger maxTicksField;
    private GuiTextFieldDouble maxDistField;

    // Checkboxes
    private WidgetCheckBox checkboxLoadToMemory;
    private WidgetCheckBox checkboxCreatePlacement;
    private WidgetCheckBox checkboxPowderSnow;

    public enum SortMode {
        DISTANCE("Distance"),
        TNT("TNT"),
        TICKS("Ticks");

        public final String displayName;

        SortMode(String displayName) {
            this.displayName = displayName;
        }
    }

    // Calculation results
    private List<CalculationResult> solutions = new ArrayList<>();
    private int selectedSolutionIndex = -1;
    private int page = 0;
    private static final int RESULTS_PER_PAGE = 5;

    private BlockPos lastCalculatedStart = null;
    private double lastCalculatedDestX = Double.NaN;
    private double lastCalculatedDestZ = Double.NaN;

    // Sorting
    private SortMode sortMode = SortMode.DISTANCE;
    private boolean sortAscending = true;

    // Status / Messages
    private String statusMessage = null;
    private boolean statusIsError = false;

    // Settings from JSON
    private CannonSettings settings;

    // Persistent GUI state across reopening
    private static String lastSX = null;
    private static String lastSZ = null;
    private static String lastDX = "100.0";
    private static String lastDZ = "100.0";
    private static PearlCalculator.Direction lastAlignerFacing = PearlCalculator.Direction.NORTH;
    private static ReturnCannonAssembler.MirrorMode lastMirrorMode = ReturnCannonAssembler.MirrorMode.OFF;
    private static String lastMaxTnt = null;
    private static String lastMaxTicks = "100";
    private static String lastMaxDist = "10.0";
    private static boolean lastLoadToMemory = true;
    private static boolean lastCreatePlacement = true;
    private static boolean lastGeneratePowderSnow = false;
    private static List<CalculationResult> lastSolutions = new ArrayList<>();
    private static int lastSelectedSolutionIndex = -1;
    private static int lastPage = 0;
    private static SortMode lastSortMode = SortMode.DISTANCE;
    private static boolean lastSortAscending = true;
    private static String lastStatusMessage = null;
    private static boolean lastStatusIsError = false;
    private static String lastSchemName = null;
    private static AreaSelection lastSelection = null;
    private static final CannonBlockPalette blockPalette = new CannonBlockPalette();

    public GuiReturnCannon(AreaSelection selection, GuiBase parent) {
        this.selection = selection;
        this.parent = parent;
        this.setParent(parent);
        this.title = StringUtils.translate("schematic_tiling.gui.cannon.title");

        if (selection != lastSelection) {
            lastSelection = selection;
            lastSX = null;
            lastSZ = null;
        }

        Path gameDir = DataManager.getSchematicsBaseDirectory().getParent();
        if (gameDir == null) gameDir = Path.of(".");
        this.settings = PearlCalculator.loadSettings(gameDir);

        // Check if an aligner placement already exists in Litematica
        if (lastSX == null) {
            try {
                SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
                SchematicPlacement selected = manager.getSelectedSchematicPlacement();
                if (selected == null) {
                    for (SchematicPlacement p : manager.getAllSchematicsPlacements()) {
                        if (p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains("aligner")) {
                            selected = p;
                            break;
                        }
                    }
                }
                if (selected != null && selected.getName() != null && selected.getName().toLowerCase(Locale.ROOT).contains("aligner")) {
                    BlockPos origin = selected.getOrigin();
                    lastSX = String.valueOf(origin.getX());
                    lastSZ = String.valueOf(origin.getZ());
                    lastAlignerFacing = PearlCalculator.Direction.fromAlignerRotation(selected.getRotation());
                    lastMirrorMode = ReturnCannonAssembler.MirrorMode.fromVanillaMirror(selected.getMirror());
                }
            } catch (Throwable ignored) {}
        }

        // Restore static state
        this.alignerFacing = lastAlignerFacing;
        this.mirrorMode = lastMirrorMode;
        this.solutions = lastSolutions;
        this.selectedSolutionIndex = lastSelectedSolutionIndex;
        this.page = lastPage;
        this.sortMode = lastSortMode;
        this.sortAscending = lastSortAscending;
        this.statusMessage = lastStatusMessage;
        this.statusIsError = lastStatusIsError;
    }

    private void saveGuiState() {
        if (this.startXField != null) lastSX = this.startXField.getTextWrapper();
        if (this.startZField != null) lastSZ = this.startZField.getTextWrapper();
        if (this.destXField != null) lastDX = this.destXField.getTextWrapper();
        if (this.destZField != null) lastDZ = this.destZField.getTextWrapper();
        if (this.maxTntField != null) lastMaxTnt = this.maxTntField.getTextWrapper();
        if (this.maxTicksField != null) lastMaxTicks = this.maxTicksField.getTextWrapper();
        if (this.maxDistField != null) lastMaxDist = this.maxDistField.getTextWrapper();
        if (this.schematicNameField != null) lastSchemName = this.schematicNameField.getTextWrapper();
        lastAlignerFacing = this.alignerFacing;
        lastMirrorMode = this.mirrorMode;
        if (this.checkboxLoadToMemory != null) lastLoadToMemory = this.checkboxLoadToMemory.isChecked();
        if (this.checkboxCreatePlacement != null) lastCreatePlacement = this.checkboxCreatePlacement.isChecked();
        if (this.checkboxPowderSnow != null) lastGeneratePowderSnow = this.checkboxPowderSnow.isChecked();
        lastSolutions = this.solutions;
        lastSelectedSolutionIndex = this.selectedSolutionIndex;
        lastPage = this.page;
        lastSortMode = this.sortMode;
        lastSortAscending = this.sortAscending;
        lastStatusMessage = this.statusMessage;
        lastStatusIsError = this.statusIsError;
    }

    private void setSortMode(SortMode mode) {
        if (this.sortMode == mode) {
            this.sortAscending = !this.sortAscending;
        } else {
            this.sortMode = mode;
            this.sortAscending = true;
        }
        this.sortSolutions();
        this.saveGuiState();
        this.initGui();
    }

    private void sortSolutions() {
        if (this.solutions == null || this.solutions.isEmpty()) {
            return;
        }

        CalculationResult selected = (this.selectedSolutionIndex >= 0 && this.selectedSolutionIndex < this.solutions.size())
                ? this.solutions.get(this.selectedSolutionIndex) : null;

        Comparator<CalculationResult> comp;
        switch (this.sortMode) {
            case TNT -> {
                comp = Comparator.comparingInt(r -> r.redTNT + r.blueTNT);
                if (!this.sortAscending) comp = comp.reversed();
                comp = comp.thenComparingDouble(r -> r.distance)
                           .thenComparingInt(r -> r.tick);
            }
            case TICKS -> {
                comp = Comparator.comparingInt(r -> r.tick);
                if (!this.sortAscending) comp = comp.reversed();
                comp = comp.thenComparingDouble(r -> r.distance)
                           .thenComparingInt(r -> r.redTNT + r.blueTNT);
            }
            case DISTANCE -> {
                comp = Comparator.comparingDouble(r -> r.distance);
                if (!this.sortAscending) comp = comp.reversed();
                comp = comp.thenComparingInt(r -> r.redTNT + r.blueTNT)
                           .thenComparingInt(r -> r.tick);
            }
            default -> comp = Comparator.comparingDouble(r -> r.distance);
        }
        comp = comp.thenComparing(r -> r.direction.name);

        this.solutions.sort(comp);

        if (selected != null) {
            this.selectedSolutionIndex = this.solutions.indexOf(selected);
            if (this.selectedSolutionIndex >= 0) {
                this.page = this.selectedSolutionIndex / RESULTS_PER_PAGE;
            }
        } else if (!this.solutions.isEmpty()) {
            this.selectedSolutionIndex = 0;
            this.page = 0;
        }
    }

    @Override
    public void removed() {
        this.saveGuiState();
        super.removed();
    }

    @Override
    public void onClose() {
        this.saveGuiState();
        super.onClose();
    }

    private String getMirrorBtnLabel() {
        return switch (this.mirrorMode) {
            case OFF -> StringUtils.translate("schematic_tiling.gui.cannon.button.mirror_off");
            case FRONT_BACK -> StringUtils.translate("schematic_tiling.gui.cannon.button.mirror_front_back");
            case LEFT_RIGHT -> StringUtils.translate("schematic_tiling.gui.cannon.button.mirror_left_right");
        };
    }

    @Override
    public void initGui() {
        super.initGui();

        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        // Determine default start position from selection or player
        BlockPos defaultPos = null;
        if (this.selection != null) {
            defaultPos = this.selection.getEffectiveOrigin();
            if (defaultPos == null) {
                defaultPos = this.selection.getExplicitOrigin();
            }
            if (defaultPos == null && !this.selection.getAllSubRegionBoxes().isEmpty()) {
                defaultPos = this.selection.getAllSubRegionBoxes().get(0).getPos1();
            }
        }
        if (defaultPos == null && this.mc != null && this.mc.player != null) {
            defaultPos = this.mc.player.blockPosition();
        }
        if (defaultPos == null) {
            defaultPos = BlockPos.ZERO;
        }

        int col1X = startX + 15;
        int y = startY + 30;

        if (GuiReturnCannonDebug.debugModeEnabled) {
            String debugLabel = "\u00a7b" + StringUtils.translate("schematic_tiling.gui.cannon.button.debug_menu");
            int debugBtnW = Math.max(85, this.getStringWidth(debugLabel) + 12);
            ButtonGeneric debugBtn = new ButtonGeneric(startX + dialogWidth - 15 - debugBtnW, startY + 5, debugBtnW, 18, debugLabel);
            this.addButton(debugBtn, (btn, mouse) -> {
                this.saveGuiState();
                GuiBase.openGui(new GuiReturnCannonDebug(this, this.settings));
            });
        }

        String defaultSXStr = String.valueOf(defaultPos.getX());
        String defaultSZStr = String.valueOf(defaultPos.getZ());
        String defaultMaxTntStr = String.valueOf(this.settings.maxTNT);

        // Preserve input text from fields if already active, otherwise from static cache / defaults
        String curSX = (this.startXField != null) ? this.startXField.getTextWrapper() : (lastSX != null ? lastSX : defaultSXStr);
        String curSZ = (this.startZField != null) ? this.startZField.getTextWrapper() : (lastSZ != null ? lastSZ : defaultSZStr);
        String curDX = (this.destXField != null) ? this.destXField.getTextWrapper() : (lastDX != null ? lastDX : "100.0");
        String curDZ = (this.destZField != null) ? this.destZField.getTextWrapper() : (lastDZ != null ? lastDZ : "100.0");
        String curMaxTnt = (this.maxTntField != null) ? this.maxTntField.getTextWrapper() : (lastMaxTnt != null ? lastMaxTnt : defaultMaxTntStr);
        String curMaxTicks = (this.maxTicksField != null) ? this.maxTicksField.getTextWrapper() : (lastMaxTicks != null ? lastMaxTicks : "100");
        String curMaxDist = (this.maxDistField != null) ? this.maxDistField.getTextWrapper() : (lastMaxDist != null ? lastMaxDist : "10.0");

        // --- Start Position Inputs (X, Z only; Y is locked to settings start Y) ---
        this.startXField = new GuiTextFieldInteger(col1X + 32, y, 54, 18, this.font);
        this.startXField.setTextWrapper(curSX);
        this.addTextField(this.startXField, null);

        this.startZField = new GuiTextFieldInteger(col1X + 90, y, 54, 18, this.font);
        this.startZField.setTextWrapper(curSZ);
        this.addTextField(this.startZField, null);

        ButtonGeneric toPlayerBtn = new ButtonGeneric(col1X + 148, y, 58, 18, StringUtils.translate("schematic_tiling.gui.cannon.button.to_player"));
        this.addButton(toPlayerBtn, (btn, mouse) -> {
            if (this.mc != null && this.mc.player != null) {
                BlockPos playerPos = this.mc.player.blockPosition();
                this.startXField.setTextWrapper(String.valueOf(playerPos.getX()));
                this.startZField.setTextWrapper(String.valueOf(playerPos.getZ()));
                this.saveGuiState();
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.start_set_player", playerPos.getX(), playerPos.getZ());
                this.statusIsError = false;
                this.runCalculation();
            }
        });
        y += 21;

        // --- Destination Inputs ---
        this.destXField = new GuiTextFieldDouble(col1X + 32, y, 54, 18, this.font);
        this.destXField.setTextWrapper(curDX);
        this.addTextField(this.destXField, null);

        this.destZField = new GuiTextFieldDouble(col1X + 90, y, 54, 18, this.font);
        this.destZField.setTextWrapper(curDZ);
        this.addTextField(this.destZField, null);

        ButtonGeneric toPlayerDestBtn = new ButtonGeneric(col1X + 148, y, 58, 18, StringUtils.translate("schematic_tiling.gui.cannon.button.to_player"));
        this.addButton(toPlayerDestBtn, (btn, mouse) -> {
            if (this.mc != null && this.mc.player != null) {
                double px = this.mc.player.getX();
                double pz = this.mc.player.getZ();
                this.destXField.setTextWrapper(String.format(Locale.ROOT, "%.1f", px));
                this.destZField.setTextWrapper(String.format(Locale.ROOT, "%.1f", pz));
                this.saveGuiState();
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.dest_set_player",
                        String.format(Locale.ROOT, "%.1f", px), String.format(Locale.ROOT, "%.1f", pz));
                this.statusIsError = false;
                this.runCalculation();
            }
        });
        y += 21;

        // --- Aligner Facing & Mirror ---
        String facingName = StringUtils.translate("schematic_tiling.direction." + this.alignerFacing.name().toLowerCase(Locale.ROOT));
        this.alignerFacingBtn = new ButtonGeneric(col1X + 44, y, 48, 18, facingName);
        this.addButton(this.alignerFacingBtn, (btn, mouse) -> {
            PearlCalculator.Direction[] dirs = PearlCalculator.Direction.values();
            int next = (this.alignerFacing.ordinal() + 1) % dirs.length;
            this.alignerFacing = dirs[next];
            this.alignerFacingBtn.setDisplayString(StringUtils.translate("schematic_tiling.direction." + this.alignerFacing.name().toLowerCase(Locale.ROOT)));
            this.saveGuiState();
        });

        this.mirrorAlignerBtn = new ButtonGeneric(col1X + 96, y, 110, 18, this.getMirrorBtnLabel());
        this.addButton(this.mirrorAlignerBtn, (btn, mouse) -> {
            this.mirrorMode = this.mirrorMode.next();
            this.mirrorAlignerBtn.setDisplayString(this.getMirrorBtnLabel());
            this.saveGuiState();
        });
        y += 21;

        // --- Aligner Placement in Litematica ---
        ButtonGeneric placeAlignerBtn = new ButtonGeneric(col1X, y, 101, 18, StringUtils.translate("schematic_tiling.gui.cannon.button.place_aligner"));
        placeAlignerBtn.setHoverStrings(
                StringUtils.translate("schematic_tiling.gui.cannon.hover.place_aligner_title"),
                StringUtils.translate("schematic_tiling.gui.cannon.hover.place_aligner_desc"));
        this.addButton(placeAlignerBtn, (btn, mouse) -> this.placeAlignerSchematic());

        ButtonGeneric fromPlacementBtn = new ButtonGeneric(col1X + 105, y, 101, 18, StringUtils.translate("schematic_tiling.gui.cannon.button.from_placement"));
        fromPlacementBtn.setHoverStrings(
                StringUtils.translate("schematic_tiling.gui.cannon.hover.from_placement_title"),
                StringUtils.translate("schematic_tiling.gui.cannon.hover.from_placement_desc"));
        this.addButton(fromPlacementBtn, (btn, mouse) -> this.syncFromPlacement());
        y += 21;

        // --- Max TNT & Ticks ---
        this.maxTntField = new GuiTextFieldInteger(col1X + 46, y, 48, 18, this.font);
        this.maxTntField.setTextWrapper(curMaxTnt);
        this.addTextField(this.maxTntField, null);

        this.maxTicksField = new GuiTextFieldInteger(col1X + 138, y, 48, 18, this.font);
        this.maxTicksField.setTextWrapper(curMaxTicks);
        this.addTextField(this.maxTicksField, null);
        y += 21;

        // --- Max Dist ---
        this.maxDistField = new GuiTextFieldDouble(col1X + 50, y, 54, 18, this.font);
        this.maxDistField.setTextWrapper(curMaxDist);
        this.addTextField(this.maxDistField, null);
        y += 21;

        // --- Calculate Button ---
        ButtonGeneric calcBtn = new ButtonGeneric(col1X, y, 206, 20, StringUtils.translate("schematic_tiling.gui.cannon.button.calculate_solutions"));
        this.addButton(calcBtn, (btn, mouse) -> this.runCalculation());

        // --- Results Pagination Buttons (Right Side) ---
        int resultsX = startX + 240;
        int resultsW = 235;
        int resultsY = startY + 30;
        int navY = startY + 160;

        ButtonGeneric prevBtn = new ButtonGeneric(resultsX, navY, 114, 18, StringUtils.translate("schematic_tiling.gui.cannon.button.prev"));
        prevBtn.setEnabled(this.page > 0);
        this.addButton(prevBtn, (btn, mouse) -> {
            if (this.page > 0) {
                this.page--;
                this.initGui();
            }
        });

        int maxPage = Math.max(0, (this.solutions.size() - 1) / RESULTS_PER_PAGE);
        ButtonGeneric nextBtn = new ButtonGeneric(resultsX + 121, navY, 114, 18, StringUtils.translate("schematic_tiling.gui.cannon.button.next"));
        nextBtn.setEnabled(this.page < maxPage);
        this.addButton(nextBtn, (btn, mouse) -> {
            if (this.page < maxPage) {
                this.page++;
                this.initGui();
            }
        });

        // --- Sort Buttons ---
        int sortBtnY = startY + 196;
        String distName = StringUtils.translate("schematic_tiling.gui.cannon.button.sort_dist");
        String distLabel = (this.sortMode == SortMode.DISTANCE) ? (this.sortAscending ? "\u00a7e" + distName + " в–І" : "\u00a7e" + distName + " в–ј") : distName;
        ButtonGeneric btnDist = new ButtonGeneric(resultsX, sortBtnY, 75, 18, distLabel);
        this.addButton(btnDist, (btn, mouse) -> this.setSortMode(SortMode.DISTANCE));

        String tntName = StringUtils.translate("schematic_tiling.gui.cannon.button.sort_tnt");
        String tntLabel = (this.sortMode == SortMode.TNT) ? (this.sortAscending ? "\u00a7e" + tntName + " в–І" : "\u00a7e" + tntName + " в–ј") : tntName;
        ButtonGeneric btnTnt = new ButtonGeneric(resultsX + 80, sortBtnY, 75, 18, tntLabel);
        this.addButton(btnTnt, (btn, mouse) -> this.setSortMode(SortMode.TNT));

        String ticksName = StringUtils.translate("schematic_tiling.gui.cannon.button.sort_ticks");
        String ticksLabel = (this.sortMode == SortMode.TICKS) ? (this.sortAscending ? "\u00a7e" + ticksName + " в–І" : "\u00a7e" + ticksName + " в–ј") : ticksName;
        ButtonGeneric btnTicks = new ButtonGeneric(resultsX + 160, sortBtnY, 75, 18, ticksLabel);
        this.addButton(btnTicks, (btn, mouse) -> this.setSortMode(SortMode.TICKS));

        // Add clickable result selection buttons
        int pageStart = this.page * RESULTS_PER_PAGE;
        int pageEnd = Math.min(this.solutions.size(), pageStart + RESULTS_PER_PAGE);
        for (int i = pageStart; i < pageEnd; i++) {
            final int solIdx = i;
            CalculationResult res = this.solutions.get(i);
            int rowY = resultsY + ((i - pageStart) * 26);
            boolean isSelected = (this.selectedSolutionIndex == solIdx);

            String dirName = StringUtils.translate("schematic_tiling.direction." + res.direction.name().toLowerCase(Locale.ROOT));
            String label = String.format(Locale.ROOT, "#%d: %d TNT (R:%d B:%d) %s %dt (%.2fm)",
                    solIdx + 1, res.getTotalTNT(), res.redTNT, res.blueTNT, dirName, res.tick, res.distance);

            String arrivalX = String.format(Locale.ROOT, "%.2f", res.worldArrivalPos.x);
            String arrivalY = String.format(Locale.ROOT, "%.2f", res.worldArrivalPos.y);
            String arrivalZ = String.format(Locale.ROOT, "%.2f", res.worldArrivalPos.z);
            String distStr = String.format(Locale.ROOT, "%.2f", res.distance);

            ButtonGeneric rowBtn = new ButtonGeneric(resultsX, rowY, resultsW, 22, label,
                    StringUtils.translate("schematic_tiling.gui.cannon.hover.total_tnt",
                            res.getTotalTNT(), res.redTNT, res.blueTNT),
                    StringUtils.translate("schematic_tiling.gui.cannon.hover.landing",
                            arrivalX, arrivalY, arrivalZ, distStr),
                    StringUtils.translate("schematic_tiling.gui.cannon.hover.stopper",
                            res.catchBlockPos.getX(), res.catchBlockPos.getY(), res.catchBlockPos.getZ())
            );
            if (isSelected) {
                rowBtn.setEnabled(false);
            }
            this.addButton(rowBtn, (btn, mouse) -> {
                this.selectedSolutionIndex = solIdx;
                this.initGui();
            });
        }

        boolean hasSol = this.selectedSolutionIndex >= 0 && this.selectedSolutionIndex < this.solutions.size();

        // --- Schematic Name Input Field (Right Side under Sort buttons) ---
        String defaultName = "cannon_return";
        if (this.selectedSolutionIndex >= 0 && this.selectedSolutionIndex < this.solutions.size()) {
            CalculationResult sel = this.solutions.get(this.selectedSolutionIndex);
            String selName = (this.selection != null && this.selection.getName() != null)
                    ? this.selection.getName() : "cannon";
            defaultName = String.format(Locale.ROOT, "%s_%s_%dt", selName, sel.direction.name, sel.tick);
        }
        String curSchemName = (this.schematicNameField != null)
                ? this.schematicNameField.getTextWrapper()
                : (lastSchemName != null ? lastSchemName : defaultName);

        this.schematicNameField = new GuiTextFieldGeneric(resultsX, startY + 236, resultsW, 18, this.font);
        this.schematicNameField.setMaxLengthWrapper(128);
        this.schematicNameField.setTextWrapper(curSchemName);
        this.schematicNameField.setHoverTooltip(
                StringUtils.translate("schematic_tiling.gui.cannon.hover.schematic_name_title"),
                StringUtils.translate("schematic_tiling.gui.cannon.hover.schematic_name_desc"));
        this.addTextField(this.schematicNameField, null);

        // --- Bottom Area: Checkboxes & Actions ---
        int bottomY = startY + 244;
        boolean loadMem = (this.checkboxLoadToMemory != null) ? this.checkboxLoadToMemory.isChecked() : lastLoadToMemory;
        boolean createPlace = (this.checkboxCreatePlacement != null) ? this.checkboxCreatePlacement.isChecked() : lastCreatePlacement;
        boolean genSnow = (this.checkboxPowderSnow != null) ? this.checkboxPowderSnow.isChecked() : lastGeneratePowderSnow;

        this.checkboxLoadToMemory = new WidgetCheckBox(startX + 15, bottomY, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, StringUtils.translate("schematic_tiling.gui.cannon.checkbox.load_to_memory"));
        this.checkboxLoadToMemory.setChecked(loadMem, false);
        this.addWidget(this.checkboxLoadToMemory);

        this.checkboxCreatePlacement = new WidgetCheckBox(startX + 115, bottomY, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, StringUtils.translate("schematic_tiling.gui.cannon.checkbox.create_placement"));
        this.checkboxCreatePlacement.setChecked(createPlace, false);
        this.addWidget(this.checkboxCreatePlacement);

        this.checkboxPowderSnow = new WidgetCheckBox(startX + 15, bottomY + 15, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, StringUtils.translate("schematic_tiling.gui.cannon.checkbox.powder_snow"));
        this.checkboxPowderSnow.setChecked(genSnow, false);
        this.addWidget(this.checkboxPowderSnow);

        int btnY = startY + 276;

        ButtonGeneric assembleBtn = new ButtonGeneric(startX + 15, btnY, 90, 20, StringUtils.translate("schematic_tiling.gui.cannon.button.generate_schem"));
        assembleBtn.setEnabled(hasSol);
        this.addButton(assembleBtn, (btn, mouse) -> this.generateSchematic());

        ButtonRgbPalette paletteBtn = new ButtonRgbPalette(startX + 107, btnY, 20, 20, blockPalette);
        this.addButton(paletteBtn, (btn, mouse) -> {
            this.saveGuiState();
            GuiBase.openGui(new GuiCannonBlockPalette(this, blockPalette));
        });

        ButtonGeneric copyLandingBtn = new ButtonGeneric(startX + 130, btnY, 80, 20, StringUtils.translate("schematic_tiling.gui.cannon.button.copy_landing"));
        copyLandingBtn.setEnabled(hasSol);
        this.addButton(copyLandingBtn, (btn, mouse) -> {
            if (this.selectedSolutionIndex >= 0 && this.selectedSolutionIndex < this.solutions.size() && this.mc != null) {
                CalculationResult sel = this.solutions.get(this.selectedSolutionIndex);
                String landingStr = String.format(Locale.ROOT, "%.2f %.2f %.2f",
                        sel.worldArrivalPos.x, sel.worldArrivalPos.y, sel.worldArrivalPos.z);
                this.mc.keyboardHandler.setClipboard(landingStr);
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.copied_landing",
                        String.format(Locale.ROOT, "%.2f", sel.worldArrivalPos.x),
                        String.format(Locale.ROOT, "%.2f", sel.worldArrivalPos.y),
                        String.format(Locale.ROOT, "%.2f", sel.worldArrivalPos.z));
                this.statusIsError = false;
            }
        });

        ButtonGeneric copyCatchBtn = new ButtonGeneric(startX + 215, btnY, 72, 20, StringUtils.translate("schematic_tiling.gui.cannon.button.copy_catch"));
        copyCatchBtn.setEnabled(hasSol);
        this.addButton(copyCatchBtn, (btn, mouse) -> {
            if (this.selectedSolutionIndex >= 0 && this.selectedSolutionIndex < this.solutions.size() && this.mc != null) {
                CalculationResult sel = this.solutions.get(this.selectedSolutionIndex);
                this.mc.keyboardHandler.setClipboard(String.format(Locale.ROOT, "%d %d %d",
                        sel.catchBlockPos.getX(), sel.catchBlockPos.getY(), sel.catchBlockPos.getZ()));
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.copied_catch", sel.catchBlockPos.toShortString());
                this.statusIsError = false;
            }
        });

        ButtonGeneric setblockBtn = new ButtonGeneric(startX + 292, btnY, 92, 20, StringUtils.translate("schematic_tiling.gui.cannon.button.setblock_catch"));
        setblockBtn.setEnabled(hasSol && !schematic.tiling.client.util.CatchBlockPlacer.isRunning());
        this.addButton(setblockBtn, (btn, mouse) -> {
            if (this.selectedSolutionIndex >= 0 && this.selectedSolutionIndex < this.solutions.size() && this.mc != null && this.mc.player != null) {
                CalculationResult sel = this.solutions.get(this.selectedSolutionIndex);
                BlockPos cp = sel.catchBlockPos;
                schematic.tiling.client.util.CatchBlockPlacer.startPlacement(cp);
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.placing_catch", cp.getX(), cp.getY(), cp.getZ());
                this.statusIsError = false;
            }
        });

        ButtonGeneric closeBtn = new ButtonGeneric(startX + dialogWidth - 75, btnY, 60, 20, StringUtils.translate("schematic_tiling.gui.cannon.button.close"));
        this.addButton(closeBtn, (btn, mouse) -> {
            this.saveGuiState();
            GuiBase.openGui(this.parent);
        });
    }

    private void runCalculation() {
        try {
            int sx = Integer.parseInt(this.startXField.getTextWrapper().trim());
            int sy = this.settings.getStartY();
            int sz = Integer.parseInt(this.startZField.getTextWrapper().trim());
            BlockPos cannonStart = new BlockPos(sx, sy, sz);

            double dx = Double.parseDouble(this.destXField.getTextWrapper().trim());
            double dz = Double.parseDouble(this.destZField.getTextWrapper().trim());

            int maxTnt = Integer.parseInt(this.maxTntField.getTextWrapper().trim());
            int maxTicks = Integer.parseInt(this.maxTicksField.getTextWrapper().trim());
            double maxDist = Double.parseDouble(this.maxDistField.getTextWrapper().trim());

            this.settings.maxTNT = maxTnt;

            this.solutions = PearlCalculator.calculateSolutions(
                    cannonStart, dx, dz, this.settings, maxTicks, maxDist
            );
            this.lastCalculatedStart = cannonStart;
            this.lastCalculatedDestX = dx;
            this.lastCalculatedDestZ = dz;

            this.sortSolutions();
            this.page = 0;
            this.selectedSolutionIndex = this.solutions.isEmpty() ? -1 : 0;

            if (this.solutions.isEmpty()) {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.no_solutions");
                this.statusIsError = true;
            } else {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.found_solutions", this.solutions.size());
                this.statusIsError = false;
            }
        } catch (Throwable t) {
            this.statusMessage = "Input error: " + t.getMessage();
            this.statusIsError = true;
        }
        this.saveGuiState();
        this.initGui();
    }

    public void loadPresetAndCalculate(
            int startX, int startZ,
            double destX, double destZ,
            PearlCalculator.Direction alignerFacing,
            ReturnCannonAssembler.MirrorMode mirrorMode,
            String name
    ) {
        lastSX = String.valueOf(startX);
        lastSZ = String.valueOf(startZ);
        lastDX = String.valueOf(destX);
        lastDZ = String.valueOf(destZ);
        lastAlignerFacing = alignerFacing;
        lastMirrorMode = mirrorMode;
        lastSchemName = name;
        this.alignerFacing = alignerFacing;
        this.mirrorMode = mirrorMode;
        if (this.startXField != null) this.startXField.setTextWrapper(lastSX);
        if (this.startZField != null) this.startZField.setTextWrapper(lastSZ);
        if (this.destXField != null) this.destXField.setTextWrapper(lastDX);
        if (this.destZField != null) this.destZField.setTextWrapper(lastDZ);
        if (this.schematicNameField != null) this.schematicNameField.setTextWrapper(name);
        this.runCalculation();
    }

    private void generateSchematic() {
        try {
            int sx = Integer.parseInt(this.startXField.getTextWrapper().trim());
            int sy = this.settings.getStartY();
            int sz = Integer.parseInt(this.startZField.getTextWrapper().trim());
            BlockPos cannonStart = new BlockPos(sx, sy, sz);

            double dx = Double.parseDouble(this.destXField.getTextWrapper().trim());
            double dz = Double.parseDouble(this.destZField.getTextWrapper().trim());

            if (this.lastCalculatedStart == null || !this.lastCalculatedStart.equals(cannonStart)
                    || Double.isNaN(this.lastCalculatedDestX) || Math.abs(this.lastCalculatedDestX - dx) > 1e-4
                    || Double.isNaN(this.lastCalculatedDestZ) || Math.abs(this.lastCalculatedDestZ - dz) > 1e-4) {
                int prevSel = this.selectedSolutionIndex;
                this.runCalculation();
                if (prevSel >= 0 && prevSel < this.solutions.size()) {
                    this.selectedSolutionIndex = prevSel;
                }
            }

            if (this.solutions.isEmpty() || this.selectedSolutionIndex < 0 || this.selectedSolutionIndex >= this.solutions.size()) {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.no_solutions");
                this.statusIsError = true;
                this.initGui();
                return;
            }

            CalculationResult sol = this.solutions.get(this.selectedSolutionIndex);

            String rawName = (this.schematicNameField != null) ? this.schematicNameField.getTextWrapper().trim() : "";
            if (rawName.isEmpty()) {
                String selName = (this.selection != null && this.selection.getName() != null) ? this.selection.getName() : "cannon";
                rawName = String.format(Locale.ROOT, "%s_%s_%dt", selName, sol.direction.name, sol.tick);
            }
            String schemName = FileNameUtils.generateSafeFileName(rawName);

            BlockPos placementOrigin = ReturnCannonAssembler.getSchematicOrigin(sx, sz, this.alignerFacing.getAlignerRotation(), this.mirrorMode);

            Path gameDir = DataManager.getSchematicsBaseDirectory().getParent();
            if (gameDir == null) gameDir = Path.of(".");

            ReturnCannonAssembler assembler = new ReturnCannonAssembler(gameDir);

            // Delete reference aligner placement and unload it from memory when player generates cannon
            removeReferenceAligner();

            // Assembles cannon with main Cannon subregion + 1-block Catcher subregion (+ optional Landing subregion)
            boolean genSnow = this.checkboxPowderSnow != null && this.checkboxPowderSnow.isChecked();
            LitematicaSchematic schem = assembler.assembleCannon(
                    this.alignerFacing.getAlignerRotation(),
                    this.mirrorMode,
                    sol,
                    this.settings,
                    schemName,
                    cannonStart,
                    genSnow,
                    blockPalette
            );

            this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.save_to_finish");
            this.statusIsError = false;

            this.saveGuiState();

            // Open Return Cannon save schematic menu (loads into memory and creates placement upon saving)
            GuiReturnCannonSave saveGui = new GuiReturnCannonSave(
                    schem,
                    this.checkboxLoadToMemory.isChecked(),
                    this.checkboxCreatePlacement.isChecked(),
                    placementOrigin,
                    schemName,
                    sol
            );
            saveGui.setParent(this);
            GuiBase.openGui(saveGui);

        } catch (Throwable t) {
            this.statusMessage = "Generation failed: " + t.getMessage();
            this.statusIsError = true;
        }
        this.saveGuiState();
        this.initGui();
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
        this.drawString(ctx, this.title, startX + 15, startY + 12, COLOR_WHITE);

        int col1X = startX + 15;
        int y = startY + 30;

        // Labels matching field Y offsets
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.label.start"), col1X, y + 5, 0xFFAAAAAA);
        y += 21;
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.label.dest"), col1X, y + 5, 0xFFAAAAAA);
        y += 21;
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.label.aligner"), col1X, y + 5, 0xFFAAAAAA);
        y += 21;
        y += 21; // Skips Aligner placement buttons row
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.label.max_tnt"), col1X, y + 5, 0xFFAAAAAA);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.label.ticks"), col1X + 104, y + 5, 0xFFAAAAAA);
        y += 21;
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.label.max_dist"), col1X, y + 5, 0xFFAAAAAA);
        y += 21; // Skips to Calculate button (startY + 156)
        y += 23; // Skips to Details box (startY + 179)

        // Details of selected solution (Left Side under inputs)
        if (this.selectedSolutionIndex >= 0 && this.selectedSolutionIndex < this.solutions.size()) {
            CalculationResult sel = this.solutions.get(this.selectedSolutionIndex);
            int boxY = y;
            int boxW = 206;
            int boxH = 62;
            RenderUtils.drawOutlinedBox(ctx, col1X, boxY, boxW, boxH, 0xD0202020, 0xFF505050);

            String distStr = String.format(Locale.ROOT, "%.2f", sel.distance);
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.details.landing", sel.tick, distStr),
                    col1X + 6, boxY + 3, 0xFFFFFF55);

            String xStr = String.format(Locale.ROOT, "%.2f", sel.worldArrivalPos.x);
            String yStr = String.format(Locale.ROOT, "%.2f", sel.worldArrivalPos.y);
            String zStr = String.format(Locale.ROOT, "%.2f", sel.worldArrivalPos.z);
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.details.pos", xStr, yStr, zStr),
                    col1X + 6, boxY + 13, 0xFFFFFFFF);

            boolean slotAIsRed = ReturnCannonAssembler.isSlotARed(sel.direction, this.settings);
            String redSide = slotAIsRed
                    ? StringUtils.translate("schematic_tiling.gui.cannon.side.left")
                    : StringUtils.translate("schematic_tiling.gui.cannon.side.right");
            String blueSide = slotAIsRed
                    ? StringUtils.translate("schematic_tiling.gui.cannon.side.right")
                    : StringUtils.translate("schematic_tiling.gui.cannon.side.left");

            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.details.stopper",
                            sel.catchBlockPos.getX(), sel.catchBlockPos.getY(), sel.catchBlockPos.getZ(), sel.tick),
                    col1X + 6, boxY + 23, 0xFFFFAA00);
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.details.total_tnt",
                            sel.getTotalTNT(), sel.redTNT, sel.blueTNT),
                    col1X + 6, boxY + 33, 0xFFDDDDDD);
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.details.dupers",
                            sel.redDuperSize, redSide, sel.blueDuperSize, blueSide),
                    col1X + 6, boxY + 43, 0xFFDDDDDD);

            String dirName = StringUtils.translate("schematic_tiling.direction." + sel.direction.name().toLowerCase(Locale.ROOT));
            this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.details.basket_rods", dirName),
                    col1X + 6, boxY + 53, 0xFFFFAA00);
        }

        // Solutions Header (Right Side)
        int resultsX = startX + 240;
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.label.solutions_count", this.solutions.size()), resultsX, startY + 18, COLOR_WHITE);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.label.sort_by"), resultsX, startY + 184, 0xFFAAAAAA);
        this.drawString(ctx, StringUtils.translate("schematic_tiling.gui.cannon.label.schematic_name"), resultsX, startY + 224, 0xFFAAAAAA);

        // Status message placed cleanly below action buttons
        if (this.statusMessage != null) {
            int msgColor = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            this.drawString(ctx, this.statusMessage, startX + 15, startY + 304, msgColor);
        }
    }

    private void placeAlignerSchematic() {
        try {
            BlockPos playerPos = (this.mc != null && this.mc.player != null)
                    ? this.mc.player.blockPosition()
                    : new BlockPos(0, 128, 0);

            int sx = playerPos.getX();
            int sy = PearlCalculator.NETHER_ROOF_MIN_Y;
            int sz = playerPos.getZ();
            BlockPos targetPos = new BlockPos(sx, sy, sz);

            if (this.startXField != null) this.startXField.setTextWrapper(String.valueOf(sx));
            if (this.startZField != null) this.startZField.setTextWrapper(String.valueOf(sz));

            Path schemDir = DataManager.getSchematicsBaseDirectory();
            Path gameDir = schemDir.getParent() != null ? schemDir.getParent() : Path.of(".");
            ReturnCannonAssembler assembler = new ReturnCannonAssembler(gameDir);
            LitematicaSchematic alignerSchem = assembler.loadComponentSchematic("aligner.litematic");

            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            SchematicPlacement existing = null;
            for (SchematicPlacement p : manager.getAllSchematicsPlacements()) {
                if (p.getName() != null && (p.getName().equalsIgnoreCase("aligner_reference") || p.getName().equalsIgnoreCase("aligner"))) {
                    existing = p;
                    break;
                }
            }

            sReferenceAlignerSchematic = alignerSchem;
            SchematicHolder.getInstance().addSchematic(alignerSchem, false);

            if (existing != null) {
                existing.setOrigin(targetPos, null);
                existing.setRotation(this.alignerFacing.getAlignerRotation(), null);
                existing.setMirror(this.mirrorMode.toVanillaMirror(), null);
                existing.setEnabled(true);
                existing.setRenderSchematic(true);
                existing.setShouldBeSaved(false);
                manager.setSelectedSchematicPlacement(existing);
            } else {
                SchematicPlacement placement = SchematicPlacement.createFor(
                        alignerSchem, targetPos, "aligner_reference", true, true
                );
                placement.setRotation(this.alignerFacing.getAlignerRotation(), null);
                placement.setMirror(this.mirrorMode.toVanillaMirror(), null);
                placement.setShouldBeSaved(false);
                manager.addSchematicPlacement(placement, true);
                manager.setSelectedSchematicPlacement(placement);
            }

            this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.aligner_placed", sx, sy, sz);
            this.statusIsError = false;
            String savedMsg = this.statusMessage;
            this.runCalculation();
            this.statusMessage = savedMsg;
            this.statusIsError = false;
            return;
        } catch (Throwable t) {
            this.statusMessage = "Place Aligner error: " + t.getMessage();
            this.statusIsError = true;
        }
        this.saveGuiState();
        this.initGui();
    }

    private void syncFromPlacement() {
        try {
            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            SchematicPlacement placement = manager.getSelectedSchematicPlacement();
            if (placement == null) {
                for (SchematicPlacement p : manager.getAllSchematicsPlacements()) {
                    if (p.getName() != null && (p.getName().toLowerCase(Locale.ROOT).contains("aligner")
                            || p.getName().toLowerCase(Locale.ROOT).contains("cannon"))) {
                        placement = p;
                        break;
                    }
                }
            }

            if (placement == null) {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.no_placement_selected");
                this.statusIsError = true;
                return;
            }

            BlockPos origin = placement.getOrigin();
            this.startXField.setTextWrapper(String.valueOf(origin.getX()));
            this.startZField.setTextWrapper(String.valueOf(origin.getZ()));

            Rotation rot = placement.getRotation();
            this.alignerFacing = PearlCalculator.Direction.fromAlignerRotation(rot);
            if (this.alignerFacingBtn != null) {
                this.alignerFacingBtn.setDisplayString(StringUtils.translate("schematic_tiling.direction." + this.alignerFacing.name().toLowerCase(Locale.ROOT)));
            }

            Mirror mirror = placement.getMirror();
            this.mirrorMode = ReturnCannonAssembler.MirrorMode.fromVanillaMirror(mirror);
            if (this.mirrorAlignerBtn != null) {
                this.mirrorAlignerBtn.setDisplayString(this.getMirrorBtnLabel());
            }

            String dirName = StringUtils.translate("schematic_tiling.direction." + this.alignerFacing.name().toLowerCase(Locale.ROOT));
            this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.status.synced_from_placement",
                    origin.getX(), origin.getZ(), dirName, this.getMirrorBtnLabel());
            this.statusIsError = false;
            String savedMsg = this.statusMessage;
            this.runCalculation();
            this.statusMessage = savedMsg;
            this.statusIsError = false;
            return;
        } catch (Throwable t) {
            this.statusMessage = "Sync error: " + t.getMessage();
            this.statusIsError = true;
        }
        this.saveGuiState();
        this.initGui();
    }

    private class GuiReturnCannonSave extends GuiSchematicSave {
        private final boolean loadToMemory;
        private final boolean createPlacement;
        private final BlockPos placementOrigin;
        private final String placementName;
        private final CalculationResult sol;
        private boolean processedPostSave = false;

        public GuiReturnCannonSave(
                LitematicaSchematic schematic,
                boolean loadToMemory,
                boolean createPlacement,
                BlockPos placementOrigin,
                String placementName,
                CalculationResult sol
        ) {
            super(schematic);
            this.loadToMemory = loadToMemory;
            this.createPlacement = createPlacement;
            this.placementOrigin = placementOrigin;
            this.placementName = placementName;
            this.sol = sol;
            this.title = StringUtils.translate("schematic_tiling.gui.cannon_save.title");
            this.defaultText = placementName;
            this.setTextFieldText(placementName);
            if (schematic != null) {
                schematic.getMetadata().setModifiedSinceSaved();
            }
        }

        @Override
        protected IButtonActionListener createButtonListener(GuiSchematicSaveBase.ButtonType type) {
            IButtonActionListener originalListener = super.createButtonListener(type);
            if (type == GuiSchematicSaveBase.ButtonType.SAVE) {
                return (button, mouseButton) -> {
                    originalListener.actionPerformedWithButton(button, mouseButton);

                    if (!this.processedPostSave && this.schematic != null && !this.schematic.getMetadata().wasModifiedSinceSaved()) {
                        this.processedPostSave = true;

                        Path dir = this.getListWidget().getCurrentDirectory();
                        String text = this.getTextFieldText();
                        String fileName = FileNameUtils.generateSimpleUnicodeSafeFileName(text);
                        if (FileNameUtils.doesFileNameContainIllegalCharacters(fileName)) {
                            fileName = FileNameUtils.generateSafeFileName(fileName);
                        }
                        if (!fileName.endsWith(".litematic")) {
                            fileName = fileName + ".litematic";
                        }
                        Path savedFile = (dir != null) ? dir.resolve(fileName).normalize() : null;

                        LitematicaSchematic targetSchem = null;
                        if (savedFile != null && Files.isRegularFile(savedFile)) {
                            if (this.loadToMemory) {
                                targetSchem = SchematicHolder.getInstance().getOrLoad(savedFile);
                            } else {
                                FileType fileType = FileType.fromFile(savedFile);
                                targetSchem = LitematicaSchematic.createFromFile(savedFile.getParent(), savedFile.getFileName().toString(), fileType);
                            }
                        }

                        if (targetSchem == null) {
                            targetSchem = this.schematic;
                            if (this.loadToMemory) {
                                SchematicHolder.getInstance().addSchematic(targetSchem, false);
                            }
                        }

                        removeReferenceAligner();

                        if (this.createPlacement) {
                            String name = targetSchem.getFile() != null ?
                                    FileNameUtils.getFileNameWithoutExtension(targetSchem.getFile().getFileName().toString()) :
                                    this.placementName;
                            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
                            SchematicPlacement placement = SchematicPlacement.createFor(targetSchem, this.placementOrigin, name, true, true);
                            manager.addSchematicPlacement(placement, true);
                            manager.setSelectedSchematicPlacement(placement);
                        }

                        GuiReturnCannon.this.statusMessage = String.format(Locale.ROOT,
                                "Generated & Saved! Catcher: [%d, %d, %d]",
                                this.sol.catchBlockPos.getX(), this.sol.catchBlockPos.getY(), this.sol.catchBlockPos.getZ());
                        GuiReturnCannon.this.statusIsError = false;

                        InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS,
                                "[Return Cannon] Saved! Catcher Block: [%d, %d, %d]",
                                this.sol.catchBlockPos.getX(), this.sol.catchBlockPos.getY(), this.sol.catchBlockPos.getZ());
                    }
                };
            }
            return originalListener;
        }
    }

    public static class ButtonRgbPalette extends ButtonGeneric {
        private final CannonBlockPalette palette;

        public ButtonRgbPalette(int x, int y, int width, int height, CannonBlockPalette palette) {
            super(x, y, width, height, "");
            this.palette = palette;
        }

        @Override
        public void render(GuiGraphics ctx, int mouseX, int mouseY, boolean unused) {
            this.setDisplayString("");

            this.clearHoverStrings();
            this.hoverStrings.add("\u00a7e\u00a7l" + StringUtils.translate("schematic_tiling.gui.cannon.palette.title"));
            this.hoverStrings.add("\u00a77" + StringUtils.translate("schematic_tiling.gui.cannon.palette.cat.glass") + ": \u00a7f" + this.palette.getSelectedGlass().getName().getString());
            this.hoverStrings.add("\u00a77" + StringUtils.translate("schematic_tiling.gui.cannon.palette.cat.solid") + ": \u00a7f" + this.palette.getSelectedSolid().getName().getString());
            this.hoverStrings.add("\u00a77" + StringUtils.translate("schematic_tiling.gui.cannon.palette.cat.stairs") + ": \u00a7f" + this.palette.getSelectedStairs().getName().getString());
            this.hoverStrings.add("\u00a77" + StringUtils.translate("schematic_tiling.gui.cannon.palette.cat.leaves") + ": \u00a7f" + this.palette.getSelectedLeaves().getName().getString());
            this.hoverStrings.add("\u00a77" + StringUtils.translate("schematic_tiling.gui.cannon.palette.cat.terracotta") + ": \u00a7f" + this.palette.getSelectedTerracotta().getName().getString());
            this.hoverStrings.add("\u00a77" + StringUtils.translate("schematic_tiling.gui.cannon.palette.cat.nether_wood") + ": \u00a7f" + this.palette.getSelectedNetherWood().displayName);
            this.hoverStrings.add("\u00a78" + StringUtils.translate("schematic_tiling.gui.cannon.palette.hover_hint"));

            super.render(ctx, mouseX, mouseY, unused);
            if (!this.visible) return;

            // Draw RGB rainbow bar along bottom of button
            int barY = this.y + this.height - 3;
            int barX = this.x + 2;
            int totalW = this.width - 4;
            int[] rgbColors = new int[] {
                0xFFFF3B30, // Red
                0xFFFF9500, // Orange
                0xFFFFCC00, // Yellow
                0xFF34C759, // Green
                0xFF00C7BE, // Teal
                0xFF007AFF, // Blue
                0xFF5856D6, // Indigo
                0xFFAF52DE  // Purple
            };
            float segW = (float) totalW / rgbColors.length;
            for (int i = 0; i < rgbColors.length; i++) {
                int segX1 = barX + Math.round(i * segW);
                int segX2 = barX + Math.round((i + 1) * segW);
                RenderUtils.drawRect(ctx, segX1, barY, segX2 - segX1, 2, rgbColors[i]);
            }

            // Draw artist palette icon centered in button
            int iconX = this.x + (this.width - 12) / 2;
            int iconY = this.y + (this.height - 12) / 2 - 1;
            // Palette body
            RenderUtils.drawRect(ctx, iconX + 1, iconY, 10, 11, 0xFFD8A868);
            RenderUtils.drawRect(ctx, iconX, iconY + 1, 12, 9, 0xFFD8A868);
            // Palette outline
            RenderUtils.drawRect(ctx, iconX + 1, iconY, 10, 1, 0xFF553311);
            RenderUtils.drawRect(ctx, iconX + 1, iconY + 10, 10, 1, 0xFF553311);
            RenderUtils.drawRect(ctx, iconX, iconY + 1, 1, 9, 0xFF553311);
            RenderUtils.drawRect(ctx, iconX + 11, iconY + 1, 1, 9, 0xFF553311);
            // Thumb hole
            RenderUtils.drawRect(ctx, iconX + 2, iconY + 5, 2, 2, 0xFF222222);
            // Paint dabs (Red, Yellow, Green, Cyan, Purple)
            RenderUtils.drawRect(ctx, iconX + 2, iconY + 2, 2, 2, 0xFFFF2222);
            RenderUtils.drawRect(ctx, iconX + 6, iconY + 1, 2, 2, 0xFFFFDD00);
            RenderUtils.drawRect(ctx, iconX + 8, iconY + 3, 2, 2, 0xFF22CC44);
            RenderUtils.drawRect(ctx, iconX + 8, iconY + 6, 2, 2, 0xFF00BBFF);
            RenderUtils.drawRect(ctx, iconX + 5, iconY + 8, 2, 2, 0xFFAA22EE);
        }
    }
}
