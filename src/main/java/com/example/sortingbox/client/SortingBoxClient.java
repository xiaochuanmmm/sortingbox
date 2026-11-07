package com.example.sortingbox.client;

import com.example.sortingbox.block.SortingBoxBlockEntity;
import com.example.sortingbox.compat.RoadSignCompat;
import com.example.sortingbox.network.LabelsPayload;
import com.example.sortingbox.util.CategoryConfig;
import com.example.sortingbox.util.SortCategory;
import com.example.sortingbox.util.SortingService;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client side: press the key once to reveal a sorting box and the containers that
 * carry a label, press it again to hide. The overlay is refreshed every tick so it
 * follows blocks being placed or broken.
 */
public final class SortingBoxClient implements ClientModInitializer {
    private static final int BOX_RADIUS = 16;
    private static final int LINK_RADIUS_H = 128;
    private static final int LINK_RADIUS_V = 64;

    private static final int COLOR_BOX = 0xFFFF0000;       // red: sorting box
    private static final int COLOR_TARGET = 0xFF00FF00;   // green: labeled container
    private static final int COLOR_LINE = 0xFFFFDD00;     // yellow: link
    private static final int COLOR_TEXT_BOX = 0xFFFF0000; // red text
    private static final int COLOR_TEXT_TARGET = 0xFFFFFFFF; // white text
    private static final int TEXT_BG = 0x80000000;

    private static KeyMapping showLinksKey;
    private static boolean showing;
    private static boolean foundBox;
    private static final List<Link> LINKS = new ArrayList<>();

    private record Link(BlockPos boxPos, Map<BlockPos, String> targets) {
    }

