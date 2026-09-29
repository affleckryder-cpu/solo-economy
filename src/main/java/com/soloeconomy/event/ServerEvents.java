package com.soloeconomy.event;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.command.PriceCommand;
import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.market.BasePriceLoader;
import com.soloeconomy.market.EconomyAccount;
import com.soloeconomy.market.MarketCatalog;
import com.soloeconomy.market.MerchantLoader;
import com.soloeconomy.registry.ModAttachments;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;

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
        event.addListener(new MerchantLoader());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PriceCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        EconomyConfig.INSTANCE.migrate();
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
        if (player.hasData(ModAttachments.LEGACY_BALANCE.get())) {
            // 0.1.0 stored whole emeralds. Convert once, then drop the old attachment.
            long emeralds = player.getData(ModAttachments.LEGACY_BALANCE.get());
            player.removeData(ModAttachments.LEGACY_BALANCE.get());
            EconomyAccount.setBalance(player, emeralds * EconomyAccount.CENTS_PER_EMERALD);
        } else if (!player.hasData(ModAttachments.BALANCE_CENTS.get())) {
            // An absent attachment means this player has never had an account before.
            EconomyAccount.setBalance(player,
                    EconomyConfig.INSTANCE.startingBalance.get() * EconomyAccount.CENTS_PER_EMERALD);
        } else {
            EconomyAccount.sync(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MarketCatalog.setActive(MarketCatalog.empty());
    }

    public static void rebuildCatalog(MinecraftServer server) {
        if (server == null) {
            return;
        }
        BasePriceLoader.Parsed seeds = BasePriceLoader.current();
        double multiplier = EconomyConfig.INSTANCE.priceMultiplier.get();
        Map<Item, Double> prices = new HashMap<>();
        seeds.prices().forEach((item, price) -> prices.put(item, price * multiplier));
        prices.putAll(PriceCommand.overrides());
        MarketCatalog.setActive(MarketCatalog.build(
                prices,
                seeds.untradeable(),
                MerchantLoader.current(),
                server.getRecipeManager(),
                server.registryAccess()));
    }
}
