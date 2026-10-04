package com.soloeconomy.network;

import net.minecraft.network.FriendlyByteBuf;

/** Server pushes the player's emerald balance so the HUD can draw it. */
public record BalanceSyncPayload(long balance) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(balance);
    }

    public static BalanceSyncPayload decode(FriendlyByteBuf buf) {
        return new BalanceSyncPayload(buf.readVarLong());
    }
}
