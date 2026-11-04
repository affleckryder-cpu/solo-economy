package com.soloeconomy.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * The merchant list for the sidebar, the goods matching the client's query, and prices for what the
 * player is carrying (for the inventory panel). All small, so simply resent on every query.
 */
public record MarketListingsPayload(List<Merchant> merchants, List<Listing> listings, List<Listing> carried,
                                    float spread, long balance) {

    /**
     * The most goods one reply carries. A listing is about 18 bytes, so this stays well inside the
     * 1 MB packet limit. It was 2,048 once, and an open-market modpack with 2,069 priced items
     * disconnected the player as soon as the stall opened.
     */
    public static final int MAX_LISTINGS = 20_000;


    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(merchants, (b, merchant) -> merchant.encode(b));
        buf.writeCollection(listings, (b, listing) -> listing.encode(b));
        buf.writeCollection(carried, (b, listing) -> listing.encode(b));
        buf.writeFloat(spread);
        buf.writeVarLong(balance);
    }

    public static MarketListingsPayload decode(FriendlyByteBuf buf) {
        return new MarketListingsPayload(buf.readList(Merchant::decode), buf.readList(Listing::decode),
                buf.readList(Listing::decode), buf.readFloat(), buf.readVarLong());
    }

    /** What the sidebar needs: the id (its lang keys) and an icon. */
    public record Merchant(String id, Item icon) {

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            buf.writeId(BuiltInRegistries.ITEM, icon);
        }

        static Merchant decode(FriendlyByteBuf buf) {
            return new Merchant(buf.readUtf(64), buf.readById(BuiltInRegistries.ITEM));
        }
    }
}
