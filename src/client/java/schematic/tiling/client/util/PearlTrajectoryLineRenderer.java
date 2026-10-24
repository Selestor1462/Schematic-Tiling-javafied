package schematic.tiling.client.util;

import java.util.List;
import java.util.Objects;

import org.joml.Matrix4fc;
import org.joml.Vector4f;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;

import schematic.tiling.cannon.PearlCalculator.Space3D;
import schematic.tiling.cannon.PearlFlightSimulator;
import schematic.tiling.cannon.PearlFlightSimulator.SimulationPoint;
import schematic.tiling.cannon.PearlFlightSimulator.SimulationResult;

/**
 * Highly visible, glowing, non-culled 3D in-game line and box renderer for pearl trajectories.
 */
public class PearlTrajectoryLineRenderer implements IClientTickHandler, IRenderer {

    private static final PearlTrajectoryLineRenderer INSTANCE = new PearlTrajectoryLineRenderer();

    private static SimulationResult currentSim = null;
    private static boolean sEnabled = false;
    private static int remainingTicks = 0;
    private static boolean initialized = false;
    private static boolean registeredTick = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        RenderEventHandler.getInstance().registerWorldLastRenderer(INSTANCE);
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

    public static void toggleTrajectory(SimulationResult sim) {
        if (isDisplaying(sim)) {
            clearTrajectory();
        } else {
            displayTrajectory(sim, -1);
        }
    }

    private static double cachedGroundY = Double.NaN;
    private static int cachedLandingBlockY = -1;
    private static SimulationResult lastGroundYSim = null;
    private static int groundYCheckTimer = 0;
    private static final BlockPos.MutableBlockPos mutLandingPos = new BlockPos.MutableBlockPos();

    public static void clearTrajectory() {
        currentSim = null;
        sEnabled = false;
        remainingTicks = 0;
        cachedGroundY = Double.NaN;
        cachedLandingBlockY = -1;
        lastGroundYSim = null;
        groundYCheckTimer = 0;
    }

    public static boolean isDisplaying() {
        return sEnabled && currentSim != null && (remainingTicks > 0 || remainingTicks == -1);
    }

    public static boolean isDisplaying(SimulationResult sim) {
        return isDisplaying() && currentSim == sim;
    }

