package com.soloeconomy.gametest;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.market.BasePriceLoader;
import com.soloeconomy.market.MarketAudit;
import com.soloeconomy.market.MarketCatalog;
import com.soloeconomy.market.MarketData;

import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
        MarketCatalog catalog = MarketCatalog.build(seeds.prices(), seeds.untradeable(),
                server.getRecipeManager(), server.registryAccess());
        MarketCatalog.setActive(catalog);
        if (catalog.size() < 500) {
            helper.fail("Catalogue only resolved " + catalog.size()
                    + " items - base prices or recipes did not load");
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
}
