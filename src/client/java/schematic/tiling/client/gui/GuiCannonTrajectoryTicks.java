package schematic.tiling.client.gui;

import java.util.List;
import java.util.Locale;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.GuiGraphics;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

import schematic.tiling.cannon.PearlFlightSimulator.SimulationPoint;
import schematic.tiling.cannon.PearlFlightSimulator.SimulationResult;
import schematic.tiling.client.util.ChunkFlightRouteRenderer;
import schematic.tiling.client.util.PearlTrajectoryLineRenderer;

/**
 * Detailed viewer for pearl coordinates, velocity, and chunk locations at every tick of the simulated trajectory.
 */
public class GuiCannonTrajectoryTicks extends GuiBase {

    private static final int DIALOG_WIDTH = 500;
    private static final int DIALOG_HEIGHT = 330;
    private static final int TICKS_PER_PAGE = 6;

    private final GuiBase parentGui;
    private final SimulationResult sim;
    private int page = 0;

    private String statusMessage = null;
    private boolean statusIsError = false;

    public GuiCannonTrajectoryTicks(GuiBase parent, SimulationResult sim) {
        this.parentGui = parent;
        this.sim = sim;
        this.setParent(parent);
        int totalTicks = (sim != null && sim.trajectory != null) ? sim.trajectory.size() : 0;
        this.title = StringUtils.translate("schematic_tiling.gui.cannon.ticks.title", totalTicks);
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (verticalAmount != 0 && this.sim != null && this.sim.trajectory != null) {
            int total = this.sim.trajectory.size();
            int maxPage = Math.max(0, (total - 1) / TICKS_PER_PAGE);
            int prevPage = this.page;
            if (verticalAmount > 0 && this.page > 0) {
                this.page--;
            } else if (verticalAmount < 0 && this.page < maxPage) {
                this.page++;
            }
            if (this.page != prevPage) {
                this.initGui();
                return true;
            }
        }
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearElements();

        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        if (this.sim == null || this.sim.trajectory == null || this.sim.trajectory.isEmpty()) {
            ButtonGeneric closeBtn = new ButtonGeneric(startX + dialogWidth - 75, startY + dialogHeight - 25, 60, 20,
                    StringUtils.translate("schematic_tiling.gui.cannon.button.close"));
            this.addButton(closeBtn, (btn, mouse) -> GuiBase.openGui(this.parentGui));
            return;
        }

        List<SimulationPoint> list = this.sim.trajectory;
        int total = list.size();
        int maxPage = Math.max(0, (total - 1) / TICKS_PER_PAGE);
        this.page = Math.max(0, Math.min(this.page, maxPage));

        int pageStart = this.page * TICKS_PER_PAGE;
        int pageEnd = Math.min(total, pageStart + TICKS_PER_PAGE);

        int listY = startY + 44;
        for (int i = pageStart; i < pageEnd; i++) {
            final int tickIdx = i;
            SimulationPoint pt = list.get(i);
            int rowY = listY + ((i - pageStart) * 27);

            // Copy button per tick
            ButtonGeneric copyBtn = new ButtonGeneric(startX + 400, rowY + 3, 42, 18,
                    StringUtils.translate("schematic_tiling.gui.cannon.button.copy_landing"));
            copyBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.cannon.hover.copy_tick_coords", tickIdx));
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

            // TP button per tick
            ButtonGeneric tpBtn = new ButtonGeneric(startX + 446, rowY + 3, 38, 18,
                    StringUtils.translate("schematic_tiling.gui.cannon.sim.button.tp"));
            tpBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.cannon.hover.tp_tick", tickIdx));
            this.addButton(tpBtn, (btn, mouse) -> {
                if (this.mc != null && this.mc.getConnection() != null) {
                    this.mc.getConnection().sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", pt.pos.x, pt.pos.y, pt.pos.z));
                    this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.ticks.status.teleported_tick", tickIdx);
                    this.statusIsError = false;
                }
            });
        }

        // Pagination buttons placed side by side for easy clicking and zero text collisions
        int navY = startY + 215;
        ButtonGeneric prevBtn = new ButtonGeneric(startX + 15, navY, 70, 18,
                StringUtils.translate("schematic_tiling.gui.cannon.button.prev"));
        prevBtn.setEnabled(this.page > 0);
        this.addButton(prevBtn, (btn, mouse) -> {
            if (this.page > 0) {
                this.page--;
                this.initGui();
            }
        });

        ButtonGeneric nextBtn = new ButtonGeneric(startX + 90, navY, 70, 18,
                StringUtils.translate("schematic_tiling.gui.cannon.button.next"));
        nextBtn.setEnabled(this.page < maxPage);
        this.addButton(nextBtn, (btn, mouse) -> {
            if (this.page < maxPage) {
                this.page++;
                this.initGui();
            }
        });

        ButtonGeneric copyAllBtn = new ButtonGeneric(startX + dialogWidth - 95, navY, 80, 18,
                StringUtils.translate("schematic_tiling.gui.cannon.ticks.button.copy_all"));
        copyAllBtn.setHoverStrings(StringUtils.translate("schematic_tiling.gui.cannon.hover.copy_all_ticks"));
        this.addButton(copyAllBtn, (btn, mouse) -> this.copyAllTicks());

        // Bottom Action Row
        int btnY = startY + 295;

        boolean isLineShowing = PearlTrajectoryLineRenderer.isDisplaying(this.sim);
        String lineLabel = isLineShowing
                ? StringUtils.translate("schematic_tiling.gui.cannon.sim.button.hide_line")
                : StringUtils.translate("schematic_tiling.gui.cannon.sim.button.draw_line");
        ButtonGeneric drawLineBtn = new ButtonGeneric(startX + 15, btnY, 100, 20, lineLabel);
        this.addButton(drawLineBtn, (btn, mouse) -> {
            PearlTrajectoryLineRenderer.toggleTrajectory(this.sim);
            if (PearlTrajectoryLineRenderer.isDisplaying(this.sim)) {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.line_shown");
            } else {
                this.statusMessage = StringUtils.translate("schematic_tiling.gui.cannon.sim.status.line_hidden");
            }
            this.statusIsError = false;
            this.initGui();
        });

        boolean isHudShowing = ChunkFlightRouteRenderer.isHudEnabled();
        String hudLabel = isHudShowing
                ? StringUtils.translate("schematic_tiling.gui.cannon.sim.button.hide_hud")
                : StringUtils.translate("schematic_tiling.gui.cannon.sim.button.show_hud");
        ButtonGeneric hudBtn = new ButtonGeneric(startX + 125, btnY, 80, 20, hudLabel);
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

        ButtonGeneric closeBtn = new ButtonGeneric(startX + dialogWidth - 75, btnY, 60, 20,
                StringUtils.translate("schematic_tiling.gui.cannon.button.close"));
        this.addButton(closeBtn, (btn, mouse) -> GuiBase.openGui(this.parentGui));
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

    @Override
    protected void drawContents(GuiGraphics ctx, int mouseX, int mouseY, float partialTicks) {
        RenderUtils.drawRect(ctx, 0, 0, this.getScreenWidth(), this.getScreenHeight(), 0x80000000);

        int dialogWidth = DIALOG_WIDTH;
        int dialogHeight = DIALOG_HEIGHT;
        int startX = (this.getScreenWidth() - dialogWidth) / 2;
        int startY = (this.getScreenHeight() - dialogHeight) / 2;

        RenderUtils.drawOutlinedBox(ctx, startX, startY, dialogWidth, dialogHeight, 0xFF1C1C1C, COLOR_HORIZONTAL_BAR);

        // Title
        this.drawString(ctx, this.title, startX + 15, startY + 12, COLOR_WHITE);

        if (this.sim == null || this.sim.trajectory == null || this.sim.trajectory.isEmpty()) {
            this.drawString(ctx, "No trajectory data available.", startX + 15, startY + 40, 0xFFFF5555);
            return;
        }

        List<SimulationPoint> list = this.sim.trajectory;
        int total = list.size();
        int maxPage = Math.max(0, (total - 1) / TICKS_PER_PAGE);
        int pageStart = this.page * TICKS_PER_PAGE;
        int pageEnd = Math.min(total, pageStart + TICKS_PER_PAGE);

        // Header Subtitle: Start -> Catch info
        if (this.sim.solution != null) {
            String sub = String.format(Locale.ROOT, "Start: (%s) -> Dest: (%s) | Max Alt: Y=%.1f",
                    this.sim.cannonStartPos != null ? this.sim.cannonStartPos.toShortString() : "?",
                    this.sim.solution.catchBlockPos != null ? this.sim.solution.catchBlockPos.toShortString() : "?",
                    this.sim.maxAltitude);
            this.drawString(ctx, sub, startX + 15, startY + 26, 0xFFAAAAAA);
        }

        int listY = startY + 44;
        for (int i = pageStart; i < pageEnd; i++) {
            SimulationPoint pt = list.get(i);
            int rowY = listY + ((i - pageStart) * 27);

            boolean isCollisionTick = (this.sim.status == schematic.tiling.cannon.PearlFlightSimulator.FlightStatus.COLLISION && i == this.sim.collisionTick);
            boolean isStartTick = (i == 0);
            boolean isLandingTick = (i == total - 1 && this.sim.status == schematic.tiling.cannon.PearlFlightSimulator.FlightStatus.CLEAR);

            int boxBg = isCollisionTick ? 0xDD441818 : (isLandingTick ? 0xDD184418 : (isStartTick ? 0xDD183044 : 0xD0202020));
            int boxBorder = isCollisionTick ? 0xFFFF4444 : (isLandingTick ? 0xFF44FF44 : (isStartTick ? 0xFF00AAFF : 0xFF404040));
            RenderUtils.drawOutlinedBox(ctx, startX + 15, rowY, 470, 24, boxBg, boxBorder);

            // Tick badge
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

            // Position & Velocity strings
            String posStr = String.format(Locale.ROOT, "X: \u00a7f%.2f \u00a77Y: \u00a7f%.2f \u00a77Z: \u00a7f%.2f",
                    pt.pos.x, pt.pos.y, pt.pos.z);
            this.drawString(ctx, posStr, startX + 115, rowY + 7, 0xFFAAAAAA);

            double speed = (pt.motion != null) ? pt.motion.length() : 0.0;
            int cx = ((int) Math.floor(pt.pos.x)) >> 4;
            int cz = ((int) Math.floor(pt.pos.z)) >> 4;
            String metaStr = String.format(Locale.ROOT, "spd: %.1f m/t (c:[%d,%d])", speed, cx, cz);
            this.drawString(ctx, metaStr, startX + 285, rowY + 7, 0xFF888888);
        }

        // Page info centered between buttons
        String pageStr = StringUtils.translate("schematic_tiling.gui.cannon.ticks.page",
                this.page + 1, maxPage + 1, pageStart + 1, pageEnd, total);
        this.drawString(ctx, pageStr, startX + 172, startY + 220, 0xFFAAAAAA);

        // Status message
        if (this.statusMessage != null) {
            int msgCol = this.statusIsError ? 0xFFFF5555 : 0xFF55FF55;
            this.drawString(ctx, this.statusMessage, startX + 15, startY + 250, msgCol);
        }
    }
}
