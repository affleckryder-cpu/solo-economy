package com.soloeconomy.gametest;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.event.ServerEvents;
import com.soloeconomy.market.BasePriceLoader;
import com.soloeconomy.market.BrokerDeals;
import com.soloeconomy.market.Discovery;
import com.soloeconomy.market.EconomyAccount;
import com.soloeconomy.market.MarketAudit;
import com.soloeconomy.market.MarketCatalog;
import com.soloeconomy.market.MarketData;
import com.soloeconomy.market.MerchantLoader;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The economy's guarantees, checked against the real recipe set.
 *
 * <p>Every one of these exists because the thing it checks was once broken in a way that reasoning
 * alone did not catch. Run with {@code ./gradlew runGameTestServer}; the process exits non-zero if
 * any test fails.
 */
@GameTestHolder(SoloEconomy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {

    private static final String TEMPLATE = "empty";
    private static final double EPSILON = 1.0e-9D;

    private EconomyGameTests() {
    }

    /** Builds the catalogue exactly as a server start would, and makes it the live one. */
    private static MarketCatalog buildCatalog(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BasePriceLoader.Parsed seeds = BasePriceLoader.current();
        MarketCatalog catalog = MarketCatalog.build(seeds.prices(), seeds.untradeable(), MerchantLoader.current(),
                server.getRecipeManager(), server.registryAccess());
        MarketCatalog.setActive(catalog);
        if (catalog.size() < 150) {
            helper.fail("Merchants only list " + catalog.size()
                    + " priced items - prices, merchants or recipes did not load");
        }
        return catalog;
    }

    /**
     * No recipe may pay more to sell than its inputs cost to buy, at any spread down to zero. Zero is
     * the strictest case: if raw materials alone cannot turn a profit, no spread can make them.
     *
     * <p>Guards against: lead, blaze powder, melon seeds and smooth basalt being hand-priced
     * despite having recipes, and cake being priced as though its milk buckets were destroyed.
     */
    @GameTest(template = TEMPLATE)
    public static void noRecipeIsProfitableAtAnySpread(GameTestHelper helper) {
        MarketCatalog catalog = buildCatalog(helper);
        EconomyConfig config = EconomyConfig.INSTANCE;

        for (double spread : new double[] {0.0D, config.minimumSpread(), config.spread.get()}) {
            List<MarketAudit.LoopRisk> loops = MarketAudit.findLoops(catalog, catalog.recipes(), spread);
            if (!loops.isEmpty()) {
                helper.fail(String.format("%d profitable recipe(s) at spread %.3f, worst: %s",
                        loops.size(), spread, loops.get(0).describe()));
            }
        }
        helper.succeed();
    }

    /**
     * Buying anything and immediately selling it back must lose money, for every item, in small and
     * bulk quantities.
     */
    @GameTest(template = TEMPLATE)
    public static void buyingThenSellingBackAlwaysLoses(GameTestHelper helper) {
        MarketCatalog catalog = buildCatalog(helper);
        double spread = EconomyConfig.INSTANCE.minimumSpread();

        for (Item item : catalog.tradeableItems()) {
            for (int count : new int[] {1, 64}) {
                MarketData market = new MarketData();
                MarketData.Quote bought = market.commitBuy(item, count, 0L, spread);
                MarketData.Quote sold = market.quoteSell(item, count, 0L, spread);
                if (sold.exactValue() >= bought.exactValue() - EPSILON) {
                    helper.fail(String.format("%dx %s bought for %.4f sells back for %.4f",
                            count, item, bought.exactValue(), sold.exactValue()));
                }
            }
        }
        helper.succeed();
    }

    /**
     * The same round trip in the thinnest, steepest market the config allows, one unit at a time.
     *
     * <p>Guards against: pricing each unit before its own effect on stock. A sale then lands one
     * step up the curve from the purchase before it, and at a stock of 2 to 4 that step outruns the
     * spread - buy one, sell one, profit, repeat. The default price cap happens to hide it, so the
     * cap is lifted here.
     */
    @GameTest(template = TEMPLATE)
    public static void thinMarketRoundTripsStillLose(GameTestHelper helper) {
        EconomyConfig config = EconomyConfig.INSTANCE;
        double oldMax = config.maxPriceMultiplier.get();
        double oldElasticity = config.elasticity.get();
        int oldDepth = config.marketDepth.get();

        try {
            config.maxPriceMultiplier.set(100.0D);
            config.elasticity.set(1.0D);
            config.marketDepth.set(1);
            buildCatalog(helper);
            double spread = config.minimumSpread();

            for (Item item : List.of(Items.DIAMOND, Items.NETHER_STAR, Items.COBBLESTONE, Items.DIAMOND_BLOCK)) {
                MarketData market = new MarketData();
                // Drain the market step by step, trying a one-unit round trip at every depth.
                for (int drained = 0; drained < 40; drained++) {
                    MarketData.Quote bought = market.commitBuy(item, 1, 0L, spread);
                    MarketData.Quote sold = market.commitSell(item, 1, 0L, spread);
                    if (sold.exactValue() >= bought.exactValue() - EPSILON) {
                        helper.fail(String.format("%s after draining %d: bought for %.4f, sold back for %.4f",
                                item, drained, bought.exactValue(), sold.exactValue()));
                    }
                    market.commitBuy(item, 1, 0L, spread);
                }
            }
        } finally {
            config.maxPriceMultiplier.set(oldMax);
            config.elasticity.set(oldElasticity);
            config.marketDepth.set(oldDepth);
            buildCatalog(helper);
        }
        helper.succeed();
    }

    /**
     * A crafted item has no market of its own: its price follows its materials, and trading it
     * moves their prices.
     *
     * <p>Guards against: per-item stock, which let a floored diamond block be bought for 53 and
     * uncrafted into 252 emeralds of diamonds.
     */
    @GameTest(template = TEMPLATE)
    public static void craftedItemsTrackTheirMaterials(GameTestHelper helper) {
        buildCatalog(helper);
        double spread = EconomyConfig.INSTANCE.spread.get();
        MarketData market = new MarketData();

        double blockSale = market.quoteSell(Items.DIAMOND_BLOCK, 1, 0L, spread).exactValue();
        double nineSales = market.quoteSell(Items.DIAMOND, 9, 0L, spread).exactValue();
        // A block settles all nine diamonds at once, so it can pay a little less - never more.
        if (blockSale > nineSales + EPSILON || blockSale < nineSales * 0.9D) {
            helper.fail(String.format("A diamond block sells for %.3f but nine diamonds sell for %.3f",
                    blockSale, nineSales));
        }

        double before = market.spotSellPrice(Items.DIAMOND, 0L, spread);
        market.commitSell(Items.DIAMOND_BLOCK, 64, 0L, spread);
        double after = market.spotSellPrice(Items.DIAMOND, 0L, spread);
        if (after >= before) {
            helper.fail(String.format("Dumping diamond blocks left the diamond price at %.3f (was %.3f)",
                    after, before));
        }
        helper.succeed();
    }

    /**
     * Cheap things must cost what they are worth, not a whole emerald.
     *
     * <p>Guards against: balances held in whole emeralds, which rounded every purchase up to at
     * least 1 and every sale of anything cheap down to 0.
     */
    @GameTest(template = TEMPLATE)
    public static void cheapItemsCostFractionsOfAnEmerald(GameTestHelper helper) {
        buildCatalog(helper);
        double spread = EconomyConfig.INSTANCE.spread.get();
        MarketData market = new MarketData();

        // Charged amounts must be the real price in hundredths, not rounded to whole emeralds.
        MarketData.Quote bought = market.quoteBuy(Items.COBBLESTONE, 1, 0L, spread);
        long expected = (long) Math.ceil(bought.exactValue() * EconomyAccount.CENTS_PER_EMERALD);
        if (bought.cents() != expected) {
            helper.fail(String.format("One cobblestone is worth %.4f emeralds, so it should cost %d cents, charged %d",
                    bought.exactValue(), expected, bought.cents()));
        }
        if (bought.cents() >= EconomyAccount.CENTS_PER_EMERALD) {
            helper.fail("One cobblestone cost a whole emerald or more: " + bought.cents() + " cents");
        }

        MarketData.Quote sold = market.quoteSell(Items.COBBLESTONE, 8, 0L, spread);
        if (sold.cents() <= 0L) {
            helper.fail("Selling 8 cobblestone paid nothing");
        }

        if (!EconomyAccount.format(1234L).equals("12.34") || !EconomyAccount.format(1200L).equals("12")) {
            helper.fail("Balance formatting is wrong: " + EconomyAccount.format(1234L)
                    + " and " + EconomyAccount.format(1200L));
        }
        helper.succeed();
    }

    /** Market stock must survive a save and reload, and recover toward normal at the configured rate. */
    @GameTest(template = TEMPLATE)
    public static void marketStockSurvivesSaveAndRecovers(GameTestHelper helper) {
        MarketCatalog catalog = buildCatalog(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        double spread = EconomyConfig.INSTANCE.spread.get();

        MarketData original = new MarketData();
        original.commitSell(Items.DIAMOND, 200, 0L, spread);
        original.commitBuy(Items.OAK_LOG, 50, 0L, spread);

        MarketData loaded = MarketData.load(original.save(new CompoundTag(), registries), registries);

        List<Item> checked = List.of(Items.DIAMOND, Items.OAK_LOG, Items.COBBLESTONE);
        String mismatches = checked.stream()
                .filter(item -> Math.abs(original.stockOf(item, 0L) - loaded.stockOf(item, 0L)) > EPSILON)
                .map(item -> item + " " + original.stockOf(item, 0L) + " -> " + loaded.stockOf(item, 0L))
                .collect(Collectors.joining(", "));
        if (!mismatches.isEmpty()) {
            helper.fail("Stock changed across save/load: " + mismatches);
        }

        double base = catalog.baseStock(Items.DIAMOND);
        double gapBefore = loaded.stockOf(Items.DIAMOND, 0L) - base;
        double gapAfterADay = loaded.stockOf(Items.DIAMOND, 24000L) - base;
        double expected = gapBefore * (1.0D - EconomyConfig.INSTANCE.recoveryPerDay.get());
        if (Math.abs(gapAfterADay - expected) > 1.0e-6D * Math.max(1.0D, Math.abs(gapBefore))) {
            helper.fail(String.format("After one day the diamond glut should be %.3f over normal, was %.3f",
                    expected, gapAfterADay));
        }
        helper.succeed();
    }

    /**
     * A price set with /soloeconomy price must win over base_prices.json, and crafted items must
     * follow it. Reset must bring the default back.
     *
     * <p>Sets the config in memory rather than through /soloeconomy price set: the command saves
     * the file, and NeoForge's reload of it lands on a background thread in the middle of whatever
     * test runs next. The read-only form of the command still runs, to cover its parsing.
     */
    @GameTest(template = TEMPLATE)
    public static void priceOverridesWinAndCraftedItemsFollow(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        List<? extends String> saved = EconomyConfig.INSTANCE.priceOverrides.get();
        double savedMultiplier = EconomyConfig.INSTANCE.priceMultiplier.get();
        try {
            EconomyConfig.INSTANCE.priceOverrides.set(List.of());
            EconomyConfig.INSTANCE.priceMultiplier.set(1.0D);
            ServerEvents.rebuildCatalog(server);
            double defaultPrice = MarketCatalog.active().primitivePrice(Items.DIAMOND);

            EconomyConfig.INSTANCE.priceOverrides.set(List.of("diamond=100"));
            ServerEvents.rebuildCatalog(server);
            if (server.getCommands().getDispatcher().execute("soloeconomy price minecraft:diamond",
                    server.createCommandSourceStack().withSuppressedOutput()) != 1) {
                helper.fail("/soloeconomy price minecraft:diamond did not run");
            }
            double spread = EconomyConfig.INSTANCE.spread.get();
            double blockSale = new MarketData().quoteSell(Items.DIAMOND_BLOCK, 1, 0L, spread).exactValue();
            if (MarketCatalog.active().primitivePrice(Items.DIAMOND) != 100.0D || blockSale < 9 * 100 * (1 - spread) * 0.8D) {
                helper.fail(String.format("Override ignored: diamond %.2f, diamond block sells for %.2f",
                        MarketCatalog.active().primitivePrice(Items.DIAMOND), blockSale));
            }

            EconomyConfig.INSTANCE.priceOverrides.set(List.of());
            ServerEvents.rebuildCatalog(server);
            if (MarketCatalog.active().primitivePrice(Items.DIAMOND) != defaultPrice) {
                helper.fail("Clearing the override did not restore the default diamond price");
            }
        } finally {
            EconomyConfig.INSTANCE.priceOverrides.set(saved);
            EconomyConfig.INSTANCE.priceMultiplier.set(savedMultiplier);
            ServerEvents.rebuildCatalog(server);
        }
        helper.succeed();
    }

    /**
     * priceMultiplier must scale what things cost without changing how deep their markets are,
     * or raising prices would quietly make them swing harder too.
     */
    @GameTest(template = TEMPLATE)
    public static void priceMultiplierScalesPricesNotDepth(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        double saved = EconomyConfig.INSTANCE.priceMultiplier.get();
        List<? extends String> savedOverrides = EconomyConfig.INSTANCE.priceOverrides.get();
        try {
            EconomyConfig.INSTANCE.priceOverrides.set(List.of());
            EconomyConfig.INSTANCE.priceMultiplier.set(1.0D);
            ServerEvents.rebuildCatalog(server);
            double price = MarketCatalog.active().primitivePrice(Items.DIAMOND);
            double depth = MarketCatalog.active().baseStock(Items.DIAMOND);

            EconomyConfig.INSTANCE.priceMultiplier.set(3.0D);
            ServerEvents.rebuildCatalog(server);
            double tripled = MarketCatalog.active().primitivePrice(Items.DIAMOND);
            double tripledDepth = MarketCatalog.active().baseStock(Items.DIAMOND);
            if (Math.abs(tripled - 3 * price) > EPSILON || Math.abs(tripledDepth - depth) > EPSILON) {
                helper.fail(String.format("x3 gave diamond %.3f (was %.3f), depth %.1f (was %.1f)",
                        tripled, price, tripledDepth, depth));
            }
        } finally {
            EconomyConfig.INSTANCE.priceOverrides.set(savedOverrides);
            EconomyConfig.INSTANCE.priceMultiplier.set(saved);
            ServerEvents.rebuildCatalog(server);
        }
        helper.succeed();
    }

    /** Nothing is buyable until found, carrying it unlocks it, and only tradeable things count. */
    @GameTest(template = TEMPLATE)
    public static void carryingAnItemUnlocksBuyingIt(GameTestHelper helper) {
        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            helper.succeed(); // Create sends a mock player packets it can't receive, crashing the test server
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        boolean savedOpen = EconomyConfig.INSTANCE.openMarket.get();
        EconomyConfig.INSTANCE.openMarket.set(false); // with it on, dirt would be tradeable
        ServerEvents.rebuildCatalog(server);
        try {
            checkDiscovery(helper);
        } finally {
            EconomyConfig.INSTANCE.openMarket.set(savedOpen);
            ServerEvents.rebuildCatalog(server);
        }
        helper.succeed();
    }

    private static void checkDiscovery(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        if (Discovery.canBuy(player, Items.DIAMOND)) {
            helper.fail("A new player can already buy diamonds");
        }
        player.getInventory().add(new ItemStack(Items.DIAMOND));
        player.getInventory().add(new ItemStack(Items.DIRT));
        Discovery.discoverCarried(player);
        if (!Discovery.canBuy(player, Items.DIAMOND)) {
            helper.fail("Carrying a diamond did not unlock buying diamonds");
        }
        if (Discovery.canBuy(player, Items.DIAMOND_BLOCK)) {
            helper.fail("Carrying a diamond unlocked diamond blocks too");
        }
        if (Discovery.canBuy(player, Items.DIRT)) {
            helper.fail("Dirt, which no merchant trades, was recorded as discovered");
        }
    }

    /** openMarket must list every priced item, and the audit must still find no loop across them all. */
    @GameTest(template = TEMPLATE)
    public static void openMarketTradesEverythingSafely(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean saved = EconomyConfig.INSTANCE.openMarket.get();
        try {
            EconomyConfig.INSTANCE.openMarket.set(true);
            ServerEvents.rebuildCatalog(server);
            MarketCatalog catalog = MarketCatalog.active();
            if (catalog.size() < 500 || !catalog.isTradeable(Items.DIRT)) {
                helper.fail("Open market only lists " + catalog.size() + " items");
            }
            if (!catalog.loopRisks().isEmpty()) {
                helper.fail("Open market has a crafting loop: " + catalog.loopRisks().get(0).describe());
            }
        } finally {
            EconomyConfig.INSTANCE.openMarket.set(saved);
            ServerEvents.rebuildCatalog(server);
        }
        helper.succeed();
    }

    /**
     * A farm dumping the same thing every day must hit diminishing returns: a month of it has to
     * pay well under four times the first week.
     *
     * <p>Guards against: markets that healed half their drop overnight and floored at 20%, where a
     * 1000-logs-a-day farm earned 8,200 emeralds a month, 4x its first week, enough for an elytra
     * every day.
     */
    @GameTest(template = TEMPLATE)
    public static void dailyDumpingHitsDiminishingReturns(GameTestHelper helper) {
        buildCatalog(helper);
        double spread = EconomyConfig.INSTANCE.spread.get();
        MarketData market = new MarketData();
        double week = 0.0D;
        double month = 0.0D;
        for (int day = 0; day < 30; day++) {
            double paid = market.commitSell(Items.OAK_LOG, 1000, day * 24000L, spread).exactValue();
            month += paid;
            if (day < 7) {
                week += paid;
            }
        }
        if (month > 3.0D * week) {
            helper.fail(String.format("A month of 1000 logs a day paid %.0f, %.1fx the first week's %.0f",
                    month, month / week, week));
        }
        helper.succeed();
    }

    /**
     * modRecipes must price a modded machine's output from its ingredients, and stay off by default.
     * The "machine" here is a recipe class the market doesn't otherwise read, with a Create-style
     * chance output that must be priced by its expected count, not as a sure thing.
     */
    @GameTest(template = TEMPLATE)
    public static void modRecipesPriceMachineOutputsFromIngredients(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Recipe<RecipeInput> machine = new TestMachine();
        RecipeManager recipes = new RecipeManager(server.registryAccess());
        List<RecipeHolder<?>> all = new ArrayList<>(server.getRecipeManager().getRecipes());
        all.add(new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "test_machine"), machine));
        recipes.replaceRecipes(all);

        boolean saved = EconomyConfig.INSTANCE.modRecipes.get();
        BasePriceLoader.Parsed seeds = BasePriceLoader.current();
        try {
            EconomyConfig.INSTANCE.modRecipes.set(false);
            MarketCatalog off = MarketCatalog.build(seeds.prices(), seeds.untradeable(), MerchantLoader.current(),
                    recipes, server.registryAccess());
            if (off.bundle(Items.PIGLIN_BANNER_PATTERN) != null) {
                helper.fail("A modded recipe was read with modRecipes off");
            }

            EconomyConfig.INSTANCE.modRecipes.set(true);
            MarketCatalog on = MarketCatalog.build(seeds.prices(), seeds.untradeable(), MerchantLoader.current(),
                    recipes, server.registryAccess());
            if (on.bundle(Items.PIGLIN_BANNER_PATTERN) == null) {
                helper.fail("modRecipes did not price the machine's output");
            }
            // Two patterns at a 25% chance: half a pattern per craft on average, so each costs double.
            double expected = 2 * (2 * raw(on, Items.IRON_INGOT) + raw(on, Items.REDSTONE));
            double actual = raw(on, Items.PIGLIN_BANNER_PATTERN);
            if (Math.abs(actual - expected) > EPSILON) {
                helper.fail(String.format("Machine output worth %.3f, its ingredients %.3f", actual, expected));
            }
        } finally {
            EconomyConfig.INSTANCE.modRecipes.set(saved);
        }
        helper.succeed();
    }

    private static double raw(MarketCatalog catalog, Item item) {
        double total = 0.0D;
        for (var part : catalog.bundle(item).contents().entrySet()) {
            total += part.getValue() * catalog.primitivePrice(part.getKey());
        }
        return total;
    }

    /**
     * Broker deals drop the fee to zero on one side of a trade. Even then, buying a thing fee-free
     * and selling it straight back at a Broker's stall must lose, for every item, or a deal is a
     * money machine. Today's deals must also be stable and never put one item on both lists.
     */
    @GameTest(template = TEMPLATE)
    public static void brokerDealsNeverPayToFlip(GameTestHelper helper) {
        MarketCatalog catalog = buildCatalog(helper);
        MinecraftServer server = helper.getLevel().getServer();
        double brokerSpread = EconomyConfig.INSTANCE.effectiveSpread(true);
        for (Item item : catalog.tradeableItems()) {
            for (int count : new int[]{1, 64}) {
                MarketData market = new MarketData();
                MarketData.Quote bought = market.commitBuy(item, count, 0L, 0.0D);
                MarketData.Quote sold = market.quoteSell(item, count, 0L, brokerSpread);
                if (sold.cents() >= bought.cents()) {
                    helper.fail(String.format("Fee-free %dx %s costs %d and sells back for %d",
                            count, item, bought.cents(), sold.cents()));
                }
            }
        }

        List<Item> deals = BrokerDeals.items(server);
        int expected = 2 * EconomyConfig.INSTANCE.brokerDeals.get();
        if (deals.size() != expected || deals.stream().distinct().count() != expected
                || !deals.equals(BrokerDeals.items(server))) {
            helper.fail("Today's deals are the wrong size, repeat an item, or change between calls: " + deals);
        }
        Item offer = deals.get(0);
        Item wanted = deals.get(deals.size() - 1);
        if (BrokerDeals.spread(server, offer, true, true) != 0.0D
                || BrokerDeals.spread(server, offer, false, true) != brokerSpread
                || BrokerDeals.spread(server, wanted, false, true) != 0.0D
                || BrokerDeals.spread(server, offer, true, false) != EconomyConfig.INSTANCE.effectiveSpread(false)) {
            helper.fail("A deal waived the wrong fee, or applied at a stall with no Broker");
        }
        helper.succeed();
    }

    /** A modded machine recipe, shaped like Create's: outputs with a chance, read by method name. */
    public static final class TestMachine implements Recipe<RecipeInput> {
        public List<TestOutput> getRollableResults() {
            return List.of(new TestOutput(new ItemStack(Items.PIGLIN_BANNER_PATTERN, 2), 0.25F));
        }

        @Override
        public boolean matches(RecipeInput input, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
            return getResultItem(registries);
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return false;
        }

        @Override
        public ItemStack getResultItem(HolderLookup.Provider registries) {
            return new ItemStack(Items.PIGLIN_BANNER_PATTERN);
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT),
                    Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.REDSTONE));
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return RecipeSerializer.SHAPELESS_RECIPE;
        }

        @Override
        public RecipeType<?> getType() {
            return RecipeType.CRAFTING;
        }
    }

    public static final class TestOutput {
        private final ItemStack stack;
        private final float chance;

        TestOutput(ItemStack stack, float chance) {
            this.stack = stack;
            this.chance = chance;
        }

        public ItemStack getStack() {
            return stack;
        }

        public float getChance() {
            return chance;
        }
    }
}
