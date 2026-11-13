package com.soloeconomy.network;

import com.soloeconomy.market.Good;

import net.minecraft.network.FriendlyByteBuf;

/**
 * "What would this actually cost me?" - sent when the pointer settles on a row.
 *
 * <p>Totals cannot be worked out client-side by multiplying the unit price, because a bulk trade
 * walks the price curve as it executes. Only the server knows the real number.
 */
public record QuoteRequestPayload(Good good, int count) {

    public void encode(FriendlyByteBuf buf) {
        good.encode(buf);
        buf.writeVarInt(count);
    }

    public static QuoteRequestPayload decode(FriendlyByteBuf buf) {
        return new QuoteRequestPayload(Good.decode(buf), buf.readVarInt());
    }
}
