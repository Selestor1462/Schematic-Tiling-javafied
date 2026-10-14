package schematic.tiling.client.gui;

import java.nio.file.Path;
import java.util.*;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetCheckBox;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.cannon.PearlCalculator;
import schematic.tiling.cannon.PearlCalculator.CalculationResult;
import schematic.tiling.cannon.PearlCalculator.CannonSettings;
import schematic.tiling.cannon.ReturnCannonAssembler;
import schematic.tiling.cannon.ReturnCannonAssembler.CannonEntry;
import schematic.tiling.cannon.ReturnCannonAssembler.MirrorMode;

public class GuiReturnCannonDebug extends GuiBase {

    public static boolean debugModeEnabled = false;

    private final GuiReturnCannon parent;
    private final CannonSettings settings;

    private WidgetCheckBox checkboxLoadToMemory;
    private WidgetCheckBox checkboxCreatePlacement;
    private WidgetCheckBox checkboxPowderSnow;

    private static boolean lastLoadToMemory = true;
    private static boolean lastCreatePlacement = true;
    private static boolean lastGeneratePowderSnow = false;

    private String statusMessage = null;
    private boolean statusIsError = false;

    public record PresetSpec(
            String id,
            String label,
            int startX, int startY, int startZ,
            double destX, double destZ,
            PearlCalculator.Direction alignerFacing,
            Rotation alignerRotation,
            MirrorMode mirrorMode,
            String expectedDir
    ) {}

    private static final List<PresetSpec> PRESETS = List.of(
            new PresetSpec("Q1", "Test 1: (-300, 300) -> (-230, 230) [EAST, None, OFF]",
                    -300, 128, 300, -230.0, 230.0,
                    PearlCalculator.Direction.NORTH, Rotation.NONE, MirrorMode.OFF, "EAST"),
            new PresetSpec("Q2", "Test 2: (300, -300) -> (230, -230) [WEST, CW_90, Front-Back]",
                    300, 128, -300, 230.0, -230.0,
                    PearlCalculator.Direction.EAST, Rotation.CLOCKWISE_90, MirrorMode.FRONT_BACK, "WEST"),
            new PresetSpec("Q3", "Test 3: (-300, -300) -> (-230, -230) [SOUTH, CW_180, Left-Right]",
                    -300, 128, -300, -230.0, -230.0,
                    PearlCalculator.Direction.SOUTH, Rotation.CLOCKWISE_180, MirrorMode.LEFT_RIGHT, "SOUTH"),
            new PresetSpec("Q4", "Test 4: (300, 300) -> (230, 230) [NORTH, CCW_90, OFF]",
                    300, 128, 300, 230.0, 230.0,
                    PearlCalculator.Direction.WEST, Rotation.COUNTERCLOCKWISE_90, MirrorMode.OFF, "NORTH"),
            new PresetSpec("Q5", "Test 5: (-500, 100) -> (-470, 25) [NORTH, CW_90, Left-Right]",
                    -500, 128, 100, -470.0, 25.0,
                    PearlCalculator.Direction.EAST, Rotation.CLOCKWISE_90, MirrorMode.LEFT_RIGHT, "NORTH"),
            new PresetSpec("Q6", "Test 6: (500, -100) -> (470, -25) [SOUTH, CW_180, Front-Back]",
                    500, 128, -100, 470.0, -25.0,
                    PearlCalculator.Direction.SOUTH, Rotation.CLOCKWISE_180, MirrorMode.FRONT_BACK, "SOUTH")
    );

    public GuiReturnCannonDebug(GuiReturnCannon parent, CannonSettings settings) {
        this.parent = parent;
        this.settings = settings;
        this.setParent(parent);
        this.title = StringUtils.translate("schematic_tiling.gui.cannon_debug.title");
    }

