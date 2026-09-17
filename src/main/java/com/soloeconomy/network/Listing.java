package com.soloeconomy.network;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/**
 * One row of the market, as the client sees it.
 *
 * @param stockRatio current stock divided by equilibrium stock: above 1 means the market is
 *                   glutted and paying badly, below 1 means it is short and paying well
 */
public record Listing(Item item, float buyPrice, float sellPrice, float stockRatio) {

    public static final StreamCodec<RegistryFriendlyByteBuf, Listing> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), Listing::item,
            ByteBufCodecs.FLOAT, Listing::buyPrice,
            ByteBufCodecs.FLOAT, Listing::sellPrice,
            ByteBufCodecs.FLOAT, Listing::stockRatio,
            Listing::new);
}
