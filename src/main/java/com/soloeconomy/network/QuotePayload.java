package com.soloeconomy.network;

import com.soloeconomy.market.Good;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Exact totals for a hovered row, with "All" already resolved into a real number.
 *
 * @param sellCount how many you could actually sell - what you are carrying, capped by the request
 * @param buyCount  how many you could actually afford, capped by the request
 */
public record QuotePayload(Good good, int sellCount, long sellTotal, int buyCount, long buyTotal) {

    public void encode(FriendlyByteBuf buf) {
        good.encode(buf);
        buf.writeVarInt(sellCount);
        buf.writeVarLong(sellTotal);
        buf.writeVarInt(buyCount);
        buf.writeVarLong(buyTotal);
    }

    public static QuotePayload decode(FriendlyByteBuf buf) {
        return new QuotePayload(Good.decode(buf), buf.readVarInt(), buf.readVarLong(),
                buf.readVarInt(), buf.readVarLong());
    }
}
