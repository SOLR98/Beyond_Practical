package com.solr98.beyondpractical.client.gui;

import com.solr98.beyondpractical.common.block.entity.NetCrafterBlockEntity.CrafterStatus;
import com.solr98.beyondpractical.common.block.entity.NetCrafterBlockEntity.OutputMode;
import com.solr98.beyondpractical.common.block.entity.NetCrafterBlockEntity.RecipeOption;
import com.solr98.beyondpractical.common.menu.NetCrafterMenu;
import com.wintercogs.beyonddimensions.api.ids.BDConstants;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.client.gui.BDBaseGUI;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.client.gui.CommonTexturesRender;
import com.wintercogs.beyonddimensions.client.gui.widget.shared.RightTabButton;
import com.wintercogs.beyonddimensions.common.machine.RedStoneControlMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NetCrafterGUI extends BDBaseGUI<NetCrafterMenu>
{
    private static final int TOP_H = CommonTextures.TOP_BASE_COMMON_HEIGHT;
    private static final int INV_H = CommonTextures.PLAYER_INV_HEIGHT;
    private static final int CONN_H = CommonTextures.COMMON_CONNECTION_HEIGHT;
    private static final int STORAGE_ROWS = 3;
    private static final int PATTERN_H = 54;

    // BD 纹理常量
    private static final ResourceLocation SLOT_BTN = ResourceLocation.tryBuild("beyonddimensions", "textures/gui/sprites/widget/slot_button.png");
    private static final ResourceLocation SLOT_BTN_HOVERED = ResourceLocation.tryBuild("beyonddimensions", "textures/gui/sprites/widget/slot_button_hovered.png");
    private static final ResourceLocation SLOT_BTN_DISABLED = ResourceLocation.tryBuild("beyonddimensions", "textures/gui/sprites/widget/slot_button_disabled.png");
    private static final ResourceLocation CRAFT_BTN = ResourceLocation.tryBuild("beyonddimensions", "textures/gui/sprites/widget/craft_button.png");
    private static final ResourceLocation UP_ARROW = ResourceLocation.tryBuild("beyonddimensions", "textures/gui/sprites/widget/up_arrow.png");


    private RightTabButton outputModeButton;
    private RightTabButton controlModeButton;
    private Button prevBtn, nextBtn, okBtn;
    private EditBox batchInput;
    private Button[] dirButtons;

    private List<RecipeOption> cachedRecipes;
    private int recipeIndex;
    private boolean hasSelection;
    private KeyAmount[] lastKnownPattern;
    private boolean initialRefreshDone;

    public NetCrafterGUI(NetCrafterMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
    }

    @Override
    protected void init()
    {
        imageWidth = CommonTextures.TOP_BASE_COMMON_WIDTH;
        imageHeight = TOP_H + PATTERN_H + CONN_H + STORAGE_ROWS * 18 + CONN_H + INV_H;
        inventoryLabelY = imageHeight - 94;
        titleLabelY = 8;
        super.init();

        int bx = leftPos + imageWidth;
        int by = topPos + 6;

        // === 右侧 Tab 按钮 ===
        outputModeButton = new RightTabButton(bx, by, 23, 26,
                bx + 3, by + 4, 16, 16, button -> {
            outputModeButton.toggleState();
            menu.blockEntity.setOutputMode((OutputMode) outputModeButton.currentState);
            menu.writeAndSendQuickData();
        }) {
            @Override protected void initButton() {
                iconMap.put(OutputMode.NETWORK, ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/popmode_up.png"));
                iconMap.put(OutputMode.STORAGE, ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/popmode_down.png"));
                iconMap.put(OutputMode.POP, ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/hopper_item_mode_allow.png"));
                tooltipMap.put(OutputMode.NETWORK, Tooltip.create(Component.translatable("tooltip.beyond_practical.output_network")));
                tooltipMap.put(OutputMode.STORAGE, Tooltip.create(Component.translatable("tooltip.beyond_practical.output_storage")));
                tooltipMap.put(OutputMode.POP, Tooltip.create(Component.translatable("tooltip.beyond_practical.output_pop")));
                states.addAll(iconMap.keySet());
                setState(menu.blockEntity.getOutputMode());
            }
        };
        addRenderableWidget(outputModeButton);

        controlModeButton = new RightTabButton(bx, by + 30, 23, 26,
                bx + 3, by + 30 + 4, 16, 16, button -> {
            controlModeButton.toggleState();
            menu.blockEntity.controlMode = (RedStoneControlMode) controlModeButton.currentState;
            menu.writeAndSendQuickData();
        }) {
            @Override protected void initButton() {
                iconMap.put(RedStoneControlMode.IGNORE, ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/control_mode_ignore.png"));
                iconMap.put(RedStoneControlMode.POWERED, ResourceLocation.tryBuild(BDConstants.MODID, "textures/gui/sprites/widget/control_mode_powered.png"));
                tooltipMap.put(RedStoneControlMode.IGNORE, Tooltip.create(Component.translatable("tooltip.beyond_practical.redstone_ignore")));
                tooltipMap.put(RedStoneControlMode.POWERED, Tooltip.create(Component.translatable("tooltip.beyond_practical.redstone_powered")));
                states.addAll(iconMap.keySet());
                setState(menu.blockEntity.controlMode);
            }
        };
        addRenderableWidget(controlModeButton);

        // === 配方选择按钮 (PSD: x=71,87,103 y=60) ===
        int btnY = topPos + 60;
        prevBtn = makeArrowBtn(leftPos + 71, btnY, -90f, b -> cycleRecipe(-1));  // ←
        nextBtn = makeArrowBtn(leftPos + 103, btnY, 90f, b -> cycleRecipe(1));   // →
        okBtn = makeCheckBtn(leftPos + 87, btnY, b -> confirmRecipe());
        addRenderableWidget(prevBtn);
        addRenderableWidget(nextBtn);
        addRenderableWidget(okBtn);
        prevBtn.setTooltip(Tooltip.create(Component.translatable("tooltip.beyond_practical.recipe_prev")));
        nextBtn.setTooltip(Tooltip.create(Component.translatable("tooltip.beyond_practical.recipe_next")));
        okBtn.setTooltip(Tooltip.create(Component.translatable("tooltip.beyond_practical.recipe_confirm")));

        // === 输出方向 2×3 按钮 (PSD: x=121,137,153 y=24,40) ===
        int[] dirXs = {121, 137, 153};
        int[] dirYs = {24, 40};
        dirButtons = new Button[6];
        for (int row = 0; row < 2; row++)
            for (int col = 0; col < 3; col++)
            {
                int idx = row * 3 + col;
                int x = leftPos + dirXs[col];
                int y = topPos + dirYs[row];
                dirButtons[idx] = makeSlotBtn(Component.literal(""), x, y, SLOT_BTN, b -> toggleDir(idx));
                addRenderableWidget(dirButtons[idx]);
                dirButtons[idx].setTooltip(Tooltip.create(Component.translatable("tooltip.beyond_practical.direction." + idx)));
            }

        // === 批量大小输入框 (方向按钮下方) ===
        batchInput = new EditBox(font, leftPos + 137, topPos + 61, 28, 14, Component.literal("batch"));
        batchInput.setValue(String.valueOf(menu.blockEntity.getBatchSize()));
        batchInput.setMaxLength(3);
        batchInput.setFilter(s -> s.matches("\\d*") && s.length() <= 3);
        batchInput.setResponder(s -> {
            int v = s.isEmpty() ? 1 : Math.max(1, Math.min(64, Integer.parseInt(s)));
            menu.blockEntity.setBatchSize(v);
            menu.writeAndSendQuickData();
        });
        addRenderableWidget(batchInput);

        initialRefreshDone = false;
    }

    private Button makeSlotBtn(Component text, int x, int y, ResourceLocation tex, Button.OnPress onPress)
    {
        return new StyledBtn(x, y, text, onPress, tex);
    }

    private Button makeArrowBtn(int x, int y, float rotation, Button.OnPress onPress)
    {
        return new ArrowBtn(x, y, onPress, rotation);
    }

    private Button makeCheckBtn(int x, int y, Button.OnPress onPress)
    {
        return new CheckBtn(x, y, onPress);
    }

    private static class StyledBtn extends Button
    {
        private final ResourceLocation icon;
        private boolean toggled;
        protected StyledBtn(int x, int y, Component text, OnPress onPress, ResourceLocation icon) {
            super(x, y, 16, 16, text, onPress, DEFAULT_NARRATION);
            this.icon = icon;
        }
        private void setToggled(boolean t) { toggled = t; }
        private ResourceLocation bg() {
            return toggled ? SLOT_BTN_HOVERED : SLOT_BTN;
        }
        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            isHovered = mx >= getX() && my >= getY() && mx < getX() + width && my < getY() + height;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(bg(), getX(), getY(), 0, 0, 16, 16, 16, 16);
            var msg = getMessage().getString();
            if (!msg.isEmpty())
                g.drawString(net.minecraft.client.Minecraft.getInstance().font, getMessage(), getX() + 3, getY() + 4, 0xFFFFFF, false);
        }
    }

    private static class CheckBtn extends Button
    {
        protected CheckBtn(int x, int y, OnPress onPress) {
            super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
        }
        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            isHovered = mx >= getX() && my >= getY() && mx < getX() + width && my < getY() + height;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            ResourceLocation bg = !active ? SLOT_BTN_DISABLED : isHoveredOrFocused() ? SLOT_BTN_HOVERED : SLOT_BTN;
            g.blit(bg, getX(), getY(), 0, 0, 16, 16, 16, 16);
            g.blit(CRAFT_BTN, getX(), getY(), 0, 0, 16, 16, 16, 16);
        }
    }

    private static class ArrowBtn extends Button
    {
        private final float rotation;
        protected ArrowBtn(int x, int y, OnPress onPress, float rotation) {
            super(x, y, 16, 16, Component.empty(), onPress, DEFAULT_NARRATION);
            this.rotation = rotation;
        }
        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            isHovered = mx >= getX() && my >= getY() && mx < getX() + width && my < getY() + height;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            ResourceLocation bg = !active ? SLOT_BTN_DISABLED : isHoveredOrFocused() ? SLOT_BTN_HOVERED : SLOT_BTN;
            g.blit(bg, getX(), getY(), 0, 0, 16, 16, 16, 16);
            g.pose().pushPose();
            g.pose().translate(getX() + 8, getY() + 8, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
            g.blit(UP_ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
            g.pose().popPose();
        }
    }

    private void toggleDir(int idx)
    {
        if (menu.blockEntity == null) return;
        menu.blockEntity.toggleDirection(idx);
        menu.writeAndSendQuickData();
    }

    private void refreshRecipes()
    {
        var be = menu.blockEntity;
        if (be == null) return;
        var pattern = be.getPatternSlots();
        boolean hasAny = false;
        for (int i = 0; i < pattern.getSlots(); i++)
            if (!pattern.getStackBySlot(i).isEmpty()) { hasAny = true; break; }
        if (hasAny) { cachedRecipes = be.getAvailableRecipes(); recipeIndex = 0; }
        else { cachedRecipes = null; recipeIndex = 0; }
        hasSelection = false;
        updateResultSlot();
        updateButtons();
    }

    private void cycleRecipe(int delta)
    {
        if (cachedRecipes == null || cachedRecipes.isEmpty()) return;
        recipeIndex = ((recipeIndex + delta) % cachedRecipes.size() + cachedRecipes.size()) % cachedRecipes.size();
        hasSelection = false;
        updateResultSlot();
        updateButtons();
    }

    private void confirmRecipe()
    {
        if (cachedRecipes == null || cachedRecipes.isEmpty()) return;
        var opt = cachedRecipes.get(recipeIndex);
        menu.sendRecipeSelection(opt.id());
        hasSelection = true;
        updateButtons();
    }

    private void updateResultSlot()
    {
        ItemStack display = ItemStack.EMPTY;
        if (cachedRecipes != null && !cachedRecipes.isEmpty() && recipeIndex < cachedRecipes.size())
            display = cachedRecipes.get(recipeIndex).output();
        menu.resultSlot.set(display);
    }

    private void updateButtons()
    {
        boolean has = cachedRecipes != null && !cachedRecipes.isEmpty();
        prevBtn.active = has;
        nextBtn.active = has;
        okBtn.active = has && !hasSelection;
    }

    private boolean patternChanged()
    {
        var pattern = menu.blockEntity.getPatternSlots();
        int slots = pattern.getSlots();
        if (lastKnownPattern == null || lastKnownPattern.length != slots) return true;
        for (int i = 0; i < slots; i++)
        {
            var cur = pattern.getStackBySlot(i);
            var last = lastKnownPattern[i];
            if (cur.isEmpty() && last.isEmpty()) continue;
            if (cur.isEmpty() != last.isEmpty()) return true;
            if (cur.amount() != last.amount()) return true;
            if (!cur.key().isSameTypeSameComponents(last.key())) return true;
        }
        return false;
    }

    private void savePatternState()
    {
        var pattern = menu.blockEntity.getPatternSlots();
        int slots = pattern.getSlots();
        lastKnownPattern = new KeyAmount[slots];
        for (int i = 0; i < slots; i++)
            lastKnownPattern[i] = pattern.getStackBySlot(i);
    }

    @Override
    public void containerTick()
    {
        super.containerTick();
        if (menu.blockEntity != null)
        {
            if (outputModeButton.currentState != menu.blockEntity.getOutputMode())
                outputModeButton.setState(menu.blockEntity.getOutputMode());
            if (controlModeButton.currentState != menu.blockEntity.controlMode)
                controlModeButton.setState(menu.blockEntity.controlMode);

            for (int i = 0; i < dirButtons.length; i++)
                ((StyledBtn) dirButtons[i]).setToggled(menu.blockEntity.isDirectionEnabled(i));

            if (patternChanged())
            {
                savePatternState();
                refreshRecipes();
            }

            if (!initialRefreshDone)
            {
                initialRefreshDone = true;
                refreshRecipes();
                savePatternState();
            }
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int[] y = {topPos};
        CommonTexturesRender.renderTopBaseCommon(graphics, leftPos, y);
        PatternGridRenderer.render(graphics, leftPos, y[0], imageWidth);
        y[0] += PatternGridRenderer.HEIGHT;

        CommonTexturesRender.renderCommonConnection(graphics, leftPos, y);

        // 成品仓库
        for (int i = 0; i < STORAGE_ROWS; i++)
            CommonTexturesRender.renderCommonSlots(graphics, leftPos, y);

        CommonTexturesRender.renderCommonConnection(graphics, leftPos, y);
        CommonTexturesRender.renderPlayerInv(graphics, leftPos, y);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY)
    {
        var be = menu.blockEntity;
        if (be == null) return;

        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);

        // 运行状态 (右上角)
        CrafterStatus status = be.getStatus();
        String statusText;
        if (cachedRecipes != null && !cachedRecipes.isEmpty() && recipeIndex < cachedRecipes.size())
        {
            int bs = menu.blockEntity.getBatchSize();
            String itemName = cachedRecipes.get(recipeIndex).output().getHoverName().getString() + "x" + bs;
            String statusSuffix = switch (status) {
                case NO_PATTERN, INVALID_PATTERN -> "";
                case RECIPE_READY -> " §a已就绪";
                case WORKING -> " §a工作中...";
                case RESOURCE_BLOCKED -> " §c原料不足";
            };
            statusText = "产物: " + itemName + statusSuffix;
        }
        else
        {
            statusText = switch (status) {
                case NO_PATTERN -> "§7设定图案";
                case INVALID_PATTERN -> "§e图案无效";
                case RECIPE_READY -> "§7设定图案";
                case WORKING -> "§a工作中...";
                case RESOURCE_BLOCKED -> "§c原料不足";
            };
        }
        graphics.drawString(font, Component.literal(statusText), 169 - font.width(statusText), 8, 0, false);

        // 批量输入框前的 "x" 标签 (与输入框间隔2px, 垂直居中)
        graphics.drawString(font, "x", 128, 68, 0x404040, false);

        // 输出方向 — 相邻方块图标（渲染在按钮上）
        var level = minecraft.level;
        if (level != null && menu.blockEntity != null)
        {
            BlockPos pos = menu.blockEntity.getBlockPos();
            Direction[] dirs = Direction.values();
            int[] dirXs = {121, 137, 153};
            int[] dirYs = {24, 40};
            for (int idx = 0; idx < 6; idx++)
            {
                int col = idx % 3;
                int row = idx / 3;
                Direction dir = dirs[idx];
                BlockPos adjPos = pos.relative(dir);
                var state = level.getBlockState(adjPos);
                var target = level.getBlockEntity(adjPos);
                boolean valid = target != null
                        && target.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).isPresent();
                int x = dirXs[col];
                int y = dirYs[row];
                var stack = state.getBlock().asItem().getDefaultInstance();
                if (!stack.isEmpty())
                {
                    var p = graphics.pose();
                    p.pushPose();
                    p.translate(x + 1, y + 1, 1);
                    p.scale(0.85f, 0.85f, 1);
                    graphics.renderFakeItem(stack, 0, 0);
                    p.popPose();
                    graphics.fill(x, y, x + 16, y + 16, valid ? 0x3000AA00 : 0x20AA0000);
                }
            }
        }
    }
}
