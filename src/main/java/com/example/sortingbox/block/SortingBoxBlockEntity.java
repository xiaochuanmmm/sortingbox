package com.example.sortingbox.block;

import com.example.sortingbox.SortingBoxMod;
import com.example.sortingbox.network.LabelsPayload;
import com.example.sortingbox.util.SortingService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SortingBoxBlockEntity extends BlockEntity implements Container {
    public static final int SIZE = 27;
    private static final int DISTRIBUTE_INTERVAL = 20;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final Map<BlockPos, String> labels = new java.util.LinkedHashMap<>();
    private int tickCounter;

    public SortingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(SortingBoxMod.SORTING_BOX_ENTITY, pos, state);
    }

    public NonNullList<ItemStack> getItems() {
        return items;
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
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
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

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        labels.clear();
        input.childrenListOrEmpty("labels").forEach(child -> {
            int x = child.getIntOr("x", 0);
            int y = child.getIntOr("y", 0);
            int z = child.getIntOr("z", 0);
            String category = child.getStringOr("category", "misc");
            labels.put(new BlockPos(x, y, z), category);
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        ValueOutput.ValueOutputList list = output.childrenList("labels");
        labels.forEach((pos, category) -> {
            ValueOutput child = list.addChild();
            child.putInt("x", pos.getX());
            child.putInt("y", pos.getY());
            child.putInt("z", pos.getZ());
            child.putString("category", category);
        });
    }

    public void setLabel(BlockPos pos, String categoryId) {
        labels.put(pos.immutable(), categoryId);
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            var packet = new LabelsPayload(worldPosition, pos.immutable(), categoryId, false);
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) < 64.0 * 64.0) {
                    ServerPlayNetworking.send(player, packet);
                }
            }
        }
    }

    /**
     * Drops a label and tells nearby clients about it (container broken/replaced).
     */
    public void removeLabelAndNotify(BlockPos pos) {
        if (labels.remove(pos.immutable()) == null) {
            return;
        }
        setChanged();
        broadcastRemoval(pos);
    }

    private void broadcastRemoval(BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            var packet = new LabelsPayload(worldPosition, pos.immutable(), "", true);
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) <= 4096.0) {
                    ServerPlayNetworking.send(player, packet);
                }
            }
        }
    }

    /**
     * Labels belong to the container at the recorded position: if that position is
     * no longer a container, the label is dropped so a freshly placed block does not
     * inherit it.
     */
    private void pruneLabels() {
        if (level == null || level.isClientSide()) {
            return;
        }
        List<BlockPos> removed = new ArrayList<>();
        for (var it = labels.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (!(level.getBlockEntity(entry.getKey()) instanceof Container container)
                    || container instanceof SortingBoxBlockEntity) {
                it.remove();
                removed.add(entry.getKey());
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        setChanged();
        for (BlockPos pos : removed) {
            broadcastRemoval(pos);
        }
    }

    public String getLabel(BlockPos pos) {
        return labels.get(pos);
    }

    public Map<BlockPos, String> getLabels() {
        return labels;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (++tickCounter < DISTRIBUTE_INTERVAL) {
            return;
        }
        tickCounter = 0;
        pruneLabels();
        SortingService.distribute(this);
    }
}