    @Override
    public void initGui() {
        super.initGui();

        int dialogWidth = 490;
        int dialogHeight = 315;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        int y = startY + 28;

        // --- Batch Generation Buttons ---
        ButtonGeneric btnGen6 = new ButtonGeneric(startX + 15, y, 220, 20, "\u00a7a" + StringUtils.translate("schematic_tiling.gui.cannon_debug.button.gen_all_quadrant"));
        this.addButton(btnGen6, (btn, mouse) -> this.generateAllQuadrantTests());

        ButtonGeneric btnGen4 = new ButtonGeneric(startX + 245, y, 220, 20, "\u00a7e" + StringUtils.translate("schematic_tiling.gui.cannon_debug.button.gen_cardinal_player"));
        this.addButton(btnGen4, (btn, mouse) -> this.generateCardinalTestsAtPlayer());

        y += 28;

        // Preset row buttons
        int presetStartY = startY + 70;
        for (int i = 0; i < PRESETS.size(); i++) {
            PresetSpec spec = PRESETS.get(i);
            int rowY = presetStartY + (i * 24);

            ButtonGeneric loadBtn = new ButtonGeneric(startX + 345, rowY, 58, 20, StringUtils.translate("schematic_tiling.gui.cannon_debug.button.load_gui"));
            this.addButton(loadBtn, (btn, mouse) -> {
                if (this.parent != null) {
                    this.parent.loadPresetAndCalculate(
                            spec.startX, spec.startZ,
                            spec.destX, spec.destZ,
                            spec.alignerFacing, spec.mirrorMode,
                            "cannon_" + spec.id.toLowerCase(Locale.ROOT)
                    );
                    GuiBase.openGui(this.parent);
                } else {
                    GuiReturnCannon gui = new GuiReturnCannon(null, null);
                    gui.loadPresetAndCalculate(
                            spec.startX, spec.startZ,
                            spec.destX, spec.destZ,
                            spec.alignerFacing, spec.mirrorMode,
                            "cannon_" + spec.id.toLowerCase(Locale.ROOT)
                    );
                    GuiBase.openGui(gui);
                }
            });

            ButtonGeneric buildBtn = new ButtonGeneric(startX + 408, rowY, 57, 20, StringUtils.translate("schematic_tiling.gui.cannon_debug.button.build_one", i + 1));
            this.addButton(buildBtn, (btn, mouse) -> this.generateSinglePreset(spec));
        }

        // --- Checkboxes at Bottom ---
        int checkRow1Y = startY + 220;
        this.checkboxLoadToMemory = new WidgetCheckBox(startX + 15, checkRow1Y, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, StringUtils.translate("schematic_tiling.gui.cannon_debug.checkbox.load_memory"));
        this.checkboxLoadToMemory.setChecked(lastLoadToMemory, false);
        this.addWidget(this.checkboxLoadToMemory);

        this.checkboxCreatePlacement = new WidgetCheckBox(startX + 120, checkRow1Y, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, StringUtils.translate("schematic_tiling.gui.cannon_debug.checkbox.placement_origin"));
        this.checkboxCreatePlacement.setChecked(lastCreatePlacement, false);
        this.addWidget(this.checkboxCreatePlacement);

        int checkRow2Y = startY + 238;
        this.checkboxPowderSnow = new WidgetCheckBox(startX + 15, checkRow2Y, Icons.CHECKBOX_UNSELECTED, Icons.CHECKBOX_SELECTED, StringUtils.translate("schematic_tiling.gui.cannon.checkbox.powder_snow"));
        this.checkboxPowderSnow.setChecked(lastGeneratePowderSnow, false);
        this.addWidget(this.checkboxPowderSnow);

        // --- Action Buttons ---
        int btnY = startY + 280;
        ButtonGeneric backBtn = new ButtonGeneric(startX + dialogWidth - 95, btnY, 80, 20, StringUtils.translate("schematic_tiling.gui.cannon_debug.button.back"));
        this.addButton(backBtn, (btn, mouse) -> {
            if (this.parent != null) {
                GuiBase.openGui(this.parent);
            } else {
                GuiBase.openGui(null);
            }
        });
    }

    private void saveCheckboxes() {
        if (this.checkboxLoadToMemory != null) lastLoadToMemory = this.checkboxLoadToMemory.isChecked();
        if (this.checkboxCreatePlacement != null) lastCreatePlacement = this.checkboxCreatePlacement.isChecked();
        if (this.checkboxPowderSnow != null) lastGeneratePowderSnow = this.checkboxPowderSnow.isChecked();
    }

