package com.example.sortingbox.client;

import com.example.sortingbox.util.CategoryConfig;
import com.example.sortingbox.util.SortCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

/**
 * Lists all categories; each row can be edited or deleted.
 */
public class CategoryListScreen extends Screen {
    private final Screen parent;

    public CategoryListScreen(Screen parent) {
        super(Component.literal("分类箱分类"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = 32;
        for (SortCategory category : new ArrayList<>(CategoryConfig.CATEGORIES)) {
            addRenderableWidget(Button.builder(Component.literal("编辑"), b ->
                            Minecraft.getInstance().setScreenAndShow(new CategoryEditScreen(this, category)))
                    .bounds(this.width - 130, y, 60, 18).build());
            addRenderableWidget(Button.builder(Component.literal("删除"), b -> {
                CategoryConfig.removeCategory(category.id());
                Minecraft.getInstance().setScreenAndShow(new CategoryListScreen(parent));
            }).bounds(this.width - 66, y, 60, 18).build());
            y += 22;
        }
        addRenderableWidget(Button.builder(Component.literal("新增分类"), b ->
                        Minecraft.getInstance().setScreenAndShow(new CategoryEditScreen(this, null)))
                .bounds(this.width / 2 - 150, this.height - 28, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复默认"), b -> {
            CategoryConfig.resetToDefaults();
            CategoryConfig.load();
            Minecraft.getInstance().setScreenAndShow(new CategoryListScreen(parent));
        }).bounds(this.width / 2 - 45, this.height - 28, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(this.width / 2 + 60, this.height - 28, 90, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, "分类箱分类", this.width / 2, 14, 0xFFFFFF);
        int y = 36;
        for (SortCategory category : CategoryConfig.CATEGORIES) {
            if (y > this.height - 40) {
                break;
            }
            graphics.text(this.font, category.name() + "  (" + category.id() + ")",
                    20, y + 4, 0xFFFFFF);
            y += 22;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }
}