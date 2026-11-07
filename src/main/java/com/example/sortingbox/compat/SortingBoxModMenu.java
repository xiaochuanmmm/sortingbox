package com.example.sortingbox.compat;

import com.example.sortingbox.client.MenuConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Adds a config button in Mod Menu for client display settings.
 */
public class SortingBoxModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return MenuConfigScreen::new;
    }
}