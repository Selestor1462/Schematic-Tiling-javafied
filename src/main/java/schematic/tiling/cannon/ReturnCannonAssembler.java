package schematic.tiling.cannon;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.util.FileType;
import fi.dy.masa.malilib.util.FileNameUtils;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import fi.dy.masa.malilib.util.data.tag.ListData;
import fi.dy.masa.malilib.util.data.tag.LongArrayData;
import fi.dy.masa.malilib.util.data.tag.util.DataFileUtils;
import fi.dy.masa.malilib.util.data.tag.util.DataTypeUtils;

import schematic.tiling.cannon.PearlCalculator.CalculationResult;
import schematic.tiling.core.NormalizedRegion;
import schematic.tiling.core.SchematicTilerUtil.GenerationResult;

public class ReturnCannonAssembler {

    private final Path gameDirectory;

    public ReturnCannonAssembler(Path gameDirectory) {
        this.gameDirectory = gameDirectory;
    }

    /**
     * Resolves and loads a schematic file, checking game directory, schematics folder,
     * or extracting from bundled classpath resources if not found.
     */
    public LitematicaSchematic loadComponentSchematic(String relativePath) throws Exception {
        // Candidate paths on disk
        List<Path> candidates = List.of(
                this.gameDirectory.resolve("return_cannon").resolve(relativePath),
                this.gameDirectory.resolve("schematics").resolve("return_cannon").resolve(relativePath),
                this.gameDirectory.resolve("projects").resolve("return_cannon").resolve(relativePath),
                Path.of("return_cannon").resolve(relativePath)
        );

        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                LitematicaSchematic schem = loadSchematicSilently(p);
                if (schem != null) {
                    return schem;
                }
                return LitematicaSchematic.createFromFile(p.getParent(), p.getFileName().toString());
            }
        }

        // Try bundled resource
        String resPath = "/return_cannon/" + relativePath.replace('\\', '/');
        InputStream is = getClass().getResourceAsStream(resPath);
        if (is != null) {
            Path cacheDir = this.gameDirectory.resolve("cache").resolve("return_cannon");
            Files.createDirectories(cacheDir);
            Path tempFile = cacheDir.resolve(Path.of(relativePath).getFileName().toString());
            try (is; OutputStream os = Files.newOutputStream(tempFile)) {
                is.transferTo(os);
            }
            LitematicaSchematic schem = loadSchematicSilently(tempFile);
            if (schem != null) {
                return schem;
            }
            return LitematicaSchematic.createFromFile(tempFile.getParent(), tempFile.getFileName().toString());
        }

        throw new IllegalStateException("Could not find component schematic: " + relativePath);
    }

    private LitematicaSchematic loadSchematicSilently(Path path) {
        try {
            CompoundData data = DataFileUtils.readCompoundDataFromNbtFile(path);
            if (data != null) {
                data.putInt("MinecraftDataVersion", LitematicaSchematic.MINECRAFT_DATA_VERSION);
                return new LitematicaSchematic(path, data, FileType.LITEMATICA_SCHEMATIC);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static class BlockEntry {
        public BlockPos pos;
        public BlockState state;

        public BlockEntry(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }

    /**
     * Extracts all blocks from a schematic into a list of BlockEntry using container-local coordinates.
     */
    public List<BlockEntry> extractBlocks(LitematicaSchematic schematic) {
        List<BlockEntry> result = new ArrayList<>();
        Map<String, BlockPos> areaSizes = schematic.getAreaSizes();

        for (String regionName : schematic.getAreas().keySet()) {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(regionName);
            BlockPos regSize = areaSizes.get(regionName);

            if (container == null || regSize == null) continue;

            int sx = Math.abs(regSize.getX());
            int sy = Math.abs(regSize.getY());
            int sz = Math.abs(regSize.getZ());

            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        BlockState state = container.get(x, y, z);
                        if (!state.isAir()) {
                            result.add(new BlockEntry(new BlockPos(x, y, z), state));
                        }
                    }
                }
            }
        }
        return result;
    }

    public static class TileEntityEntry {
        public BlockPos pos;
        public CompoundData nbt;

        public TileEntityEntry(BlockPos pos, CompoundData nbt) {
            this.pos = pos;
            this.nbt = nbt;
        }
    }

    public List<TileEntityEntry> extractTileEntities(LitematicaSchematic schematic) {
        List<TileEntityEntry> result = new ArrayList<>();
        for (String regionName : schematic.getAreas().keySet()) {
            Map<BlockPos, CompoundData> teMap = schematic.getBlockEntityMapForRegion(regionName);
            if (teMap != null) {
                for (Map.Entry<BlockPos, CompoundData> e : teMap.entrySet()) {
                    result.add(new TileEntityEntry(e.getKey(), e.getValue().copy()));
                }
            }
        }
        return result;
    }

    /**
     * Rotates 2D coordinates around (0, 0) according to Minecraft's Rotation enum.
     */
    public static double[] rotateCoords(double x, double z, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new double[]{x, z};
            case CLOCKWISE_90 -> new double[]{-z, x};
            case CLOCKWISE_180 -> new double[]{-x, -z};
            case COUNTERCLOCKWISE_90 -> new double[]{z, -x};
        };
    }

    public static int rotateLayoutBits(int bits, Rotation rotation) {
        int out = 0;
        for (PearlCalculator.Direction dir : PearlCalculator.Direction.values()) {
            if ((bits & dir.bit) != 0) {
                PearlCalculator.Direction rotDir = switch (rotation) {
                    case NONE -> dir;
                    case CLOCKWISE_90 -> switch (dir) {
                        case NORTH -> PearlCalculator.Direction.EAST;
                        case EAST -> PearlCalculator.Direction.SOUTH;
                        case SOUTH -> PearlCalculator.Direction.WEST;
                        case WEST -> PearlCalculator.Direction.NORTH;
                    };
                    case CLOCKWISE_180 -> dir.invert();
                    case COUNTERCLOCKWISE_90 -> switch (dir) {
                        case NORTH -> PearlCalculator.Direction.WEST;
                        case WEST -> PearlCalculator.Direction.SOUTH;
                        case SOUTH -> PearlCalculator.Direction.EAST;
                        case EAST -> PearlCalculator.Direction.NORTH;
                    };
                };
                out |= rotDir.bit;
            }
        }
        return out;
    }

    public enum MirrorMode {
        OFF("OFF", false, false),
        FRONT_BACK("Front-Back", true, false),
        LEFT_RIGHT("Left-Right", false, true);

        public final String label;
        public final boolean flipX;
        public final boolean flipZ;

        MirrorMode(String label, boolean flipX, boolean flipZ) {
            this.label = label;
            this.flipX = flipX;
            this.flipZ = flipZ;
        }

        public MirrorMode next() {
            MirrorMode[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }

        public Mirror toVanillaMirror() {
            return switch (this) {
                case OFF -> Mirror.NONE;
                case FRONT_BACK -> Mirror.FRONT_BACK;
                case LEFT_RIGHT -> Mirror.LEFT_RIGHT;
            };
        }

        public static MirrorMode fromVanillaMirror(Mirror mirror) {
            if (mirror == null) return OFF;
            return switch (mirror) {
                case NONE -> OFF;
                case FRONT_BACK -> FRONT_BACK;
                case LEFT_RIGHT -> LEFT_RIGHT;
            };
        }
    }

    /**
     * Determines whether physical Slot A (unmirrored at bx = 4 + dx) should receive
     * the Red duper (true) or the Blue duper (false) based on the flight direction.
     * Slot A is Left of the basket, Slot B is Right of the basket.
     */
    public static boolean isSlotARed(PearlCalculator.Direction flightDirection, PearlCalculator.CannonSettings settings) {
        return switch (flightDirection) {
            case NORTH, EAST -> true;  // Slot A = Red (Left),  Slot B = Blue (Right)
            case SOUTH, WEST -> false; // Slot A = Blue (Left), Slot B = Red (Right)
        };
    }

    public static Block getWaxedLightningRodBlock() {
        return Blocks.LIGHTNING_ROD.waxed().unaffected();
    }

    public static BlockState swapWaxedLightningRodIfNeeded(BlockState state) {
        if (state.is(Blocks.LIGHTNING_ROD.weathering().unaffected())) {
            Block targetRod = getWaxedLightningRodBlock();
            return targetRod.withPropertiesOf(state);
        }
        return state;
    }

    /**
     * Resolves double-chest connections dynamically based on actual adjacent neighbors in space.
     * Ensures CHEST_TYPE is always correctly aligned with Minecraft's connected direction rule:
     * partner at facing.getClockWise() -> LEFT, partner at facing.getCounterClockWise() -> RIGHT.
     */
    public static void fixDoubleChests(Map<BlockPos, BlockState> blocks) {
        for (Map.Entry<BlockPos, BlockState> e : new ArrayList<>(blocks.entrySet())) {
            BlockState state = e.getValue();
            if (state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                BlockPos pos = e.getKey();
                BlockPos cwPos = pos.relative(facing.getClockWise());
                BlockPos ccwPos = pos.relative(facing.getCounterClockWise());

                BlockState cwState = blocks.get(cwPos);
                BlockState ccwState = blocks.get(ccwPos);

                if (cwState != null && cwState.getBlock() == state.getBlock()
                        && cwState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                        && cwState.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing) {
                    blocks.put(pos, state.setValue(BlockStateProperties.CHEST_TYPE, ChestType.LEFT));
                } else if (ccwState != null && ccwState.getBlock() == state.getBlock()
                        && ccwState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                        && ccwState.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing) {
                    blocks.put(pos, state.setValue(BlockStateProperties.CHEST_TYPE, ChestType.RIGHT));
                } else {
                    blocks.put(pos, state.setValue(BlockStateProperties.CHEST_TYPE, ChestType.SINGLE));
                }
            }
        }
    }

    /**
     * Calculates the world placement origin (under the aligner lever at y=128)
     * given the pearl position (intersection of the 4 iron bars) and aligner rotation.
     */
    public static BlockPos getSchematicOrigin(int pearlX, int pearlZ, Rotation alignerRotation) {
        return getSchematicOrigin(pearlX, pearlZ, alignerRotation, MirrorMode.OFF);
    }

    public static BlockPos getSchematicOrigin(int pearlX, int pearlZ, Rotation alignerRotation, boolean mirrorAligner) {
        return getSchematicOrigin(pearlX, pearlZ, alignerRotation, mirrorAligner ? MirrorMode.FRONT_BACK : MirrorMode.OFF);
    }

    public static BlockPos getSchematicOrigin(int pearlX, int pearlZ, Rotation alignerRotation, MirrorMode mirrorMode) {
        double ldx = -0.5;
        double ldz = 0.5;
        if (mirrorMode != null) {
            if (mirrorMode.flipX) ldx = -ldx;
            if (mirrorMode.flipZ) ldz = -ldz;
        }
        double[] rot = rotateCoords(ldx, ldz, alignerRotation);
        int offX = (int) Math.round(0.5 + rot[0]) - 1;
        int offZ = (int) Math.round(0.5 + rot[1]) - 1;
        return new BlockPos(pearlX + offX, 128, pearlZ + offZ);
    }

    public static BlockPos getPearlPosFromPlacementOrigin(BlockPos placementOrigin, Rotation alignerRotation, MirrorMode mirrorMode) {
        if (placementOrigin == null) return null;
        double ldx = -0.5;
        double ldz = 0.5;
        if (mirrorMode != null) {
            if (mirrorMode.flipX) ldx = -ldx;
            if (mirrorMode.flipZ) ldz = -ldz;
        }
        double[] rot = rotateCoords(ldx, ldz, alignerRotation);
        int offX = (int) Math.round(0.5 + rot[0]) - 1;
        int offZ = (int) Math.round(0.5 + rot[1]) - 1;
        return new BlockPos(placementOrigin.getX() - offX, placementOrigin.getY(), placementOrigin.getZ() - offZ);
    }

    /**
     * Calculates the offset from the aligner schematic placement origin to the pearl position
     * (the center intersection between the 4 iron bars).
     */
    public static int[] getAlignerPearlOffset(Rotation alignerRotation, MirrorMode mirrorMode) {
        boolean flipX = (mirrorMode != null && mirrorMode.flipX);
        boolean flipZ = (mirrorMode != null && mirrorMode.flipZ);
        double x = flipX ? 1.0 : 0.0;
        double z = flipZ ? 1.0 : 0.0;
        double rx, rz;
        switch (alignerRotation != null ? alignerRotation : Rotation.NONE) {
            case CLOCKWISE_90 -> {
                rx = 1.0 - z;
                rz = x;
            }
            case CLOCKWISE_180 -> {
                rx = 1.0 - x;
                rz = 1.0 - z;
            }
            case COUNTERCLOCKWISE_90 -> {
                rx = z;
                rz = 1.0 - x;
            }
            default -> {
                rx = x;
                rz = z;
            }
        }
        return new int[]{(int) Math.round(rx), (int) Math.round(rz)};
    }

    public static BlockPos getAlignerPlacementOrigin(int pearlX, int pearlZ, Rotation alignerRotation, MirrorMode mirrorMode) {
        int[] off = getAlignerPearlOffset(alignerRotation, mirrorMode);
        return new BlockPos(pearlX - off[0], 128, pearlZ - off[1]);
    }

    public static BlockPos getPearlPosFromAlignerPlacement(BlockPos placementOrigin, Rotation alignerRotation, MirrorMode mirrorMode) {
        if (placementOrigin == null) return null;
        int[] off = getAlignerPearlOffset(alignerRotation, mirrorMode);
        return new BlockPos(placementOrigin.getX() + off[0], placementOrigin.getY(), placementOrigin.getZ() + off[1]);
    }

    /**
     * Replaces excess TNT blocks with red concrete, keeping TNT packed in full rows
     * plus at most one partial row (closest to the duper output / center) in row x = 1
     * rather than spreading missing TNT across multiple rows.
     */
    public static void replaceExcessTNT(List<BlockEntry> blocks, int excessCount) {
        if (excessCount <= 0) return;

        NavigableMap<Integer, List<BlockEntry>> rowsByX = new TreeMap<>();
        for (BlockEntry b : blocks) {
            if (b.state.is(Blocks.TNT)) {
                rowsByX.computeIfAbsent(b.pos.getX(), k -> new ArrayList<>()).add(b);
            }
        }

        if (rowsByX.isEmpty()) return;

        BlockState redConcrete = Blocks.CONCRETE.red().defaultBlockState();
        int remainingToReplace = excessCount;

        // Iterate rows from max X down towards min X (closest to trigger/basket first)
        for (int rowX : rowsByX.descendingKeySet()) {
            List<BlockEntry> targetRow = rowsByX.get(rowX);

            // Sort by Z descending (highest Z first: 11, 10, 9, ...)
            targetRow.sort((a, b) -> Integer.compare(b.pos.getZ(), a.pos.getZ()));

            int toReplace = Math.min(remainingToReplace, targetRow.size());
            for (int i = 0; i < toReplace; i++) {
                targetRow.get(i).state = redConcrete;
            }
            remainingToReplace -= toReplace;
            if (remainingToReplace <= 0) {
                break;
            }
        }
    }

    /**
     * Generates a Manhattan redstone bridge on plane y=42 from aligner's redstone block
     * to basket's sticky piston, avoiding the 4 iron bars.
     * Support blocks are placed at y=41 (first block touching aligner is gray glazed terracotta,
     * remaining are black stained glass). Redstone wire is placed at y=42.
     */
    public static List<BlockEntry> generateRedstoneBridge(
            BlockPos alignerRS,
            BlockPos basketPiston,
            Set<BlockPos> existingBlocks
    ) {
        List<BlockEntry> bridge = new ArrayList<>();
        int ySupport = 41;
        int yWire = 42;

        Set<String> forbidden = Set.of("0:0", "1:0", "0:1", "1:1");

        int[] dx = {0, 0, 1, -1};
        int[] dz = {1, -1, 0, 0};

        List<BlockPos> starts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int sx = alignerRS.getX() + dx[i];
            int sz = alignerRS.getZ() + dz[i];
            String key = sx + ":" + sz;
            BlockPos sup = new BlockPos(sx, ySupport, sz);
            BlockPos wire = new BlockPos(sx, yWire, sz);
            if (!forbidden.contains(key) && !existingBlocks.contains(sup) && !existingBlocks.contains(wire)) {
                starts.add(sup);
            }
        }

        Set<String> targetCoords = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            int tx = basketPiston.getX() + dx[i];
            int tz = basketPiston.getZ() + dz[i];
            String key = tx + ":" + tz;
            if (!forbidden.contains(key)) {
                targetCoords.add(key);
            }
        }

        Queue<List<BlockPos>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        for (BlockPos s : starts) {
            List<BlockPos> path = new ArrayList<>();
            path.add(s);
            queue.add(path);
            visited.add(s.getX() + ":" + s.getZ());
        }

        List<BlockPos> bestPath = null;
        while (!queue.isEmpty()) {
            List<BlockPos> path = queue.poll();
            BlockPos curr = path.get(path.size() - 1);

            if (targetCoords.contains(curr.getX() + ":" + curr.getZ())) {
                bestPath = path;
                break;
            }

            if (path.size() >= 15) continue;

            for (int i = 0; i < 4; i++) {
                int nx = curr.getX() + dx[i];
                int nz = curr.getZ() + dz[i];
                String key = nx + ":" + nz;

                if (forbidden.contains(key) || visited.contains(key)) continue;

                BlockPos nextSupport = new BlockPos(nx, ySupport, nz);
                BlockPos nextWire = new BlockPos(nx, yWire, nz);
                if (existingBlocks.contains(nextSupport) || existingBlocks.contains(nextWire)) continue;

                visited.add(key);
                List<BlockPos> nextPath = new ArrayList<>(path);
                nextPath.add(nextSupport);
                queue.add(nextPath);
            }
        }

        if (bestPath != null) {
            int pathLen = bestPath.size();

            for (int i = 0; i < pathLen; i++) {
                BlockPos supPos = bestPath.get(i);
                BlockPos wirePos = supPos.above();

                boolean isLastToPiston = (i == pathLen - 1);
                boolean pointsIntoPiston = false;

                if (isLastToPiston) {
                    BlockPos prevWirePos = (i > 0) ? bestPath.get(i - 1).above() : alignerRS;
                    int fromPrevX = wirePos.getX() - prevWirePos.getX();
                    int fromPrevZ = wirePos.getZ() - prevWirePos.getZ();
                    int toPistonX = basketPiston.getX() - wirePos.getX();
                    int toPistonZ = basketPiston.getZ() - wirePos.getZ();
                    pointsIntoPiston = (fromPrevX == toPistonX && fromPrevZ == toPistonZ);
                }

                if (isLastToPiston && !pointsIntoPiston) {
                    // Piston is to the side (wire doesn't directly point into piston):
                    // Swap dust into full block with leaves on top to power piston and avoid sticking to slime
                    BlockState supState = (i == 0)
                            ? Blocks.GLAZED_TERRACOTTA.gray().defaultBlockState()
                            : Blocks.STAINED_GLASS.black().defaultBlockState();
                    bridge.add(new BlockEntry(supPos, supState));
                    bridge.add(new BlockEntry(wirePos, Blocks.CONCRETE.gray().defaultBlockState()));
                    bridge.add(new BlockEntry(wirePos.above(), Blocks.AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true)));
                } else {
                    BlockState supState = (i == 0)
                            ? Blocks.GLAZED_TERRACOTTA.gray().defaultBlockState()
                            : Blocks.STAINED_GLASS.black().defaultBlockState();

                    bridge.add(new BlockEntry(supPos, supState));

                    BlockPos prevWire = (i > 0) ? bestPath.get(i - 1).above() : null;
                    BlockPos nextWire = (i < pathLen - 1) ? bestPath.get(i + 1).above() : null;

                    BlockState wireState = Blocks.REDSTONE_WIRE.defaultBlockState();
                    Set<BlockPos> connectedTargets = new HashSet<>();
                    if (prevWire != null) connectedTargets.add(prevWire);
                    if (nextWire != null) connectedTargets.add(nextWire);
                    if (alignerRS != null) connectedTargets.add(alignerRS);
                    if (isLastToPiston) connectedTargets.add(basketPiston);

                    for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                        BlockPos neighbor = wirePos.relative(dir);
                        if (connectedTargets.contains(neighbor)) {
                            wireState = wireState.setValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(dir), RedstoneSide.SIDE);
                        } else {
                            wireState = wireState.setValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(dir), RedstoneSide.NONE);
                        }
                    }
                    wireState = wireState.setValue(RedStoneWireBlock.POWER, Math.max(0, 15 - i));

                    bridge.add(new BlockEntry(wirePos, wireState));
                }
            }
        }

        return bridge;
    }

    public GenerationResult assembleAndSaveCannon(
            Rotation alignerRotation,
            CalculationResult calcResult,
            String schematicName,
            Path outputDirectory
    ) throws Exception {
        return assembleAndSaveCannon(alignerRotation, calcResult, null, schematicName, outputDirectory);
    }

    /**
     * Assembles the complete Return Cannon schematic in memory,
     * containing both the "Cannon" subregion and the 1-block "Catcher" subregion.
     */
    public LitematicaSchematic assembleCannon(
            Rotation alignerRotation,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            BlockPos cannonStartPos
    ) throws Exception {
        return assembleCannon(alignerRotation, MirrorMode.OFF, calcResult, settings, schematicName, cannonStartPos);
    }

    public LitematicaSchematic assembleCannon(
            Rotation alignerRotation,
            boolean mirrorAligner,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            BlockPos cannonStartPos
    ) throws Exception {
        return assembleCannon(alignerRotation, mirrorAligner ? MirrorMode.FRONT_BACK : MirrorMode.OFF, calcResult, settings, schematicName, cannonStartPos);
    }

    public static BlockPos getLandingPos(CalculationResult calcResult, BlockPos cannonStartPos) {
        int landingX = (int) Math.floor(calcResult.worldArrivalPos.x);
        int landingZ = (int) Math.floor(calcResult.worldArrivalPos.z);
        int landingY = (cannonStartPos != null && cannonStartPos.getY() < 150) ? cannonStartPos.getY() : 128;
        return new BlockPos(landingX, landingY, landingZ);
    }

    public CompoundData assembleCannonNbt(
            Rotation alignerRotation,
            MirrorMode mirrorMode,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            BlockPos cannonStartPos
    ) throws Exception {
        return assembleCannonNbt(alignerRotation, mirrorMode, calcResult, settings, schematicName, cannonStartPos, false);
    }

    public CompoundData assembleCannonNbt(
            Rotation alignerRotation,
            MirrorMode mirrorMode,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            BlockPos cannonStartPos,
            boolean generatePowderSnowLanding
    ) throws Exception {
        return assembleCannonNbt(alignerRotation, mirrorMode, calcResult, settings, schematicName, cannonStartPos, generatePowderSnowLanding, null);
    }

    public CompoundData assembleCannonNbt(
            Rotation alignerRotation,
            MirrorMode mirrorMode,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            BlockPos cannonStartPos,
            boolean generatePowderSnowLanding,
            CannonBlockPalette blockPalette
    ) throws Exception {
        if (mirrorMode == null) mirrorMode = MirrorMode.OFF;

        // 1. Load Aligner
        LitematicaSchematic alignerSchem = loadComponentSchematic("aligner.litematic");
        List<BlockEntry> alignerBlocks = extractBlocks(alignerSchem);
        List<TileEntityEntry> alignerTEs = extractTileEntities(alignerSchem);

        // In aligner.litematic: iron bars center at (1.5, 2.5), RS block at (0, 42, 1)
        List<BlockEntry> rotatedAligner = new ArrayList<>();
        for (BlockEntry b : alignerBlocks) {
            double dx = b.pos.getX() - 1.5;
            double dz = b.pos.getZ() - 2.5;
            if (mirrorMode.flipX) {
                dx = -dx;
            }
            if (mirrorMode.flipZ) {
                dz = -dz;
            }
            double[] rot = rotateCoords(dx, dz, alignerRotation);
            int wx = (int) Math.round(0.5 + rot[0]);
            int wy = b.pos.getY();
            int wz = (int) Math.round(0.5 + rot[1]);
            BlockState state = b.state;
            if (mirrorMode.flipX) {
                state = state.mirror(Mirror.FRONT_BACK);
            }
            if (mirrorMode.flipZ) {
                state = state.mirror(Mirror.LEFT_RIGHT);
            }
            state = state.rotate(alignerRotation);
            rotatedAligner.add(new BlockEntry(new BlockPos(wx, wy, wz), state));
        }

        List<TileEntityEntry> rotatedAlignerTEs = new ArrayList<>();
        for (TileEntityEntry te : alignerTEs) {
            double dx = te.pos.getX() - 1.5;
            double dz = te.pos.getZ() - 2.5;
            if (mirrorMode.flipX) {
                dx = -dx;
            }
            if (mirrorMode.flipZ) {
                dz = -dz;
            }
            double[] rot = rotateCoords(dx, dz, alignerRotation);
            int wx = (int) Math.round(0.5 + rot[0]);
            int wy = te.pos.getY();
            int wz = (int) Math.round(0.5 + rot[1]);
            rotatedAlignerTEs.add(new TileEntityEntry(new BlockPos(wx, wy, wz), te.nbt));
        }

        double arDx = mirrorMode.flipX ? 1.5 : -1.5;
        double arDz = mirrorMode.flipZ ? 1.5 : -1.5;
        double[] arRot = rotateCoords(arDx, arDz, alignerRotation);
        BlockPos rotatedAlignerRS = new BlockPos((int) Math.round(0.5 + arRot[0]), 42, (int) Math.round(0.5 + arRot[1]));

        // 2. Load Basket
        LitematicaSchematic basketSchem = loadComponentSchematic("basket.litematic");
        List<BlockEntry> basketBlocks = extractBlocks(basketSchem);
        List<TileEntityEntry> basketTEs = extractTileEntities(basketSchem);

        // In basket.litematic: iron bars at y=1 (center 3.5, 0.5), sticky piston at (4, 0, 5), top RS at (4, 81, 4)
        // Basket is shifted by dy = +42 so y=1 aligns with aligner y=43
        Rotation basketRotation = calcResult.direction.getBasketRotation();
        List<BlockEntry> rotatedBasket = new ArrayList<>();
        for (BlockEntry b : basketBlocks) {
            double[] rot = rotateCoords(b.pos.getX() - 3.5, b.pos.getZ() - 0.5, basketRotation);
            int wx = (int) Math.round(0.5 + rot[0]);
            int wy = b.pos.getY() + 42;
            int wz = (int) Math.round(0.5 + rot[1]);
            rotatedBasket.add(new BlockEntry(new BlockPos(wx, wy, wz), swapWaxedLightningRodIfNeeded(b.state.rotate(basketRotation))));
        }

        List<TileEntityEntry> rotatedBasketTEs = new ArrayList<>();
        for (TileEntityEntry te : basketTEs) {
            double[] rot = rotateCoords(te.pos.getX() - 3.5, te.pos.getZ() - 0.5, basketRotation);
            int wx = (int) Math.round(0.5 + rot[0]);
            int wy = te.pos.getY() + 42;
            int wz = (int) Math.round(0.5 + rot[1]);
            rotatedBasketTEs.add(new TileEntityEntry(new BlockPos(wx, wy, wz), te.nbt));
        }

        double[] bpRot = rotateCoords(4 - 3.5, 5 - 0.5, basketRotation);
        BlockPos rotatedBasketPiston = new BlockPos((int) Math.round(0.5 + bpRot[0]), 42, (int) Math.round(0.5 + bpRot[1]));

        // Extend sticky piston and add sticky piston head above it at y=43
        for (BlockEntry b : rotatedBasket) {
            if (b.pos.equals(rotatedBasketPiston)) {
                b.state = Blocks.STICKY_PISTON.defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.UP)
                        .setValue(BlockStateProperties.EXTENDED, true);
            }
        }
        rotatedBasket.add(new BlockEntry(
                rotatedBasketPiston.above(),
                Blocks.PISTON_HEAD.defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.UP)
                        .setValue(BlockStateProperties.PISTON_TYPE, PistonType.STICKY)
        ));

        // 3. Determine which physical slot receives Red vs Blue duper based on flight direction and geometry
        if (settings == null) {
            settings = PearlCalculator.loadSettings(this.gameDirectory);
        }
        boolean slotAIsRed = isSlotARed(calcResult.direction, settings);

        int slotADuperSize = slotAIsRed ? calcResult.redDuperSize : calcResult.blueDuperSize;
        int slotAExcess = slotAIsRed ? calcResult.getRedExcess() : calcResult.getBlueExcess();

        int slotBDuperSize = slotAIsRed ? calcResult.blueDuperSize : calcResult.redDuperSize;
        int slotBExcess = slotAIsRed ? calcResult.getBlueExcess() : calcResult.getRedExcess();

        // Slot A (unmirrored, feeds into basket at bx = 4 + dx)
        String slotAFileName = String.format(Locale.ROOT, "dupers/duper %03d TNT.litematic", slotADuperSize);
        LitematicaSchematic slotASchem = loadComponentSchematic(slotAFileName);
        List<BlockEntry> slotABlocks = extractBlocks(slotASchem);
        List<TileEntityEntry> slotATEs = extractTileEntities(slotASchem);
        int slotASx = 0;
        for (BlockEntry b : slotABlocks) slotASx = Math.max(slotASx, b.pos.getX() + 1);

        replaceExcessTNT(slotABlocks, slotAExcess);

        List<BlockEntry> transformedSlotADuper = new ArrayList<>();
        for (BlockEntry b : slotABlocks) {
            int dx = b.pos.getX() - (slotASx - 1);
            int dy = b.pos.getY() - 15;
            int dz = b.pos.getZ() - 15;

            int bx = 4 + dx;
            int by = 123 + dy;
            int bz = 4 + dz;

            double[] rot = rotateCoords(bx - 3.5, bz - 0.5, basketRotation);
            int wx = (int) Math.round(0.5 + rot[0]);
            int wy = by;
            int wz = (int) Math.round(0.5 + rot[1]);
            transformedSlotADuper.add(new BlockEntry(new BlockPos(wx, wy, wz), b.state.rotate(basketRotation)));
        }

        List<TileEntityEntry> transformedSlotATEs = new ArrayList<>();
        for (TileEntityEntry te : slotATEs) {
            int dx = te.pos.getX() - (slotASx - 1);
            int dy = te.pos.getY() - 15;
            int dz = te.pos.getZ() - 15;

            int bx = 4 + dx;
            int by = 123 + dy;
            int bz = 4 + dz;

            double[] rot = rotateCoords(bx - 3.5, bz - 0.5, basketRotation);
            int wx = (int) Math.round(0.5 + rot[0]);
            int wy = by;
            int wz = (int) Math.round(0.5 + rot[1]);
            transformedSlotATEs.add(new TileEntityEntry(new BlockPos(wx, wy, wz), te.nbt));
        }

        // Slot B (mirrored FRONT_BACK, feeds into basket at bx = 4 - dx)
        String slotBFileName = String.format(Locale.ROOT, "dupers/duper %03d TNT.litematic", slotBDuperSize);
        LitematicaSchematic slotBSchem = loadComponentSchematic(slotBFileName);
        List<BlockEntry> slotBBlocks = extractBlocks(slotBSchem);
        List<TileEntityEntry> slotBTEs = extractTileEntities(slotBSchem);
        int slotBSx = 0;
        for (BlockEntry b : slotBBlocks) slotBSx = Math.max(slotBSx, b.pos.getX() + 1);

        replaceExcessTNT(slotBBlocks, slotBExcess);

        List<BlockEntry> transformedSlotBDuper = new ArrayList<>();
        for (BlockEntry b : slotBBlocks) {
            int dx = b.pos.getX() - (slotBSx - 1);
            int dy = b.pos.getY() - 15;
            int dz = b.pos.getZ() - 15;

            int bx = 4 - dx;
            int by = 123 + dy;
            int bz = 4 + dz;

            double[] rot = rotateCoords(bx - 3.5, bz - 0.5, basketRotation);
            int wx = (int) Math.round(0.5 + rot[0]);
            int wy = by;
            int wz = (int) Math.round(0.5 + rot[1]);
            BlockState rotState = b.state.mirror(Mirror.FRONT_BACK).rotate(basketRotation);
            transformedSlotBDuper.add(new BlockEntry(new BlockPos(wx, wy, wz), rotState));
        }

        List<TileEntityEntry> transformedSlotBTEs = new ArrayList<>();
        for (TileEntityEntry te : slotBTEs) {
            int dx = te.pos.getX() - (slotBSx - 1);
            int dy = te.pos.getY() - 15;
            int dz = te.pos.getZ() - 15;

            int bx = 4 - dx;
            int by = 123 + dy;
            int bz = 4 + dz;

            double[] rot = rotateCoords(bx - 3.5, bz - 0.5, basketRotation);
            int wx = (int) Math.round(0.5 + rot[0]);
            int wy = by;
            int wz = (int) Math.round(0.5 + rot[1]);
            transformedSlotBTEs.add(new TileEntityEntry(new BlockPos(wx, wy, wz), te.nbt));
        }

        // 5. Existing blocks set for bridge collision avoidance
        Set<BlockPos> existingBlocks = new HashSet<>();
        for (BlockEntry b : rotatedAligner) existingBlocks.add(b.pos);
        for (BlockEntry b : rotatedBasket) existingBlocks.add(b.pos);

        // 6. Generate redstone bridge
        List<BlockEntry> bridge = generateRedstoneBridge(rotatedAlignerRS, rotatedBasketPiston, existingBlocks);

        // 7. Combine all blocks into a map (overwriting overlaps properly)
        Map<BlockPos, BlockState> allBlocks = new HashMap<>();
        for (BlockEntry b : rotatedAligner) allBlocks.put(b.pos, swapWaxedLightningRodIfNeeded(b.state));
        for (BlockEntry b : rotatedBasket) allBlocks.put(b.pos, swapWaxedLightningRodIfNeeded(b.state));
        for (BlockEntry b : bridge) allBlocks.put(b.pos, swapWaxedLightningRodIfNeeded(b.state));
        for (BlockEntry b : transformedSlotADuper) allBlocks.put(b.pos, swapWaxedLightningRodIfNeeded(b.state));
        for (BlockEntry b : transformedSlotBDuper) allBlocks.put(b.pos, swapWaxedLightningRodIfNeeded(b.state));

        if (blockPalette != null) {
            for (Map.Entry<BlockPos, BlockState> entry : allBlocks.entrySet()) {
                entry.setValue(blockPalette.applyReplacement(entry.getValue()));
            }
        }

        fixDoubleChests(allBlocks);

        // 8. Build bounding box
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos p : allBlocks.keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;

        NormalizedRegion mainRegion = new NormalizedRegion(minX, minY, minZ, width, height, length);
        for (Map.Entry<BlockPos, BlockState> e : allBlocks.entrySet()) {
            BlockPos p = e.getKey();
            int rx = p.getX() - minX;
            int ry = p.getY() - minY;
            int rz = p.getZ() - minZ;
            mainRegion.getContainer().set(rx, ry, rz, e.getValue());
        }

        // Combine and copy all tile entities into mainRegion (with relative coordinates rx, ry, rz)
        Map<BlockPos, CompoundData> allTileEntities = new HashMap<>();
        for (TileEntityEntry te : rotatedAlignerTEs) allTileEntities.put(te.pos, te.nbt);
        for (TileEntityEntry te : rotatedBasketTEs) allTileEntities.put(te.pos, te.nbt);
        for (TileEntityEntry te : transformedSlotATEs) allTileEntities.put(te.pos, te.nbt);
        for (TileEntityEntry te : transformedSlotBTEs) allTileEntities.put(te.pos, te.nbt);

        for (Map.Entry<BlockPos, CompoundData> e : allTileEntities.entrySet()) {
            BlockPos p = e.getKey();
            int rx = p.getX() - minX;
            int ry = p.getY() - minY;
            int rz = p.getZ() - minZ;
            CompoundData tag = e.getValue().copy();
            tag.putInt("x", rx);
            tag.putInt("y", ry);
            tag.putInt("z", rz);
            mainRegion.getTileEntities().put(new BlockPos(rx, ry, rz), tag);
        }

        // 9. Build LitematicaSchematic NBT
        CompoundData root = new CompoundData();
        root.putInt("MinecraftDataVersion", LitematicaSchematic.MINECRAFT_DATA_VERSION);
        root.putInt("Version", LitematicaSchematic.SCHEMATIC_VERSION);
        root.putInt("SubVersion", LitematicaSchematic.SCHEMATIC_VERSION_SUB);

        CompoundData regTag = new CompoundData();
        regTag.put("BlockStatePalette", mainRegion.getContainer().getPalette().writeToData());
        regTag.put("BlockStates", new LongArrayData(mainRegion.getContainer().getBackingLongArray()));

        ListData tileList = new ListData();
        for (CompoundData teTag : mainRegion.getTileEntities().values()) {
            tileList.add(teTag);
        }
        regTag.put("TileEntities", tileList);
        regTag.put("Entities", new ListData());

        // Position of the block under the lever in assembled coordinates:
        double levDx = -0.5;
        double levDz = 0.5;
        if (mirrorMode.flipX) levDx = -levDx;
        if (mirrorMode.flipZ) levDz = -levDz;
        double[] rotLever = rotateCoords(levDx, levDz, alignerRotation);
        int lx = (int) Math.round(0.5 + rotLever[0]);
        int lz = (int) Math.round(0.5 + rotLever[1]);

        BlockPos pos = new BlockPos(minX - lx, 0, minZ - lz);
        BlockPos size = new BlockPos(width, height, length);
        regTag.put("Position", DataTypeUtils.createBlockPosTag(pos));
        regTag.put("Size", DataTypeUtils.createBlockPosTag(size));

        // Calculate placement origin (lever block at y=128)
        BlockPos placementOrigin = getSchematicOrigin(cannonStartPos.getX(), cannonStartPos.getZ(), alignerRotation, mirrorMode);

        CompoundData regionsData = new CompoundData();
        String cannonSubregionName = String.format(Locale.ROOT,
                "Cannon [Origin: %d, %d, %d | Pearl: %d, %d, %d]",
                placementOrigin.getX(), placementOrigin.getY(), placementOrigin.getZ(),
                cannonStartPos.getX(), cannonStartPos.getY(), cannonStartPos.getZ()
        );
        regionsData.put(cannonSubregionName, regTag);

        // 1-block subregion for the catcher block with red stained glass
        BlockPos catcherRelativePos = calcResult.catchBlockPos.subtract(placementOrigin);
        BlockPos catcherSize = new BlockPos(1, 1, 1);

        NormalizedRegion catcherRegion = new NormalizedRegion(0, 0, 0, 1, 1, 1);
        catcherRegion.getContainer().set(0, 0, 0, Blocks.STAINED_GLASS.red().defaultBlockState());

        CompoundData catcherTag = new CompoundData();
        catcherTag.put("BlockStatePalette", catcherRegion.getContainer().getPalette().writeToData());
        catcherTag.put("BlockStates", new LongArrayData(catcherRegion.getContainer().getBackingLongArray()));
        catcherTag.put("TileEntities", new ListData());
        catcherTag.put("Entities", new ListData());
        catcherTag.put("Position", DataTypeUtils.createBlockPosTag(catcherRelativePos));
        catcherTag.put("Size", DataTypeUtils.createBlockPosTag(catcherSize));

        String catcherSubregionName = String.format(Locale.ROOT,
                "Catcher [%d, %d, %d] Pearl (%.2f, %.2f, %.2f)",
                calcResult.catchBlockPos.getX(),
                calcResult.catchBlockPos.getY(),
                calcResult.catchBlockPos.getZ(),
                calcResult.worldArrivalPos.x,
                calcResult.worldArrivalPos.y,
                calcResult.worldArrivalPos.z
        );
        regionsData.put(catcherSubregionName, catcherTag);

        BlockPos landingRelativePos = null;
        if (generatePowderSnowLanding) {
            BlockPos landingPos = getLandingPos(calcResult, cannonStartPos);
            landingRelativePos = landingPos.subtract(placementOrigin);

            NormalizedRegion landingRegion = new NormalizedRegion(0, 0, 0, 1, 2, 1);
            landingRegion.getContainer().set(0, 0, 0, Blocks.POWDER_SNOW.defaultBlockState());
            landingRegion.getContainer().set(0, 1, 0, Blocks.CARPET.gray().defaultBlockState());

            CompoundData landingTag = new CompoundData();
            landingTag.put("BlockStatePalette", landingRegion.getContainer().getPalette().writeToData());
            landingTag.put("BlockStates", new LongArrayData(landingRegion.getContainer().getBackingLongArray()));
            landingTag.put("TileEntities", new ListData());
            landingTag.put("Entities", new ListData());
            landingTag.put("Position", DataTypeUtils.createBlockPosTag(landingRelativePos));
            landingTag.put("Size", DataTypeUtils.createBlockPosTag(new BlockPos(1, 2, 1)));

            String landingSubregionName = String.format(Locale.ROOT,
                    "Landing [%d, %d, %d]",
                    landingPos.getX(),
                    landingPos.getY(),
                    landingPos.getZ()
            );
            regionsData.put(landingSubregionName, landingTag);
        }

        CompoundData meta = new CompoundData();
        meta.putString("Name", schematicName);
        meta.putString("Author", "Return Cannon Generator");
        meta.putString("Description", String.format(Locale.ROOT,
                "Origin: [%d, %d, %d] | Pearl: [%d, %d, %d] | %s %dt (Red: %d/%d, Blue: %d/%d) | Catch: [%d, %d, %d] | Landing: (%.2f, %.2f, %.2f)",
                placementOrigin.getX(), placementOrigin.getY(), placementOrigin.getZ(),
                cannonStartPos.getX(), cannonStartPos.getY(), cannonStartPos.getZ(),
                calcResult.direction.name, calcResult.tick,
                calcResult.redTNT, calcResult.redDuperSize,
                calcResult.blueTNT, calcResult.blueDuperSize,
                calcResult.catchBlockPos.getX(),
                calcResult.catchBlockPos.getY(),
                calcResult.catchBlockPos.getZ(),
                calcResult.worldArrivalPos.x,
                calcResult.worldArrivalPos.y,
                calcResult.worldArrivalPos.z
        ));
        meta.putInt("RegionCount", generatePowderSnowLanding ? 3 : 2);
        meta.putInt("TotalVolume", width * height * length + 1 + (generatePowderSnowLanding ? 2 : 0));
        int totalBlocks = 0;
        for (long count : mainRegion.getContainer().getBlockCounts()) {
            totalBlocks += (int) count;
        }
        meta.putInt("TotalBlocks", totalBlocks + 1 + (generatePowderSnowLanding ? 2 : 0));
        meta.putInt("EntityCount", 0);
        meta.putInt("BlockEntityCount", mainRegion.getTileEntities().size());
        meta.putLong("TimeCreated", System.currentTimeMillis());
        meta.putLong("TimeModified", System.currentTimeMillis());

        int allMinX = Math.min(pos.getX(), catcherRelativePos.getX());
        int allMinY = Math.min(pos.getY(), catcherRelativePos.getY());
        int allMinZ = Math.min(pos.getZ(), catcherRelativePos.getZ());
        int allMaxX = Math.max(pos.getX() + width, catcherRelativePos.getX() + 1);
        int allMaxY = Math.max(pos.getY() + height, catcherRelativePos.getY() + 1);
        int allMaxZ = Math.max(pos.getZ() + length, catcherRelativePos.getZ() + 1);
        if (landingRelativePos != null) {
            allMinX = Math.min(allMinX, landingRelativePos.getX());
            allMinY = Math.min(allMinY, landingRelativePos.getY());
            allMinZ = Math.min(allMinZ, landingRelativePos.getZ());
            allMaxX = Math.max(allMaxX, landingRelativePos.getX() + 1);
            allMaxY = Math.max(allMaxY, landingRelativePos.getY() + 2);
            allMaxZ = Math.max(allMaxZ, landingRelativePos.getZ() + 1);
        }
        meta.put("EnclosingSize", DataTypeUtils.createVec3iTag(new Vec3i(allMaxX - allMinX, allMaxY - allMinY, allMaxZ - allMinZ)));

        root.put("Metadata", meta);
        root.put("Regions", regionsData);
        return root;
    }

    public LitematicaSchematic assembleCannon(
            Rotation alignerRotation,
            MirrorMode mirrorMode,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            BlockPos cannonStartPos
    ) throws Exception {
        return assembleCannon(alignerRotation, mirrorMode, calcResult, settings, schematicName, cannonStartPos, false);
    }

    public LitematicaSchematic assembleCannon(
            Rotation alignerRotation,
            MirrorMode mirrorMode,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            BlockPos cannonStartPos,
            boolean generatePowderSnowLanding
    ) throws Exception {
        return assembleCannon(alignerRotation, mirrorMode, calcResult, settings, schematicName, cannonStartPos, generatePowderSnowLanding, null);
    }

    public LitematicaSchematic assembleCannon(
            Rotation alignerRotation,
            MirrorMode mirrorMode,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            BlockPos cannonStartPos,
            boolean generatePowderSnowLanding,
            CannonBlockPalette blockPalette
    ) throws Exception {
        CompoundData root = assembleCannonNbt(alignerRotation, mirrorMode, calcResult, settings, schematicName, cannonStartPos, generatePowderSnowLanding, blockPalette);
        Path defaultPath = this.gameDirectory != null
                ? this.gameDirectory.resolve("schematics").resolve(schematicName + ".litematic")
                : Path.of(schematicName + ".litematic");
        return new LitematicaSchematic(defaultPath, root, FileType.LITEMATICA_SCHEMATIC);
    }

    public GenerationResult assembleAndSaveCannon(
            Rotation alignerRotation,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            Path outputDirectory,
            BlockPos cannonStartPos
    ) throws Exception {
        return assembleAndSaveCannon(alignerRotation, MirrorMode.OFF, calcResult, settings, schematicName, outputDirectory, cannonStartPos, false);
    }

    public GenerationResult assembleAndSaveCannon(
            Rotation alignerRotation,
            boolean mirrorAligner,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            Path outputDirectory,
            BlockPos cannonStartPos
    ) throws Exception {
        return assembleAndSaveCannon(alignerRotation, mirrorAligner ? MirrorMode.FRONT_BACK : MirrorMode.OFF, calcResult, settings, schematicName, outputDirectory, cannonStartPos, false);
    }

    public GenerationResult assembleAndSaveCannon(
            Rotation alignerRotation,
            MirrorMode mirrorMode,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            Path outputDirectory,
            BlockPos cannonStartPos
    ) throws Exception {
        return assembleAndSaveCannon(alignerRotation, mirrorMode, calcResult, settings, schematicName, outputDirectory, cannonStartPos, false);
    }

    public GenerationResult assembleAndSaveCannon(
            Rotation alignerRotation,
            MirrorMode mirrorMode,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            Path outputDirectory,
            BlockPos cannonStartPos,
            boolean generatePowderSnowLanding
    ) throws Exception {
        LitematicaSchematic schematic = assembleCannon(alignerRotation, mirrorMode, calcResult, settings, schematicName, cannonStartPos, generatePowderSnowLanding);
        String safeName = FileNameUtils.generateSafeFileName(schematicName);
        Files.createDirectories(outputDirectory);
        schematic.writeToFile(outputDirectory, safeName, false);
        Path outputFile = outputDirectory.resolve(safeName + ".litematic");
        return new GenerationResult(schematic, outputFile);
    }

    public GenerationResult assembleAndSaveCannon(
            Rotation alignerRotation,
            CalculationResult calcResult,
            PearlCalculator.CannonSettings settings,
            String schematicName,
            Path outputDirectory
    ) throws Exception {
        return assembleAndSaveCannon(alignerRotation, MirrorMode.OFF, calcResult, settings, schematicName, outputDirectory, BlockPos.ZERO, false);
    }

    public static class CannonEntry {
        public final String name;
        public final Rotation alignerRotation;
        public final MirrorMode mirrorMode;
        public final CalculationResult calcResult;
        public final BlockPos cannonStartPos;
        public final boolean generatePowderSnowLanding;

        public CannonEntry(String name, Rotation alignerRotation, MirrorMode mirrorMode, CalculationResult calcResult, BlockPos cannonStartPos) {
            this(name, alignerRotation, mirrorMode, calcResult, cannonStartPos, false);
        }

        public CannonEntry(String name, Rotation alignerRotation, MirrorMode mirrorMode, CalculationResult calcResult, BlockPos cannonStartPos, boolean generatePowderSnowLanding) {
            this.name = name;
            this.alignerRotation = alignerRotation;
            this.mirrorMode = mirrorMode;
            this.calcResult = calcResult;
            this.cannonStartPos = cannonStartPos;
            this.generatePowderSnowLanding = generatePowderSnowLanding;
        }
    }

    public LitematicaSchematic assembleCombinedCannonSchematic(
            List<CannonEntry> entries,
            PearlCalculator.CannonSettings settings,
            String combinedName
    ) throws Exception {
        CompoundData root = new CompoundData();
        root.putInt("MinecraftDataVersion", LitematicaSchematic.MINECRAFT_DATA_VERSION);
        root.putInt("Version", LitematicaSchematic.SCHEMATIC_VERSION);
        root.putInt("SubVersion", LitematicaSchematic.SCHEMATIC_VERSION_SUB);

        CompoundData regionsData = new CompoundData();
        int totalBlocks = 0;
        int totalVolume = 0;
        int allMinX = Integer.MAX_VALUE, allMinY = Integer.MAX_VALUE, allMinZ = Integer.MAX_VALUE;
        int allMaxX = Integer.MIN_VALUE, allMaxY = Integer.MIN_VALUE, allMaxZ = Integer.MIN_VALUE;

        for (int i = 0; i < entries.size(); i++) {
            CannonEntry entry = entries.get(i);
            CompoundData singleRoot = assembleCannonNbt(
                    entry.alignerRotation,
                    entry.mirrorMode,
                    entry.calcResult,
                    settings,
                    entry.name,
                    entry.cannonStartPos,
                    entry.generatePowderSnowLanding
            );
            CompoundData singleRegions = singleRoot.getCompound("Regions");
            BlockPos placementOrigin = getSchematicOrigin(
                    entry.cannonStartPos.getX(),
                    entry.cannonStartPos.getZ(),
                    entry.alignerRotation,
                    entry.mirrorMode
            );

            if (singleRegions != null) {
                for (String regName : singleRegions.getKeys()) {
                    CompoundData regTag = singleRegions.getCompound(regName);
                    BlockPos regPos = DataTypeUtils.readBlockPos(regTag.getCompound("Position"));
                    BlockPos regSize = DataTypeUtils.readBlockPos(regTag.getCompound("Size"));

                    // Convert relative to placementOrigin into absolute world coordinates (for [0, 0, 0] placement)
                    BlockPos absPos = placementOrigin.offset(regPos);
                    regTag.put("Position", DataTypeUtils.createBlockPosTag(absPos));

                    String uniqueRegName = String.format(Locale.ROOT, "[%d] %s", i + 1, regName);
                    regionsData.put(uniqueRegName, regTag);

                    int w = Math.abs(regSize.getX());
                    int h = Math.abs(regSize.getY());
                    int l = Math.abs(regSize.getZ());
                    totalVolume += w * h * l;

                    allMinX = Math.min(allMinX, absPos.getX());
                    allMinY = Math.min(allMinY, absPos.getY());
                    allMinZ = Math.min(allMinZ, absPos.getZ());
                    allMaxX = Math.max(allMaxX, absPos.getX() + w);
                    allMaxY = Math.max(allMaxY, absPos.getY() + h);
                    allMaxZ = Math.max(allMaxZ, absPos.getZ() + l);
                }
            }

            CompoundData singleMeta = singleRoot.getCompound("Metadata");
            if (singleMeta != null) {
                totalBlocks += singleMeta.getInt("TotalBlocks");
            }
        }

        CompoundData meta = new CompoundData();
        meta.putString("Name", combinedName);
        meta.putString("Author", "Return Cannon Debug Generator");
        meta.putString("Description", "Combined test cannons with exact 1-block stopper blocks at absolute world coordinates (place at [0, 0, 0])");
        meta.putInt("RegionCount", regionsData.size());
        meta.putInt("TotalVolume", totalVolume);
        meta.putInt("TotalBlocks", totalBlocks);
        meta.putInt("EntityCount", 0);
        meta.putInt("BlockEntityCount", 0);
        meta.putLong("TimeCreated", System.currentTimeMillis());
        meta.putLong("TimeModified", System.currentTimeMillis());

        if (allMinX <= allMaxX) {
            meta.put("EnclosingSize", DataTypeUtils.createVec3iTag(new Vec3i(allMaxX - allMinX, allMaxY - allMinY, allMaxZ - allMinZ)));
        } else {
            meta.put("EnclosingSize", DataTypeUtils.createVec3iTag(new Vec3i(1, 1, 1)));
        }

        root.put("Metadata", meta);
        root.put("Regions", regionsData);

        Path defaultPath = this.gameDirectory != null
                ? this.gameDirectory.resolve("schematics").resolve(combinedName + ".litematic")
                : Path.of(combinedName + ".litematic");
        return new LitematicaSchematic(defaultPath, root, FileType.LITEMATICA_SCHEMATIC);
    }
}
