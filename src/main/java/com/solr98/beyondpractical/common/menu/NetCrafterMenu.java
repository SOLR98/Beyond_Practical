package com.solr98.beyondpractical.common.menu;

import com.solr98.beyondpractical.client.gui.PatternGridRenderer;
import com.solr98.beyondpractical.common.block.entity.NetCrafterBlockEntity;
import com.solr98.beyondpractical.common.block.entity.NetCrafterBlockEntity.OutputMode;
import com.solr98.beyondpractical.common.init.BPMenus;
import com.solr98.beyondpractical.common.menu.widget.PatternSlot;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class NetCrafterMenu extends BDBaseMenu
{
    public final NetCrafterBlockEntity blockEntity;
    public final Slot resultSlot;

    /* PSD 布局 (更新后):
     *  top_base: 0-24
     *  pattern rows: 24-78 (3 rows)
     *    3×3 grid at y=24, slot offset +1
     *    result at y=42
     *  conn: 78-86
     *  storage: 86-140 (3 rows)
     *  conn: 140-148
     *  inv: 148-237
     */
    private static final int GRID_TOP = 24;                     // 网格区域起始
    private static final int PATTERN_START = GRID_TOP + 1;      // 24 + 0 + 1 = 25
    private static final int RESULT_X = 87;
    private static final int RESULT_Y = GRID_TOP + 18 + 1;     // 24 + 18 + 1 = 43
    private static final int STORAGE_START = 86 + 1;            // 87
    private static final int INV_START = 148 + 7;               // 155

    private static final int STORAGE_ROWS = 3;

    private OutputMode lastOutputMode;
    private RedStoneControlMode lastControlMode;
    private int lastDirections;
    private int lastBatchSize;
    private ResourceLocation recipeIdToSync;

    public NetCrafterMenu(int id, Inventory inventory, FriendlyByteBuf data)
    {
        this(id, inventory, (NetCrafterBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
    }

    public NetCrafterMenu(int id, Inventory inventory, NetCrafterBlockEntity blockEntity)
    {
        super(BPMenus.NET_CRAFTER_MENU.get(), id, inventory);
        this.blockEntity = blockEntity;

        if (blockEntity != null)
        {
            lastOutputMode = blockEntity.getOutputMode();
            lastControlMode = blockEntity.controlMode;
            lastDirections = blockEntity.getOutputDirections();
            lastBatchSize = blockEntity.getBatchSize();

            // 3×3 配方图案
            var pattern = blockEntity.getPatternSlots();
            int idx = 0;
            for (int row = 0; row < PatternGridRenderer.ROWS; row++)
                for (int col = 0; col < PatternGridRenderer.COLS; col++)
                    addSlot(new PatternSlot(this, pattern, idx++,
                            PatternGridRenderer.getSlotX(col) + 1,
                            PATTERN_START + row * PatternGridRenderer.SLOT_SIZE));

            // 结果预览（只读）
            var previewInv = new net.minecraft.world.SimpleContainer(1);
            resultSlot = addSlot(new Slot(previewInv, 0, RESULT_X, RESULT_Y) {
                @Override public boolean mayPickup(Player p) { return false; }
                @Override public boolean mayPlace(ItemStack s) { return false; }
            });

            // 成品仓库
            var storage = blockEntity.getOutputStorage();
            for (int row = 0; row < STORAGE_ROWS; row++)
                for (int col = 0; col < 9; col++)
                    addSlot(new SlotItemHandler(storage, row * 9 + col,
                            8 + col * 18, STORAGE_START + row * 18));
        }
        else
        {
            resultSlot = null;
        }

        inventoryStartIndex = slots.size();

        // 玩家背包
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9,
                        8 + col * 18, INV_START + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, INV_START + 58));

        inventoryEndIndex = slots.size();
    }

    public void sendRecipeSelection(ResourceLocation recipeId)
    {
        recipeIdToSync = recipeId;
        writeAndSendQuickData();
    }

    @Override
    protected boolean shouldSendQuickData()
    {
        return blockEntity != null
                && (recipeIdToSync != null
                    || blockEntity.getOutputMode() != lastOutputMode
                    || blockEntity.controlMode != lastControlMode
                    || blockEntity.getOutputDirections() != lastDirections
                    || blockEntity.getBatchSize() != lastBatchSize);
    }

    @Override
    protected void writeQuickDataTag(CompoundTag tag)
    {
        if (blockEntity == null) return;
        lastOutputMode = blockEntity.getOutputMode();
        lastControlMode = blockEntity.controlMode;
        lastDirections = blockEntity.getOutputDirections();
        lastBatchSize = blockEntity.getBatchSize();
        tag.putString("outputMode", lastOutputMode.name());
        tag.putString("controlMode", lastControlMode.name());
        tag.putInt("outputDirections", lastDirections);
        tag.putInt("batchSize", lastBatchSize);
        if (recipeIdToSync != null)
        {
            tag.putString("recipeId", recipeIdToSync.toString());
            recipeIdToSync = null;
        }
    }

    @Override
    public void readQuickDataTag(CompoundTag tag)
    {
        if (blockEntity != null)
        {
            blockEntity.setOutputMode(OutputMode.valueOf(tag.getString("outputMode")));
            blockEntity.controlMode = RedStoneControlMode.valueOf(tag.getString("controlMode"));
            blockEntity.setOutputDirections(tag.getInt("outputDirections"));
            blockEntity.setBatchSize(tag.getInt("batchSize"));
            if (tag.contains("recipeId"))
                blockEntity.setSelectedRecipeId(ResourceLocation.tryParse(tag.getString("recipeId")));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player)
    {
        return blockEntity != null && !blockEntity.isRemoved()
                && player.distanceToSqr(blockEntity.getBlockPos().getX() + 0.5D,
                       blockEntity.getBlockPos().getY() + 0.5D,
                       blockEntity.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }
}
