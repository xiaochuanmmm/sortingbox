package com.example.sortingbox;

import com.example.sortingbox.block.SortingBoxBlock;
import com.example.sortingbox.block.SortingBoxBlockEntity;
import com.example.sortingbox.block.SortingWorkbenchBlock;
import com.example.sortingbox.block.SortingWorkbenchBlockEntity;
import com.example.sortingbox.block.SortingWorkbenchMenu;
import com.example.sortingbox.compat.RoadSignCompat;
import com.example.sortingbox.component.SortingBoxComponents;
import com.example.sortingbox.item.LabelItem;
import com.example.sortingbox.network.LabelsPayload;
import com.example.sortingbox.util.CategoryConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class SortingBoxMod implements ModInitializer {
    public static final String MOD_ID = "sortingbox";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Block SORTING_BOX;
    public static BlockEntityType<SortingBoxBlockEntity> SORTING_BOX_ENTITY;
    public static Item SORTING_BOX_ITEM;
    public static LabelItem LABEL_ITEM;
    public static Block SORTING_WORKBENCH;
    public static BlockEntityType<SortingWorkbenchBlockEntity> SORTING_WORKBENCH_ENTITY;
    public static Item SORTING_WORKBENCH_ITEM;
    public static MenuType<SortingWorkbenchMenu> SORTING_WORKBENCH_MENU;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Registers the block/item content. Called from the Bootstrap mixin before the
     * built-in registries are frozen (26.3 freezes them inside Bootstrap.bootStrap).
     * Labels are generated from config/sortingbox/categories.json.
     */
    public static synchronized void registerContent() {
        if (SORTING_BOX != null) {
            return;
        }

        CategoryConfig.load();

        SORTING_BOX = new SortingBoxBlock(
                BlockBehaviour.Properties.of()
                        .strength(2.5f)
                        .setId(ResourceKey.create(Registries.BLOCK, id("sorting_box"))));

        SORTING_BOX_ENTITY = new BlockEntityType<>(SortingBoxBlockEntity::new, Set.of(SORTING_BOX));

        SORTING_BOX_ITEM = new BlockItem(SORTING_BOX,
                new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("sorting_box"))));

        Registry.register(BuiltInRegistries.BLOCK, id("sorting_box"), SORTING_BOX);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("sorting_box"), SORTING_BOX_ENTITY);
        Registry.register(BuiltInRegistries.ITEM, id("sorting_box"), SORTING_BOX_ITEM);

        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("category"), SortingBoxComponents.CATEGORY);

        // One universal label item; the chosen category lives in a data component,
        // so new categories can be added at runtime without new items.
        LABEL_ITEM = new LabelItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("label"))));
        Registry.register(BuiltInRegistries.ITEM, id("label"), LABEL_ITEM);

        // Furnace-like work block: material + universal label -> category-tagged label.
        SORTING_WORKBENCH = new SortingWorkbenchBlock(BlockBehaviour.Properties.of()
                .strength(2.5f)
                .setId(ResourceKey.create(Registries.BLOCK, id("sorting_workbench"))));
        SORTING_WORKBENCH_ENTITY = new BlockEntityType<>(SortingWorkbenchBlockEntity::new,
                Set.of(SORTING_WORKBENCH));
        SORTING_WORKBENCH_ITEM = new BlockItem(SORTING_WORKBENCH,
                new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("sorting_workbench"))));
        Registry.register(BuiltInRegistries.BLOCK, id("sorting_workbench"), SORTING_WORKBENCH);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("sorting_workbench"), SORTING_WORKBENCH_ENTITY);
        Registry.register(BuiltInRegistries.ITEM, id("sorting_workbench"), SORTING_WORKBENCH_ITEM);
        SORTING_WORKBENCH_MENU = new ExtendedMenuType<SortingWorkbenchMenu, Unit>(
                (containerId, inventory, data) -> new SortingWorkbenchMenu(containerId, inventory),
                StreamCodec.unit(Unit.INSTANCE));
        Registry.register(BuiltInRegistries.MENU, id("sorting_workbench"), SORTING_WORKBENCH_MENU);
        BlockState workbenchState = SORTING_WORKBENCH.defaultBlockState();
        if (Block.BLOCK_STATE_REGISTRY.getId(workbenchState) == -1) {
            Block.BLOCK_STATE_REGISTRY.add(workbenchState);
            workbenchState.initCache();
        }

        // 26.3 registers block states into Block.BLOCK_STATE_REGISTRY from the Blocks
        // class initializer. Blocks created outside that pass (like ours, registered
        // before BuiltInRegistries.bootStrap) are missing there, which makes the
        // server fail to encode clientbound/minecraft:block_update packets.
        BlockState state = SORTING_BOX.defaultBlockState();
        if (Block.BLOCK_STATE_REGISTRY.getId(state) == -1) {
            Block.BLOCK_STATE_REGISTRY.add(state);
            state.initCache();
        }

        LOGGER.info("Sorting Box content registered ({} categories)", CategoryConfig.CATEGORIES.size());
    }

    @Override
    public void onInitialize() {
        registerContent();
        PayloadTypeRegistry.clientboundPlay().register(LabelsPayload.TYPE, LabelsPayload.CODEC);

        // Labels follow the container: when a labeled container is broken, drop the
        // label from every nearby sorting box immediately (a new block placed on the
        // same spot starts unlabeled).
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level.isClientSide()) {
                return;
            }
            // Breaking a category sign also removes the category of the container behind it.
            BlockPos signedContainer = RoadSignCompat.containerBehind(level, pos, state);
            if (signedContainer != null) {
                for (BlockPos p : BlockPos.betweenClosed(
                        signedContainer.offset(-32, -16, -32), signedContainer.offset(32, 16, 32))) {
                    if (level.getBlockEntity(p) instanceof SortingBoxBlockEntity box
                            && box.getLabels().containsKey(signedContainer)) {
                        box.removeLabelAndNotify(signedContainer);
                        break;
                    }
                }
                return;
            }
            if (blockEntity instanceof SortingBoxBlockEntity
                    || !(blockEntity instanceof Container)) {
                return;
            }
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-16, -8, -16), pos.offset(16, 8, 16))) {
                if (level.getBlockEntity(p) instanceof SortingBoxBlockEntity box
                        && box.getLabels().containsKey(pos)) {
                    box.removeLabelAndNotify(pos);
                }
            }
        });

        // Re-send every known label around the player when they join, so the client
        // cache survives leaving and re-entering the world.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            ServerLevel level = (ServerLevel) player.level();
            BlockPos center = player.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(
                    center.offset(-16, -8, -16), center.offset(16, 8, 16))) {
                if (level.getBlockEntity(p) instanceof SortingBoxBlockEntity box) {
                    for (var entry : box.getLabels().entrySet()) {
                        ServerPlayNetworking.send(player, new LabelsPayload(
                                p.immutable(), entry.getKey(), entry.getValue(), false));
                    }
                }
            }
        });

        LOGGER.info("Sorting Box initialized");
    }
}