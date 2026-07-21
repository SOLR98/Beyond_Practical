package com.solr98.beyondpractical.jei;

import com.solr98.beyondpractical.api.ids.BPConstants;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class BPJeiPlugin implements IModPlugin
{
    private static final ResourceLocation UID = ResourceLocation.tryBuild(BPConstants.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid()
    {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration)
    {
        registration.addRecipeTransferHandler(new CraftingPatternTransferHandler(), RecipeTypes.CRAFTING);
    }
}
