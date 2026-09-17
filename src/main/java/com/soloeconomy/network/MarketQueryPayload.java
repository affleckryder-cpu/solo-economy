package com.soloeconomy.network;

import com.soloeconomy.SoloEconomy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client asks the server for everything matching a filter.
 *
 * <p>There is no page number: the whole match set comes back at once and the client scrolls it
 * locally, so dragging the scrollbar never waits on the network.
 */
public record MarketQueryPayload(String search, int sort, boolean inventoryOnly)
        implements CustomPacketPayload {

    public static final Type<MarketQueryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "market_query"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketQueryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), MarketQueryPayload::search,
                    ByteBufCodecs.VAR_INT, MarketQueryPayload::sort,
                    ByteBufCodecs.BOOL, MarketQueryPayload::inventoryOnly,
                    MarketQueryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
