package schematic.tiling.client.gui;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.GuiGraphics;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.cannon.PearlCalculator;
import schematic.tiling.cannon.PearlFlightSimulator;
import schematic.tiling.cannon.PearlFlightSimulator.SimulationPoint;
import schematic.tiling.cannon.PearlFlightSimulator.SimulationResult;
import schematic.tiling.client.util.ChunkFlightRouteRenderer;
import schematic.tiling.client.util.PearlTrajectoryLineRenderer;

public class GuiCannonSimulation extends GuiBase {

    private static final int DIALOG_WIDTH = 500;
    private static final int DIALOG_HEIGHT = 330;
    private static final int TICKS_PER_PAGE = 6;

    public enum ViewMode {
        OVERVIEW,
        TICKS
    }

    private final GuiReturnCannon parentGui;
    private SimulationResult sim;
    private ViewMode viewMode = ViewMode.OVERVIEW;
    private int tickPage = 0;

    private String statusMessage = null;
    private boolean statusIsError = false;

    public GuiCannonSimulation(GuiReturnCannon parent, SimulationResult sim) {
        this.parentGui = parent;
        this.sim = sim;
        this.setParent(parent);
        this.title = StringUtils.translate("schematic_tiling.gui.cannon.sim.title");
        if (this.sim != null) {
            ChunkFlightRouteRenderer.trackRoute(this.sim, parent != null ? parent.getSettings() : null);
        }
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (this.viewMode == ViewMode.TICKS && verticalAmount != 0 && this.sim != null && this.sim.trajectory != null) {
            int total = this.sim.trajectory.size();
            int maxPage = Math.max(0, (total - 1) / TICKS_PER_PAGE);
            int prevPage = this.tickPage;
            if (verticalAmount > 0 && this.tickPage > 0) {
                this.tickPage--;
            } else if (verticalAmount < 0 && this.tickPage < maxPage) {
                this.tickPage++;
            }
            if (this.tickPage != prevPage) {
                this.initGui();
                return true;
            }
        }
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearButtons();

        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        if (this.sim == null) {
            ButtonGeneric closeBtn = new ButtonGeneric(startX + dialogWidth - 75, startY + dialogHeight - 25, 60, 20,
                    StringUtils.translate("schematic_tiling.gui.cannon.button.close"));
            this.addButton(closeBtn, (btn, mouse) -> GuiBase.openGui(this.parentGui));
            return;
        }

        // --- Tabs: Overview vs Pearl Ticks ---
        int tabY = startY + 54;
        ButtonGeneric btnOverview = new ButtonGeneric(startX + 15, tabY, 80, 18,
                StringUtils.translate("schematic_tiling.gui.cannon.sim.tab.overview"));
        btnOverview.setEnabled(this.viewMode != ViewMode.OVERVIEW);
        this.addButton(btnOverview, (btn, mouse) -> {
            this.viewMode = ViewMode.OVERVIEW;
            this.initGui();
        });

        int totalTicks = (this.sim.trajectory != null) ? this.sim.trajectory.size() : 0;
        String ticksTabTitle = StringUtils.translate("schematic_tiling.gui.cannon.sim.tab.ticks", totalTicks);
        ButtonGeneric btnTicks = new ButtonGeneric(startX + 100, tabY, 115, 18, ticksTabTitle);
        btnTicks.setEnabled(this.viewMode != ViewMode.TICKS);
        this.addButton(btnTicks, (btn, mouse) -> {
            this.viewMode = ViewMode.TICKS;
            this.initGui();
        });

        if (this.viewMode == ViewMode.OVERVIEW) {
            // If collision: add Copy Coords and Teleport buttons in Column 2
            if (this.sim.status == PearlFlightSimulator.FlightStatus.COLLISION && this.sim.collisionPos != null) {
                int col2X = startX + 250;
                int actionY = startY + 236;
                ButtonGeneric copyBtn = new ButtonGeneric(col2X, actionY, 114, 18, StringUtils.translate("schematic_tiling.gui.cannon.sim.button.copy_coords"));
                this.addButton(copyBtn, (btn, mouse) -> {
                    if (this.mc != null && this.sim.collisionPos != null) {
                        BlockPos p = this.sim.collisionPos;
                        this.mc.keyboardHandler.setClipboard(String.format(Locale.ROOT, "%d %d %d", p.getX(), p.getY(), p.getZ()));
                        this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.copied_coords", p.toShortString());
                        this.statusIsError = false;
                    }
                });

                ButtonGeneric tpBtn = new ButtonGeneric(col2X + 120, actionY, 115, 18, StringUtils.translate("schematic_tiling.gui.cannon.sim.button.tp"));
                this.addButton(tpBtn, (btn, mouse) -> {
                    if (this.mc != null && this.mc.getConnection() != null && this.sim.collisionPos != null) {
                        BlockPos p = this.sim.collisionPos;
                        this.mc.getConnection().sendCommand(String.format(Locale.ROOT, "tp @s %d %d %d", p.getX(), p.getY() + 1, p.getZ()));
                        this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.teleported", p.toShortString());
                        this.statusIsError = false;
                    }
                });
            }
        } else {
            // TICKS VIEW: Add per-tick copy/tp buttons and pagination
            List<SimulationPoint> list = this.sim.trajectory;
            int total = (list != null) ? list.size() : 0;
            int maxPage = Math.max(0, (total - 1) / TICKS_PER_PAGE);
            this.tickPage = Math.max(0, Math.min(this.tickPage, maxPage));

            int pageStart = this.tickPage * TICKS_PER_PAGE;
            int pageEnd = Math.min(total, pageStart + TICKS_PER_PAGE);

            int listY = startY + 76;
            for (int i = pageStart; i < pageEnd; i++) {
                final int tickIdx = i;
                SimulationPoint pt = list.get(i);
                int rowY = listY + ((i - pageStart) * 27);

                // Copy button
                ButtonGeneric copyBtn = new ButtonGeneric(startX + 400, rowY + 3, 42, 18,
                        StringUtils.translate("schematic_tiling.gui.cannon.button.copy_landing"));
                this.addButton(copyBtn, (btn, mouse) -> {
                    if (this.mc != null) {
                        String coords = String.format(Locale.ROOT, "%.2f %.2f %.2f", pt.pos.x, pt.pos.y, pt.pos.z);
                        this.mc.keyboardHandler.setClipboard(coords);
                        this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.ticks.status.copied_tick",
                                tickIdx, String.format(Locale.ROOT, "%.2f", pt.pos.x),
                                String.format(Locale.ROOT, "%.2f", pt.pos.y),
                                String.format(Locale.ROOT, "%.2f", pt.pos.z));
                        this.statusIsError = false;
                    }
                });

                // TP button
                ButtonGeneric tpBtn = new ButtonGeneric(startX + 446, rowY + 3, 38, 18,
                        StringUtils.translate("schematic_tiling.gui.cannon.sim.button.tp"));
                this.addButton(tpBtn, (btn, mouse) -> {
                    if (this.mc != null && this.mc.getConnection() != null) {
                        this.mc.getConnection().sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", pt.pos.x, pt.pos.y, pt.pos.z));
                        this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.ticks.status.teleported_tick", tickIdx);
                        this.statusIsError = false;
                    }
                });
            }

            // Pagination
            int navY = startY + 242;
            ButtonGeneric prevBtn = new ButtonGeneric(startX + 15, navY, 70, 18,
                    StringUtils.translate("schematic_tiling.gui.cannon.button.prev"));
            prevBtn.setEnabled(this.tickPage > 0);
            this.addButton(prevBtn, (btn, mouse) -> {
                if (this.tickPage > 0) {
                    this.tickPage--;
                    this.initGui();
                }
            });

            ButtonGeneric nextBtn = new ButtonGeneric(startX + 90, navY, 70, 18,
                    StringUtils.translate("schematic_tiling.gui.cannon.button.next"));
            nextBtn.setEnabled(this.tickPage < maxPage);
            this.addButton(nextBtn, (btn, mouse) -> {
                if (this.tickPage < maxPage) {
                    this.tickPage++;
                    this.initGui();
                }
            });

            ButtonGeneric copyAllBtn = new ButtonGeneric(startX + dialogWidth - 95, navY, 80, 18,
                    StringUtils.translate("schematic_tiling.gui.cannon.ticks.button.copy_all"));
            this.addButton(copyAllBtn, (btn, mouse) -> this.copyAllTicks());
        }

