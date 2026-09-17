package com.soloeconomy.event;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.market.BasePriceLoader;
import com.soloeconomy.market.EconomyAccount;
import com.soloeconomy.market.MarketCatalog;
import com.soloeconomy.registry.ModAttachments;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Server lifecycle wiring: keep the price book in step with the loaded datapacks and recipes,
 * and keep every client's balance display honest.
 */
@EventBusSubscriber(modid = SoloEconomy.MOD_ID)
public final class ServerEvents {

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BasePriceLoader());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rebuildCatalog(event.getServer());
    }

    /**
     * Fires with a null player once a datapack reload has finished, which is the first moment both
     * the seed prices and the recipe manager are guaranteed to be up to date.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            rebuildCatalog(event.getPlayerList().getServer());
        } else {
            EconomyAccount.sync(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // An absent attachment means this player has never had an account before.
        if (!player.hasData(ModAttachments.BALANCE.get())) {
            EconomyAccount.setBalance(player, EconomyConfig.INSTANCE.startingBalance.get());
        } else {
            EconomyAccount.sync(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MarketCatalog.setActive(MarketCatalog.empty());
    }

    private static void rebuildCatalog(MinecraftServer server) {
        if (server == null) {
            return;
        }
        BasePriceLoader.Parsed seeds = BasePriceLoader.current();
        MarketCatalog.setActive(MarketCatalog.build(
                seeds.prices(),
                seeds.untradeable(),
                server.getRecipeManager(),
                server.registryAccess()));
    }
}
