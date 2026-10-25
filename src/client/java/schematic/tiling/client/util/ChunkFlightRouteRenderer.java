package schematic.tiling.client.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.joml.Matrix4fc;
import org.joml.Vector4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.CommonColors;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import fi.dy.masa.malilib.config.HudAlignment;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;

import schematic.tiling.cannon.PearlCalculator;
import schematic.tiling.cannon.PearlFlightSimulator;

/**
 * World and HUD renderer for pearl flight trajectories and unchecked chunk glowing.
 */
public class ChunkFlightRouteRenderer implements IRenderer {

    private static final ChunkFlightRouteRenderer INSTANCE = new ChunkFlightRouteRenderer();

    private static final Color4f UNCHECKED_CHUNK_COLOR = new Color4f(1.0f, 0.90f, 0.25f, 0.38f);

    private static PearlFlightSimulator.SimulationResult activeSim = null;
    private static PearlCalculator.CannonSettings activeSettings = null;
    private static String activeRouteKey = null;
    private static boolean sHudEnabled = true;

    private static final List<Long> cachedUncheckedChunks = new ArrayList<>();
    private static int lastRenderVersion = -1;
    private static String lastRenderRouteKey = null;
    private static int renderCheckTimer = 0;

    public static boolean isHudEnabled() {
        return sHudEnabled;
    }

    public static void setHudEnabled(boolean enabled) {
        sHudEnabled = enabled;
    }

