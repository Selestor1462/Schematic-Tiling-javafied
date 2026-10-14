package schematic.tiling.cannon;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

public class PearlCalculator {

    public static final double PEARL_GRAVITY_ACCELERATION = 0.03;
    public static final double PEARL_DRAG_MULTIPLIER = 0.99;
    public static final double TNT_EXPLOSION_RADIUS = 8.0;
    public static final double TNT_ENTITY_Y_OFFSET = 0.06125;
    public static final double PEARL_HEIGHT = 0.25;
    public static final double PEARL_EXPLOSION_Y_FACTOR = 0.85;
    public static final double FLOAT_PRECISION_EPSILON = 1e-10;
    public static final int NETHER_ROOF_MIN_Y = 128;

    public enum Direction {
        NORTH(1, "North", Rotation.NONE),
        SOUTH(2, "South", Rotation.CLOCKWISE_180),
        EAST(4, "East", Rotation.CLOCKWISE_90),
        WEST(8, "West", Rotation.COUNTERCLOCKWISE_90);

        public final int bit;
        public final String name;
        public final Rotation rotation;

        Direction(int bit, String name, Rotation rotation) {
            this.bit = bit;
            this.name = name;
            this.rotation = rotation;
        }

        public Rotation getAlignerRotation() {
            return switch (this) {
                case NORTH -> Rotation.NONE;
                case EAST -> Rotation.CLOCKWISE_90;
                case SOUTH -> Rotation.CLOCKWISE_180;
                case WEST -> Rotation.COUNTERCLOCKWISE_90;
            };
        }

        public Rotation getBasketRotation() {
            return this.rotation;
        }

