package com.example.sortingbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Visual item picker: an icon grid with search, paging and click-to-toggle selection.
 */
public class ItemPickerScreen extends Screen {
    private static final int CELL = 20;
    private static final int COLS = 10;
    private static final int ROWS = 8;

    private final Screen parent;
    private final Set<Identifier> selected;
    private final Consumer<Set<Identifier>> onDone;
    private final List<ItemStack> allItems;
    private String search = "";
    private int page;

    public ItemPickerScreen(Screen parent, Set<Identifier> selected, Consumer<Set<Identifier>> onDone) {
        super(Component.literal("选择物品"));
        this.parent = parent;
        this.selected = new HashSet<>(selected);
        this.onDone = onDone;
        this.allItems = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()) {
                allItems.add(stack);
            }
        }
    }

    @Override
    protected void init() {
        EditBox searchBox = new EditBox(this.font, 100, 20, 200, 20, Component.literal("搜索"));
        searchBox.setResponder(text -> {
            search = text.toLowerCase();
            page = 0;
        });
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("上一页"), b -> {
            if (page > 0) {
                page--;
            }
        }).bounds(10, this.height - 26, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("下一页"), b -> page++)
                .bounds(90, this.height - 26, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> {
            onDone.accept(selected);
            onClose();
        }).bounds(this.width / 2 - 40, this.height - 26, 80, 20).build());
    }

    private List<ItemStack> filtered() {
        if (search.isEmpty()) {
            return allItems;
        }
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : allItems) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && id.toString().contains(search)) {
                result.add(stack);
            }
        }
        return result;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        List<ItemStack> list = filtered();
        int start = page * COLS * ROWS;
        for (int i = 0; i < COLS * ROWS; i++) {
            int index = start + i;
            if (index >= list.size()) {
                break;
            }
            int col = i % COLS;
            int row = i / COLS;
            int x = 10 + col * CELL;
            int y = 60 + row * CELL;
            Identifier id = BuiltInRegistries.ITEM.getKey(list.get(index).getItem());
            if (id != null && selected.contains(id)) {
                graphics.fill(x, y, x + CELL, y + CELL, 0x8000FF00);
            }
            graphics.item(list.get(index), x + 2, y + 2);
        }
        graphics.text(this.font, "已选 " + selected.size() + " 个物品（点击图标切换）", 10, 42, 0xFFFFFF);
        graphics.text(this.font, "第 " + (page + 1) + " 页 / 共 " + Math.max(1, (list.size() + COLS * ROWS - 1) / (COLS * ROWS)) + " 页",
                10, this.height - 40, 0xAAAAAA);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        List<ItemStack> list = filtered();
        int start = page * COLS * ROWS;
        for (int i = 0; i < COLS * ROWS; i++) {
            int index = start + i;
            if (index >= list.size()) {
                break;
            }
            int col = i % COLS;
            int row = i / COLS;
            int x = 10 + col * CELL;
            int y = 60 + row * CELL;
            if (event.x() >= x && event.x() < x + CELL && event.y() >= y && event.y() < y + CELL) {
                Identifier id = BuiltInRegistries.ITEM.getKey(list.get(index).getItem());
                if (id != null) {
                    if (!selected.remove(id)) {
                        selected.add(id);
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }
}