    public static SimulationResult getCurrentSim() {
        return currentSim;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks == 0) {
                currentSim = null;
                sEnabled = false;
            }
        }
    }

    @Override
    public void onRenderWorldLast(RenderTarget target, Matrix4fc modelView, CameraRenderState cameraRenderState,
                                  Frustum frustum, RenderBuffers buffers, GpuBufferSlice slice,
                                  Vector4f fog, ProfilerFiller profiler) {
        renderWorld(cameraRenderState);
    }

    public static void renderWorld(CameraRenderState cameraRenderState) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || !sEnabled || currentSim == null) return;
        if (currentSim.trajectory == null || currentSim.trajectory.size() < 2) return;

        ClientLevel world = mc.level;
        if (world == null) return;

        Vec3 camera = cameraRenderState.pos;

        try (RenderContext ctx = new RenderContext(() -> "schematic_tiling:trajectory", MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL, 0)) {
            BufferBuilder lineBuffer = ctx.getBuilder();
            boolean hasVertices = false;

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

                add3DBeamSegment(lineBuffer, p1.pos, p2.pos, camera,
                        c1[0], c1[1], c1[2], c1[3],
                        c2[0], c2[1], c2[2], c2[3],
                        beamRadius);
                hasVertices = true;
            }

            // 2. Render prominent tick node crystals along the route
            for (int i = 0; i <= maxIdx; i++) {
                SimulationPoint pt = pts.get(i);
                float t = (float) i / Math.max(1, maxIdx);
                float[] c = getColorForTick(i, maxIdx, isCollision, colTick, t);

                float nodeHalf = 0.14f;
                float nr = c[0];
                float ng = c[1];
                float nb = c[2];
                float na = c[3];

                Vec3 nodePos = pt.pos;

                if (i == 0) {
                    nodeHalf = 0.22f; // Big blue start marker
                    nr = 0.2f; ng = 0.8f; nb = 1.0f; na = 1.0f;
                } else if (isCollision && i == colTick) {
                    nodeHalf = 0.28f; // Big red collision marker at impact tick
                    nr = 1.0f; ng = 0.1f; nb = 0.1f; na = 1.0f;
                } else if (i == maxIdx) {
                    nodeHalf = 0.24f; // Big landing marker
                    if (!isCollision) {
                        nr = 0.2f; ng = 1.0f; nb = 0.3f; na = 1.0f;
                    } else {
                        nr = 0.85f; ng = 0.3f; nb = 0.6f; na = 0.85f;
                    }
                } else {
                    if (!isCollision || i < colTick) {
                        nr = 1.0f; ng = 0.9f; nb = 0.2f; na = 0.95f;
                    } else {
                        nodeHalf = 0.10f;
                        nr = 0.85f; ng = 0.25f; nb = 0.50f; na = 0.60f;
                    }
                }

                addBox(lineBuffer,
                        (float) (nodePos.x - camera.x),
                        (float) (nodePos.y - camera.y),
                        (float) (nodePos.z - camera.z),
                        nodeHalf, nr, ng, nb, na);
                hasVertices = true;
            }

            // 3. Highlight collision obstacle block with bright 3D wireframe edges & tinted box
            if (isCollision && currentSim.collisionPos != null) {
                BlockPos cp = currentSim.collisionPos;
                addHighlightBoxWithEdges(lineBuffer, cp, camera, 1.0f, 0.12f, 0.12f, 0.40f, 1.0f, 0.2f, 0.2f, 1.0f);
                hasVertices = true;

                if (currentSim.collisionExact != null) {
                    Vec3 hit = currentSim.collisionExact;
                    addBox(lineBuffer,
                            (float) (hit.x - camera.x),
                            (float) (hit.y - camera.y),
                            (float) (hit.z - camera.z),
                            0.20f, 1.0f, 0.05f, 0.05f, 1.0f);
                }
            }

            // 4. Highlight catcher block with bright emerald wireframe edges & tinted box
            if (currentSim.solution != null && currentSim.solution.catchBlockPos != null) {
                BlockPos catchPos = currentSim.solution.catchBlockPos;
                addHighlightBoxWithEdges(lineBuffer, catchPos, camera, 0.15f, 1.0f, 0.3f, 0.35f, 0.3f, 1.0f, 0.4f, 1.0f);
                hasVertices = true;
            }

            // 5. Render vertical drop line from pearl trajectory to actual player landing position on ground
            if (currentSim.solution != null && currentSim.solution.worldArrivalPos != null) {
                Space3D arr = currentSim.solution.worldArrivalPos;
                BlockPos startPos = currentSim.cannonStartPos;
                int defaultLandingY = (startPos != null && startPos.getY() < 150) ? startPos.getY() : 128;

                if (lastGroundYSim != currentSim || Double.isNaN(cachedGroundY) || cachedLandingBlockY < 0 || ++groundYCheckTimer >= 20) {
                    groundYCheckTimer = 0;
                    lastGroundYSim = currentSim;
                    int foundBlockY = -1;
                    double foundGroundY = defaultLandingY + 1.0;

                    if (mc.level != null) {
                        int bx = (int) Math.floor(arr.x);
                        int bz = (int) Math.floor(arr.z);
                        int startCheckY = Math.min((int) Math.floor(arr.y), mc.level.getMaxY() - 1);
                        int minCheckY = Math.max(mc.level.getMinY(), defaultLandingY - 5);
                        for (int y = startCheckY; y >= minCheckY; y--) {
                            mutLandingPos.set(bx, y, bz);
                            BlockState st = mc.level.getBlockState(mutLandingPos);
                            if (!st.isAir() && !st.is(Blocks.CAVE_AIR) && !st.is(Blocks.VOID_AIR)) {
                                if (st.getBlock() instanceof CarpetBlock) {
                                    foundBlockY = y - 1;
                                    foundGroundY = y + 0.0625;
                                } else {
                                    foundBlockY = y;
                                    foundGroundY = y + 1.0;
                                }
                                break;
                            }
                        }
                    }

                    cachedLandingBlockY = (foundBlockY >= 0) ? foundBlockY : defaultLandingY;
                    cachedGroundY = (foundBlockY >= 0) ? foundGroundY : (defaultLandingY + 1.0);
                }

                double groundY = cachedGroundY;
                int landingBlockY = cachedLandingBlockY;

                Vec3 trajPoint = new Vec3(arr.x, arr.y, arr.z);
                Vec3 groundPoint = new Vec3(arr.x, groundY, arr.z);

                float vr = 0.20f;
                float vg = 1.00f;
                float vb = 0.50f;
                float va = 0.90f;

                if (isCollision) {
                    vr = 1.00f;
                    vg = 0.65f;
                    vb = 0.20f;
                    va = 0.70f;
                }

                float dropRadius = 0.05f;
                add3DBeamSegment(lineBuffer, trajPoint, groundPoint, camera,
                        vr, vg, vb, va,
                        vr, vg, vb, va,
                        dropRadius);

                float padHalf = 0.18f;
                addBox(lineBuffer,
                        (float) (groundPoint.x - camera.x),
                        (float) (groundPoint.y - camera.y),
                        (float) (groundPoint.z - camera.z),
                        padHalf, vr, vg, vb, 1.0f);

                BlockPos landingBlockPos = new BlockPos((int) Math.floor(arr.x), landingBlockY, (int) Math.floor(arr.z));
                addHighlightBoxWithEdges(lineBuffer, landingBlockPos, camera,
                        vr, vg, vb, 0.20f,
                        vr, vg, vb, 0.85f);
                hasVertices = true;
            }

            if (hasVertices) {
                ctx.draw();
            }
        } catch (Throwable ignored) {
        }
    }

    private static float[] getColorForTick(int tick, int maxIdx, boolean isCollision, int colTick, float progress) {
        if (!isCollision || colTick < 0) {
            float r = 0.0f + 0.20f * progress;
            float g = 0.90f + 0.10f * progress;
            float b = 1.0f - 0.65f * progress;
            return new float[]{r, g, b, 0.90f};
        } else {
            if (tick < colTick - 2) {
                return new float[]{0.15f, 0.85f, 1.0f, 0.85f};
            } else if (tick < colTick) {
                return new float[]{1.0f, 0.60f, 0.05f, 0.95f};
            } else if (tick == colTick) {
                return new float[]{1.0f, 0.12f, 0.12f, 1.00f};
            } else {
                return new float[]{0.85f, 0.25f, 0.50f, 0.50f};
            }
        }
    }

    private static int toByte(float f) {
        return Math.clamp((int) (f * 255.0f + 0.5f), 0, 255);
    }

    private static void add3DBeamSegment(
            BufferBuilder buffer,
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

        int ir1 = toByte(r1); int ig1 = toByte(g1); int ib1 = toByte(b1); int ia1 = toByte(a1);
        int ir2 = toByte(r2); int ig2 = toByte(g2); int ib2 = toByte(b2); int ia2 = toByte(a2);

        // Plane 1
        Vec3 v1 = p1.subtract(perp1);
        Vec3 v2 = p1.add(perp1);
        Vec3 v3 = p2.add(perp1);
        Vec3 v4 = p2.subtract(perp1);
        addDoubleSidedQuad(buffer, v1, v2, v3, v4, ir1, ig1, ib1, ia1, ir2, ig2, ib2, ia2);

        // Plane 2
        Vec3 w1 = p1.subtract(perp2);
        Vec3 w2 = p1.add(perp2);
        Vec3 w3 = p2.add(perp2);
        Vec3 w4 = p2.subtract(perp2);
        addDoubleSidedQuad(buffer, w1, w2, w3, w4, ir1, ig1, ib1, ia1, ir2, ig2, ib2, ia2);
    }

    private static void addDoubleSidedQuad(
            BufferBuilder buffer,
            Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4,
            int r1, int g1, int b1, int a1,
            int r2, int g2, int b2, int a2
    ) {
        // Forward face
        buffer.addVertex((float) v1.x, (float) v1.y, (float) v1.z).setColor(r1, g1, b1, a1);
        buffer.addVertex((float) v2.x, (float) v2.y, (float) v2.z).setColor(r1, g1, b1, a1);
        buffer.addVertex((float) v3.x, (float) v3.y, (float) v3.z).setColor(r2, g2, b2, a2);
        buffer.addVertex((float) v4.x, (float) v4.y, (float) v4.z).setColor(r2, g2, b2, a2);

        // Reverse face
        buffer.addVertex((float) v1.x, (float) v1.y, (float) v1.z).setColor(r1, g1, b1, a1);
        buffer.addVertex((float) v4.x, (float) v4.y, (float) v4.z).setColor(r2, g2, b2, a2);
        buffer.addVertex((float) v3.x, (float) v3.y, (float) v3.z).setColor(r2, g2, b2, a2);
        buffer.addVertex((float) v2.x, (float) v2.y, (float) v2.z).setColor(r1, g1, b1, a1);
    }

    private static void addBox(
            BufferBuilder buffer,
            float cx, float cy, float cz, float halfSize,
            float r, float g, float b, float a
    ) {
        float x1 = cx - halfSize;
        float x2 = cx + halfSize;
        float y1 = cy - halfSize;
        float y2 = cy + halfSize;
        float z1 = cz - halfSize;
        float z2 = cz + halfSize;

        addBoxFaces(buffer, x1, y1, z1, x2, y2, z2, toByte(r), toByte(g), toByte(b), toByte(a));
    }

    private static void addHighlightBoxWithEdges(
            BufferBuilder buffer,
            BlockPos pos, Vec3 camera,
            float fr, float fg, float fb, float fa,
            float er, float eg, float eb, float ea
    ) {
        float x1 = (float) (pos.getX() - camera.x);
        float x2 = x1 + 1.0f;
        float y1 = (float) (pos.getY() - camera.y);
        float y2 = y1 + 1.0f;
        float z1 = (float) (pos.getZ() - camera.z);
        float z2 = z1 + 1.0f;

        // 1. Semi-transparent faces
        addBoxFaces(buffer, x1, y1, z1, x2, y2, z2, toByte(fr), toByte(fg), toByte(fb), toByte(fa));

        // 2. Thick 3D wireframe edges around the 12 edges
        float edgeRadius = 0.035f;
        int ier = toByte(er); int ieg = toByte(eg); int ieb = toByte(eb); int iea = toByte(ea);

        // 4 Bottom edges (Y1)
        addEdgeBeam(buffer, x1, y1, z1, x2, y1, z1, edgeRadius, ier, ieg, ieb, iea);
        addEdgeBeam(buffer, x2, y1, z1, x2, y1, z2, edgeRadius, ier, ieg, ieb, iea);
        addEdgeBeam(buffer, x2, y1, z2, x1, y1, z2, edgeRadius, ier, ieg, ieb, iea);
        addEdgeBeam(buffer, x1, y1, z2, x1, y1, z1, edgeRadius, ier, ieg, ieb, iea);

        // 4 Top edges (Y2)
        addEdgeBeam(buffer, x1, y2, z1, x2, y2, z1, edgeRadius, ier, ieg, ieb, iea);
        addEdgeBeam(buffer, x2, y2, z1, x2, y2, z2, edgeRadius, ier, ieg, ieb, iea);
        addEdgeBeam(buffer, x2, y2, z2, x1, y2, z2, edgeRadius, ier, ieg, ieb, iea);
        addEdgeBeam(buffer, x1, y2, z2, x1, y2, z1, edgeRadius, ier, ieg, ieb, iea);

        // 4 Vertical pillar edges
        addEdgeBeam(buffer, x1, y1, z1, x1, y2, z1, edgeRadius, ier, ieg, ieb, iea);
        addEdgeBeam(buffer, x2, y1, z1, x2, y2, z1, edgeRadius, ier, ieg, ieb, iea);
        addEdgeBeam(buffer, x2, y1, z2, x2, y2, z2, edgeRadius, ier, ieg, ieb, iea);
        addEdgeBeam(buffer, x1, y1, z2, x1, y2, z2, edgeRadius, ier, ieg, ieb, iea);
    }

    private static void addEdgeBeam(
            BufferBuilder buffer,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float radius, int r, int g, int b, int a
    ) {
        float minX = Math.min(x1, x2) - radius;
        float maxX = Math.max(x1, x2) + radius;
        float minY = Math.min(y1, y2) - radius;
        float maxY = Math.max(y1, y2) + radius;
        float minZ = Math.min(z1, z2) - radius;
        float maxZ = Math.max(z1, z2) + radius;

        addBoxFaces(buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a);
    }

    private static void addBoxFaces(
            BufferBuilder buffer,
            float x1, float y1, float z1, float x2, float y2, float z2,
            int r, int g, int b, int a
    ) {
        // Top
        buffer.addVertex(x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x1, y2, z2).setColor(r, g, b, a);

        // Bottom
        buffer.addVertex(x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(x1, y1, z1).setColor(r, g, b, a);

        // North
        buffer.addVertex(x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x1, y1, z1).setColor(r, g, b, a);

        // South
        buffer.addVertex(x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(x1, y1, z2).setColor(r, g, b, a);

        // West
        buffer.addVertex(x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(x1, y1, z1).setColor(r, g, b, a);

        // East
        buffer.addVertex(x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y1, z1).setColor(r, g, b, a);
    }
}
