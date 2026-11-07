package com.example.sortingbox.util;

import com.example.sortingbox.block.SortingBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Distributes items from a sorting box into the containers that were explicitly tagged
 * with labels. Because only tagged positions are visited, the reach is effectively
 * unlimited (labels can be placed up to 128 blocks away) without scanning the area.
 */
public final class SortingService {
    /**
     * Informational reach used by tooltips/UI: labels may be placed this far away.
     */
    public static final int RADIUS_H = 128;

    private SortingService() {
    }

    public static void distribute(SortingBoxBlockEntity box) {
        if (!(box.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // LinkedHashMap keeps the order in which containers were tagged, so the first
        // tagged container is always served first.
        Map<BlockPos, String> labels = box.getLabels();
        if (labels.isEmpty()) {
            return;
        }

        Map<BlockPos, Container> containers = new HashMap<>();
        for (BlockPos pos : labels.keySet()) {
            Container container = containerAt(level, pos);
            if (container != null) {
                containers.put(pos, container);
            }
        }
        if (containers.isEmpty()) {
            return;
        }

        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack stack = box.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            String category = CategoryConfig.match(stack);

            // Priority 1: a tagged container that already holds this exact item and
            // still has room for more.
            Container destination = null;
            for (Map.Entry<BlockPos, String> entry : labels.entrySet()) {
                Container candidate = containers.get(entry.getKey());
                if (candidate != null && containsSameItem(candidate, stack) && canAccept(candidate, stack)) {
                    destination = candidate;
                    break;
                }
            }

            // Priority 2: a tagged container whose category matches the item.
            if (destination == null) {
                for (Map.Entry<BlockPos, String> entry : labels.entrySet()) {
                    if (category.equals(entry.getValue())) {
                        Container candidate = containers.get(entry.getKey());
                        if (candidate != null && canAccept(candidate, stack)) {
                            destination = candidate;
                            break;
                        }
                    }
                }
            }

            if (destination != null) {
                moveInto(destination, stack);
                box.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
        }
    }

    private static boolean containsSameItem(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (ItemStack.isSameItemSameComponents(container.getItem(i), stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canAccept(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < Math.min(slot.getMaxStackSize(), stack.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    private static Container containerAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            // Double chests are returned as one 54-slot compound container.
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        return level.getBlockEntity(pos) instanceof Container c ? c : null;
    }

    /**
     * Returns the other half of a double chest at {@code pos}, or null.
     */
    public static BlockPos otherHalf(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock
                && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE)
                != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            return ChestBlock.getConnectedBlockPos(pos, state);
        }
        return null;
    }

    private static void moveInto(Container target, ItemStack stack) {
        for (int i = 0; i < target.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = target.getItem(i);
            if (slot.isEmpty()) {
                target.setItem(i, stack.copyWithCount(stack.getCount()));
                stack.setCount(0);
            } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
                int space = slot.getMaxStackSize() - slot.getCount();
                if (space > 0) {
                    int moved = Math.min(space, stack.getCount());
                    slot.grow(moved);
                    stack.shrink(moved);
                    target.setItem(i, slot);
                }
            }
        }
        target.setChanged();
    }
}