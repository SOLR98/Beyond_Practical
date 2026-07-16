package com.solr98.beyondpractical.common.block.entity;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.util.BDMath;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FilteredFluidHandler implements IFluidHandler
{
    private final UnifiedStorage storage;
    private final List<IStackKey<?>> whitelist;

    public FilteredFluidHandler(UnifiedStorage storage, List<IStackKey<?>> whitelist)
    {
        this.storage = storage;
        this.whitelist = whitelist;
    }

    @Override
    public int getTanks()
    {
        return whitelist.size();
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank)
    {
        if (tank < 0 || tank >= whitelist.size())
            return FluidStack.EMPTY;
        IStackKey<?> key = whitelist.get(tank);
        Object outStack = storage.getOutStackByKey(key);
        if (outStack instanceof FluidStack fluidStack)
        {
            if (!fluidStack.isEmpty())
                fluidStack.setAmount(BDMath.clampLongToInt(storage.getStackByKey(key).amount()));
            return fluidStack;
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank)
    {
        return BDMath.clampLongToInt(storage.getSlotCapacity(0));
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack)
    {
        return true;
    }

    @Override
    public int fill(FluidStack fluidStack, @NotNull FluidAction fluidAction)
    {
        if (fluidStack.isEmpty())
            return 0;
        FluidStackKey key = new FluidStackKey(fluidStack);
        if (whitelist.stream().noneMatch(w -> w.isSame(key)))
            return 0;
        int allAmount = fluidStack.getAmount();
        int remaining = (int) storage.insert(key, fluidStack.getAmount(), fluidAction.simulate()).amount();
        return allAmount - remaining;
    }

    @Override
    public @NotNull FluidStack drain(@NotNull FluidStack fluidStack, FluidAction fluidAction)
    {
        FluidStackKey key = new FluidStackKey(fluidStack);
        if (whitelist.stream().noneMatch(w -> w.isSame(key)))
            return FluidStack.EMPTY;
        if (storage.extract(key, fluidStack.getAmount(), fluidAction.simulate(), false).toStack() instanceof FluidStack result)
            return result;
        return FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction fluidAction)
    {
        if (whitelist.isEmpty())
            return FluidStack.EMPTY;
        IStackKey<?> firstKey = whitelist.get(0);
        if (storage.extract(firstKey, maxDrain, fluidAction.simulate(), false).toStack() instanceof FluidStack result)
            return result;
        return FluidStack.EMPTY;
    }
}
