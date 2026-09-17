package com.soloeconomy.client;

import com.soloeconomy.network.Listing;
import com.soloeconomy.network.QuotePayload;

import net.minecraft.world.item.Item;

import javax.annotation.Nullable;

import java.util.List;

/** Client-side mirror of what the server last told us. Never trusted for pricing. */
public final class ClientMarketState {

    private static long balance;
    private static List<Listing> listings = List.of();
    private static boolean truncated;
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

    public static boolean truncated() {
        return truncated;
    }

    public static float spread() {
        return spread;
    }

    public static void setListings(List<Listing> newListings, boolean wasTruncated, float newSpread) {
        listings = newListings;
        truncated = wasTruncated;
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
