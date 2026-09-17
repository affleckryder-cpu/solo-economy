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
 * Exact totals for a hovered row, with "All" already resolved into a real number.
 *
 * @param sellCount how many you could actually sell - what you are carrying, capped by the request
 * @param buyCount  how many you could actually afford, capped by the request
 */
public record QuotePayload(Item item, int sellCount, long sellTotal, int buyCount, long buyTotal)
        implements CustomPacketPayload {

    public static final Type<QuotePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "quote"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuotePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(Registries.ITEM), QuotePayload::item,
                    ByteBufCodecs.VAR_INT, QuotePayload::sellCount,
                    ByteBufCodecs.VAR_LONG, QuotePayload::sellTotal,
                    ByteBufCodecs.VAR_INT, QuotePayload::buyCount,
                    ByteBufCodecs.VAR_LONG, QuotePayload::buyTotal,
                    QuotePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
