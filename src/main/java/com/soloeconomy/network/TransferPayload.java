package com.soloeconomy.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Move physical emeralds between the inventory and the account, 1:1.
 *
 * @param amount   emeralds to move, or -1 for "as many as possible"
 * @param deposit  true to put emeralds in, false to take them out
 */
public record TransferPayload(int amount, boolean deposit) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(amount);
        buf.writeBoolean(deposit);
    }

    public static TransferPayload decode(FriendlyByteBuf buf) {
        return new TransferPayload(buf.readVarInt(), buf.readBoolean());
    }
}
