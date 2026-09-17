package com.soloeconomy.network;

import com.soloeconomy.SoloEconomy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Move physical emeralds between the inventory and the account, 1:1.
 *
 * @param amount   emeralds to move, or -1 for "as many as possible"
 * @param deposit  true to put emeralds in, false to take them out
 */
public record TransferPayload(int amount, boolean deposit) implements CustomPacketPayload {

    public static final Type<TransferPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "transfer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransferPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TransferPayload::amount,
                    ByteBufCodecs.BOOL, TransferPayload::deposit,
                    TransferPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
