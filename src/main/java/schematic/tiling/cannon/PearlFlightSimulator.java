package schematic.tiling.cannon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Simulates pearl flight trajectories and checks for obstacles and collision points along the flight path.
 * Features ultra-fast precomputed mathematical trajectory curves, inverted chunk-to-route indexing,
 * zero-lag chunk loading checks, and leak-free memory management.
 */
public class PearlFlightSimulator {

    public enum FlightStatus {
        CLEAR,
        COLLISION,
        UNCHECKED
    }

    public static class SimulationPoint {
        public final int tick;
        public final Vec3 pos;
        public final Vec3 motion;

        public SimulationPoint(int tick, Vec3 pos, Vec3 motion) {
            this.tick = tick;
            this.pos = pos;
            this.motion = motion;
        }
    }

    public static class SimulationResult {
        public final FlightStatus status;
        public final PearlCalculator.CalculationResult solution;
        public final BlockPos cannonStartPos;
        public final BlockPos collisionPos;
        public final BlockState collisionBlockState;
        public final Vec3 collisionExact;
        public final int collisionTick;
        public final double collisionDistFromStart;
        public final double collisionDistToCatch;
        public final int totalTicks;
        public final double maxAltitude;
        public final int chunksTraversed;
        public final int chunksLoaded;
        public final int chunksUnloaded;
        public final List<SimulationPoint> trajectory;
        public final Set<Long> traversedChunkKeys;

        public SimulationResult(
                FlightStatus status,
                PearlCalculator.CalculationResult solution,
                BlockPos cannonStartPos,
                BlockPos collisionPos,
                BlockState collisionBlockState,
                Vec3 collisionExact,
                int collisionTick,
                double collisionDistFromStart,
                double collisionDistToCatch,
                int totalTicks,
                double maxAltitude,
                int chunksTraversed,
                int chunksLoaded,
                int chunksUnloaded,
                List<SimulationPoint> trajectory,
                Set<Long> traversedChunkKeys
        ) {
            this.status = status;
            this.solution = solution;
            this.cannonStartPos = cannonStartPos;
            this.collisionPos = collisionPos;
            this.collisionBlockState = collisionBlockState;
            this.collisionExact = collisionExact;
            this.collisionTick = collisionTick;
            this.collisionDistFromStart = collisionDistFromStart;
            this.collisionDistToCatch = collisionDistToCatch;
            this.totalTicks = totalTicks;
            this.maxAltitude = maxAltitude;
            this.chunksTraversed = chunksTraversed;
            this.chunksLoaded = chunksLoaded;
            this.chunksUnloaded = chunksUnloaded;
            this.trajectory = trajectory;
            this.traversedChunkKeys = (traversedChunkKeys != null) ? Collections.unmodifiableSet(new HashSet<>(traversedChunkKeys)) : Collections.emptySet();
        }

        public String getCollisionBlockName() {
            if (this.collisionBlockState == null) return "Unknown";
            try {
                return this.collisionBlockState.getBlock().getName().getString();
            } catch (Throwable t) {
                return this.collisionBlockState.getBlock().getDescriptionId();
            }
        }
    }

    public static class RouteTrajectory {
        public final String routeKey;
        public final BlockPos cannonStartPos;
        public final PearlCalculator.CalculationResult solution;
        public final List<SimulationPoint> trajectory;
        public final Set<Long> traversedChunks;
        public final double maxAltitude;
        public final Map<Long, List<Integer>> ticksByChunk;

        public RouteTrajectory(
                String routeKey,
                BlockPos cannonStartPos,
                PearlCalculator.CalculationResult solution,
                List<SimulationPoint> trajectory,
                Set<Long> traversedChunks,
                double maxAltitude,
                Map<Long, List<Integer>> ticksByChunk
        ) {
            this.routeKey = routeKey;
            this.cannonStartPos = cannonStartPos;
            this.solution = solution;
            this.trajectory = trajectory;
            this.traversedChunks = traversedChunks;
            this.maxAltitude = maxAltitude;
            this.ticksByChunk = ticksByChunk;
        }
    }

    public static class ObstacleHit {
        public final BlockPos pos;
        public final BlockState state;
        public final Vec3 exactPos;

        public ObstacleHit(BlockPos pos, BlockState state, Vec3 exactPos) {
            this.pos = pos;
            this.state = state;
            this.exactPos = exactPos;
        }
    }

