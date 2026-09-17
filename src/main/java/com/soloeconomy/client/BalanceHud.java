package com.soloeconomy.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The balance readout, pinned to the right edge of the screen.
 *
 * <p>Hidden while the stall is open, since that screen already shows the number in its header.
 */
public final class BalanceHud {

    private static final int PADDING = 4;
    private static final int HEIGHT = 20;
    private static final int BACKGROUND = 0x90000000;
    private static final int TEXT = 0xFFFFFFFF;

    private BalanceHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (minecraft.screen instanceof MarketScreen) {
            return;
        }

        String text = Long.toString(ClientMarketState.balance());
        int textWidth = minecraft.font.width(text);
        int boxWidth = 22 + textWidth + 6;
        int x = graphics.guiWidth() - boxWidth - PADDING;
        int y = PADDING;

        graphics.fill(x, y, x + boxWidth, y + HEIGHT, BACKGROUND);
        graphics.renderItem(new ItemStack(Items.EMERALD), x + 3, y + 2);
        graphics.drawString(minecraft.font, text, x + 22, y + 6, TEXT);
    }
}
