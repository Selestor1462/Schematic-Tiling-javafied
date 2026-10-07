package schematic.tiling.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TilingSubgroup {
    private final String name;
    private final List<TilingUnit> units = new ArrayList<>();
    private int[] knapsackCache = new int[0];

    public TilingSubgroup(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public List<TilingUnit> getUnits() {
        return this.units;
    }

    public boolean isOutline() {
        return !this.units.isEmpty() && this.units.get(0).isOutline();
    }

    public boolean dependsOnZ() {
        if (this.isOutline()) {
            throw new UnsupportedOperationException("The dependsOnZ property is not defined for outline-generators.");
        }
        return !this.units.isEmpty() && this.units.get(0).dependsOnZ();
    }

    public void verify(String parentName) {
        String sgId = parentName + TilingUnit.SEP + this.name;
        int capCount = 0;
        int zdepCount = 0;
        int outlineCount = 0;

        for (TilingUnit unit : this.units) {
            if (unit.getXDependence() == XDependence.CAP) {
                capCount++;
            }
            if (unit.dependsOnZ()) {
                zdepCount++;
            }
            if (!unit.isOutline()) {
                if (unit.getXDependence() == XDependence.OFFSET) {
                    throw new AssertionError("'OFFSET' X-dependence is not supported yet");
                }
                continue;
            }

            throw new AssertionError("Outline generators are not supported yet.");
        }

        if (outlineCount == 0) {
            if (capCount > 1) {
                throw new AssertionError("Subgroup '" + sgId + "' has more than one cap-tile.");
            }
            if (zdepCount != 0 && zdepCount != this.units.size()) {
                throw new AssertionError("Subgroup '" + sgId + "' has mixed Z-dependence, even though it's not an outline generator.");
            }

            List<Integer> widths = new ArrayList<>();
            Set<Integer> uniqueWidths = new HashSet<>();
            for (TilingUnit u : this.units) {
                if (u.getXDependence() == XDependence.TILE) {
                    widths.add(u.getWidth());
                    uniqueWidths.add(u.getWidth());
                }
            }
            if (widths.size() != uniqueWidths.size()) {
                throw new AssertionError("Subgroup '" + sgId + "' has several tiles of the same size. That's ambiguous.");
            }
        } else if (outlineCount != 2 || this.units.size() != 2) {
            throw new AssertionError("The outline-generating subgroup '" + sgId + "' must have exactly 2 elements.");
        }
    }

    public static class Box {
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        public Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }

    public Box getBox(int width) {
        if (this.isOutline()) {
            throw new AssertionError("The getBox method is poorly defined for outlines.");
        }

        List<TilingUnit> staticUnits = new ArrayList<>();
        List<TilingUnit> tiles = new ArrayList<>();
        TilingUnit cap = null;

        for (TilingUnit unit : this.units) {
            if (unit.getXDependence() == XDependence.TILE) {
                tiles.add(unit);
            } else if (unit.getXDependence() == XDependence.CAP) {
                cap = unit;
            } else if (unit.getXDependence() == XDependence.NONE) {
                staticUnits.add(unit);
            }
        }

        tiles.sort((a, b) -> Integer.compare(b.getWidth(), a.getWidth()));

        int capW = (cap != null) ? cap.getWidth() : 0;
        int tMinX;
        int tMaxX;

        if (!tiles.isEmpty()) {
            tMinX = tiles.stream().mapToInt(TilingUnit::minX).min().getAsInt();
            int[] widths = tiles.stream().mapToInt(TilingUnit::getWidth).toArray();
            int[] sol = KnapsackSolver.solve(widths, width - capW - tMinX, true);
            this.knapsackCache = sol;
            int sumTiles = 0;
            for (int i = 0; i < widths.length; i++) {
                sumTiles += sol[i] * widths[i];
            }
            tMaxX = tMinX + sumTiles + capW;
        } else {
            tMinX = Integer.MAX_VALUE;
            tMaxX = Integer.MIN_VALUE;
        }

        int sMinX;
        int sMaxX;
        if (!staticUnits.isEmpty()) {
            sMinX = staticUnits.stream().mapToInt(TilingUnit::minX).min().getAsInt();
            sMaxX = staticUnits.stream().mapToInt(TilingUnit::maxX).max().getAsInt();
        } else {
            sMinX = Integer.MAX_VALUE;
            sMaxX = Integer.MIN_VALUE;
        }

        int minX = Math.min(tMinX, sMinX);
        int maxX = Math.max(tMaxX, sMaxX);
        int minY = this.units.stream().mapToInt(TilingUnit::minY).min().getAsInt();
        int maxY = this.units.stream().mapToInt(TilingUnit::maxY).max().getAsInt();
        int minZ = this.units.stream().mapToInt(TilingUnit::minZ).min().getAsInt();
        int maxZ = this.units.stream().mapToInt(TilingUnit::maxZ).max().getAsInt();

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public void stack(NormalizedRegion target, int originX, int originY, int originZ) {
        List<TilingUnit> tiles = new ArrayList<>();
        TilingUnit cap = null;

        for (TilingUnit unit : this.units) {
            if (unit.getXDependence() == XDependence.TILE) {
                tiles.add(unit);
            } else if (unit.getXDependence() == XDependence.CAP) {
                cap = unit;
            }
        }
        tiles.sort((a, b) -> Integer.compare(b.getWidth(), a.getWidth()));

        // Paste static units
        for (TilingUnit unit : this.units) {
            if (unit.getXDependence() != XDependence.NONE) {
                continue;
            }
            int dx = unit.minX() - originX;
            int dy = unit.minY() - originY;
            int dz = unit.minZ() - originZ;
            unit.getRegion().pasteInto(target, dx, dy, dz, true);
        }

        if (tiles.isEmpty()) {
            return;
        }

        int x = tiles.stream().mapToInt(TilingUnit::minX).min().getAsInt() - originX;
        int[] sol;
        if (cap != null) {
            sol = this.knapsackCache;
        } else {
            int[] widths = tiles.stream().mapToInt(TilingUnit::getWidth).toArray();
            sol = KnapsackSolver.solve(widths, target.getWidth() - x, false);
        }

        for (int i = 0; i < sol.length && i < tiles.size(); i++) {
            int count = sol[i];
            TilingUnit unit = tiles.get(i);
            int y = unit.minY() - originY;
            int z = unit.minZ() - originZ;
            for (int step = 0; step < count; step++) {
                unit.getRegion().pasteInto(target, x, y, z, cap != null);
                x += unit.getWidth();
            }
        }

        if (cap == null) {
            return;
        }

        int y = cap.minY() - originY;
        int z = cap.minZ() - originZ;
        cap.getRegion().pasteInto(target, x, y, z, true);
    }
}
