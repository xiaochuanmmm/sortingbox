package com.example.sortingbox.mixin;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Exposes the private {@code MenuScreens.SCREENS} map used by Fabric's extended menu
 * networking to resolve the screen constructor for a menu type.
 */
@Mixin(MenuScreens.class)
public interface MenuScreensAccessor {
    @Accessor("SCREENS")
    static Map<MenuType<?>, Object> getScreens() {
        throw new AssertionError();
    }
}