    private void generateAllQuadrantTests() {
        this.saveCheckboxes();
        try {
            Path schemDir = DataManager.getSchematicsBaseDirectory();
            Path gameDir = schemDir.getParent() != null ? schemDir.getParent() : Path.of(".");
            ReturnCannonAssembler assembler = new ReturnCannonAssembler(gameDir);

            List<CannonEntry> entries = new ArrayList<>();
            sendChatMessage("\u00a76=== Generating 6 Quadrant Test Cannons ===");

            for (int i = 0; i < PRESETS.size(); i++) {
                PresetSpec spec = PRESETS.get(i);
                BlockPos startPos = new BlockPos(spec.startX, spec.startY, spec.startZ);

                List<CalculationResult> sols = PearlCalculator.calculateSolutions(
                        startPos, spec.destX, spec.destZ, this.settings, 100, 10.0
                );
                if (sols.isEmpty()) {
                    throw new IllegalStateException("No solution for " + spec.id);
                }

                CalculationResult best = sols.get(0);
                String safeName = String.format(Locale.ROOT, "debug_test_%d_%s", i + 1, spec.id.toLowerCase(Locale.ROOT));

                // Save individual schematic
                assembler.assembleAndSaveCannon(
                        spec.alignerRotation, spec.mirrorMode, best, this.settings, safeName, schemDir, startPos, lastGeneratePowderSnow
                );

                BlockPos leverPos = assembler.getSchematicOrigin(spec.startX, spec.startZ, spec.alignerRotation, spec.mirrorMode);
                sendChatMessage(String.format(Locale.ROOT,
                        "\u00a7e[%d] %s: \u00a7f%s %dt \u00a77(R:%d, B:%d) | \u00a7aLever: [%d, %d, %d] \u00a77| \u00a7cCatch: [%d, %d, %d]",
                        i + 1, spec.id, best.direction.name, best.tick, best.redTNT, best.blueTNT,
                        leverPos.getX(), leverPos.getY(), leverPos.getZ(),
                        best.catchBlockPos.getX(), best.catchBlockPos.getY(), best.catchBlockPos.getZ()
                ));

                entries.add(new CannonEntry(safeName, spec.alignerRotation, spec.mirrorMode, best, startPos, lastGeneratePowderSnow));
            }

            // Assemble combined schematic with all 6 cannons at world coordinates
            String combinedName = "debug_cannons_all";
            LitematicaSchematic combinedSchem = assembler.assembleCombinedCannonSchematic(entries, this.settings, combinedName);
            Path combinedFile = schemDir.resolve(combinedName + ".litematic");
            combinedSchem.writeToFile(schemDir, combinedName, false);

            if (lastLoadToMemory) {
                SchematicHolder.getInstance().addSchematic(combinedSchem, true);
            }
            if (lastCreatePlacement) {
                SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
                SchematicPlacement placement = SchematicPlacement.createFor(
                        combinedSchem, BlockPos.ZERO, combinedName, true, true
                );
                manager.addSchematicPlacement(placement, true);
                manager.setSelectedSchematicPlacement(placement);
            }

            this.statusMessage = "Generated 6 tests + debug_cannons_all.litematic! Coords sent to chat.";
            this.statusIsError = false;
            InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "All 6 Quadrant tests generated successfully!");
        } catch (Throwable t) {
            this.statusMessage = "Error: " + t.getMessage();
            this.statusIsError = true;
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "Generation error: " + t.getMessage());
        }
        this.initGui();
    }

    private void generateCardinalTestsAtPlayer() {
        this.saveCheckboxes();
        try {
            BlockPos playerPos = (this.mc != null && this.mc.player != null)
                    ? this.mc.player.blockPosition() : new BlockPos(0, 128, 0);

            Path schemDir = DataManager.getSchematicsBaseDirectory();
            Path gameDir = schemDir.getParent() != null ? schemDir.getParent() : Path.of(".");
            ReturnCannonAssembler assembler = new ReturnCannonAssembler(gameDir);

            // 4 directions around player at ~80m
            double dist = 80.0;
            List<PresetSpec> cardinalSpecs = List.of(
                    new PresetSpec("NORTH", "North Test (~80m)", playerPos.getX(), 128, playerPos.getZ(),
                            playerPos.getX() + 15.0, playerPos.getZ() - dist,
                            PearlCalculator.Direction.NORTH, Rotation.NONE, MirrorMode.OFF, "NORTH"),
                    new PresetSpec("SOUTH", "South Test (~80m)", playerPos.getX(), 128, playerPos.getZ(),
                            playerPos.getX() - 15.0, playerPos.getZ() + dist,
                            PearlCalculator.Direction.SOUTH, Rotation.CLOCKWISE_180, MirrorMode.OFF, "SOUTH"),
                    new PresetSpec("EAST", "East Test (~80m)", playerPos.getX(), 128, playerPos.getZ(),
                            playerPos.getX() + dist, playerPos.getZ() + 15.0,
                            PearlCalculator.Direction.EAST, Rotation.CLOCKWISE_90, MirrorMode.OFF, "EAST"),
                    new PresetSpec("WEST", "West Test (~80m)", playerPos.getX(), 128, playerPos.getZ(),
                            playerPos.getX() - dist, playerPos.getZ() - 15.0,
                            PearlCalculator.Direction.WEST, Rotation.COUNTERCLOCKWISE_90, MirrorMode.OFF, "WEST")
            );

            List<CannonEntry> entries = new ArrayList<>();
            sendChatMessage("\u00a76=== Generating 4 Cardinal Tests At Player ===");

            for (int i = 0; i < cardinalSpecs.size(); i++) {
                PresetSpec spec = cardinalSpecs.get(i);
                BlockPos startPos = new BlockPos(spec.startX, spec.startY, spec.startZ);

                List<CalculationResult> sols = PearlCalculator.calculateSolutions(
                        startPos, spec.destX, spec.destZ, this.settings, 100, 10.0
                );
                if (sols.isEmpty()) continue;

                CalculationResult best = sols.get(0);
                String safeName = String.format(Locale.ROOT, "cardinal_test_%s", spec.id.toLowerCase(Locale.ROOT));

                assembler.assembleAndSaveCannon(
                        spec.alignerRotation, spec.mirrorMode, best, this.settings, safeName, schemDir, startPos, lastGeneratePowderSnow
                );

                BlockPos leverPos = assembler.getSchematicOrigin(spec.startX, spec.startZ, spec.alignerRotation, spec.mirrorMode);
                sendChatMessage(String.format(Locale.ROOT,
                        "\u00a7e[%s]: \u00a7f%s %dt \u00a77(R:%d, B:%d) | \u00a7aLever: [%d, %d, %d] \u00a77| \u00a7cCatch: [%d, %d, %d]",
                        spec.id, best.direction.name, best.tick, best.redTNT, best.blueTNT,
                        leverPos.getX(), leverPos.getY(), leverPos.getZ(),
                        best.catchBlockPos.getX(), best.catchBlockPos.getY(), best.catchBlockPos.getZ()
                ));

                entries.add(new CannonEntry(safeName, spec.alignerRotation, spec.mirrorMode, best, startPos, lastGeneratePowderSnow));
            }

            String combinedName = "cardinal_tests_all";
            LitematicaSchematic combinedSchem = assembler.assembleCombinedCannonSchematic(entries, this.settings, combinedName);
            Path combinedFile = schemDir.resolve(combinedName + ".litematic");
            combinedSchem.writeToFile(schemDir, combinedName, false);
            if (lastLoadToMemory) {
                SchematicHolder.getInstance().addSchematic(combinedSchem, true);
            }
            if (lastCreatePlacement) {
                SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
                SchematicPlacement placement = SchematicPlacement.createFor(
                        combinedSchem, BlockPos.ZERO, combinedName, true, true
                );
                manager.addSchematicPlacement(placement, true);
                manager.setSelectedSchematicPlacement(placement);
            }

            this.statusMessage = "Generated 4 cardinal tests + cardinal_tests_all.litematic!";
            this.statusIsError = false;
            InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "4 Cardinal tests generated!");
        } catch (Throwable t) {
            this.statusMessage = "Error: " + t.getMessage();
            this.statusIsError = true;
            InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "Generation error: " + t.getMessage());
        }
        this.initGui();
    }

    private void generateSinglePreset(PresetSpec spec) {
        this.saveCheckboxes();
        try {
            Path schemDir = DataManager.getSchematicsBaseDirectory();
            Path gameDir = schemDir.getParent() != null ? schemDir.getParent() : Path.of(".");
            ReturnCannonAssembler assembler = new ReturnCannonAssembler(gameDir);

            BlockPos startPos = new BlockPos(spec.startX, spec.startY, spec.startZ);
            List<CalculationResult> sols = PearlCalculator.calculateSolutions(
                    startPos, spec.destX, spec.destZ, this.settings, 100, 10.0
            );
            if (sols.isEmpty()) {
                throw new IllegalStateException("No solution for " + spec.id);
            }

            CalculationResult best = sols.get(0);
            String safeName = "debug_test_" + spec.id.toLowerCase(Locale.ROOT);

            LitematicaSchematic schem = assembler.assembleCannon(
                    spec.alignerRotation, spec.mirrorMode, best, this.settings, safeName, startPos, lastGeneratePowderSnow
            );
            schem.writeToFile(schemDir, safeName, false);

            BlockPos leverPos = assembler.getSchematicOrigin(spec.startX, spec.startZ, spec.alignerRotation, spec.mirrorMode);
            if (lastLoadToMemory) {
                SchematicHolder.getInstance().addSchematic(schem, true);
            }
            if (lastCreatePlacement) {
                SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
                SchematicPlacement placement = SchematicPlacement.createFor(
                        schem, leverPos, safeName, true, true
                );
                manager.addSchematicPlacement(placement, true);
                manager.setSelectedSchematicPlacement(placement);
            }

            sendChatMessage(String.format(Locale.ROOT,
                    "\u00a7a[Generated %s]: \u00a7f%s %dt \u00a77(R:%d, B:%d) | \u00a7aLever: [%d, %d, %d] \u00a77| \u00a7cCatch: [%d, %d, %d]",
                    spec.id, best.direction.name, best.tick, best.redTNT, best.blueTNT,
                    leverPos.getX(), leverPos.getY(), leverPos.getZ(),
                    best.catchBlockPos.getX(), best.catchBlockPos.getY(), best.catchBlockPos.getZ()
            ));

            this.statusMessage = String.format(Locale.ROOT, "Saved %s! Lever: [%d, %d, %d], Catch: [%d, %d, %d]",
                    safeName, leverPos.getX(), leverPos.getY(), leverPos.getZ(),
                    best.catchBlockPos.getX(), best.catchBlockPos.getY(), best.catchBlockPos.getZ());
            this.statusIsError = false;
        } catch (Throwable t) {
            this.statusMessage = "Error: " + t.getMessage();
            this.statusIsError = true;
        }
        this.initGui();
    }

    private void sendChatMessage(String msg) {
        if (this.mc != null && this.mc.player != null) {
            this.mc.player.displayClientMessage(Component.literal(msg), false);
        }
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);
        RenderUtils.drawRect(ctx, 0, 0, this.getScreenWidth(), this.getScreenHeight(), 0x80000000);

        int dialogWidth = 490;
        int dialogHeight = 315;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        RenderUtils.drawOutlinedBox(ctx, startX, startY, dialogWidth, dialogHeight, 0xE0101010, 0xFF404040);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int dialogWidth = 490;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - 315) / 2;

        this.drawString(ctx, "\u00a76\u00a7lReturn Cannon Debug & Test Generator", startX + 15, startY + 10, COLOR_WHITE);

        // Header for presets
        this.drawString(ctx, "\u00a77Preset Test Configurations (Calculated & Built via Java Engine):", startX + 15, startY + 56, 0xFFAAAAAA);

        // Preset rows
        int presetStartY = startY + 70;
        for (int i = 0; i < PRESETS.size(); i++) {
            PresetSpec spec = PRESETS.get(i);
            int rowY = presetStartY + (i * 24);
            int boxColor = (i % 2 == 0) ? 0x40202020 : 0x20303030;
            RenderUtils.drawOutlinedBox(ctx, startX + 15, rowY, 325, 20, boxColor, 0x40505050);

            this.drawString(ctx, spec.label, startX + 20, rowY + 6, 0xFFE0E0E0);
        }

        // Status message
        if (this.statusMessage != null) {
            int msgColor = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            this.drawString(ctx, this.statusMessage, startX + 15, startY + 262, msgColor);
        }
    }
}
