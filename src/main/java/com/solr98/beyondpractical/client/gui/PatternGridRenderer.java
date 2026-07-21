package com.solr98.beyondpractical.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * PSD 布局 (更新后):
 *   pattern_row ×3 (y=24,42,60) 全宽铺满
 *   3×3 网格 (x=7, y=24)
 *   产物结果 (x=86, y=42)
 *   箭头 (x=62, y=20)
 *   ◄ ✓ ► (x=71,87,103, y=60)
 *   输出方向 2×3 按钮 (x=121-153, y=24,40)
 */
public class PatternGridRenderer
{
    private static final ResourceLocation ROW_TEX =
            ResourceLocation.tryParse("beyond_practical:textures/gui/pattern_row.png");
    private static final ResourceLocation SLOT_TEX =
            ResourceLocation.tryParse("beyond_practical:textures/gui/pattern_slot.png");
    private static final ResourceLocation ARROW_TEX =
            ResourceLocation.tryParse("beyond_practical:textures/gui/pattern_arrow.png");

    public static final int ROWS = 3;
    public static final int COLS = 3;
    public static final int SLOT_SIZE = 18;
    public static final int WIDTH = COLS * SLOT_SIZE;
    public static final int HEIGHT = ROWS * SLOT_SIZE; // 3 行 pattern_row
    public static final int SLOT_X = 7;

    // 3×3 网格起始行 (第1行 pattern_row)
    public static final int GRID_Y = 0;
    public static final int RESULT_X = 86;
    public static final int RESULT_Y = GRID_Y + SLOT_SIZE;

    public static void render(GuiGraphics graphics, int areaX, int areaY, int areaWidth)
    {
        // 3 行 pattern_row 全宽
        for (int row = 0; row < 3; row++)
            graphics.blit(ROW_TEX, areaX, areaY + row * SLOT_SIZE,
                    0, 0, areaWidth, SLOT_SIZE, 176, SLOT_SIZE);

        // 3×3 网格槽纹理 (从 GRID_Y 开始, 3 行)
        for (int row = 0; row < ROWS; row++)
            for (int col = 0; col < COLS; col++)
                graphics.blit(SLOT_TEX,
                        areaX + SLOT_X + col * SLOT_SIZE,
                        areaY + GRID_Y + row * SLOT_SIZE,
                        0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);

        // 产物结果槽
        graphics.blit(SLOT_TEX, areaX + RESULT_X, areaY + RESULT_Y,
                0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);

        // 箭头: 网格右边缘 → 结果槽 (从 PSD 截取)
        graphics.blit(ARROW_TEX, areaX + 62, areaY + 20, 0, 0, 22, 15, 22, 15);
    }

    /** 3×3 网格槽位 X */
    public static int getSlotX(int col) { return SLOT_X + col * SLOT_SIZE; }
    /** 3×3 网格槽位 Y (相对 pattern 区域) */
    public static int getSlotY(int row) { return GRID_Y + row * SLOT_SIZE; }
}
