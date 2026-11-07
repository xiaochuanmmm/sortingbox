package com.example.sortingbox.client;

import com.example.sortingbox.util.CategoryConfig;
import com.example.sortingbox.util.SortCategory;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Modern category editor built on YACL (Yet Another Config Lib): one tab per
 * category with name/item/tag fields, a delete button, plus an "add category" tab.
 */
public final class SortingBoxYaclScreen {
    private SortingBoxYaclScreen() {
    }

    public static Screen create(Screen parent) {
        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                .title(Component.literal("分类箱配置"));

        Map<String, String[]> buffers = new LinkedHashMap<>();

        for (SortCategory category : CategoryConfig.CATEGORIES) {
            String[] buffer = new String[]{
                    category.name(),
                    category.items().stream().map(Identifier::toString).collect(Collectors.joining(", ")),
                    category.tags().stream().map(Identifier::toString).collect(Collectors.joining(", "))
            };
            buffers.put(category.id(), buffer);

            OptionGroup group = OptionGroup.createBuilder()
                    .name(Component.literal("分类内容"))
                    .option(stringOption("显示名称", buffer, 0))
                    .option(stringOption("物品列表（逗号分隔，可省略 minecraft:）", buffer, 1))
                    .option(ButtonOption.createBuilder()
                            .name(Component.literal("可视化选择物品"))
                            .text(Component.literal("打开物品选择"))
                            .action(screen -> Minecraft.getInstance().setScreenAndShow(
                                    new ItemPickerScreen(screen, parseIds(buffer[1]),
                                            ids -> buffer[1] = ids.stream()
                                                    .map(Identifier::toString)
                                                    .collect(Collectors.joining(", ")))))
                            .build())
                    .option(stringOption("标签列表（逗号分隔）", buffer, 2))
                    .option(ButtonOption.createBuilder()
                            .name(Component.literal("删除此分类"))
                            .text(Component.literal("删除"))
                            .action(screen -> {
                                CategoryConfig.removeCategory(category.id());
                                Minecraft.getInstance().setScreenAndShow(create(parent));
                            })
                            .build())
                    .collapsed(false)
                    .build();

            builder.category(ConfigCategory.createBuilder()
                    .name(Component.literal(category.name()))
                    .group(group)
                    .build());
        }

        // Add-category tab
        String[] newBuffer = new String[]{"custom_" + (CategoryConfig.CATEGORIES.size() + 1), "新分类", "", ""};
        OptionGroup addGroup = OptionGroup.createBuilder()
                .name(Component.literal("新分类"))
                .option(stringOption("分类 id", newBuffer, 0))
                .option(stringOption("显示名称", newBuffer, 1))
                .option(stringOption("物品列表（逗号分隔）", newBuffer, 2))
                .option(stringOption("标签列表（逗号分隔）", newBuffer, 3))
                .option(ButtonOption.createBuilder()
                        .name(Component.literal("创建分类"))
                        .text(Component.literal("创建"))
                        .action(screen -> {
                            String id = newBuffer[0].trim();
                            if (id.isEmpty() || CategoryConfig.byId(id) != null) {
                                return;
                            }
                            CategoryConfig.addCategory(new SortCategory(
                                    id, newBuffer[1], parseIds(newBuffer[2]), parseIds(newBuffer[3])));
                            Minecraft.getInstance().setScreenAndShow(create(parent));
                        })
                        .build())
                .build();
        builder.category(ConfigCategory.createBuilder()
                .name(Component.literal("新增分类"))
                .group(addGroup)
                .build());

        builder.save(() -> {
            for (Map.Entry<String, String[]> entry : buffers.entrySet()) {
                String[] buffer = entry.getValue();
                CategoryConfig.replaceCategory(entry.getKey(),
                        new SortCategory(entry.getKey(), buffer[0], parseIds(buffer[1]), parseIds(buffer[2])));
            }
        });

        return builder.build().generateScreen(parent);
    }

    private static Option<String> stringOption(String label, String[] buffer, int index) {
        return Option.<String>createBuilder()
                .name(Component.literal(label))
                .binding(buffer[index], () -> buffer[index], value -> buffer[index] = value)
                .controller(StringControllerBuilder::create)
                .build();
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
}