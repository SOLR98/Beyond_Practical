package com.solr98.beyondpractical.common.menu.widget;

import com.wintercogs.beyonddimensions.api.capability.helper.CapabilityHelper;
import com.wintercogs.beyonddimensions.api.capability.helper.wrapper.IStackHandlerWrapper;
import com.wintercogs.beyonddimensions.api.capability.helper.wrapper.StackHandlerWrapperHelper;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.init.BDFluids;
import com.wintercogs.beyonddimensions.common.init.BDPackets;
import com.wintercogs.beyonddimensions.common.item.XpExchangeItem;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import com.wintercogs.beyonddimensions.network.packet.s2c.OrderedStackTypedSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Function;

/**
 * 3×3 配方图案槽位。
 * 行为等同 BD 的 FlagStackTypedSlot，但属于本模组自有实现。
 * 支持左键设物品标记、右键扫能力标记、空手清除。
 */
public class PatternSlot extends AbstractStackTypedSlot
{
    private KeyAmount lastStack = new KeyAmount(ItemStackKey.EMPTY, 0);

    public PatternSlot(BDBaseMenu menu, IStackHandler storage, int slotIndex, int x, int y)
    {
        super(menu, storage, slotIndex, x, y);
        setFake(true);
    }

    @Override
    public boolean isOrdered()
    {
        return true;
    }

    @Override
    public void setStackDirectly(IStackKey<?> key, long amount)
    {
        storage.setStackDirectly(theSlot, key, amount);
    }

    @Override
    public KeyAmount safeInsert(IStackKey<?> key, long amount)
    {
        if (key != null)
            setStackDirectly(key, amount);
        return new KeyAmount(EmptyStackKey.INSTANCE, amount);
    }

    @Override
    public KeyAmount safeExtract(IStackKey<?> key, long amount)
    {
        setStackDirectly(ItemStackKey.EMPTY, 0);
        return new KeyAmount(ItemStackKey.EMPTY, 0);
    }

    @Override
    public void click(KeyAmount clickStack, int button, Player player)
    {
        ItemStack carriedItem = menu.getCarried().copy();

        if (clickStack.isEmpty())
        {
            if (!carriedItem.isEmpty())
            {
                if (button == 0)
                {
                    setStackDirectly(new ItemStackKey(carriedItem), 1);
                }
                else if (button == 1)
                {
                    if (carriedItem.getItem() instanceof XpExchangeItem)
                    {
                        setStackDirectly(new FluidStackKey(
                                new FluidStack(BDFluids.XP_FLUID.source().get(), 1)), 1);
                    }
                    else
                    {
                        ItemStack copy = carriedItem.copy();
                        copy.setCount(1);
                        CapabilityHelper.ItemCapabilityMap.forEach((typeId, cap) -> {
                            LazyOptional<?> handler = copy.getCapability(cap);
                            if (handler.isPresent())
                            {
                                Function getter = StackHandlerWrapperHelper.stackWrappers.get(typeId);
                                IStackHandlerWrapper<Object> wrapper =
                                        (IStackHandlerWrapper) getter.apply(handler.resolve().get());
                                if (wrapper.getSlots() > 0)
                                {
                                    for (int i = 0; i < wrapper.getSlots(); i++)
                                    {
                                        IStackKey<?> typeKey = StackKeyRegistry.getType(typeId);
                                        KeyAmount ts = typeKey.fromStackObject(wrapper.getStackInSlot(0));
                                        if (ts != null)
                                        {
                                            KeyAmount s = new KeyAmount(ts.key(), 1);
                                            if (!s.isEmpty())
                                            {
                                                setStackDirectly(s.key(), s.amount());
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }
        else
        {
            if (carriedItem.isEmpty())
            {
                setStackDirectly(ItemStackKey.EMPTY, 0);
            }
            else
            {
                setStackDirectly(ItemStackKey.EMPTY, 0);
            }
        }
    }

    @Override
    public void quickMove(KeyAmount clickStack, int button, Player player)
    {
        click(clickStack, button, player);
    }

    @Override
    public void updateChange()
    {
        KeyAmount current = storage.getStackBySlot(this.getSlotIndex());
        if (lastStack.amount() != current.amount()
                || !lastStack.key().getTypeId().equals(current.key().getTypeId())
                || !lastStack.key().isSameTypeSameComponents(current.key()))
        {
            lastStack = current;
            BDPackets.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> (ServerPlayer) menu.player),
                    new OrderedStackTypedSlotPacket(index, theSlot, lastStack.key(), lastStack.amount()));
        }
    }

    @Override
    public void loadChange(int where, IStackKey<?> newKey, long newAmount)
    {
        storage.setStackDirectly(where, newKey, newAmount);
    }
}
