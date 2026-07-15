package com.solr98.beyondpractical.client.gui;

import com.solr98.beyondpractical.common.menu.NetTypePathwayMenu;
import com.wintercogs.beyonddimensions.client.gui.BDBaseGUI;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.client.gui.CommonTexturesRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import net.minecraft.network.chat.Component;

public class NetTypePathwayGUI extends BDBaseGUI<NetTypePathwayMenu>
{
    public NetTypePathwayGUI(NetTypePathwayMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
    }

    @Override
    protected void init()
    {
        imageWidth = CommonTextures.TOP_BASE_COMMON_WIDTH;
        imageHeight = CommonTextures.TOP_BASE_COMMON_HEIGHT
                + CommonTextures.FILTER_SLOTS_HEIGHT
                + CommonTextures.COMMON_CONNECTION_HEIGHT
                + CommonTextures.PLAYER_INV_HEIGHT;
        inventoryLabelY = CommonTextures.TOP_BASE_COMMON_HEIGHT
                + CommonTextures.FILTER_SLOTS_HEIGHT + 4;
        titleLabelY = 8;
        super.init();
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int[] y = {topPos};
        CommonTexturesRender.renderTopBaseCommon(graphics, leftPos, y);
        CommonTexturesRender.renderFilterSlots(graphics, leftPos, y);
        CommonTexturesRender.renderCommonConnection(graphics, leftPos, y);
        CommonTexturesRender.renderPlayerInv(graphics, leftPos, y);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
