package com.soloeconomy.event;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.command.PriceCommand;
import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.market.BasePriceLoader;
import com.soloeconomy.market.EconomyAccount;
import com.soloeconomy.market.MarketCatalog;
import com.soloeconomy.market.MerchantLoader;
import com.soloeconomy.registry.ModBlocks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Server lifecycle wiring: keep the price book in step with the loaded datapacks and recipes,
 * and keep every client's balance display honest.
 */
@Mod.EventBusSubscriber(modid = SoloEconomy.MOD_ID)
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
        if (!EconomyAccount.hasAccount(player)) {
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

    /** Chests a stock certificate can turn up in, and how often. */
    private static final Set<String> CERTIFICATE_CHESTS = Set.of("simple_dungeon", "abandoned_mineshaft",
            "desert_pyramid", "jungle_temple", "shipwreck_treasure", "buried_treasure", "bastion_treasure",
            "stronghold_corridor", "woodland_mansion", "end_city_treasure", "ancient_city", "pillager_outpost",
            "ruined_portal");
    private static final float CERTIFICATE_CHANCE = 0.25F;

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        String name = event.getName().toString();
        if (name.startsWith("minecraft:chests/") && CERTIFICATE_CHESTS.contains(name.substring("minecraft:chests/".length()))) {
            event.getTable().addPool(LootPool.lootPool()
                    .name("soloeconomy:stock_certificate")
                    .when(LootItemRandomChanceCondition.randomChance(CERTIFICATE_CHANCE))
                    .add(LootItem.lootTableItem(ModBlocks.STOCK_CERTIFICATE.get()))
                    .build());
        }
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
