package schematic.tiling.client.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonColors;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import fi.dy.masa.malilib.config.HudAlignment;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;

import schematic.tiling.cannon.PearlCalculator;
import schematic.tiling.cannon.PearlFlightSimulator;

/**
 * World and HUD renderer for pearl flight trajectories and unchecked chunk glowing.
 */
public class ChunkFlightRouteRenderer {

    private static BufferBuilder blockBuffer;
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

        WorldRenderEvents.AFTER_TRANSLUCENT.register(ChunkFlightRouteRenderer::renderWorld);
        HudRenderCallback.EVENT.register(ChunkFlightRouteRenderer::renderHud);
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            onChunkLoaded(world, chunk.getPos());
        });
    }

    public static void trackRoute(PearlFlightSimulator.SimulationResult sim, PearlCalculator.CannonSettings settings) {
        activeSim = sim;
        activeSettings = settings;
        if (sim != null && sim.cannonStartPos != null && sim.solution != null) {
            activeRouteKey = PearlFlightSimulator.getRouteKey(sim.cannonStartPos, sim.solution);
            lastRenderVersion = -1;
            lastRenderRouteKey = null;
            cachedUncheckedChunks.clear();
        } else {
            activeRouteKey = null;
            cachedUncheckedChunks.clear();
        }
    }

    public static boolean hasTrackedRoute() {
        return activeSim != null && activeRouteKey != null;
    }

    public static boolean isTracked(PearlFlightSimulator.SimulationResult sim) {
        return activeSim != null && (sim == null || activeSim == sim);
    }

    public static void clearTrackedRoute() {
        activeSim = null;
        activeSettings = null;
        activeRouteKey = null;
        cachedUncheckedChunks.clear();
        lastRenderRouteKey = null;
        lastRenderVersion = -1;
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

    public static void restartScan(ClientLevel world) {
        if (world == null) return;
        PearlFlightSimulator.restartScan(world);
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

    public static void onChunkLoaded(ClientLevel world, ChunkPos pos) {
        if (world == null || activeSim == null || activeRouteKey == null) {
            return;
        }

        long key = PearlFlightSimulator.chunkPosKey(pos.x, pos.z);
        if (!activeSim.traversedChunkKeys.contains(key)) {
            return;
        }

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

    public static void renderWorld(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || activeSim == null || activeRouteKey == null) return;
        if (activeSim.traversedChunkKeys.isEmpty()) return;

        ClientLevel world = mc.level;
        if (world == null) return;

        Vec3 camera = context.camera().getPosition();
        PoseStack matrices = context.matrixStack();

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

            for (Long chunkKey : activeSim.traversedChunkKeys) {
                if (chunkKey != null && !PearlFlightSimulator.isChunkVerified(chunkKey)) {
                    cachedUncheckedChunks.add(chunkKey);
                }
            }
        }

        if (cachedUncheckedChunks.isEmpty()) return;

        int playerChunkX = mc.player != null ? mc.player.getBlockX() >> 4 : 0;
        int playerChunkZ = mc.player != null ? mc.player.getBlockZ() >> 4 : 0;
        final int RENDER_RADIUS = 32;

        try {
            blockBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f positionMatrix = matrices.last().pose();

            for (Long chunkKey : cachedUncheckedChunks) {
                int cx = PearlFlightSimulator.chunkKeyX(chunkKey);
                int cz = PearlFlightSimulator.chunkKeyZ(chunkKey);

                if (Math.abs(cx - playerChunkX) > RENDER_RADIUS || Math.abs(cz - playerChunkZ) > RENDER_RADIUS) {
                    continue;
                }

                double minX = cx * 16.0;
                double minZ = cz * 16.0;
                double maxX = minX + 16.0;
                double maxZ = minZ + 16.0;

                filledPlane(positionMatrix, blockBuffer, minX, minZ, maxX, maxZ, 128.05, camera, UNCHECKED_CHUNK_COLOR);
            }

            renderAll(mc, matrices);
        } catch (Throwable ignored) {
            blockBuffer = null;
        }
    }

    private static void filledPlane(Matrix4f positionMatrix, BufferBuilder buffer,
                                    double x1, double z1, double x2, double z2,
                                    double y, Vec3 camera, Color4f color) {
        float rx1 = (float) (x1 - camera.x);
        float rz1 = (float) (z1 - camera.z);
        float rx2 = (float) (x2 - camera.x);
        float rz2 = (float) (z2 - camera.z);
        float ry = (float) (y - camera.y);

        // Top face
        buffer.addVertex(positionMatrix, rx1, ry, rz2).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, rx2, ry, rz2).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, rx2, ry, rz1).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, rx1, ry, rz1).setColor(color.r, color.g, color.b, color.a);

        // Bottom face (reverse winding for 2-sided visibility)
        buffer.addVertex(positionMatrix, rx1, ry, rz1).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, rx2, ry, rz1).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, rx2, ry, rz2).setColor(color.r, color.g, color.b, color.a);
        buffer.addVertex(positionMatrix, rx1, ry, rz2).setColor(color.r, color.g, color.b, color.a);
    }

    private static void renderAll(Minecraft client, PoseStack matrices) {
        if (blockBuffer == null) return;
        MeshData builtBuffer = blockBuffer.build();
        blockBuffer = null;
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

    public static void renderHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!sHudEnabled || mc.options.hideGui) return;
        if (activeSim == null || activeRouteKey == null) return;

        int totalChunks = activeSim.traversedChunkKeys.size();
        if (totalChunks == 0) return;

        int remainingUnchecked = cachedUncheckedChunks.size();
        int verifiedChunks = Math.max(0, totalChunks - remainingUnchecked);
        double pct = (double) verifiedChunks / totalChunks * 100.0;

        int totalAllChunks = PearlFlightSimulator.getTotalCandidateChunksCount();
        int verifiedAll = PearlFlightSimulator.getVerifiedCandidateChunksCount(mc.level);
        int remainingAll = Math.max(0, totalAllChunks - verifiedAll);
        double pctAll = totalAllChunks > 0 ? ((double) verifiedAll / totalAllChunks * 100.0) : 100.0;

        List<String> lines = new ArrayList<>();
        lines.add("\u00a76\u00a7lPearl Route Verification");

        String statusColor = (remainingUnchecked == 0) ? "\u00a7a" : (remainingUnchecked < 10 ? "\u00a7e" : "\u00a7c");
        lines.add(String.format(Locale.ROOT, "%sRoute: %d/%d chunks (%.1f%%)",
                statusColor, verifiedChunks, totalChunks, pct));

        if (totalAllChunks > totalChunks) {
            String allColor = (remainingAll == 0) ? "\u00a7a" : (remainingAll < 20 ? "\u00a7e" : "\u00a7c");
            lines.add(String.format(Locale.ROOT, "%sAll Enc: %d/%d chunks (%.1f%%)",
                    allColor, verifiedAll, totalAllChunks, pctAll));
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
            RenderUtils.renderText(
                    10, 10,
                    1.0,
                    CommonColors.WHITE,
                    0x80000000,
                    HudAlignment.BOTTOM_RIGHT,
                    true,
                    true,
                    lines,
                    guiGraphics
            );
        } catch (Throwable ignored) {}
    }
}
