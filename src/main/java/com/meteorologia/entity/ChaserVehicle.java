package com.meteorologia.entity;

import com.meteorologia.Config;
import com.meteorologia.ModRegistry;
import com.meteorologia.vehicle.VehiclePhysics;
import com.meteorologia.vehicle.VehicleType;
import com.meteorologia.weather.Compass;
import com.meteorologia.weather.StormManager;
import com.meteorologia.weather.StormWorld;
import com.meteorologia.weather.Tornado;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A drivable storm-chasing vehicle (Dominator, TIV, Chaser SUV).
 * Controls: W / S = throttle / brake and reverse, A / D = steer, Shift = get off.
 * Keys: R = anchors (TIV and Dominator), L = lights, H = horn.
 * The server is authoritative; the client only animates wheels and plays effects.
 */
public class ChaserVehicle extends Entity {
    private static final EntityDataAccessor<Integer> DATA_FUEL =
            SynchedEntityData.defineId(ChaserVehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_HEALTH =
            SynchedEntityData.defineId(ChaserVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_ANCHORED =
            SynchedEntityData.defineId(ChaserVehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LIGHTS =
            SynchedEntityData.defineId(ChaserVehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BRAKING =
            SynchedEntityData.defineId(ChaserVehicle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_YAW =
            SynchedEntityData.defineId(ChaserVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STEER =
            SynchedEntityData.defineId(ChaserVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED =
            SynchedEntityData.defineId(ChaserVehicle.class, EntityDataSerializers.FLOAT);

    public static final int ACTION_ANCHOR = 0;
    public static final int ACTION_LIGHTS = 1;
    public static final int ACTION_HORN = 2;
    public static final int FUEL_PER_CANISTER = 1000;

    private final VehicleType vtype;
    private final VehiclePhysics.State phys = new VehiclePhysics.State();

    // ---- client animation state
    private float yawO;
    private float yawV;
    private float steerO;
    private float steerV;
    private float wheelRot;
    private float wheelRotO;
    private float anchorAnim;
    private float anchorAnimO;

    public ChaserVehicle(EntityType<? extends ChaserVehicle> type, Level level, VehicleType vtype) {
        super(type, level);
        this.vtype = vtype;
        entityData.set(DATA_FUEL, (int) (vtype.fuelTank * 0.4F));
        entityData.set(DATA_HEALTH, vtype.maxHealth);
    }

    public VehicleType getVehicleType() {
        return vtype;
    }

    // ---------------------------------------------------------------- data ----
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_HEALTH, 100F);
        builder.define(DATA_ANCHORED, false);
        builder.define(DATA_LIGHTS, false);
        builder.define(DATA_BRAKING, false);
        builder.define(DATA_YAW, 0F);
        builder.define(DATA_STEER, 0F);
        builder.define(DATA_SPEED, 0F);
    }

    public int getFuel() {
        return entityData.get(DATA_FUEL);
    }

    public void setFuel(int fuel) {
        entityData.set(DATA_FUEL, Math.max(0, Math.min(vtype.fuelTank, fuel)));
    }

    public float getHealth() {
        return entityData.get(DATA_HEALTH);
    }

    public boolean isAnchored() {
        return entityData.get(DATA_ANCHORED);
    }

    public boolean lightsOn() {
        return entityData.get(DATA_LIGHTS);
    }

    public boolean isBraking() {
        return entityData.get(DATA_BRAKING);
    }

    public boolean isWrecked() {
        return getHealth() <= 0F;
    }

    /** Sets the heading (used when the item places the vehicle). */
    public void setYaw(float yaw) {
        phys.yaw = yaw;
        entityData.set(DATA_YAW, yaw);
        setYRot(yaw);
        yawO = yaw;
        yawV = yaw;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setFuel(tag.getInt("Fuel"));
        entityData.set(DATA_HEALTH, tag.contains("Health") ? tag.getFloat("Health") : vtype.maxHealth);
        entityData.set(DATA_ANCHORED, tag.getBoolean("Anchored"));
        setYaw(tag.getFloat("VehicleYaw"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Fuel", getFuel());
        tag.putFloat("Health", getHealth());
        tag.putBoolean("Anchored", isAnchored());
        tag.putFloat("VehicleYaw", phys.yaw);
    }

    // ---------------------------------------------------- client render API ----
    public float getRenderYaw(float pt) {
        return Mth.rotLerp(pt, yawO, yawV);
    }

    public float getRenderSteer(float pt) {
        return Mth.lerp(pt, steerO, steerV);
    }

    public float getWheelRot(float pt) {
        return Mth.lerp(pt, wheelRotO, wheelRot);
    }

    /** Signed forward speed in blocks/tick, as synced to the client (used for the body lean). */
    public float getSyncedSpeed() {
        return entityData.get(DATA_SPEED);
    }

    public float getAnchorAnim(float pt) {
        return Mth.lerp(pt, anchorAnimO, anchorAnim);
    }

    /** Current heading as seen by this side (client animation yaw or server physics yaw). */
    public float currentYaw() {
        return level().isClientSide ? yawV : phys.yaw;
    }

    // -------------------------------------------------------------- misc API ----
    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2.0, 1.0, 2.0);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < vtype.seats && !isWrecked();
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!hasPassenger(passenger)) {
            return;
        }
        int idx = Math.max(0, Math.min(vtype.seatOffsets.length - 1, getPassengers().indexOf(passenger)));
        float[] so = vtype.seatOffsets[idx];
        double yaw = Math.toRadians(currentYaw());
        double dx = so[0] * Math.cos(yaw) - so[2] * Math.sin(yaw);
        double dz = so[0] * Math.sin(yaw) + so[2] * Math.cos(yaw);
        callback.accept(passenger, getX() + dx, getY() + so[1], getZ() + dz);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModRegistry.vehicleItem(vtype));
    }

    /** How strongly a tornado pushes this vehicle (1 = like any entity). */
    public float windPushFactor(int ef) {
        float base = vtype == VehicleType.TIV ? 0.45F : vtype == VehicleType.DOMINATOR ? 0.65F : 0.95F;
        if (isAnchored()) {
            return ef <= 3 ? 0.04F : ef == 4 ? 0.30F : 0.60F;
        }
        return base;
    }

    /** Damage from crashes, tornado debris and so on, reduced by the armour. */
    public void hurtVehicle(float amount) {
        if (level().isClientSide || amount <= 0F || !Config.VEHICLE_DAMAGE.get()) {
            return;
        }
        float before = getHealth();
        float after = Math.max(0F, before - amount * (1F - vtype.armor * 0.6F));
        entityData.set(DATA_HEALTH, after);
        if (before > 0F && after <= 0F) {
            ejectPassengers();
            entityData.set(DATA_ANCHORED, false);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 25, 0.8, 0.5, 0.8, 0.05);
                sl.playSound(null, blockPosition(), SoundEvents.ANVIL_DESTROY, SoundSource.NEUTRAL, 1.0F, 0.9F);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof Player p && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            discard();   // creative + sneak = remove
            return true;
        }
        hurtVehicle(amount * 0.25F);
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModRegistry.FUEL_CANISTER.get())) {
            if (!level().isClientSide && getFuel() < vtype.fuelTank) {
                setFuel(getFuel() + FUEL_PER_CANISTER);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level().playSound(null, blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1.0F, 1.2F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (held.is(Items.IRON_INGOT) && getHealth() < vtype.maxHealth) {
            if (!level().isClientSide) {
                entityData.set(DATA_HEALTH, Math.min(vtype.maxHealth, getHealth() + vtype.maxHealth * 0.15F));
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level().playSound(null, blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.4F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isShiftKeyDown()) {
            if (!level().isClientSide && getPassengers().isEmpty() && held.isEmpty()) {
                spawnAtLocation(ModRegistry.vehicleItem(vtype));
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isWrecked()) {
            return InteractionResult.PASS;
        }
        if (getPassengers().size() < vtype.seats) {
            if (!level().isClientSide) {
                return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Called when the driver presses a vehicle key (R / L / H). */
    public void handleAction(int action, Player driver) {
        switch (action) {
            case ACTION_ANCHOR:
                if (!vtype.canAnchor) {
                    driver.displayClientMessage(Component.literal("§cThis vehicle has no ground anchors."), true);
                } else if (!isAnchored() && Math.abs(phys.forwardSpeed) > 0.15F) {
                    driver.displayClientMessage(Component.literal("§cStop the vehicle to deploy the anchors."), true);
                } else {
                    entityData.set(DATA_ANCHORED, !isAnchored());
                    level().playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL, 1.0F, 0.7F);
                    driver.displayClientMessage(Component.literal(isAnchored() ? "§aAnchors deployed." : "§eAnchors retracted."), true);
                }
                break;
            case ACTION_LIGHTS:
                entityData.set(DATA_LIGHTS, !lightsOn());
                level().playSound(null, blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.NEUTRAL, 0.6F, 1.2F);
                break;
            case ACTION_HORN:
                level().playSound(null, blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 3.0F, 0.55F);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ tick ----
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    private void clientTick() {
        yawO = yawV;
        yawV = entityData.get(DATA_YAW);
        steerO = steerV;
        steerV = entityData.get(DATA_STEER);
        wheelRotO = wheelRot;
        float spd = entityData.get(DATA_SPEED);
        wheelRot += spd / vtype.wheelRadius;
        anchorAnimO = anchorAnim;
        anchorAnim = Mth.clamp(anchorAnim + (isAnchored() ? 0.07F : -0.07F), 0F, 1F);

        boolean driven = !getPassengers().isEmpty() && !isWrecked();
        double yawRad = Math.toRadians(yawV);
        double bx = getX() + Math.sin(yawRad) * (vtype.wheelbase * 0.6);   // rear of the vehicle
        double bz = getZ() - Math.cos(yawRad) * (vtype.wheelbase * 0.6);
        if (driven && getFuel() > 0 && tickCount % 3 == 0) {
            level().addParticle(ParticleTypes.SMOKE, bx, getY() + 0.35, bz, 0.0, 0.02, 0.0);
        }
        if (driven && Math.abs(spd) > 0.02F && tickCount % 3 == 0) {
            level().playLocalSound(getX(), getY(), getZ(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL,
                    0.25F + Math.min(0.5F, Math.abs(spd) * 0.4F), 0.55F + Math.abs(spd) * 0.5F, false);
        }
        if (getHealth() < vtype.maxHealth * 0.35F && tickCount % 4 == 0) {
            level().addParticle(isWrecked() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                    getX() + Math.sin(yawRad) * -1.2, getY() + 1.2, getZ() + Math.cos(yawRad) * 1.2, 0.0, 0.06, 0.0);
        }
    }

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();

        // ---- driver input (same fields the balloon reads)
        float throttle = 0F;
        float steer = 0F;
        Entity first = getFirstPassenger();
        LivingEntity driver = first instanceof LivingEntity le ? le : null;
        if (driver != null) {
            throttle = Mth.clamp(driver.zza, -1F, 1F);
            steer = Mth.clamp(driver.xxa, -1F, 1F);
        }
        boolean wrecked = isWrecked();
        int fuel = getFuel();
        boolean powered = fuel > 0 && !wrecked && driver != null;
        if (wrecked) {
            throttle = 0F;
            steer = 0F;
        }

        // ---- grip: wet, icy or slippery surfaces
        float gripMul = 1F - 0.4F * sl.getRainLevel(1.0F);
        BlockState below = sl.getBlockState(blockPosition().below());
        float friction = below.getBlock().getFriction();
        if (friction > 0.9F) {
            gripMul *= 0.25F;       // ice
        } else if (friction > 0.7F) {
            gripMul *= 0.6F;
        }

        // ---- horizontal motion
        Vec3 dm = getDeltaMovement();
        phys.vx = dm.x;
        phys.vz = dm.z;
        if (isAnchored() || wrecked) {
            phys.vx *= 0.5;
            phys.vz *= 0.5;
            if (Math.abs(phys.vx) < 0.01 && Math.abs(phys.vz) < 0.01) {
                phys.vx = 0;
                phys.vz = 0;
            }
            phys.forwardSpeed = 0F;
        } else {
            VehiclePhysics.step(phys, vtype, throttle, steer, gripMul, powered);
        }
        double speedBefore = Math.sqrt(phys.vx * phys.vx + phys.vz * phys.vz);

        // ---- vertical motion
        double vy = dm.y;
        if (isInWater()) {
            vy = vy * 0.7 + 0.03;
            phys.vx *= 0.92;
            phys.vz *= 0.92;
        } else if (!onGround()) {
            vy -= 0.08;
        } else {
            vy = -0.04;
        }
        vy *= 0.98;
        setDeltaMovement(phys.vx, vy, phys.vz);

        // ---- climb one-block steps
        if (onGround() && speedBefore > 0.05 && speedBefore < 0.9) {
            AABB bb = getBoundingBox();
            if (!sl.noCollision(this, bb.move(phys.vx, 0.0, phys.vz)) && sl.noCollision(this, bb.move(phys.vx, 1.02, phys.vz))) {
                setPos(getX(), getY() + 1.0, getZ());
            }
        }
        move(MoverType.SELF, getDeltaMovement());

        Vec3 after = getDeltaMovement();
        double speedAfter = Math.sqrt(after.x * after.x + after.z * after.z);
        phys.vx = after.x;
        phys.vz = after.z;

        // ---- crashes
        double impact = speedBefore - speedAfter;
        if (horizontalCollision && impact > 0.22) {
            hurtVehicle((float) ((impact - 0.18) * 220.0));
            if (driver != null && impact > 0.6) {
                float dmg = (float) ((impact - 0.55) * 14.0) * (1F - vtype.armor);
                for (Entity p : getPassengers()) {
                    if (p instanceof LivingEntity le) {
                        le.hurt(sl.damageSources().generic(), dmg);
                    }
                }
            }
            sl.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.8F, 0.6F);
        }

        // ---- hit entities in the way
        if (speedAfter > 0.35 && tickCount % 2 == 0) {
            List<LivingEntity> hit = sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.15),
                    e -> e.isAlive() && !hasPassenger(e) && !e.isPassenger());
            for (LivingEntity e : hit) {
                e.hurt(sl.damageSources().generic(), (float) (speedAfter * 12.0));
                e.push(after.x * 0.8, 0.3, after.z * 0.8);
                e.hurtMarked = true;
            }
        }

        // ---- fuel, wheel state and sync
        if (driver != null && !wrecked) {
            setFuel(fuel - (int) Math.ceil(0.12 + 0.45 * Math.max(0F, throttle)));
        }
        entityData.set(DATA_BRAKING, throttle < -0.1F && phys.forwardSpeed > 0.03F);
        entityData.set(DATA_YAW, phys.yaw);
        entityData.set(DATA_STEER, phys.steer);
        entityData.set(DATA_SPEED, phys.forwardSpeed);
        setYRot(phys.yaw);

        if (driver instanceof Player p && tickCount % 4 == 0) {
            p.displayClientMessage(Component.literal(hudLine(sl)), true);
        }
    }

    private String hudLine(ServerLevel sl) {
        int kmh = (int) Math.round(Math.abs(phys.forwardSpeed) * 72.0);
        int fuelPct = (int) Math.round(100.0 * getFuel() / vtype.fuelTank);
        int hpPct = (int) Math.round(100.0 * getHealth() / vtype.maxHealth);
        StringBuilder sb = new StringBuilder();
        sb.append("§f").append(kmh).append(" km/h §7| ").append(fuelPct < 15 ? "§c" : "§e").append("Fuel ").append(fuelPct)
                .append("% §7| ").append(hpPct < 30 ? "§c" : "§a").append("Hull ").append(hpPct).append("%");
        if (vtype.canAnchor) {
            sb.append(" §7| ").append(isAnchored() ? "§bANCHORED" : "§8anchors up");
        }
        StormWorld w = StormManager.get(sl);
        Tornado nearest = null;
        double best = Double.MAX_VALUE;
        for (Tornado t : w.tornadoes) {
            double d = Math.hypot(t.x - getX(), t.z - getZ());
            if (t.strength > 0.1F && d < best) {
                best = d;
                nearest = t;
            }
        }
        if (nearest != null) {
            sb.append(" §7| §cEF").append(nearest.ef).append(" ").append(Compass.distance(best)).append(" ")
                    .append(Compass.direction(nearest.x - getX(), nearest.z - getZ()));
        }
        return sb.toString();
    }
}