    public static void toggleHud() {
        sHudEnabled = !sHudEnabled;
    }

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        RenderEventHandler.getInstance().registerWorldLastRenderer(INSTANCE);
        RenderEventHandler.getInstance().registerInGameGuiRenderer(INSTANCE);
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            onChunkLoaded(world, chunk.getPos());
        });
    }

    public static void trackRoute(PearlFlightSimulator.SimulationResult sim, PearlCalculator.CannonSettings settings) {
        activeSim = sim;
        activeSettings = settings;
        if (sim != null && sim.cannonStartPos != null && sim.solution != null) {
            activeRouteKey = PearlFlightSimulator.getRouteKey(sim.cannonStartPos, sim.solution);
        } else {
            activeRouteKey = null;
        }
        cachedUncheckedChunks.clear();
        lastRenderVersion = -1;
        lastRenderRouteKey = null;
        renderCheckTimer = 999;
    }

    public static boolean isTracked(PearlFlightSimulator.SimulationResult sim) {
        if (activeSim == null || sim == null) return false;
        if (activeRouteKey == null) return false;
        String key = PearlFlightSimulator.getRouteKey(sim.cannonStartPos, sim.solution);
        return activeRouteKey.equals(key);
    }

    public static void clearTrackedRoute() {
        activeSim = null;
        activeSettings = null;
        activeRouteKey = null;
        cachedUncheckedChunks.clear();
        lastRenderRouteKey = null;
        lastRenderVersion = -1;
    }

    public static boolean hasTrackedRoute() {
        return activeSim != null && activeRouteKey != null;
    }

    public static String getActiveRouteKey() {
        return activeRouteKey;
    }

    public static PearlCalculator.CannonSettings getActiveSettings() {
        return activeSettings;
    }

    public static void rescan(ClientLevel world) {
        if (world == null) return;
        PearlFlightSimulator.rescanLoadedChunks(world);
        if (activeRouteKey != null) {
            PearlFlightSimulator.SimulationResult updated = PearlFlightSimulator.getRouteSimulation(activeRouteKey);
            if (updated != null) {
                activeSim = updated;
                if (PearlTrajectoryLineRenderer.isLineDrawingEnabled()) {
                    PearlTrajectoryLineRenderer.displayTrajectory(updated, -1);
                }
            }
        }
    }

    public static PearlFlightSimulator.SimulationResult getActiveSimulation() {
        return activeSim;
    }

    public static PearlFlightSimulator.SimulationResult getActiveSim() {
        return activeSim;
    }

    private static void onChunkLoaded(ClientLevel world, ChunkPos pos) {
        long key = PearlFlightSimulator.chunkPosKey(pos.x(), pos.z());
        PearlFlightSimulator.onChunkLoadedFast(world, key);

        if (activeRouteKey != null) {
            PearlFlightSimulator.SimulationResult updated = PearlFlightSimulator.getRouteSimulation(activeRouteKey);
            if (updated != null && updated != activeSim) {
                activeSim = updated;
                if (PearlTrajectoryLineRenderer.isLineDrawingEnabled()) {
                    PearlTrajectoryLineRenderer.displayTrajectory(updated, -1);
                }
            }
        }
    }

    @Override
    public void onExtractGuiOverlayPost(GuiContext ctx, float partialTicks, ProfilerFiller profiler) {
        renderHud(ctx);
    }

    @Override
    public void onRenderWorldLast(RenderTarget target, CameraRenderState cameraRenderState,
                                  Frustum frustum, RenderBuffers buffers, GpuBufferSlice slice,
                                  Vector4f fog, ProfilerFiller profiler) {
        renderWorld(cameraRenderState);
    }

    public static void renderWorld(CameraRenderState cameraRenderState) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || activeSim == null || activeRouteKey == null) return;
        if (activeSim.traversedChunkKeys.isEmpty()) return;

        ClientLevel world = mc.level;
        if (world == null) return;

        Vec3 camera = cameraRenderState.pos;

        if (lastRenderVersion != PearlFlightSimulator.getVerifiedChunksVersion()
                || !Objects.equals(lastRenderRouteKey, activeRouteKey)
                || ++renderCheckTimer >= 20) {
            renderCheckTimer = 0;

            if (activeSim != null && activeRouteKey != null && activeSim.status == PearlFlightSimulator.FlightStatus.COLLISION && activeSim.collisionPos != null) {
                if (PearlFlightSimulator.isObstacleCleared(world, activeSim.collisionPos)) {
                    PearlFlightSimulator.recheckRoute(world, activeRouteKey);
                    PearlFlightSimulator.SimulationResult updated = PearlFlightSimulator.getRouteSimulation(activeRouteKey);
                    if (updated != null && updated != activeSim) {
                        activeSim = updated;
                        if (PearlTrajectoryLineRenderer.isLineDrawingEnabled()) {
                            PearlTrajectoryLineRenderer.displayTrajectory(updated, -1);
                        }
                    }
                }
            }

            lastRenderVersion = PearlFlightSimulator.getVerifiedChunksVersion();
            lastRenderRouteKey = activeRouteKey;
            cachedUncheckedChunks.clear();
            Set<Long> candidateChunks = PearlFlightSimulator.getAllCandidateChunks();
            Set<Long> allChunks = !candidateChunks.isEmpty() ? candidateChunks : activeSim.traversedChunkKeys;
            for (long key : allChunks) {
                int cx = PearlFlightSimulator.chunkKeyX(key);
                int cz = PearlFlightSimulator.chunkKeyZ(key);
                if (!PearlFlightSimulator.isChunkVerified(key) && !PearlFlightSimulator.isChunkLoaded(world, cx, cz)) {
                    cachedUncheckedChunks.add(key);
                }
            }
        }

        if (cachedUncheckedChunks.isEmpty()) return;

        try (RenderContext ctx = new RenderContext(() -> "schematic_tiling:chunk_highlight", MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL, 0)) {
            BufferBuilder blockBuffer = ctx.getBuilder();

            double renderY = (camera.y >= 127.0) ? 127.8 : (camera.y - 2.0);
            double maxDistSq = 1200.0 * 1200.0;
            boolean hasVertices = false;

            for (int i = 0; i < cachedUncheckedChunks.size(); i++) {
                long key = cachedUncheckedChunks.get(i);
                int cx = PearlFlightSimulator.chunkKeyX(key);
                int cz = PearlFlightSimulator.chunkKeyZ(key);
                int minX = cx << 4;
                int minZ = cz << 4;
                double centerX = minX + 8.0;
                double centerZ = minZ + 8.0;
                double relx = centerX - camera.x;
                double relz = centerZ - camera.z;
                if ((relx * relx + relz * relz) > maxDistSq) {
                    continue;
                }

                filledPlane(blockBuffer,
                        minX, minZ,
                        minX + 16, minZ + 16,
                        renderY, camera, UNCHECKED_CHUNK_COLOR);
                hasVertices = true;
            }

            if (hasVertices) {
                ctx.draw();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void filledPlane(BufferBuilder buffer,
                                    double x1, double z1, double x2, double z2,
                                    double y, Vec3 camera, Color4f color) {
        float rx1 = (float) (x1 - camera.x);
        float rz1 = (float) (z1 - camera.z);
        float rx2 = (float) (x2 - camera.x);
        float rz2 = (float) (z2 - camera.z);
        float ry = (float) (y - camera.y);

        int r = (int) (color.r * 255.0f);
        int g = (int) (color.g * 255.0f);
        int b = (int) (color.b * 255.0f);
        int a = (int) (color.a * 255.0f);

        // Top face
        buffer.addVertex(rx1, ry, rz2).setColor(r, g, b, a);
        buffer.addVertex(rx2, ry, rz2).setColor(r, g, b, a);
        buffer.addVertex(rx2, ry, rz1).setColor(r, g, b, a);
        buffer.addVertex(rx1, ry, rz1).setColor(r, g, b, a);

        // Bottom face (reverse winding for 2-sided visibility)
        buffer.addVertex(rx1, ry, rz1).setColor(r, g, b, a);
        buffer.addVertex(rx2, ry, rz1).setColor(r, g, b, a);
        buffer.addVertex(rx2, ry, rz2).setColor(r, g, b, a);
        buffer.addVertex(rx1, ry, rz2).setColor(r, g, b, a);
    }

    public static void renderHud(GuiContext guiContext) {
        Minecraft mc = Minecraft.getInstance();
        if (!sHudEnabled || mc.gui.hud.isHidden() || activeSim == null || activeRouteKey == null) return;
        if (mc.gui.screen() != null) return;

        int total = activeSim.traversedChunkKeys.size();
        if (total == 0) return;

        ClientLevel level = mc.level;
        int loaded = 0;
        for (long key : activeSim.traversedChunkKeys) {
            int cx = PearlFlightSimulator.chunkKeyX(key);
            int cz = PearlFlightSimulator.chunkKeyZ(key);
            if (PearlFlightSimulator.isRouteChunkVerified(activeRouteKey, key) || (level != null && PearlFlightSimulator.isChunkLoaded(level, cx, cz))) {
                loaded++;
            }
        }

        double pct = (total > 0) ? ((double) loaded * 100.0 / total) : 100.0;

        List<String> lines = new ArrayList<>();
        lines.add("\u00a76[Pearl Route Scan]");

        String selDir = (activeSim.solution != null && activeSim.solution.direction != null)
                ? activeSim.solution.direction.name : "";
        int selTnt = (activeSim.solution != null) ? activeSim.solution.getTotalTNT() : 0;
        int selTick = (activeSim.solution != null) ? activeSim.solution.tick : 0;
        lines.add(String.format(Locale.ROOT, "\u00a7eSelected (%s, %dTNT, %dt): \u00a7f%d / %d \u00a77(%.1f%%)",
                selDir, selTnt, selTick, loaded, total, pct));

        int totalAllChunks = PearlFlightSimulator.getTotalCandidateChunksCount();
        int loadedAllChunks = PearlFlightSimulator.getVerifiedCandidateChunksCount(level);
        int remainingAll = Math.max(0, totalAllChunks - loadedAllChunks);
        double allPct = (totalAllChunks > 0) ? ((double) loadedAllChunks * 100.0 / totalAllChunks) : 100.0;
        int totalCandidates = PearlFlightSimulator.getCandidateSolutions().size();

        if (totalCandidates > 1) {
            lines.add(String.format(Locale.ROOT, "\u00a7bAll %d Routes: \u00a7f%d / %d chunks \u00a77(%.1f%%)",
                    totalCandidates, loadedAllChunks, totalAllChunks, allPct));
        }

        if (remainingAll > 0) {
            lines.add(String.format(Locale.ROOT, "\u00a77Remaining to check: \u00a7c%d chunks", remainingAll));
        } else {
            lines.add(String.format(Locale.ROOT, "\u00a7a✔ All %d Encodings Verified!", totalCandidates));
        }

        int colCount = PearlFlightSimulator.getCollidingRoutesCount();
        if (activeSim.status == PearlFlightSimulator.FlightStatus.COLLISION && activeSim.collisionPos != null) {
            lines.add(String.format(Locale.ROOT, "\u00a7cSelected Hit: %s at %d %d %d (t=%d)",
                    activeSim.getCollisionBlockName(),
                    activeSim.collisionPos.getX(), activeSim.collisionPos.getY(), activeSim.collisionPos.getZ(),
                    activeSim.collisionTick));
        }
        if (colCount > 0) {
            lines.add(String.format(Locale.ROOT, "\u00a7c\u26a0 Collisions on %d encoding%s",
                    colCount, colCount == 1 ? "" : "s"));
        } else if (remainingAll == 0) {
            lines.add("\u00a7a✔ All routes clear (0 collisions)!");
        }

        try {
            RenderUtils.renderText(guiContext, 10, 10, 1.0, CommonColors.WHITE, 0x80000000, HudAlignment.BOTTOM_RIGHT, true, true, lines);
        } catch (Throwable ignored) {}
    }
}
