package com.soloeconomy.market;

import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.network.ServerMarketHandler;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Progression: the market only sells you what you have found yourself. Carrying an item to a
 * stall (or selling it there) unlocks buying it, per player, for good.
 */
public final class Discovery {

    private static final String DISCOVERED_KEY = "discovered";

    private Discovery() {
    }

    /** Item ids as keys, so a removed mod's items just sit there unused instead of failing to load. */
    private static CompoundTag discovered(Player player) {
        CompoundTag data = EconomyAccount.data(player);
        if (!data.contains(DISCOVERED_KEY)) {
            data.put(DISCOVERED_KEY, new CompoundTag());
        }
        return data.getCompound(DISCOVERED_KEY);
    }

    public static boolean canBuy(Player player, Item item) {
        return !EconomyConfig.INSTANCE.requireDiscovery.get()
                || discovered(player).contains(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    public static void discover(Player player, Item item) {
        discovered(player).putBoolean(BuiltInRegistries.ITEM.getKey(item).toString(), true);
    }

    /** Unlocks everything tradeable in the player's main inventory. */
    public static void discoverCarried(Player player) {
        MarketCatalog catalog = MarketCatalog.active();
        for (ItemStack stack : player.getInventory().items) {
            if (ServerMarketHandler.isTradeableStack(stack) && catalog.isTradeable(stack.getItem())) {
                discover(player, stack.getItem());
            }
        }
    }
}
