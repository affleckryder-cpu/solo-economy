package com.soloeconomy.market;

import com.soloeconomy.config.EconomyConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Progression: the market only sells you what you have found yourself. Carrying an item to a
 * stall (or selling it there) unlocks buying it, per player, for good. Each potion counts as its
 * own find: carrying a Potion of Healing unlocks that, not every potion.
 */
public final class Discovery {

    private static final String DISCOVERED_KEY = "discovered";

    private Discovery() {
    }

    /**
     * {@link Good#key()}s as keys, so a removed mod's items just sit there unused instead of
     * failing to load. A plain item's key is its id, as before potions, so old saves still read.
     */
    private static CompoundTag discovered(Player player) {
        CompoundTag data = EconomyAccount.data(player);
        if (!data.contains(DISCOVERED_KEY)) {
            data.put(DISCOVERED_KEY, new CompoundTag());
        }
        return data.getCompound(DISCOVERED_KEY);
    }

    public static boolean canBuy(Player player, Good good) {
        return !EconomyConfig.INSTANCE.requireDiscovery.get() || discovered(player).contains(good.key());
    }

    public static void discover(Player player, Good good) {
        discovered(player).putBoolean(good.key(), true);
    }

    /** Unlocks everything tradeable in the player's main inventory. */
    public static void discoverCarried(Player player) {
        MarketCatalog catalog = MarketCatalog.active();
        for (ItemStack stack : player.getInventory().items) {
            Good good = Good.of(stack);
            if (good != null && catalog.isTradeable(good)) {
                discover(player, good);
            }
        }
    }
}
