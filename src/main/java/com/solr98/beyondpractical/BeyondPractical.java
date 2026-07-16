package com.solr98.beyondpractical;

import com.solr98.beyondpractical.api.ids.BPConstants;
import com.solr98.beyondpractical.client.gui.NetTypePathwayGUI;
import com.solr98.beyondpractical.common.init.BPBlockEntities;
import com.solr98.beyondpractical.common.init.BPBlocks;
import com.solr98.beyondpractical.common.init.BPMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BPConstants.MODID)
public class BeyondPractical
{
    public BeyondPractical()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BPBlocks.register(modEventBus);
        BPBlockEntities.register(modEventBus);
        BPMenus.register(modEventBus);
        modEventBus.addListener(this::addCreativeTab);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> modEventBus.addListener(BeyondPractical::clientSetup));
    }

    private void addCreativeTab(BuildCreativeModeTabContentsEvent event)
    {
        ResourceLocation tabKey = event.getTabKey().location();
        if (tabKey.equals(ResourceLocation.tryParse("beyonddimensions:beyond_dimensions_blocks_tab")))
            event.accept(BPBlocks.NET_TYPE_PATHWAY.get());
    }

    private static void clientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> MenuScreens.register(BPMenus.NET_TYPE_PATHWAY_MENU.get(), NetTypePathwayGUI::new));
    }
}
