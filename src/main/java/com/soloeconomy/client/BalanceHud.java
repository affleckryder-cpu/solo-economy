package com.soloeconomy.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import com.soloeconomy.config.ClientConfig;
import com.soloeconomy.market.EconomyAccount;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The balance readout, in whichever corner of the screen the client config asks for.
 *
 * <p>Hidden while the stall is open, since that screen already shows the number in its header.
 */
public final class BalanceHud {

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
        ClientConfig.Corner corner = ClientConfig.INSTANCE.balanceCorner.get();
        if (minecraft.screen instanceof MarketScreen || corner == ClientConfig.Corner.HIDDEN) {
            return;
        }

        String text = Long.toString(ClientMarketState.balance() / EconomyAccount.CENTS_PER_EMERALD);
        int textWidth = minecraft.font.width(text);
        int boxWidth = 22 + textWidth + 6;
        int offsetX = ClientConfig.INSTANCE.balanceOffsetX.get();
        int offsetY = ClientConfig.INSTANCE.balanceOffsetY.get();
        boolean left = corner == ClientConfig.Corner.TOP_LEFT || corner == ClientConfig.Corner.BOTTOM_LEFT;
        boolean bottom = corner == ClientConfig.Corner.BOTTOM_LEFT || corner == ClientConfig.Corner.BOTTOM_RIGHT;
        int x = left ? offsetX : graphics.guiWidth() - boxWidth - offsetX;
        int y = bottom ? graphics.guiHeight() - HEIGHT - offsetY : offsetY;

        graphics.fill(x, y, x + boxWidth, y + HEIGHT, BACKGROUND);
        graphics.renderItem(new ItemStack(Items.EMERALD), x + 3, y + 2);
        graphics.drawString(minecraft.font, text, x + 22, y + 6, TEXT);
    }
}