    @Override
    public void onInitializeClient() {
        CategoryConfig.load();
        ClientConfig.load();
        showLinksKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.sortingbox.show_links",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F7, // 默认 F7
                KeyMapping.Category.GAMEPLAY));

        com.example.sortingbox.mixin.MenuScreensAccessor.getScreens().put(
                com.example.sortingbox.SortingBoxMod.SORTING_WORKBENCH_MENU,
                newScreenConstructor());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (showLinksKey.consumeClick()) {
                toggle();
            }
            refreshClientData();
        });

        ClientPlayNetworking.registerGlobalReceiver(LabelsPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Map<BlockPos, String> boxMap =
                            LabelCache.BOX_LABELS.computeIfAbsent(payload.boxPos(), k -> new HashMap<>());
                    if (payload.removed()) {
                        boxMap.remove(payload.labelPos());
                    } else {
                        boxMap.put(payload.labelPos(), payload.categoryId());
                    }
                }));
    }

    /**
     * Builds a {@code MenuScreens.ScreenConstructor} proxy that Fabric's extended menu
     * networking calls via {@code create(menu, inventory, title)}.
     */
    private static Object newScreenConstructor() {
        try {
            Class<?> constructorClass = Class.forName(
                    "net.minecraft.client.gui.screens.MenuScreens$ScreenConstructor");
            return java.lang.reflect.Proxy.newProxyInstance(
                    constructorClass.getClassLoader(), new Class<?>[]{constructorClass}, (proxy, method, args) -> {
                        if ("create".equals(method.getName()) && args != null && args.length == 3) {
                            return new SortingWorkbenchScreen(
                                    (com.example.sortingbox.block.SortingWorkbenchMenu) args[0],
                                    (net.minecraft.world.entity.player.Inventory) args[1],
                                    (Component) args[2]);
                        }
                        if ("fromPacket".equals(method.getName()) && args != null && args.length == 4) {
                            Component title = (Component) args[0];
                            Minecraft mc = (Minecraft) args[2];
                            int windowId = (int) args[3];
                            mc.setScreenAndShow(new SortingWorkbenchScreen(
                                    new com.example.sortingbox.block.SortingWorkbenchMenu(
                                            windowId, mc.player.getInventory()),
                                    mc.player.getInventory(), title));
                        }
                        return null;
                    });
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static void toggle() {
        showing = !showing;
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!showing) {
            LINKS.clear();
            player.sendOverlayMessage(Component.translatable("msg.sortingbox.links_off"));
            return;
        }
        refreshClientData();
        if (!LINKS.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("msg.sortingbox.links_on", LINKS.size()));
        } else if (foundBox) {
            player.sendOverlayMessage(Component.translatable("msg.sortingbox.box_no_labels"));
        } else {
            player.sendOverlayMessage(Component.translatable("msg.sortingbox.no_links"));
        }
    }

    /**
     * Re-scans every tick: refreshes the label map used by Jade (all labels of
     * nearby sorting boxes), and the overlay links when the overlay is shown.
     */
    private static void refreshClientData() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) {
            LabelCache.LABELS.clear();
            LINKS.clear();
            return;
        }
        Map<BlockPos, String> labelMap = new HashMap<>();
        List<Link> links = new ArrayList<>();
        boolean boxFound = false;
        BlockPos center = player.blockPosition();

        // Nearby boxes (freshly placed / newly approached) plus every box already
        // known from network updates, so the overlay keeps working when walking away.
        java.util.Set<BlockPos> boxes = new java.util.HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-BOX_RADIUS, -BOX_RADIUS / 2, -BOX_RADIUS),
                center.offset(BOX_RADIUS, BOX_RADIUS / 2, BOX_RADIUS))) {
            if (level.getBlockEntity(pos) instanceof SortingBoxBlockEntity) {
                boxes.add(pos.immutable());
            }
        }
        boxes.addAll(LabelCache.BOX_LABELS.keySet());

        for (BlockPos boxPos : boxes) {
            if (!(level.getBlockEntity(boxPos) instanceof SortingBoxBlockEntity)) {
                continue;
            }
            boxFound = true;
            Map<BlockPos, String> cached = LabelCache.BOX_LABELS.get(boxPos);
            if (cached == null || cached.isEmpty()) {
                continue;
            }
            // Only keep targets that still exist as containers in the world, so
            // broken/replaced blocks disappear from the overlay immediately.
            Map<BlockPos, String> valid = new HashMap<>();
            for (Map.Entry<BlockPos, String> entry : cached.entrySet()) {
                if (level.getBlockEntity(entry.getKey()) instanceof Container container
                        && !(container instanceof SortingBoxBlockEntity)) {
                    valid.put(entry.getKey(), entry.getValue());
                }
            }
            if (!valid.isEmpty()) {
                labelMap.putAll(valid);
                if (showing || ClientConfig.alwaysShowNames) {
                    links.add(new Link(boxPos, valid));
                }
            }
        }
        foundBox = boxFound;
        LabelCache.LABELS.clear();
        LabelCache.LABELS.putAll(labelMap);
        if (showing || ClientConfig.alwaysShowNames) {
            LINKS.clear();
            LINKS.addAll(links);
        } else {
            LINKS.clear();
        }
    }

    /**
     * Called from the Gizmos mixin inside the per-frame gizmo collection window.
     */
    public static void renderGizmos() {
        // Road-sign integration replaces floating text; without it we fall back to the
        // screen-space labels so categories stay readable.
        boolean useSigns = RoadSignCompat.isEnabled();
        WorldLabelRenderer.begin();
        try {
            if (!showing || LINKS.isEmpty()) {
                return;
            }
            ClientLevel level = Minecraft.getInstance().level;
            for (Link link : LINKS) {
                if (showing) {
                    Gizmos.cuboid(link.boxPos(), GizmoStyle.stroke(COLOR_BOX));
                }
                if (!useSigns) {
                    drawLabel("分类箱", link.boxPos(), COLOR_TEXT_BOX);
                }
                Vec3 boxCenter = Vec3.atCenterOf(link.boxPos());
                for (Map.Entry<BlockPos, String> entry : link.targets().entrySet()) {
                    BlockPos target = entry.getKey();
                    if (showing) {
                        BlockPos other = level != null ? SortingService.otherHalf(level, target) : null;
                        if (other != null) {
                            Gizmos.cuboid(mergedAabb(target, other), GizmoStyle.stroke(COLOR_TARGET));
                        } else {
                            Gizmos.cuboid(target, GizmoStyle.stroke(COLOR_TARGET));
                        }
                    }
                    if (!useSigns) {
                        SortCategory category = CategoryConfig.byId(entry.getValue());
                        drawLabel(category != null ? category.name() : entry.getValue(), target, COLOR_TEXT_TARGET);
                    }
                    if (showing) {
                        Gizmos.line(boxCenter, Vec3.atCenterOf(target), COLOR_LINE);
                    }
                }
            }
        } finally {
            WorldLabelRenderer.end();
        }
    }

    private static void drawLabel(String text, BlockPos pos, int color) {
        Vec3 anchor;
        float scale;
        if ("front".equals(ClientConfig.labelPlacement)) {
            ClientLevel level = Minecraft.getInstance().level;
            Direction facing = Direction.SOUTH;
            if (level != null) {
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                } else if (state.hasProperty(BlockStateProperties.FACING)) {
                    facing = state.getValue(BlockStateProperties.FACING);
                }
            }
            Vec3 center = Vec3.atCenterOf(pos);
            Vec3 offset = new Vec3(facing.getStepX(), 0.0, facing.getStepZ()).scale(0.85);
            anchor = center.add(offset).add(0, 0.25, 0);
            scale = 1.0f;
        } else {
            anchor = new Vec3(pos.getX() + 0.5, pos.getY() + 1.25, pos.getZ() + 0.5);
            scale = 1.25f;
        }
        WorldLabelRenderer.add(anchor, text, color, scale);
    }

    private static AABB mergedAabb(BlockPos a, BlockPos b) {
        return new AABB(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }
}