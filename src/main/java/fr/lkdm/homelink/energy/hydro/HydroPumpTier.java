package fr.lkdm.homelink.energy.hydro;

/**
 * Hydro pump levels. Footprints are WIDTH x HEIGHT x DEPTH and follow the solar and wind arrays:
 * width runs to the clockwise side of the intake, depth runs away from the water.
 */
public enum HydroPumpTier {
    I(1, 1, 1), II(2, 2, 1), III(3, 2, 2);

    private final int level, width, depth;

    HydroPumpTier(int level, int width, int depth) {
        this.level = level;
        this.width = width;
        this.depth = depth;
    }

    public int level() { return level; }
    public int width() { return width; }
    public int depth() { return depth; }
    public int index() { return level - 1; }
}
