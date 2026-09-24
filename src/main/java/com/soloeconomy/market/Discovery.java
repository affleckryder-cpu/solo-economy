package com.soloeconomy.market;

import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.network.ServerMarketHandler;
import com.soloeconomy.registry.ModAttachments;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Progression: the market only sells you what you have found yourself. Carrying an item to a
 * stall (or selling it there) unlocks buying it, per player, for good.
 */
public final class Discovery {

    private Discovery() {
    }

    public static boolean canBuy(ServerPlayer player, Item item) {
        return !EconomyConfig.INSTANCE.requireDiscovery.get()
                || player.getData(ModAttachments.DISCOVERED.get()).contains(BuiltInRegistries.ITEM.getKey(item));
    }

    public static void discover(ServerPlayer player, Item item) {
        player.getData(ModAttachments.DISCOVERED.get()).add(BuiltInRegistries.ITEM.getKey(item));
    }

    /** Unlocks everything tradeable in the player's main inventory. */
    public static void discoverCarried(ServerPlayer player) {
        MarketCatalog catalog = MarketCatalog.active();
        for (ItemStack stack : player.getInventory().items) {
            if (ServerMarketHandler.isTradeableStack(stack) && catalog.isTradeable(stack.getItem())) {
                discover(player, stack.getItem());
            }
        }
    }
}
