package com.soloeconomy.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Server-side tuning for the market. Everything that decides whether a trade loop is
 * profitable lives here, so the anti-exploit invariant can be checked in one place.
 */
public final class EconomyConfig {

    public static final ModConfigSpec SPEC;
    public static final EconomyConfig INSTANCE;

    public final ModConfigSpec.DoubleValue spread;
    public final ModConfigSpec.DoubleValue craftMarkup;
    public final ModConfigSpec.DoubleValue elasticity;
    public final ModConfigSpec.DoubleValue minPriceMultiplier;
    public final ModConfigSpec.DoubleValue maxPriceMultiplier;
    public final ModConfigSpec.IntValue marketDepth;
    public final ModConfigSpec.DoubleValue depthPriceExponent;
    public final ModConfigSpec.DoubleValue recoveryPerDay;
    public final ModConfigSpec.BooleanValue deriveUnpricedItems;
    public final ModConfigSpec.IntValue startingBalance;
    public final ModConfigSpec.DoubleValue brokerSpreadMultiplier;

    private EconomyConfig(ModConfigSpec.Builder builder) {
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
                .defineInRange("elasticity", 0.6D, 0.0D, 3.0D);

        minPriceMultiplier = builder
                .comment("Price floor, as a multiple of the item's base price. Stops mass-dumping from",
                        "driving a price to zero and permanently killing that market.")
                .defineInRange("minPriceMultiplier", 0.20D, 0.001D, 1.0D);

        maxPriceMultiplier = builder
                .comment("Price ceiling, as a multiple of the item's base price.")
                .defineInRange("maxPriceMultiplier", 5.0D, 1.0D, 100.0D);

        marketDepth = builder
                .comment("Equilibrium stock, in items, for something worth exactly one emerald.",
                        "This is the single biggest lever on how responsive the economy feels.",
                        "Raise it to make prices sluggish, lower it to make them twitchy.",
                        "At 1024, selling a stack of diamonds moves the diamond price about 15%.")
                .defineInRange("marketDepth", 1024, 1, 1_000_000);

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
                        "0.5 means a distorted market is halfway back to normal after one day.")
                .defineInRange("recoveryPerDay", 0.5D, 0.0D, 1.0D);

        deriveUnpricedItems = builder
                .comment("Derive a price for any item not in base_prices.json by walking its recipe tree.",
                        "Turn this off to restrict the market to explicitly priced goods only.")
                .define("deriveUnpricedItems", true);

        startingBalance = builder
                .comment("Emeralds credited to a player's account the first time they open a stall.")
                .defineInRange("startingBalance", 0, 0, 1_000_000);

        brokerSpreadMultiplier = builder
                .comment("Spread multiplier when a Broker villager has claimed the stall as their job site.",
                        "This is the payoff for actually hiring one, but note that it lowers the spread and",
                        "therefore lowers the safe craftMarkup ceiling too - the safety check uses this",
                        "reduced spread, not the base one, because a brokered stall is the cheapest",
                        "round trip available in the game.")
                .defineInRange("brokerSpreadMultiplier", 0.75D, 0.0D, 1.0D);

        builder.pop();
    }

    /** Effective half-spread for a stall, accounting for whether a broker is working it. */
    public double effectiveSpread(boolean staffed) {
        double base = spread.get();
        return staffed ? base * brokerSpreadMultiplier.get() : base;
    }

    static {
        Pair<EconomyConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(EconomyConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    /** The narrowest spread reachable in game, which is a stall with a broker working it. */
    public double minimumSpread() {
        return spread.get() * brokerSpreadMultiplier.get();
    }
}
