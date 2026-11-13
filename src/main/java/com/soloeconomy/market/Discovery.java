package com.soloeconomy.market;

import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Progression: the market only sells you what you have found yourself. Carrying an item to a
 * stall (or selling it there) unlocks buying it, per player, for good. Each potion counts as its
 * own find: carrying a Potion of Healing unlocks that, not every potion.
 */
public final class Discovery {

    private Discovery() {
    }

    public static boolean canBuy(ServerPlayer player, Good good) {
        return !EconomyConfig.INSTANCE.requireDiscovery.get()
                || player.getData(ModAttachments.DISCOVERED.get()).contains(good.key());
    }

    public static void discover(ServerPlayer player, Good good) {
        player.getData(ModAttachments.DISCOVERED.get()).add(good.key());
    }

    /** Unlocks everything tradeable in the player's main inventory. */
    public static void discoverCarried(ServerPlayer player) {
        MarketCatalog catalog = MarketCatalog.active();
        for (ItemStack stack : player.getInventory().items) {
            Good good = Good.of(stack);
            if (good != null && catalog.isTradeable(good)) {
                discover(player, good);
            }
        }
    }
}
