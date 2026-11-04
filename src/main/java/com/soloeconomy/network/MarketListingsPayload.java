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
 * The merchant list for the sidebar, the goods matching the client's query, and prices for what the
 * player is carrying (for the inventory panel). All small, so simply resent on every query.
 */
public record MarketListingsPayload(List<Merchant> merchants, List<Listing> listings, List<Listing> carried,
                                    float spread, long balance) implements CustomPacketPayload {

    /**
     * The most goods one reply carries. A listing is about 18 bytes, so this stays well inside the
     * 1 MB packet limit. It was 2,048 once, and an open-market modpack with 2,069 priced items
     * disconnected the player as soon as the stall opened.
     */
    public static final int MAX_LISTINGS = 20_000;


    public static final Type<MarketListingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "market_listings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketListingsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Merchant.STREAM_CODEC.apply(ByteBufCodecs.list(256)), MarketListingsPayload::merchants,
                    Listing.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LISTINGS)), MarketListingsPayload::listings,
                    Listing.STREAM_CODEC.apply(ByteBufCodecs.list(64)), MarketListingsPayload::carried,
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
