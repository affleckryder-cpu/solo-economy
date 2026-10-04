package com.soloeconomy.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;

/**
 * Exact totals for a hovered row, with "All" already resolved into a real number.
 *
 * @param sellCount how many you could actually sell - what you are carrying, capped by the request
 * @param buyCount  how many you could actually afford, capped by the request
 */
public record QuotePayload(Item item, int sellCount, long sellTotal, int buyCount, long buyTotal) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeId(BuiltInRegistries.ITEM, item);
        buf.writeVarInt(sellCount);
        buf.writeVarLong(sellTotal);
        buf.writeVarInt(buyCount);
        buf.writeVarLong(buyTotal);
    }

    public static QuotePayload decode(FriendlyByteBuf buf) {
        return new QuotePayload(buf.readById(BuiltInRegistries.ITEM), buf.readVarInt(), buf.readVarLong(),
                buf.readVarInt(), buf.readVarLong());
    }
}