        // --- Bottom Action Buttons ---
        int bottomY = startY + 296;

        boolean isLineShowing = PearlTrajectoryLineRenderer.isDisplaying(this.sim);
        String lineLabel = isLineShowing
                ? StringUtils.translate("schematic_tiling.gui.cannon.sim.button.hide_line")
                : StringUtils.translate("schematic_tiling.gui.cannon.sim.button.draw_line");
        ButtonGeneric drawLineBtn = new ButtonGeneric(startX + 15, bottomY, 90, 20, lineLabel);
        this.addButton(drawLineBtn, (btn, mouse) -> {
            PearlTrajectoryLineRenderer.toggleTrajectory(this.sim);
            ChunkFlightRouteRenderer.trackRoute(this.sim, this.parentGui != null ? this.parentGui.getSettings() : null);
            if (PearlTrajectoryLineRenderer.isDisplaying(this.sim)) {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.line_shown");
            } else {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.line_hidden");
            }
            this.statusIsError = false;
            this.initGui();
        });

        boolean isTracking = ChunkFlightRouteRenderer.isTracked(this.sim);
        String trackLabel = isTracking
                ? StringUtils.translate("schematic_tiling.gui.cannon.sim.button.stop_track")
                : StringUtils.translate("schematic_tiling.gui.cannon.sim.button.start_track");
        ButtonGeneric trackBtn = new ButtonGeneric(startX + 110, bottomY, 85, 20, trackLabel);
        this.addButton(trackBtn, (btn, mouse) -> {
            if (ChunkFlightRouteRenderer.isTracked(this.sim)) {
                ChunkFlightRouteRenderer.clearTrackedRoute();
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.tracking_stopped");
            } else {
                ChunkFlightRouteRenderer.trackRoute(this.sim, this.parentGui != null ? this.parentGui.getSettings() : null);
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.tracking_started");
            }
            this.statusIsError = false;
            this.initGui();
        });

