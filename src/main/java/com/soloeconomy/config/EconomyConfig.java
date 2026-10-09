package com.soloeconomy.config;

import net.minecraftforge.common.ForgeConfigSpec;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Server-side tuning for the market. Everything that decides whether a trade loop is
 * profitable lives here, so the anti-exploit invariant can be checked in one place.
 */
public final class EconomyConfig {

    public static final ForgeConfigSpec SPEC;
    public static final EconomyConfig INSTANCE;

    public final ForgeConfigSpec.DoubleValue spread;
    public final ForgeConfigSpec.DoubleValue craftMarkup;
    public final ForgeConfigSpec.DoubleValue elasticity;
    public final ForgeConfigSpec.DoubleValue minPriceMultiplier;
    public final ForgeConfigSpec.DoubleValue maxPriceMultiplier;
    public final ForgeConfigSpec.IntValue marketDepth;
    public final ForgeConfigSpec.DoubleValue depthPriceExponent;
    public final ForgeConfigSpec.DoubleValue recoveryPerDay;
    public final ForgeConfigSpec.BooleanValue deriveUnpricedItems;
    public final ForgeConfigSpec.IntValue startingBalance;
    public final ForgeConfigSpec.DoubleValue brokerSpreadMultiplier;
    public final ForgeConfigSpec.IntValue configVersion;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> priceOverrides;
    public final ForgeConfigSpec.DoubleValue priceMultiplier;
    public final ForgeConfigSpec.BooleanValue requireDiscovery;
    public final ForgeConfigSpec.BooleanValue openMarket;
    public final ForgeConfigSpec.BooleanValue modRecipes;
    public final ForgeConfigSpec.IntValue brokerDeals;

    private EconomyConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Solo Economy - market tuning").push("market");

        spread = builder
                .comment("Half-spread between buy and sell price.",
                        "Buy costs price*(1+spread), selling pays price*(1-spread).",
                        "0.10 means a 20% round-trip loss, which is what stops instant buy/sell arbitrage.")
                .defineInRange("spread", 0.10D, 0.0D, 0.9D);

        craftMarkup = builder
                .comment("Assembly fee charged per crafting step when BUYING something pre-made.",
                        "Buying a crafted item costs materials * craftMarkup^steps; selling one only ever",
                        "pays for its materials. This is pure flavour - it decides how much you pay for the",
                        "convenience of not crafting it yourself, and cannot open a money loop at any value,",
                        "because every round trip still loses the spread. 1.0 makes pre-made goods cost",
                        "exactly their materials.")
                .defineInRange("craftMarkup", 1.12D, 1.0D, 3.0D);

        elasticity = builder
                .comment("How hard price reacts to stock. price = base * (baseStock/stock)^elasticity.",
                        "0 = fixed prices, 1 = a doubled stock halves the price.")
                .defineInRange("elasticity", 1.0D, 0.0D, 3.0D);

        minPriceMultiplier = builder
                .comment("Price floor, as a multiple of the item's base price. Stops mass-dumping from",
                        "driving a price to zero and permanently killing that market.")
                .defineInRange("minPriceMultiplier", 0.05D, 0.001D, 1.0D);

        maxPriceMultiplier = builder
                .comment("Price ceiling, as a multiple of the item's base price.")
                .defineInRange("maxPriceMultiplier", 5.0D, 1.0D, 100.0D);

        marketDepth = builder
                .comment("Equilibrium stock, in items, for something worth exactly one emerald.",
                        "This is the single biggest lever on how responsive the economy feels.",
                        "Raise it to make prices sluggish, lower it to make them twitchy.",
                        "At 768, selling a stack of diamonds moves the diamond price about 30%.")
                .defineInRange("marketDepth", 768, 1, 1_000_000);

        depthPriceExponent = builder
                .comment("How much deeper the market is for cheap goods:",
                        "  baseStock = marketDepth * basePrice ^ -depthPriceExponent",
                        "0 gives every item the same depth. 1 makes depth a fixed emerald value, which",
                        "leaves cheap goods effectively immovable. 0.5 is the middle ground: a stack of",
                        "diamonds noticeably moves the price, a stack of dirt barely registers, and a",
                        "double chest of dirt does.")
                .defineInRange("depthPriceExponent", 0.5D, 0.0D, 1.0D);

        recoveryPerDay = builder
                .comment("Fraction of the gap between current stock and baseStock that heals per in-game day.",
                        "0.1 means a flooded market takes about a week to get halfway back to normal.")
                .defineInRange("recoveryPerDay", 0.1D, 0.0D, 1.0D);

