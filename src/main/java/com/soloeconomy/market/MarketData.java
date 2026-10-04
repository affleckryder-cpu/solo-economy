package com.soloeconomy.market;

import com.soloeconomy.config.EconomyConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Live market state: how much of each <em>primitive</em> the market is sitting on.
 *
 * <p>Nothing else has stock. A diamond block is priced, and settles, as nine diamonds - so selling
 * a stack of blocks pushes the diamond price down exactly as hard as selling nine stacks of
 * diamonds would, and the two can never disagree about what a diamond is worth.
 *
 * <p>Mean reversion is applied lazily on read rather than by ticking every entry, so the whole
 * market costs nothing per tick.
 */
public class MarketData extends SavedData {

    public static final String FILE_ID = "soloeconomy_market";
    private static final long TICKS_PER_DAY = 24000L;
    /** Stock never reaches zero, or the price curve divides by nothing. */
    private static final double MIN_STOCK = 1.0D;

    private final Map<Item, Entry> entries = new HashMap<>();

    public static MarketData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(MarketData::load, MarketData::new, FILE_ID);
    }

    public MarketData() {
    }

    // ------------------------------------------------------------------
    // Stock
    // ------------------------------------------------------------------

    /** Current stock of a primitive, with time-based recovery folded in. */
    public double stockOf(Item primitive, long gameTime) {
        double base = MarketCatalog.active().baseStock(primitive);
        Entry entry = entries.get(primitive);
        if (entry == null) {
            return base;
        }
        return applyRecovery(entry, base, gameTime);
    }

    private double applyRecovery(Entry entry, double baseStock, long gameTime) {
        long elapsed = gameTime - entry.lastTick;
        if (elapsed <= 0L) {
            return entry.stock;
        }
        double recovery = EconomyConfig.INSTANCE.recoveryPerDay.get();
        if (recovery <= 0.0D) {
            return entry.stock;
        }
        double days = (double) elapsed / TICKS_PER_DAY;
        double remaining = recovery >= 1.0D ? 0.0D : Math.pow(1.0D - recovery, days);
        double healed = baseStock + (entry.stock - baseStock) * remaining;

        entry.stock = healed;
        entry.lastTick = gameTime;
        setDirty();
        return healed;
    }

    /** Forget this primitive's trading history, so it sits at its equilibrium price again. */
    public void resetStock(Item primitive) {
        if (entries.remove(primitive) != null) {
            setDirty();
        }
    }

    private void setStock(Item primitive, double stock, long gameTime) {
        Entry entry = entries.computeIfAbsent(primitive, k -> new Entry());
        entry.stock = Math.max(MIN_STOCK, stock);
        entry.lastTick = gameTime;
        setDirty();
    }

    // ------------------------------------------------------------------
    // Pricing
    // ------------------------------------------------------------------

    /**
     * Price of one primitive at a given stock level. Selling floods the market and pushes this
     * down; buying drains it and pushes it up. Clamped so a market can be wrecked but never
     * permanently killed.
     */
    public static double primitivePrice(Item primitive, double stock) {
        MarketCatalog catalog = MarketCatalog.active();
        double base = catalog.primitivePrice(primitive);
        if (base <= 0.0D) {
            return 0.0D;
        }
        double ratio = catalog.baseStock(primitive) / Math.max(stock, MIN_STOCK);
        double price = base * Math.pow(ratio, EconomyConfig.INSTANCE.elasticity.get());

        double min = base * EconomyConfig.INSTANCE.minPriceMultiplier.get();
        double max = base * EconomyConfig.INSTANCE.maxPriceMultiplier.get();
        return Math.min(max, Math.max(min, price));
    }

    /**
     * Value of one unit in raw materials, at the given primitive stock levels. Never negative: a
     * bundle with a returned container subtracts that container's value, and if the container's
     * materials have spiked far enough the item is simply worth nothing.
     */
    private static double rawValue(MarketCatalog.Bundle bundle, Map<Item, Double> stocks) {
        double total = 0.0D;
        for (Map.Entry<Item, Double> part : bundle.contents().entrySet()) {
            total += part.getValue() * primitivePrice(part.getKey(), stocks.get(part.getKey()));
        }
        return Math.max(0.0D, total);
    }

    /** The assembly fee charged on top of materials when buying something pre-made. */
    private static double assemblyMultiplier(MarketCatalog.Bundle bundle) {
        return Math.pow(EconomyConfig.INSTANCE.craftMarkup.get(), bundle.craftSteps());
    }

    private Map<Item, Double> snapshotStocks(MarketCatalog.Bundle bundle, long gameTime) {
        Map<Item, Double> stocks = new HashMap<>(bundle.contents().size());
        for (Item primitive : bundle.contents().keySet()) {
            stocks.put(primitive, stockOf(primitive, gameTime));
        }
        return stocks;
    }

    /** What one more unit would cost to buy right now, assembly fee and spread included. */
    public double spotBuyPrice(Item item, long gameTime, double spread) {
        return quoteBuy(item, 1, gameTime, spread).exactValue();
    }

    /** What one more unit would pay out right now. Materials only - the market pays no wages. */
    public double spotSellPrice(Item item, long gameTime, double spread) {
        return quoteSell(item, 1, gameTime, spread).exactValue();
    }

    /**
     * How well supplied this item's materials are: above 1 means glutted and paying badly, below 1
     * means short and paying well. Weighted by each primitive's share of the item's value.
     */
    public double supplyRatio(Item item, long gameTime) {
        MarketCatalog catalog = MarketCatalog.active();
        MarketCatalog.Bundle bundle = catalog.bundle(item);
        if (bundle == null) {
            return 1.0D;
        }
        double weighted = 0.0D;
        double weight = 0.0D;
        for (Map.Entry<Item, Double> part : bundle.contents().entrySet()) {
            if (part.getValue() <= 0.0D) {
                continue; // a returned container says nothing about how well supplied the item is
            }
            Item primitive = part.getKey();
            double share = part.getValue() * catalog.primitivePrice(primitive);
            double base = catalog.baseStock(primitive);
            weighted += share * (stockOf(primitive, gameTime) / base);
            weight += share;
        }
        return weight <= 0.0D ? 1.0D : weighted / weight;
    }

    /**
     * Walk the price curve one unit at a time so bulk trades move the price as they execute.
     * Selling 64 at once and selling 64 one-at-a-time therefore pay exactly the same.
     *
     * <p>Each unit is priced <em>after</em> its own effect on stock, on both sides. That is the
     * worse price for the trader whichever way they trade: a sale is priced into the glut it just
     * created, a purchase into the shortage it just caused. Pricing before the move instead lets a
     * sale land one step up the curve from the purchase that preceded it, and in a thin market
     * that one step outruns the spread - buy one, sell one, and pocket the difference, forever.
     */
    public Quote quoteSell(Item item, int count, long gameTime, double spread) {
        MarketCatalog.Bundle bundle = MarketCatalog.active().bundle(item);
        if (count <= 0 || bundle == null) {
            return Quote.EMPTY;
        }

        Map<Item, Double> stocks = snapshotStocks(bundle, gameTime);
        double total = 0.0D;
        for (int i = 0; i < count; i++) {
            addToStocks(bundle, stocks, 1.0D);
            total += rawValue(bundle, stocks) * (1.0D - spread);
        }
        return new Quote(count, total, (long) Math.floor(total * EconomyAccount.CENTS_PER_EMERALD), stocks);
    }

    public Quote quoteBuy(Item item, int count, long gameTime, double spread) {
        MarketCatalog.Bundle bundle = MarketCatalog.active().bundle(item);
        if (count <= 0 || bundle == null) {
            return Quote.EMPTY;
        }

        double assembly = assemblyMultiplier(bundle);
        Map<Item, Double> stocks = snapshotStocks(bundle, gameTime);
        double total = 0.0D;
        for (int i = 0; i < count; i++) {
            addToStocks(bundle, stocks, -1.0D);
            total += rawValue(bundle, stocks) * assembly * (1.0D + spread);
        }

        // Round buys up so a fractional price can never be exploited down to free.
        long cost = Math.max(1L, (long) Math.ceil(total * EconomyAccount.CENTS_PER_EMERALD));
        return new Quote(count, total, cost, stocks);
    }

    /**
     * Largest quantity a given budget (in cents) can buy. Walks the curve rather than dividing by the spot
     * price, because each unit bought drains the materials and pushes the next one up.
     */
    public int maxAffordable(Item item, long budget, long gameTime, double spread, int limit) {
        MarketCatalog.Bundle bundle = MarketCatalog.active().bundle(item);
        if (bundle == null || budget <= 0L) {
            return 0;
        }

        double assembly = assemblyMultiplier(bundle);
        Map<Item, Double> stocks = snapshotStocks(bundle, gameTime);
        double spent = 0.0D;
        int best = 0;

        for (int count = 1; count <= limit; count++) {
            addToStocks(bundle, stocks, -1.0D);
            spent += rawValue(bundle, stocks) * assembly * (1.0D + spread);
            if (Math.max(1.0D, Math.ceil(spent * EconomyAccount.CENTS_PER_EMERALD)) > budget) {
                break;
            }
            best = count;
        }
        return best;
    }

    private static void addToStocks(MarketCatalog.Bundle bundle, Map<Item, Double> stocks, double units) {
        for (Map.Entry<Item, Double> part : bundle.contents().entrySet()) {
            stocks.merge(part.getKey(), part.getValue() * units,
                    (a, b) -> Math.max(MIN_STOCK, a + b));
        }
    }

    // ------------------------------------------------------------------
    // Settlement
    // ------------------------------------------------------------------

    /** Commit a sale: the item's materials flood the market and their prices drop. */
    public Quote commitSell(Item item, int count, long gameTime, double spread) {
        return commit(quoteSell(item, count, gameTime, spread), gameTime);
    }

    /** Commit a purchase: the item's materials leave the market and their prices rise. */
    public Quote commitBuy(Item item, int count, long gameTime, double spread) {
        return commit(quoteBuy(item, count, gameTime, spread), gameTime);
    }

    private Quote commit(Quote quote, long gameTime) {
        if (quote.isEmpty()) {
            return Quote.EMPTY;
        }
        quote.endStocks().forEach((primitive, stock) -> setStock(primitive, stock, gameTime));
        return quote;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public static MarketData load(CompoundTag tag) {
        MarketData data = new MarketData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entryTag.getString("id"));
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue; // item came from a mod that is no longer installed
            }
            Entry entry = new Entry();
            entry.stock = entryTag.getDouble("stock");
            entry.lastTick = entryTag.getLong("tick");
            data.entries.put(BuiltInRegistries.ITEM.get(id), entry);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Item, Entry> mapEntry : entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", BuiltInRegistries.ITEM.getKey(mapEntry.getKey()).toString());
            entryTag.putDouble("stock", mapEntry.getValue().stock);
            entryTag.putLong("tick", mapEntry.getValue().lastTick);
            list.add(entryTag);
        }
        tag.put("entries", list);
        return tag;
    }

    private static final class Entry {
        double stock;
        long lastTick;
    }

    /**
     * The result of pricing a trade before it happens.
     *
     * @param exactValue the trade's value before rounding to whole emeralds
     * @param cents      hundredths of an emerald actually paid or received
     * @param endStocks  primitive stock levels this trade would leave behind
     */
    public record Quote(int count, double exactValue, long cents, Map<Item, Double> endStocks) {

        public static final Quote EMPTY = new Quote(0, 0.0D, 0L, Map.of());

        public boolean isEmpty() {
            return count <= 0;
        }
    }
}
