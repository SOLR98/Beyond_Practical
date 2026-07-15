package com.solr98.beyondpractical.common.init;

import com.solr98.beyondpractical.api.ids.BPConstants;
import com.solr98.beyondpractical.common.block.entity.NetTypePathwayBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class BPBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BPConstants.MODID);
    public static final RegistryObject<BlockEntityType<NetTypePathwayBlockEntity>> NET_TYPE_PATHWAY = BLOCK_ENTITY_TYPES.register(
            "net_type_pathway",
            () -> BlockEntityType.Builder.of(NetTypePathwayBlockEntity::new, BPBlocks.NET_TYPE_PATHWAY.get()).build(null));

    private BPBlockEntities()
    {
    }

    public static void register(IEventBus eventBus)
    {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
