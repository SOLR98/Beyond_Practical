package com.solr98.beyondpractical.common.init;

import com.solr98.beyondpractical.api.ids.BPConstants;
import com.solr98.beyondpractical.common.menu.NetTypePathwayMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BPMenus
{
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, BPConstants.MODID);
    public static final RegistryObject<MenuType<NetTypePathwayMenu>> NET_TYPE_PATHWAY_MENU = MENUS.register(
            "net_type_pathway",
            () -> IForgeMenuType.create(NetTypePathwayMenu::new));

    private BPMenus()
    {
    }

    public static void register(IEventBus eventBus)
    {
        MENUS.register(eventBus);
    }
}