        ButtonGeneric resetBtn = new ButtonGeneric(startX + 200, bottomY, 80, 20, StringUtils.translate("schematic_tiling.gui.cannon.sim.button.rescan"));
        resetBtn.setHoverStrings(
                StringUtils.translate("schematic_tiling.gui.cannon.sim.hover.rescan"),
                StringUtils.translate("schematic_tiling.gui.cannon.sim.hover.rescan_desc")
        );
        this.addButton(resetBtn, (btn, mouse) -> {
            if (this.mc != null && this.mc.level != null) {
                if (GuiBase.isShiftDown()) {
                    PearlFlightSimulator.clearRouteCache();
                    ChunkFlightRouteRenderer.clearTrackedRoute();
                    PearlTrajectoryLineRenderer.clearTrajectory();
                    if (this.parentGui != null) {
                        this.parentGui.clearSimulationCache();
                    }
                    this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.cache_cleared");
                } else {
                    PearlFlightSimulator.rescanLoadedChunks(this.mc.level);
                    if (this.parentGui != null) {
                        this.parentGui.clearSimulationCacheOnly();
                    }
                    String rKey = PearlFlightSimulator.getRouteKey(this.sim.cannonStartPos, this.sim.solution);
                    SimulationResult updated = PearlFlightSimulator.getRouteSimulation(rKey);
                    if (updated == null && this.sim.cannonStartPos != null && this.sim.solution != null) {
                        updated = PearlFlightSimulator.simulate(this.sim.cannonStartPos, this.sim.solution, this.parentGui != null ? this.parentGui.getSettings() : PearlFlightSimulator.getActiveSettings(), this.mc.level);
                    }
                    if (updated != null) {
                        this.sim = updated;
                        if (PearlTrajectoryLineRenderer.isDisplaying()) {
                            PearlTrajectoryLineRenderer.displayTrajectory(updated, -1);
                        }
                        if (ChunkFlightRouteRenderer.isTracked(updated)) {
                            ChunkFlightRouteRenderer.trackRoute(updated, this.parentGui != null ? this.parentGui.getSettings() : null);
                        }
                    }
                    this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.rescanned");
                }
            }
            this.statusIsError = false;
            this.initGui();
        });

