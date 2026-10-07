package schematic.tiling.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TilingGroup {
    private final String name;
    private final Map<String, TilingSubgroup> subgroups = new LinkedHashMap<>();

    public TilingGroup(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public Map<String, TilingSubgroup> getSubgroups() {
        return this.subgroups;
    }

    public void addUnit(String subgroupName, TilingUnit unit) {
        this.subgroups.computeIfAbsent(subgroupName, TilingSubgroup::new).getUnits().add(unit);
    }

    public List<TilingUnit> walkUnits() {
        List<TilingUnit> all = new ArrayList<>();
        for (TilingSubgroup sg : this.subgroups.values()) {
            all.addAll(sg.getUnits());
        }
        return all;
    }

    public void verify() {
        for (TilingSubgroup sg : this.subgroups.values()) {
            sg.verify(this.name);
        }
    }

    public int getXOffset(int width) {
        int max = Integer.MIN_VALUE;
        boolean found = false;
        for (TilingUnit u : this.walkUnits()) {
            if (u.getXDependence() != XDependence.NONE) {
                found = true;
                if (u.maxX() > max) {
                    max = u.maxX();
                }
            }
        }
        if (!found) {
            return 0;
        }
        return width - max;
    }

    public int getZOffset(int length) {
        int max = Integer.MIN_VALUE;
        boolean found = false;
        for (TilingUnit u : this.walkUnits()) {
            if (u.dependsOnZ()) {
                found = true;
                if (u.maxZ() > max) {
                    max = u.maxZ();
                }
            }
        }
        if (!found) {
            return 0;
        }
        return length - max;
    }

    public TilingSubgroup.Box getBox(int width, int length) {
        int offset = this.getZOffset(length);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        List<TilingSubgroup> outlines = new ArrayList<>();

        for (TilingSubgroup subgr : this.subgroups.values()) {
            if (subgr.isOutline()) {
                outlines.add(subgr);
                continue;
            }
            TilingSubgroup.Box b = subgr.getBox(width);
            int z0 = b.minZ;
            int z1 = b.maxZ;
            if (subgr.dependsOnZ()) {
                z0 += offset;
                z1 += offset;
            }
            minX = Math.min(minX, b.minX);
            maxX = Math.max(maxX, b.maxX);
            minY = Math.min(minY, b.minY);
            maxY = Math.max(maxY, b.maxY);
            minZ = Math.min(minZ, z0);
            maxZ = Math.max(maxZ, z1);
        }

        for (TilingSubgroup subgr : outlines) {
            TilingUnit c0 = subgr.getUnits().get(0);
            TilingUnit c1 = subgr.getUnits().get(1);
            int x0 = c0.minX(), y0 = c0.minY(), z0 = c0.minZ();
            int x1 = c1.maxX(), y1 = c1.maxY(), z1 = c1.maxZ();
            // Outlines are not implemented in original script either
        }

        return new TilingSubgroup.Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public NormalizedRegion stack(int width, int length) {
        TilingSubgroup.Box box = this.getBox(width, length);
        int regWidth = box.maxX - box.minX + 1;
        int regHeight = box.maxY - box.minY + 1;
        int regLength = box.maxZ - box.minZ + 1;

        NormalizedRegion reg = new NormalizedRegion(box.minX, box.minY, box.minZ, regWidth, regHeight, regLength);
        int zOffset = this.getZOffset(length);

        int localOriginX = reg.getMinX();
        int localOriginY = reg.getMinY();
        int localOriginZ;

        boolean allDependOnZ = true;
        for (TilingUnit u : this.walkUnits()) {
            if (!u.dependsOnZ()) {
                allDependOnZ = false;
                break;
            }
        }

        if (allDependOnZ) {
            localOriginZ = reg.getMinZ() - zOffset;
        } else {
            localOriginZ = reg.getMinZ();
        }

        for (TilingSubgroup subgr : this.subgroups.values()) {
            subgr.stack(reg, localOriginX, localOriginY, localOriginZ);
        }

        return reg;
    }
}

