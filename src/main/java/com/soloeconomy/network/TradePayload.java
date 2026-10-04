package com.soloeconomy.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;

/** Client asks to buy or sell a quantity of one item. The server re-prices it from scratch. */
public record TradePayload(Item item, int count, boolean buying) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeId(BuiltInRegistries.ITEM, item);
        buf.writeVarInt(count);
        buf.writeBoolean(buying);
    }

    public static TradePayload decode(FriendlyByteBuf buf) {
        return new TradePayload(buf.readById(BuiltInRegistries.ITEM), buf.readVarInt(), buf.readBoolean());
    }
}
