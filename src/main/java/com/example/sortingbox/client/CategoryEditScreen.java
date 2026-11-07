package com.example.sortingbox.client;

import com.example.sortingbox.util.CategoryConfig;
import com.example.sortingbox.util.SortCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Edits one category (or creates a new one): id, display name and comma-separated
 * item/tag ids.
 */
public class CategoryEditScreen extends Screen {
    private final Screen parent;
    private final SortCategory original;
    private final boolean isNew;
    private EditBox idBox;
    private EditBox nameBox;
    private EditBox itemsBox;
    private EditBox tagsBox;

    public CategoryEditScreen(Screen parent, SortCategory category) {
        super(Component.literal("编辑分类"));
        this.parent = parent;
        this.original = category;
        this.isNew = category == null;
    }

    @Override
    protected void init() {
        int y = 40;
        if (isNew) {
            idBox = new EditBox(this.font, 100, y, 200, 20, Component.literal("分类 id"));
            idBox.setValue("custom_" + (CategoryConfig.CATEGORIES.size() + 1));
            addRenderableWidget(idBox);
            y += 36;
        }
        nameBox = new EditBox(this.font, 100, y, 200, 20, Component.literal("名称"));
        nameBox.setValue(isNew ? "" : original.name());
        addRenderableWidget(nameBox);
        y += 36;

        itemsBox = new EditBox(this.font, 100, y, this.width - 140, 20, Component.literal("物品 ID"));
        itemsBox.setValue(isNew ? "" :
                original.items().stream().map(Identifier::toString).collect(Collectors.joining(",")));
        addRenderableWidget(itemsBox);
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("从图标选择物品"), b ->
                        Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this,
                                parseIds(itemsBox.getValue()),
                                ids -> itemsBox.setValue(ids.stream()
                                        .map(Identifier::toString)
                                        .collect(Collectors.joining(","))))))
                .bounds(100, y, this.width - 140, 18).build());
        y += 30;

        tagsBox = new EditBox(this.font, 100, y, this.width - 140, 20, Component.literal("标签 ID"));
        tagsBox.setValue(isNew ? "" :
                original.tags().stream().map(Identifier::toString).collect(Collectors.joining(",")));
        addRenderableWidget(tagsBox);

        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(this.width / 2 - 100, y + 40, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(this.width / 2 + 10, y + 40, 90, 20).build());
    }

    private void save() {
        String id;
        if (isNew) {
            id = idBox.getValue().trim();
            if (id.isEmpty() || CategoryConfig.byId(id) != null) {
                return;
            }
        } else {
            id = original.id();
        }
        Set<Identifier> items = parseIds(itemsBox.getValue());
        Set<Identifier> tags = parseIds(tagsBox.getValue());
        SortCategory updated = new SortCategory(id, nameBox.getValue(), items, tags);
        if (isNew) {
            CategoryConfig.addCategory(updated);
        } else {
            CategoryConfig.replaceCategory(original.id(), updated);
        }
        Minecraft.getInstance().setScreenAndShow(new CategoryListScreen(parent));
    }

    private static Set<Identifier> parseIds(String text) {
        Set<Identifier> result = new HashSet<>();
        for (String part : text.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!value.contains(":")) {
                value = "minecraft:" + value;
            }
            Identifier id = Identifier.tryParse(value);
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, isNew ? "新增分类" : "编辑分类 " + original.id(),
                this.width / 2, 14, 0xFFFFFF);
        int y = 46;
        if (isNew) {
            graphics.text(this.font, "分类 id", 20, y, 0xFFFFFF);
            y += 36;
        }
        graphics.text(this.font, "名称", 20, y, 0xFFFFFF);
        y += 36;
        graphics.text(this.font, "物品 ID（逗号分隔，可省略 minecraft:）", 20, y, 0xFFFFFF);
        y += 36;
        graphics.text(this.font, "标签 ID（逗号分隔）", 20, y, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }
}