package com.soloeconomy.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;

/**
 * One row of the market, as the client sees it.
 *
 * @param stockRatio current stock divided by equilibrium stock: above 1 means the market is
 *                   glutted and paying badly, below 1 means it is short and paying well
 * @param locked     the player has not discovered this item yet, so it can be sold but not bought
 * @param deal       a BrokerDeals constant: fee-free to buy today, fee-free to sell today, or neither
 */
public record Listing(Item item, float buyPrice, float sellPrice, float stockRatio, boolean locked, byte deal) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeId(BuiltInRegistries.ITEM, item);
        buf.writeFloat(buyPrice);
        buf.writeFloat(sellPrice);
        buf.writeFloat(stockRatio);
        buf.writeBoolean(locked);
        buf.writeByte(deal);
    }

    public static Listing decode(FriendlyByteBuf buf) {
        return new Listing(buf.readById(BuiltInRegistries.ITEM), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readByte());
    }
}
