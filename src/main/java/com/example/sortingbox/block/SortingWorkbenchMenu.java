package com.example.sortingbox.block;

import com.example.sortingbox.SortingBoxMod;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Furnace-like layout: material (top-left), universal label (bottom-left),
 * result (right), plus the player inventory.
 */
public class SortingWorkbenchMenu extends AbstractContainerMenu {
    private final net.minecraft.world.Container container;

    public SortingWorkbenchMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory,
                new net.minecraft.world.SimpleContainer(SortingWorkbenchBlockEntity.SIZE));
    }

    public SortingWorkbenchMenu(int containerId, Inventory playerInventory, net.minecraft.world.Container container) {
        super(SortingBoxMod.SORTING_WORKBENCH_MENU, containerId);
        this.container = container;
        addSlot(new Slot(container, SortingWorkbenchBlockEntity.SLOT_MATERIAL, 56, 17));
        addSlot(new Slot(container, SortingWorkbenchBlockEntity.SLOT_LABEL, 56, 53));
        addSlot(new Slot(container, SortingWorkbenchBlockEntity.SLOT_RESULT, 116, 31) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                super.onTake(player, stack);
                container.removeItem(SortingWorkbenchBlockEntity.SLOT_MATERIAL, 1);
                container.removeItem(SortingWorkbenchBlockEntity.SLOT_LABEL, 1);
            }
        });
        addStandardInventorySlots(playerInventory, 8, 84);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return empty;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index == 2) {
            if (!this.moveItemStackTo(stack, 3, this.slots.size(), true)) {
                return empty;
            }
        } else if (index < 3) {
            if (!this.moveItemStackTo(stack, 3, this.slots.size(), true)) {
                return empty;
            }
        } else if (!this.moveItemStackTo(stack, 0, 2, false)) {
            return empty;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.NULL, player, SortingBoxMod.SORTING_WORKBENCH);
    }
}