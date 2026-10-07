package schematic.tiling.core;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.malilib.util.data.tag.CompoundData;

public class TilingTree {
    private String name = "Unnamed";
    private final LitematicaSchematic schem;
    private final Map<String, TilingGroup> groups = new LinkedHashMap<>();

    public TilingTree(LitematicaSchematic schem, String fileName) {
        this.schem = schem;
        this.parseName(fileName);
        this.initTree();
    }

    private void parseName(String fileName) {
        String fn = fileName;
        int lastSlash = Math.max(fn.lastIndexOf('/'), fn.lastIndexOf('\\'));
        if (lastSlash != -1) {
            fn = fn.substring(lastSlash + 1);
        }

        String lower = fn.toLowerCase();
        if (lower.endsWith("parts.litematic")) {
            this.name = fn.substring(0, fn.length() - "parts.litematic".length()).trim();
        } else if (lower.endsWith(".litematic")) {
            this.name = fn.substring(0, fn.length() - ".litematic".length()).trim();
        } else {
            this.name = fn.trim();
        }
    }

    private void initTree() {
        Map<String, BlockPos> subRegionPositions = this.schem.getAreaPositions();
        Map<String, BlockPos> subRegionSizes = this.schem.getAreaSizes();

        for (String regionName : subRegionPositions.keySet()) {
            BlockPos pos = subRegionPositions.get(regionName);
            BlockPos size = subRegionSizes.get(regionName);
            LitematicaBlockStateContainer container = this.schem.getSubRegionContainer(regionName);

            if (pos == null || size == null || container == null) {
                continue;
            }

            int sx = size.getX();
            int sy = size.getY();
            int sz = size.getZ();

            int minX = (sx >= 0) ? pos.getX() : pos.getX() + sx + 1;
            int minY = (sy >= 0) ? pos.getY() : pos.getY() + sy + 1;
            int minZ = (sz >= 0) ? pos.getZ() : pos.getZ() + sz + 1;

            int width = Math.abs(sx);
            int height = Math.abs(sy);
            int length = Math.abs(sz);

            NormalizedRegion normReg = new NormalizedRegion(minX, minY, minZ, width, height, length);

            // Copy blocks from container
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        normReg.getContainer().set(x, y, z, container.get(x, y, z));
                    }
                }
            }

            // Copy tile entities
            Map<BlockPos, CompoundData> teMap = this.schem.getBlockEntityMapForRegion(regionName);
            if (teMap != null) {
                for (Map.Entry<BlockPos, CompoundData> teEntry : teMap.entrySet()) {
                    normReg.getTileEntities().put(teEntry.getKey(), teEntry.getValue().copy());
                }
            }

            // Copy entities
            java.util.List<LitematicaSchematic.EntityInfo> entList = this.schem.getEntityListForRegion(regionName);
            if (entList != null) {
                for (LitematicaSchematic.EntityInfo info : entList) {
                    normReg.getEntities().add(new LitematicaSchematic.EntityInfo(info.posVec(), info.nbt().copy()));
                }
            }

            // Copy pending ticks
            if (this.schem.getScheduledBlockTicksForRegion(regionName) != null) {
                normReg.getPendingBlockTicks().putAll(this.schem.getScheduledBlockTicksForRegion(regionName));
            }
            if (this.schem.getScheduledFluidTicksForRegion(regionName) != null) {
                normReg.getPendingFluidTicks().putAll(this.schem.getScheduledFluidTicksForRegion(regionName));
            }

            TilingUnit unit = new TilingUnit(regionName, normReg);
            TilingGroup group = this.groups.computeIfAbsent(unit.getGroup(), TilingGroup::new);

            if (unit.getSubgroup() == null) {
                group.addUnit(TilingUnit.SEP + unit.getCustomId(), unit);
            } else {
                group.addUnit(unit.getSubgroup(), unit);
            }
        }

        for (TilingGroup group : this.groups.values()) {
            group.verify();
        }
    }

    public String getName() {
        return this.name;
    }

    public LitematicaSchematic getSourceSchematic() {
        return this.schem;
    }

    public Map<String, TilingGroup> getGroups() {
        return this.groups;
    }

    public boolean needsLength() {
        for (TilingGroup group : this.groups.values()) {
            for (TilingUnit unit : group.walkUnits()) {
                if (unit.dependsOnZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    public Map<String, NormalizedRegion> generateRegions(int width, int length) {
        Map<String, NormalizedRegion> result = new LinkedHashMap<>();
        for (Map.Entry<String, TilingGroup> entry : this.groups.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stack(width, length));
        }
        return result;
    }
}
