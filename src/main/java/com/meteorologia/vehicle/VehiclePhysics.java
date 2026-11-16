package com.meteorologia.vehicle;

/**
 * Arcade-realistic car physics (bicycle model with tyre grip limits), in world axes.
 * Pure Java: no Minecraft classes. Speeds are blocks/tick, angles in degrees (Minecraft yaw:
 * 0 = facing +Z, increasing yaw turns right) except {@link State#steer} which is radians.
 */
public final class VehiclePhysics {
    private VehiclePhysics() {}

    public static final class State {
        public double vx;
        public double vz;
        public float yaw;
        /** Current front-wheel angle in radians (positive = left). */
        public float steer;
        /** Signed speed along the heading (blocks/tick), updated every step. */
        public float forwardSpeed;
    }

    private static final float BRAKE = 0.0225F;     // ~9 m/s^2
    private static final float ROLLING = 0.9996F;
    private static final float COAST = 0.9890F;

    /**
     * Advances the horizontal motion by one tick.
     *
     * @param throttle  +1 = accelerate, -1 = brake / reverse
     * @param steerLeft +1 = full left lock, -1 = full right lock
     * @param gripMul   surface/weather grip multiplier (1 = dry asphalt)
     * @param powered   false when out of fuel or wrecked
     */
    public static void step(State s, VehicleType t, float throttle, float steerLeft, float gripMul, boolean powered) {
        final float top = t.topSpeed();
        double yawRad = Math.toRadians(s.yaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double vf = s.vx * fx + s.vz * fz;

        // ---- steering: the lock reduces with speed, wheels move smoothly
        float speedFrac = (float) Math.min(1.0, Math.abs(vf) / top);
        float maxLock = 0.62F + (0.10F - 0.62F) * speedFrac;
        float target = steerLeft * maxLock;
        s.steer += (target - s.steer) * 0.28F;

        // ---- yaw rate from the bicycle model; the tyres can only supply so much lateral force
        double yawRate = vf * Math.tan(s.steer) / t.wheelbase;          // rad/tick, positive = left
        double limit = 0.026 * gripMul * (0.6 + 0.4 * t.grip);          // max lateral acceleration (~0.9 g on dry asphalt)
        double demand = Math.abs(vf * yawRate);
        boolean sliding = demand > limit;
        if (sliding) {
            yawRate *= limit / demand;                                  // understeer
        }
        s.yaw -= (float) Math.toDegrees(yawRate);
        s.yaw = wrap(s.yaw);

        // ---- re-express the (unchanged) velocity in the new heading
        yawRad = Math.toRadians(s.yaw);
        fx = -Math.sin(yawRad);
        fz = Math.cos(yawRad);
        double lx = Math.cos(yawRad);
        double lz = Math.sin(yawRad);
        vf = s.vx * fx + s.vz * fz;
        double vl = s.vx * lx + s.vz * lz;

        // ---- longitudinal forces
        if (!powered) {
            throttle = Math.min(throttle, 0F);
        }
        if (throttle > 0F) {
            if (vf < -0.02) {
                vf += BRAKE * 1.2F;                                     // braking out of reverse
            } else {
                // throttle sets a target speed: equilibrium where throttle = (v / top)^2
                double r = Math.min(1.2, vf / top);
                double acc = t.accel * (throttle - r * r) * gripMul;
                vf += acc < 0 ? acc * 0.6 : acc;
            }
            vf *= ROLLING;
        } else if (throttle < 0F) {
            if (vf > 0.03) {
                vf -= BRAKE * (0.6F + 0.4F * -throttle) * Math.min(1.0F, 0.5F + gripMul * 0.5F);
            } else if (powered) {
                vf -= t.accel * 0.55F * -throttle;                      // reverse
                vf = Math.max(vf, -top * 0.28);
            }
        } else {
            vf *= COAST;                                                // engine braking
        }
        if (Math.abs(vf) < 0.004 && throttle == 0F) {
            vf = 0.0;
        }

        // ---- lateral grip: kills sideways velocity (less when sliding)
        double grip = Math.min(0.97, t.grip * gripMul);
        if (sliding) {
            grip *= 0.55;
        }
        vl *= (1.0 - grip);

        s.vx = fx * vf + lx * vl;
        s.vz = fz * vf + lz * vl;
        s.forwardSpeed = (float) vf;
    }

    public static float wrap(float deg) {
        while (deg > 180F) deg -= 360F;
        while (deg < -180F) deg += 360F;
        return deg;
    }
}
