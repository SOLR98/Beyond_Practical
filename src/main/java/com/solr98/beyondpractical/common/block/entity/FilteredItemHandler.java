package com.solr98.beyondpractical.common.block.entity;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.util.BDMath;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FilteredItemHandler implements IItemHandler
{
    private final UnifiedStorage storage;
    private final List<IStackKey<?>> whitelist;

    public FilteredItemHandler(UnifiedStorage storage, List<IStackKey<?>> whitelist)
    {
        this.storage = storage;
        this.whitelist = whitelist;
    }

    @Override
    public int getSlots()
    {
        return whitelist.size();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot)
    {
        if (slot < 0 || slot >= whitelist.size())
            return ItemStack.EMPTY;
        IStackKey<?> key = whitelist.get(slot);
        Object outStack = storage.getOutStackByKey(key);
        if (outStack instanceof ItemStack itemStack)
        {
            if (!itemStack.isEmpty())
                itemStack.setCount(BDMath.clampLongToInt(storage.getStackByKey(key).amount()));
            return itemStack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean sim)
    {
        if (stack.isEmpty())
            return ItemStack.EMPTY;
        ItemStackKey key = new ItemStackKey(stack);
        if (whitelist.stream().noneMatch(w -> w.isSame(key)))
            return stack;
        KeyAmount remaining = storage.insert(key, stack.getCount(), sim);
        if (!remaining.isEmpty() && remaining.toStack() instanceof ItemStack is)
            return is;
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int count, boolean sim)
    {
        if (slot < 0 || slot >= whitelist.size())
            return ItemStack.EMPTY;
        IStackKey<?> key = whitelist.get(slot);
        KeyAmount extracted = storage.extract(key, count, sim, false);
        if (extracted.toStack() instanceof ItemStack is)
            return is;
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot)
    {
        return BDMath.clampLongToInt(storage.getSlotCapacity(0));
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack)
    {
        return true;
    }
}
