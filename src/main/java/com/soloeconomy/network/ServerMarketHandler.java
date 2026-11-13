package com.soloeconomy.network;

import com.soloeconomy.market.BrokerDeals;
import com.soloeconomy.market.Discovery;
import com.soloeconomy.market.EconomyAccount;
import com.soloeconomy.market.Good;
import com.soloeconomy.market.MarketCatalog;
import com.soloeconomy.market.MarketData;
import com.soloeconomy.menu.MarketMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every trade is priced and validated here, on the server. The client sends nothing but a good,
 * a count and a direction - never a price - so a modified client can ask for a bad trade but
 * cannot invent a good one.
 */
public final class ServerMarketHandler {

    /** A count of -1 from the client means "as many as possible", resolved here. */
    public static final int COUNT_MAX = -1;
    /** Enough for a shulker box of anything; also stops a hostile client asking for 2^31 items. */
    private static final int MAX_TRADE_COUNT = 2304;
    /** Only the 36 main inventory slots trade. Worn armour and the offhand are left alone. */
    private static final int TRADEABLE_SLOTS = 36;

    private ServerMarketHandler() {
    }

    // ------------------------------------------------------------------
    // Catalogue browsing
    // ------------------------------------------------------------------

    public static void handleQuery(MarketQueryPayload payload, ServerPlayer player) {
        if (!(player.containerMenu instanceof MarketMenu menu)) {
            return;
        }

        MarketCatalog catalog = MarketCatalog.active();
        MarketData data = MarketData.get(player.server);
        long gameTime = player.level().getGameTime();
        double spread = menu.spread();
        Discovery.discoverCarried(player);

        // A search looks across every merchant; otherwise show one merchant's goods in their own order.
        String search = payload.search().trim().toLowerCase(Locale.ROOT);
        MarketCatalog.Merchant merchant = catalog.merchant(payload.merchant());
        List<Good> deals = menu.isStaffed() ? BrokerDeals.goods(player.server) : List.of();
        List<Good> source = !search.isEmpty() ? catalog.goods()
                : BrokerDeals.MERCHANT_ID.equals(payload.merchant()) && !deals.isEmpty() ? deals
                : merchant != null ? merchant.goods() : List.of();

        List<Listing> carried = new ArrayList<>();
        for (int slot = 0; slot < TRADEABLE_SLOTS; slot++) {
            Good good = Good.of(player.getInventory().getItem(slot));
            if (good != null && catalog.isTradeable(good) && carried.stream().noneMatch(l -> l.good().equals(good))) {
                carried.add(listing(data, player, menu, good, gameTime));
            }
        }

        List<Listing> listings = new ArrayList<>();
        for (Good good : source) {
            if (!search.isEmpty() && !matchesSearch(good, search)) {
                continue;
            }
            if (payload.inventoryOnly() && countInInventory(player.getInventory(), good) <= 0) {
                continue;
            }
            if (listings.size() >= MarketListingsPayload.MAX_LISTINGS) {
                break; // searching narrows it down; the rest are a scroll too far anyway
            }
            listings.add(listing(data, player, menu, good, gameTime));
        }

        List<MarketListingsPayload.Merchant> merchants = new ArrayList<>();
        for (MarketCatalog.Merchant m : catalog.merchants()) {
            merchants.add(new MarketListingsPayload.Merchant(m.id(), m.icon()));
        }
        if (!deals.isEmpty()) {
            merchants.add(new MarketListingsPayload.Merchant(BrokerDeals.MERCHANT_ID, Items.BELL));
        }
        ModNetwork.sendTo(player, new MarketListingsPayload(merchants, listings, carried, (float) spread,
                EconomyAccount.balance(player)));
    }

    private static Listing listing(MarketData data, ServerPlayer player, MarketMenu menu, Good good, long gameTime) {
        return new Listing(good,
                (float) data.spotBuyPrice(good, gameTime, fee(player, menu, good, true)),
                (float) data.spotSellPrice(good, gameTime, fee(player, menu, good, false)),
                (float) data.supplyRatio(good, gameTime),
                !canBuy(player, menu, good),
                BrokerDeals.dealFor(player.server, good, menu.isStaffed()));
    }

    /** Discovered, or on offer from this stall's Broker today: a deal is how you get hold of something new. */
    private static boolean canBuy(ServerPlayer player, MarketMenu menu, Good good) {
        return Discovery.canBuy(player, good)
                || BrokerDeals.dealFor(player.server, good, menu.isStaffed()) == BrokerDeals.ON_OFFER;
    }

    /** The half-spread this stall charges on one side of a trade in this good, Broker deals included. */
    private static double fee(ServerPlayer player, MarketMenu menu, Good good, boolean buying) {
        return BrokerDeals.spread(player.server, good, buying, menu.isStaffed());
    }

