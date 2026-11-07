package com.example.sortingbox.item;

import com.example.sortingbox.block.SortingBoxBlockEntity;
import com.example.sortingbox.compat.RoadSignCompat;
import com.example.sortingbox.component.SortingBoxComponents;
import com.example.sortingbox.util.CategoryConfig;
import com.example.sortingbox.util.SortCategory;
import com.example.sortingbox.util.SortingService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Universal label item. Its category lives in a data component and is displayed as a
 * pseudo-enchantment tooltip line; the item name follows the category as well.
 */
public class LabelItem extends Item {
    public LabelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        String categoryId = stack.get(SortingBoxComponents.CATEGORY);
        if (categoryId == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        SortingBoxBlockEntity box = null;
        for (BlockPos pos : BlockPos.betweenClosed(clicked.offset(-32, -16, -32), clicked.offset(32, 16, 32))) {
            if (level.getBlockEntity(pos) instanceof SortingBoxBlockEntity found) {
                box = found;
                break;
            }
        }
        if (box == null) {
            return InteractionResult.PASS;
        }

        // Already tagged with this exact category? Right-click again to toggle it off.
        String existing = box.getLabel(clicked);
        BlockPos other = SortingService.otherHalf(level, clicked);
        if (existing == null && other != null) {
            existing = box.getLabel(other);
        }
        if (categoryId.equals(existing)) {
            if (categoryId.equals(box.getLabel(clicked))) {
                box.removeLabelAndNotify(clicked);
            }
            if (other != null && categoryId.equals(box.getLabel(other))) {
                box.removeLabelAndNotify(other);
            }
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                RoadSignCompat.remove(serverLevel, clicked, context.getClickedFace());
            }
            return InteractionResult.SUCCESS;
        }

        box.setLabel(clicked, categoryId);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            String display = CategoryConfig.byId(categoryId) != null
                    ? CategoryConfig.byId(categoryId).name() : categoryId;
            RoadSignCompat.place(serverLevel, clicked, context.getClickedFace(), display);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        String categoryId = stack.get(SortingBoxComponents.CATEGORY);
        if (categoryId != null) {
            return Component.literal(categoryName(categoryId) + "标签");
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        String categoryId = stack.get(SortingBoxComponents.CATEGORY);
        if (categoryId != null) {
            tooltip.accept(Component.literal("附魔：" + categoryName(categoryId))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    private static String categoryName(String categoryId) {
        SortCategory category = CategoryConfig.byId(categoryId);
        return category != null ? category.name() : categoryId;
    }
}