package schematic.tiling.client.util;

import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.gui.Message.MessageType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Locale;

public class CatchBlockPlacer implements IClientTickHandler {
    private static final CatchBlockPlacer INSTANCE = new CatchBlockPlacer();
    private static boolean registered = false;

    private enum State {
        IDLE,
        WAIT_FOR_CHUNK,
        WAIT_AFTER_SETBLOCK
    }

    private State state = State.IDLE;
    private BlockPos targetPos;
    private double origX, origY, origZ;
    private float origYaw, origPitch;
    private int ticksWaiting = 0;
    private int chunkLoadedTicks = 0;

    public static void startPlacement(BlockPos targetPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }

        if (!registered) {
            TickHandler.getInstance().registerClientTickHandler(INSTANCE);
            registered = true;
        }

        INSTANCE.targetPos = targetPos;
        INSTANCE.origX = mc.player.getX();
        INSTANCE.origY = mc.player.getY();
        INSTANCE.origZ = mc.player.getZ();
        INSTANCE.origYaw = mc.player.getYRot();
        INSTANCE.origPitch = mc.player.getXRot();
        INSTANCE.ticksWaiting = 0;
        INSTANCE.chunkLoadedTicks = 0;
        INSTANCE.state = State.WAIT_FOR_CHUNK;

        // 1. Initial teleport to the target location (+1 above catch block)
        mc.getConnection().sendCommand(String.format(Locale.ROOT, "tp @s %d %d %d",
                targetPos.getX(), targetPos.getY() + 1, targetPos.getZ()));

        InfoUtils.showGuiOrInGameMessage(MessageType.INFO,
                "[Return Cannon] Teleporting to [%d, %d, %d] to load chunk...",
                targetPos.getX(), targetPos.getY(), targetPos.getZ());
    }

    public static boolean isRunning() {
        return INSTANCE.state != State.IDLE;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (this.state == State.IDLE || this.targetPos == null || mc.player == null || mc.getConnection() == null) {
            this.state = State.IDLE;
            return;
        }

        this.ticksWaiting++;

        if (this.state == State.WAIT_FOR_CHUNK) {
            int cx = this.targetPos.getX() >> 4;
            int cz = this.targetPos.getZ() >> 4;

            boolean hasChunk = mc.level != null && schematic.tiling.cannon.PearlFlightSimulator.isChunkLoaded(mc.level, cx, cz);
            if (hasChunk) {
                this.chunkLoadedTicks++;
            }

            // Require chunk to be loaded for at least 4 ticks, or timeout fallback at 40 ticks (~2 sec)
            if (this.chunkLoadedTicks >= 4 || this.ticksWaiting >= 40) {
                // 2. Send setblock command
                mc.getConnection().sendCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:red_stained_glass",
                        this.targetPos.getX(), this.targetPos.getY(), this.targetPos.getZ()));

                this.state = State.WAIT_AFTER_SETBLOCK;
                this.ticksWaiting = 0;
            }
        } else if (this.state == State.WAIT_AFTER_SETBLOCK) {
            // Wait 5 ticks (250ms) so server finishes executing the setblock
            if (this.ticksWaiting >= 5) {
                // 3. Teleport back to original position and view
                mc.getConnection().sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.2f %.2f",
                        this.origX, this.origY, this.origZ, this.origYaw, this.origPitch));

                InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS,
                        "[Return Cannon] Stopper block placed at [%d, %d, %d] and returned!",
                        this.targetPos.getX(), this.targetPos.getY(), this.targetPos.getZ());

                this.state = State.IDLE;
                this.targetPos = null;
            }
        }
    }
}