    /**
     * Price a hovered row for real. Both directions are resolved against what the player actually
     * has - emeralds to spend, goods to sell - so "All" comes back as a concrete number rather
     * than leaving the UI to guess.
     */
    public static void handleQuoteRequest(QuoteRequestPayload payload, ServerPlayer player) {
        if (!(player.containerMenu instanceof MarketMenu menu)) {
            return;
        }

        Good good = payload.good();
        if (!MarketCatalog.active().isTradeable(good)) {
            return;
        }

        MarketData data = MarketData.get(player.server);
        long gameTime = player.level().getGameTime();
        double sellFee = fee(player, menu, good, false);
        double buyFee = fee(player, menu, good, true);
        boolean wantsMax = payload.count() == COUNT_MAX;
        int requested = wantsMax ? MAX_TRADE_COUNT : Mth.clamp(payload.count(), 1, MAX_TRADE_COUNT);

        int sellCount = Math.min(requested, countInInventory(player.getInventory(), good));
        long sellTotal = sellCount > 0
                ? data.quoteSell(good, sellCount, gameTime, sellFee).cents()
                : 0L;

        int buyCount = !canBuy(player, menu, good) ? 0
                : wantsMax
                ? data.maxAffordable(good, EconomyAccount.balance(player), gameTime, buyFee, MAX_TRADE_COUNT)
                : requested;
        long buyTotal = buyCount > 0
                ? data.quoteBuy(good, buyCount, gameTime, buyFee).cents()
                : 0L;

        ModNetwork.sendTo(player, new QuotePayload(good, sellCount, sellTotal, buyCount, buyTotal));
    }

