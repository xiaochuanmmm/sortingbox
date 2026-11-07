package com.example.sortingbox.client;

import com.example.sortingbox.block.SortingWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Furnace-style screen for the sorting workbench. Draws a self-contained panel
 * (background, border, slot wells) with plain fills so it does not depend on
 * texture loading.
 */
public class SortingWorkbenchScreen extends AbstractContainerScreen<SortingWorkbenchMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int BORDER = 0xFF555555;
    private static final int SLOT_OUTER = 0xFF8B8B8B;
    private static final int SLOT_INNER = 0xFF373737;

    public SortingWorkbenchScreen(SortingWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x1 = this.leftPos;
        int y1 = this.topPos;
        int x2 = x1 + this.imageWidth;
        int y2 = y1 + this.imageHeight;

        // Panel
        graphics.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, BORDER);
        graphics.fill(x1, y1, x2, y2, PANEL);

        // Slot wells at the same positions as the menu slots
        drawSlot(graphics, x1 + 56, y1 + 17);
        drawSlot(graphics, x1 + 56, y1 + 53);
        drawSlot(graphics, x1 + 116, y1 + 31);

        // Player inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(graphics, x1 + 8 + col * 18, y1 + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(graphics, x1 + 8 + col * 18, y1 + 142);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_OUTER);
        graphics.fill(x, y, x + 16, y + 16, SLOT_INNER);
    }
}