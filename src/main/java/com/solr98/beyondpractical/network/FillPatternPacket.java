package com.solr98.beyondpractical.network;

import com.solr98.beyondpractical.common.block.entity.NetCrafterBlockEntity;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record FillPatternPacket(BlockPos pos, ItemStack[] items)
{
    public static void encode(FillPatternPacket p, FriendlyByteBuf buf)
    {
        buf.writeBlockPos(p.pos());
        for (int i = 0; i < 9; i++)
            buf.writeItem(p.items()[i]);
    }

    public static FillPatternPacket decode(FriendlyByteBuf buf)
    {
        BlockPos pos = buf.readBlockPos();
        ItemStack[] items = new ItemStack[9];
        for (int i = 0; i < 9; i++)
            items[i] = buf.readItem();
        return new FillPatternPacket(pos, items);
    }

    public static void handle(FillPatternPacket p, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Level level = player.level();
            if (!level.isLoaded(p.pos())) return;
            if (level.getBlockEntity(p.pos()) instanceof NetCrafterBlockEntity be)
            {
                var pattern = be.getPatternSlots();
                for (int i = 0; i < 9; i++)
                {
                    var stack = p.items()[i];
                    if (stack.isEmpty())
                        pattern.setStackDirectly(i, com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey.INSTANCE, 0);
                    else
                        pattern.setStackDirectly(i, new ItemStackKey(stack), 1);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
