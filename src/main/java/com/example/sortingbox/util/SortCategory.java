package com.example.sortingbox.util;

import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * One user-editable sorting category: a display name plus the items/tags that belong
 * to it. Loaded from {@code config/sortingbox/categories.json}.
 */
public record SortCategory(String id, String name, Set<Identifier> items, Set<Identifier> tags) {
}