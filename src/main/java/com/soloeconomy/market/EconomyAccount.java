package com.soloeconomy.market;

import com.soloeconomy.network.BalanceSyncPayload;
import com.soloeconomy.network.ModNetwork;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Read and write a player's emerald account.
 *
 * <p>The balance lives in the player's persisted Forge data, which Forge copies across death and
 * respawn, so dying is never a way to lose (or duplicate) money. Every mutation syncs to the
 * owning client so the HUD stays honest.
 */
public final class EconomyAccount {

    private static final String DATA_KEY = "soloeconomy";
    private static final String BALANCE_KEY = "balance_cents";

    private EconomyAccount() {
    }

    /** Hundredths of an emerald per emerald. Balances are integers so money never drifts. */
    public static final long CENTS_PER_EMERALD = 100L;

    /** This mod's corner of the player's data: saved with the player and kept through death. */
    static CompoundTag data(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(DATA_KEY)) {
            persisted.put(DATA_KEY, new CompoundTag());
        }
        return persisted.getCompound(DATA_KEY);
    }

    /** False for a player who has never had an account, which is how a first login is recognised. */
    public static boolean hasAccount(Player player) {
        return data(player).contains(BALANCE_KEY);
    }

    /** The account balance, in hundredths of an emerald. */
    public static long balance(Player player) {
        return data(player).getLong(BALANCE_KEY);
    }

    public static void setBalance(Player player, long cents) {
        data(player).putLong(BALANCE_KEY, Math.max(0L, cents));
        sync(player);
    }

    /** "12", or "12.34" when there are odd cents. Whole amounts don't need the decimals. */
    public static String format(long cents) {
        long whole = cents / CENTS_PER_EMERALD;
        long part = Math.abs(cents % CENTS_PER_EMERALD);
        return part == 0L ? Long.toString(whole) : String.format("%d.%02d", whole, part);
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
        // Fake players (Create deployers, GameTest mocks) have no real channel, and a connection
        // without the mod can't receive it either.
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null
                && serverPlayer.connection.connection.channel() != null
                && ModNetwork.CHANNEL.isRemotePresent(serverPlayer.connection.connection)) {
            ModNetwork.sendTo(serverPlayer, new BalanceSyncPayload(balance(serverPlayer)));
        }
    }
}
