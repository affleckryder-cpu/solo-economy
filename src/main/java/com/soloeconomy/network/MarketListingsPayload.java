package com.soloeconomy.network;

import com.soloeconomy.SoloEconomy;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * The merchant list for the sidebar, plus the goods matching the client's query. Both are small -
 * a dozen merchants and a few dozen rows - so they're simply resent on every query.
 */
public record MarketListingsPayload(List<Merchant> merchants, List<Listing> listings,
                                    float spread, long balance) implements CustomPacketPayload {

    public static final Type<MarketListingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "market_listings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketListingsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Merchant.STREAM_CODEC.apply(ByteBufCodecs.list(64)), MarketListingsPayload::merchants,
                    Listing.STREAM_CODEC.apply(ByteBufCodecs.list(2048)), MarketListingsPayload::listings,
                    ByteBufCodecs.FLOAT, MarketListingsPayload::spread,
                    ByteBufCodecs.VAR_LONG, MarketListingsPayload::balance,
                    MarketListingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** What the sidebar needs: the id (its lang keys) and an icon. */
    public record Merchant(String id, Item icon) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Merchant> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Merchant::id,
                ByteBufCodecs.registry(Registries.ITEM), Merchant::icon,
                Merchant::new);
    }
}
