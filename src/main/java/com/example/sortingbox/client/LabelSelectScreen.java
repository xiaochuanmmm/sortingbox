package com.example.sortingbox.client;

import com.example.sortingbox.component.SortingBoxComponents;
import com.example.sortingbox.util.CategoryConfig;
import com.example.sortingbox.util.SortCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * Choose which category the held universal label should carry.
 */
public class LabelSelectScreen extends Screen {
    private final Player player;
    private final InteractionHand hand;

    public LabelSelectScreen(Player player, InteractionHand hand) {
        super(Component.literal("选择标签分类"));
        this.player = player;
        this.hand = hand;
    }

    @Override
    protected void init() {
        int y = 40;
        for (SortCategory category : CategoryConfig.CATEGORIES) {
            if (y > this.height - 30) {
                break;
            }
            addRenderableWidget(Button.builder(Component.literal(category.name()), b -> {
                player.getItemInHand(hand).set(SortingBoxComponents.CATEGORY, category.id());
                Minecraft.getInstance().setScreenAndShow(null);
            }).bounds(this.width / 2 - 80, y, 160, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(this.width / 2 - 40, this.height - 28, 80, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, "选择标签分类", this.width / 2, 14, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}