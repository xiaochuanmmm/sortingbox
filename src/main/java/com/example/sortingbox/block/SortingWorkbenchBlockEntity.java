package com.example.sortingbox.block;

import com.example.sortingbox.SortingBoxMod;
import com.example.sortingbox.component.SortingBoxComponents;
import com.example.sortingbox.util.CategoryConfig;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Furnace-like work block. Slot 0 (top-left): category material, slot 9 (bottom-left):
 * universal label, slot 4 (right): result label carrying the material's category.
 */
public class SortingWorkbenchBlockEntity extends BlockEntity
        implements Container, MenuProvider, ExtendedMenuProvider<Unit> {
    public static final int SIZE = 27;
    public static final int SLOT_MATERIAL = 0;
    public static final int SLOT_LABEL = 9;
    public static final int SLOT_RESULT = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public SortingWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(SortingBoxMod.SORTING_WORKBENCH_ENTITY, pos, state);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = stack.split(amount);
        if (slot == SLOT_MATERIAL || slot == SLOT_LABEL) {
            updateOutput();
        }
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        if (slot == SLOT_MATERIAL || slot == SLOT_LABEL) {
            updateOutput();
        }
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (slot == SLOT_MATERIAL || slot == SLOT_LABEL) {
            updateOutput();
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < SIZE; i++) {
            items.set(i, ItemStack.EMPTY);
        }
    }

    private void updateOutput() {
        ItemStack material = items.get(SLOT_MATERIAL);
        ItemStack label = items.get(SLOT_LABEL);
        if (label.getItem() == SortingBoxMod.LABEL_ITEM && !material.isEmpty()) {
            ItemStack result = label.copyWithCount(1);
            result.set(SortingBoxComponents.CATEGORY, CategoryConfig.match(material));
            items.set(SLOT_RESULT, result);
        } else {
            items.set(SLOT_RESULT, ItemStack.EMPTY);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.sortingbox.sorting_workbench");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SortingWorkbenchMenu(containerId, inventory, this);
    }

    @Override
    public Unit getScreenOpeningData(ServerPlayer player) {
        return Unit.INSTANCE;
    }
}