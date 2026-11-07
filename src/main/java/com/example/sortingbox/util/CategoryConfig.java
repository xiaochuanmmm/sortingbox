package com.example.sortingbox.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the user-editable category table from
 * {@code config/sortingbox/categories.json} (created with sensible defaults on
 * first launch). Categories are matched in file order; unmatched items fall back to
 * {@code misc}.
 */
public final class CategoryConfig {
    private static final String FILE_NAME = "categories.json";
    private static final String FALLBACK_ID = "misc";
    /** Bump this whenever the built-in default categories change. */
    private static final int CURRENT_VERSION = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final List<SortCategory> CATEGORIES = new ArrayList<>();
    private static final Map<String, SortCategory> BY_ID = new ConcurrentHashMap<>();

    private CategoryConfig() {
    }

    public static SortCategory byId(String id) {
        return BY_ID.get(id);
    }

    /**
     * Returns the category id for an item stack.
     */
    public static String match(ItemStack stack) {
        for (SortCategory category : CATEGORIES) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null && category.items().contains(itemId)) {
                return category.id();
            }
            for (Identifier tagId : category.tags()) {
                if (stack.is(TagKey.create(Registries.ITEM, tagId))) {
                    return category.id();
                }
            }
        }
        return FALLBACK_ID;
    }

    /**
     * Reads the config (creating or upgrading it when needed) and fills
     * {@link #CATEGORIES}. Old configs without a version are replaced by the new
     * defaults; versioned configs keep user-defined categories (ids that do not
     * exist in the defaults) while built-in categories are refreshed.
     */
    public static void load() {
        Path file;
        try {
            file = FabricLoader.getInstance().getConfigDir().resolve("sortingbox").resolve(FILE_NAME);
        } catch (Throwable throwable) {
            file = Path.of("config", "sortingbox", FILE_NAME);
        }
        JsonObject defaults = defaultJson();
        JsonObject existing = null;
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                existing = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (IOException | RuntimeException ignored) {
        }

        JsonObject source;
        if (existing == null) {
            source = defaults;
            write(file, defaults);
        } else {
            int version = existing.has("configVersion") ? existing.get("configVersion").getAsInt() : 0;
            if (version >= CURRENT_VERSION) {
                source = existing;
            } else if (version == 0) {
                source = defaults;
                write(file, defaults);
            } else {
                source = merge(existing, defaults);
                write(file, source);
            }
        }
        parse(source);
    }

    /**
     * Keeps user-defined categories (ids absent from the defaults) and refreshes all
     * built-in categories from the new defaults.
     */
    private static JsonObject merge(JsonObject existing, JsonObject defaults) {
        Set<String> defaultIds = new LinkedHashSet<>();
        JsonArray defaultCategories = defaults.getAsJsonArray("categories");
        for (JsonElement element : defaultCategories) {
            defaultIds.add(element.getAsJsonObject().get("id").getAsString());
        }
        JsonArray merged = new JsonArray();
        if (existing.has("categories")) {
            for (JsonElement element : existing.getAsJsonArray("categories")) {
                String id = element.getAsJsonObject().get("id").getAsString();
                if (!defaultIds.contains(id)) {
                    merged.add(element);
                }
            }
        }
        for (JsonElement element : defaultCategories) {
            merged.add(element);
        }
        JsonObject result = new JsonObject();
        result.addProperty("comment", defaults.get("comment").getAsString());
        result.addProperty("configVersion", CURRENT_VERSION);
        result.add("categories", merged);
        return result;
    }

    private static void write(Path file, JsonObject json) {
        try {
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /**
     * Deletes the config file so the next {@link #load()} regenerates the defaults.
     */
    public static void resetToDefaults() {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("sortingbox").resolve(FILE_NAME);
            Files.deleteIfExists(file);
        } catch (IOException | RuntimeException ignored) {
        }
    }

    /**
     * Writes the current {@link #CATEGORIES} table back to the config file.
     */
    public static void save() {
        Path file;
        try {
            file = FabricLoader.getInstance().getConfigDir().resolve("sortingbox").resolve(FILE_NAME);
        } catch (Throwable throwable) {
            file = Path.of("config", "sortingbox", FILE_NAME);
        }
        JsonObject root = new JsonObject();
        root.addProperty("configVersion", CURRENT_VERSION);
        root.addProperty("comment",
                "编辑本文件后重启游戏生效（或使用游戏内的配置界面）。items/tags 写完整 ID，可省略 minecraft: 前缀。分类按顺序匹配，未匹配的物品归入 misc。");
        JsonArray array = new JsonArray();
        for (SortCategory category : CATEGORIES) {
            JsonObject object = new JsonObject();
            object.addProperty("id", category.id());
            object.addProperty("name", category.name());
            JsonArray items = new JsonArray();
            category.items().forEach(item -> items.add(item.toString()));
            object.add("items", items);
            JsonArray tags = new JsonArray();
            category.tags().forEach(tag -> tags.add(tag.toString()));
            object.add("tags", tags);
            array.add(object);
        }
        root.add("categories", array);
        write(file, root);
    }

    /**
     * Replaces (or appends) a category, preserving its position, then saves.
     */
    public static void replaceCategory(String oldId, SortCategory updated) {
        int index = -1;
        for (int i = 0; i < CATEGORIES.size(); i++) {
            if (CATEGORIES.get(i).id().equals(oldId)) {
                index = i;
                break;
            }
        }
        CATEGORIES.removeIf(category -> category.id().equals(oldId));
        BY_ID.remove(oldId);
        if (index >= 0 && index <= CATEGORIES.size()) {
            CATEGORIES.add(index, updated);
        } else {
            CATEGORIES.add(updated);
        }
        BY_ID.put(updated.id(), updated);
        save();
    }

    /**
     * Adds a brand new category and saves the config.
     */
    public static void addCategory(SortCategory category) {
        CATEGORIES.add(category);
        BY_ID.put(category.id(), category);
        save();
    }

    public static void removeCategory(String id) {
        CATEGORIES.removeIf(category -> category.id().equals(id));
        BY_ID.remove(id);
        save();
    }

    private static void parse(JsonObject root) {
        CATEGORIES.clear();
        BY_ID.clear();
        JsonArray array = root.has("categories") ? root.getAsJsonArray("categories") : new JsonArray();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            String id = object.has("id") ? object.get("id").getAsString() : null;
            if (id == null || id.isBlank() || !seen.add(id)) {
                continue;
            }
            String name = object.has("name") ? object.get("name").getAsString() : id;
            Set<Identifier> items = new HashSet<>();
            if (object.has("items")) {
                for (JsonElement item : object.getAsJsonArray("items")) {
                    Identifier identifier = parseId(item.getAsString());
                    if (identifier != null) {
                        items.add(identifier);
                    }
                }
            }
            Set<Identifier> tags = new HashSet<>();
            if (object.has("tags")) {
                for (JsonElement tag : object.getAsJsonArray("tags")) {
                    Identifier identifier = parseId(tag.getAsString());
                    if (identifier != null) {
                        tags.add(identifier);
                    }
                }
            }
            SortCategory category = new SortCategory(id, name, items, tags);
            CATEGORIES.add(category);
            BY_ID.put(id, category);
        }
        if (!BY_ID.containsKey(FALLBACK_ID)) {
            SortCategory misc = new SortCategory(FALLBACK_ID, "其他", Set.of(), Set.of());
            CATEGORIES.add(misc);
            BY_ID.put(FALLBACK_ID, misc);
        }
    }

    private static Identifier parseId(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.contains(":")) {
            value = "minecraft:" + value;
        }
        return Identifier.tryParse(value);
    }

    private static JsonObject defaultJson() {
        JsonObject root = new JsonObject();
        root.addProperty("configVersion", CURRENT_VERSION);
        root.addProperty("comment",
                "编辑本文件后重启游戏生效。items/tags 写完整 ID，可省略 minecraft: 前缀。分类按顺序匹配，未匹配的物品归入 misc。"
                        + " 内置分类会随模组更新自动刷新；自定义分类（id 不与内置重复）会保留。");
        JsonArray categories = new JsonArray();

        add(categories, "wood", "木类",
                new String[]{"bamboo_block", "bamboo_mosaic", "bamboo_mosaic_slab", "bamboo_mosaic_stairs",
                        "stripped_oak_log", "stripped_spruce_log", "stripped_birch_log", "stripped_jungle_log",
                        "stripped_acacia_log", "stripped_dark_oak_log", "stripped_mangrove_log",
                        "stripped_cherry_log", "stripped_pale_oak_log"},
                new String[]{"minecraft:logs", "minecraft:planks", "minecraft:wooden_slabs",
                        "minecraft:wooden_stairs", "minecraft:wooden_fences", "minecraft:wooden_fence_gates",
                        "minecraft:wooden_doors", "minecraft:wooden_trapdoors", "minecraft:wooden_buttons",
                        "minecraft:wooden_pressure_plates", "minecraft:bamboo_blocks",
                        "minecraft:signs", "minecraft:boats", "minecraft:hanging_signs"});

        add(categories, "stone", "石类",
                new String[]{"cobblestone", "mossy_cobblestone", "stone", "granite", "polished_granite",
                        "diorite", "polished_diorite", "andesite", "polished_andesite", "deepslate",
                        "cobbled_deepslate", "polished_deepslate", "tuff", "polished_tuff", "calcite",
                        "sandstone", "red_sandstone", "smooth_stone", "smooth_sandstone", "netherrack",
                        "basalt", "smooth_basalt", "blackstone", "polished_blackstone", "end_stone",
                        "quartz_block", "purpur_block", "prismarine", "prismarine_bricks", "dark_prismarine",
                        "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks",
                        "bricks", "nether_bricks", "red_nether_bricks", "mud_bricks", "deepslate_bricks",
                        "deepslate_tiles", "polished_blackstone_bricks"},
                new String[]{"minecraft:stone_bricks"});

        add(categories, "ore", "矿物",
                new String[]{"coal", "charcoal", "diamond", "emerald", "lapis_lazuli", "redstone", "quartz",
                        "amethyst_shard", "netherite_scrap", "netherite_ingot", "iron_ingot", "gold_ingot",
                        "copper_ingot", "iron_nugget", "gold_nugget", "raw_iron", "raw_gold", "raw_copper",
                        "iron_block", "gold_block", "copper_block", "diamond_block", "emerald_block",
                        "lapis_block", "redstone_block", "coal_block", "amethyst_block", "netherite_block"},
                new String[]{"c:ores", "c:raw_materials", "c:ingots", "c:nuggets", "c:gems"});

        add(categories, "decoration", "装饰",
                new String[]{"painting", "item_frame", "glow_item_frame", "flower_pot", "armor_stand",
                        "candle", "white_candle", "orange_candle", "magenta_candle", "light_blue_candle",
                        "yellow_candle", "lime_candle", "pink_candle", "gray_candle", "light_gray_candle",
                        "cyan_candle", "purple_candle", "blue_candle", "brown_candle", "green_candle",
                        "red_candle", "black_candle", "lantern", "soul_lantern", "torch", "soul_torch",
                        "sea_lantern", "shroomlight", "glowstone", "redstone_lamp", "scaffolding",
                        "white_banner", "black_banner", "red_banner", "blue_banner", "green_banner"},
                new String[]{"minecraft:candles", "minecraft:banners", "minecraft:wool",
                        "minecraft:carpets", "minecraft:concrete", "minecraft:concrete_powders",
                        "minecraft:beds", "minecraft:signs", "minecraft:bars", "minecraft:chains",
                        "minecraft:lanterns", "minecraft:walls"});

        add(categories, "plant", "植物",
                new String[]{"grass_block", "short_grass", "fern", "dead_bush", "brown_mushroom",
                        "red_mushroom", "sugar_cane", "cactus", "kelp", "seagrass", "bamboo", "vine",
                        "lily_pad", "nether_wart", "wheat_seeds", "beetroot_seeds", "melon_seeds",
                        "pumpkin_seeds", "cocoa_beans", "pumpkin", "melon", "big_dripleaf", "small_dripleaf",
                        "moss_block", "azalea", "flowering_azalea"},
                new String[]{"minecraft:flowers", "minecraft:saplings", "minecraft:crops",
                        "minecraft:leaves", "minecraft:small_flowers", "minecraft:tall_flowers"});

        add(categories, "functional", "功能物品",
                new String[]{"crafting_table", "furnace", "blast_furnace", "smoker", "chest", "trapped_chest",
                        "barrel", "ender_chest", "bucket", "water_bucket", "lava_bucket", "milk_bucket",
                        "hopper", "dropper", "dispenser", "anvil", "chipped_anvil", "damaged_anvil",
                        "enchanting_table", "brewing_stand", "cauldron", "ladder", "composter",
                        "smithing_table", "cartography_table", "fletching_table", "loom", "stonecutter",
                        "grindstone", "beacon", "conduit", "jukebox", "bookshelf", "chiseled_bookshelf",
                        "campfire", "soul_campfire", "bell", "lectern", "barrel", "respawn_anchor",
                        "lodestone", "crafter", "trial_spawner", "vault"},
                new String[]{"minecraft:beds"});

        add(categories, "redstone", "红石",
                new String[]{"redstone", "redstone_torch", "redstone_lamp", "repeater", "comparator",
                        "piston", "sticky_piston", "observer", "daylight_detector", "target", "lever",
                        "tripwire_hook", "tripwire", "note_block", "redstone_block", "sculk_sensor",
                        "calibrated_sculk_sensor", "lightning_rod", "stone_button", "stone_pressure_plate",
                        "heavy_weighted_pressure_plate", "light_weighted_pressure_plate", "rail",
                        "powered_rail", "detector_rail", "activator_rail", "minecart", "tnt"},
                new String[]{"c:redstone", "minecraft:buttons", "minecraft:pressure_plates"});

        add(categories, "equipment", "装备",
                new String[]{"leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots",
                        "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots",
                        "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots",
                        "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots",
                        "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots",
                        "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
                        "turtle_helmet", "shield", "elytra", "iron_horse_armor", "golden_horse_armor",
                        "diamond_horse_armor", "leather_horse_armor", "wolf_armor"},
                new String[]{"minecraft:trimmable_armor", "minecraft:armor", "minecraft:chest_armor"});

        add(categories, "weapon", "武器",
                new String[]{"wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword",
                        "netherite_sword", "bow", "crossbow", "trident", "arrow", "spectral_arrow",
                        "tipped_arrow", "snowball", "egg"},
                new String[]{"minecraft:swords", "minecraft:arrows", "minecraft:bow", "minecraft:crossbow"});

        add(categories, "tool", "工具",
                new String[]{"wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "golden_pickaxe",
                        "diamond_pickaxe", "netherite_pickaxe",
                        "wooden_axe", "stone_axe", "iron_axe", "golden_axe", "diamond_axe", "netherite_axe",
                        "wooden_shovel", "stone_shovel", "iron_shovel", "golden_shovel", "diamond_shovel",
                        "netherite_shovel",
                        "wooden_hoe", "stone_hoe", "iron_hoe", "golden_hoe", "diamond_hoe", "netherite_hoe",
                        "shears", "flint_and_steel", "fishing_rod", "compass", "clock", "map", "spyglass",
                        "brush", "carrot_on_a_stick", "warped_fungus_on_a_stick"},
                new String[]{"minecraft:pickaxes", "minecraft:axes", "minecraft:shovels", "minecraft:hoes"});

        add(categories, "enchanted_book", "附魔书",
                new String[]{"enchanted_book"},
                new String[]{});

        add(categories, "food", "食物",
                new String[]{"golden_apple", "enchanted_golden_apple", "golden_carrot", "bread",
                        "baked_potato", "pumpkin_pie", "cookie", "cake", "honey_bottle"},
                new String[]{"c:foods"});

        add(categories, "material", "原材料",
                new String[]{"string", "feather", "leather", "slime_ball", "gunpowder", "bone", "bone_meal",
                        "sugar", "paper", "book", "stick", "flint", "clay_ball", "clay", "snowball",
                        "glass_bottle", "blaze_rod", "blaze_powder", "ender_pearl", "ender_eye",
                        "ghast_tear", "phantom_membrane", "rabbit_hide", "rabbit_foot", "spider_eye",
                        "fermented_spider_eye", "magma_cream", "glistering_melon_slice", "nether_star",
                        "nautilus_shell", "heart_of_the_sea", "prismarine_shard", "prismarine_crystals",
                        "ink_sac", "glow_ink_sac", "honeycomb", "resin_clump", "resin_block",
                        "turtle_scute", "armadillo_scute", "breeze_rod", "wind_charge"},
                new String[]{"minecraft:coals"});

        add(categories, "misc", "其他",
                new String[]{},
                new String[]{});

        root.add("categories", categories);
        return root;
    }

    private static void add(JsonArray array, String id, String name, String[] items, String[] tags) {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("name", name);
        JsonArray itemArray = new JsonArray();
        for (String item : items) {
            itemArray.add(item);
        }
        JsonArray tagArray = new JsonArray();
        for (String tag : tags) {
            tagArray.add(tag);
        }
        object.add("items", itemArray);
        object.add("tags", tagArray);
        array.add(object);
    }
}