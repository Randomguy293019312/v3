package com.meteorologia;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private Config() {}

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // ---- server / world
    public static final ModConfigSpec.BooleanValue NATURAL_STORMS;
    public static final ModConfigSpec.DoubleValue STORM_FREQUENCY;
    public static final ModConfigSpec.IntValue MAX_SUPERCELLS;
    public static final ModConfigSpec.BooleanValue BLOCK_DAMAGE;
    public static final ModConfigSpec.IntValue MAX_DEBRIS;
    public static final ModConfigSpec.BooleanValue LIGHTNING;
    public static final ModConfigSpec.BooleanValue VEHICLE_DAMAGE;

    // ---- client: storms
    public static final ModConfigSpec.BooleanValue ENABLE_SHADER;
    public static final ModConfigSpec.IntValue STORM_STEPS;
    public static final ModConfigSpec.IntValue TORNADO_VIEW_DISTANCE;
    public static final ModConfigSpec.BooleanValue STORM_FOG;

    // ---- client: sun
    public static final ModConfigSpec.BooleanValue SUN_ENABLED;
    public static final ModConfigSpec.DoubleValue SUN_SIZE;
    public static final ModConfigSpec.DoubleValue SUN_GLOW;

    // ---- client: clouds
    public static final ModConfigSpec.BooleanValue CLOUDS_ENABLED;
    public static final ModConfigSpec.DoubleValue CLOUD_AMOUNT;
    public static final ModConfigSpec.DoubleValue CLOUD_REALISM;
    public static final ModConfigSpec.IntValue CLOUD_HEIGHT;
    public static final ModConfigSpec.DoubleValue CLOUD_SPEED;
    public static final ModConfigSpec.IntValue CLOUD_QUALITY;

    private static final String T = "meteorologia.configuration.";

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();
        NATURAL_STORMS = common
                .comment("Supercells and tornadoes form naturally when it rains / thunders.")
                .translation(T + "naturalStorms")
                .define("naturalStorms", true);
        STORM_FREQUENCY = common
                .comment("Natural storm frequency multiplier (0 = never, 1 = normal).")
                .translation(T + "stormFrequency")
                .defineInRange("stormFrequency", 1.0D, 0.0D, 10.0D);
        MAX_SUPERCELLS = common
                .comment("Maximum number of simultaneous supercells.")
                .translation(T + "maxSupercells")
                .defineInRange("maxSupercells", 2, 1, 8);
        BLOCK_DAMAGE = common
                .comment("Tornadoes destroy blocks (depends on the EF rating).")
                .translation(T + "blockDamage")
                .define("blockDamage", true);
        MAX_DEBRIS = common
                .comment("Maximum flying debris blocks per tornado (lag control). 0 disables debris.")
                .translation(T + "maxDebris")
                .defineInRange("maxDebris", 40, 0, 400);
        LIGHTNING = common
                .comment("Supercells strike lightning near players.")
                .translation(T + "lightning")
                .define("lightning", true);
        VEHICLE_DAMAGE = common
                .comment("Vehicles take damage from crashes and tornado debris (they stop working at 0 hull).")
                .translation(T + "vehicleDamage")
                .define("vehicleDamage", true);
        COMMON_SPEC = common.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        ENABLE_SHADER = client
                .comment("Ray marched tornadoes and supercells. Turn off if you have problems (particles are used instead).")
                .translation(T + "rayMarching")
                .define("rayMarching", true);
        STORM_STEPS = client
                .comment("Storm ray marching quality (steps per pixel). 24 = fast, 48 = normal, 96 = pretty.")
                .translation(T + "stormSteps")
                .defineInRange("stormSteps", 48, 16, 120);
        TORNADO_VIEW_DISTANCE = client
                .comment("Maximum distance (blocks) at which tornadoes are drawn. Far tornadoes are scaled up so they stay visible.")
                .translation(T + "tornadoViewDistance")
                .defineInRange("tornadoViewDistance", 8000, 1000, 16000);
        STORM_FOG = client
                .comment("Dense fog during heavy rain / near storms.")
                .translation(T + "stormFog")
                .define("stormFog", true);

        SUN_ENABLED = client
                .comment("Realistic sun (the vanilla sun texture is hidden by this mod). Off = no sun is drawn.")
                .translation(T + "sunEnabled")
                .define("sunEnabled", true);
        SUN_SIZE = client
                .comment("Apparent diameter of the sun disc, in degrees.")
                .translation(T + "sunSize")
                .defineInRange("sunSize", 4.0D, 1.0D, 15.0D);
        SUN_GLOW = client
                .comment("Strength of the glow / halo around the sun.")
                .translation(T + "sunGlow")
                .defineInRange("sunGlow", 1.0D, 0.0D, 2.0D);

        CLOUDS_ENABLED = client
                .comment("Volumetric clouds (the vanilla clouds are hidden by this mod). Off = no clouds are drawn.")
                .translation(T + "cloudsEnabled")
                .define("cloudsEnabled", true);
        CLOUD_AMOUNT = client
                .comment("How much of the sky is covered (0 = none, 1 = overcast). Rain adds to this automatically.")
                .translation(T + "cloudAmount")
                .defineInRange("cloudAmount", 0.35D, 0.0D, 1.0D);
        CLOUD_REALISM = client
                .comment("0 = blocky vanilla-like clouds, 1 = fully realistic billowing clouds (more GPU cost).")
                .translation(T + "cloudRealism")
                .defineInRange("cloudRealism", 0.8D, 0.0D, 1.0D);
        CLOUD_HEIGHT = client
                .comment("Altitude (Y) of the cloud base.")
                .translation(T + "cloudHeight")
                .defineInRange("cloudHeight", 192, 100, 300);
        CLOUD_SPEED = client
                .comment("Cloud drift speed multiplier (0 = frozen).")
                .translation(T + "cloudSpeed")
                .defineInRange("cloudSpeed", 1.0D, 0.0D, 5.0D);
        CLOUD_QUALITY = client
                .comment("Cloud ray marching steps. Lower = faster, higher = smoother.")
                .translation(T + "cloudQuality")
                .defineInRange("cloudQuality", 28, 12, 64);
        CLIENT_SPEC = client.build();
    }
}
