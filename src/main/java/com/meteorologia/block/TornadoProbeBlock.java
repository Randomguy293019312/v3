package com.meteorologia.block;

import com.meteorologia.weather.StormManager;
import com.meteorologia.weather.WindModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tornado probe ("turtle"): a heavy instrument pod that records the PEAK wind it has experienced.
 * Place it in a tornado's path and come back for the data. Right click reads it; sneak + right click resets it.
 * Each step of the block state is 30 km/h (0 = nothing recorded). Emits a comparator signal.
 */
public class TornadoProbeBlock extends Block {
    public static final IntegerProperty PEAK = BlockStateProperties.LEVEL;
    public static final int KMH_PER_STEP = 30;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 9, 15);

    public TornadoProbeBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(PEAK, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PEAK);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(PEAK);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return state.getValue(PEAK);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 4);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        double[] wind = WindModel.wind(StormManager.get(level), level.getGameTime(),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        int step = (int) Math.min(15, Math.ceil(WindModel.speed(wind) / KMH_PER_STEP));
        if (step > state.getValue(PEAK)) {
            level.setBlock(pos, state.setValue(PEAK, step), 3);
        }
        level.scheduleTick(pos, this, 4);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        int peak = state.getValue(PEAK);
        if (player.isShiftKeyDown()) {
            level.setBlock(pos, state.setValue(PEAK, 0), 3);
            player.displayClientMessage(Component.literal("§eProbe reset."), true);
        } else if (peak == 0) {
            player.displayClientMessage(Component.literal("§7Probe: nothing recorded yet."), true);
        } else {
            int lo = (peak - 1) * KMH_PER_STEP;
            String text = peak >= 15 ? "§cProbe: peak wind above " + lo + " km/h!"
                    : "§bProbe: peak wind between §f" + lo + " and " + (peak * KMH_PER_STEP) + " km/h";
            player.displayClientMessage(Component.literal(text), false);
        }
        return InteractionResult.sidedSuccess(false);
    }
}
