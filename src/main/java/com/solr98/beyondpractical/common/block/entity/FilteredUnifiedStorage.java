package com.solr98.beyondpractical.common.block.entity;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class FilteredUnifiedStorage extends UnifiedStorage
{
    private final UnifiedStorage delegate;
    private final Set<IStackKey<?>> allowedKeys;

    public FilteredUnifiedStorage(UnifiedStorage delegate, Set<IStackKey<?>> allowedKeys)
    {
        super(null, AbstractUnorderedStackHandler.UiTimestampPolicy.NONE, 0, 0);
        this.delegate = delegate;
        this.allowedKeys = allowedKeys;
    }

    @Override
    public @NotNull KeyAmount getStackByKey(IStackKey<?> key)
    {
        if (key == null || !allowedKeys.contains(key))
            return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        return delegate.getStackByKey(key);
    }

    @Override
    public Object getOutStackByKey(IStackKey<?> key)
    {
        if (key == null || !allowedKeys.contains(key))
            return null;
        return delegate.getOutStackByKey(key);
    }

    @Override
    public @NotNull KeyAmount insert(IStackKey<?> key, long amount, boolean simulate)
    {
        if (key == null || !allowedKeys.contains(key))
            return new KeyAmount(key == null ? EmptyStackKey.INSTANCE : key, amount);
        return delegate.insert(key, amount, simulate);
    }

    @Override
    public @NotNull KeyAmount extract(IStackKey<?> key, long amount, boolean simulate, boolean fuzzy)
    {
        if (key == null) return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        if (fuzzy)
        {
            for (IStackKey<?> k : allowedKeys)
                if (k.isSame(key))
                    return delegate.extract(k, amount, simulate, false);
            return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        }
        if (!allowedKeys.contains(key))
            return new KeyAmount(EmptyStackKey.INSTANCE, 0L);
        return delegate.extract(key, amount, simulate, false);
    }

    @Override
    public long getSlotCapacity(int slot)
    {
        return delegate.getSlotCapacity(slot);
    }

    @Override
    public boolean isFullSlotsSize()
    {
        return delegate.isFullSlotsSize();
    }

    @Override
    public void onChange() { }
}
