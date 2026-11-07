package com.example.sortingbox.mixin;

import com.example.sortingbox.SortingBoxMod;
import net.minecraft.server.Bootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 26.3 freezes the built-in registries inside Bootstrap.bootStrap().
 * Fabric initializes mod entrypoints after that, so block/item construction (which
 * needs an intrusive holder) has to happen before the freeze: hook bootStrap's head.
 */
@Mixin(Bootstrap.class)
public class BootstrapMixin {
    /**
     * Inject right before BuiltInRegistries.bootStrap() is invoked: at that point
     * Bootstrap has already flagged itself bootstrapped (so registry registration is
     * allowed) but the built-in registries are not frozen yet.
     */
    @Inject(method = "bootStrap",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/registries/BuiltInRegistries;bootStrap()V"))
    private static void sortingbox$registerContent(CallbackInfo ci) {
        try {
            SortingBoxMod.registerContent();
        } catch (Throwable t) {
            SortingBoxMod.LOGGER.error("Failed to register Sorting Box content", t);
        }
    }
}