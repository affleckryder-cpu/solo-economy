package com.soloeconomy.network;

import com.soloeconomy.market.Good;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One row of the market, as the client sees it.
 *
 * @param stockRatio current stock divided by equilibrium stock: above 1 means the market is
 *                   glutted and paying badly, below 1 means it is short and paying well
 * @param locked     the player has not discovered this good yet, so it can be sold but not bought
 * @param deal       a BrokerDeals constant: fee-free to buy today, fee-free to sell today, or neither
 */
public record Listing(Good good, float buyPrice, float sellPrice, float stockRatio, boolean locked, byte deal) {

    public static final StreamCodec<RegistryFriendlyByteBuf, Listing> STREAM_CODEC = StreamCodec.composite(
            Good.STREAM_CODEC, Listing::good,
            ByteBufCodecs.FLOAT, Listing::buyPrice,
            ByteBufCodecs.FLOAT, Listing::sellPrice,
            ByteBufCodecs.FLOAT, Listing::stockRatio,
            ByteBufCodecs.BOOL, Listing::locked,
            ByteBufCodecs.BYTE, Listing::deal,
            Listing::new);
}