        public Direction invert() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case EAST -> WEST;
                case WEST -> EAST;
            };
        }

        public static Direction fromAngle(double angle) {
            if (angle >= -135.0 && angle < -45.0) {
                return EAST;
            } else if (angle >= -45.0 && angle < 45.0) {
                return SOUTH;
            } else if (angle >= 45.0 && angle < 135.0) {
                return WEST;
            } else {
                return NORTH;
            }
        }

        public static List<Direction> fromAngleWithFallbacks(double angle) {
            double eps = 10.0;
            if (Math.abs(angle - (-45.0)) < eps) {
                return List.of(SOUTH, EAST);
            } else if (Math.abs(angle - 45.0) < eps) {
                return List.of(WEST, SOUTH);
            } else if (Math.abs(angle - 135.0) < eps) {
                return List.of(NORTH, WEST);
            } else if (Math.abs(angle - (-135.0)) < eps) {
                return List.of(EAST, NORTH);
            } else {
                return List.of(fromAngle(angle));
            }
        }
    }

    public enum LayoutDirection {
        NORTH_WEST(Direction.NORTH.bit | Direction.WEST.bit),
        NORTH_EAST(Direction.NORTH.bit | Direction.EAST.bit),
        SOUTH_WEST(Direction.SOUTH.bit | Direction.WEST.bit),
        SOUTH_EAST(Direction.SOUTH.bit | Direction.EAST.bit);

        public final int bits;

        LayoutDirection(int bits) {
            this.bits = bits;
        }

        public static LayoutDirection fromBits(int bits) {
            int n = Direction.NORTH.bit;
            int s = Direction.SOUTH.bit;
            int w = Direction.WEST.bit;
            int e = Direction.EAST.bit;
            if ((bits & (n | w)) == (n | w)) return NORTH_WEST;
            if ((bits & (n | e)) == (n | e)) return NORTH_EAST;
            if ((bits & (s | w)) == (s | w)) return SOUTH_WEST;
            return SOUTH_EAST;
        }
    }

    public static class Space3D {
        public final double x;
        public final double y;
        public final double z;

        public Space3D() {
            this(0.0, 0.0, 0.0);
        }

        public Space3D(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Space3D add(Space3D o) {
            return new Space3D(this.x + o.x, this.y + o.y, this.z + o.z);
        }

        public Space3D subtract(Space3D o) {
            return new Space3D(this.x - o.x, this.y - o.y, this.z - o.z);
        }

        public Space3D multiply(double s) {
            return new Space3D(this.x * s, this.y * s, this.z * s);
        }

        public Space3D divide(double s) {
            return new Space3D(this.x / s, this.y / s, this.z / s);
        }

        public double lengthSq() {
            return this.x * this.x + this.y * this.y + this.z * this.z;
        }

        public double length() {
            return Math.sqrt(this.lengthSq());
        }

        public double distance2D(Space3D o) {
            return Math.sqrt(this.distance2DSq(o));
        }

        public double distance2DSq(Space3D o) {
            double dx = this.x - o.x;
            double dz = this.z - o.z;
            return dx * dx + dz * dz;
        }

        public double angleToYaw(Space3D o) {
            double dx = o.x - this.x;
            double dz = o.z - this.z;
            return Math.toDegrees(Math.atan2(-dx, dz));
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", this.x, this.y, this.z);
        }
    }

    public static class CannonSettings {
        public Space3D pearlPos = new Space3D(0.0, 172.47373832068837, 0.0);
        public Space3D pearlMotion = new Space3D(0.0, 0.34301973395906404, 0.0);
        public Space3D nwTNT = new Space3D(-0.884999990463257, 172.625, -0.884999990463257);
        public Space3D neTNT = new Space3D(0.884999990463257, 172.625, -0.884999990463257);
        public Space3D swTNT = new Space3D(-0.884999990463257, 172.625, 0.884999990463257);
        public Space3D seTNT = new Space3D(0.884999990463257, 172.625, 0.884999990463257);
        public LayoutDirection defaultRed = LayoutDirection.SOUTH_EAST;
        public LayoutDirection defaultBlue = LayoutDirection.NORTH_WEST;
        public int maxTNT = 209;
        public double pearlAdjustedY = 170.0;

        public int getStartY() {
            return (int) Math.round(this.pearlAdjustedY);
        }
    }

    public static CannonSettings loadSettings(Path gameDirectory) {
        List<Path> candidates = new ArrayList<>();
        if (gameDirectory != null) {
            candidates.add(gameDirectory.resolve("return_cannon").resolve("return cannon settings.json"));
            candidates.add(gameDirectory.resolve("schematics").resolve("return_cannon").resolve("return cannon settings.json"));
            candidates.add(gameDirectory.resolve("projects").resolve("return_cannon").resolve("return cannon settings.json"));
        }
        candidates.add(Path.of("return_cannon").resolve("return cannon settings.json"));

        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try (Reader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    return parseSettingsJson(reader);
                } catch (Exception ignored) {}
            }
        }

        try (InputStream is = PearlCalculator.class.getResourceAsStream("/return_cannon/return cannon settings.json")) {
            if (is != null) {
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    return parseSettingsJson(reader);
                }
            }
        } catch (Exception ignored) {}

        return new CannonSettings();
    }

    public static CannonSettings parseSettingsJson(Reader reader) {
        CannonSettings settings = new CannonSettings();
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("CannonSettings") && root.getAsJsonArray("CannonSettings").size() > 0) {
                JsonObject cs = root.getAsJsonArray("CannonSettings").get(0).getAsJsonObject();
                if (cs.has("MaxTNT")) {
                    settings.maxTNT = cs.get("MaxTNT").getAsInt();
                }
                if (cs.has("DefaultRedDirection")) {
                    settings.defaultRed = parseLayoutDirection(cs.get("DefaultRedDirection").getAsString());
                }
                if (cs.has("DefaultBlueDirection")) {
                    settings.defaultBlue = parseLayoutDirection(cs.get("DefaultBlueDirection").getAsString());
                }
                if (cs.has("NorthWestTNT")) {
                    settings.nwTNT = parseSpace3D(cs.getAsJsonObject("NorthWestTNT"));
                }
                if (cs.has("NorthEastTNT")) {
                    settings.neTNT = parseSpace3D(cs.getAsJsonObject("NorthEastTNT"));
                }
                if (cs.has("SouthWestTNT")) {
                    settings.swTNT = parseSpace3D(cs.getAsJsonObject("SouthWestTNT"));
                }
                if (cs.has("SouthEastTNT")) {
                    settings.seTNT = parseSpace3D(cs.getAsJsonObject("SouthEastTNT"));
                }
                if (cs.has("Pearl")) {
                    JsonObject pearl = cs.getAsJsonObject("Pearl");
                    if (pearl.has("Position")) {
                        settings.pearlPos = parseSpace3D(pearl.getAsJsonObject("Position"));
                    }
                    if (pearl.has("Motion")) {
                        settings.pearlMotion = parseSpace3D(pearl.getAsJsonObject("Motion"));
                    }
                }
                if (cs.has("PearlYPositionAdjusted")) {
                    settings.pearlAdjustedY = cs.get("PearlYPositionAdjusted").getAsDouble();
                }
            }
        } catch (Exception ignored) {}
        return settings;
    }

    private static Space3D parseSpace3D(JsonObject obj) {
        double x = obj.has("X") ? obj.get("X").getAsDouble() : 0.0;
        double y = obj.has("Y") ? obj.get("Y").getAsDouble() : 0.0;
        double z = obj.has("Z") ? obj.get("Z").getAsDouble() : 0.0;
        return new Space3D(x, y, z);
    }

    public static LayoutDirection parseLayoutDirection(String s) {
        if (s == null) return LayoutDirection.SOUTH_EAST;
        String normalized = s.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        try {
            return LayoutDirection.valueOf(normalized);
        } catch (Exception e) {
            return LayoutDirection.SOUTH_EAST;
        }
    }

    public static class CalculationResult {
        public final int redTNT;
        public final int blueTNT;
        public final int redDuperSize;
        public final int blueDuperSize;
        public final Direction direction;
        public final int tick;
        public final double distance;
        public final Space3D worldArrivalPos;
        public final Space3D worldCatchPos;
        public final BlockPos catchBlockPos;

        public CalculationResult(
                int redTNT, int blueTNT,
                int redDuperSize, int blueDuperSize,
                Direction direction, int tick,
                double distance,
                Space3D worldArrivalPos,
                Space3D worldCatchPos,
                BlockPos catchBlockPos
        ) {
            this.redTNT = redTNT;
            this.blueTNT = blueTNT;
            this.redDuperSize = redDuperSize;
            this.blueDuperSize = blueDuperSize;
            this.direction = direction;
            this.tick = tick;
            this.distance = distance;
            this.worldArrivalPos = worldArrivalPos;
            this.worldCatchPos = worldCatchPos;
            this.catchBlockPos = catchBlockPos;
        }

        public int getRedExcess() {
            return this.redDuperSize - this.redTNT;
        }

        public int getBlueExcess() {
            return this.blueDuperSize - this.blueTNT;
        }

        public int getTotalTNT() {
            return this.redTNT + this.blueTNT;
        }
    }

    public static int roundUpToDuperSize(int tnt) {
        if (tnt <= 0) return 11;
        // duper sizes are 11, 22, 33, ... 209
        int multiples = (tnt + 10) / 11;
        return Math.min(209, Math.max(11, multiples * 11));
    }

    public static Space3D calculateTNTMotion(Space3D pearlPos, Space3D tntPos) {
        Space3D tntAdj = new Space3D(tntPos.x, tntPos.y + TNT_ENTITY_Y_OFFSET, tntPos.z);
        Space3D distVec = pearlPos.subtract(tntAdj);
        double dist = distVec.length();
        if (dist >= TNT_EXPLOSION_RADIUS) {
            return new Space3D();
        }
        Space3D expVec = new Space3D(
                distVec.x,
                pearlPos.y + (PEARL_EXPLOSION_Y_FACTOR * PEARL_HEIGHT) - tntAdj.y,
                distVec.z
        );
        double expLen = expVec.length();
        if (Math.abs(expLen) < FLOAT_PRECISION_EPSILON) {
            return new Space3D();
        }
        expVec = expVec.divide(expLen);
        double expStrength = 1.0 - (dist / TNT_EXPLOSION_RADIUS);
        return expVec.multiply(expStrength);
    }

    public static Space3D[] runPost1212Tick(Space3D pos, Space3D motion) {
        Space3D newMotion = new Space3D(
                motion.x * PEARL_DRAG_MULTIPLIER,
                (motion.y - PEARL_GRAVITY_ACCELERATION) * PEARL_DRAG_MULTIPLIER,
                motion.z * PEARL_DRAG_MULTIPLIER
        );
        Space3D newPos = pos.add(newMotion);
        return new Space3D[]{newPos, newMotion};
    }

    private static Space3D tntLocFromLayout(CannonSettings cannon, LayoutDirection dir) {
        return switch (dir) {
            case NORTH_WEST -> cannon.nwTNT;
            case NORTH_EAST -> cannon.neTNT;
            case SOUTH_WEST -> cannon.swTNT;
            case SOUTH_EAST -> cannon.seTNT;
        };
    }

    public static Space3D[] resolveVectorsForDirection(CannonSettings cannon, Direction direction) {
        LayoutDirection blueDuper = cannon.defaultBlue;
        LayoutDirection redDuper = cannon.defaultRed;

        Space3D redTNTLoc;
        Space3D blueTNTLoc;

        int blueBits = blueDuper.bits;
        int dirBit = direction.bit;
        int invDirBit = direction.invert().bit;

        if ((dirBit & blueBits) == 0) {
            blueTNTLoc = tntLocFromLayout(cannon, blueDuper);
            int otherBits = (~(dirBit | blueBits)) & 0b1111;
            int finalBits = otherBits | invDirBit;
            redTNTLoc = tntLocFromLayout(cannon, LayoutDirection.fromBits(finalBits));
        } else {
            redTNTLoc = tntLocFromLayout(cannon, redDuper);
            int redBits = redDuper.bits;
            int otherBits = (~(dirBit | redBits)) & 0b1111;
            int finalBits = otherBits | invDirBit;
            blueTNTLoc = tntLocFromLayout(cannon, LayoutDirection.fromBits(finalBits));
        }

        Space3D redVec = calculateTNTMotion(cannon.pearlPos, redTNTLoc);
        Space3D blueVec = calculateTNTMotion(cannon.pearlPos, blueTNTLoc);
        return new Space3D[]{redVec, blueVec};
    }

    /**
     * Solves for all TNT candidates given world start coordinates and destination coordinates.
     */
    public static List<CalculationResult> calculateSolutions(
            BlockPos cannonStartPos,
            double destX,
            double destZ,
            CannonSettings settings,
            int maxTicks,
            double maxDistance
    ) {
        // Relative destination from cannon position
        double relDestX = destX - cannonStartPos.getX();
        double relDestZ = destZ - cannonStartPos.getZ();
        Space3D destPosRel = new Space3D(relDestX, 0.0, relDestZ);

        Space3D pearlStartPos = settings.pearlPos;
        double yaw = pearlStartPos.angleToYaw(destPosRel);
        List<Direction> flightDirections = Direction.fromAngleWithFallbacks(yaw);

        List<CalculationResult> results = new ArrayList<>();
        double maxDistSq = maxDistance * maxDistance;

        for (Direction flightDir : flightDirections) {
            Space3D[] vecs = resolveVectorsForDirection(settings, flightDir);
            Space3D redVec = vecs[0];
            Space3D blueVec = vecs[1];

            double denominator = redVec.z * blueVec.x - blueVec.z * redVec.x;
            if (Math.abs(denominator) < FLOAT_PRECISION_EPSILON) {
                continue;
            }

            Space3D trueDistance = destPosRel.subtract(pearlStartPos);
            double dragMultiplier = PEARL_DRAG_MULTIPLIER;
            double denominatorConstant = 1.0 - dragMultiplier;
            double projection = dragMultiplier; // Post1212

            Map<Long, List<Integer>> theoreticalGroups = new HashMap<>();
            double simGravVel = 0.0;
            double simGravPos = 0.0;
            Space3D simMotionVel = settings.pearlMotion;
            Space3D simMotionPos = new Space3D();

            for (int tick = 1; tick <= maxTicks; tick++) {
                simGravVel = (simGravVel - PEARL_GRAVITY_ACCELERATION) * dragMultiplier;
                simGravPos += simGravVel;

                simMotionVel = new Space3D(
                        simMotionVel.x * dragMultiplier,
                        simMotionVel.y * dragMultiplier,
                        simMotionVel.z * dragMultiplier
                );
                simMotionPos = simMotionPos.add(simMotionVel);

                // Stop considering ticks once the pearl drops below the Nether roof (Y < 128)
                double approxPearlY = pearlStartPos.y + simGravPos + simMotionPos.y;
                double approxWorldY = cannonStartPos.getY() + (approxPearlY - settings.pearlAdjustedY);
                if (approxWorldY < NETHER_ROOF_MIN_Y) {
                    break;
                }

                Space3D compDist = new Space3D(
                        trueDistance.x - simMotionPos.x,
                        trueDistance.y - (simGravPos + simMotionPos.y),
                        trueDistance.z - simMotionPos.z
                );

                double numerator = 1.0 - Math.pow(dragMultiplier, tick);
                double divider = projection * numerator / denominatorConstant;

                double trueRed = (compDist.z * blueVec.x - compDist.x * blueVec.z) / denominator;
                double trueBlue = (compDist.x - trueRed * redVec.x) / blueVec.x;

                int rCand = (int) Math.round(trueRed / divider);
                int bCand = (int) Math.round(trueBlue / divider);

                if (rCand >= 0 && bCand >= 0) {
                    long key = (((long) rCand) << 32) | (bCand & 0xFFFFFFFFL);
                    theoreticalGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(tick);
                }
            }

            // Expand candidates around search radius 5
            int searchRadius = 5;
            Map<Long, Set<Integer>> candidateMap = new HashMap<>();

            for (Map.Entry<Long, List<Integer>> entry : theoreticalGroups.entrySet()) {
                int cr = (int) (entry.getKey() >> 32);
                int cb = (int) (entry.getKey() & 0xFFFFFFFFL);
                List<Integer> ticks = entry.getValue();

                for (int ro = -searchRadius; ro <= searchRadius; ro++) {
                    for (int bo = -searchRadius; bo <= searchRadius; bo++) {
                        int r = cr + ro;
                        int b = cb + bo;
                        if (r < 0 || b < 0) continue;
                        if (settings.maxTNT > 0 && Math.max(r, b) > settings.maxTNT) continue;

                        long key = (((long) r) << 32) | (b & 0xFFFFFFFFL);
                        candidateMap.computeIfAbsent(key, k -> new HashSet<>()).addAll(ticks);
                    }
                }
            }

            // Validate candidates through trajectory simulation
            for (Map.Entry<Long, Set<Integer>> entry : candidateMap.entrySet()) {
                int r = (int) (entry.getKey() >> 32);
                int b = (int) (entry.getKey() & 0xFFFFFFFFL);
                Set<Integer> validTicks = entry.getValue();

                int maxSimTick = 0;
                for (int t : validTicks) {
                    if (t > maxSimTick) maxSimTick = t;
                }
                if (maxSimTick == 0) continue;

                Space3D tntImpact = redVec.multiply(r).add(blueVec.multiply(b));
                Space3D mot = settings.pearlMotion.add(tntImpact);
                Space3D pos = pearlStartPos;

                int bestTick = -1;
                double bestDistSq = Double.MAX_VALUE;
                Space3D bestPos = null;
                Space3D bestMot = null;

                for (int tick = 1; tick <= maxSimTick; tick++) {
                    Space3D[] step = runPost1212Tick(pos, mot);
                    pos = step[0];
                    mot = step[1];

                    // If the pearl fell below the Nether roof, it collided with bedrock
                    double currentWorldY = cannonStartPos.getY() + (pos.y - settings.pearlAdjustedY);
                    if (currentWorldY < NETHER_ROOF_MIN_Y) {
                        break;
                    }

                    if (validTicks.contains(tick)) {
                        double dSq = pos.distance2DSq(destPosRel);
                        if (dSq <= maxDistSq && dSq < bestDistSq) {
                            bestDistSq = dSq;
                            bestTick = tick;
                            bestPos = pos;
                            bestMot = mot;
                        }
                    }

                    if (mot.lengthSq() < FLOAT_PRECISION_EPSILON) break;
                }

                if (bestTick > 0 && bestPos != null) {
                    // Simulate tick T+1 to get catch block location
                    Space3D[] nextStep = runPost1212Tick(bestPos, bestMot);
                    Space3D catchPosRel = nextStep[0];
                    Space3D catchMotRel = nextStep[1];

                    double worldArrivalY = cannonStartPos.getY() >= 150
                            ? cannonStartPos.getY() + (bestPos.y - settings.pearlAdjustedY)
                            : bestPos.y;
                    double worldCatchY = cannonStartPos.getY() >= 150
                            ? cannonStartPos.getY() + (catchPosRel.y - settings.pearlAdjustedY)
                            : catchPosRel.y;

                    // Convert to world coordinates
                    Space3D worldArrival = new Space3D(
                            cannonStartPos.getX() + bestPos.x,
                            worldArrivalY,
                            cannonStartPos.getZ() + bestPos.z
                    );
                    Space3D worldCatch = new Space3D(
                            cannonStartPos.getX() + catchPosRel.x,
                            worldCatchY,
                            cannonStartPos.getZ() + catchPosRel.z
                    );
                    int catchX = (int) Math.floor(worldCatch.x);
                    int catchY = (int) Math.floor(worldCatch.y);
                    int catchZ = (int) Math.floor(worldCatch.z);
                    BlockPos catchBlock = new BlockPos(catchX, catchY, catchZ);

                    // Stopper block and arrival point must be on or above the Nether roof (Y >= 128)
                    if (catchBlock.getY() < NETHER_ROOF_MIN_Y || worldArrival.y < NETHER_ROOF_MIN_Y) {
                        continue;
                    }

                    int rDuperSize = roundUpToDuperSize(r);
                    int bDuperSize = roundUpToDuperSize(b);

                    results.add(new CalculationResult(
                            r, b,
                            rDuperSize, bDuperSize,
                            flightDir,
                            bestTick,
                            Math.sqrt(bestDistSq),
                            worldArrival,
                            worldCatch,
                            catchBlock
                    ));
                }
            }
        }

        // De-duplicate by (red, blue, direction), keeping lowest distance
        Map<String, CalculationResult> uniqueResults = new HashMap<>();
        for (CalculationResult res : results) {
            String key = res.redTNT + ":" + res.blueTNT + ":" + res.direction;
            CalculationResult existing = uniqueResults.get(key);
            if (existing == null || res.distance < existing.distance || (Math.abs(res.distance - existing.distance) < FLOAT_PRECISION_EPSILON && res.tick < existing.tick)) {
                uniqueResults.put(key, res);
            }
        }

        List<CalculationResult> sorted = new ArrayList<>(uniqueResults.values());
        sorted.sort(Comparator.comparingDouble((CalculationResult a) -> a.distance)
                .thenComparingInt(a -> a.tick));

        return sorted;
    }
}

