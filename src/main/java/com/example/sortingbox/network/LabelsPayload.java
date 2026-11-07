package com.example.sortingbox.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client label update. {@code removed} means the label at
 * {@code labelPos} was dropped (e.g. the container was broken).
 */
public record LabelsPayload(BlockPos boxPos, BlockPos labelPos, String categoryId, boolean removed)
        implements CustomPacketPayload {
    public static final Type<LabelsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("sortingbox", "label"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LabelsPayload> CODEC =
            CustomPacketPayload.codec(
                    (payload, buf) -> {
                        buf.writeBlockPos(payload.boxPos());
                        buf.writeBlockPos(payload.labelPos());
                        buf.writeUtf(payload.categoryId());
                        buf.writeBoolean(payload.removed());
                    },
                    buf -> new LabelsPayload(buf.readBlockPos(), buf.readBlockPos(),
                            buf.readUtf(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}