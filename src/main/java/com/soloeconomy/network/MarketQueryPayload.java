package com.soloeconomy.network;

import com.soloeconomy.SoloEconomy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client asks for one merchant's goods, or - when {@code search} is not blank - for matches
 * across every merchant.
 */
public record MarketQueryPayload(String search, boolean inventoryOnly, String merchant)
        implements CustomPacketPayload {

    public static final Type<MarketQueryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "market_query"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketQueryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), MarketQueryPayload::search,
                    ByteBufCodecs.BOOL, MarketQueryPayload::inventoryOnly,
                    ByteBufCodecs.stringUtf8(64), MarketQueryPayload::merchant,
                    MarketQueryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
