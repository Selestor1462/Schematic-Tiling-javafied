package schematic.tiling.cannon;

import java.util.Objects;

public class CannonDestination {
    public final String name;
    public final double x;
    public final double z;
    public final boolean isGlobal;
    public final String worldName; // null if isGlobal, otherwise the world or server name

    public CannonDestination(String name, double x, double z, boolean isGlobal, String worldName) {
        this.name = name != null ? name.trim() : "";
        this.x = x;
        this.z = z;
        this.isGlobal = isGlobal;
        this.worldName = isGlobal ? null : (worldName != null ? worldName.trim() : "default");
    }

    public double getDistance2D(double fromX, double fromZ) {
        double dx = this.x - fromX;
        double dz = this.z - fromZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CannonDestination that = (CannonDestination) o;
        return isGlobal == that.isGlobal &&
                Double.compare(that.x, x) == 0 &&
                Double.compare(that.z, z) == 0 &&
                Objects.equals(name, that.name) &&
                Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, x, z, isGlobal, worldName);
    }
}

