package com.example.sortingbox.client;

import com.example.sortingbox.util.CategoryConfig;
import com.example.sortingbox.util.SortCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * In-game view of the category table, opened from Mod Menu. Editing still happens in
 * the JSON file, but reload / reset / open-folder are one click away.
 */
public class SortingBoxConfigScreen extends Screen {
    private final Screen parent;

    public SortingBoxConfigScreen(Screen parent) {
        super(Component.literal("分类箱配置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = this.height - 30;
        int w = 120;
        int gap = 4;
        int total = w * 4 + gap * 3;
        int x = (this.width - total) / 2;
        addRenderableWidget(Button.builder(Component.literal("重新载入配置"), b -> CategoryConfig.load())
                .bounds(x, y, w, 20).build());
        x += w + gap;
        addRenderableWidget(Button.builder(Component.literal("恢复默认配置"), b -> {
            CategoryConfig.resetToDefaults();
            CategoryConfig.load();
        }).bounds(x, y, w, 20).build());
        x += w + gap;
        addRenderableWidget(Button.builder(Component.literal("打开配置文件夹"), b -> {
            try {
                Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("sortingbox");
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(dir.toFile());
                }
            } catch (Exception ignored) {
            }
        }).bounds(x, y, w, 20).build());
        x += w + gap;
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(x, y, w, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, "分类箱配置", this.width / 2, 16, 0xFFFFFF);
        graphics.centeredText(this.font, "编辑 config/sortingbox/categories.json 后点「重新载入配置」",
                this.width / 2, 32, 0xAAAAAA);
        int y = 56;
        for (SortCategory category : CategoryConfig.CATEGORIES) {
            if (y > this.height - 60) {
                break;
            }
            String line = String.format("%-16s %-10s 物品 %d / 标签 %d",
                    category.id(), category.name(), category.items().size(), category.tags().size());
            graphics.text(this.font, line, 24, y, 0xE0E0E0);
            y += 14;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }
}