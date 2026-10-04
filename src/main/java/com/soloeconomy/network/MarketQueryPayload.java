package com.soloeconomy.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client asks for one merchant's goods, or - when {@code search} is not blank - for matches
 * across every merchant.
 */
public record MarketQueryPayload(String search, boolean inventoryOnly, String merchant) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(search, 64);
        buf.writeBoolean(inventoryOnly);
        buf.writeUtf(merchant, 64);
    }

    public static MarketQueryPayload decode(FriendlyByteBuf buf) {
        return new MarketQueryPayload(buf.readUtf(64), buf.readBoolean(), buf.readUtf(64));
    }
}
