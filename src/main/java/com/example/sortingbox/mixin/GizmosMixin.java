package com.example.sortingbox.mixin;

import com.example.sortingbox.client.SortingBoxClient;
import net.minecraft.gizmos.Gizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only: contributes our gizmos while the game is collecting them for a frame.
 */
@Mixin(Gizmos.class)
public class GizmosMixin {
    @Inject(method = "withCollector", at = @At("RETURN"))
    private static void sortingbox$collect(CallbackInfoReturnable<Gizmos.TemporaryCollection> cir) {
        SortingBoxClient.renderGizmos();
    }
}