    private static final Map<String, RouteTrajectory> sTrajectoryCache = new HashMap<>();
    private static final Map<Long, List<String>> sChunkToRoutesMap = new HashMap<>();
    private static final Map<String, SimulationResult> sRouteSimulations = new HashMap<>();
    private static final Map<String, Set<Long>> sRouteVerifiedChunks = new HashMap<>();
    private static final Set<Long> sVerifiedChunks = new HashSet<>();
    private static int sVerifiedChunksVersion = 0;

    private static BlockPos sActiveStartPos = null;
    private static PearlCalculator.CannonSettings sActiveSettings = null;
    private static List<PearlCalculator.CalculationResult> sCandidateSolutions = new ArrayList<>();

    public static void registerCandidateSolutions(
            BlockPos startPos,
            PearlCalculator.CannonSettings settings,
            List<PearlCalculator.CalculationResult> solutions
    ) {
        if (startPos == null || solutions == null || solutions.isEmpty()) {
            return;
        }

        boolean sameStart = Objects.equals(sActiveStartPos, startPos);
        boolean sameSolutions = sCandidateSolutions != null && sCandidateSolutions.equals(solutions);
        if (sameStart && sameSolutions) {
            return;
        }

        clearRouteCache();
        sActiveStartPos = startPos;
        sActiveSettings = settings;
        sCandidateSolutions = new ArrayList<>(solutions);
        sChunkToRoutesMap.clear();

        for (PearlCalculator.CalculationResult sol : sCandidateSolutions) {
            RouteTrajectory traj = computeTrajectory(startPos, sol, settings);
            if (traj != null) {
                for (long chunkKey : traj.traversedChunks) {
                    sChunkToRoutesMap.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(traj.routeKey);
                }
            }
        }
    }

    public static void verifyCurrentlyLoadedChunks(Level level) {
        if (level == null || sChunkToRoutesMap.isEmpty()) return;
        List<Long> keysToCheck = new ArrayList<>(sChunkToRoutesMap.keySet());
        for (long chunkKey : keysToCheck) {
            int cx = chunkKeyX(chunkKey);
            int cz = chunkKeyZ(chunkKey);
            if (isChunkLoaded(level, cx, cz)) {
                onChunkLoadedFast(level, chunkKey);
            }
        }
    }

    public static void rescanLoadedChunks(Level level) {
        if (level == null) return;
        sRouteSimulations.clear();
        if (sActiveStartPos != null && sCandidateSolutions != null && !sCandidateSolutions.isEmpty()) {
            for (PearlCalculator.CalculationResult sol : sCandidateSolutions) {
                simulate(sActiveStartPos, sol, sActiveSettings, level);
            }
        }
        sVerifiedChunksVersion++;
    }

    public static void restartScan(Level level) {
        sRouteSimulations.clear();
        sRouteVerifiedChunks.clear();
        sVerifiedChunks.clear();
        sVerifiedChunksVersion++;
        if (level != null && sActiveStartPos != null && sCandidateSolutions != null && !sCandidateSolutions.isEmpty()) {
            for (PearlCalculator.CalculationResult sol : sCandidateSolutions) {
                simulate(sActiveStartPos, sol, sActiveSettings, level);
            }
        }
    }

    public static SimulationResult recheckRoute(Level level, String routeKey) {
        if (level == null || routeKey == null) return null;
        RouteTrajectory traj = sTrajectoryCache.get(routeKey);
        if (traj == null) return null;

        sRouteSimulations.remove(routeKey);
        SimulationResult res = simulate(traj.cannonStartPos, traj.solution, sActiveSettings, level);
        sVerifiedChunksVersion++;
        return res;
    }

    public static BlockPos getActiveStartPos() {
        return sActiveStartPos;
    }

    public static PearlCalculator.CannonSettings getActiveSettings() {
        return sActiveSettings;
    }

    public static List<PearlCalculator.CalculationResult> getCandidateSolutions() {
        return Collections.unmodifiableList(sCandidateSolutions);
    }

    public static SimulationResult getRouteSimulation(String routeKey) {
        if (routeKey == null) return null;
        return sRouteSimulations.get(routeKey);
    }

    public static int getVerifiedChunksVersion() {
        return sVerifiedChunksVersion;
    }

    public static void clearRouteCache() {
        sTrajectoryCache.clear();
        sChunkToRoutesMap.clear();
        sRouteSimulations.clear();
        sRouteVerifiedChunks.clear();
        sVerifiedChunks.clear();
        sCandidateSolutions.clear();
        sActiveStartPos = null;
        sActiveSettings = null;
        sVerifiedChunksVersion++;
    }

