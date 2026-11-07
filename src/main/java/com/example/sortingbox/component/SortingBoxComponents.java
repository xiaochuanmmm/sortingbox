package com.example.sortingbox.component;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Data components used by sorting box items.
 */
public final class SortingBoxComponents {
    /** Category id carried by a universal label item. */
    public static final DataComponentType<String> CATEGORY = DataComponentType.<String>builder()
            .persistent(Codec.STRING)
            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
            .build();

    private SortingBoxComponents() {
    }
}