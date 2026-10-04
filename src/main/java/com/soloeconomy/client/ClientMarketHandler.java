package com.soloeconomy.client;

import com.soloeconomy.network.BalanceSyncPayload;
import com.soloeconomy.network.MarketListingsPayload;
import com.soloeconomy.network.QuotePayload;

import net.minecraft.client.Minecraft;

/** Client-only. Reached exclusively through the client-bound handlers in ModNetwork. */
public final class ClientMarketHandler {

    private ClientMarketHandler() {
    }

    public static void handleListings(MarketListingsPayload payload) {
        ClientMarketState.setListings(payload.merchants(), payload.listings(), payload.carried(), payload.spread());
        ClientMarketState.setBalance(payload.balance());
        if (Minecraft.getInstance().screen instanceof MarketScreen screen) {
            screen.onListingsChanged();
        }
    }

    public static void handleQuote(QuotePayload payload) {
        ClientMarketState.setQuote(payload);
    }

    public static void handleBalance(BalanceSyncPayload payload) {
        ClientMarketState.setBalance(payload.balance());
        // A balance change means prices moved too, so pull a fresh page if the stall is open.
        if (Minecraft.getInstance().screen instanceof MarketScreen screen) {
            screen.requestRefresh();
        }
    }
}
