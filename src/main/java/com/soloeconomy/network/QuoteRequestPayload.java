package com.soloeconomy.network;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.market.Good;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * "What would this actually cost me?" - sent when the pointer settles on a row.
 *
 * <p>Totals cannot be worked out client-side by multiplying the unit price, because a bulk trade
 * walks the price curve as it executes. Only the server knows the real number.
 */
public record QuoteRequestPayload(Good good, int count) implements CustomPacketPayload {

    public static final Type<QuoteRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "quote_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuoteRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Good.STREAM_CODEC, QuoteRequestPayload::good,
                    ByteBufCodecs.VAR_INT, QuoteRequestPayload::count,
                    QuoteRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
