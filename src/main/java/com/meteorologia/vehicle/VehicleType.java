package com.meteorologia.vehicle;

/**
 * The storm-chasing vehicles. Pure data (no Minecraft classes) so the physics and the
 * geometry can be unit-tested outside the game.
 *
 * Seat offsets are {left, up, forward} in blocks, relative to the vehicle origin (ground, centre).
 * Position 0 is the driver (left-hand drive).
 */
public enum VehicleType {
    //            id             seats  km/h   accel   wheelbase grip  health armor anchor fuel  width height wheelR
    DOMINATOR("dominator",       3,  130F, 0.0130F, 3.3F, 0.80F, 260F, 0.60F, true,  3000, 2.3F, 1.9F, 0.46F,
            new float[][] {{0.45F, -0.05F, 0.35F}, {-0.45F, -0.05F, 0.35F}, {0.0F, -0.05F, -0.75F}}),
    TIV("tiv",                   4,  105F, 0.0125F, 3.7F, 0.75F, 520F, 0.80F, true,  3600, 2.7F, 2.5F, 0.62F,
            new float[][] {{0.55F, 0.20F, 0.55F}, {-0.55F, 0.20F, 0.55F}, {0.55F, 0.20F, -0.65F}, {-0.55F, 0.20F, -0.65F}}),
    CHASER_SUV("chaser_suv",     4,  140F, 0.0150F, 3.0F, 0.82F, 160F, 0.25F, false, 2600, 2.1F, 2.0F, 0.43F,
            new float[][] {{0.45F, 0.05F, 0.30F}, {-0.45F, 0.05F, 0.30F}, {0.45F, 0.05F, -0.80F}, {-0.45F, 0.05F, -0.80F}});

    public final String id;
    public final int seats;
    public final float topSpeedKmh;
    /** Peak engine acceleration in blocks/tick^2. */
    public final float accel;
    public final float wheelbase;
    /** 0..1: how quickly sideways sliding is killed (higher = more planted). */
    public final float grip;
    public final float maxHealth;
    /** Fraction of damage that is blocked (also protects the riders). */
    public final float armor;
    public final boolean canAnchor;
    public final int fuelTank;
    public final float width;
    public final float height;
    public final float wheelRadius;
    public final float[][] seatOffsets;

    VehicleType(String id, int seats, float topSpeedKmh, float accel, float wheelbase, float grip,
                float maxHealth, float armor, boolean canAnchor, int fuelTank,
                float width, float height, float wheelRadius, float[][] seatOffsets) {
        this.id = id;
        this.seats = seats;
        this.topSpeedKmh = topSpeedKmh;
        this.accel = accel;
        this.wheelbase = wheelbase;
        this.grip = grip;
        this.maxHealth = maxHealth;
        this.armor = armor;
        this.canAnchor = canAnchor;
        this.fuelTank = fuelTank;
        this.width = width;
        this.height = height;
        this.wheelRadius = wheelRadius;
        this.seatOffsets = seatOffsets;
    }

    /** Top speed in blocks per tick (1 block = 1 m, 1 tick = 1/20 s). */
    public float topSpeed() {
        return topSpeedKmh / 72F;
    }

    public static VehicleType byOrdinal(int i) {
        VehicleType[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
