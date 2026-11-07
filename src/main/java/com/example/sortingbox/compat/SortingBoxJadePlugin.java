package com.example.sortingbox.compat;

import com.example.sortingbox.block.SortingBoxBlock;
import com.example.sortingbox.block.SortingBoxBlockEntity;
import com.example.sortingbox.client.LabelCache;
import com.example.sortingbox.util.CategoryConfig;
import com.example.sortingbox.util.SortCategory;
import com.example.sortingbox.util.SortingService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Map;

/**
 * Jade (玉) compatibility:
 * - sorting boxes show how many container labels they hold
 * - labeled containers show which category label they carry
 */
public class SortingBoxJadePlugin implements IWailaPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath("sortingbox", "sorting_box_info");
    private static final Identifier UID_LABEL = Identifier.fromNamespaceAndPath("sortingbox", "container_label");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new InfoProvider(), SortingBoxBlock.class);
        registration.registerBlockComponent(new ContainerLabelProvider(), Block.class);
    }

    public static class InfoProvider implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockEntity() instanceof SortingBoxBlockEntity) {
                Map<?, ?> labels = LabelCache.BOX_LABELS.get(accessor.getPosition());
                if (labels != null && !labels.isEmpty()) {
                    tooltip.add(Component.translatable("jade.sortingbox.labels", labels.size()));
                }
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    public static class ContainerLabelProvider implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            String categoryId = LabelCache.LABELS.get(accessor.getPosition());
            if (categoryId == null && accessor.getLevel() != null) {
                // A label on either half of a double chest applies to the whole chest.
                var other = SortingService.otherHalf(accessor.getLevel(), accessor.getPosition());
                if (other != null) {
                    categoryId = LabelCache.LABELS.get(other);
                }
            }
            if (categoryId != null) {
                SortCategory category = CategoryConfig.byId(categoryId);
                String name = category != null ? category.name() : categoryId;
                tooltip.add(Component.translatable("jade.sortingbox.category", name)
                        .withStyle(ChatFormatting.GREEN));
            }
        }

        @Override
        public Identifier getUid() {
            return UID_LABEL;
        }
    }
}