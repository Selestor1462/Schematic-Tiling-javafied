package schematic.tiling.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ScheduledTick;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;

public class NormalizedRegion {
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int width;
    private final int height;
    private final int length;

    private final LitematicaBlockStateContainer container;
    private final Map<BlockPos, CompoundTag> tileEntities = new HashMap<>();
    private final List<LitematicaSchematic.EntityInfo> entities = new ArrayList<>();
    private final Map<BlockPos, ScheduledTick<Block>> pendingBlockTicks = new HashMap<>();
    private final Map<BlockPos, ScheduledTick<Fluid>> pendingFluidTicks = new HashMap<>();

    public NormalizedRegion(int minX, int minY, int minZ, int width, int height, int length) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.length = Math.max(1, length);
        this.container = new LitematicaBlockStateContainer(this.width, this.height, this.length);
    }

    public int getMinX() {
        return this.minX;
    }

    public int getMinY() {
        return this.minY;
    }

    public int getMinZ() {
        return this.minZ;
    }

    public int getMaxX() {
        return this.minX + this.width - 1;
    }

    public int getMaxY() {
        return this.minY + this.height - 1;
    }

    public int getMaxZ() {
        return this.minZ + this.length - 1;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getLength() {
        return this.length;
    }

    public LitematicaBlockStateContainer getContainer() {
        return this.container;
    }

    public Map<BlockPos, CompoundTag> getTileEntities() {
        return this.tileEntities;
    }

    public List<LitematicaSchematic.EntityInfo> getEntities() {
        return this.entities;
    }

    public Map<BlockPos, ScheduledTick<Block>> getPendingBlockTicks() {
        return this.pendingBlockTicks;
    }

    public Map<BlockPos, ScheduledTick<Fluid>> getPendingFluidTicks() {
        return this.pendingFluidTicks;
    }

    /**
     * Pastes blocks, tile entities, and entities from this region into `target`,
     * offsetting all positions by (dx, dy, dz).
     */
    public void pasteInto(NormalizedRegion target, int dx, int dy, int dz, boolean strict) {
        // Paste blocks
        for (int y = 0; y < this.height; y++) {
            int ty = y + dy;
            if (ty < 0 || ty >= target.height) {
                continue;
            }
            for (int z = 0; z < this.length; z++) {
                int tz = z + dz;
                if (tz < 0 || tz >= target.length) {
                    continue;
                }
                for (int x = 0; x < this.width; x++) {
                    int tx = x + dx;
                    if (tx < 0 || tx >= target.width) {
                        continue;
                    }
                    BlockState state = this.container.get(x, y, z);
                    target.container.set(tx, ty, tz, state);
                }
            }
        }

        // Paste tile entities
        for (Map.Entry<BlockPos, CompoundTag> entry : this.tileEntities.entrySet()) {
            BlockPos pos = entry.getKey();
            int tx = pos.getX() + dx;
            int ty = pos.getY() + dy;
            int tz = pos.getZ() + dz;

            if (!strict && (tx < 0 || tx >= target.width || ty < 0 || ty >= target.height || tz < 0 || tz >= target.length)) {
                continue;
            }

            BlockPos newPos = new BlockPos(tx, ty, tz);
            CompoundTag newTag = entry.getValue().copy();
            newTag.putInt("x", tx);
            newTag.putInt("y", ty);
            newTag.putInt("z", tz);
            target.tileEntities.put(newPos, newTag);
        }

        // Paste entities
        for (LitematicaSchematic.EntityInfo info : this.entities) {
            Vec3 pos = info.posVec;
            double tx = pos.x + dx;
            double ty = pos.y + dy;
            double tz = pos.z + dz;

            if (!strict && (tx < 0 || tx >= target.width || ty < 0 || ty >= target.height || tz < 0 || tz >= target.length)) {
                continue;
            }

            Vec3 newPos = new Vec3(tx, ty, tz);
            CompoundTag newNbt = info.nbt.copy();

            ListTag posTag = new ListTag();
            posTag.add(DoubleTag.valueOf(newPos.x));
            posTag.add(DoubleTag.valueOf(newPos.y));
            posTag.add(DoubleTag.valueOf(newPos.z));
            newNbt.put("Pos", posTag);

            if (newNbt.contains("TileX")) {
                int oldTileX = newNbt.getIntOr("TileX", 0);
                int oldTileY = newNbt.getIntOr("TileY", 0);
                int oldTileZ = newNbt.getIntOr("TileZ", 0);
                newNbt.putInt("TileX", oldTileX + dx);
                newNbt.putInt("TileY", oldTileY + dy);
                newNbt.putInt("TileZ", oldTileZ + dz);
            }

            target.entities.add(new LitematicaSchematic.EntityInfo(newPos, newNbt));
        }

        // Paste pending block ticks
        for (Map.Entry<BlockPos, ScheduledTick<Block>> entry : this.pendingBlockTicks.entrySet()) {
            BlockPos p = entry.getKey();
            int tx = p.getX() + dx;
            int ty = p.getY() + dy;
            int tz = p.getZ() + dz;
            if (tx >= 0 && tx < target.width && ty >= 0 && ty < target.height && tz >= 0 && tz < target.length) {
                BlockPos newPos = new BlockPos(tx, ty, tz);
                ScheduledTick<Block> tick = entry.getValue();
                target.pendingBlockTicks.put(newPos, new ScheduledTick<>(tick.type(), newPos, tick.triggerTick(), tick.priority(), tick.subTickOrder()));
            }
        }

        // Paste pending fluid ticks
        for (Map.Entry<BlockPos, ScheduledTick<Fluid>> entry : this.pendingFluidTicks.entrySet()) {
            BlockPos p = entry.getKey();
            int tx = p.getX() + dx;
            int ty = p.getY() + dy;
            int tz = p.getZ() + dz;
            if (tx >= 0 && tx < target.width && ty >= 0 && ty < target.height && tz >= 0 && tz < target.length) {
                BlockPos newPos = new BlockPos(tx, ty, tz);
                ScheduledTick<Fluid> tick = entry.getValue();
                target.pendingFluidTicks.put(newPos, new ScheduledTick<>(tick.type(), newPos, tick.triggerTick(), tick.priority(), tick.subTickOrder()));
            }
        }
    }
}

