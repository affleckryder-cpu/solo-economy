package com.soloeconomy.network;

import com.soloeconomy.market.Discovery;
import com.soloeconomy.market.EconomyAccount;
import com.soloeconomy.market.MarketCatalog;
import com.soloeconomy.market.MarketData;
import com.soloeconomy.menu.MarketMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every trade is priced and validated here, on the server. The client sends nothing but an item,
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

    public static void handleQuery(MarketQueryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
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
        List<Item> source = !search.isEmpty() ? catalog.tradeableItems()
                : merchant != null ? merchant.items() : List.of();

        List<Listing> carried = new ArrayList<>();
        for (int slot = 0; slot < TRADEABLE_SLOTS; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            Item item = stack.getItem();
            if (isTradeableStack(stack) && catalog.isTradeable(item) && carried.stream().noneMatch(l -> l.item() == item)) {
                carried.add(listing(data, player, item, gameTime, spread));
            }
        }

        List<Listing> listings = new ArrayList<>();
        for (Item item : source) {
            if (!search.isEmpty() && !matchesSearch(item, search)) {
                continue;
            }
            if (payload.inventoryOnly() && countInInventory(player.getInventory(), item) <= 0) {
                continue;
            }
            listings.add(listing(data, player, item, gameTime, spread));
        }

        List<MarketListingsPayload.Merchant> merchants = new ArrayList<>();
        for (MarketCatalog.Merchant m : catalog.merchants()) {
            merchants.add(new MarketListingsPayload.Merchant(m.id(), m.icon()));
        }
        context.reply(new MarketListingsPayload(merchants, listings, carried, (float) spread,
                EconomyAccount.balance(player)));
    }

    private static Listing listing(MarketData data, ServerPlayer player, Item item, long gameTime, double spread) {
        return new Listing(item,
                (float) data.spotBuyPrice(item, gameTime, spread),
                (float) data.spotSellPrice(item, gameTime, spread),
                (float) data.supplyRatio(item, gameTime),
                !Discovery.canBuy(player, item));
    }

    /**
     * Price a hovered row for real. Both directions are resolved against what the player actually
     * has - emeralds to spend, goods to sell - so "All" comes back as a concrete number rather
     * than leaving the UI to guess.
     */
    public static void handleQuoteRequest(QuoteRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof MarketMenu menu)) {
            return;
        }

        Item item = payload.item();
        if (!MarketCatalog.active().isTradeable(item)) {
            return;
        }

        MarketData data = MarketData.get(player.server);
        long gameTime = player.level().getGameTime();
        double spread = menu.spread();
        boolean wantsMax = payload.count() == COUNT_MAX;
        int requested = wantsMax ? MAX_TRADE_COUNT : Mth.clamp(payload.count(), 1, MAX_TRADE_COUNT);

        int sellCount = Math.min(requested, countInInventory(player.getInventory(), item));
        long sellTotal = sellCount > 0
                ? data.quoteSell(item, sellCount, gameTime, spread).cents()
                : 0L;

        int buyCount = !Discovery.canBuy(player, item) ? 0
                : wantsMax
                ? data.maxAffordable(item, EconomyAccount.balance(player), gameTime, spread, MAX_TRADE_COUNT)
                : requested;
        long buyTotal = buyCount > 0
                ? data.quoteBuy(item, buyCount, gameTime, spread).cents()
                : 0L;

        context.reply(new QuotePayload(item, sellCount, sellTotal, buyCount, buyTotal));
    }

    private static boolean matchesSearch(Item item, String lowerSearch) {
        if (item.getDescriptionId().toLowerCase(Locale.ROOT).contains(lowerSearch)) {
            return true;
        }
        return new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(lowerSearch);
    }

    // ------------------------------------------------------------------
    // Trading
    // ------------------------------------------------------------------

    public static void handleTrade(TradePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof MarketMenu menu) || !menu.stillValid(player)) {
            return;
        }

        Item item = payload.item();
        if (!MarketCatalog.active().isTradeable(item)) {
            feedback(player, Component.translatable("message.soloeconomy.untradeable"), true);
            return;
        }

        MarketData data = MarketData.get(player.server);
        long gameTime = player.level().getGameTime();
        double spread = menu.spread();
        boolean wantsMax = payload.count() == COUNT_MAX;

        if (payload.buying()) {
            if (!Discovery.canBuy(player, item)) {
                feedback(player, Component.translatable("message.soloeconomy.locked",
                        new ItemStack(item).getHoverName()), true);
                return;
            }
            int requested = wantsMax
                    ? data.maxAffordable(item, EconomyAccount.balance(player), gameTime, spread, MAX_TRADE_COUNT)
                    : Mth.clamp(payload.count(), 1, MAX_TRADE_COUNT);
            if (requested <= 0) {
                feedback(player, Component.translatable("message.soloeconomy.cannot_afford",
                        EconomyAccount.format(data.quoteBuy(item, 1, gameTime, spread).cents()),
                        EconomyAccount.format(EconomyAccount.balance(player))), true);
                return;
            }
            buy(player, data, item, requested, gameTime, spread);
        } else {
            int requested = wantsMax ? MAX_TRADE_COUNT : Mth.clamp(payload.count(), 1, MAX_TRADE_COUNT);
            sell(player, data, item, requested, gameTime, spread);
        }

        EconomyAccount.sync(player);
    }

    private static void buy(ServerPlayer player, MarketData data, Item item,
                            int count, long gameTime, double spread) {
        MarketData.Quote quote = data.quoteBuy(item, count, gameTime, spread);
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
        data.commitBuy(item, count, gameTime, spread);
        EconomyAccount.withdraw(player, quote.cents());
        giveItems(player, item, count);

        feedback(player, Component.translatable("message.soloeconomy.bought",
                count, new ItemStack(item).getHoverName(), EconomyAccount.format(quote.cents())), false);
    }

    private static void sell(ServerPlayer player, MarketData data, Item item,
                             int count, long gameTime, double spread) {
        int available = countInInventory(player.getInventory(), item);
        if (available <= 0) {
            feedback(player, Component.translatable("message.soloeconomy.none_to_sell",
                    new ItemStack(item).getHoverName()), true);
            return;
        }

        int actual = Math.min(count, available);
        MarketData.Quote quote = data.quoteSell(item, actual, gameTime, spread);
        if (quote.isEmpty()) {
            return;
        }
        if (quote.cents() <= 0L) {
            feedback(player, Component.translatable("message.soloeconomy.worthless",
                    new ItemStack(item).getHoverName()), true);
            return;
        }

        Discovery.discover(player, item);
        // Take the goods first; only pay for what actually left the inventory.
        int removed = removeFromInventory(player.getInventory(), item, actual);
        if (removed <= 0) {
            return;
        }
        MarketData.Quote settled = removed == actual
                ? data.commitSell(item, actual, gameTime, spread)
                : data.commitSell(item, removed, gameTime, spread);

        EconomyAccount.deposit(player, settled.cents());
        feedback(player, Component.translatable("message.soloeconomy.sold",
                removed, new ItemStack(item).getHoverName(), EconomyAccount.format(settled.cents())), false);
    }

    // ------------------------------------------------------------------
    // Emerald deposit / withdrawal, always 1:1
    // ------------------------------------------------------------------

    public static void handleTransfer(TransferPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof MarketMenu menu) || !menu.stillValid(player)) {
            return;
        }

        Inventory inventory = player.getInventory();

        if (payload.deposit()) {
            int held = countInInventory(inventory, Items.EMERALD);
            int amount = payload.amount() < 0 ? held : Math.min(payload.amount(), held);
            if (amount <= 0) {
                feedback(player, Component.translatable("message.soloeconomy.no_emeralds"), true);
                return;
            }
            int removed = removeFromInventory(inventory, Items.EMERALD, amount);
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
            giveItems(player, Items.EMERALD, amount);
            feedback(player, Component.translatable("message.soloeconomy.withdrew", amount), false);
        }

        EconomyAccount.sync(player);
    }

    // ------------------------------------------------------------------
    // Inventory plumbing
    // ------------------------------------------------------------------

    /**
     * Items carrying custom components are never tradeable. That one rule covers enchanted gear,
     * renamed items, damaged tools and - importantly - shulker boxes with things inside, any of
     * which would otherwise be bought or sold at the price of a plain one.
     */
    public static boolean isTradeableStack(ItemStack stack) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty() && !stack.isDamaged();
    }

    private static int countInInventory(Inventory inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < TRADEABLE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() == item && isTradeableStack(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int removeFromInventory(Inventory inventory, Item item, int count) {
        int remaining = count;
        for (int slot = 0; slot < TRADEABLE_SLOTS && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() != item || !isTradeableStack(stack)) {
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

    private static void giveItems(ServerPlayer player, Item item, int count) {
        int remaining = count;
        int stackLimit = new ItemStack(item).getMaxStackSize();
        while (remaining > 0) {
            int size = Math.min(remaining, stackLimit);
            ItemStack stack = new ItemStack(item, size);
            if (!player.getInventory().add(stack)) {
                // No room left: drop the rest at the player's feet rather than voiding it.
                player.drop(stack, false);
            }
            remaining -= size;
        }
        player.containerMenu.broadcastChanges();
    }

    private static void feedback(ServerPlayer player, Component message, boolean problem) {
        player.displayClientMessage(
                problem ? message.copy().withStyle(ChatFormatting.RED) : message, true);
    }
}
