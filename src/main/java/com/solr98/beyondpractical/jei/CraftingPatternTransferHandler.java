package com.solr98.beyondpractical.jei;

import com.solr98.beyondpractical.common.init.BPMenus;
import com.solr98.beyondpractical.common.menu.NetCrafterMenu;
import com.solr98.beyondpractical.network.BPNetwork;
import com.solr98.beyondpractical.network.FillPatternPacket;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CraftingPatternTransferHandler implements IRecipeTransferHandler<NetCrafterMenu, CraftingRecipe>
{
    @Override
    public Class<? extends NetCrafterMenu> getContainerClass()
    {
        return NetCrafterMenu.class;
    }

    @Override
    public Optional<MenuType<NetCrafterMenu>> getMenuType()
    {
        return Optional.of(com.solr98.beyondpractical.common.init.BPMenus.NET_CRAFTER_MENU.get());
    }

    @Override
    public RecipeType<CraftingRecipe> getRecipeType()
    {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(NetCrafterMenu menu, CraftingRecipe recipe,
                                                         IRecipeSlotsView slotsView, Player player,
                                                         boolean maxTransfer, boolean doTransfer)
    {
        // 收集 JEI 配方 3×3 输入
        List<IRecipeSlotView> inputs = new ArrayList<>();
        for (var slotView : slotsView.getSlotViews(RecipeIngredientRole.INPUT))
            inputs.add(slotView);

        ItemStack[] fill = new ItemStack[9];
        java.util.Arrays.fill(fill, ItemStack.EMPTY);
        for (int i = 0; i < 9 && i < inputs.size(); i++)
        {
            var slotView = inputs.get(i);
            var candidates = slotView.getIngredients(VanillaTypes.ITEM_STACK)
                    .filter(s -> !s.isEmpty()).toList();
            fill[i] = candidates.isEmpty() ? ItemStack.EMPTY : candidates.get(0).copy();
        }

        if (doTransfer && menu.blockEntity != null)
        {
            BPNetwork.CHANNEL.sendToServer(new FillPatternPacket(menu.blockEntity.getBlockPos(), fill));
        }

        return null;
    }
}