        deriveUnpricedItems = builder
                .comment("Derive a price for any item not in base_prices.json by walking its recipe tree.",
                        "Turn this off to restrict the market to explicitly priced goods only.")
                .define("deriveUnpricedItems", true);

        startingBalance = builder
                .comment("Whole emeralds credited to a player's account the first time they log in.")
                .defineInRange("startingBalance", 0, 0, 1_000_000);

        brokerSpreadMultiplier = builder
                .comment("Spread multiplier when a Broker villager has claimed the stall as their job site.",
                        "This is the payoff for actually hiring one, but note that it lowers the spread and",
                        "therefore lowers the safe craftMarkup ceiling too - the safety check uses this",
                        "reduced spread, not the base one, because a brokered stall is the cheapest",
                        "round trip available in the game.")
                .defineInRange("brokerSpreadMultiplier", 0.75D, 0.0D, 1.0D);

        configVersion = builder
                .comment("Used to update old defaults when the mod changes them. Leave it alone.")
                .defineInRange("configVersion", 1, 1, 1000);

        priceMultiplier = builder
                .comment("Scales every default price in base_prices.json. 3 makes everything three times the",
                        "emeralds, so emeralds from villagers and mining go a third as far. Prices set with",
                        "/soloeconomy price are used as written. How fast prices move is unaffected.")
                .defineInRange("priceMultiplier", 1.0D, 0.01D, 100.0D);

        requireDiscovery = builder
                .comment("Only let players buy items they have found themselves. Carrying an item to a stall",
                        "or selling it there unlocks buying it, per player, for good. Selling is never locked.")
                .define("requireDiscovery", true);

        openMarket = builder
                .comment("Add a Store merchant that buys and sells every item with a price, not just what the",
                        "merchants in merchants.json deal in. Modded items are included when they are crafted,",
                        "smelted or stonecut from priced materials, or given a price with /soloeconomy price.")
                .define("openMarket", false);

        brokerDeals = builder
                .comment("How many goods a stall with a Broker has on offer each day (no fee to buy), and how",
                        "many it wants (no fee when you sell). They change every in-game day. 0 turns deals off.")
                .defineInRange("brokerDeals", 3, 0, 20);

        modRecipes = builder
                .comment("Price items from any mod's recipes (Create machines, modded workbenches...), not just",
                        "crafting, smelting and stonecutting. Best effort: only a recipe's ingredient list and",
                        "main result are visible, so fluids and per-slot counts are missed (too cheap), and",
                        "random byproducts or tools that aren't used up can make an item too valuable. Fix any",
                        "odd price with /soloeconomy price. Modded items are only sold with openMarket on or",
                        "when a datapack lists them under a merchant.")
                .define("modRecipes", false);

        priceOverrides = builder
                .comment("Base price changes, as \"item_id=emeralds\". These win over base_prices.json.",
                        "This file belongs to one world. Put a copy in defaultconfigs to start new worlds with it.",
                        "Easiest to change in game: /soloeconomy price <item> set <emeralds>, or ... reset.",
                        "Edits made here by hand apply on /reload. Price raw materials where you can: setting a",
                        "crafted item gives it its own price, detached from its ingredients.")
                .defineListAllowEmpty(List.of("priceOverrides"), List::of,
                        o -> o instanceof String s && s.indexOf('=') > 0);

        builder.pop();
    }

    /**
     * NeoForge keeps whatever an existing config file says, so a changed default never reaches
     * anyone upgrading. Version 2 made bulk selling hit diminishing returns: settings still at
     * their 0.3.x defaults move to the new ones, and anything a player changed is left alone.
     */
    public void migrate() {
        if (configVersion.get() >= 2) {
            return;
        }
        if (recoveryPerDay.get() == 0.5D) {
            recoveryPerDay.set(0.1D);
        }
        if (elasticity.get() == 0.6D) {
            elasticity.set(1.0D);
        }
        if (minPriceMultiplier.get() == 0.2D) {
            minPriceMultiplier.set(0.05D);
        }
        if (marketDepth.get() == 1024) {
            marketDepth.set(768);
        }
        configVersion.set(2);
        configVersion.save();
    }

    /** Effective half-spread for a stall, accounting for whether a broker is working it. */
    public double effectiveSpread(boolean staffed) {
        double base = spread.get();
        return staffed ? base * brokerSpreadMultiplier.get() : base;
    }

    static {
        Pair<EconomyConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(EconomyConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    /** The narrowest spread reachable in game, which is a stall with a broker working it. */
    public double minimumSpread() {
        return spread.get() * brokerSpreadMultiplier.get();
    }
}
