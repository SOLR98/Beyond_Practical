package com.solr98.beyondpractical.common.block;

import com.solr98.beyondpractical.common.block.entity.NetCrafterBlockEntity;
import com.wintercogs.beyonddimensions.common.block.BaseMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class NetCrafterBlock extends BaseMachineBlock
{
    public NetCrafterBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new NetCrafterBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!level.isClientSide() && !player.isShiftKeyDown())
        {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof NetCrafterBlockEntity crafter)
                NetworkHooks.openScreen((ServerPlayer) player, crafter, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston)
    {
        if (!state.is(newState.getBlock()))
        {
            if (level.getBlockEntity(pos) instanceof NetCrafterBlockEntity be)
            {
                be.dropContent();
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
