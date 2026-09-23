package com.soloeconomy.client;

import com.soloeconomy.network.Listing;
import com.soloeconomy.network.MarketListingsPayload;
import com.soloeconomy.network.QuotePayload;

import net.minecraft.world.item.Item;

import javax.annotation.Nullable;

import java.util.List;

/** Client-side mirror of what the server last told us. Never trusted for pricing. */
public final class ClientMarketState {

    private static long balance;
    private static List<MarketListingsPayload.Merchant> merchants = List.of();
    private static List<Listing> listings = List.of();
    private static float spread = 0.1F;

    @Nullable
    private static QuotePayload quote;

    private ClientMarketState() {
    }

    public static long balance() {
        return balance;
    }

    public static void setBalance(long value) {
        balance = value;
    }

    public static List<Listing> listings() {
        return listings;
    }

    public static List<MarketListingsPayload.Merchant> merchants() {
        return merchants;
    }

    public static float spread() {
        return spread;
    }

    public static void setListings(List<MarketListingsPayload.Merchant> newMerchants, List<Listing> newListings,
                                   float newSpread) {
        merchants = newMerchants;
        listings = newListings;
        spread = newSpread;
        // Prices just moved, so any cost preview we were showing is stale.
        quote = null;
    }

    /** The exact totals for the row under the pointer, or null while we are still asking. */
    @Nullable
    public static QuotePayload quoteFor(Item item) {
        return quote != null && quote.item() == item ? quote : null;
    }

    public static void setQuote(QuotePayload value) {
        quote = value;
    }

    public static void clearQuote() {
        quote = null;
    }
}
