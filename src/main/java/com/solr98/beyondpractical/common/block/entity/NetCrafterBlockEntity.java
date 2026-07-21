package com.solr98.beyondpractical.common.block.entity;

import com.solr98.beyondpractical.common.init.BPBlockEntities;
import com.solr98.beyondpractical.common.menu.NetCrafterMenu;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.block.entity.BaseMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NetCrafterBlockEntity extends BaseMachineBlockEntity implements MenuProvider
{
    public enum OutputMode { NETWORK, STORAGE, POP }

    private static final AbstractContainerMenu DUMMY_MENU = new AbstractContainerMenu(null, 0) {
        @Override public boolean stillValid(Player p) { return true; }
        @Override public void slotsChanged(net.minecraft.world.Container c) {}
        @Override public net.minecraft.world.item.ItemStack quickMoveStack(Player p, int i) { return net.minecraft.world.item.ItemStack.EMPTY; }
    };

    // === 配方 ===
    private static final int PATTERN_SIZE = 9;
    private final StackHandler patternSlots = new StackHandler(PATTERN_SIZE)
    {
        @Override
        public void onChange()
        {
            if (level != null && !level.isClientSide())
            {
                // 仅更新图案相关缓存，不清除配方（配方由玩家显式确认）
                cachedStatus = null;
                cachedPatternItemKeys = null;
                returnBufferToNetwork();
                setChanged();
            }
        }
    };
    private ResourceLocation selectedRecipeId;
    private @Nullable CraftingRecipe cachedRecipe;
    private boolean cachedStandard;
    private @Nullable NonNullList<ItemStack> cappedRemainders;
    private @Nullable List<RecipeOption> cachedAvailableRecipes;
    private long availableRecipesHash;
    private @Nullable ItemStackKey[] cachedPatternItemKeys;

    // === 运行参数 ===
    private int batchSize = 64;
    private OutputMode outputMode = OutputMode.NETWORK;
    private int outputDirections = 0x3F; // bit0=N, 1=E, 2=S, 3=W, 4=U, 5=D, 默认全选

    // === 运行时 ===
    private static final int OUTPUT_SLOTS = 36;
    private final ItemStackHandler batchBuffer = new ItemStackHandler(PATTERN_SIZE);
    private final ItemStackHandler outputStorage = new ItemStackHandler(OUTPUT_SLOTS);
    private LazyOptional<IItemHandler> storageCap = createStorageCap();

    private LazyOptional<IItemHandler> createStorageCap() {
        return LazyOptional.of(() -> outputStorage);
    }

    public NetCrafterBlockEntity(BlockPos pos, BlockState state)
    {
        super(BPBlockEntities.NET_CRAFTER.get(), pos, state);
    }

    // ========== 公共访问 ==========

    public StackHandler getPatternSlots() { return patternSlots; }
    public ItemStackHandler getBatchBuffer() { return batchBuffer; }
    public ItemStackHandler getOutputStorage() { return outputStorage; }
    public ResourceLocation getSelectedRecipeId() { return selectedRecipeId; }
    public int getBatchSize() { return batchSize; }
    public OutputMode getOutputMode() { return outputMode; }

    public void setBatchSize(int v) { batchSize = Math.max(1, Math.min(64, v)); setChanged(); }
    public void setOutputMode(OutputMode v) { outputMode = v; setChanged(); }
    public int getOutputDirections() { return outputDirections; }
    public void setOutputDirections(int bits) { outputDirections = bits & 0x3F; setChanged(); }
    public boolean isDirectionEnabled(int idx) { return (outputDirections & (1 << idx)) != 0; }
    public void toggleDirection(int idx) { outputDirections ^= (1 << idx); outputDirections &= 0x3F; setChanged(); }

    // ========== 状态 ==========

    public enum CrafterStatus { NO_PATTERN, INVALID_PATTERN, RECIPE_READY, WORKING, RESOURCE_BLOCKED }

    private CrafterStatus cachedStatus;
    private long statusGameTime;

    public CrafterStatus getStatus()
    {
        // 每 tick 最多计算一次（renderLabels 可能每帧调用）
        long gt = level != null ? level.getGameTime() : 0;
        if (cachedStatus != null && gt == statusGameTime)
            return cachedStatus;
        statusGameTime = gt;
        if (level == null) return cachedStatus = CrafterStatus.NO_PATTERN;
        var pattern = patternSlots;
        boolean hasPattern = false;
        for (int i = 0; i < pattern.getSlots(); i++)
            if (!pattern.getStackBySlot(i).isEmpty()) { hasPattern = true; break; }
        if (!hasPattern) return cachedStatus = CrafterStatus.NO_PATTERN;

        if (selectedRecipeId == null) return cachedStatus = CrafterStatus.INVALID_PATTERN;

        if (getNet() == null) return cachedStatus = CrafterStatus.RESOURCE_BLOCKED;

        var recipe = cachedRecipe;
        if (recipe == null)
        {
            var r = level.getRecipeManager().byKey(selectedRecipeId);
            if (r.isEmpty() || !(r.get() instanceof CraftingRecipe cr)) return cachedStatus = CrafterStatus.INVALID_PATTERN;
            recipe = cr;
        }

        // 检查原料是否充足
        var ingredients = recipe.getIngredients();
        var us = getNet().getUnifiedStorage();
        for (var ing : ingredients)
        {
            if (ing.isEmpty()) continue;
            var stacks = ing.getItems();
            if (stacks.length == 0) continue;
            var key = new ItemStackKey(stacks[0]);
            long need = (long) stacks[0].getCount() * batchSize;
            var got = us.extract(key, need, true, false);
            if (got.amount() < need) return cachedStatus = CrafterStatus.RESOURCE_BLOCKED;
        }

        if (shouldWork()) return cachedStatus = CrafterStatus.WORKING;
        return cachedStatus = CrafterStatus.RECIPE_READY;
    }

    // ========== 配方 ==========

    public void setSelectedRecipeId(@Nullable ResourceLocation id)
    {
        if (level == null || level.isClientSide()) return;
        if (id == null) { selectedRecipeId = null; return; }
        var recipe = level.getRecipeManager().byKey(id);
        if (recipe.isEmpty() || !(recipe.get() instanceof CraftingRecipe cr)) return;
        cachedStatus = null;
        selectedRecipeId = id;
        cachedRecipe = cr;
        classifyRecipe(cr);
        setChanged();
    }

    public List<RecipeOption> getAvailableRecipes()
    {
        long hash = computePatternHash();
        if (cachedAvailableRecipes != null && hash == availableRecipesHash)
            return cachedAvailableRecipes;
        cachedAvailableRecipes = computeAvailableRecipes();
        availableRecipesHash = hash;
        return cachedAvailableRecipes;
    }

    private long computePatternHash()
    {
        long h = 0;
        for (int i = 0; i < PATTERN_SIZE; i++)
        {
            var ka = patternSlots.getStackBySlot(i);
            h = h * 31 + (ka.isEmpty() ? 0 : ka.key().hashCode() * 31 + ka.amount());
        }
        return h;
    }

    private List<RecipeOption> computeAvailableRecipes()
    {
        List<RecipeOption> list = new ArrayList<>();
        if (level == null) return list;
        var input = buildPatternContainer();
        for (var recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING))
        {
            if (recipe.matches(input, level))
                list.add(new RecipeOption(recipe.getId(), recipe.getResultItem(level.registryAccess())));
        }
        return list;
    }

    public record RecipeOption(ResourceLocation id, ItemStack output) {}

    private boolean validatePatternForRecipe(CraftingRecipe recipe)
    {
        var input = buildPatternContainer();
        return recipe.matches(input, level);
    }

    private CraftingContainer buildPatternContainer()
    {
        var c = new TransientCraftingContainer(DUMMY_MENU, 3, 3);
        for (int i = 0; i < PATTERN_SIZE; i++)
        {
            var ka = patternSlots.getStackBySlot(i);
            if (!ka.isEmpty() && ka.key() instanceof ItemStackKey ik)
            {
                var s = ik.getReadOnlyStack();
                var copy = s.copy();
                copy.setCount(1);
                c.setItem(i, copy);
            }
        }
        return c;
    }

    private void classifyRecipe(CraftingRecipe recipe)
    {
        cachedStandard = recipe.getClass().getName().startsWith("net.minecraft.world.item.crafting.");
    }

    // ========== 工作周期 ==========

    @Override
    public int getTicksPerWork() { return 10; }

    @Override
    public boolean shouldWork()
    {
        if (level == null || level.isClientSide()) return false;
        if (selectedRecipeId == null) return false;
        return super.shouldWork();
    }

    @Override
    public void workContent()
    {
        if (cachedRecipe == null)
        {
            var r = level.getRecipeManager().byKey(selectedRecipeId);
            if (r.isEmpty() || !(r.get() instanceof CraftingRecipe cr)) return;
            cachedRecipe = cr;
            classifyRecipe(cr);
        }

        if (!validatePatternForRecipe(cachedRecipe))
        {
            selectedRecipeId = null;
            cachedRecipe = null;
            cachedStandard = false;
            cappedRemainders = null;
            setChanged();
            return;
        }

        var storage = getNetStorage();
        if (storage == null) return;

        if (cachedStandard)
        {
            if (!doStandardCraft(batchSize, storage)) return;
        }
        else
        {
            var ingredients = cachedRecipe.getIngredients();
            if (!batchExtractPerSlot(storage, ingredients, batchSize)) return;
            doCustomCraft(batchSize, storage);
        }

        setChanged();
    }

    @Override
    public void workEnd()
    {
        if (outputMode == OutputMode.POP)
        {
            Direction[] dirs = Direction.values();
            for (int i = 0; i < outputStorage.getSlots(); i++)
            {
                var stack = outputStorage.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                for (int d = 0; d < dirs.length; d++)
                {
                    if (!isDirectionEnabled(d)) continue;
                    Direction dir = dirs[d];
                    var target = level.getBlockEntity(worldPosition.relative(dir));
                    if (target == null || target instanceof NetCrafterBlockEntity) continue;
                    var cap = target.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite());
                    if (!cap.isPresent()) continue;
                    var handler = cap.resolve().get();
                    var remaining = insertItemToHandler(handler, stack);
                    if (remaining.isEmpty()) { outputStorage.setStackInSlot(i, ItemStack.EMPTY); break; }
                    outputStorage.setStackInSlot(i, remaining);
                }
            }
        }
    }

    // ========== 合成路径 ==========

    private boolean doStandardCraft(int need, DimensionsNet storage)
    {
        var us = storage.getUnifiedStorage();
        var ings = cachedRecipe.getIngredients();

        // 第 1 遍：统计每种材料总需求（合并相同 key，如 8 个铁→总数）
        // 同时也算出一批中每种材料总量 ÷ need = 单次消耗
        int ingCount = ings.size();
        java.util.Map<ItemStackKey, long[]> reqMap = new java.util.HashMap<>();
        for (int s = 0; s < ingCount; s++)
        {
            var ing = ings.get(s);
            if (ing.isEmpty()) continue;
            var stacks = ing.getItems();
            if (stacks.length == 0) continue;
            var key = new ItemStackKey(stacks[0]);
            long add = (long) stacks[0].getCount();
            long[] total = reqMap.get(key);
            reqMap.put(key, new long[] { (total == null ? 0 : total[0]) + add });
        }

        // 第 2 遍：将单次消耗 × need 得到总需求，模拟验证
        for (var entry : reqMap.entrySet())
        {
            long total = entry.getValue()[0] * need;
            if (us.extract(entry.getKey(), total, true, true).amount() < total)
                return false;
        }

        // 第 3 遍：正式提取全部材料 → batchBuffer（扁平存放，每种材料一格）
        int slotIdx = 0;
        for (var entry : reqMap.entrySet())
        {
            long total = entry.getValue()[0] * need;
            var got = us.extract(entry.getKey(), total, false, true);
            batchBuffer.setStackInSlot(slotIdx++,
                    got.toStack() instanceof ItemStack is ? is.copy() : ItemStack.EMPTY);
        }

        // 第 4 遍：填充无效 batchBuffer 槽位为 EMPTY
        for (; slotIdx < batchBuffer.getSlots(); slotIdx++)
            batchBuffer.setStackInSlot(slotIdx, ItemStack.EMPTY);

        // 缓存剩余物
        if (cappedRemainders == null)
        {
            var dummy = new TransientCraftingContainer(DUMMY_MENU, 3, 3);
            for (int i = 0; i < ingCount; i++)
            {
                var ig = ings.get(i);
                if (!ig.isEmpty())
                {
                    var items = ig.getItems();
                    if (items.length > 0) dummy.setItem(i, items[0].copy());
                }
            }
            cappedRemainders = cachedRecipe.getRemainingItems(dummy);
        }

        var result = cachedRecipe.getResultItem(level.registryAccess());

        // 一次性消耗：清空 batchBuffer（材料已提取完成）
        for (int r = 0; r < reqMap.size(); r++)
            batchBuffer.setStackInSlot(r, ItemStack.EMPTY);

        // 一次性输出所有产物
        for (int i = 0; i < need; i++)
            pushOutput(result.copy());

        // 剩余物（如空桶）乘以批量次数后返还网络
        if (cappedRemainders != null)
        {
            for (var rem : cappedRemainders)
            {
                if (!rem.isEmpty())
                {
                    var net = getNet();
                    if (net != null)
                    {
                        var total = rem.copy();
                        total.setCount(total.getCount() * need);
                        net.getUnifiedStorage().insert(new ItemStackKey(total), total.getCount(), false);
                    }
                }
            }
        }
        return true;
    }

    private void doCustomCraft(int need, DimensionsNet storage)
    {
        var input = new TransientCraftingContainer(DUMMY_MENU, 3, 3);

        for (int i = 0; i < need; i++)
        {
            for (int s = 0; s < PATTERN_SIZE; s++)
            {
                var stack = batchBuffer.getStackInSlot(s);
                if (!stack.isEmpty())
                {
                    input.setItem(s, stack.copy());
                    stack.shrink(1);
                    if (stack.getCount() <= 0) batchBuffer.setStackInSlot(s, ItemStack.EMPTY);
                }
                else
                {
                    input.setItem(s, ItemStack.EMPTY);
                }
            }

            if (!cachedRecipe.matches(input, level)) break;

            var result = cachedRecipe.assemble(input, level.registryAccess());
            var remainders = cachedRecipe.getRemainingItems(input);

            pushOutput(result);
            handleRemainders(remainders);

            for (int r = 0; r < remainders.size(); r++)
            {
                var rem = remainders.get(r);
                if (!rem.isEmpty())
                {
                    var existing = batchBuffer.getStackInSlot(r);
                    if (existing.isEmpty())
                        batchBuffer.setStackInSlot(r, rem.copy());
                    else if (ItemStack.isSameItemSameTags(existing, rem))
                        existing.grow(rem.getCount());
                }
            }
        }
    }

    // ========== 原料处理 ==========

    private @Nullable DimensionsNet getNetStorage()
    {
        var net = getNet();
        return net;
    }

    private void buildPatternKeyCache()
    {
        if (cachedPatternItemKeys != null) return;
        cachedPatternItemKeys = new ItemStackKey[PATTERN_SIZE];
        for (int i = 0; i < PATTERN_SIZE; i++)
        {
            var ka = patternSlots.getStackBySlot(i);
            if (!ka.isEmpty() && ka.key() instanceof ItemStackKey ik)
                cachedPatternItemKeys[i] = ik;
        }
    }

    private @Nullable ItemStackKey extractKeyForSlot(int slot)
    {
        buildPatternKeyCache();
        return cachedPatternItemKeys[slot];
    }

    private long ingredientCountForSlot(int slot, NonNullList<Ingredient> ingredients)
    {
        if (slot < ingredients.size())
        {
            var ing = ingredients.get(slot);
            if (!ing.isEmpty())
            {
                var stacks = ing.getItems();
                if (stacks.length > 0)
                    return stacks[0].getCount();
            }
        }
        return 1;
    }

    private boolean batchExtractPerSlot(DimensionsNet storage, NonNullList<Ingredient> ingredients, int need)
    {
        var us = storage.getUnifiedStorage();
        int ingCount = Math.min(ingredients.size(), PATTERN_SIZE);

        // Phase 1: Simulate — 验证全部足够
        for (int s = 0; s < ingCount; s++)
        {
            var ing = ingredients.get(s);
            if (ing.isEmpty()) continue;
            // 用图案槽精确 key 保证 NBT，用 Ingredient count 保证数量
            var key = extractKeyForSlot(s);
            if (key == null)
            {
                var stacks = ing.getItems();
                if (stacks.length == 0) continue;
                key = new ItemStackKey(stacks[0]);
            }
            long perCraft = ingredientCountForSlot(s, ingredients);
            long total = perCraft * need;
            var got = us.extract(key, total, true, true);
            if (got.amount() < total) return false;
        }

        // Phase 2: Execute — 按格位提取并填入 batchBuffer，失败则回滚
        var net = getNet();
        for (int s = 0; s < PATTERN_SIZE; s++)
        {
            if (s >= ingCount) { batchBuffer.setStackInSlot(s, ItemStack.EMPTY); continue; }
            var ing = ingredients.get(s);
            if (ing.isEmpty()) { batchBuffer.setStackInSlot(s, ItemStack.EMPTY); continue; }
            var key = extractKeyForSlot(s);
            if (key == null)
            {
                var stacks = ing.getItems();
                if (stacks.length == 0) { batchBuffer.setStackInSlot(s, ItemStack.EMPTY); continue; }
                key = new ItemStackKey(stacks[0]);
            }
            long perCraft = ingredientCountForSlot(s, ingredients);
            long total = perCraft * need;
            var got = us.extract(key, total, false, true);
            if (got.amount() < total)
            {
                for (int r = 0; r < s; r++)
                {
                    var rollback = batchBuffer.getStackInSlot(r);
                    if (!rollback.isEmpty())
                    {
                        if (net != null)
                            net.getUnifiedStorage().insert(new ItemStackKey(rollback), rollback.getCount(), false);
                        batchBuffer.setStackInSlot(r, ItemStack.EMPTY);
                    }
                }
                return false;
            }
            if (got.toStack() instanceof ItemStack is)
                batchBuffer.setStackInSlot(s, is.copy());
            else
                batchBuffer.setStackInSlot(s, ItemStack.EMPTY);
        }
        return true;
    }

    private void returnBufferToNetwork()
    {
        var net = getNet();
        if (net == null) return;
        var us = net.getUnifiedStorage();
        for (int i = 0; i < batchBuffer.getSlots(); i++)
        {
            var stack = batchBuffer.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            us.insert(new ItemStackKey(stack), stack.getCount(), false);
            batchBuffer.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // ========== 输出处理 ==========

    private void pushOutput(ItemStack stack)
    {
        if (stack.isEmpty()) return;
        switch (outputMode)
        {
            case NETWORK ->
            {
                var net = getNet();
                if (net != null)
                    net.getUnifiedStorage().insert(new ItemStackKey(stack), stack.getCount(), false);
            }
            case STORAGE, POP ->
            {
                for (int i = 0; i < outputStorage.getSlots(); i++)
                {
                    var existing = outputStorage.getStackInSlot(i);
                    if (existing.isEmpty())
                    {
                        outputStorage.setStackInSlot(i, stack.copy());
                        return;
                    }
                    if (ItemStack.isSameItemSameTags(existing, stack) && existing.getCount() < existing.getMaxStackSize())
                    {
                        int space = existing.getMaxStackSize() - existing.getCount();
                        int add = Math.min(space, stack.getCount());
                        existing.grow(add);
                        stack.shrink(add);
                        if (stack.isEmpty()) return;
                    }
                }
            }
        }
    }

    private void handleRemainders(NonNullList<ItemStack> remainders)
    {
        var net = getNet();
        if (net == null) return;
        var us = net.getUnifiedStorage();
        for (var rem : remainders)
        {
            if (!rem.isEmpty())
                us.insert(new ItemStackKey(rem), rem.getCount(), false);
        }
    }

    private ItemStack insertItemToHandler(IItemHandler handler, ItemStack stack)
    {
        var copy = stack.copy();
        for (int i = 0; i < handler.getSlots(); i++)
        {
            copy = handler.insertItem(i, copy, false);
            if (copy.isEmpty()) return ItemStack.EMPTY;
        }
        return copy;
    }

    // ========== 能力暴露 ==========

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side)
    {
        if (cap == ForgeCapabilities.ITEM_HANDLER && outputMode != OutputMode.NETWORK)
            return storageCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps()
    {
        super.invalidateCaps();
        storageCap.invalidate();
        storageCap = createStorageCap();
    }

    // ========== NBT ==========

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        patternSlots.deserializeNBT(tag.getCompound("patternSlots"));
        cachedPatternItemKeys = null;
        selectedRecipeId = tag.contains("recipeId") ? ResourceLocation.tryParse(tag.getString("recipeId")) : null;
        batchSize = tag.getInt("batchSize");
        outputMode = OutputMode.valueOf(tag.getString("outputMode"));
        outputDirections = tag.getInt("outputDirections");
        batchBuffer.deserializeNBT(tag.getCompound("batchBuffer"));
        outputStorage.deserializeNBT(tag.getCompound("outputStorage"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.put("patternSlots", patternSlots.serializeNBT());
        if (selectedRecipeId != null) tag.putString("recipeId", selectedRecipeId.toString());
        tag.putInt("batchSize", batchSize);
        tag.putString("outputMode", outputMode.name());
        tag.putInt("outputDirections", outputDirections);
        tag.put("batchBuffer", batchBuffer.serializeNBT());
        tag.put("outputStorage", outputStorage.serializeNBT());
    }

    // ========== 掉落 ==========

    public void dropContent()
    {
        if (level == null || level.isClientSide()) return;
        var net = getNet();
        if (net != null) returnBufferToNetwork();
        for (int i = 0; i < OUTPUT_SLOTS; i++)
        {
            var s = outputStorage.getStackInSlot(i);
            if (!s.isEmpty())
                Block.popResource(level, worldPosition, s.copy());
        }
    }

    // ========== MenuProvider ==========

    @Override
    public Component getDisplayName() { return Component.translatable("menu.beyond_practical.net_crafter"); }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player)
    {
        return new NetCrafterMenu(id, inv, this);
    }
}
