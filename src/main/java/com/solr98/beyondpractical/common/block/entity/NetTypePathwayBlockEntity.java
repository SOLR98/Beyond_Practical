package com.solr98.beyondpractical.common.block.entity;

import com.solr98.beyondpractical.common.init.BPBlockEntities;
import com.solr98.beyondpractical.common.menu.NetTypePathwayMenu;
import com.wintercogs.beyonddimensions.api.capability.helper.CapabilityHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.util.CapCtx;
import com.wintercogs.beyonddimensions.api.util.CommonHandler;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class NetTypePathwayBlockEntity extends NetedBlockEntity implements MenuProvider
{
    private final Map<SidedCapId, LazyOptional<?>> caps = new HashMap<>();

    private static final int FILTER_SLOTS = 9;
    private final StackHandler filterSlots = new StackHandler(FILTER_SLOTS)
    {
        @Override
        public void onChange()
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

    public StackHandler getFilterSlots()
    {
        return filterSlots;
    }

    private List<IStackKey<?>> getWhitelistForType(ResourceLocation typeId)
    {
        List<IStackKey<?>> list = new ArrayList<>();
        for (KeyAmount ka : filterSlots.getStorage())
        {
            if (!ka.isEmpty() && ka.key().getTypeId().equals(typeId))
                list.add(ka.key());
        }
        return list;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side)
    {
        DimensionsNet net = getNet();
        if (net == null) return super.getCapability(cap, side);

        for (Map.Entry<ResourceLocation, Capability<?>> entry : CapabilityHelper.BlockCapabilityMap.entrySet())
        {
            if (entry.getValue() != cap) continue;

            List<IStackKey<?>> whitelist = getWhitelistForType(entry.getKey());
            if (whitelist.isEmpty()) continue;

            SidedCapId capId = new SidedCapId(cap, null);
            LazyOptional<?> cached = caps.get(capId);
            if (cached != null && cached.isPresent())
                return cached.cast();

            Object result;
            CommonHandler ch = CapabilityHelper.CommonHandlerMap.get(entry.getKey());
            if (ch != null)
            {
                UnifiedStorage us = net.getUnifiedStorage();
                StackHandler temp = new StackHandler(whitelist.size())
                {
                    @Override
                    public @NotNull KeyAmount insert(int slot, IStackKey<?> key, long amount, boolean simulate)
                    {
                        KeyAmount r = us.insert(key, amount, simulate);
                        if (!simulate)
                            super.setStackDirectly(slot, key, us.getStackByKey(key).amount());
                        return r;
                    }

                    @Override
                    public @NotNull KeyAmount extract(int slot, long count, boolean simulate)
                    {
                        IStackKey<?> k = whitelist.get(slot);
                        KeyAmount r = us.extract(k, count, simulate, false);
                        if (!simulate)
                            super.setStackDirectly(slot, k, us.getStackByKey(k).amount());
                        return r;
                    }
                };
                for (int i = 0; i < whitelist.size(); i++)
                {
                    IStackKey<?> k = whitelist.get(i);
                    temp.setStackDirectly(i, k, us.getStackByKey(k).amount());
                }
                result = ch.apply(temp, ch.isContextual() ? new CapCtx(level, getBlockPos(), this) : null);
            }
            else
            {
                USHandler uh = CapabilityHelper.USHandlerMap.get(entry.getKey());
                if (uh == null) return LazyOptional.empty();
                UnifiedStorage filtered = new FilteredUnifiedStorage(
                        net.getUnifiedStorage(), new HashSet<>(whitelist));
                result = uh.apply(filtered,
                        uh.isContextual() ? new CapCtx(level, getBlockPos(), this) : null);
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
