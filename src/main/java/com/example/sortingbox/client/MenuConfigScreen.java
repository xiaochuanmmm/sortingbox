package com.example.sortingbox.client;

import com.example.sortingbox.compat.RoadSignCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Minimal Mod Menu settings screen: adjusts the sign text size (mishanguc "size" units).
 */
public class MenuConfigScreen extends Screen {
    private final Screen parent;
    private Button valueButton;

    public MenuConfigScreen(Screen parent) {
        super(Component.literal("分类箱设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.valueButton = Button.builder(Component.literal(valueText()), button -> {
                })
                .bounds(centerX - 110, centerY - 10, 220, 20)
                .build();
        addRenderableWidget(this.valueButton);

        addRenderableWidget(Button.builder(Component.literal("-"), button -> change(-1.0f))
                .bounds(centerX + 114, centerY - 10, 20, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> change(1.0f))
                .bounds(centerX + 136, centerY - 10, 20, 20)
                .build());

        Button integrationButton = Button.builder(Component.literal(integrationText()), button -> {
                    if (RoadSignCompat.isAvailable()) {
                        ClientConfig.signIntegration = !ClientConfig.signIntegration;
                        ClientConfig.save();
                        button.setMessage(Component.literal(integrationText()));
                    }
                })
                .bounds(centerX - 110, centerY + 16, 220, 20)
                .build();
        addRenderableWidget(integrationButton);

        addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
                .bounds(centerX - 60, centerY + 48, 120, 20)
                .build());
    }

    private void change(float delta) {
        ClientConfig.signTextSize = Math.max(1.0f, Math.min(32.0f, ClientConfig.signTextSize + delta));
        ClientConfig.save();
        if (this.valueButton != null) {
            this.valueButton.setMessage(Component.literal(valueText()));
        }
    }

    private static String integrationText() {
        if (!RoadSignCompat.isAvailable()) {
            return "豹猫指示牌集成：未安装";
        }
        return "豹猫指示牌集成：" + (ClientConfig.signIntegration ? "开" : "关");
    }

    private static String valueText() {
        return "标牌文字大小：" + ClientConfig.signTextSize + "（内置默认 8.0）";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 40, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }
}
