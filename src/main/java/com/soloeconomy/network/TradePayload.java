package com.soloeconomy.network;

import com.soloeconomy.SoloEconomy;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Client asks to buy or sell a quantity of one item. The server re-prices it from scratch. */
public record TradePayload(Item item, int count, boolean buying) implements CustomPacketPayload {

    public static final Type<TradePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "trade"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(Registries.ITEM), TradePayload::item,
                    ByteBufCodecs.VAR_INT, TradePayload::count,
                    ByteBufCodecs.BOOL, TradePayload::buying,
                    TradePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
