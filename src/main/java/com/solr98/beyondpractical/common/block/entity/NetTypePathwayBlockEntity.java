package com.solr98.beyondpractical.common.block.entity;

import com.solr98.beyondpractical.common.init.BPBlockEntities;
import com.solr98.beyondpractical.common.menu.NetTypePathwayMenu;
import com.wintercogs.beyonddimensions.api.capability.helper.CapabilityHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.util.CapCtx;
import com.wintercogs.beyonddimensions.api.util.USHandler;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import com.wintercogs.beyonddimensions.util.SidedCapId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetTypePathwayBlockEntity extends NetedBlockEntity implements MenuProvider
{
    private final Map<SidedCapId, LazyOptional<?>> caps = new HashMap<>();

    private static final int FILTER_SLOTS = 9;
    private final ItemStackHandler filterSlots = new ItemStackHandler(FILTER_SLOTS)
    {
        @Override
        protected void onContentsChanged(int slot)
        {
            if (level != null && !level.isClientSide())
            {
                clearCapCache();
                setChanged();
            }
        }
    };

    public NetTypePathwayBlockEntity(BlockPos pos, BlockState state)
    {
        super(BPBlockEntities.NET_TYPE_PATHWAY.get(), pos, state);
        addNetChangeTask(this::clearCapCache);
    }

    // ========== 物品级白名单 ==========

    public ItemStackHandler getFilterSlots()
    {
        return filterSlots;
    }

    private List<IStackKey<?>> buildWhitelist()
    {
        List<IStackKey<?>> list = new ArrayList<>();
        for (int i = 0; i < filterSlots.getSlots(); i++)
        {
            ItemStack stack = filterSlots.getStackInSlot(i);
            if (!stack.isEmpty())
                list.add(new ItemStackKey(stack));
        }
        return list;
    }

    // ========== 能力代理 ==========

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side)
    {
        DimensionsNet net = getNet();
        if (net == null) return super.getCapability(cap, side);

        for (Map.Entry<ResourceLocation, Capability<?>> entry : CapabilityHelper.BlockCapabilityMap.entrySet())
        {
            if (entry.getValue() != cap) continue;

            SidedCapId capId = new SidedCapId(cap, null);
            LazyOptional<?> cached = caps.get(capId);
            if (cached != null && cached.isPresent())
                return cached.cast();

            Object result;
            List<IStackKey<?>> whitelist = buildWhitelist();

            if (!whitelist.isEmpty() && entry.getValue() == ForgeCapabilities.ITEM_HANDLER)
                result = new FilteredItemHandler(net.getUnifiedStorage(), whitelist);
            else if (!whitelist.isEmpty() && entry.getValue() == ForgeCapabilities.FLUID_HANDLER)
                result = new FilteredFluidHandler(net.getUnifiedStorage(), whitelist);
            else
            {
                USHandler handler = CapabilityHelper.USHandlerMap.get(entry.getKey());
                if (handler == null) return LazyOptional.empty();
                result = handler.apply(net.getUnifiedStorage(),
                        handler.isContextual() ? new CapCtx(level, getBlockPos(), this) : null);
            }

            if (result == null) return LazyOptional.empty();

            LazyOptional<?> optional = LazyOptional.of(() -> result);
            caps.put(capId, optional);
            optional.addListener(value -> caps.remove(capId, value));
            return optional.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        clearCapCache();
    }

    private void clearCapCache()
    {
        for (LazyOptional<?> optional : new ArrayList<>(caps.values()))
            optional.invalidate();
        caps.clear();
    }

    // ========== NBT ==========

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        if (tag.contains("filterSlots"))
            filterSlots.deserializeNBT(tag.getCompound("filterSlots"));
        clearCapCache();
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.put("filterSlots", filterSlots.serializeNBT());
    }

    // ========== MenuProvider ==========

    @Override
    public Component getDisplayName()
    {
        return Component.translatable("menu.beyond_practical.net_type_pathway");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player player)
    {
        return new NetTypePathwayMenu(containerId, inventory, this);
    }
}
