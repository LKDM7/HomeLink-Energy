package fr.lkdm.homelink.energy.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only presentation of Hydro machines ({@code config/homelink_energy-client.toml}). Never affects production. */
public final class HydroClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SOUNDS;
    public static final ModConfigSpec.DoubleValue VOLUME;
    public static final ModConfigSpec.BooleanValue PARTICLES;
    public static final ModConfigSpec.BooleanValue ANIMATIONS;
    public static final ModConfigSpec.IntValue PREVIEW_RANGE;

    static {
        var b = new ModConfigSpec.Builder();
        b.push("hydro");
        SOUNDS = b.comment("Play pump, turbine and discharge sounds.").define("sounds", true);
        VOLUME = b.comment("Volume multiplier of Hydro sounds.").defineInRange("volume", 0.6, 0.0, 1.0);
        PARTICLES = b.comment("Show discharge splash and bubble particles (vanilla particle settings still apply).").define("particles", true);
        ANIMATIONS = b.comment("Animate rotor, louvers and the water sheet. False keeps them still (reduced motion).").define("animations", true);
        PREVIEW_RANGE = b.comment("Distance in blocks under which water-zone and obstruction previews are drawn.").defineInRange("previewRange", 64, 16, 128);
        b.pop();
        SPEC = b.build();
    }

    private HydroClientConfig() { }

    public static boolean sounds() { return !SPEC.isLoaded() || SOUNDS.get(); }
    public static float volume() { return SPEC.isLoaded() ? VOLUME.get().floatValue() : 0.6f; }
    public static boolean particles() { return !SPEC.isLoaded() || PARTICLES.get(); }
    public static boolean animations() { return !SPEC.isLoaded() || ANIMATIONS.get(); }
    public static int previewRange() { return SPEC.isLoaded() ? PREVIEW_RANGE.get() : 64; }
}