    public static String getRouteKey(BlockPos cannonStartPos, PearlCalculator.CalculationResult solution) {
        if (cannonStartPos == null || solution == null) return "";
        return cannonStartPos.getX() + "," + cannonStartPos.getZ() + "->"
                + solution.direction + ":" + solution.redTNT + ":" + solution.blueTNT + ":" + solution.tick;
    }

    public static Set<Long> getVerifiedChunks() {
        return Collections.unmodifiableSet(sVerifiedChunks);
    }

    public static Set<Long> getAllCandidateChunks() {
        return Collections.unmodifiableSet(sChunkToRoutesMap.keySet());
    }

    public static int getTotalCandidateChunksCount() {
        return sChunkToRoutesMap.size();
    }

    public static int getVerifiedCandidateChunksCount(Level level) {
        int count = 0;
        for (long key : sChunkToRoutesMap.keySet()) {
            if (sVerifiedChunks.contains(key)) {
                count++;
            } else if (level != null) {
                int cx = chunkKeyX(key);
                int cz = chunkKeyZ(key);
                if (isChunkLoaded(level, cx, cz)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int getCollidingRoutesCount() {
        int count = 0;
        for (SimulationResult sim : sRouteSimulations.values()) {
            if (sim != null && sim.status == FlightStatus.COLLISION) {
                count++;
            }
        }
        return count;
    }

    public static Set<Long> getVerifiedChunks(String routeKey) {
        if (routeKey == null) return Collections.emptySet();
        Set<Long> set = sRouteVerifiedChunks.get(routeKey);
        return (set != null) ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    public static void addVerifiedChunk(long chunkKey) {
        if (sVerifiedChunks.add(chunkKey)) {
            sVerifiedChunksVersion++;
        }
    }

    public static void addVerifiedChunk(String routeKey, long chunkKey) {
        if (routeKey == null || routeKey.isEmpty()) {
            addVerifiedChunk(chunkKey);
            return;
        }
        Set<Long> set = sRouteVerifiedChunks.computeIfAbsent(routeKey, k -> new HashSet<>());
        if (set.add(chunkKey)) {
            sVerifiedChunks.add(chunkKey);
            sVerifiedChunksVersion++;
        }
    }

    public static boolean isChunkVerified(long chunkKey) {
        return sVerifiedChunks.contains(chunkKey);
    }

    public static boolean isRouteChunkVerified(String routeKey, long chunkKey) {
        if (routeKey == null) return sVerifiedChunks.contains(chunkKey);
        Set<Long> set = sRouteVerifiedChunks.get(routeKey);
        if (set != null && set.contains(chunkKey)) {
            return true;
        }
        return false;
    }

    public static boolean isChunkLoaded(Level level, int chunkX, int chunkZ) {
        if (level == null) return false;
        try {
            if (level.getChunkSource() != null) {
                ChunkAccess chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                return chunk != null && !(chunk instanceof EmptyLevelChunk);
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static RouteTrajectory computeTrajectory(
            BlockPos cannonStartPos,
            PearlCalculator.CalculationResult solution,
            PearlCalculator.CannonSettings settings
    ) {
        if (cannonStartPos == null || solution == null || settings == null) return null;
        String routeKey = getRouteKey(cannonStartPos, solution);
        RouteTrajectory cached = sTrajectoryCache.get(routeKey);
        if (cached != null) return cached;

        PearlCalculator.Space3D[] vecs = PearlCalculator.resolveVectorsForDirection(settings, solution.direction);
        PearlCalculator.Space3D redVec = vecs[0];
        PearlCalculator.Space3D blueVec = vecs[1];

        PearlCalculator.Space3D tntImpact = redVec.multiply(solution.redTNT).add(blueVec.multiply(solution.blueTNT));
        PearlCalculator.Space3D mot = settings.pearlMotion.add(tntImpact);
        PearlCalculator.Space3D pos = settings.pearlPos;

        List<SimulationPoint> trajectory = new ArrayList<>(solution.tick + 2);
        Set<Long> traversedChunks = new HashSet<>();
        Map<Long, List<Integer>> ticksByChunk = new HashMap<>();

        Vec3 currentWorld = toWorld(pos, cannonStartPos, settings);
        Vec3 initialMot = new Vec3(mot.x, mot.y, mot.z);
        trajectory.add(new SimulationPoint(0, currentWorld, initialMot));

        double maxAltitude = currentWorld.y;
        long startChunkKey = chunkPosKey(((int) Math.floor(currentWorld.x)) >> 4, ((int) Math.floor(currentWorld.z)) >> 4);
        traversedChunks.add(startChunkKey);
        ticksByChunk.computeIfAbsent(startChunkKey, k -> new ArrayList<>()).add(0);

        int totalSimTicks = solution.tick + 1;
        for (int t = 1; t <= totalSimTicks; t++) {
            Vec3 prevWorld = currentWorld;

            PearlCalculator.Space3D[] step = PearlCalculator.runPost1212Tick(pos, mot);
            pos = step[0];
            mot = step[1];

            currentWorld = toWorld(pos, cannonStartPos, settings);
            if (currentWorld.y > maxAltitude) {
                maxAltitude = currentWorld.y;
            }

            trajectory.add(new SimulationPoint(t, currentWorld, new Vec3(mot.x, mot.y, mot.z)));
            addChunksAlongSegmentWithRadius(prevWorld, currentWorld, 0.15, traversedChunks, ticksByChunk, t);
        }

        RouteTrajectory res = new RouteTrajectory(
                routeKey,
                cannonStartPos,
                solution,
                Collections.unmodifiableList(trajectory),
                Collections.unmodifiableSet(traversedChunks),
                maxAltitude,
                ticksByChunk
        );
        sTrajectoryCache.put(routeKey, res);
        return res;
    }

    public static boolean isObstacleCleared(Level world, BlockPos pos) {
        if (world == null || pos == null) return false;
        if (!isChunkLoaded(world, pos.getX() >> 4, pos.getZ() >> 4)) return false;
        try {
            BlockState bs = world.getBlockState(pos);
            Level schemWorld = getSchematicWorld();
            BlockState schemBs = (schemWorld != null) ? schemWorld.getBlockState(pos) : null;
            boolean noWorldCol = bs.isAir() || bs.getCollisionShape(world, pos).isEmpty();
            boolean noSchemCol = (schemBs == null || schemBs.isAir() || schemBs.getCollisionShape(schemWorld, pos).isEmpty());
            return noWorldCol && noSchemCol;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isSegmentAnyChunkLoaded(Level level, RouteTrajectory traj, int t, int cxStart, int czStart, int cxEnd, int czEnd) {
        if (level == null) return false;
        if (isChunkLoaded(level, cxStart, czStart) || isChunkLoaded(level, cxEnd, czEnd)) {
            return true;
        }
        if (traj != null && traj.ticksByChunk != null) {
            for (Map.Entry<Long, List<Integer>> entry : traj.ticksByChunk.entrySet()) {
                if (entry.getValue().contains(t)) {
                    int cx = chunkKeyX(entry.getKey());
                    int cz = chunkKeyZ(entry.getKey());
                    if (isChunkLoaded(level, cx, cz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static SimulationResult simulate(
            BlockPos cannonStartPos,
            PearlCalculator.CalculationResult solution,
            PearlCalculator.CannonSettings settings,
            Level level
    ) {
        if (solution == null || cannonStartPos == null || settings == null) {
            return null;
        }

        RouteTrajectory traj = computeTrajectory(cannonStartPos, solution, settings);
        if (traj == null) return null;

        String routeKey = traj.routeKey;
        SimulationResult existing = sRouteSimulations.get(routeKey);
        Set<Long> routeVerified = sRouteVerifiedChunks.computeIfAbsent(routeKey, k -> new HashSet<>());

        // Check if existing obstacle was broken
        if (existing != null && existing.status == FlightStatus.COLLISION && existing.collisionPos != null) {
            int colChunkX = existing.collisionPos.getX() >> 4;
            int colChunkZ = existing.collisionPos.getZ() >> 4;
            if (level != null && isChunkLoaded(level, colChunkX, colChunkZ)) {
                try {
                    Level schemWorld = getSchematicWorld();
                    BlockState cur = level.getBlockState(existing.collisionPos);
                    BlockState schemCur = (schemWorld != null) ? schemWorld.getBlockState(existing.collisionPos) : null;
                    boolean noWorldCol = cur.isAir() || cur.getCollisionShape(level, existing.collisionPos).isEmpty();
                    boolean noSchemCol = (schemCur == null || schemCur.isAir() || schemCur.getCollisionShape(schemWorld, existing.collisionPos).isEmpty());
                    if (noWorldCol && noSchemCol) {
                        existing = null;
                        sRouteSimulations.remove(routeKey);
                    }
                } catch (Throwable ignored) {}
            }
        }

        BlockPos collisionPos = null;
        BlockState collisionState = null;
        Vec3 collisionExact = null;
        int collisionTick = -1;
        FlightStatus status = FlightStatus.CLEAR;

        if (level != null) {
            int totalSimTicks = traj.solution.tick + 1;
            for (int t = 1; t <= totalSimTicks; t++) {
                SimulationPoint pPrev = traj.trajectory.get(t - 1);
                SimulationPoint pCur = traj.trajectory.get(t);
                int cxStart = ((int) Math.floor(pPrev.pos.x)) >> 4;
                int czStart = ((int) Math.floor(pPrev.pos.z)) >> 4;
                int cxEnd = ((int) Math.floor(pCur.pos.x)) >> 4;
                int czEnd = ((int) Math.floor(pCur.pos.z)) >> 4;

                // Check collision if any chunk traversed by this segment is loaded
                if (isSegmentAnyChunkLoaded(level, traj, t, cxStart, czStart, cxEnd, czEnd)) {
                    ObstacleHit hit = testTickCollision(level, pPrev.pos, pCur.pos, t, traj.solution, traj.cannonStartPos);
                    if (hit != null) {
                        collisionPos = hit.pos;
                        collisionState = hit.state;
                        collisionExact = hit.exactPos;
                        collisionTick = t;
                        status = FlightStatus.COLLISION;
                        break;
                    }
                }
            }
        }

        // If no collision detected in loaded chunks, but an existing verified collision was recorded and block still exists
        if (status == FlightStatus.CLEAR && existing != null && existing.status == FlightStatus.COLLISION && existing.collisionPos != null) {
            boolean isColLoaded = (level != null) && isChunkLoaded(level, existing.collisionPos.getX() >> 4, existing.collisionPos.getZ() >> 4);
            boolean blockStillExists = !isColLoaded;
            if (isColLoaded && level != null) {
                BlockState cur = level.getBlockState(existing.collisionPos);
                Level schemWorld = getSchematicWorld();
                BlockState schemCur = (schemWorld != null) ? schemWorld.getBlockState(existing.collisionPos) : null;
                boolean worldSolid = !cur.isAir() && !cur.getCollisionShape(level, existing.collisionPos).isEmpty();
                boolean schemSolid = (schemCur != null && !schemCur.isAir() && !schemCur.getCollisionShape(schemWorld, existing.collisionPos).isEmpty());
                blockStillExists = worldSolid || schemSolid;
            }
            if (blockStillExists) {
                collisionPos = existing.collisionPos;
                collisionState = existing.collisionBlockState;
                collisionExact = existing.collisionExact;
                collisionTick = existing.collisionTick;
                status = FlightStatus.COLLISION;
            }
        }

        // Mark chunks as verified
        if (level == null) {
            status = FlightStatus.UNCHECKED;
        } else {
            for (long key : traj.traversedChunks) {
                int cx = chunkKeyX(key);
                int cz = chunkKeyZ(key);
                if (isChunkLoaded(level, cx, cz)) {
                    if (routeVerified.add(key)) {
                        sVerifiedChunks.add(key);
                        sVerifiedChunksVersion++;
                    }
                }
            }

            if (collisionPos != null) {
                long colKey = chunkPosKey(collisionPos.getX() >> 4, collisionPos.getZ() >> 4);
                if (routeVerified.add(colKey)) {
                    sVerifiedChunks.add(colKey);
                    sVerifiedChunksVersion++;
                }
            }

            if (status == FlightStatus.CLEAR) {
                for (long key : traj.traversedChunks) {
                    if (!isRouteChunkVerified(traj.routeKey, key)) {
                        status = FlightStatus.UNCHECKED;
                        break;
                    }
                }
            }
        }

        SimulationResult res = buildSimulationResult(
                traj,
                status,
                collisionPos,
                collisionState,
                collisionExact,
                collisionTick,
                routeVerified,
                level
        );
        sRouteSimulations.put(routeKey, res);
        return res;
    }

    public static void onChunkLoadedFast(Level level, long chunkKey) {
        if (level == null) return;
        List<String> routeKeys = sChunkToRoutesMap.get(chunkKey);
        if (routeKeys == null || routeKeys.isEmpty()) {
            return;
        }

        for (String rKey : routeKeys) {
            Set<Long> routeVerified = sRouteVerifiedChunks.computeIfAbsent(rKey, k -> new HashSet<>());
            if (routeVerified.contains(chunkKey)) {
                continue;
            }

            RouteTrajectory traj = sTrajectoryCache.get(rKey);
            if (traj == null) continue;

            List<Integer> ticks = traj.ticksByChunk.get(chunkKey);
            if (ticks == null || ticks.isEmpty()) {
                routeVerified.add(chunkKey);
                sVerifiedChunks.add(chunkKey);
                sVerifiedChunksVersion++;
                continue;
            }

            SimulationResult existing = sRouteSimulations.get(rKey);

            // Check if existing obstacle in this chunk was broken
            if (existing != null && existing.status == FlightStatus.COLLISION && existing.collisionPos != null) {
                int colCX = existing.collisionPos.getX() >> 4;
                int colCZ = existing.collisionPos.getZ() >> 4;
                if (chunkPosKey(colCX, colCZ) == chunkKey) {
                    Level schemWorld = getSchematicWorld();
                    BlockState cur = level.getBlockState(existing.collisionPos);
                    BlockState schemCur = (schemWorld != null) ? schemWorld.getBlockState(existing.collisionPos) : null;
                    boolean noWorldCol = cur.isAir() || cur.getCollisionShape(level, existing.collisionPos).isEmpty();
                    boolean noSchemCol = (schemCur == null || schemCur.isAir() || schemCur.getCollisionShape(schemWorld, existing.collisionPos).isEmpty());
                    if (noWorldCol && noSchemCol) {
                        existing = null;
                        sRouteSimulations.remove(rKey);
                    }
                }
            }

            // If route already collided at a tick EARLIER than any tick in this chunk, skip raycasting
            int firstTickInChunk = ticks.get(0);
            if (existing != null && existing.status == FlightStatus.COLLISION && existing.collisionTick < firstTickInChunk) {
                routeVerified.add(chunkKey);
                sVerifiedChunks.add(chunkKey);
                sVerifiedChunksVersion++;
                continue;
            }

            ObstacleHit earliestHit = null;
            int hitTick = -1;

            for (int t : ticks) {
                if (t <= 0 || t >= traj.trajectory.size()) continue;
                Vec3 p1 = traj.trajectory.get(t - 1).pos;
                Vec3 p2 = traj.trajectory.get(t).pos;
                ObstacleHit hit = testTickCollision(level, p1, p2, t, traj.solution, traj.cannonStartPos);
                if (hit != null) {
                    earliestHit = hit;
                    hitTick = t;
                    break;
                }
            }

            routeVerified.add(chunkKey);
            sVerifiedChunks.add(chunkKey);
            sVerifiedChunksVersion++;

            if (earliestHit != null) {
                if (existing == null || existing.status != FlightStatus.COLLISION || hitTick <= existing.collisionTick) {
                    long colKey = chunkPosKey(earliestHit.pos.getX() >> 4, earliestHit.pos.getZ() >> 4);
                    routeVerified.add(colKey);
                    sVerifiedChunks.add(colKey);

                    SimulationResult newSim = buildSimulationResult(
                            traj,
                            FlightStatus.COLLISION,
                            earliestHit.pos,
                            earliestHit.state,
                            earliestHit.exactPos,
                            hitTick,
                            routeVerified,
                            level
                    );
                    sRouteSimulations.put(rKey, newSim);
                }
            } else if (existing == null || existing.status != FlightStatus.COLLISION) {
                boolean allVerified = true;
                for (long reqKey : traj.traversedChunks) {
                    if (!isRouteChunkVerified(rKey, reqKey)) {
                        allVerified = false;
                        break;
                    }
                }

                FlightStatus newStatus = allVerified ? FlightStatus.CLEAR : FlightStatus.UNCHECKED;
                SimulationResult newSim = buildSimulationResult(
                        traj,
                        newStatus,
                        null,
                        null,
                        null,
                        -1,
                        routeVerified,
                        level
                );
                sRouteSimulations.put(rKey, newSim);
            }
        }
    }

    private static Level getSchematicWorld() {
        try {
            return fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static ObstacleHit testTickCollision(
            Level level,
            Vec3 prevWorld,
            Vec3 currentWorld,
            int t,
            PearlCalculator.CalculationResult solution,
            BlockPos cannonStartPos
    ) {
        if (level == null) return null;
        try {
            Vec3 move = currentWorld.subtract(prevWorld);
            double hLen = Math.sqrt(move.x * move.x + move.z * move.z);
            double perpX = (hLen > 1e-6) ? (-move.z / hLen) : 0.0;
            double perpZ = (hLen > 1e-6) ? (move.x / hLen) : 0.0;
            double radius = 0.12;

            Vec3[] rayOffsets = new Vec3[]{
                    Vec3.ZERO,
                    new Vec3(perpX * radius, 0, perpZ * radius),
                    new Vec3(-perpX * radius, 0, -perpZ * radius),
                    new Vec3(0, radius, 0),
                    new Vec3(0, -radius, 0)
            };

            Level schemWorld = getSchematicWorld();

            for (Vec3 offset : rayOffsets) {
                Vec3 rFrom = prevWorld.add(offset);
                Vec3 rTo = currentWorld.add(offset);
                Vec3 rayDir = rTo.subtract(rFrom);
                double rayDist = rayDir.length();
                if (rayDist < 1e-4) continue;
                Vec3 rayStep = rayDir.scale(1.0 / rayDist);

                Vec3 currentFrom = rFrom;
                for (int iter = 0; iter < 10 && currentFrom != null && currentFrom.distanceTo(rTo) > 0.05; iter++) {
                    ClipContext ctx = new ClipContext(
                            currentFrom,
                            rTo,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            CollisionContext.empty()
                    );

                    BlockHitResult worldHit = level.clip(ctx);
                    BlockPos worldHitPos = (worldHit != null && worldHit.getType() == HitResult.Type.BLOCK) ? worldHit.getBlockPos() : null;
                    if (worldHitPos != null && !isChunkLoaded(level, worldHitPos.getX() >> 4, worldHitPos.getZ() >> 4)) {
                        currentFrom = worldHit.getLocation().add(rayStep.scale(0.5));
                        continue;
                    }

                    BlockHitResult schemHit = (schemWorld != null) ? schemWorld.clip(ctx) : null;
                    BlockPos schemHitPos = (schemHit != null && schemHit.getType() == HitResult.Type.BLOCK) ? schemHit.getBlockPos() : null;
                    if (schemHitPos != null && !isChunkLoaded(level, schemHitPos.getX() >> 4, schemHitPos.getZ() >> 4)) {
                        currentFrom = schemHit.getLocation().add(rayStep.scale(0.5));
                        continue;
                    }

                    BlockHitResult hit = null;
                    BlockPos hitPos = null;
                    BlockState hitState = null;

                    if (worldHitPos != null && schemHitPos != null) {
                        double dWorld = worldHit.getLocation().distanceToSqr(currentFrom);
                        double dSchem = schemHit.getLocation().distanceToSqr(currentFrom);
                        if (dSchem < dWorld) {
                            hit = schemHit;
                            hitPos = schemHitPos;
                            hitState = schemWorld.getBlockState(schemHitPos);
                        } else {
                            hit = worldHit;
                            hitPos = worldHitPos;
                            hitState = level.getBlockState(worldHitPos);
                        }
                    } else if (worldHitPos != null) {
                        hit = worldHit;
                        hitPos = worldHitPos;
                        hitState = level.getBlockState(worldHitPos);
                    } else if (schemHitPos != null) {
                        hit = schemHit;
                        hitPos = schemHitPos;
                        hitState = schemWorld.getBlockState(schemHitPos);
                    }

                    if (hit == null || hitPos == null || hitState == null || hitState.isAir()) {
                        break;
                    }

                    boolean isLaunchChamberRest = (t == 1 && prevWorld.distanceTo(hit.getLocation()) < 0.45);
                    if (isLaunchChamberRest) {
                        currentFrom = hit.getLocation().add(rayStep.scale(0.08));
                        continue;
                    }

                    boolean isTargetCatcher = (t >= solution.tick
                            && (hitPos.equals(solution.catchBlockPos) || hitPos.distManhattan(solution.catchBlockPos) <= 1));

                    if (!isTargetCatcher) {
                        return new ObstacleHit(hitPos, hitState, hit.getLocation());
                    } else {
                        break;
                    }
                }
            }

            return checkNonColliderObstacles(level, schemWorld, prevWorld, currentWorld, t, solution, cannonStartPos);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SimulationResult buildSimulationResult(
            RouteTrajectory traj,
            FlightStatus status,
            BlockPos collisionPos,
            BlockState collisionState,
            Vec3 collisionExact,
            int collisionTick,
            Set<Long> routeVerified,
            Level level
    ) {
        int loaded = 0;
        int unloaded = 0;
        for (long key : traj.traversedChunks) {
            int cx = chunkKeyX(key);
            int cz = chunkKeyZ(key);
            if (isRouteChunkVerified(traj.routeKey, key) || (level != null && isChunkLoaded(level, cx, cz))) {
                loaded++;
            } else {
                unloaded++;
            }
        }

        double colDistStart = 0.0;
        double colDistCatch = 0.0;
        if (collisionPos != null) {
            double dx = collisionPos.getX() - traj.cannonStartPos.getX();
            double dz = collisionPos.getZ() - traj.cannonStartPos.getZ();
            colDistStart = Math.sqrt(dx * dx + dz * dz);

            double cdx = collisionPos.getX() - traj.solution.catchBlockPos.getX();
            double cdz = collisionPos.getZ() - traj.solution.catchBlockPos.getZ();
            colDistCatch = Math.sqrt(cdx * cdx + cdz * cdz);
        }

        return new SimulationResult(
                status,
                traj.solution,
                traj.cannonStartPos,
                collisionPos,
                collisionState,
                collisionExact,
                collisionTick,
                colDistStart,
                colDistCatch,
                traj.solution.tick,
                traj.maxAltitude,
                traj.traversedChunks.size(),
                loaded,
                unloaded,
                traj.trajectory,
                traj.traversedChunks
        );
    }

    private static void addChunksAlongSegmentWithRadius(
            Vec3 p1, Vec3 p2, double radius,
            Set<Long> chunks, Map<Long, List<Integer>> ticksByChunk, int tick
    ) {
        double dist = Math.hypot(p2.x - p1.x, p2.z - p1.z);
        int steps = Math.max(1, (int) Math.ceil(dist));
        for (int i = 0; i <= steps; i++) {
            double frac = (double) i / steps;
            double x = p1.x + (p2.x - p1.x) * frac;
            double z = p1.z + (p2.z - p1.z) * frac;

            int minCX = ((int) Math.floor(x - radius)) >> 4;
            int maxCX = ((int) Math.floor(x + radius)) >> 4;
            int minCZ = ((int) Math.floor(z - radius)) >> 4;
            int maxCZ = ((int) Math.floor(z + radius)) >> 4;

            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    long k = chunkPosKey(cx, cz);
                    chunks.add(k);
                    if (ticksByChunk != null) {
                        List<Integer> list = ticksByChunk.computeIfAbsent(k, key -> new ArrayList<>());
                        if (list.isEmpty() || list.get(list.size() - 1) != tick) {
                            list.add(tick);
                        }
                    }
                }
            }
        }
    }

    private static ObstacleHit checkNonColliderObstacles(
            Level level,
            Level schemWorld,
            Vec3 p1,
            Vec3 p2,
            int tick,
            PearlCalculator.CalculationResult solution,
            BlockPos cannonStartPos
    ) {
        int x0 = (int) Math.floor(p1.x);
        int y0 = (int) Math.floor(p1.y);
        int z0 = (int) Math.floor(p1.z);

        int x1 = (int) Math.floor(p2.x);
        int y1 = (int) Math.floor(p2.y);
        int z1 = (int) Math.floor(p2.z);

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int dz = Math.abs(z1 - z0);

        int maxDiff = Math.max(dx, Math.max(dy, dz));
        if (maxDiff <= 0) return null;

        int samples = Math.min(maxDiff * 2, 200);
        BlockPos lastPos = null;

        for (int i = 0; i <= samples; i++) {
            double frac = (double) i / samples;
            int bx = (int) Math.floor(p1.x + (p2.x - p1.x) * frac);
            int by = (int) Math.floor(p1.y + (p2.y - p1.y) * frac);
            int bz = (int) Math.floor(p1.z + (p2.z - p1.z) * frac);

            BlockPos bPos = new BlockPos(bx, by, bz);
            if (bPos.equals(lastPos)) continue;
            lastPos = bPos;

            int cx = bx >> 4;
            int cz = bz >> 4;
            if (!isChunkLoaded(level, cx, cz)) continue;

            BlockState state = level.getBlockState(bPos);
            if (state.isAir() && schemWorld != null) {
                state = schemWorld.getBlockState(bPos);
            }
            if (state != null && (state.is(Blocks.COBWEB) || state.is(Blocks.POWDER_SNOW)
                    || state.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock
                    || state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock)) {
                boolean isTargetCatcher = (tick >= solution.tick
                        && (bPos.equals(solution.catchBlockPos) || bPos.distManhattan(solution.catchBlockPos) <= 1));

                boolean isStartingRestBlock = (tick == 1 && bPos.equals(BlockPos.containing(p1)));

                if (!isStartingRestBlock && !isTargetCatcher) {
                    return new ObstacleHit(bPos, state, Vec3.atCenterOf(bPos));
                }
            }
        }
        return null;
    }

    private static Vec3 toWorld(PearlCalculator.Space3D p, BlockPos cannonStartPos, PearlCalculator.CannonSettings settings) {
        double wy = cannonStartPos.getY() >= 150
                ? cannonStartPos.getY() + (p.y - settings.pearlAdjustedY)
                : p.y;
        return new Vec3(cannonStartPos.getX() + p.x, wy, cannonStartPos.getZ() + p.z);
    }

    public static long chunkPosKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (((long) chunkZ) & 0xFFFFFFFFL);
    }

    public static int chunkKeyX(long key) {
        return (int) (key >> 32);
    }

    public static int chunkKeyZ(long key) {
        return (int) key;
    }
}
