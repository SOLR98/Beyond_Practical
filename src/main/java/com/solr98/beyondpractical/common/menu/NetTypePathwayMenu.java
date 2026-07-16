package com.solr98.beyondpractical.common.menu;

import com.solr98.beyondpractical.common.block.entity.NetTypePathwayBlockEntity;
import com.solr98.beyondpractical.common.init.BPMenus;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

public class NetTypePathwayMenu extends BDBaseMenu
{
    public final NetTypePathwayBlockEntity blockEntity;

    private static final int slotStartY = 1 + CommonTextures.TOP_BASE_COMMON_HEIGHT;
    private static final int invSlotStartY = 6 + slotStartY
            + CommonTextures.FILTER_SLOTS_HEIGHT
            + CommonTextures.COMMON_CONNECTION_HEIGHT;

    public NetTypePathwayMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, (NetTypePathwayBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
    }

    public NetTypePathwayMenu(int id, Inventory inventory, NetTypePathwayBlockEntity blockEntity)
    {
        super(BPMenus.NET_TYPE_PATHWAY_MENU.get(), id, inventory);
        this.blockEntity = blockEntity;

        if (blockEntity != null)
        {
            StackHandler filterSlots = blockEntity.getFilterSlots();
            for (int i = 0; i < filterSlots.getSlots(); i++)
                addSlot(new FlagStackTypedSlot(this, filterSlots, i, 8 + i * 18, slotStartY));
        }

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, invSlotStartY + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, invSlotStartY + 58));

        inventoryStartIndex = slots.size();
        inventoryEndIndex = slots.size();
    }

    @Override
    public boolean stillValid(@NotNull Player player)
    {
        return blockEntity != null && !blockEntity.isRemoved()
                && player.distanceToSqr(blockEntity.getBlockPos().getX() + 0.5D, blockEntity.getBlockPos().getY() + 0.5D, blockEntity.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }
}
