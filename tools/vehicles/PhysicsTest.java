import com.meteorologia.vehicle.*;
public class PhysicsTest {
    static VehiclePhysics.State fresh() { return new VehiclePhysics.State(); }
    public static void main(String[] a) {
        for (VehicleType t : VehicleType.values()) {
            System.out.println("=== " + t + "  top " + t.topSpeedKmh + " km/h, wheelbase " + t.wheelbase);
            // 0-100 km/h and time to 95% of top speed
            VehiclePhysics.State s = fresh(); int t100 = -1, t95 = -1;
            for (int i = 1; i <= 2000; i++) {
                VehiclePhysics.step(s, t, 1, 0, 1, true);
                double kmh = Math.hypot(s.vx, s.vz) * 72;
                if (t100 < 0 && kmh >= 100) t100 = i;
                if (t95 < 0 && kmh >= t.topSpeedKmh * 0.95) { t95 = i; }
            }
            System.out.printf("  0-100 km/h: %s s | 95%% top: %s s | final %.1f km/h%n",
                    t100 < 0 ? "n/a" : String.format("%.1f", t100 / 20.0), t95 < 0 ? "n/a" : String.format("%.1f", t95 / 20.0), Math.hypot(s.vx, s.vz) * 72);
            // braking distance from 100 (or top if lower)
            s = fresh(); double v0 = Math.min(100.0, t.topSpeedKmh * 0.95) / 72.0; s.vz = v0; s.vx = 0; s.yaw = 0; double dist = 0; int ticks = 0;
            while (Math.hypot(s.vx, s.vz) > 0.01 && ticks < 2000) { VehiclePhysics.step(s, t, -1, 0, 1, true); dist += Math.hypot(s.vx, s.vz); ticks++; }
            System.out.printf("  braking from %.0f km/h: %.1f m in %.1f s%n", v0 * 72, dist, ticks / 20.0);
            // turning radius at various speeds (full left lock)
            for (double kmh : new double[] {20, 40, 70, 100}) {
                if (kmh > t.topSpeedKmh) continue;
                s = fresh(); s.vz = kmh / 72.0; double x = 0, z = 0, minx = 1e9, maxx = -1e9, minz = 1e9, maxz = -1e9; double slip = 0;
                for (int i = 0; i < 400; i++) {
                    double sp = Math.hypot(s.vx, s.vz) / t.topSpeed(); VehiclePhysics.step(s, t, (float) Math.min(1.0, (kmh / 72.0 / t.topSpeed()) * (kmh / 72.0 / t.topSpeed()) + 0.02), 1, 1, true); x += s.vx; z += s.vz;
                    if (i > 80) { minx = Math.min(minx, x); maxx = Math.max(maxx, x); minz = Math.min(minz, z); maxz = Math.max(maxz, z);
                        double yr = Math.toRadians(s.yaw); double lat = s.vx * Math.cos(yr) + s.vz * Math.sin(yr); slip = Math.max(slip, Math.abs(lat)); }
                }
                double radius = Math.max(maxx - minx, maxz - minz) / 2;
                System.out.printf("  full-left lock @ %3.0f km/h (holding speed): turn radius ~%.1f m, max sideways slip %.2f b/tick, speed now %.0f km/h%n", kmh, radius, slip, Math.hypot(s.vx, s.vz) * 72);
            }
            // wet road: same turn at 70 km/h
            s = fresh(); s.vz = Math.min(70, t.topSpeedKmh) / 72.0; double slip2 = 0;
            for (int i = 0; i < 200; i++) { VehiclePhysics.step(s, t, 0.3F, 1, 0.55F, true); double yr = Math.toRadians(s.yaw); slip2 = Math.max(slip2, Math.abs(s.vx * Math.cos(yr) + s.vz * Math.sin(yr))); }
            System.out.printf("  wet road (grip .55) @ 70 km/h: max sideways slip %.2f b/tick%n", slip2);
            // reverse
            s = fresh(); for (int i = 0; i < 200; i++) VehiclePhysics.step(s, t, -1, 0, 1, true);
            System.out.printf("  reverse top: %.1f km/h%n", s.forwardSpeed * 72);
        }
    }
}
