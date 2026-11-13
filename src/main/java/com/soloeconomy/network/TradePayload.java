package com.soloeconomy.network;

import com.soloeconomy.market.Good;

import net.minecraft.network.FriendlyByteBuf;

/** Client asks to buy or sell a quantity of one good. The server re-prices it from scratch. */
public record TradePayload(Good good, int count, boolean buying) {

    public void encode(FriendlyByteBuf buf) {
        good.encode(buf);
        buf.writeVarInt(count);
        buf.writeBoolean(buying);
    }

    public static TradePayload decode(FriendlyByteBuf buf) {
        return new TradePayload(Good.decode(buf), buf.readVarInt(), buf.readBoolean());
    }
}
