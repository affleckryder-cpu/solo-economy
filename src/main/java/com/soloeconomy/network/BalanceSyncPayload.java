package com.soloeconomy.network;

import com.soloeconomy.SoloEconomy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server pushes the player's emerald balance so the HUD can draw it. */
public record BalanceSyncPayload(long balance) implements CustomPacketPayload {

    public static final Type<BalanceSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "balance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BalanceSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, BalanceSyncPayload::balance,
                    BalanceSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
