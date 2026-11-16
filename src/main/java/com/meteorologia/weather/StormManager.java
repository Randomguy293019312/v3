package com.meteorologia.weather;

import com.meteorologia.Config;
import com.meteorologia.net.StormSyncPayload;
import com.meteorologia.entity.ChaserVehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Server logic: spawning, movement, effects and syncing of the storms. */
public final class StormManager {
    private StormManager() {}

    public static final String DEBRIS_TAG = "meteorologia_debris";

    private static final Map<ResourceKey<Level>, StormWorld> WORLDS = new HashMap<>();

    /** Acceleration applied to entities (blocks/tick^2) per EF rating. */
    private static final double[] EF_POWER = {0.018, 0.030, 0.050, 0.075, 0.105, 0.140};
    /** Maximum blast resistance of the blocks a tornado can tear out, per EF rating. */
    private static final float[] EF_BLOCK_RESISTANCE = {0.35F, 0.8F, 2.5F, 4.5F, 8.0F, 40.0F};
    /** Typical tornado width (diameter, blocks) per EF rating; randomised +-40%. */
    private static final float[] EF_WIDTH = {16F, 28F, 44F, 64F, 90F, 120F};

    public static StormWorld get(Level level) {
        return WORLDS.computeIfAbsent(level.dimension(), k -> new StormWorld());
    }

    public static void clearAllWorlds() {
        WORLDS.clear();
    }

    // =====================================================================
    //  Main tick
    // =====================================================================
    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        StormWorld w = get(level);
        long time = level.getGameTime();

        if (time % 100L == 0L) {
            naturalSpawn(level, w, players);
        }

        for (Iterator<Supercell> it = w.supercells.iterator(); it.hasNext(); ) {
            if (!tickSupercell(level, w, it.next(), players)) {
                it.remove();
            }
        }
        for (Iterator<Tornado> it = w.tornadoes.iterator(); it.hasNext(); ) {
            if (!tickTornado(level, w, it.next(), players)) {
                it.remove();
            }
        }

