package com.soloeconomy.client;

import com.soloeconomy.config.ClientConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

import java.util.Locale;

/**
 * Where the balance sits on screen, opened from the Mods list. The readout is drawn where the
 * current choices put it, so you can see the result before pressing Done.
 */
public final class BalanceSettingsScreen extends Screen {

    private final Screen parent;
    private ClientConfig.Corner corner = ClientConfig.INSTANCE.balanceCorner.get();
    private EditBox offsetX;
    private EditBox offsetY;

    public BalanceSettingsScreen(Screen parent) {
        super(Component.translatable("gui.soloeconomy.balance_settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 50;
        addRenderableWidget(CycleButton.<ClientConfig.Corner>builder(
                        value -> Component.translatable("gui.soloeconomy.corner." + value.name().toLowerCase(Locale.ROOT)))
                .withValues(ClientConfig.Corner.values())
                .withInitialValue(corner)
                .create(x, y, 200, 20, Component.translatable("gui.soloeconomy.corner"), (button, value) -> corner = value));
        offsetX = addRenderableWidget(number(x + 140, y + 30, ClientConfig.INSTANCE.balanceOffsetX.get(), "gui.soloeconomy.offset_x"));
        offsetY = addRenderableWidget(number(x + 140, y + 55, ClientConfig.INSTANCE.balanceOffsetY.get(), "gui.soloeconomy.offset_y"));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x, y + 90, 200, 20).build());
    }

    private EditBox number(int x, int y, int value, String label) {
        EditBox box = new EditBox(font, x, y, 60, 20, Component.translatable(label));
        box.setFilter(text -> text.matches("\\d{0,4}"));
        box.setValue(Integer.toString(value));
        return box;
    }

    private static int parse(EditBox box) {
        return box.getValue().isEmpty() ? 0 : Integer.parseInt(box.getValue());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - 100;
        int y = height / 2 - 50;
        graphics.drawCenteredString(font, title, width / 2, y - 25, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.soloeconomy.offset_x"), x, y + 36, 0xE0E0E0);
        graphics.drawString(font, Component.translatable("gui.soloeconomy.offset_y"), x, y + 61, 0xE0E0E0);
        BalanceHud.draw(graphics, corner, parse(offsetX), parse(offsetY));
    }

    /** Saves on the way out, Done or Escape alike. */
    @Override
    public void onClose() {
        ClientConfig.INSTANCE.balanceCorner.set(corner);
        ClientConfig.INSTANCE.balanceOffsetX.set(Math.min(4000, parse(offsetX)));
        ClientConfig.INSTANCE.balanceOffsetY.set(Math.min(4000, parse(offsetY)));
        ClientConfig.SPEC.save();
        minecraft.setScreen(parent);
    }
}
