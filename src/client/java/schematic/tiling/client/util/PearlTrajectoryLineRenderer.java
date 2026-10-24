package schematic.tiling.client.util;

import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;

import schematic.tiling.cannon.PearlCalculator.Space3D;
import schematic.tiling.cannon.PearlFlightSimulator;
import schematic.tiling.cannon.PearlFlightSimulator.SimulationPoint;
import schematic.tiling.cannon.PearlFlightSimulator.SimulationResult;

/**
 * High-performance 3D line and marker renderer for pearl flight trajectories.
 * Renders glowing 3D beam ribbons, visible per-tick node crystals, prominent collision wireframes, and target markers.
 * Disabled by default; only draws when explicitly requested by user.
 */
public class PearlTrajectoryLineRenderer implements IClientTickHandler {

    private static final PearlTrajectoryLineRenderer INSTANCE = new PearlTrajectoryLineRenderer();
    private static BufferBuilder lineBuffer;

    private static SimulationResult currentSim = null;
    private static boolean sEnabled = false;
    private static int remainingTicks = 0;
    private static boolean initialized = false;
    private static boolean registeredTick = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PearlTrajectoryLineRenderer::renderWorld);
    }

    public static boolean isLineDrawingEnabled() {
        return sEnabled;
    }

    public static void setLineDrawingEnabled(boolean enabled) {
        sEnabled = enabled;
        if (!enabled) {
            clearTrajectory();
        }
    }

    public static void displayTrajectory(SimulationResult sim, int durationTicks) {
        init();
        currentSim = sim;
        sEnabled = true;
        remainingTicks = durationTicks;
        if (!registeredTick) {
            TickHandler.getInstance().registerClientTickHandler(INSTANCE);
            registeredTick = true;
        }
    }

    public static void clearTrajectory() {
        sEnabled = false;
        currentSim = null;
        remainingTicks = 0;
    }

    public static void toggleTrajectory(SimulationResult sim) {
        if (isDisplaying(sim)) {
            clearTrajectory();
        } else {
            displayTrajectory(sim, -1);
        }
    }

    public static boolean isDisplaying() {
        return sEnabled && currentSim != null;
    }

    public static boolean isDisplaying(SimulationResult sim) {
        return sEnabled && currentSim != null && (sim == null || currentSim == sim);
    }

    public static SimulationResult getCurrentSim() {
        return currentSim;
    }

    @Override
    public void onClientTick(Minecraft client) {
        if (!sEnabled || currentSim == null) return;
        if (remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks <= 0) {
                clearTrajectory();
            }
        }
    }

    private static long lastRaycastTimeMs = 0;
    private static BlockPos cachedLandingBlockPos = null;
    private static Vec3 cachedLandingGroundPoint = null;
    private static int cachedLandingBlockY = -1;

    private static void updateCachedGroundMarker(ClientLevel world, Space3D startPos, Space3D velocity, double finalX, double finalZ) {
        long now = System.currentTimeMillis();
        if (now - lastRaycastTimeMs < 1000 && cachedLandingGroundPoint != null) {
            return;
        }
        lastRaycastTimeMs = now;

        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;
        double x = startPos.x;
        double y = startPos.y;
        double z = startPos.z;

        cachedLandingGroundPoint = null;
        cachedLandingBlockPos = null;

        for (int step = 0; step < 200; step++) {
            double nextX = x + vx;
            double nextY = y + vy;
            double nextZ = z + vz;

            int bx = (int) Math.floor(nextX);
            int by = (int) Math.floor(nextY);
            int bz = (int) Math.floor(nextZ);

            if (by < -64 || by > 320) break;

            BlockPos bpos = new BlockPos(bx, by, bz);
            if (world.hasChunkAt(bpos)) {
                BlockState state = world.getBlockState(bpos);
                if (!state.isAir() && state.getBlock() != Blocks.VOID_AIR) {
                    if (state.getBlock() instanceof CarpetBlock) {
                        cachedLandingBlockPos = bpos.below();
                        cachedLandingGroundPoint = new Vec3(nextX, by + 0.0625, nextZ);
                        cachedLandingBlockY = by - 1;
                    } else {
                        cachedLandingBlockPos = bpos;
                        cachedLandingGroundPoint = new Vec3(nextX, by + 1.0, nextZ);
                        cachedLandingBlockY = by;
                    }
                    return;
                }
            }

            x = nextX;
            y = nextY;
            z = nextZ;
            vx *= 0.99;
            vy = (vy * 0.99) - 0.03;
            vz *= 0.99;
        }

        int targetY = (cachedLandingBlockY != -1) ? cachedLandingBlockY : 128;
        BlockPos fallbackPos = new BlockPos((int) Math.floor(finalX), targetY, (int) Math.floor(finalZ));
        if (world.hasChunkAt(fallbackPos)) {
            BlockState state = world.getBlockState(fallbackPos);
            if (state.getBlock() instanceof CarpetBlock) {
                cachedLandingBlockPos = fallbackPos.below();
                cachedLandingGroundPoint = new Vec3(finalX, targetY + 0.0625, finalZ);
                cachedLandingBlockY = targetY - 1;
                return;
            }
        }
        cachedLandingBlockPos = fallbackPos;
        cachedLandingGroundPoint = new Vec3(finalX, targetY + 1.0, finalZ);
    }

    public static void renderWorld(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !sEnabled || currentSim == null) return;
        if (currentSim.trajectory == null || currentSim.trajectory.size() < 2) return;

        ClientLevel world = mc.level;
        if (world == null) return;

        Vec3 camera = context.camera().getPosition();
        PoseStack matrices = context.matrixStack();

        try {
            matrices.pushPose();
            lineBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            Matrix4f pose = matrices.last().pose();
            List<SimulationPoint> pts = currentSim.trajectory;
            boolean isCollision = (currentSim.status == PearlFlightSimulator.FlightStatus.COLLISION);
            int colTick = isCollision ? currentSim.collisionTick : -1;
            int maxIdx = pts.size() - 1;

            // 1. Render glowing 3D cross-ribbon beam along the entire flight path
            float beamRadius = 0.08f;
            for (int i = 0; i < maxIdx; i++) {
                SimulationPoint p1 = pts.get(i);
                SimulationPoint p2 = pts.get(i + 1);

                float t1 = (float) i / Math.max(1, maxIdx);
                float t2 = (float) (i + 1) / Math.max(1, maxIdx);

                float[] c1 = getColorForTick(i, maxIdx, isCollision, colTick, t1);
                float[] c2 = getColorForTick(i + 1, maxIdx, isCollision, colTick, t2);

                add3DBeamSegment(pose, lineBuffer,
                        p1.pos, p2.pos, camera,
                        c1[0], c1[1], c1[2], c1[3],
                        c2[0], c2[1], c2[2], c2[3],
                        beamRadius);
            }

            // 2. Render tick node markers
            float nodeHalfSize = 0.09f;
            for (int i = 0; i <= maxIdx; i++) {
                SimulationPoint pt = pts.get(i);
                float relT = (float) i / Math.max(1, maxIdx);
                float[] col = getColorForTick(i, maxIdx, isCollision, colTick, relT);

                float cx = (float) (pt.pos.x - camera.x);
                float cy = (float) (pt.pos.y - camera.y);
                float cz = (float) (pt.pos.z - camera.z);

                addBox(pose, lineBuffer, cx, cy, cz, nodeHalfSize, col[0], col[1], col[2], 0.95f);
            }

            // 3. Render Collision block highlight
            if (isCollision && currentSim.collisionPos != null) {
                addHighlightBoxWithEdges(pose, lineBuffer, currentSim.collisionPos, camera,
                        1.0f, 0.15f, 0.15f, 0.40f,
                        1.0f, 0.05f, 0.05f, 0.95f);
            }

            // 4. Render Target/Arrival marker
            if (!isCollision && currentSim.solution != null) {
                SimulationPoint lastPt = pts.get(maxIdx);
                Space3D start = new Space3D(lastPt.pos.x, lastPt.pos.y, lastPt.pos.z);
                Space3D vel = (lastPt.motion != null) ? new Space3D(lastPt.motion.x, lastPt.motion.y, lastPt.motion.z) : new Space3D(0, 0, 0);

                updateCachedGroundMarker(world, start, vel, lastPt.pos.x, lastPt.pos.z);

                if (cachedLandingBlockPos != null) {
                    addHighlightBoxWithEdges(pose, lineBuffer, cachedLandingBlockPos, camera,
                            0.15f, 1.0f, 0.30f, 0.35f,
                            0.10f, 1.0f, 0.20f, 0.95f);
                }

                if (cachedLandingGroundPoint != null) {
                    float bx = (float) (cachedLandingGroundPoint.x - camera.x);
                    float by = (float) (cachedLandingGroundPoint.y - camera.y);
                    float bz = (float) (cachedLandingGroundPoint.z - camera.z);

                    addBox(pose, lineBuffer, bx, by, bz, 0.15f, 0.2f, 1.0f, 0.4f, 1.0f);

                    float flagHalfW = 0.04f;
                    float flagH = 1.6f;
                    addBoxFaces(pose, lineBuffer,
                            bx - flagHalfW, by, bz - flagHalfW,
                            bx + flagHalfW, by + flagH, bz + flagHalfW,
                            0.2f, 1.0f, 0.4f, 0.90f);
                }
            }

            renderAll(mc, matrices);
            matrices.popPose();
        } catch (Throwable ignored) {
            lineBuffer = null;
        }
    }

    private static float[] getColorForTick(int tick, int maxTick, boolean isCollision, int colTick, float t) {
        if (isCollision && tick >= colTick - 1) {
            return new float[]{1.0f, 0.15f, 0.15f, 0.95f};
        }

        if (tick == 0) {
            return new float[]{0.2f, 0.9f, 1.0f, 0.95f};
        }

        if (tick == maxTick) {
            return new float[]{0.2f, 1.0f, 0.3f, 0.95f};
        }

        float r = 0.2f + 0.7f * t;
        float g = 0.6f + 0.4f * (1.0f - Math.abs(t - 0.5f) * 2.0f);
        float b = 1.0f - 0.7f * t;
        return new float[]{r, g, b, 0.85f};
    }

    private static void add3DBeamSegment(
            Matrix4f matrix, BufferBuilder buffer,
            Vec3 a, Vec3 b, Vec3 camera,
            float r1, float g1, float b1, float a1,
            float r2, float g2, float b2, float a2,
            float radius
    ) {
        Vec3 p1 = a.subtract(camera);
        Vec3 p2 = b.subtract(camera);
        Vec3 dir = p2.subtract(p1);
        double len = dir.length();
        if (len < 1e-4) return;

        Vec3 uDir = dir.scale(1.0 / len);
        Vec3 perp1;
        if (Math.abs(uDir.y) < 0.95) {
            perp1 = new Vec3(uDir.z, 0, -uDir.x).normalize().scale(radius);
        } else {
            perp1 = new Vec3(0, -uDir.z, uDir.y).normalize().scale(radius);
        }
        Vec3 perp2 = uDir.cross(perp1.normalize()).scale(radius);

        // Plane 1
        Vec3 v1 = p1.subtract(perp1);
        Vec3 v2 = p1.add(perp1);
        Vec3 v3 = p2.add(perp1);
        Vec3 v4 = p2.subtract(perp1);
        addDoubleSidedQuad(matrix, buffer, v1, v2, v3, v4, r1, g1, b1, a1, r2, g2, b2, a2);

        // Plane 2
        Vec3 w1 = p1.subtract(perp2);
        Vec3 w2 = p1.add(perp2);
        Vec3 w3 = p2.add(perp2);
        Vec3 w4 = p2.subtract(perp2);
        addDoubleSidedQuad(matrix, buffer, w1, w2, w3, w4, r1, g1, b1, a1, r2, g2, b2, a2);
    }

    private static void addDoubleSidedQuad(
            Matrix4f matrix, BufferBuilder buffer,
            Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4,
            float r1, float g1, float b1, float a1,
            float r2, float g2, float b2, float a2
    ) {
        // Forward face
        buffer.addVertex(matrix, (float) v1.x, (float) v1.y, (float) v1.z).setColor(r1, g1, b1, a1);
        buffer.addVertex(matrix, (float) v2.x, (float) v2.y, (float) v2.z).setColor(r1, g1, b1, a1);
        buffer.addVertex(matrix, (float) v3.x, (float) v3.y, (float) v3.z).setColor(r2, g2, b2, a2);
        buffer.addVertex(matrix, (float) v4.x, (float) v4.y, (float) v4.z).setColor(r2, g2, b2, a2);

        // Reverse face
        buffer.addVertex(matrix, (float) v1.x, (float) v1.y, (float) v1.z).setColor(r1, g1, b1, a1);
        buffer.addVertex(matrix, (float) v4.x, (float) v4.y, (float) v4.z).setColor(r2, g2, b2, a2);
        buffer.addVertex(matrix, (float) v3.x, (float) v3.y, (float) v3.z).setColor(r2, g2, b2, a2);
        buffer.addVertex(matrix, (float) v2.x, (float) v2.y, (float) v2.z).setColor(r1, g1, b1, a1);
    }

    private static void addBox(
            Matrix4f matrix, BufferBuilder buffer,
            float cx, float cy, float cz, float halfSize,
            float r, float g, float b, float a
    ) {
        float x1 = cx - halfSize;
        float x2 = cx + halfSize;
        float y1 = cy - halfSize;
        float y2 = cy + halfSize;
        float z1 = cz - halfSize;
        float z2 = cz + halfSize;

        addBoxFaces(matrix, buffer, x1, y1, z1, x2, y2, z2, r, g, b, a);
    }

    private static void addHighlightBoxWithEdges(
            Matrix4f matrix, BufferBuilder buffer,
            BlockPos pos, Vec3 camera,
            float fr, float fg, float fb, float fa,
            float er, float eg, float eb, float ea
    ) {
        float x1 = (float) (pos.getX() - camera.x);
        float y1 = (float) (pos.getY() - camera.y);
        float z1 = (float) (pos.getZ() - camera.z);
        float x2 = x1 + 1.0f;
        float y2 = y1 + 1.0f;
        float z2 = z1 + 1.0f;

        // Semi-transparent interior box faces
        addBoxFaces(matrix, buffer, x1, y1, z1, x2, y2, z2, fr, fg, fb, fa);

        // Prominent 3D edge beams for high visibility
        float edgeRadius = 0.025f;

        // 4 Bottom edges
        addEdgeBeam(matrix, buffer, x1, y1, z1, x2, y1, z1, edgeRadius, er, eg, eb, ea);
        addEdgeBeam(matrix, buffer, x2, y1, z1, x2, y1, z2, edgeRadius, er, eg, eb, ea);
        addEdgeBeam(matrix, buffer, x2, y1, z2, x1, y1, z2, edgeRadius, er, eg, eb, ea);
        addEdgeBeam(matrix, buffer, x1, y1, z2, x1, y1, z1, edgeRadius, er, eg, eb, ea);

        // 4 Top edges
        addEdgeBeam(matrix, buffer, x1, y2, z1, x2, y2, z1, edgeRadius, er, eg, eb, ea);
        addEdgeBeam(matrix, buffer, x2, y2, z1, x2, y2, z2, edgeRadius, er, eg, eb, ea);
        addEdgeBeam(matrix, buffer, x2, y2, z2, x1, y2, z2, edgeRadius, er, eg, eb, ea);
        addEdgeBeam(matrix, buffer, x1, y2, z2, x1, y2, z1, edgeRadius, er, eg, eb, ea);

        // 4 Vertical pillar edges
        addEdgeBeam(matrix, buffer, x1, y1, z1, x1, y2, z1, edgeRadius, er, eg, eb, ea);
        addEdgeBeam(matrix, buffer, x2, y1, z1, x2, y2, z1, edgeRadius, er, eg, eb, ea);
        addEdgeBeam(matrix, buffer, x2, y1, z2, x2, y2, z2, edgeRadius, er, eg, eb, ea);
        addEdgeBeam(matrix, buffer, x1, y1, z2, x1, y2, z2, edgeRadius, er, eg, eb, ea);
    }

    private static void addEdgeBeam(
            Matrix4f matrix, BufferBuilder buffer,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float radius, float r, float g, float b, float a
    ) {
        float minX = Math.min(x1, x2) - radius;
        float maxX = Math.max(x1, x2) + radius;
        float minY = Math.min(y1, y2) - radius;
        float maxY = Math.max(y1, y2) + radius;
        float minZ = Math.min(z1, z2) - radius;
        float maxZ = Math.max(z1, z2) + radius;

        addBoxFaces(matrix, buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);
    }

    private static void addBoxFaces(
            Matrix4f matrix, BufferBuilder buffer,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float r, float g, float b, float a
    ) {
        // Top
        buffer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a);

        // Bottom
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);

        // North (Z-)
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);

        // South (Z+)
        buffer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a);

        // West (X-)
        buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a);

        // East (X+)
        buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a);
    }

    private static void renderAll(Minecraft client, PoseStack matrices) {
        if (lineBuffer == null) return;
        MeshData builtBuffer = lineBuffer.build();
        lineBuffer = null;
        if (builtBuffer == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);

        BufferUploader.drawWithShader(builtBuffer);

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
