package com.example.sortingbox.mixin;

import com.example.sortingbox.client.WorldLabelRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the fallback world-anchored labels in the HUD pass.
 */
@Mixin(Hud.class)
public class HudMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void sortingbox$renderWorldLabels(GuiGraphicsExtractor extractor, DeltaTracker delta,
                                              CallbackInfo callbackInfo) {
        WorldLabelRenderer.render(extractor);
    }
}
