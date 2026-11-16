package com.meteorologia.item;

import com.meteorologia.ModRegistry;
import com.meteorologia.entity.ChaserVehicle;
import com.meteorologia.vehicle.VehicleType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Places a chase vehicle on the ground, facing the way the player is looking. */
public class VehicleItem extends Item {
    private final VehicleType type;

    public VehicleItem(VehicleType type, Properties props) {
        super(props);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel sl)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        ChaserVehicle vehicle = ModRegistry.vehicleType(type).get().create(sl);
        if (vehicle == null) {
            return InteractionResult.FAIL;
        }
        vehicle.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, ctx.getRotation(), 0F);
        vehicle.setYaw(ctx.getRotation());
        sl.addFreshEntity(vehicle);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