    private static boolean matchesSearch(Good good, String lowerSearch) {
        ItemStack stack = good.stack(1);
        // The stack's id, not the item's, so "healing" finds the Potion of Healing.
        if (stack.getDescriptionId().toLowerCase(Locale.ROOT).contains(lowerSearch)
                || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(lowerSearch)) {
            return true;
        }
        // The market lists a potion by its effect ("Speed"), so that has to find it too.
        if (good.potion() != null) {
            for (MobEffectInstance effect : good.potion().getEffects()) {
                if (Component.translatable(effect.getDescriptionId()).getString().toLowerCase(Locale.ROOT).contains(lowerSearch)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Trading
    // ------------------------------------------------------------------

    public static void handleTrade(TradePayload payload, ServerPlayer player) {
        if (!(player.containerMenu instanceof MarketMenu menu) || !menu.stillValid(player)) {
            return;
        }

        Good good = payload.good();
        if (!MarketCatalog.active().isTradeable(good)) {
            feedback(player, Component.translatable("message.soloeconomy.untradeable"), true);
            return;
        }

        MarketData data = MarketData.get(player.server);
        long gameTime = player.level().getGameTime();
        double spread = fee(player, menu, good, payload.buying());
        boolean wantsMax = payload.count() == COUNT_MAX;

        if (payload.buying()) {
            if (!canBuy(player, menu, good)) {
                feedback(player, Component.translatable("message.soloeconomy.locked",
                        good.name()), true);
                return;
            }
            int requested = wantsMax
                    ? data.maxAffordable(good, EconomyAccount.balance(player), gameTime, spread, MAX_TRADE_COUNT)
                    : Mth.clamp(payload.count(), 1, MAX_TRADE_COUNT);
            if (requested <= 0) {
                feedback(player, Component.translatable("message.soloeconomy.cannot_afford",
                        EconomyAccount.format(data.quoteBuy(good, 1, gameTime, spread).cents()),
                        EconomyAccount.format(EconomyAccount.balance(player))), true);
                return;
            }
            buy(player, data, good, requested, gameTime, spread);
        } else {
            int requested = wantsMax ? MAX_TRADE_COUNT : Mth.clamp(payload.count(), 1, MAX_TRADE_COUNT);
            sell(player, data, good, requested, gameTime, spread);
        }

        syncInventory(player);
        EconomyAccount.sync(player);
    }

    private static void buy(ServerPlayer player, MarketData data, Good good,
                            int count, long gameTime, double spread) {
        MarketData.Quote quote = data.quoteBuy(good, count, gameTime, spread);
        if (quote.isEmpty()) {
            return;
        }

        if (!EconomyAccount.canAfford(player, quote.cents())) {
            feedback(player, Component.translatable("message.soloeconomy.cannot_afford",
                    EconomyAccount.format(quote.cents()),
                    EconomyAccount.format(EconomyAccount.balance(player))), true);
            return;
        }

        // Price is locked in before the market state moves, so what you were quoted is what you pay.
        data.commitBuy(good, count, gameTime, spread);
        EconomyAccount.withdraw(player, quote.cents());
        giveItems(player, good, count);

        feedback(player, Component.translatable("message.soloeconomy.bought",
                count, good.name(), EconomyAccount.format(quote.cents())), false);
    }

    private static void sell(ServerPlayer player, MarketData data, Good good,
                             int count, long gameTime, double spread) {
        int available = countInInventory(player.getInventory(), good);
        if (available <= 0) {
            feedback(player, Component.translatable("message.soloeconomy.none_to_sell",
                    good.name()), true);
            return;
        }

        int actual = Math.min(count, available);
        MarketData.Quote quote = data.quoteSell(good, actual, gameTime, spread);
        if (quote.isEmpty()) {
            return;
        }
        if (quote.cents() <= 0L) {
            feedback(player, Component.translatable("message.soloeconomy.worthless",
                    good.name()), true);
            return;
        }

        Discovery.discover(player, good);
        // Take the goods first; only pay for what actually left the inventory.
        int removed = removeFromInventory(player.getInventory(), good, actual);
        if (removed <= 0) {
            return;
        }
        MarketData.Quote settled = removed == actual
                ? data.commitSell(good, actual, gameTime, spread)
                : data.commitSell(good, removed, gameTime, spread);

        EconomyAccount.deposit(player, settled.cents());
        feedback(player, Component.translatable("message.soloeconomy.sold",
                removed, good.name(), EconomyAccount.format(settled.cents())), false);
    }

    // ------------------------------------------------------------------
    // Emerald deposit / withdrawal, always 1:1
    // ------------------------------------------------------------------

    public static void handleTransfer(TransferPayload payload, ServerPlayer player) {
        if (!(player.containerMenu instanceof MarketMenu menu) || !menu.stillValid(player)) {
            return;
        }

        Inventory inventory = player.getInventory();
        Good emerald = Good.of(Items.EMERALD);

        if (payload.deposit()) {
            int held = countInInventory(inventory, emerald);
            int amount = payload.amount() < 0 ? held : Math.min(payload.amount(), held);
            if (amount <= 0) {
                feedback(player, Component.translatable("message.soloeconomy.no_emeralds"), true);
                return;
            }
            int removed = removeFromInventory(inventory, emerald, amount);
            EconomyAccount.deposit(player, removed * EconomyAccount.CENTS_PER_EMERALD);
            feedback(player, Component.translatable("message.soloeconomy.deposited", removed), false);
        } else {
            long whole = EconomyAccount.balance(player) / EconomyAccount.CENTS_PER_EMERALD;
            long requested = payload.amount() < 0 ? whole : payload.amount();
            int amount = (int) Math.min(Math.min(requested, whole), MAX_TRADE_COUNT);
            if (amount <= 0) {
                feedback(player, Component.translatable("message.soloeconomy.no_balance"), true);
                return;
            }
            EconomyAccount.withdraw(player, amount * EconomyAccount.CENTS_PER_EMERALD);
            giveItems(player, emerald, amount);
            feedback(player, Component.translatable("message.soloeconomy.withdrew", amount), false);
        }

        syncInventory(player);
        EconomyAccount.sync(player);
    }

    // ------------------------------------------------------------------
    // Inventory plumbing
    // ------------------------------------------------------------------

    /** Only stacks that are exactly this good count; see {@link Good#of(ItemStack)}. */
    private static int countInInventory(Inventory inventory, Good good) {
        int total = 0;
        for (int slot = 0; slot < TRADEABLE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() == good.item() && good.equals(Good.of(stack))) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int removeFromInventory(Inventory inventory, Good good, int count) {
        int remaining = count;
        for (int slot = 0; slot < TRADEABLE_SLOTS && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() != good.item() || !good.equals(Good.of(stack))) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            remaining -= taken;
        }
        inventory.setChanged();
        return count - remaining;
    }

    private static void giveItems(ServerPlayer player, Good good, int count) {
        int remaining = count;
        int stackLimit = good.maxStackSize();
        while (remaining > 0) {
            int size = Math.min(remaining, stackLimit);
            ItemStack stack = good.stack(size);
            if (!player.getInventory().add(stack)) {
                // No room left: drop the rest at the player's feet rather than voiding it.
                player.drop(stack, false);
            }
            remaining -= size;
        }
        player.containerMenu.broadcastChanges();
    }

    /**
     * The market menu has no slots, so nothing else tells the client that its inventory changed.
     * Container id -2 writes straight into the player's inventory, whatever screen is open.
     */
    private static void syncInventory(ServerPlayer player) {
        for (int slot = 0; slot < TRADEABLE_SLOTS; slot++) {
            player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, player.getInventory().getItem(slot)));
        }
    }

    private static void feedback(ServerPlayer player, Component message, boolean problem) {
        player.displayClientMessage(
                problem ? message.copy().withStyle(ChatFormatting.RED) : message, true);
    }
}
