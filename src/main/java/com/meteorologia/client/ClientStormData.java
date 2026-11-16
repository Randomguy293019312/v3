package com.meteorologia.client;

import com.meteorologia.net.StormSyncPayload;
import com.meteorologia.weather.Supercell;
import com.meteorologia.weather.Tornado;

import java.util.ArrayList;
import java.util.List;

/**
 * Local copy of the storms. IMPORTANT: this class does NOT import anything from
 * net.minecraft.client, so it is safe to reference from common code.
 * Positions are extrapolated (dead reckoning) between sync packets.
 */
public final class ClientStormData {
    private ClientStormData() {}

    private static List<Supercell> supercells = new ArrayList<>();
    private static List<Tornado> tornadoes = new ArrayList<>();
    private static float rain;

    private static long tickCount;
    private static long lastTickNanos = System.nanoTime();
    private static long syncTick;
    private static double syncSub;

    /** Cloud pattern offset (blocks), accumulated from the wind each tick. */
    public static double cloudOffX;
    public static double cloudOffZ;
    /** Smoothed 0..1 intensity of storms around the player (darkens clouds, thickens fog). */
    public static float stormLevel;

    public static void apply(StormSyncPayload p) {
        supercells = new ArrayList<>(p.supercells());
        tornadoes = new ArrayList<>(p.tornadoes());
        rain = p.rain();
        syncTick = tickCount;
        syncSub = subTick();
    }

    public static void reset() {
        supercells = new ArrayList<>();
        tornadoes = new ArrayList<>();
        rain = 0F;
        stormLevel = 0F;
    }

    /** Called once per client tick. */
    public static void tick() {
        tickCount++;
        lastTickNanos = System.nanoTime();
    }

    public static double subTick() {
        double f = (System.nanoTime() - lastTickNanos) / 50_000_000.0;
        return Math.max(0.0, Math.min(1.0, f));
    }

    /** Ticks elapsed since the last packet (with a tick fraction, for smooth animation). */
    public static double elapsedTicks() {
        return (tickCount - syncTick) + subTick() - syncSub;
    }

    public static List<Supercell> supercells() {
        return supercells;
    }

    public static List<Tornado> tornadoes() {
        return tornadoes;
    }

    public static float rain() {
        return rain;
    }

    public static float strength(Supercell s, double el) {
        return (float) Math.max(0.0, Math.min(1.0, s.strength + s.dStrength * el));
    }

    public static float strength(Tornado t, double el) {
        return (float) Math.max(0.0, Math.min(1.0, t.strength + t.dStrength * el));
    }

    /**
     * Local rain intensity (0..1) at the given point, caused by nearby supercells.
     * Used to override the client rain level ("local" rain).
     */
    public static float localIntensity(double px, double pz) {
        double el = elapsedTicks();
        float best = 0F;
        for (Supercell s : supercells) {
            double sx = s.x + s.vx * el;
            double sz = s.z + s.vz * el;
            double d = Math.hypot(px - sx, pz - sz);
            double core = s.radius * 0.9;
            double fade = s.radius * 2.2;
            double k = Math.max(0.0, Math.min(1.0, (fade - d) / (fade - core)));
            best = Math.max(best, (float) (k * (0.35 + 0.65 * strength(s, el))));
        }
        return best;
    }
}
