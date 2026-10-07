package schematic.tiling.core;

public enum XDependence {
    NONE("0"),
    OFFSET("1"),
    TILE("t"),
    CAP("c");

    private final String code;

    XDependence(String code) {
        this.code = code;
    }

    public String getCode() {
        return this.code;
    }

    public static XDependence fromCode(String code) {
        if (code == null) {
            return NONE;
        }
        String trimmed = code.trim().toLowerCase();
        for (XDependence dep : values()) {
            if (dep.code.equalsIgnoreCase(trimmed)) {
                return dep;
            }
        }
        return NONE;
    }
}

