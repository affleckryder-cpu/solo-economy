package com.soloeconomy.client;

import com.soloeconomy.network.BalanceSyncPayload;
import com.soloeconomy.network.MarketListingsPayload;
import com.soloeconomy.network.QuotePayload;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-only. Reached exclusively through the forwarding methods in ModNetwork. */
public final class ClientMarketHandler {

    private ClientMarketHandler() {
    }

    public static void handleListings(MarketListingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientMarketState.setListings(payload.listings(), payload.truncated(), payload.spread());
            ClientMarketState.setBalance(payload.balance());
            if (Minecraft.getInstance().screen instanceof MarketScreen screen) {
                screen.onListingsChanged();
            }
        });
    }

    public static void handleQuote(QuotePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientMarketState.setQuote(payload));
    }

    public static void handleBalance(BalanceSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientMarketState.setBalance(payload.balance());
            // A balance change means prices moved too, so pull a fresh page if the stall is open.
            if (Minecraft.getInstance().screen instanceof MarketScreen screen) {
                screen.requestRefresh();
            }
        });
    }
}
