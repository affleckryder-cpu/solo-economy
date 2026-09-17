package com.soloeconomy.network;

import com.soloeconomy.SoloEconomy;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * "What would this actually cost me?" - sent when the pointer settles on a row.
 *
 * <p>Totals cannot be worked out client-side by multiplying the unit price, because a bulk trade
 * walks the price curve as it executes. Only the server knows the real number.
 */
public record QuoteRequestPayload(Item item, int count) implements CustomPacketPayload {

    public static final Type<QuoteRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "quote_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuoteRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(Registries.ITEM), QuoteRequestPayload::item,
                    ByteBufCodecs.VAR_INT, QuoteRequestPayload::count,
                    QuoteRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
