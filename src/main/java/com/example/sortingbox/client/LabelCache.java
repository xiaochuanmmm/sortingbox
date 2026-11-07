package com.example.sortingbox.client;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Plain data holder, refreshed by the client tick loop and read by the Jade plugin.
 * Deliberately free of any client-only Minecraft classes so both sides can load it.
 */
public final class LabelCache {
    /** Target container position -> category id (for Jade tooltips). */
    public static final Map<BlockPos, String> LABELS = new HashMap<>();
    /** Sorting box position -> (target position -> category id), for the F7 overlay. */
    public static final Map<BlockPos, Map<BlockPos, String>> BOX_LABELS = new HashMap<>();

    private LabelCache() {
    }
}