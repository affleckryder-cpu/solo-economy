package com.soloeconomy.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;

/**
 * "What would this actually cost me?" - sent when the pointer settles on a row.
 *
 * <p>Totals cannot be worked out client-side by multiplying the unit price, because a bulk trade
 * walks the price curve as it executes. Only the server knows the real number.
 */
public record QuoteRequestPayload(Item item, int count) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeId(BuiltInRegistries.ITEM, item);
        buf.writeVarInt(count);
    }

    public static QuoteRequestPayload decode(FriendlyByteBuf buf) {
        return new QuoteRequestPayload(buf.readById(BuiltInRegistries.ITEM), buf.readVarInt());
    }
}
