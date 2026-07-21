package com.solr98.beyondpractical.common.init;

import com.solr98.beyondpractical.api.ids.BPBlockIds;
import com.solr98.beyondpractical.api.ids.BPConstants;
import com.solr98.beyondpractical.common.block.NetCrafterBlock;
import com.solr98.beyondpractical.common.block.NetTypePathwayBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BPBlocks
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BPConstants.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BPConstants.MODID);

    public static final RegistryObject<NetTypePathwayBlock> NET_TYPE_PATHWAY = BLOCKS.register(
            BPBlockIds.NET_TYPE_PATHWAY,
            () -> new NetTypePathwayBlock(BlockBehaviour.Properties.of().strength(2.0F)));
    public static final RegistryObject<Item> NET_TYPE_PATHWAY_ITEM = ITEMS.register(
            BPBlockIds.NET_TYPE_PATHWAY,
            () -> new BlockItem(NET_TYPE_PATHWAY.get(), new Item.Properties()));

    public static final RegistryObject<NetCrafterBlock> NET_CRAFTER = BLOCKS.register(
            BPBlockIds.NET_CRAFTER,
            () -> new NetCrafterBlock(BlockBehaviour.Properties.of().strength(2.0F)));
    public static final RegistryObject<Item> NET_CRAFTER_ITEM = ITEMS.register(
            BPBlockIds.NET_CRAFTER,
            () -> new BlockItem(NET_CRAFTER.get(), new Item.Properties()));

    private BPBlocks()
    {
    }

    public static void register(IEventBus eventBus)
    {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
