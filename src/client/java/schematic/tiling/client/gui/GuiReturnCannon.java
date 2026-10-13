package schematic.tiling.client.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;

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
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.FileNameUtils;
import fi.dy.masa.malilib.util.InfoUtils;

import schematic.tiling.cannon.PearlCalculator;
import schematic.tiling.cannon.PearlCalculator.CalculationResult;
import schematic.tiling.cannon.PearlCalculator.CannonSettings;
import schematic.tiling.cannon.ReturnCannonAssembler;
import schematic.tiling.core.SchematicTilerUtil.GenerationResult;

public class GuiReturnCannon extends GuiBase {

    private static final int DIALOG_WIDTH = 490;
    private static final int DIALOG_HEIGHT = 330;

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
    private static List<CalculationResult> lastSolutions = new ArrayList<>();
    private static int lastSelectedSolutionIndex = -1;
    private static int lastPage = 0;
    private static SortMode lastSortMode = SortMode.DISTANCE;
    private static boolean lastSortAscending = true;
    private static String lastStatusMessage = null;
    private static boolean lastStatusIsError = false;
    private static String lastSchemName = null;
    private static AreaSelection lastSelection = null;

    public GuiReturnCannon(AreaSelection selection, GuiBase parent) {
        this.selection = selection;
        this.parent = parent;
        this.setParent(parent);
        this.title = "Return Cannon Generator";

        if (selection != lastSelection) {
            lastSelection = selection;
            lastSX = null;
            lastSZ = null;
        }

        Path gameDir = DataManager.getSchematicsBaseDirectory().getParent();
        if (gameDir == null) gameDir = Path.of(".");
        this.settings = PearlCalculator.loadSettings(gameDir);

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
            case OFF -> "§7Mirror: OFF";
            case FRONT_BACK -> "§aMirror: Front-Back";
            case LEFT_RIGHT -> "§bMirror: Left-Right";
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

        ButtonGeneric toPlayerBtn = new ButtonGeneric(col1X + 148, y, 58, 18, "To Player");
        this.addButton(toPlayerBtn, (btn, mouse) -> {
            if (this.mc != null && this.mc.player != null) {
                BlockPos playerPos = this.mc.player.blockPosition();
                this.startXField.setTextWrapper(String.valueOf(playerPos.getX()));
                this.startZField.setTextWrapper(String.valueOf(playerPos.getZ()));
                this.saveGuiState();
                this.statusMessage = String.format(Locale.ROOT, "Start set to player: [%d, %d]", playerPos.getX(), playerPos.getZ());
                this.statusIsError = false;
            }
        });
        y += 22;

        // --- Destination Inputs ---
        this.destXField = new GuiTextFieldDouble(col1X + 32, y, 54, 18, this.font);
        this.destXField.setTextWrapper(curDX);
        this.addTextField(this.destXField, null);

        this.destZField = new GuiTextFieldDouble(col1X + 90, y, 54, 18, this.font);
        this.destZField.setTextWrapper(curDZ);
        this.addTextField(this.destZField, null);

        ButtonGeneric toPlayerDestBtn = new ButtonGeneric(col1X + 148, y, 58, 18, "To Player");
        this.addButton(toPlayerDestBtn, (btn, mouse) -> {
            if (this.mc != null && this.mc.player != null) {
                double px = this.mc.player.getX();
                double pz = this.mc.player.getZ();
                this.destXField.setTextWrapper(String.format(Locale.ROOT, "%.1f", px));
                this.destZField.setTextWrapper(String.format(Locale.ROOT, "%.1f", pz));
                this.saveGuiState();
                this.statusMessage = String.format(Locale.ROOT, "Dest set to player: [%.1f, %.1f]", px, pz);
                this.statusIsError = false;
            }
        });
        y += 22;

        // --- Aligner Facing & Mirror ---
        this.alignerFacingBtn = new ButtonGeneric(col1X + 44, y, 50, 18, this.alignerFacing.name);
        this.addButton(this.alignerFacingBtn, (btn, mouse) -> {
            PearlCalculator.Direction[] dirs = PearlCalculator.Direction.values();
            int next = (this.alignerFacing.ordinal() + 1) % dirs.length;
            this.alignerFacing = dirs[next];
            this.alignerFacingBtn.setDisplayString(this.alignerFacing.name);
            this.saveGuiState();
        });

        this.mirrorAlignerBtn = new ButtonGeneric(col1X + 98, y, 108, 18, this.getMirrorBtnLabel());
        this.addButton(this.mirrorAlignerBtn, (btn, mouse) -> {
            this.mirrorMode = this.mirrorMode.next();
            this.mirrorAlignerBtn.setDisplayString(this.getMirrorBtnLabel());
            this.saveGuiState();
        });
        y += 22;

        // --- Max TNT & Ticks ---
        this.maxTntField = new GuiTextFieldInteger(col1X + 46, y, 48, 18, this.font);
        this.maxTntField.setTextWrapper(curMaxTnt);
        this.addTextField(this.maxTntField, null);

        this.maxTicksField = new GuiTextFieldInteger(col1X + 138, y, 48, 18, this.font);
        this.maxTicksField.setTextWrapper(curMaxTicks);
        this.addTextField(this.maxTicksField, null);
        y += 22;

        // --- Max Dist ---
        this.maxDistField = new GuiTextFieldDouble(col1X + 50, y, 54, 18, this.font);
        this.maxDistField.setTextWrapper(curMaxDist);
        this.addTextField(this.maxDistField, null);
        y += 22;

        // --- Calculate Button ---
        ButtonGeneric calcBtn = new ButtonGeneric(col1X, y, 206, 20, "Calculate Solutions");
        this.addButton(calcBtn, (btn, mouse) -> this.runCalculation());

        // --- Results Pagination Buttons (Right Side) ---
        int resultsX = startX + 240;
        int resultsW = 235;
        int resultsY = startY + 30;
        int navY = startY + 160;

        ButtonGeneric prevBtn = new ButtonGeneric(resultsX, navY, 114, 18, "< Prev");
        prevBtn.setEnabled(this.page > 0);
        this.addButton(prevBtn, (btn, mouse) -> {
            if (this.page > 0) {
                this.page--;
                this.initGui();
            }
        });

        int maxPage = Math.max(0, (this.solutions.size() - 1) / RESULTS_PER_PAGE);
        ButtonGeneric nextBtn = new ButtonGeneric(resultsX + 121, navY, 114, 18, "Next >");
        nextBtn.setEnabled(this.page < maxPage);
        this.addButton(nextBtn, (btn, mouse) -> {
            if (this.page < maxPage) {
                this.page++;
                this.initGui();
            }
        });

        // --- Sort Buttons ---
        int sortBtnY = startY + 196;
        String distLabel = (this.sortMode == SortMode.DISTANCE) ? (this.sortAscending ? "§eDist ▲" : "§eDist ▼") : "Dist";
        ButtonGeneric btnDist = new ButtonGeneric(resultsX, sortBtnY, 75, 18, distLabel);
        this.addButton(btnDist, (btn, mouse) -> this.setSortMode(SortMode.DISTANCE));

        String tntLabel = (this.sortMode == SortMode.TNT) ? (this.sortAscending ? "§eTNT ▲" : "§eTNT ▼") : "TNT";
        ButtonGeneric btnTnt = new ButtonGeneric(resultsX + 80, sortBtnY, 75, 18, tntLabel);
        this.addButton(btnTnt, (btn, mouse) -> this.setSortMode(SortMode.TNT));

        String ticksLabel = (this.sortMode == SortMode.TICKS) ? (this.sortAscending ? "§eTicks ▲" : "§eTicks ▼") : "Ticks";
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

            String label = String.format(Locale.ROOT, "#%d: %d TNT (R:%d B:%d) %s %dt (%.2fm)",
                    solIdx + 1, res.getTotalTNT(), res.redTNT, res.blueTNT, res.direction.name, res.tick, res.distance);

            ButtonGeneric rowBtn = new ButtonGeneric(resultsX, rowY, resultsW, 22, label,
                    String.format(Locale.ROOT, "§6Total TNT: §e%d §7(Red: §c%d§7, Blue: §9%d§7)",
                            res.getTotalTNT(), res.redTNT, res.blueTNT),
                    String.format(Locale.ROOT, "§eLanding: §f(%.2f, %.2f, %.2f) §7[%.2fm]",
                            res.worldArrivalPos.x, res.worldArrivalPos.y, res.worldArrivalPos.z, res.distance),
                    String.format(Locale.ROOT, "§6Stopper: §f[%d, %d, %d]",
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
        this.schematicNameField.setHoverTooltip("§eSchematic Name", "§7Name of the generated schematic and subregion metadata");
        this.addTextField(this.schematicNameField, null);

        // --- Bottom Area: Checkboxes & Actions ---
        int bottomY = startY + 246;
        boolean loadMem = (this.checkboxLoadToMemory != null) ? this.checkboxLoadToMemory.isChecked() : lastLoadToMemory;
        boolean createPlace = (this.checkboxCreatePlacement != null) ? this.checkboxCreatePlacement.isChecked() : lastCreatePlacement;

        this.checkboxLoadToMemory = new WidgetCheckBox(startX + 15, bottomY, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, "Load to memory");
        this.checkboxLoadToMemory.setChecked(loadMem, false);
        this.addWidget(this.checkboxLoadToMemory);

        this.checkboxCreatePlacement = new WidgetCheckBox(startX + 125, bottomY, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, "Create placement");
        this.checkboxCreatePlacement.setChecked(createPlace, false);
        this.addWidget(this.checkboxCreatePlacement);

        int btnY = startY + 282;

        ButtonGeneric assembleBtn = new ButtonGeneric(startX + 15, btnY, 110, 20, "Generate Schem");
        assembleBtn.setEnabled(hasSol);
        this.addButton(assembleBtn, (btn, mouse) -> this.generateSchematic());

        ButtonGeneric copyLandingBtn = new ButtonGeneric(startX + 130, btnY, 80, 20, "Copy Land");
        copyLandingBtn.setEnabled(hasSol);
        this.addButton(copyLandingBtn, (btn, mouse) -> {
            if (this.selectedSolutionIndex >= 0 && this.selectedSolutionIndex < this.solutions.size() && this.mc != null) {
                CalculationResult sel = this.solutions.get(this.selectedSolutionIndex);
                String landingStr = String.format(Locale.ROOT, "%.2f %.2f %.2f",
                        sel.worldArrivalPos.x, sel.worldArrivalPos.y, sel.worldArrivalPos.z);
                this.mc.keyboardHandler.setClipboard(landingStr);
                this.statusMessage = String.format(Locale.ROOT, "Copied Landing: (%.2f, %.2f, %.2f)",
                        sel.worldArrivalPos.x, sel.worldArrivalPos.y, sel.worldArrivalPos.z);
                this.statusIsError = false;
            }
        });

        ButtonGeneric copyCatchBtn = new ButtonGeneric(startX + 215, btnY, 72, 20, "Copy Catch");
        copyCatchBtn.setEnabled(hasSol);
        this.addButton(copyCatchBtn, (btn, mouse) -> {
            if (this.selectedSolutionIndex >= 0 && this.selectedSolutionIndex < this.solutions.size() && this.mc != null) {
                CalculationResult sel = this.solutions.get(this.selectedSolutionIndex);
                this.mc.keyboardHandler.setClipboard(String.format(Locale.ROOT, "%d %d %d",
                        sel.catchBlockPos.getX(), sel.catchBlockPos.getY(), sel.catchBlockPos.getZ()));
                this.statusMessage = "Copied Catch: " + sel.catchBlockPos.toShortString();
                this.statusIsError = false;
            }
        });

        ButtonGeneric setblockBtn = new ButtonGeneric(startX + 292, btnY, 92, 20, "Setblock Catch");
        setblockBtn.setEnabled(hasSol && !schematic.tiling.client.util.CatchBlockPlacer.isRunning());
        this.addButton(setblockBtn, (btn, mouse) -> {
            if (this.selectedSolutionIndex >= 0 && this.selectedSolutionIndex < this.solutions.size() && this.mc != null && this.mc.player != null) {
                CalculationResult sel = this.solutions.get(this.selectedSolutionIndex);
                BlockPos cp = sel.catchBlockPos;
                schematic.tiling.client.util.CatchBlockPlacer.startPlacement(cp);
                this.statusMessage = String.format(Locale.ROOT, "Placing catch block at [%d, %d, %d]...", cp.getX(), cp.getY(), cp.getZ());
                this.statusIsError = false;
            }
        });

        ButtonGeneric closeBtn = new ButtonGeneric(startX + dialogWidth - 75, btnY, 60, 20, "Close");
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

            this.sortSolutions();
            this.page = 0;
            this.selectedSolutionIndex = this.solutions.isEmpty() ? -1 : 0;

            if (this.solutions.isEmpty()) {
                this.statusMessage = "No valid solutions found within range.";
                this.statusIsError = true;
            } else {
                this.statusMessage = "Found " + this.solutions.size() + " solutions!";
                this.statusIsError = false;
            }
        } catch (Throwable t) {
            this.statusMessage = "Input error: " + t.getMessage();
            this.statusIsError = true;
        }
        this.saveGuiState();
        this.initGui();
    }

    private void generateSchematic() {
        if (this.selectedSolutionIndex < 0 || this.selectedSolutionIndex >= this.solutions.size()) {
            return;
        }

        CalculationResult sol = this.solutions.get(this.selectedSolutionIndex);
        try {
            String rawName = (this.schematicNameField != null) ? this.schematicNameField.getTextWrapper().trim() : "";
            if (rawName.isEmpty()) {
                String selName = (this.selection != null && this.selection.getName() != null) ? this.selection.getName() : "cannon";
                rawName = String.format(Locale.ROOT, "%s_%s_%dt", selName, sol.direction.name, sol.tick);
            }
            String schemName = FileNameUtils.generateSafeFileName(rawName);

            int sx = Integer.parseInt(this.startXField.getTextWrapper().trim());
            int sy = this.settings.getStartY();
            int sz = Integer.parseInt(this.startZField.getTextWrapper().trim());
            BlockPos cannonStart = new BlockPos(sx, sy, sz);
            BlockPos placementOrigin = ReturnCannonAssembler.getSchematicOrigin(sx, sz, this.alignerFacing.getAlignerRotation(), this.mirrorMode);

            Path gameDir = DataManager.getSchematicsBaseDirectory().getParent();
            if (gameDir == null) gameDir = Path.of(".");

            ReturnCannonAssembler assembler = new ReturnCannonAssembler(gameDir);

            // Assembles cannon with main Cannon subregion + 1-block Catcher subregion
            LitematicaSchematic schem = assembler.assembleCannon(
                    this.alignerFacing.getAlignerRotation(),
                    this.mirrorMode,
                    sol,
                    this.settings,
                    schemName,
                    cannonStart
            );

            this.statusMessage = "Please save schematic to finish generation.";
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
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);
        RenderUtils.drawRect(ctx, 0, 0, this.getScreenWidth(), this.getScreenHeight(), 0x80000000);

        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        RenderUtils.drawOutlinedBox(ctx, startX, startY, dialogWidth, dialogHeight, 0xFF1C1C1C, COLOR_HORIZONTAL_BAR);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        // Title
        this.drawString(ctx, this.title, startX + 15, startY + 12, COLOR_WHITE);

        int col1X = startX + 15;
        int y = startY + 30;

        // Labels matching field Y offsets
        this.drawString(ctx, "Start:", col1X, y + 5, 0xFFAAAAAA);
        y += 22;
        this.drawString(ctx, "Dest:", col1X, y + 5, 0xFFAAAAAA);
        y += 22;
        this.drawString(ctx, "Aligner:", col1X, y + 5, 0xFFAAAAAA);
        y += 22;
        this.drawString(ctx, "MaxTNT:", col1X, y + 5, 0xFFAAAAAA);
        this.drawString(ctx, "Ticks:", col1X + 104, y + 5, 0xFFAAAAAA);
        y += 22;
        this.drawString(ctx, "Max Dist:", col1X, y + 5, 0xFFAAAAAA);
        y += 22; // Skips to Calculate button (startY + 140)
        y += 24; // Skips to Details box (startY + 164)

        // Details of selected solution (Left Side under inputs)
        if (this.selectedSolutionIndex >= 0 && this.selectedSolutionIndex < this.solutions.size()) {
            CalculationResult sel = this.solutions.get(this.selectedSolutionIndex);
            int boxY = y;
            int boxW = 206;
            int boxH = 76;
            RenderUtils.drawOutlinedBox(ctx, col1X, boxY, boxW, boxH, 0xD0202020, 0xFF505050);

            this.drawString(ctx, String.format(Locale.ROOT, "§e§lLanding (Tick %d): §a[%.2fm]", sel.tick, sel.distance),
                    col1X + 6, boxY + 5, 0xFFFFFF55);
            this.drawString(ctx, String.format(Locale.ROOT, "§7Pos: §f%.2f, %.2f, %.2f",
                            sel.worldArrivalPos.x, sel.worldArrivalPos.y, sel.worldArrivalPos.z),
                    col1X + 6, boxY + 16, 0xFFFFFFFF);

            boolean slotAIsRed = ReturnCannonAssembler.isSlotARed(sel.direction, this.settings);
            String redSide = slotAIsRed ? "Left" : "Right";
            String blueSide = slotAIsRed ? "Right" : "Left";

            this.drawString(ctx, String.format(Locale.ROOT, "§6Stopper: §f[%d, %d, %d] §7(T+%d)",
                            sel.catchBlockPos.getX(), sel.catchBlockPos.getY(), sel.catchBlockPos.getZ(), 1),
                    col1X + 6, boxY + 27, 0xFFFFAA00);
            this.drawString(ctx, String.format(Locale.ROOT, "Total TNT: §e%d§r (Red: §c%d§r, Blue: §9%d§r)",
                            sel.getTotalTNT(), sel.redTNT, sel.blueTNT),
                    col1X + 6, boxY + 38, 0xFFDDDDDD);
            this.drawString(ctx, String.format(Locale.ROOT, "Dupers: Red %d (%s), Blue %d (%s)",
                            sel.redDuperSize, redSide, sel.blueDuperSize, blueSide),
                    col1X + 6, boxY + 49, 0xFFDDDDDD);
            this.drawString(ctx, String.format(Locale.ROOT, "Basket Rods: §6%s", sel.direction.name),
                    col1X + 6, boxY + 60, 0xFFFFAA00);
        }

        // Solutions Header (Right Side)
        int resultsX = startX + 240;
        this.drawString(ctx, "Solutions (" + this.solutions.size() + "):", resultsX, startY + 18, COLOR_WHITE);
        this.drawString(ctx, "Sort by:", resultsX, startY + 184, 0xFFAAAAAA);
        this.drawString(ctx, "Schematic Name:", resultsX, startY + 224, 0xFFAAAAAA);

        // Status message placed cleanly above action buttons and below checkboxes
        if (this.statusMessage != null) {
            int msgColor = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            this.drawString(ctx, this.statusMessage, startX + 15, startY + 266, msgColor);
        }
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
            this.title = "Save Return Cannon Schematic";
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
}
