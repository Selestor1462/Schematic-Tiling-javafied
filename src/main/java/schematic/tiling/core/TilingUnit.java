package schematic.tiling.core;

public class TilingUnit {
    public static final String SEP = "//";

    private final NormalizedRegion region;
    private String group = "Unnamed";
    private String subgroup = null;
    private String customId = null;
    private XDependence xDependence = XDependence.NONE;
    private boolean dependsOnZ = false;
    private boolean isOutline = false;

    public TilingUnit(String name, NormalizedRegion region) {
        this.region = region;
        this.parseRegionName(name);
    }

    private void parseRegionName(String name) {
        // [group] // [name or subgroup::name] // [depends_on_W] // [depends_on_L] // [is_outline]
        String[] parts = name.split(SEP);
        if (parts.length > 0 && parts[0] != null) {
            this.group = parts[0].trim();
        }

        if (parts.length > 1 && parts[1] != null) {
            String arg = parts[1].trim();
            if (arg.contains("::")) {
                String[] subParts = arg.split("::", 2);
                this.subgroup = subParts[0].trim();
                this.customId = subParts[1].trim();
            } else {
                this.customId = arg;
            }
        }

        if (parts.length > 2 && parts[2] != null) {
            this.xDependence = XDependence.fromCode(parts[2]);
        }

        if (parts.length > 3 && parts[3] != null) {
            try {
                this.dependsOnZ = Integer.parseInt(parts[3].trim()) != 0;
            } catch (NumberFormatException ignored) {}
        }

        if (parts.length > 4 && parts[4] != null) {
            try {
                this.isOutline = Integer.parseInt(parts[4].trim()) != 0;
            } catch (NumberFormatException ignored) {}
        }
    }

    public NormalizedRegion getRegion() {
        return this.region;
    }

    public String getGroup() {
        return this.group;
    }

    public String getSubgroup() {
        return this.subgroup;
    }

    public String getCustomId() {
        return this.customId;
    }

    public XDependence getXDependence() {
        return this.xDependence;
    }

    public boolean dependsOnZ() {
        return this.dependsOnZ;
    }

    public boolean isOutline() {
        return this.isOutline;
    }

    public int minX() {
        return this.region.getMinX();
    }

    public int maxX() {
        return this.region.getMaxX();
    }

    public int minY() {
        return this.region.getMinY();
    }

    public int maxY() {
        return this.region.getMaxY();
    }

    public int minZ() {
        return this.region.getMinZ();
    }

    public int maxZ() {
        return this.region.getMaxZ();
    }

    public int getWidth() {
        return this.region.getWidth();
    }

    public int getHeight() {
        return this.region.getHeight();
    }

    public int getLength() {
        return this.region.getLength();
    }
}

