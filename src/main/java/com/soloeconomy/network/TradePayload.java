package com.soloeconomy.network;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.market.Good;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client asks to buy or sell a quantity of one good. The server re-prices it from scratch. */
public record TradePayload(Good good, int count, boolean buying) implements CustomPacketPayload {

    public static final Type<TradePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "trade"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradePayload> STREAM_CODEC =
            StreamCodec.composite(
                    Good.STREAM_CODEC, TradePayload::good,
                    ByteBufCodecs.VAR_INT, TradePayload::count,
                    ByteBufCodecs.BOOL, TradePayload::buying,
                    TradePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
