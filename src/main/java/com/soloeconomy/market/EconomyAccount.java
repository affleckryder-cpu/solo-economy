package com.soloeconomy.market;

import com.soloeconomy.network.BalanceSyncPayload;
import com.soloeconomy.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Read and write a player's emerald account.
 *
 * <p>The balance lives as a data attachment that copies on death, so dying is never a way to lose
 * (or duplicate) money. Every mutation syncs to the owning client so the HUD stays honest.
 */
public final class EconomyAccount {

    private EconomyAccount() {
    }

    public static long balance(Player player) {
        return player.getData(ModAttachments.BALANCE.get());
    }

    public static void setBalance(Player player, long amount) {
        player.setData(ModAttachments.BALANCE.get(), Math.max(0L, amount));
        sync(player);
    }

    public static void deposit(Player player, long amount) {
        if (amount <= 0L) {
            return;
        }
        // Saturating add: a long overflow would wrap a fortune into a debt.
        long current = balance(player);
        long next = current + amount;
        setBalance(player, next < current ? Long.MAX_VALUE : next);
    }

    /** Deducts {@code amount} only if the player can afford it. */
    public static boolean withdraw(Player player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        long current = balance(player);
        if (current < amount) {
            return false;
        }
        setBalance(player, current - amount);
        return true;
    }

    public static boolean canAfford(Player player, long amount) {
        return balance(player) >= amount;
    }

    public static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new BalanceSyncPayload(balance(serverPlayer)));
        }
    }
}
