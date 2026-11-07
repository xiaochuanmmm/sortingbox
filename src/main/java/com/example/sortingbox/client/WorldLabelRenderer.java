package com.example.sortingbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Screen-space labels for the fallback display (when no road-sign mod is installed).
 * The world anchor is projected with the vanilla {@code projectPointToScreen} every
 * frame, so the text stays fixed in the world, never rotates with the camera, and keeps
 * a constant on-screen size.
 */
public final class WorldLabelRenderer {
    private static final int BACKGROUND = 0x99000000;
    private static final double MAX_DISTANCE_SQR = 256.0 * 256.0;

    private static final AtomicReference<List<Label>> FRAME = new AtomicReference<>(List.of());
    private static final ThreadLocal<List<Label>> BUILDING =
            ThreadLocal.withInitial(ArrayList::new);

    private record Label(Vec3 pos, String text, int color, float scale) {
    }

    private WorldLabelRenderer() {
    }

    public static void begin() {
        BUILDING.get().clear();
    }

    public static void add(Vec3 pos, String text, int color, float scale) {
        BUILDING.get().add(new Label(pos, text, color, scale));
    }

    public static void end() {
        List<Label> labels = BUILDING.get();
        FRAME.set(labels.isEmpty() ? List.of() : List.copyOf(labels));
    }

    public static void render(GuiGraphicsExtractor graphics) {
        List<Label> labels = FRAME.get();
        if (labels.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        Vec3 cameraPos = camera.pos;
        Quaternionf orientation = camera.orientation;
        Vector3f forward = orientation.transform(new Vector3f(0.0f, 0.0f, -1.0f));
        Vec3 look = new Vec3(forward.x, forward.y, forward.z);
        Font font = mc.font;

        for (Label label : labels) {
            Vec3 toLabel = label.pos.subtract(cameraPos);
            if (toLabel.lengthSqr() > MAX_DISTANCE_SQR) {
                continue;
            }
            if (toLabel.dot(look) <= 0.1) {
                continue;
            }
            Vec3 ndc = mc.gameRenderer.projectPointToScreen(label.pos);
            if (Math.abs(ndc.x) > 1.0 || Math.abs(ndc.y) > 1.0) {
                continue;
            }
            float sx = (float) ((ndc.x * 0.5 + 0.5) * screenWidth);
            float sy = (float) ((1.0 - (ndc.y * 0.5 + 0.5)) * screenHeight);

            float scale = label.scale;
            int textWidth = font.width(label.text);
            float half = textWidth * scale / 2.0f;
            float x = sx - half;
            float y = sy - 4.0f * scale;

            graphics.fill((int) (x - 2), (int) (y - 2),
                    (int) (x + textWidth * scale + 2), (int) (y + 9.0f * scale + 2), BACKGROUND);
            graphics.pose().pushMatrix();
            graphics.pose().scale(scale, scale);
            graphics.text(font, label.text, (int) (x / scale), (int) (y / scale), label.color);
            graphics.pose().popMatrix();
        }
    }
}
