package com.example.sortingbox.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import com.example.sortingbox.SortingBoxMod;

/**
 * Optional integration with OcelotSignMod: instead of floating text, a real wall road
 * sign is placed on the face of the tagged container and filled with the category name.
 *
 * <p>Everything is done reflectively so the mod keeps working (and building) when
 * OcelotSignMod / mishanguc are not installed.</p>
 */
public final class RoadSignCompat {
    private static final Identifier SIGN_ID =
            Identifier.fromNamespaceAndPath("ocelotsignmod", "wallroadsign/blue/small_a");

    private static Boolean available;

    private RoadSignCompat() {
    }

    /** Available AND enabled by the player. */
    public static boolean isEnabled() {
        return isAvailable() && com.example.sortingbox.client.ClientConfig.signIntegration;
    }

    public static boolean isAvailable() {
        if (available == null) {
            available = FabricLoader.getInstance().isModLoaded("ocelotsignmod")
                    && FabricLoader.getInstance().isModLoaded("mishanguc")
                    && BuiltInRegistries.BLOCK.getValue(SIGN_ID) != null;
        }
        return available;
    }

    /**
     * Places (or updates) the wall sign in front of {@code containerPos} on the face the
     * player clicked, and writes {@code text} into it.
     */
    public static void place(ServerLevel level, BlockPos containerPos, Direction face, String text) {
        if (!isEnabled()) {
            return;
        }
        try {
            BlockPos signPos = containerPos.relative(face);
            if (!level.isLoaded(signPos)) {
                return;
            }
            Block block = BuiltInRegistries.BLOCK.getValue(SIGN_ID);
            if (block == null) {
                return;
            }

            BlockState current = level.getBlockState(signPos);
            if (!(current.isAir() || current.canBeReplaced())) {
                if (current.getBlock() != block) {
                    return;
                }
                writeText(level, signPos, text);
                return;
            }

            BlockState state = block.defaultBlockState()
                    .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
                    .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, face);
            level.setBlock(signPos, state, 3);
            writeText(level, signPos, text);
        } catch (Throwable throwable) {
            SortingBoxMod.LOGGER.warn("Road sign placement failed", throwable);
        }
    }

    /**
     * Returns the block position a wall sign is mounted on, or {@code null} if
     * {@code state} is not the road sign used by this integration.
     */
    public static BlockPos containerBehind(Level level, BlockPos signPos, BlockState state) {
        if (!isAvailable() || state == null) {
            return null;
        }
        try {
            Block block = BuiltInRegistries.BLOCK.getValue(SIGN_ID);
            if (block == null || state.getBlock() != block) {
                return null;
            }
            if (state.hasProperty(FaceAttachedHorizontalDirectionalBlock.FACING)) {
                Direction facing = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING);
                return signPos.relative(facing.getOpposite());
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return null;
    }

    /** Removes a sign that this mod previously placed on the given face, if any. */
    public static void remove(ServerLevel level, BlockPos containerPos, Direction face) {
        if (!isAvailable()) {
            return;
        }
        try {
            BlockPos signPos = containerPos.relative(face);
            if (!level.isLoaded(signPos)) {
                return;
            }
            Block block = BuiltInRegistries.BLOCK.getValue(SIGN_ID);
            if (block == null) {
                return;
            }
            BlockState current = level.getBlockState(signPos);
            if (current.getBlock() == block) {
                level.removeBlock(signPos, false);
            }
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private static void writeText(ServerLevel level, BlockPos signPos, String text) {
        try {
            BlockEntity be = level.getBlockEntity(signPos);
            if (be == null) {
                SortingBoxMod.LOGGER.warn("Road sign text skipped: no block entity at {}", signPos);
                return;
            }
            Method create = be.getClass().getMethod("createDefaultTextContext");
            Object context = create.invoke(be);
            if (context == null) {
                SortingBoxMod.LOGGER.warn("Road sign text skipped: default context is null at {}", signPos);
                return;
            }
            Field textField = context.getClass().getField("text");
            textField.set(context, Component.literal(text));
            Field sizeField = context.getClass().getField("size");
            sizeField.setFloat(context, com.example.sortingbox.client.ClientConfig.signTextSize);

            // The default value is an immutable empty list, so always replace it with a
            // fresh mutable list instead of clearing the existing one.
            Field listField = be.getClass().getField("textContexts");
            List<Object> contexts = new java.util.ArrayList<>();
            contexts.add(context);
            listField.set(be, contexts);

            be.getClass().getMethod("setChanged").invoke(be);
            Method markDirty = be.getClass().getMethod("markDirtyAndUpdate");
            markDirty.invoke(be);
            BlockState state = level.getBlockState(signPos);
            level.sendBlockUpdated(signPos, state, state, 3);

            Object listNow = listField.get(be);
            Object storedText = textField.get(contexts.isEmpty() ? context : contexts.get(0));
            SortingBoxMod.LOGGER.info("Road sign text at {}: list={} size={} text={}",
                    signPos, listNow != null, contexts.size(), storedText);
        } catch (Throwable throwable) {
            SortingBoxMod.LOGGER.warn("Road sign text write failed at {}", signPos, throwable);
        }
    }
}