        if (time % 20L == 0L && w.rainIntensity > 0F && !level.isRaining()) {
            w.rainIntensity = Math.max(0F, w.rainIntensity - 0.05F);
        }
        sync(level, w, players, time);
    }

    // =====================================================================
    //  Spawning
    // =====================================================================
    private static void naturalSpawn(ServerLevel level, StormWorld w, List<ServerPlayer> players) {
        if (!Config.NATURAL_STORMS.get() || w.supercells.size() >= Config.MAX_SUPERCELLS.get()) {
            return;
        }
        double p = level.isThundering() ? 0.05 : level.isRaining() ? 0.02 : 0.002;
        p *= (0.6 + w.rainIntensity) * Config.STORM_FREQUENCY.get();
        if (level.random.nextDouble() >= p) {
            return;
        }
        ServerPlayer target = players.get(level.random.nextInt(players.size()));
        spawnApproaching(level, w, target.getX(), target.getZ(),
                1500 + level.random.nextInt(1200), randomPotential(level.random));
    }

    private static int randomPotential(RandomSource r) {
        float f = r.nextFloat();
        if (f < 0.35F) return -1;   // no tornado
        if (f < 0.55F) return 0;
        if (f < 0.73F) return 1;
        if (f < 0.85F) return 2;
        if (f < 0.93F) return 3;
        if (f < 0.98F) return 4;
        return 5;
    }

    private static Supercell newSupercell(StormWorld w, RandomSource r, double x, double z, double vx, double vz) {
        Supercell s = new Supercell();
        s.id = w.nextId++;
        s.x = x;
        s.z = z;
        s.vx = vx;
        s.vz = vz;
        s.radius = 260F + r.nextFloat() * 220F;
        s.baseY = 210F + r.nextFloat() * 50F;
        s.topY = s.baseY + 650F + r.nextFloat() * 300F;
        s.windAngle = (float) Math.atan2(vz, vx);
        return s;
    }

    /** A supercell that forms far away and approaches the point (px, pz). */
    public static Supercell spawnApproaching(ServerLevel level, StormWorld w, double px, double pz,
                                             double dist, int maxEf) {
        RandomSource r = level.random;
        double ang = r.nextDouble() * Math.PI * 2.0;
        double sx = px + Math.cos(ang) * dist;
        double sz = pz + Math.sin(ang) * dist;
        double aim = (r.nextDouble() - 0.5) * dist * 0.4;
        double tx = px - Math.sin(ang) * aim;
        double tz = pz + Math.cos(ang) * aim;
        double speed = 0.25 + r.nextDouble() * 0.2;
        double dx = tx - sx;
        double dz = tz - sz;
        double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        Supercell s = newSupercell(w, r, sx, sz, dx / len * speed, dz / len * speed);
        s.maxEf = maxEf;
        s.lifetime = (int) (dist / speed) + 5000 + r.nextInt(4000);
        w.supercells.add(s);
        return s;
    }

    /** Picks a funnel shape from the width when the user did not choose one. */
    private static int pickShape(RandomSource r, float widthBlocks) {
        if (widthBlocks < 16F) {
            return Tornado.SHAPE_ROPE;
        }
        if (widthBlocks > 80F) {
            return Tornado.SHAPE_WEDGE;
        }
        return r.nextFloat() < 0.65F ? Tornado.SHAPE_CONE : Tornado.SHAPE_STOVEPIPE;
    }

    /**
     * Creates a tornado (with its parent supercell) exactly at (x, z).
     *
     * @param width diameter of the damage swath in blocks, or {@code <= 0} for a random width typical of the EF rating
     * @param shape one of the {@code Tornado.SHAPE_*} constants (SHAPE_AUTO picks one from the width)
     */
    public static Tornado spawnTornado(ServerLevel level, double x, double z, int ef, double width, int shape) {
        StormWorld w = get(level);
        RandomSource r = level.random;
        double heading = r.nextDouble() * Math.PI * 2.0;
        double speed = 0.18 + r.nextDouble() * 0.12;
        Supercell s = newSupercell(w, r, x, z, Math.cos(heading) * speed, Math.sin(heading) * speed);
        double offAng = r.nextDouble() * Math.PI * 2.0;
        double offDist = s.radius * 0.28;
        s.x = x - Math.cos(offAng) * offDist;
        s.z = z - Math.sin(offAng) * offDist;
        s.age = 600;
        s.lifetime = 600 + 4800 + ef * 600;
        s.maxEf = ef;
        s.hasTornado = true;
        w.supercells.add(s);
        Tornado t = spawnTornadoFor(level, w, s, ef, 120, offAng, offDist, width, shape);
        t.lifetime = 4200 + ef * 600;
        return t;
    }

    private static Tornado spawnTornadoFor(ServerLevel level, StormWorld w, Supercell s, int ef,
                                           int formTicks, double offAng, double offDist,
                                           double width, int shape) {
        RandomSource r = level.random;
        Tornado t = new Tornado();
        t.id = w.nextId++;
        t.parentId = s.id;
        t.ef = Math.max(0, Math.min(5, ef));
        float wBlocks = width > 0.0 ? (float) width : EF_WIDTH[t.ef] * (0.6F + r.nextFloat() * 0.8F);
        t.radius = Math.max(2F, wBlocks / 2F);
        t.shape = shape == Tornado.SHAPE_AUTO ? pickShape(r, wBlocks) : shape;
        t.formTicks = formTicks;
        t.lifetime = 2400 + t.ef * 900 + r.nextInt(2400);
        t.offAngle = offAng;
        t.offDist = offDist;
        t.x = s.x + Math.cos(offAng) * offDist;
        t.z = s.z + Math.sin(offAng) * offDist;
        t.y = groundY(level, t.x, t.z, level.getSeaLevel());
        t.height = Math.max(60F, s.baseY - (float) t.y);
        s.hasTornado = true;
        w.tornadoes.add(t);

        for (ServerPlayer p : level.players()) {
            double dx = t.x - p.getX();
            double dz = t.z - p.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 6000.0) {
                p.displayClientMessage(Component.literal("§4§l[WARNING] EF" + t.ef + " tornado forming "
                        + Compass.distance(d) + " " + Compass.direction(dx, dz) + " of you! Seek underground shelter."), false);
            }
        }
        return t;
    }

    /**
     * Terrain height at (x, z). When the chunk is loaded the real surface is used; otherwise the
     * height is estimated from the world generator (no chunk is loaded or generated), so tornadoes
     * that spawn beyond the loaded area still touch the ground instead of floating at sea level.
     */
    public static double groundY(ServerLevel level, double x, double z, double fallback) {
        BlockPos bp = BlockPos.containing(x, 64, z);
        if (level.hasChunkAt(bp)) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
        }
        try {
            ServerChunkCache cache = level.getChunkSource();
            return cache.getGenerator().getBaseHeight(bp.getX(), bp.getZ(),
                    Heightmap.Types.WORLD_SURFACE_WG, level, cache.randomState());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    // =====================================================================
    //  Supercell
    // =====================================================================
    private static boolean tickSupercell(ServerLevel level, StormWorld w, Supercell s, List<ServerPlayer> players) {
        s.age++;
        float prev = s.strength;
        float grow = Math.min(1F, s.age / 1200F);
        float decay = s.age > s.lifetime - 1200 ? Math.max(0F, (s.lifetime - s.age) / 1200F) : 1F;
        s.strength = grow * decay;
        s.dStrength = s.strength - prev;
        s.x += s.vx;
        s.z += s.vz;
        if (s.age >= s.lifetime) {
            return false;
        }

        // tornadogenesis
        if (!s.hasTornado && s.maxEf >= 0 && s.age > 1800 && s.age < s.lifetime - 3600
                && level.random.nextFloat() < 0.0025F && nearAnyPlayer(players, s.x, s.z, 3000.0)) {
            double offAng = level.random.nextDouble() * Math.PI * 2.0;
            spawnTornadoFor(level, w, s, s.maxEf, 600, offAng, s.radius * 0.28, 0.0, Tornado.SHAPE_AUTO);
        }

        // global rain/thunder when the storm is right above someone
        if (s.age % 40 == 0 && s.strength > 0.3F && !level.isThundering()
                && nearAnyPlayer(players, s.x, s.z, s.radius * 1.2)) {
            level.setWeatherParameters(0, 6000, true, true);
        }

        if (s.age % 20 == 0 && s.strength > 0.5F && Config.LIGHTNING.get()) {
            lightning(level, s, players);
        }
        return true;
    }

    private static void lightning(ServerLevel level, Supercell s, List<ServerPlayer> players) {
        RandomSource r = level.random;
        double reach = s.radius * 1.3;
        for (ServerPlayer p : players) {
            double dx = p.getX() - s.x;
            double dz = p.getZ() - s.z;
            if (dx * dx + dz * dz > reach * reach || r.nextFloat() > 0.10F * s.strength) {
                continue;
            }
            double ang = r.nextDouble() * Math.PI * 2.0;
            double d = 25.0 + r.nextDouble() * 70.0;
            BlockPos col = BlockPos.containing(p.getX() + Math.cos(ang) * d, 64, p.getZ() + Math.sin(ang) * d);
            if (!level.hasChunkAt(col)) {
                continue;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(top));
                level.addFreshEntity(bolt);
            }
        }
    }

    // =====================================================================
    //  Tornado
    // =====================================================================
    private static boolean tickTornado(ServerLevel level, StormWorld w, Tornado t, List<ServerPlayer> players) {
        Supercell parent = w.findSupercell(t.parentId);
        final int decayTicks = 600;
        t.age++;
        if (parent == null && t.lifetime > t.age + decayTicks) {
            t.lifetime = t.age + decayTicks;
        }
        float form = Math.min(1F, t.age / (float) Math.max(1, t.formTicks));
        float decay = t.age > t.lifetime - decayTicks ? Math.max(0F, (t.lifetime - t.age) / (float) decayTicks) : 1F;
        float prev = t.strength;
        t.strength = form * decay;
        t.dStrength = t.strength - prev;
        if (t.age >= t.lifetime) {
            return false;
        }

        // movement: follows the supercell, with some meandering
        if (parent != null) {
            t.offAngle += (level.random.nextDouble() - 0.5) * 0.01;
            double nx = parent.x + Math.cos(t.offAngle) * t.offDist;
            double nz = parent.z + Math.sin(t.offAngle) * t.offDist;
            t.vx = nx - t.x;
            t.vz = nz - t.z;
            t.x = nx;
            t.z = nz;
        } else {
            t.x += t.vx;
            t.z += t.vz;
        }
        BlockPos bp = BlockPos.containing(t.x, 64, t.z);
        if (level.hasChunkAt(bp)) {
            double gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
            t.y += (gy - t.y) * 0.15;
        } else if (t.age % 10 == 0) {
            // not loaded: estimate from the world generator (cheap enough every half second)
            t.y += (groundY(level, t.x, t.z, t.y) - t.y) * 0.5;
        }
        float baseY = parent != null ? parent.baseY : (float) t.y + 140F;
        t.height = Math.max(60F, baseY - (float) t.y);

        if (t.strength > 0.08F && nearAnyPlayer(players, t.x, t.z, Math.max(400.0, t.radius * 4.0 + 60.0))) {
            affectEntities(level, t);
            if (Config.BLOCK_DAMAGE.get()) {
                damageBlocks(level, t);
            }
        }
        return true;
    }

    private static double effRadius(Tornado t) {
        return t.radius * (0.55 + 0.45 * t.strength);
    }

    private static void affectEntities(ServerLevel level, Tornado t) {
        double radius = effRadius(t);
        double reach = radius * 4.0 + 20.0;
        AABB box = new AABB(t.x - reach, t.y - 8.0, t.z - reach, t.x + reach, t.y + t.height + 10.0, t.z + reach);
        List<Entity> list = level.getEntitiesOfClass(Entity.class, box,
                e -> e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.getAbilities().flying));
        long time = level.getGameTime();

        for (Entity e : list) {
            double dx = e.getX() - t.x;
            double dz = e.getZ() - t.z;
            double r = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            if (r > reach || e.isPassenger()) {
                continue;   // passengers are carried by (and protected by) their vehicle
            }
            // underground shelter protects
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                    (int) Math.floor(e.getX()), (int) Math.floor(e.getZ()));
            if (e.getY() < surface - 4) {
                continue;
            }
            double fall = r < radius * 1.6 ? 1.0 : Math.max(0.0, 1.0 - (r - radius * 1.6) / (reach - radius * 1.6));
            double p = EF_POWER[t.ef] * t.strength * fall;
            if (e instanceof Player pl && pl.isShiftKeyDown()) {
                p *= 0.6;
            }
            if (e instanceof ChaserVehicle v) {
                p *= v.windPushFactor(t.ef);
                if (t.ef >= 1 && r < radius * 1.15 && time % 10L == 0L) {
                    v.hurtVehicle((2.0F + t.ef * 5.0F) * t.strength);
                }
            }
            double nx = dx / r;
            double nz = dz / r;
            double ax = (nz * 0.9 - nx * 0.7) * p;
            double az = (-nx * 0.9 - nz * 0.7) * p;
            double ay = (r < radius * 2.0 ? 1.15 : 0.15) * p;

            Vec3 v = e.getDeltaMovement().add(ax, ay, az);
            double hx = v.x;
            double hz = v.z;
            double hLen = Math.sqrt(hx * hx + hz * hz);
            if (hLen > 1.6) {
                hx = hx / hLen * 1.6;
                hz = hz / hLen * 1.6;
            }
            e.setDeltaMovement(hx, Math.min(v.y, 0.85), hz);
            e.hurtMarked = true;

            if (t.ef >= 1 && r < radius * 1.15 && time % 10L == 0L && e instanceof LivingEntity le) {
                le.hurt(level.damageSources().generic(), 1.0F + t.ef * 1.2F * t.strength);
            }
        }
    }

    private static void damageBlocks(ServerLevel level, Tornado t) {
        RandomSource rnd = level.random;
        double radius = effRadius(t);
        float maxRes = EF_BLOCK_RESISTANCE[t.ef];

        // wider tornadoes tear out proportionally more blocks per tick
        double samplesD = Math.min(24.0, (1 + t.ef * 2) * t.strength * Math.max(1.0, t.radius / 12.0));
        int samples = (int) samplesD;
        if (rnd.nextDouble() < samplesD - samples) {
            samples++;
        }
        if (samples <= 0) {
            return;
        }

        AABB area = new AABB(t.x - radius * 2.5, t.y - 8.0, t.z - radius * 2.5,
                t.x + radius * 2.5, t.y + t.height, t.z + radius * 2.5);
        int debris = Config.MAX_DEBRIS.get() <= 0 ? Integer.MAX_VALUE
                : level.getEntitiesOfClass(FallingBlockEntity.class, area, e -> e.getTags().contains(DEBRIS_TAG)).size();

        for (int i = 0; i < samples; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double rr = radius * (0.3 + rnd.nextDouble() * 1.4);
            int bx = (int) Math.floor(t.x + Math.cos(ang) * rr);
            int bz = (int) Math.floor(t.z + Math.sin(ang) * rr);
            if (!level.hasChunkAt(new BlockPos(bx, 64, bz))) {
                continue;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
            int by = top - rnd.nextInt(1 + t.ef / 2);
            BlockPos pos = new BlockPos(bx, by, bz);
            BlockState st = level.getBlockState(pos);

            if (st.isAir() || !st.getFluidState().isEmpty()) {
                continue;
            }
            if (st.hasBlockEntity() && t.ef < 4) {
                continue;
            }
            if (st.getDestroySpeed(level, pos) < 0F) {
                continue;
            }
            if (st.getBlock().getExplosionResistance() > maxRes) {
                continue;
            }

            if (rnd.nextFloat() < 0.35F && debris < Config.MAX_DEBRIS.get() && !st.hasBlockEntity()) {
                FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, st);
                fb.addTag(DEBRIS_TAG);
                double tx = (bz - t.z);
                double tz = -(bx - t.x);
                double len = Math.max(1.0, Math.sqrt(tx * tx + tz * tz));
                double sp = 0.25 + 0.1 * t.ef;
                fb.setDeltaMovement(tx / len * sp, 0.35 + rnd.nextDouble() * 0.3, tz / len * sp);
                fb.hurtMarked = true;
                debris++;
            } else {
                level.removeBlock(pos, false);
            }
            level.sendParticles(ParticleTypes.POOF, bx + 0.5, by + 0.5, bz + 0.5, 3, 0.3, 0.3, 0.3, 0.02);
        }
    }

    // =====================================================================
    //  Utilities
    // =====================================================================
    private static boolean nearAnyPlayer(List<ServerPlayer> players, double x, double z, double range) {
        double r2 = range * range;
        for (ServerPlayer p : players) {
            double dx = p.getX() - x;
            double dz = p.getZ() - z;
            if (dx * dx + dz * dz <= r2) {
                return true;
            }
        }
        return false;
    }

    /** 0 = no danger, 1 = severe storm nearby, 2 = tornado close. */
    public static int warningLevel(ServerLevel level, double x, double z) {
        StormWorld w = get(level);
        int lvl = 0;
        for (Tornado t : w.tornadoes) {
            if (t.strength < 0.15F) {
                continue;
            }
            double d = Math.hypot(t.x - x, t.z - z);
            if (d < 1500.0) {
                lvl = 2;
            } else if (d < 3000.0) {
                lvl = Math.max(lvl, 1);
            }
        }
        for (Supercell s : w.supercells) {
            if (s.maxEf >= 0 && s.strength > 0.3F && Math.hypot(s.x - x, s.z - z) < s.radius * 3.0) {
                lvl = Math.max(lvl, 1);
            }
        }
        return lvl;
    }

    /** Report lines (used by the weather radio and /storm info). */
    public static List<String> report(ServerLevel level, double px, double pz) {
        StormWorld w = get(level);
        List<String> out = new ArrayList<>();
        for (Tornado t : w.tornadoes) {
            out.add("§c[!] EF" + t.ef + " tornado (" + (int) (t.radius * 2) + " blocks wide) §7" + where(t.x - px, t.z - pz, t.vx, t.vz));
        }
        for (Supercell s : w.supercells) {
            String pot = s.maxEf >= 0 ? " (tornado risk)" : "";
            out.add("§6Supercell" + pot + " §7" + where(s.x - px, s.z - pz, s.vx, s.vz));
        }
        if (out.isEmpty()) {
            out.add("§aNo severe storms detected.");
        }
        if (w.rainIntensity >= 0.9F) {
            out.add("§9Heavy rain in progress.");
        } else if (w.rainIntensity >= 0.6F) {
            out.add("§9Moderate rain in progress.");
        } else if (level.isRaining()) {
            out.add("§9Rain.");
        }
        return out;
    }

    private static String where(double dx, double dz, double vx, double vz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        double speedKmh = Math.hypot(vx, vz) * 20.0 * 3.6;
        StringBuilder sb = new StringBuilder();
        sb.append(Compass.distance(d)).append(" ").append(Compass.direction(dx, dz));
        if (speedKmh > 1.0) {
            sb.append(", moving ").append(Compass.direction(vx, vz)).append(" at ").append((int) speedKmh).append(" km/h");
            double closing = -(vx * dx + vz * dz) / Math.max(1.0, d);
            if (closing > 0.01) {
                int secs = (int) (d / (closing * 20.0));
                sb.append(", arrives in ~").append(Math.max(1, secs / 60)).append(" min");
            }
        }
        return sb.toString();
    }

    // =====================================================================
    //  Command helpers
    // =====================================================================
    public static void setRain(ServerLevel level, float intensity, boolean thunder) {
        StormWorld w = get(level);
        w.rainIntensity = intensity;
        if (intensity <= 0F) {
            level.setWeatherParameters(12000, 0, false, false);
        } else {
            level.setWeatherParameters(0, 12000, true, thunder);
        }
    }

    public static void clearAll(ServerLevel level) {
        StormWorld w = get(level);
        w.supercells.clear();
        w.tornadoes.clear();
        w.rainIntensity = 0F;
    }

    // =====================================================================
    //  Syncing with the clients
    // =====================================================================
    private static void sync(ServerLevel level, StormWorld w, List<ServerPlayer> players, long time) {
        boolean any = !w.supercells.isEmpty() || !w.tornadoes.isEmpty() || w.rainIntensity > 0F;
        boolean send = any ? time % 10L == 0L : (!w.sentEmpty || time % 200L == 0L);
        if (!send) {
            return;
        }
        StormSyncPayload payload = new StormSyncPayload(w.rainIntensity,
                new ArrayList<>(w.supercells), new ArrayList<>(w.tornadoes));
        for (ServerPlayer p : players) {
            PacketDistributor.sendToPlayer(p, payload);
        }
        w.sentEmpty = !any;
    }
}
