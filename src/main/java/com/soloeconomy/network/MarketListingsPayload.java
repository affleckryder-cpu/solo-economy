package com.soloeconomy.network;

import com.soloeconomy.SoloEconomy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Every listing matching the client's filter, in one go.
 *
 * <p>At roughly fifteen bytes a row the full catalogue is about fourteen kilobytes, which is
 * cheap enough to resend on each query and buys instant local scrolling. Unit prices only -
 * bulk totals move along the price curve and are quoted separately on demand.
 *
 * @param truncated whether the match set was clipped at the transport cap, so the UI can say so
 */
public record MarketListingsPayload(List<Listing> listings, boolean truncated,
                                    float spread, long balance) implements CustomPacketPayload {

    public static final Type<MarketListingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "market_listings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketListingsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Listing.STREAM_CODEC.apply(ByteBufCodecs.list(2048)), MarketListingsPayload::listings,
                    ByteBufCodecs.BOOL, MarketListingsPayload::truncated,
                    ByteBufCodecs.FLOAT, MarketListingsPayload::spread,
                    ByteBufCodecs.VAR_LONG, MarketListingsPayload::balance,
                    MarketListingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