        boolean isHudShowing = ChunkFlightRouteRenderer.isHudEnabled();
        String hudLabel = isHudShowing
                ? StringUtils.translate("schematic_tiling.gui.cannon.sim.button.hide_hud")
                : StringUtils.translate("schematic_tiling.gui.cannon.sim.button.show_hud");
        ButtonGeneric hudBtn = new ButtonGeneric(startX + 285, bottomY, 80, 20, hudLabel);
        this.addButton(hudBtn, (btn, mouse) -> {
            ChunkFlightRouteRenderer.toggleHud();
            GuiReturnCannon.setLastShowRouteHud(ChunkFlightRouteRenderer.isHudEnabled());
            if (ChunkFlightRouteRenderer.isHudEnabled()) {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.hud_shown");
            } else {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.hud_hidden");
            }
            this.statusIsError = false;
            this.initGui();
        });

        ButtonGeneric backBtn = new ButtonGeneric(startX + dialogWidth - 75, bottomY, 60, 20, StringUtils.translate("schematic_tiling.gui.cannon.button.close"));
        this.addButton(backBtn, (btn, mouse) -> {
            GuiBase.openGui(this.parentGui);
        });
    }

    private void copyAllTicks() {
        if (this.sim == null || this.sim.trajectory == null || this.mc == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("# Pearl Flight Trajectory (Total: ").append(this.sim.trajectory.size()).append(" ticks)\n");
        if (this.sim.solution != null) {
            sb.append("# Direction: ").append(this.sim.solution.direction.name)
              .append(", Total TNT: ").append(this.sim.solution.getTotalTNT())
              .append(", Distance: ").append(String.format(Locale.ROOT, "%.2f", this.sim.solution.distance)).append("m\n");
        }
        sb.append("Tick,X,Y,Z,Vx,Vy,Vz,Speed_m_per_tick,ChunkX,ChunkZ\n");
        for (int i = 0; i < this.sim.trajectory.size(); i++) {
            SimulationPoint pt = this.sim.trajectory.get(i);
            double spd = (pt.motion != null) ? pt.motion.length() : 0.0;
            int cx = ((int) Math.floor(pt.pos.x)) >> 4;
            int cz = ((int) Math.floor(pt.pos.z)) >> 4;
            sb.append(String.format(Locale.ROOT, "%d,%.3f,%.3f,%.3f,%.4f,%.4f,%.4f,%.3f,%d,%d\n",
                    i, pt.pos.x, pt.pos.y, pt.pos.z,
                    (pt.motion != null ? pt.motion.x : 0.0),
                    (pt.motion != null ? pt.motion.y : 0.0),
                    (pt.motion != null ? pt.motion.z : 0.0),
                    spd, cx, cz));
        }
        this.mc.keyboardHandler.setClipboard(sb.toString());
        this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.ticks.status.copied_all", this.sim.trajectory.size());
        this.statusIsError = false;
    }

    private void drawCentered(GuiGraphics ctx, String text, int centerX, int y, int color) {
        int w = this.getStringWidth(text);
        this.drawString(ctx, text, centerX - (w / 2), y, color);
    }

    @Override
    protected void drawContents(GuiGraphics ctx, int mouseX, int mouseY, float partialTicks) {
        RenderUtils.drawRect(ctx, 0, 0, this.getScreenWidth(), this.getScreenHeight(), 0x80000000);

        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;
        int centerX = this.getScreenWidth() / 2;

        RenderUtils.drawOutlinedBox(ctx, startX, startY, dialogWidth, dialogHeight, 0xFF1C1C1C, COLOR_HORIZONTAL_BAR);

        // Title
        this.drawCentered(ctx, this.title, centerX, startY + 10, COLOR_WHITE);

        if (this.sim == null) {
            return;
        }

        // Status Banner
        int bannerY = startY + 26;
        int bannerW = dialogWidth - 30;
        int bannerH = 22;

        if (this.sim.status == PearlFlightSimulator.FlightStatus.COLLISION) {
            RenderUtils.drawOutlinedBox(ctx, startX + 15, bannerY, bannerW, bannerH, 0xDD441818, 0xFFFF4444);
            String bannerText = "\u00a7c\u00a7l" + StringUtils.translate("schematic_tiling.gui.cannon.sim.status.collision")
                    + ": " + this.sim.getCollisionBlockName() + " (" + this.sim.collisionPos.toShortString() + ")";
            this.drawCentered(ctx, bannerText, centerX, bannerY + 6, 0xFFFF6666);
        } else if (this.sim.status == PearlFlightSimulator.FlightStatus.CLEAR) {
            RenderUtils.drawOutlinedBox(ctx, startX + 15, bannerY, bannerW, bannerH, 0xDD184418, 0xFF44FF44);
            String bannerText = "\u00a7a\u00a7l" + StringUtils.translate("schematic_tiling.gui.cannon.sim.status.clear");
            this.drawCentered(ctx, bannerText, centerX, bannerY + 6, 0xFF66FF66);
        } else if (this.sim.chunksUnloaded > 0) {
            RenderUtils.drawOutlinedBox(ctx, startX + 15, bannerY, bannerW, bannerH, 0xDD443818, 0xFFFFAA00);
            String bannerText = "\u00a7e\u00a7l" + StringUtils.translate("schematic_tiling.gui.cannon.sim.status.unverified", this.sim.chunksUnloaded);
            this.drawCentered(ctx, bannerText, centerX, bannerY + 6, 0xFFFFDD55);
        } else {
            RenderUtils.drawOutlinedBox(ctx, startX + 15, bannerY, bannerW, bannerH, 0xDD333333, 0xFF888888);
            String bannerText = "\u00a77\u00a7l" + StringUtils.translate("schematic_tiling.gui.cannon.sim.status.unchecked");
            this.drawCentered(ctx, bannerText, centerX, bannerY + 6, 0xFFAAAAAA);
        }

        if (this.viewMode == ViewMode.OVERVIEW) {
            // Two Column Panels
            int col1X = startX + 15;
            int col1W = 225;
            int col2X = startX + 250;
            int col2W = 235;
            int panelY = startY + 76;
            int panelH = 186;

            RenderUtils.drawOutlinedBox(ctx, col1X, panelY, col1W, panelH, 0xD0202020, 0xFF505050);
            RenderUtils.drawOutlinedBox(ctx, col2X, panelY, col2W, panelH, 0xD0202020, 0xFF505050);

            // Column 1: Flight Information
            int y1 = panelY + 6;
            this.drawString(ctx, "\u00a7e\u00a7l" + StringUtils.translate("schematic_tiling.gui.cannon.sim.label.flight_info"), col1X + 8, y1, 0xFFFFFF55);
            y1 += 14;

            PearlCalculator.CalculationResult sol = this.sim.solution;
            String dirName = StringUtils.translate("schematic_tiling.direction." + sol.direction.name().toLowerCase(Locale.ROOT));
            this.drawString(ctx, "Direction: \u00a7f" + dirName, col1X + 8, y1, 0xFFAAAAAA);
            y1 += 12;

            this.drawString(ctx, "Start Pos: \u00a7f" + this.sim.cannonStartPos.toShortString(), col1X + 8, y1, 0xFFAAAAAA);
            y1 += 12;

            this.drawString(ctx, "Catcher Pos: \u00a7f" + sol.catchBlockPos.toShortString(), col1X + 8, y1, 0xFFAAAAAA);
            y1 += 12;

            this.drawString(ctx, String.format(Locale.ROOT, "Distance: \u00a7f%.1f m", sol.distance), col1X + 8, y1, 0xFFAAAAAA);
            y1 += 12;

            double flightSecs = sol.tick * 0.05;
            this.drawString(ctx, String.format(Locale.ROOT, "Duration: \u00a7f%d ticks (%.2fs)", sol.tick, flightSecs), col1X + 8, y1, 0xFFAAAAAA);
            y1 += 12;

            this.drawString(ctx, String.format(Locale.ROOT, "Max Altitude: \u00a7fY = %.1f", this.sim.maxAltitude), col1X + 8, y1, 0xFFAAAAAA);
            y1 += 12;

            this.drawString(ctx, String.format(Locale.ROOT, "TNT: \u00a7fTotal %d (R:%d B:%d)", sol.getTotalTNT(), sol.redTNT, sol.blueTNT), col1X + 8, y1, 0xFFAAAAAA);
            y1 += 14;

            this.drawString(ctx, "\u00a7eChunk Visibility:", col1X + 8, y1, 0xFFFFFF55);
            y1 += 12;
            this.drawString(ctx, String.format(Locale.ROOT, "Traversed Chunks: \u00a7f%d", this.sim.chunksTraversed), col1X + 8, y1, 0xFFAAAAAA);
            y1 += 12;

            int loadCol = (this.sim.chunksUnloaded == 0) ? 0xFF55FF55 : 0xFFFFAA00;
            this.drawString(ctx, String.format(Locale.ROOT, "Loaded in Client: \u00a7f%d / %d", this.sim.chunksLoaded, this.sim.chunksTraversed), col1X + 8, y1, loadCol);
            y1 += 12;
            if (this.sim.chunksUnloaded > 0) {
                this.drawString(ctx, String.format(Locale.ROOT, "\u00a7e[!] %d chunks outside render dist", this.sim.chunksUnloaded), col1X + 8, y1, 0xFFFFAA00);
            }

            // Column 2: Obstacle or Destination Details
            int y2 = panelY + 6;
            if (this.sim.status == PearlFlightSimulator.FlightStatus.COLLISION) {
                this.drawString(ctx, "\u00a7c\u00a7l" + StringUtils.translate("schematic_tiling.gui.cannon.sim.label.collision_info"), col2X + 8, y2, 0xFFFF5555);
                y2 += 14;

                this.drawString(ctx, "Block: \u00a7c" + this.sim.getCollisionBlockName(), col2X + 8, y2, 0xFFAAAAAA);
                y2 += 12;

                this.drawString(ctx, "Position: \u00a7f" + this.sim.collisionPos.toShortString(), col2X + 8, y2, 0xFFAAAAAA);
                y2 += 12;

                if (this.sim.collisionExact != null) {
                    String exactStr = String.format(Locale.ROOT, "%.2f, %.2f, %.2f",
                            this.sim.collisionExact.x, this.sim.collisionExact.y, this.sim.collisionExact.z);
                    this.drawString(ctx, "Impact: \u00a77(" + exactStr + ")", col2X + 8, y2, 0xFF888888);
                    y2 += 12;
                }

                this.drawString(ctx, String.format(Locale.ROOT, "Collision Tick: \u00a7f%d / %d", this.sim.collisionTick, sol.tick), col2X + 8, y2, 0xFFAAAAAA);
                y2 += 12;

                this.drawString(ctx, String.format(Locale.ROOT, "From Cannon: \u00a7f%.1f m", this.sim.collisionDistFromStart), col2X + 8, y2, 0xFFAAAAAA);
                y2 += 12;

                this.drawString(ctx, String.format(Locale.ROOT, "Before Catcher: \u00a7f%.1f m", this.sim.collisionDistToCatch), col2X + 8, y2, 0xFFAAAAAA);
                y2 += 14;

                this.drawString(ctx, "\u00a7c[!] Early impact intercepts pearl", col2X + 8, y2, 0xFFFF7777);
                y2 += 11;
                this.drawString(ctx, "\u00a7cand prevents return landing.", col2X + 8, y2, 0xFFFF7777);
            } else if (this.sim.chunksUnloaded > 0) {
                this.drawString(ctx, "\u00a7e\u00a7l" + StringUtils.translate("schematic_tiling.gui.cannon.sim.label.destination_info"), col2X + 8, y2, 0xFFFFDD55);
                y2 += 14;

                this.drawString(ctx, "Path Status: \u00a7eUnverified Route", col2X + 8, y2, 0xFFAAAAAA);
                y2 += 12;

                this.drawString(ctx, "Loaded Chunks: \u00a7f" + this.sim.chunksLoaded + " / " + this.sim.chunksTraversed, col2X + 8, y2, 0xFFAAAAAA);
                y2 += 12;

                this.drawString(ctx, "Unloaded Chunks: \u00a7c" + this.sim.chunksUnloaded, col2X + 8, y2, 0xFFAAAAAA);
                y2 += 14;

                this.drawString(ctx, "\u00a76[!] All " + this.sim.chunksTraversed + " route chunks must", col2X + 8, y2, 0xFFFFDD55);
                y2 += 11;
                this.drawString(ctx, "\u00a76be loaded to verify safety!", col2X + 8, y2, 0xFFFFDD55);
            } else {
                this.drawString(ctx, "\u00a7a\u00a7l" + StringUtils.translate("schematic_tiling.gui.cannon.sim.label.destination_info"), col2X + 8, y2, 0xFF55FF55);
                y2 += 14;

                this.drawString(ctx, "Path Status: \u00a7aClear (No Obstacles)", col2X + 8, y2, 0xFFAAAAAA);
                y2 += 12;

                this.drawString(ctx, "Catcher Block: \u00a7f" + sol.catchBlockPos.toShortString(), col2X + 8, y2, 0xFFAAAAAA);
                y2 += 12;

                String arrivalStr = String.format(Locale.ROOT, "%.2f, %.2f, %.2f",
                        sol.worldArrivalPos.x, sol.worldArrivalPos.y, sol.worldArrivalPos.z);
                this.drawString(ctx, "Arrival Pos: \u00a7f" + arrivalStr, col2X + 8, y2, 0xFFAAAAAA);
                y2 += 12;

                this.drawString(ctx, String.format(Locale.ROOT, "Distance Flown: \u00a7f%.2f m", sol.distance), col2X + 8, y2, 0xFFAAAAAA);
                y2 += 14;

                this.drawString(ctx, "\u00a7a[✓] The pearl trajectory reaches", col2X + 8, y2, 0xFF77FF77);
                y2 += 11;
                this.drawString(ctx, "\u00a7athe catcher safely with no obstacles.", col2X + 8, y2, 0xFF77FF77);
            }
        } else {
            // TICKS VIEW: Render list of tick coordinates
            List<SimulationPoint> list = this.sim.trajectory;
            int total = (list != null) ? list.size() : 0;
            int maxPage = Math.max(0, (total - 1) / TICKS_PER_PAGE);
            int pageStart = this.tickPage * TICKS_PER_PAGE;
            int pageEnd = Math.min(total, pageStart + TICKS_PER_PAGE);

            int listY = startY + 76;
            for (int i = pageStart; i < pageEnd; i++) {
                SimulationPoint pt = list.get(i);
                int rowY = listY + ((i - pageStart) * 27);

                boolean isCollisionTick = (this.sim.status == PearlFlightSimulator.FlightStatus.COLLISION && i == this.sim.collisionTick);
                boolean isStartTick = (i == 0);
                boolean isLandingTick = (i == total - 1 && this.sim.status == PearlFlightSimulator.FlightStatus.CLEAR);

                int boxBg = isCollisionTick ? 0xDD441818 : (isLandingTick ? 0xDD184418 : (isStartTick ? 0xDD183044 : 0xD0202020));
                int boxBorder = isCollisionTick ? 0xFFFF4444 : (isLandingTick ? 0xFF44FF44 : (isStartTick ? 0xFF00AAFF : 0xFF404040));
                RenderUtils.drawOutlinedBox(ctx, startX + 15, rowY, 470, 24, boxBg, boxBorder);

                String tickBadge = String.format(Locale.ROOT, "t=%-2d", i);
                int badgeColor = COLOR_WHITE;
                if (isStartTick) {
                    tickBadge += " \u00a7b[START]";
                    badgeColor = 0xFF55FFFF;
                } else if (isCollisionTick) {
                    tickBadge += " \u00a7c[COLLISION]";
                    badgeColor = 0xFFFF5555;
                } else if (isLandingTick) {
                    tickBadge += " \u00a7a[LANDING]";
                    badgeColor = 0xFF55FF55;
                }
                this.drawString(ctx, tickBadge, startX + 22, rowY + 7, badgeColor);

                String posStr = String.format(Locale.ROOT, "X: \u00a7f%.2f \u00a77Y: \u00a7f%.2f \u00a77Z: \u00a7f%.2f",
                        pt.pos.x, pt.pos.y, pt.pos.z);
                this.drawString(ctx, posStr, startX + 115, rowY + 7, 0xFFAAAAAA);

                double speed = (pt.motion != null) ? pt.motion.length() : 0.0;
                int cx = ((int) Math.floor(pt.pos.x)) >> 4;
                int cz = ((int) Math.floor(pt.pos.z)) >> 4;
                String metaStr = String.format(Locale.ROOT, "spd: %.1f m/t (c:[%d,%d])", speed, cx, cz);
                this.drawString(ctx, metaStr, startX + 285, rowY + 7, 0xFF888888);
            }

            // Pagination info
            String pageStr = StringUtils.translate("schematic_tiling.gui.cannon.ticks.page",
                    this.tickPage + 1, maxPage + 1, pageStart + 1, pageEnd, total);
            this.drawString(ctx, pageStr, startX + 172, startY + 246, 0xFFAAAAAA);
        }

        // Status message
        if (this.statusMessage != null) {
            int msgCol = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            this.drawString(ctx, this.statusMessage, startX + 15, startY + 276, msgCol);
        }
    }
}
